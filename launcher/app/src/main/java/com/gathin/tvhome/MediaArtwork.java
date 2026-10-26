package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

final class MediaArtwork {
    interface Transport {HttpURLConnection open(String source) throws Exception;}
    private volatile boolean closed,paused;
    private final Object lock=new Object();
    private final Map<ImageView,Subscription> pending=new WeakHashMap<>();
    private final Map<String,Job> jobs=new HashMap<>();
    private final ThreadPoolExecutor worker=(ThreadPoolExecutor)Executors.newFixedThreadPool(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Transport transport;
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(4*1024*1024){
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getAllocationByteCount();}
    };
    MediaArtwork(){this(source->(HttpURLConnection)new URL(source).openConnection());}
    MediaArtwork(Transport transport){this.transport=transport;}
    private static final class Subscription {
        final Context context;final String uri;final WeakReference<ImageView> target;final java.util.function.Consumer<Bitmap> applied;Job job;
        Subscription(Context c,String uri,ImageView view,java.util.function.Consumer<Bitmap> applied){context=c;this.uri=uri;target=new WeakReference<>(view);this.applied=applied;}
    }
    private final class Job {
        final Context context;final String uri;final List<Subscription> listeners=new ArrayList<>();
        volatile boolean cancelled;volatile HttpURLConnection connection;Future<?> future;
        Job(Subscription s){context=s.context;uri=s.uri;}
        void check() throws InterruptedIOException {if(cancelled||closed||paused||Thread.currentThread().isInterrupted())throw new InterruptedIOException();}
        void cancel(){cancelled=true;if(future!=null){future.cancel(true);if(future instanceof Runnable)worker.remove((Runnable)future);}HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    }
    void load(Context context,String uri,ImageView view){load(context,uri,view,null);}
    void load(Context context,String uri,ImageView view,java.util.function.Consumer<Bitmap> applied){
        cancel(view);if(closed||uri==null||uri.isEmpty())return;view.setTag(uri);Bitmap ready=cache.get(uri);
        if(ready!=null){view.setImageBitmap(ready);if(applied!=null)applied.accept(ready);return;}
        Subscription s=new Subscription(context.getApplicationContext(),uri,view,applied);pending.put(view,s);if(!paused)enqueue(s);
    }
    private void enqueue(Subscription s){
        synchronized(lock){
            if(closed||paused)return;
            Job job=jobs.get(s.uri);
            if(job==null){job=new Job(s);jobs.put(s.uri,job);}
            s.job=job;job.listeners.add(s);
            if(job.future==null){final Job task=job;job.future=worker.submit(()->{
                Bitmap image=null;
                try{task.check();image=read(task);task.check();if(image!=null)cache.put(task.uri,image);}catch(Exception|OutOfMemoryError ignored){}
                final Bitmap result=image;main.post(()->finish(task,result));
            });}
        }
    }
    private void finish(Job job,Bitmap bitmap){
        List<Subscription> listeners;
        synchronized(lock){if(jobs.get(job.uri)!=job)return;jobs.remove(job.uri);listeners=new ArrayList<>(job.listeners);job.listeners.clear();}
        for(Subscription s:listeners){ImageView view=s.target.get();if(view==null||pending.get(view)!=s)continue;
            if(closed||paused||job.cancelled)continue;pending.remove(view);
            if(bitmap!=null&&s.uri.equals(view.getTag())){view.setImageBitmap(bitmap);if(s.applied!=null)s.applied.accept(bitmap);}
        }
    }
    void cancel(ImageView view){
        view.setTag(null);Subscription s=pending.remove(view);if(s==null)return;Job job=s.job;if(job==null)return;
        boolean cancel=false;synchronized(lock){job.listeners.remove(s);if(job.listeners.isEmpty()&&jobs.get(job.uri)==job){jobs.remove(job.uri);cancel=true;}}
        if(cancel)job.cancel();
    }
    void pause(){
        paused=true;List<Job> cancelled;
        synchronized(lock){cancelled=new ArrayList<>(jobs.values());jobs.clear();for(Job job:cancelled){for(Subscription s:job.listeners)s.job=null;job.listeners.clear();}}
        for(Job job:cancelled)job.cancel();
    }
    void resume(){
        if(closed)return;paused=false;
        for(Subscription s:new ArrayList<>(pending.values())){
            ImageView view=s.target.get();if(view==null||!s.uri.equals(view.getTag())||s.job!=null)continue;
            Bitmap ready=cache.get(s.uri);if(ready!=null){pending.remove(view);view.setImageBitmap(ready);if(s.applied!=null)s.applied.accept(ready);}else enqueue(s);
        }
    }
    void trim(){cache.evictAll();}
    private Bitmap read(Job job) throws Exception {
        Context c=job.context;String source=job.uri;job.check();
        if(source.startsWith("app-icon://")){
            String pkg=source.substring(11);android.graphics.drawable.Drawable d=c.getPackageManager().getApplicationBanner(pkg);if(d==null)d=c.getPackageManager().getApplicationIcon(pkg);if(d==null)return null;
            int w=Math.max(1,d.getIntrinsicWidth()),h=Math.max(1,d.getIntrinsicHeight());float scale=Math.min(1f,512f/Math.max(w,h));w=Math.max(1,Math.round(w*scale));h=Math.max(1,Math.round(h*scale));Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);d.setBounds(0,0,w,h);d.draw(new Canvas(b));return b;
        }
        Uri uri=Uri.parse(source);boolean https="https".equals(uri.getScheme());
        ThumbnailDiskCache disk=https?new ThumbnailDiskCache(new File(c.getCacheDir(),"media-thumbnails")):null;
        if(disk!=null){byte[] saved=disk.read(source,System.currentTimeMillis());if(saved!=null){Bitmap image=decode(saved);if(image!=null)return image;disk.remove(source);}}
        InputStream stream;
        if(https){
            HttpURLConnection connection=transport.open(source);job.connection=connection;
            try{job.check();connection.setConnectTimeout(5000);connection.setReadTimeout(5000);connection.setInstanceFollowRedirects(false);if(connection.getResponseCode()!=200){connection.disconnect();job.connection=null;return null;}stream=connection.getInputStream();}
            catch(Exception e){connection.disconnect();job.connection=null;throw e;}
        }else if("content".equals(uri.getScheme())||"android.resource".equals(uri.getScheme()))stream=c.getContentResolver().openInputStream(uri);
        else return null;
        try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(in==null)return null;byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){job.check();if(out.size()+n>8*1024*1024)return null;out.write(buffer,0,n);}
            job.check();Bitmap image=decode(out.toByteArray());
            if(image!=null&&disk!=null){job.check();ByteArrayOutputStream encoded=new ByteArrayOutputStream();if(image.compress(image.hasAlpha()?Bitmap.CompressFormat.PNG:Bitmap.CompressFormat.JPEG,85,encoded)){job.check();disk.write(source,encoded.toByteArray(),System.currentTimeMillis());}}
            return image;
        }finally{HttpURLConnection connection=job.connection;if(connection!=null)connection.disconnect();job.connection=null;}
    }
    private static Bitmap decode(byte[] bytes){
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<1||options.outHeight<1)return null;options.inJustDecodeBounds=false;options.inSampleSize=1;
        while(options.outWidth/options.inSampleSize>640||options.outHeight/options.inSampleSize>640)options.inSampleSize*=2;
        return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
    }

    static Bitmap blur(Bitmap source){return blur(source,.48f);}
    static Bitmap blur(Bitmap source,float brightness){
        final int width=640,height=360,radius=6,diameter=radius*2+1;
        Bitmap small=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        float scale=Math.max((float)width/source.getWidth(),(float)height/source.getHeight());
        int cropWidth=Math.max(1,Math.round(width/scale)),cropHeight=Math.max(1,Math.round(height/scale));
        int left=(source.getWidth()-cropWidth)/2,top=(source.getHeight()-cropHeight)/2;
        new Canvas(small).drawBitmap(source,new Rect(left,top,left+cropWidth,top+cropHeight),new Rect(0,0,width,height),new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        int[] pixels=new int[width*height],temp=new int[pixels.length];small.getPixels(pixels,0,width,0,0,width,height);
        // Two passes at 4x the resolution retain detail: about 20% of the old apparent blur.
        // Sliding sums produce the same clamped box blur without re-reading 13 pixels per pixel.
        for(int pass=0;pass<2;pass++){
            for(int y=0;y<height;y++){
                int r=0,g=0,b=0,base=y*width;
                for(int k=-radius;k<=radius;k++){int pixel=pixels[base+Math.max(0,Math.min(width-1,k))];r+=(pixel>>16)&255;g+=(pixel>>8)&255;b+=pixel&255;}
                for(int x=0;x<width;x++){
                    temp[base+x]=0xff000000|(r/diameter<<16)|(g/diameter<<8)|b/diameter;
                    int leaving=pixels[base+Math.max(0,x-radius)],right=pixels[base+Math.min(width-1,x+radius+1)];
                    r+=((right>>16)&255)-((leaving>>16)&255);g+=((right>>8)&255)-((leaving>>8)&255);b+=(right&255)-(leaving&255);
                }
            }
            for(int x=0;x<width;x++){
                int r=0,g=0,b=0;
                for(int k=-radius;k<=radius;k++){int pixel=temp[Math.max(0,Math.min(height-1,k))*width+x];r+=(pixel>>16)&255;g+=(pixel>>8)&255;b+=pixel&255;}
                for(int y=0;y<height;y++){
                    pixels[y*width+x]=0xff000000|(r/diameter<<16)|(g/diameter<<8)|b/diameter;
                    int leaving=temp[Math.max(0,y-radius)*width+x],bottom=temp[Math.min(height-1,y+radius+1)*width+x];
                    r+=((bottom>>16)&255)-((leaving>>16)&255);g+=((bottom>>8)&255)-((leaving>>8)&255);b+=(bottom&255)-(leaving&255);
                }
            }
        }
        int light=Math.round(Math.max(0f,Math.min(1f,brightness))*100);
        // Music keeps its existing darkness; optional glass preserves the backdrop colors.
        for(int i=0;i<pixels.length;i++){int p=pixels[i];pixels[i]=0xff000000|(((p>>16)&255)*light/100<<16)|(((p>>8)&255)*light/100<<8)|(p&255)*light/100;}
        small.setPixels(pixels,0,width,0,0,width,height);return small;
    }
    void close(){closed=true;pause();pending.clear();worker.shutdownNow();main.removeCallbacksAndMessages(null);cache.evictAll();}
}
