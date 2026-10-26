package com.gathin.tvhome;
final class RowOptions {
    static final String[] KEYS={DynamicRows.ALL,DynamicRows.WATCH,DynamicRows.UNITV,DynamicRows.LIVE,DynamicRows.MUSIC,DynamicRows.COBALT,DynamicRows.YOUTUBE};
    static boolean defaultValue(int index){return index!=0;}
    static int count(boolean[] values){int n=0;for(int i=1;i<values.length;i++)if(values[i])n++;return n;}
    static boolean recommendations(boolean[] values){return values[5]||values[6];}
}
