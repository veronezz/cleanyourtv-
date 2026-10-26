package com.gathin.tvhome;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;

/** A single small texture: depth without a full-screen CPU gradient or a live blur. */
final class SoftBackground {
    static BitmapDrawable make(Resources resources, boolean aurora) {
        Bitmap bitmap=Bitmap.createBitmap(480,270,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(0xff070c16);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(105,45,330,new int[]{aurora?0xff203d60:0xff142334,0xff080e1a},null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,480,270,p);
        if(aurora){p.setShader(new RadialGradient(370,160,250,new int[]{0x60392c72,0x00392c72},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,480,270,p);}
        BitmapDrawable d=new BitmapDrawable(resources,bitmap);d.setGravity(Gravity.FILL);return d;
    }
}
