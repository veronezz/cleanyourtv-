package com.gathin.tvhome;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.View;
import java.lang.ref.WeakReference;

/** Backdrop blur is confined to the glass silhouette; all text remains on top, unblurred. */
final class SmokedGlassDrawable extends Drawable {
    private final WeakReference<View> host;private final GlassScene scene;private final boolean focus;
    private final float density,radiusDp;private final RectF area=new RectF();private final Path mask=new Path();
    private final Paint tint=new Paint(Paint.ANTI_ALIAS_FLAG),rim=new Paint(Paint.ANTI_ALIAS_FLAG),shade=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int alpha=255;
    SmokedGlassDrawable(View host,GlassScene scene,boolean focus){this(host,scene,focus,18);}
    SmokedGlassDrawable(View host,GlassScene scene,boolean focus,float radiusDp){this.radiusDp=radiusDp;this.host=new WeakReference<>(host);this.scene=scene;this.focus=focus;density=host==null?1:host.getResources().getDisplayMetrics().density;rim.setStyle(Paint.Style.STROKE);rim.setStrokeWidth(density);shade.setStyle(Paint.Style.STROKE);shade.setStrokeWidth(density*2);if(scene!=null)scene.observe(this);}
    @Override protected void onBoundsChange(Rect b){
        area.set(b.left+density,b.top+density,b.right-density,b.bottom-density);float radius=Math.min(radiusDp*density,Math.min(area.width(),area.height())/2);
        mask.reset();mask.addRoundRect(area,radius,radius,Path.Direction.CW);
        tint.setShader(new LinearGradient(b.left,b.top,b.right,b.bottom,focus?new int[]{0x80435458,0xa51c282a}:new int[]{0x64313b3c,0x96101b1d},null,Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(b.left,b.top,b.right,b.bottom,new int[]{focus?0xb3efffff:0x70efffff,0x08ffffff,focus?0x85d3e6ea:0x38cbdcde},new float[]{0,.58f,1},Shader.TileMode.CLAMP));
    }
    @Override public void draw(Canvas c){
        float radius=Math.min(radiusDp*density,Math.min(area.width(),area.height())/2);
        c.save();c.clipPath(mask);if(scene!=null)scene.draw(c,host.get());tint.setAlpha(alpha);c.drawRoundRect(area,radius,radius,tint);c.restore();
        shade.setColor(0x26000000);c.drawRoundRect(area,radius,radius,shade);rim.setAlpha(alpha);c.drawRoundRect(area,radius,radius,rim);
    }
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter value){tint.setColorFilter(value);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
