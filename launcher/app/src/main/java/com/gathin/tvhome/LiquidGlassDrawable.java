package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Smoked translucent body, layered rim and a static specular sheen; no live CPU blur. */
final class LiquidGlassDrawable extends Drawable {
    private final Paint body=new Paint(Paint.ANTI_ALIAS_FLAG),rim=new Paint(Paint.ANTI_ALIAS_FLAG),sheen=new Paint(Paint.ANTI_ALIAS_FLAG),inner=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF area=new RectF();private final float density;private final boolean focus;private final int radius;
    LiquidGlassDrawable(float density,boolean focus,int radius){this.density=density;this.focus=focus;this.radius=radius;rim.setStyle(Paint.Style.STROKE);rim.setStrokeWidth(density);}
    @Override protected void onBoundsChange(Rect b){
        body.setShader(new LinearGradient(b.left,b.top,b.right,b.bottom,focus?new int[]{0xb34a7387,0xd523394d}:new int[]{0xc5313b4b,0xe0101826},null,Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(b.left,b.top,b.right,b.bottom,new int[]{focus?0xccedffff:0x80eff7ff,0x0affffff,focus?0x779bd6f0:0x30d5e9ff},new float[]{0,.55f,1},Shader.TileMode.CLAMP));
        sheen.setShader(new LinearGradient(0,b.top,0,b.top+(b.height()*.55f),focus?0x28ffffff:0x17ffffff,0x00ffffff,Shader.TileMode.CLAMP));
    }
    @Override public void draw(Canvas c){
        Rect b=getBounds();float inset=density,r=radius*density;
        area.set(b.left+inset,b.top+inset,b.right-inset,b.bottom-inset);c.drawRoundRect(area,r,r,body);
        c.save();c.clipRect(area.left,area.top,area.right,area.top+area.height()*.5f);c.drawRoundRect(area,r,r,sheen);c.restore();
        c.drawRoundRect(area,r,r,rim);
        // The inner rim gives a quiet lens edge, without a high-cost refractive shader.
        area.inset(density,density);inner.setStyle(Paint.Style.STROKE);inner.setStrokeWidth(density*.5f);inner.setColor(focus?0x48ffffff:0x13ffffff);c.drawRoundRect(area,r-density,r-density,inner);
    }
    @Override public void setAlpha(int alpha){body.setAlpha(alpha);rim.setAlpha(alpha);sheen.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){body.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
