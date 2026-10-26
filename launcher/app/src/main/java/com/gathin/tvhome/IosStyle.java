package com.gathin.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
/** Lightweight milky surfaces. No shaders, screen capture or per-frame blur on Home. */
final class IosStyle {
    static final int INK=0xff202733,SECONDARY=0xff596575,VEIL=0xb8f5f7fc;
    static int ink(Context c){return LauncherTheme.dark(c)?0xffeef3fa:INK;}
    static int veil(Context c){return LauncherTheme.dark(c)?0xb80c1421:VEIL;}
    static int text(Context c,int original){return LauncherTheme.ios(c)?(original==android.graphics.Color.WHITE||original==0xffeef6ff?ink(c):LauncherTheme.dark(c)?0xffb9c7d9:SECONDARY):original;}
    static GradientDrawable surface(Context c,boolean focus,float radius){return surface(c,focus,radius,LauncherTheme.dark(c));}
    static GradientDrawable surface(Context c,boolean focus,float radius,boolean dark){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{dark?(focus?0xf236465e:0xd5202c40):(focus?0xfaffffff:0xcceff3fa),dark?(focus?0xee24334a:0xc0121c2e):(focus?0xe8edf3fc:0xa8e0e7f1)});
        d.setCornerRadius(radius*c.getResources().getDisplayMetrics().density);d.setStroke(Math.max(1,GlassUi.dp(c,1)),dark?(focus?0xff9cbeeb:0x80819ab9):(focus?0xff7b9cc6:0xb8ffffff));return d;
    }
    static GradientDrawable background(Context c){return background(LauncherTheme.dark(c));}
    static GradientDrawable background(boolean dark){if(dark)return new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff172235,0xff0b1220,0xff202637});return new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xffedf0f7,0xffdbe3ef,0xfff0eaf2});}
}
