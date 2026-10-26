package com.gathin.tvhome;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,qualifiers="en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class WallhavenRuntimeTest {
    Context context;SharedPreferences prefs;WallhavenWallpaperEngine engine;FakeNetwork network;
    @Before public void setup() throws Exception {context=RuntimeEnvironment.getApplication();prefs=LocalPreferences.get(context);prefs.edit().clear().putString("background_style","wallhaven").putString("wallhaven_source","nature").putInt("wallhaven_interval",1).commit();File dir=new File(context.getFilesDir(),"wallhaven-wallpapers");if(dir.isDirectory())for(File f:dir.listFiles())f.delete();network=new FakeNetwork();engine=new WallhavenWallpaperEngine(context,image->{},network);}
    @After public void close(){engine.close();}
    private void idleWorker() throws Exception {Shadows.shadowOf(Looper.getMainLooper()).idle();java.lang.reflect.Field f=WallhavenWallpaperEngine.class.getDeclaredField("worker");f.setAccessible(true);((ExecutorService)f.get(engine)).submit(()->{}).get(15,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();}
    private int countFiles(){File[] f=new File(context.getFilesDir(),"wallhaven-wallpapers").listFiles();return f==null?0:f.length;}
    @Test public void downloadsOnlyCurrentAndNextAndRotatesWithinTwoSlots() throws Exception {engine.start();idleWorker();assertEquals(1,network.api);assertEquals(2,network.images);assertEquals(2,countFiles());engine.stop();prefs.edit().putLong("wallhaven_current_since",System.currentTimeMillis()-120_000).commit();engine.start();idleWorker();assertEquals(1,prefs.getInt("wallhaven_current_slot",0));assertEquals(3,network.images);assertEquals(2,countFiles());assertEquals(1,network.api);}
    @Test public void manualSkipWaitsForHomeCoalescesClicksAndResetsTheTimer() throws Exception {
        engine.start();idleWorker();engine.stop();long before=prefs.getLong("wallhaven_current_since",0);
        WallpaperSettings.requestNext(context);WallpaperSettings.requestNext(context);
        assertEquals(1,prefs.getLong(WallpaperSettings.NEXT_REQUEST,0));assertEquals(2,network.images);
        engine.start();idleWorker();assertEquals(1,prefs.getInt("wallhaven_current_slot",0));assertEquals(1,prefs.getLong(WallpaperSettings.NEXT_APPLIED,0));assertTrue(prefs.getLong("wallhaven_current_since",0)>=before);assertEquals(3,network.images);assertEquals(2,countFiles());
        engine.stop();engine.start();idleWorker();assertEquals(1,prefs.getInt("wallhaven_current_slot",0));assertEquals(3,network.images);
    }
    @Test public void manualSkipUsesCachedNextEvenIfPrefetchIsOffline() throws Exception {
        engine.start();idleWorker();engine.stop();network.failImages=true;WallpaperSettings.requestNext(context);
        engine.start();idleWorker();assertEquals(1,prefs.getInt("wallhaven_current_slot",0));assertEquals(1,prefs.getLong(WallpaperSettings.NEXT_APPLIED,0));assertEquals(1,countFiles());int attempts=network.images;
        engine.stop();engine.start();idleWorker();assertEquals(attempts,network.images);assertEquals(1,prefs.getInt("wallhaven_current_slot",0));
    }
    @Test public void manualRequestBeforeFirstDownloadCountsTheFirstNewImage() throws Exception {
        WallpaperSettings.requestNext(context);engine.start();idleWorker();assertEquals(1,prefs.getLong(WallpaperSettings.NEXT_APPLIED,0));assertEquals(2,network.images);assertEquals(2,countFiles());
    }
    @Test public void manualSkipDoesNothingForAnInactiveWallpaperSource(){
        prefs.edit().putString("background_style","dark").commit();WallpaperSettings.requestNext(context);assertEquals(0,prefs.getLong(WallpaperSettings.NEXT_REQUEST,0));assertEquals(0,network.api);assertEquals(0,network.images);
    }
    @Test public void metadataSurvivesProviderRestartWithoutAnotherApiCall() throws Exception {engine.start();idleWorker();engine.close();engine=new WallhavenWallpaperEngine(context,image->{},network);engine.start();idleWorker();assertEquals(1,network.api);assertEquals(2,network.images);}
    @Test public void stoppedOrUnselectedProviderMakesNoNetworkRequests() throws Exception {engine.start();engine.stop();idleWorker();assertEquals(0,network.api);prefs.edit().putString("background_style","dark").commit();engine.start();idleWorker();assertEquals(0,network.api);}
    @Test public void networkFailureKeepsCurrentImageAndLeavesNoTemporaryFiles() throws Exception {engine.start();idleWorker();engine.stop();File current=new File(context.getFilesDir(),"wallhaven-wallpapers/wall-0.jpg");long length=current.length();new File(context.getFilesDir(),"wallhaven-wallpapers/wall-1.jpg").delete();network.failImages=true;engine.start();idleWorker();assertTrue(current.isFile());assertEquals(length,current.length());assertEquals(1,countFiles());}
    @Test public void unavailableNewSourceKeepsTheExistingCachedImage() throws Exception {engine.start();idleWorker();engine.stop();int index=prefs.getInt("wallhaven_current_slot",0);File current=new File(context.getFilesDir(),"wallhaven-wallpapers/wall-"+index+".jpg");long length=current.length();prefs.edit().putString("wallhaven_source","space").commit();network.apiCode=503;engine.start();idleWorker();assertTrue(current.isFile());assertEquals(length,current.length());assertEquals(2,countFiles());}
    @Test public void rateLimitHonorsRetryAfterWithoutRetryingOnEachResume() throws Exception {network.apiCode=429;engine.start();idleWorker();engine.stop();engine.start();idleWorker();assertEquals(1,network.api);assertEquals("http:429",prefs.getString("wallhaven_status",""));}
    @Test public void defaultImageFitStretchesAndOptionalFitPreservesAspect(){assertEquals(ImageView.ScaleType.FIT_XY,WallpaperSettings.scale(context));prefs.edit().putString("wallpaper_fit","crop").commit();assertEquals(ImageView.ScaleType.CENTER_CROP,WallpaperSettings.scale(context));prefs.edit().putString("wallpaper_fit","contain").commit();assertEquals(ImageView.ScaleType.FIT_CENTER,WallpaperSettings.scale(context));}
    @Test public void legacySelectionMigratesToWallhavenAndKeepsOtherChoices(){prefs.edit().putString("background_style","reddit").putInt("reddit_interval",31).putString("app_language","es").putString("launcher_theme","smoked").commit();WallpaperSettings.migrate(context);assertEquals("wallhaven",prefs.getString("background_style",""));assertEquals(31,prefs.getInt("wallhaven_interval",0));assertEquals("es",prefs.getString("app_language",""));assertEquals("smoked",prefs.getString("launcher_theme",""));}
    @Test public void textContrastAdaptsToBrightAndDarkWallpapersAndProtectsCards(){
        try(org.robolectric.android.controller.ActivityController<android.app.Activity> activity=Robolectric.buildActivity(android.app.Activity.class).setup().visible()){
        FrameLayout root=new FrameLayout(activity.get());activity.get().setContentView(root);root.setBackgroundColor(Color.BLACK);TextView text=new TextView(context);text.setText("Clock and weather");text.setTextColor(Color.WHITE);root.addView(text,new FrameLayout.LayoutParams(400,100));TextView protectedText=new TextView(context);protectedText.setText("Retomar");protectedText.setTextColor(Color.WHITE);protectedText.setTag(R.id.contrast_protected,true);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(200,100);lp.topMargin=150;root.addView(protectedText,lp);
        root.measure(View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(540,View.MeasureSpec.EXACTLY));root.layout(0,0,960,540);BackdropContrast contrast=new BackdropContrast(root);
        try{Bitmap bright=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);bright.eraseColor(Color.WHITE);contrast.source(bright,ImageView.ScaleType.FIT_XY);Shadows.shadowOf(Looper.getMainLooper()).idleFor(600,TimeUnit.MILLISECONDS);assertEquals(0xff142027,text.getCurrentTextColor());assertEquals(Color.WHITE,protectedText.getCurrentTextColor());assertTrue(text.getShadowRadius()>0);Bitmap dark=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);dark.eraseColor(Color.BLACK);contrast.source(dark,ImageView.ScaleType.FIT_XY);Shadows.shadowOf(Looper.getMainLooper()).idleFor(600,TimeUnit.MILLISECONDS);assertEquals(Color.WHITE,text.getCurrentTextColor());prefs.edit().putBoolean("adaptive_contrast",false).commit();contrast.schedule();Shadows.shadowOf(Looper.getMainLooper()).idleFor(600,TimeUnit.MILLISECONDS);assertEquals(0f,text.getShadowRadius(),.001f);}finally{contrast.close();}
        }
    }
    @Test public void stoppingCancelsAnInFlightDownloadAndDoesNotStartTheNextImage() throws Exception {
        network.blockImage=true;engine.start();Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(network.entered.await(5,TimeUnit.SECONDS));engine.stop();idleWorker();assertEquals(1,network.images);assertEquals(0,countFiles());assertTrue(network.disconnected);
    }
    private static final class FakeNetwork implements WallhavenWallpaperEngine.Transport {
        int api,images,apiCode=200;boolean failImages;volatile boolean blockImage,disconnected;final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);byte[] png;String listing;
        FakeNetwork() throws Exception {Bitmap image=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);image.eraseColor(0xff8090b0);ByteArrayOutputStream out=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.PNG,100,out);image.recycle();png=out.toByteArray();JSONArray data=new JSONArray();for(String id:new String[]{"aa0001","bb0002","cc0003"})data.put(new JSONObject().put("id",id).put("path","https://w.wallhaven.cc/full/"+id.substring(0,2)+"/wallhaven-"+id+".jpg").put("url","https://wallhaven.cc/w/"+id).put("purity","sfw").put("dimension_x",1920).put("dimension_y",1080));listing=new JSONObject().put("data",data).toString();}
        public HttpURLConnection open(String url) throws Exception {boolean isImage=url.startsWith("https://w.wallhaven.cc/");if(isImage){images++;if(failImages)throw new IOException("offline");}else api++;byte[] bytes=isImage?png:listing.getBytes("UTF-8");return new HttpURLConnection(new URL(url)){public void connect(){}public void disconnect(){if(isImage){disconnected=true;release.countDown();}}public boolean usingProxy(){return false;}public int getResponseCode(){return isImage?200:apiCode;}public String getContentType(){return isImage?"image/png":"application/json";}public InputStream getInputStream(){if(isImage&&blockImage)return new InputStream(){public int read() throws IOException {entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){throw new InterruptedIOException();}throw new InterruptedIOException();}};return new ByteArrayInputStream(bytes);}public String getHeaderField(String key){return "Retry-After".equals(key)?"900":null;}};}
    }
}
