package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;

/** Fractional, frame-synchronised progress. Only the small bar redraws while playing. */
final class RoundedProgressView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect visibleRect=new Rect();
    private long position,duration,updated;
    private float speed;
    private boolean playing;
    RoundedProgressView(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void timeline(long position,long duration,long updated,float speed,boolean playing){
        this.position=position;this.duration=duration;this.updated=updated;this.speed=speed;this.playing=playing;
        invalidate();
    }
    void stop(){
        position=MediaMath.position(position,updated,speed,playing,SystemClock.elapsedRealtime(),duration);
        playing=false;
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float height=getHeight(),radius=height/2f;
        paint.setColor(0x45ffffff);canvas.drawRoundRect(0,0,getWidth(),height,radius,radius,paint);
        long value=MediaMath.position(position,updated,speed,playing,SystemClock.elapsedRealtime(),duration);
        float ratio=duration>0&&value>=0?Math.min(1f,Math.max(0f,(float)((double)value/duration))):0f;
        if(ratio>0){paint.setColor(0xffffffff);canvas.drawRoundRect(0,0,Math.max(height,getWidth()*ratio),height,radius,radius,paint);}
        if(playing&&duration>0&&value<duration&&getGlobalVisibleRect(visibleRect))postInvalidateOnAnimation();
    }
}
