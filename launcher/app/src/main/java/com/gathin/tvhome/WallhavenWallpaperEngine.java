package com.gathin.tvhome;

import android.content.*;
import android.graphics.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/** Public SFW Wallhaven provider. Two image slots, cached metadata, cancellable foreground work. */
final class WallhavenWallpaperEngine {
    interface Host {void wallpaper(Bitmap image);}
    interface Transport {HttpURLConnection open(String url) throws Exception;}
    private static final Object DISK=new Object();
    private final Context context;private final Host host;private final Transport transport;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
    private volatile boolean active,closed;private volatile Session session;private Future<?> pending;private boolean busy;
    private long retryAt;private String delivered="",poolKey="";private List<WallhavenImages.Image> pool=new ArrayList<>();private int cursor;
    private final Runnable tick=()->{if(active&&!closed&&!busy)check();};
    WallhavenWallpaperEngine(Context c,Host host){this(c,host,url->(HttpURLConnection)new URL(url).openConnection());}
    WallhavenWallpaperEngine(Context c,Host host,Transport transport){context=c.getApplicationContext();this.host=host;this.transport=transport;}
    void start(){if(active||closed)return;active=true;main.post(tick);}
    void stop(){active=false;main.removeCallbacks(tick);Session s=session;if(s!=null)s.cancel();if(pending!=null)pending.cancel(true);busy=false;}
    void close(){stop();closed=true;worker.shutdownNow();}
    private File dir(){File d=new File(context.getFilesDir(),"wallhaven-wallpapers");d.mkdirs();return d;}
    private File[] files(){File[] out=dir().listFiles();return out==null?new File[0]:out;}
    private File slot(int n){return new File(dir(),"wall-"+n+".jpg");}
    private final class Session {
        final String key,input,quality;final boolean ratio;volatile boolean cancelled;volatile HttpURLConnection connection;
        Session(String key){this.key=key;SharedPreferences p=LocalPreferences.get(context);input=p.getString("wallhaven_source","nature");quality=p.getString("wallhaven_quality","fhd");ratio=p.getBoolean("wallhaven_widescreen",true);}
        void check() throws IOException {SharedPreferences p=LocalPreferences.get(context);if(cancelled||closed||!active||Thread.currentThread().isInterrupted()||!"wallhaven".equals(LocalPreferences.get(context).getString("background_style",""))||!input.equals(p.getString("wallhaven_source","nature"))||!quality.equals(p.getString("wallhaven_quality","fhd"))||ratio!=p.getBoolean("wallhaven_widescreen",true))throw new InterruptedIOException();}
        HttpURLConnection open(String url) throws Exception {check();HttpURLConnection c=transport.open(url);connection=c;if(cancelled){c.disconnect();check();}c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","TvHome/3.3 Wallhaven wallpapers");return c;}
        void cancel(){cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();}
        void disconnect(HttpURLConnection c){c.disconnect();if(connection==c)connection=null;}
    }
    private void check(){
        if(!active||closed||busy)return;
        WallhavenSource source;try{source=WallpaperSettings.source(context);}catch(Exception e){status("source_invalid");return;}
        Session run=new Session(source.fingerprint);session=run;busy=true;
        pending=worker.submit(()->{
            try{synchronized(DISK){run.check();work(run,source);}}
            catch(InterruptedIOException ignored){}
            catch(Exception|OutOfMemoryError error){if(!run.cancelled&&!closed){retryAt=Math.max(retryAt,System.currentTimeMillis()+300_000);status(error instanceof IOException?error.getMessage():"error");}}
            finally{long delay=nextDelay();main.post(()->{if(session!=run)return;busy=false;if(active&&!closed&&!run.cancelled){main.removeCallbacks(tick);main.postDelayed(tick,delay);}});}
        });
    }
    private void work(Session run,WallhavenSource source) throws Exception {
        SharedPreferences p=LocalPreferences.get(context);run.check();long now=System.currentTimeMillis();
        long manualRequest=p.getLong(WallpaperSettings.NEXT_REQUEST,0);boolean manual=manualRequest>p.getLong(WallpaperSettings.NEXT_APPLIED,0);
        for(File f:files())if(f.getName().endsWith(".download")||f.getName().endsWith(".pending"))f.delete();
        if(!source.fingerprint.equals(p.getString("wallhaven_cache_source",""))){
            int old=Math.max(0,Math.min(1,p.getInt("wallhaven_current_slot",0)));
            if(slot(old).isFile())publishFile(run,old,"previous-source");
            if(now<retryAt)return;
            WallhavenImages.Image first=next(run,source,"");if(first==null){status("empty");retryAt=now+12*60*60_000L;return;}
            int replacement=slot(old).isFile()?1-old:old;
            download(run,source,first,slot(replacement));saveSlot(p,replacement,first);
            p.edit().putString("wallhaven_cache_source",source.fingerprint).putInt("wallhaven_current_slot",replacement).putLong("wallhaven_current_since",System.currentTimeMillis()).apply();
            if(manual){p.edit().putLong(WallpaperSettings.NEXT_APPLIED,manualRequest).apply();manual=false;}
            if(slot(1-replacement).isFile())slot(1-replacement).delete();p.edit().remove("wallhaven_slot_"+(1-replacement)+"_url").apply();delivered="";run.check();
        }
        int current=Math.max(0,Math.min(1,p.getInt("wallhaven_current_slot",0)));boolean due=manual||now-p.getLong("wallhaven_current_since",now)>=interval();
        if(!slot(current).isFile()){
            if(now<retryAt)return;WallhavenImages.Image image=next(run,source,"");if(image==null){status("empty");retryAt=now+12*60*60_000L;return;}
            download(run,source,image,slot(current));saveSlot(p,current,image);run.check();SharedPreferences.Editor restored=p.edit().putLong("wallhaven_current_since",System.currentTimeMillis());if(manual)restored.putLong(WallpaperSettings.NEXT_APPLIED,manualRequest);restored.apply();manual=false;due=false;
        }
        publishFile(run,current,source.fingerprint);
        int next=1-current;if(now<retryAt&&!slot(next).isFile())return;
        if(!slot(next).isFile())fill(run,source,next,p.getString("wallhaven_slot_"+current+"_url",""));
        run.check();if(due&&slot(next).isFile()){
            current=next;SharedPreferences.Editor changed=p.edit().putInt("wallhaven_current_slot",current).putLong("wallhaven_current_since",System.currentTimeMillis());if(manual)changed.putLong(WallpaperSettings.NEXT_APPLIED,manualRequest);changed.apply();publishFile(run,current,source.fingerprint);
            next=1-current;slot(next).delete();p.edit().remove("wallhaven_slot_"+next+"_url").apply();if(now>=retryAt)fill(run,source,next,p.getString("wallhaven_slot_"+current+"_url",""));
        }
        status(slot(1-current).isFile()?"ready":"single");
    }
    private long interval(){return PreferenceValues.minutes(LocalPreferences.get(context).getInt("wallhaven_interval",15))*60_000L;}
    private long nextDelay(){long now=System.currentTimeMillis();if(retryAt>now)return Math.max(1000,retryAt-now);SharedPreferences p=LocalPreferences.get(context);int current=Math.max(0,Math.min(1,p.getInt("wallhaven_current_slot",0)));if(slot(1-current).isFile())return Math.max(1000,p.getLong("wallhaven_current_since",now)+interval()-now);return 12*60*60_000L;}
    private void fill(Session run,WallhavenSource source,int slot,String exclude) throws Exception {run.check();WallhavenImages.Image image=next(run,source,exclude);if(image!=null){download(run,source,image,slot(slot));saveSlot(LocalPreferences.get(context),slot,image);run.check();}}
    private WallhavenImages.Image next(Session run,WallhavenSource source,String exclude) throws Exception {
        run.check();SharedPreferences p=LocalPreferences.get(context);long now=System.currentTimeMillis();
        if(!source.fingerprint.equals(poolKey)||now-p.getLong("wallhaven_metadata_time",0)>=12*60*60_000L||now<p.getLong("wallhaven_metadata_time",0)){
            boolean cached=source.fingerprint.equals(p.getString("wallhaven_metadata_source",""))&&now>=p.getLong("wallhaven_metadata_time",0)&&now-p.getLong("wallhaven_metadata_time",0)<12*60*60_000L;
            if(cached)try{pool=WallhavenImages.parse(p.getString("wallhaven_metadata","{}"),source);}catch(Exception e){cached=false;}
            if(!cached){HttpURLConnection c=run.open(source.endpoint);try{int code=c.getResponseCode();if(code!=200)throw httpFailure(c,code);pool=WallhavenImages.parse(read(run,c.getInputStream(),2*1024*1024),source);run.check();p.edit().putString("wallhaven_metadata",WallhavenImages.encode(pool)).putString("wallhaven_metadata_source",source.fingerprint).putLong("wallhaven_metadata_time",now).apply();}finally{run.disconnect(c);}}
            poolKey=source.fingerprint;cursor=0;Collections.shuffle(pool);
        }
        for(int n=0;n<pool.size();n++){WallhavenImages.Image i=pool.get(cursor++%pool.size());if(!i.url.equals(exclude))return i;}return null;
    }
    private IOException httpFailure(HttpURLConnection c,int code){if(code==429){long seconds=300;try{seconds=Long.parseLong(c.getHeaderField("Retry-After"));}catch(Exception ignored){}retryAt=System.currentTimeMillis()+Math.max(60,Math.min(86400,seconds))*1000;}return new IOException("http:"+code);}
    private String read(Session run,InputStream stream,int max) throws Exception {try(InputStream in=stream;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){run.check();if(out.size()+n>max)throw new IOException("too_large");out.write(b,0,n);}return out.toString("UTF-8");}}
    private void download(Session run,WallhavenSource source,WallhavenImages.Image image,File output) throws Exception {
        run.check();if(!WallhavenSource.imageUrl(image.url))throw new IOException("source_invalid");File raw=new File(output+".download"),encoded=new File(output+".pending");HttpURLConnection c=run.open(image.url);
        try{int code=c.getResponseCode();if(code!=200)throw httpFailure(c,code);String type=c.getContentType();if(type==null||!type.toLowerCase(Locale.ROOT).startsWith("image/"))throw new IOException("image_invalid");
            try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(raw)){byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){run.check();if((total+=n)>20*1024*1024)throw new IOException("too_large");out.write(b,0,n);}}
            BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(raw.getPath(),o);if(!source.accepts(o.outWidth,o.outHeight)||(long)o.outWidth*o.outHeight>80_000_000)throw new IOException("image_invalid");
            int capW=1920,capH=1080;android.util.DisplayMetrics dm=context.getResources().getDisplayMetrics();if(dm.widthPixels>=3840&&dm.heightPixels>=2160&&Runtime.getRuntime().maxMemory()>=256L*1024*1024){capW=3840;capH=2160;}
            float scale=Math.min(1f,Math.min((float)capW/o.outWidth,(float)capH/o.outHeight));int goalW=Math.max(1,Math.round(o.outWidth*scale)),goalH=Math.max(1,Math.round(o.outHeight*scale));
            o.inSampleSize=1;while(o.outWidth/(o.inSampleSize*2)>=goalW&&o.outHeight/(o.inSampleSize*2)>=goalH)o.inSampleSize*=2;o.inJustDecodeBounds=false;o.inPreferredConfig=Bitmap.Config.RGB_565;run.check();Bitmap bitmap=BitmapFactory.decodeFile(raw.getPath(),o);if(bitmap==null)throw new IOException("image_invalid");
            if(bitmap.getWidth()!=goalW||bitmap.getHeight()!=goalH){Bitmap sized=Bitmap.createScaledBitmap(bitmap,goalW,goalH,true);if(sized!=bitmap)bitmap.recycle();bitmap=sized;}
            try(FileOutputStream out=new FileOutputStream(encoded)){if(!bitmap.compress(Bitmap.CompressFormat.JPEG,92,out))throw new IOException("image_invalid");}finally{bitmap.recycle();}
            run.check();if(!encoded.renameTo(output))throw new IOException("error");
        }finally{run.disconnect(c);raw.delete();encoded.delete();}
    }
    private void publishFile(Session run,int current,String source) throws Exception {String key=current+":"+slot(current).lastModified()+":"+source;if(!key.equals(delivered)){Bitmap image=decode(slot(current));if(image==null){slot(current).delete();throw new IOException("image_invalid");}publish(run,image,key);}}
    private Bitmap decode(File f){BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=Bitmap.Config.RGB_565;return BitmapFactory.decodeFile(f.getPath(),o);}
    private void saveSlot(SharedPreferences p,int n,WallhavenImages.Image i){p.edit().putString("wallhaven_slot_"+n+"_url",i.url).putString("wallhaven_slot_"+n+"_page",i.page).putString("wallhaven_slot_"+n+"_id",i.id).apply();}
    private void publish(Session run,Bitmap image,String key){if(key.equals(delivered))return;main.post(()->{try{run.check();if(session!=run)return;host.wallpaper(image);delivered=key;}catch(IOException ignored){}});}
    private void status(String value){if(!closed)LocalPreferences.get(context).edit().putString("wallhaven_status",value==null?"error":value).apply();}
}
