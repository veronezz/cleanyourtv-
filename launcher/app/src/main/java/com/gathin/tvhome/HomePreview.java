package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** A small, process-local snapshot captured only when Preferences is opened. */
final class HomePreview {
    private static Bitmap picture,background;
    private static java.lang.ref.WeakReference<View> home=new java.lang.ref.WeakReference<>(null);
    private static int generation;
    private static final java.util.List<java.util.function.Consumer<Bitmap>> listeners=new java.util.ArrayList<>();
    static void observe(java.util.function.Consumer<Bitmap> listener){listeners.add(listener);}
    static void removeObserver(java.util.function.Consumer<Bitmap> listener){listeners.remove(listener);}
    static void capture(android.app.Activity activity,View view){
        if(view==null||view.getWidth()==0||view.getHeight()==0)return;
        home=new java.lang.ref.WeakReference<>(view);background=null;int request=++generation;
        try{
            Bitmap bitmap=Bitmap.createBitmap(480,270,Bitmap.Config.ARGB_8888);
            int[] location=new int[2];view.getLocationInWindow(location);
            android.graphics.Rect bounds=new android.graphics.Rect(location[0],location[1],location[0]+view.getWidth(),location[1]+view.getHeight());
            android.view.PixelCopy.request(activity.getWindow(),bounds,bitmap,result->{
                if(result!=android.view.PixelCopy.SUCCESS||request!=generation){bitmap.recycle();return;}
                picture=bitmap;
                for(java.util.function.Consumer<Bitmap> listener:new java.util.ArrayList<>(listeners))listener.accept(bitmap);
            },new android.os.Handler(android.os.Looper.getMainLooper()));
        }catch(RuntimeException|OutOfMemoryError ignored){}
    }
    static void releaseBackground(){background=null;}
    static void trim(){generation++;picture=null;background=null;}
    static Bitmap background(){
        if(background!=null)return background;
        View root=home.get();
        try{
            Bitmap b=Bitmap.createBitmap(480,270,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);
            if(root!=null&&root.getWidth()>0&&root.getHeight()>0){
                c.scale(480f/root.getWidth(),270f/root.getHeight());
                if(root.getBackground()!=null)root.getBackground().draw(c);
                if(root instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)root;
                    for(int n=0;n<group.getChildCount();n++){View child=group.getChildAt(n);
                        if(!(child instanceof android.widget.ImageView)||child.getVisibility()!=View.VISIBLE||child.getAlpha()<=0||child.getWidth()<root.getWidth()*.8f||child.getHeight()<root.getHeight()*.8f)continue;
                        c.save();c.translate(child.getLeft()+child.getTranslationX(),child.getTop()+child.getTranslationY());int layer=c.saveLayerAlpha(0,0,child.getWidth(),child.getHeight(),Math.round(child.getAlpha()*255));child.draw(c);c.restoreToCount(layer);c.restore();
                    }
                }
            }else{Paint paint=new Paint();paint.setShader(new android.graphics.LinearGradient(0,0,480,270,new int[]{0xff314b57,0xff777c64,0xff182e36},null,android.graphics.Shader.TileMode.CLAMP));c.drawRect(0,0,480,270,paint);}
            background=b;return b;
        }catch(RuntimeException|OutOfMemoryError e){return snapshot();}
    }
    static Bitmap snapshot(){
        if(picture!=null)return picture;
        Bitmap b=Bitmap.createBitmap(480,270,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(0xff14223a);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(0xffeaf8ff);p.setTextSize(11);c.drawText("BOA NOITE",28,25,p);p.setTextSize(31);c.drawText("22:49",28,61,p);
        p.setColor(0xff9fbed3);p.setTextSize(10);c.drawText("SEGUNDA-FEIRA  ·  SUA TELA INICIAL",130,57,p);
        p.setColor(0xffdbe8f4);p.setTextSize(12);c.drawText("Continuar assistindo",28,100,p);
        int[] colors={0xffb4a839,0xff316a6b,0xff3881b8,0xff8c4a89,0xffb7586a,0xff303657};
        for(int i=0;i<6;i++){p.setColor(colors[i]);c.drawRoundRect(new RectF(28+i*72,112,93+i*72,151),7,7,p);}
        p.setColor(0x25ffffff);c.drawRoundRect(new RectF(22,192,458,253),15,15,p);
        String[] apps={"Netflix","Prime","YouTube","Globo","Spotify","Max"};
        for(int i=0;i<6;i++){p.setColor(colors[5-i]);c.drawRoundRect(new RectF(30+i*72,202,92+i*72,242),8,8,p);p.setColor(Color.WHITE);p.setTextSize(9);c.drawText(apps[i],35+i*72,226,p);}
        picture=b;return picture;
    }
}
