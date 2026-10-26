package com.gathin.tvhome;
import android.content.Context;
/** Physical tile size is independent of focus scale. Larger tiles reduce columns to avoid overlaps. */
final class TileSizing {
    static int percent(Context c){return Math.max(100,Math.min(150,LocalPreferences.get(c).getInt("icon_size_percent",100)));}
    static int width(Context c,int inner){float d=c.getResources().getDisplayMetrics().density;if(inner<=0)inner=c.getResources().getDisplayMetrics().widthPixels-Math.round(112*d);return width(inner,Config.load(c).columns,d,percent(c));}
    static int width(int inner,int columns,float density,int percent){
        int desired=Math.round(AppAdapter.tileWidthPx(inner,columns,density)*Math.max(100,Math.min(150,percent))/100f);
        return Math.min(desired,Math.max(1,(int)((inner-12*density)/AppAdapter.FOCUS_SCALE)));
    }
    static int columns(Context c,int requested){float d=c.getResources().getDisplayMetrics().density;int inner=c.getResources().getDisplayMetrics().widthPixels-Math.round(112*d);return columns(DockSpacing.contentWidth(inner,d),requested,d,percent(c));}
    static int columns(int inner,int requested,float density,int percent){if(percent<=100)return requested;int width=width(inner,requested,density,percent);return Math.max(1,Math.min(requested,(int)(inner/(width*AppAdapter.FOCUS_SCALE+12*density))));}
}
