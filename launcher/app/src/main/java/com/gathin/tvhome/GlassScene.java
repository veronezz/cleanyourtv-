package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One small frozen texture per open surface, never a continuous capture or blur loop. */
final class GlassScene implements AutoCloseable {
    private final WeakReference<View> anchor;
    private final ArrayList<WeakReference<Drawable>> surfaces=new ArrayList<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF target=new RectF();private final int[] origin=new int[2],location=new int[2];
    private Bitmap texture;private volatile boolean closed;private volatile int generation;
    GlassScene(View anchor,Bitmap source){this.anchor=new WeakReference<>(anchor);update(source);}
    static GlassScene capture(View view){
        if(view==null||view.getWidth()==0||view.getHeight()==0)return null;
        try{Bitmap b=Bitmap.createBitmap(640,Math.max(1,Math.round(640f*view.getHeight()/view.getWidth())),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.scale((float)b.getWidth()/view.getWidth(),(float)b.getHeight()/view.getHeight());view.draw(c);return new GlassScene(view,b);}catch(RuntimeException|OutOfMemoryError e){return null;}
    }
    void update(Bitmap source){
        if(closed||source==null)return;int request=++generation;
        worker.execute(()->{if(closed||request!=generation)return;try{Bitmap blur=MediaArtwork.blur(source,1f);main.post(()->{if(closed||request!=generation)return;texture=blur;for(int n=surfaces.size()-1;n>=0;n--){Drawable d=surfaces.get(n).get();if(d==null)surfaces.remove(n);else d.invalidateSelf();}});}catch(RuntimeException|OutOfMemoryError ignored){}});
    }
    void observe(Drawable drawable){if(surfaces.size()>64)surfaces.removeIf(ref->ref.get()==null);surfaces.add(new WeakReference<>(drawable));}
    void draw(Canvas c,View host){
        View root=anchor.get();Bitmap b=texture;if(closed||root==null||host==null||b==null||root.getWidth()==0||root.getHeight()==0)return;
        root.getLocationOnScreen(origin);host.getLocationOnScreen(location);
        float scale=Math.max((float)root.getWidth()/b.getWidth(),(float)root.getHeight()/b.getHeight());float w=b.getWidth()*scale,h=b.getHeight()*scale;
        float left=origin[0]-location[0]+(root.getWidth()-w)/2,top=origin[1]-location[1]+(root.getHeight()-h)/2;
        target.set(left,top,left+w,top+h);c.drawBitmap(b,null,target,paint);
    }
    @Override public void close(){closed=true;generation++;worker.shutdownNow();texture=null;surfaces.clear();}
}
