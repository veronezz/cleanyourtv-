package com.gathin.tvhome;

import java.util.Locale;

final class MediaMath {
    static long position(long position,long updated,float speed,boolean playing,long now,long duration) {
        if(position<0)return -1;
        double value=position;
        if(playing && updated>0 && now>=updated && Float.isFinite(speed)) value+=(now-updated)*(double)speed;
        long result=(long)Math.max(0,Math.min(Long.MAX_VALUE,value));
        return duration>0?Math.min(result,duration):result;
    }
    static String time(long ms) {
        if(ms<0)return "--:--";
        long seconds=ms/1000,h=seconds/3600,m=seconds/60%60,s=seconds%60;
        return h>0?String.format(Locale.ROOT,"%d:%02d:%02d",h,m,s):String.format(Locale.ROOT,"%d:%02d",m,s);
    }
    static int progress(long position,long duration) {
        return duration>0&&position>=0?(int)Math.max(0,Math.min(1000,1000.0*position/duration)):0;
    }
}
