package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.ImageView;

final class WallpaperSettings {
    static final String NEXT_REQUEST="wallhaven_next_request",NEXT_APPLIED="wallhaven_next_applied";
    static void requestNext(Context c){
        SharedPreferences p=LocalPreferences.get(c);
        if(!"wallhaven".equals(p.getString("background_style","")))return;
        // Coalesce repeated clicks while Home is paused; no network work in Preferences.
        long applied=p.getLong(NEXT_APPLIED,0);
        if(p.getLong(NEXT_REQUEST,0)>applied)return;
        LocalPreferences.save(c,p.edit().putLong(NEXT_REQUEST,applied+1));
    }
    static WallhavenSource source(Context c){SharedPreferences p=LocalPreferences.get(c);return new WallhavenSource(p.getString("wallhaven_source","nature"),p.getString("wallhaven_quality","fhd"),p.getBoolean("wallhaven_widescreen",true));}
    static ImageView.ScaleType scale(Context c){String value=LocalPreferences.get(c).getString("wallpaper_fit","stretch");return "crop".equals(value)?ImageView.ScaleType.CENTER_CROP:"contain".equals(value)?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.FIT_XY;}
    static void migrate(Context c){
        SharedPreferences p=LocalPreferences.get(c);if(p.getBoolean("wallhaven_migrated",false))return;
        SharedPreferences.Editor e=p.edit().putBoolean("wallhaven_migrated",true);
        if("reddit".equals(p.getString("background_style","")))e.putString("background_style","wallhaven").putString("wallhaven_source","nature").putInt("wallhaven_interval",p.getInt("reddit_interval",15));
        LocalPreferences.save(c,e);
        Context app=c.getApplicationContext();new Thread(()->{java.io.File old=new java.io.File(app.getFilesDir(),"reddit-wallpapers");java.io.File[] files=old.listFiles();if(files!=null)for(java.io.File f:files)if(f.isFile())f.delete();old.delete();},"wallpaper-migration").start();
    }
}
