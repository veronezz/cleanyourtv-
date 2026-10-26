package com.gathin.tvhome;

/** Pure navigation rules shared by the remote move mode and its JVM tests. */
final class AppOrder {
    static int target(int from,int key,int columns,int count,boolean rtl){
        int logical=rtl?(key==21?22:key==22?21:key):key;return target(from,logical,columns,count);
    }
    static int target(int from,int key,int columns,int count) {
        if(from<0||from>=count||columns<1) return from;
        switch(key) {
            case 21: return from%columns>0?from-1:from; // LEFT
            case 22: return from%columns<columns-1&&from+1<count?from+1:from; // RIGHT
            case 19: return from>=columns?from-columns:from; // UP
            case 20: return from+columns<count?from+columns:from; // DOWN
            default: return from;
        }
    }
}
