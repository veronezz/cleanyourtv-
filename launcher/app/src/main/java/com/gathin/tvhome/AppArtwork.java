package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;

/** Bounded process cache. Loaded only by the app-list worker, never by a tile bind. */
final class AppArtwork {
    private static final int BUDGET=(int)Math.min(8*1024*1024,Runtime.getRuntime().maxMemory()/16);
    private static final LruCache<String,Bitmap> CACHE=new LruCache<String,Bitmap>(BUDGET){
        @Override protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}
    };
    static Drawable load(Context c,ResolveInfo source,boolean banner){
        String pkg=source.activityInfo.packageName,key=pkg+"/"+source.activityInfo.name+"/"+banner+"/"+IconPacks.cacheKey(c,pkg);
        Bitmap bitmap=CACHE.get(key);
        if(bitmap==null){
            try{
                Drawable d=banner?AppAppearance.image(c,pkg):null;
                boolean pack=false;
                if(d==null&&banner){
                    Drawable original=source.activityInfo.loadBanner(c.getPackageManager());
                    Drawable themed=IconPacks.artwork(c,source,original!=null);
                    if(themed!=null){d=themed;pack=true;}else d=original;
                }
                if(d==null&&!banner)d=source.loadIcon(c.getPackageManager());
                if(d==null)return null;
                int w=Math.max(1,d.getIntrinsicWidth()),h=Math.max(1,d.getIntrinsicHeight());
                float scale=Math.min(1f,(banner?512f:128f)/Math.max(w,h));w=Math.max(1,Math.round(w*scale));h=Math.max(1,Math.round(h*scale));
                if(pack){
                    // Render into a landscape card with aspect preserved, including transparent logos.
                    bitmap=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);
                    float fit=Math.min(288f/w,162f/h);int width=Math.max(1,Math.round(w*fit)),height=Math.max(1,Math.round(h*fit));
                    int left=(320-width)/2,top=(180-height)/2;d.setBounds(left,top,left+width,top+height);
                }else{bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);d.setBounds(0,0,w,h);}
                d.draw(new Canvas(bitmap));CACHE.put(key,bitmap);
            }catch(Exception|OutOfMemoryError error){return null;}
        }
        return new BitmapDrawable(c.getResources(),bitmap);
    }
    static void invalidate(String pkg){for(String key:CACHE.snapshot().keySet())if(key.startsWith(pkg+"/"))CACHE.remove(key);}
    static void trim(){CACHE.evictAll();}
}
