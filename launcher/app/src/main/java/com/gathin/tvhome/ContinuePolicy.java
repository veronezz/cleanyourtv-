package com.gathin.tvhome;
import java.util.regex.*;
final class ContinuePolicy {
    static final long FIVE_MINUTES=300_000L;
    static final class Removed {
        final long after;final int available;
        Removed(long after,int available){this.after=after;this.available=available;}
    }
    static boolean nearEnd(long position,long duration) {
        if(position<=0||duration<=0)return false;
        if(position>=duration)return true;
        return duration>FIVE_MINUTES&&duration-position<=FIVE_MINUTES;
    }
    static boolean newPlayback(Removed hidden,long engagement){return hidden!=null&&engagement>0&&engagement>hidden.after;}
    static boolean newEpisodes(Removed hidden,int available){return hidden!=null&&available>=0&&available>Math.max(0,hidden.available);}
    // Metadata protocol in UniTV v12: the last number in followed/update detail is the available count.
    static int available(String detail) {
        int value=-1;Matcher m=Pattern.compile("[0-9]+").matcher(detail);
        while(m.find())try{value=Integer.parseInt(m.group());}catch(NumberFormatException ignored){}
        return value;
    }
}
