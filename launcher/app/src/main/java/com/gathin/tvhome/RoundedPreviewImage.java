package com.gathin.tvhome;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.widget.ImageView;
/** Explicit crop also works in software captures, independently of hardware outline clipping. */
final class RoundedPreviewImage extends ImageView {
    private final Path clip=new Path();
    RoundedPreviewImage(Context c){super(c);}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);clip.reset();float r=GlassUi.dp(getContext(),18);clip.addRoundRect(new RectF(0,0,w,h),r,r,Path.Direction.CW);}
    @Override protected void onDraw(Canvas canvas){int save=canvas.save();canvas.clipPath(clip);super.onDraw(canvas);canvas.restoreToCount(save);}
}
