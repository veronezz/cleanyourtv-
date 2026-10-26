package com.gathin.tvhome;

import android.animation.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Reuses a tiny integral luminance map and text samples until their background/rectangle changes. */
final class BackdropContrast implements AutoCloseable {
    private final ViewGroup root;private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final WeakHashMap<TextView,Style> originals=new WeakHashMap<>();
    private final WeakHashMap<TextView,Sample> samples=new WeakHashMap<>();
    private final WeakHashMap<TextView,Integer> targets=new WeakHashMap<>();
    private Bitmap source;private LuminanceGrid grid;private int revision;
    private ImageView.ScaleType fit=ImageView.ScaleType.CENTER_CROP;private ValueAnimator animation;private boolean closed,paused;
    private final Runnable refresh=this::apply;
    private final ViewTreeObserver.OnGlobalLayoutListener layout=()->schedule();
    private final ViewTreeObserver.OnScrollChangedListener scroll=()->schedule();
    private static final class Style {final int color,shadow;final float radius,dx,dy;Style(TextView v){color=v.getCurrentTextColor();shadow=v.getShadowColor();radius=v.getShadowRadius();dx=v.getShadowDx();dy=v.getShadowDy();}}
    private static final class Sample {
        final int left,top,right,bottom,revision;final boolean dark;
        Sample(int l,int t,int r,int b,int revision,boolean dark){left=l;top=t;right=r;bottom=b;this.revision=revision;this.dark=dark;}
        boolean matches(int l,int t,int r,int b,int rev){return left==l&&top==t&&right==r&&bottom==b&&revision==rev;}
    }
    BackdropContrast(ViewGroup root){this.root=root;root.getViewTreeObserver().addOnGlobalLayoutListener(layout);root.getViewTreeObserver().addOnScrollChangedListener(scroll);}
    void source(Bitmap image,ImageView.ScaleType fit){if(source!=image||this.fit!=fit){source=image;this.fit=fit;trim();}schedule();}
    void schedule(){if(closed||paused)return;handler.removeCallbacks(refresh);handler.postDelayed(refresh,100);}
    void pause(){paused=true;handler.removeCallbacks(refresh);if(animation!=null)animation.cancel();}
    void resume(){if(!closed){paused=false;schedule();}}
    void trim(){grid=null;revision++;samples.clear();}
    static boolean darkText(double luminance){return luminance>.179;}
    private void makeMap(){
        Bitmap b=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);
        try{
            Canvas c=new Canvas(b);c.drawColor(0xff14223a);Drawable background=root.getBackground();
            if(background!=null){c.save();c.scale(160f/root.getWidth(),90f/root.getHeight());background.draw(c);c.restore();}
            if(source!=null&&!source.isRecycled()){
                RectF out=new RectF(0,0,160,90);
                if(fit!=ImageView.ScaleType.FIT_XY){float scale=fit==ImageView.ScaleType.CENTER_CROP?Math.max(160f/source.getWidth(),90f/source.getHeight()):Math.min(160f/source.getWidth(),90f/source.getHeight());float w=source.getWidth()*scale,h=source.getHeight()*scale;out.set((160-w)/2,(90-h)/2,(160+w)/2,(90+h)/2);}
                c.drawBitmap(source,null,out,new Paint(Paint.FILTER_BITMAP_FLAG));
            }
            if(LauncherTheme.ios(root.getContext()))c.drawColor(IosStyle.veil(root.getContext()));
            int[] pixels=new int[160*90];b.getPixels(pixels,0,160,0,0,160,90);grid=new LuminanceGrid(pixels,160,90);
        }finally{b.recycle();}
    }
    private void apply(){
        if(closed||paused||root.getWidth()==0||root.getHeight()==0)return;
        if(!LauncherTheme.ios(root.getContext())&&!LocalPreferences.get(root.getContext()).getBoolean("adaptive_contrast",true)){
            if(animation!=null)animation.cancel();
            for(Map.Entry<TextView,Style> e:originals.entrySet()){Style s=e.getValue();e.getKey().setTextColor(s.color);e.getKey().setShadowLayer(s.radius,s.dx,s.dy,s.shadow);}
            samples.clear();targets.clear();return;
        }
        if(grid==null)try{makeMap();}catch(RuntimeException|OutOfMemoryError ignored){return;}
        List<TextView> texts=new ArrayList<>();collect(root,texts);List<TextView> changed=new ArrayList<>();List<Integer> from=new ArrayList<>(),to=new ArrayList<>();
        boolean targetChanged=false;int[] rootAt=new int[2],at=new int[2];root.getLocationOnScreen(rootAt);float density=root.getResources().getDisplayMetrics().density;
        for(TextView text:texts){
            if(text.getWidth()==0||text.getHeight()==0||!text.isShown())continue;originals.putIfAbsent(text,new Style(text));text.getLocationOnScreen(at);
            int left=Math.max(0,Math.min(159,(at[0]-rootAt[0])*160/root.getWidth())),top=Math.max(0,Math.min(89,(at[1]-rootAt[1])*90/root.getHeight()));int right=Math.max(left+1,Math.min(160,(at[0]-rootAt[0]+text.getWidth())*160/root.getWidth())),bottom=Math.max(top+1,Math.min(90,(at[1]-rootAt[1]+text.getHeight())*90/root.getHeight()));
            Sample sample=samples.get(text);
            if(sample==null||!sample.matches(left,top,right,bottom,revision)){sample=new Sample(left,top,right,bottom,revision,darkText(grid.average(left,top,right,bottom)));samples.put(text,sample);}
            int color=sample.dark?0xff142027:Color.WHITE;Integer old=targets.put(text,color);
            if(old==null||old!=color){targetChanged=true;text.setShadowLayer(LauncherTheme.ios(root.getContext())?0:density*(sample.dark?1.5f:2.5f),0,density*.7f,sample.dark?0xccefffff:0xf2000000);}
            if(text.getCurrentTextColor()!=color){changed.add(text);from.add(text.getCurrentTextColor());to.add(color);}
        }
        if(changed.isEmpty()||!targetChanged&&animation!=null&&animation.isRunning())return;
        if(animation!=null)animation.cancel();
        if(LocalPreferences.get(root.getContext()).getBoolean("reduced_motion",false)){for(int n=0;n<changed.size();n++)changed.get(n).setTextColor(to.get(n));return;}
        ArgbEvaluator colors=new ArgbEvaluator();animation=ValueAnimator.ofFloat(0f,1f);animation.setDuration(300);animation.addUpdateListener(a->{float f=(Float)a.getAnimatedValue();for(int n=0;n<changed.size();n++)changed.get(n).setTextColor((Integer)colors.evaluate(f,from.get(n),to.get(n)));});animation.start();
    }
    private static void collect(View v,List<TextView> out){
        if(Boolean.TRUE.equals(v.getTag(R.id.contrast_protected))||v.getId()==R.id.card)return;
        if(v instanceof TextView){out.add((TextView)v);return;}
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int n=0;n<group.getChildCount();n++)collect(group.getChildAt(n),out);}
    }
    @Override public void close(){closed=true;pause();ViewTreeObserver tree=root.getViewTreeObserver();if(tree.isAlive()){tree.removeOnGlobalLayoutListener(layout);tree.removeOnScrollChangedListener(scroll);}grid=null;source=null;originals.clear();samples.clear();targets.clear();}
}
