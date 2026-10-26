package com.gathin.tvhome;
import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
/** Optional static Google icons; forecast data continues to come from Open-Meteo. */
final class GoogleWeatherIcon implements AutoCloseable {
    private final Activity activity;private final TextView text;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile HttpURLConnection connection;private Future<?> task;private volatile int generation;private boolean closed;
    private String requested="",shown="";private long retry;
    GoogleWeatherIcon(Activity a,TextView t){activity=a;text=t;text.setCompoundDrawablePadding(GlassUi.dp(a,7));}
    static String name(int code,boolean day){
        if(code==0)return day?"sunny":"clear";
        if(code==1)return day?"mostly_sunny":"mostly_clear";
        if(code==2)return day?"partly_cloudy":"partly_clear";
        if(code==3||code==45||code==48)return "cloudy";
        if(code>=51&&code<=57)return "drizzle";
        if(code>=61&&code<=67||code>=80&&code<=82)return code==65||code==82?"heavy":"showers";
        if(code>=71&&code<=77||code==85||code==86)return code==86?"heavy_snow":"snow_showers";
        if(code>=95&&code<=99)return "strong_tstorms";
        return "";
    }
    void update(){
        if(closed)return;SharedPreferences p=LocalPreferences.get(activity);long age=System.currentTimeMillis()-p.getLong("weather_updated",0);
        if(!p.getBoolean("weather_google_icons",false)||!p.getBoolean("weather_enabled",true)||!p.contains("weather_code")||age<0||age>WeatherData.MAX_AGE_MS||!WeatherData.coordinateKey(p).equals(p.getString("weather_cache_key",""))){stop();text.setCompoundDrawables(null,null,null,null);shown="";return;}
        final String key=name(p.getInt("weather_code",-1),p.getBoolean("weather_is_day",true));
        if(key.isEmpty()){stop();text.setCompoundDrawables(null,null,null,null);shown="";return;}
        if(key.equals(shown)||key.equals(requested)&&System.currentTimeMillis()<retry)return;
        stop();requested=key;retry=System.currentTimeMillis()+5*60_000L;final int ticket=generation;
        task=worker.submit(()->{
            try{
                File cache=new File(activity.getCacheDir(),"google-weather.png");Bitmap image=null;
                if(key.equals(p.getString("weather_icon_cached","")))image=BitmapFactory.decodeFile(cache.getPath());
                if(image==null){
                    HttpURLConnection c=(HttpURLConnection)new URL("https://maps.gstatic.com/weather/v1/"+key+".png").openConnection();connection=c;
                    c.setConnectTimeout(5000);c.setReadTimeout(5000);c.setInstanceFollowRedirects(false);
                    byte[] bytes;try{if(c.getResponseCode()!=200)throw new IOException();try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(ticket!=generation||out.size()+n>256*1024)throw new IOException();out.write(b,0,n);}bytes=out.toByteArray();}}finally{c.disconnect();connection=null;}
                    BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                    if(bounds.outWidth<1||bounds.outHeight<1||bounds.outWidth>2048||bounds.outHeight>2048)throw new IOException();
                    image=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(image==null)throw new IOException();
                    if(ticket!=generation)return;
                    File temp=new File(activity.getCacheDir(),"google-weather.pending");try(FileOutputStream out=new FileOutputStream(temp)){out.write(bytes);}if(temp.renameTo(cache))p.edit().putString("weather_icon_cached",key).apply();else temp.delete();
                }
                final Bitmap ready=image;main.post(()->{if(closed||ticket!=generation||activity.isDestroyed())return;BitmapDrawable icon=new BitmapDrawable(activity.getResources(),ready);int size=GlassUi.dp(activity,24);icon.setBounds(0,0,size,size);text.setCompoundDrawables(icon,null,null,null);shown=key;});
            }catch(Exception|OutOfMemoryError ignored){}
        });
    }
    void stop(){generation++;requested="";if(task!=null)task.cancel(true);HttpURLConnection c=connection;if(c!=null)c.disconnect();}
    @Override public void close(){closed=true;stop();worker.shutdownNow();main.removeCallbacksAndMessages(null);}
}
