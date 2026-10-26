package com.gathin.tvhome;
import android.app.Activity;
import android.content.*;
import android.database.*;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=34,qualifiers="pt-rBR-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class PerformanceRuntimeTest {
    Context context;byte[] png;
    @Before public void setup(){
        context=RuntimeEnvironment.getApplication();LocalPreferences.get(context).edit().clear().commit();
        File folder=new File(context.getCacheDir(),"media-thumbnails");File[] files=folder.listFiles();if(files!=null)for(File f:files)f.delete();
        Bitmap b=Bitmap.createBitmap(24,14,Bitmap.Config.ARGB_8888);b.eraseColor(Color.RED);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);png=out.toByteArray();b.recycle();
    }
    final class Network implements MediaArtwork.Transport {
        final AtomicInteger calls=new AtomicInteger();final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);volatile boolean blocked=true,disconnected;
        public HttpURLConnection open(String url)throws Exception{calls.incrementAndGet();return new HttpURLConnection(new URL(url)){
            public void connect(){}public boolean usingProxy(){return false;}
            public int getResponseCode(){entered.countDown();return 200;}
            public InputStream getInputStream()throws IOException{if(blocked)try{if(!release.await(5,TimeUnit.SECONDS))throw new IOException("timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException();}if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();return new ByteArrayInputStream(png);}
            public void disconnect(){disconnected=true;release.countDown();}
        };}
    }
    private static void delivered(CountDownLatch done)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(7);while(done.getCount()>0&&System.nanoTime()<until){Shadows.shadowOf(Looper.getMainLooper()).idle();Thread.sleep(10);}assertEquals("image delivery",0,done.getCount());}
    @Test public void simultaneousViewsShareOneDownloadAndBitmap()throws Exception{
        Network network=new Network();MediaArtwork artwork=new MediaArtwork(network);ImageView a=new ImageView(context),b=new ImageView(context);CountDownLatch done=new CountDownLatch(2);
        try{artwork.load(context,"https://example.test/shared",a,image->done.countDown());artwork.load(context,"https://example.test/shared",b,image->done.countDown());assertTrue(network.entered.await(3,TimeUnit.SECONDS));assertEquals(1,network.calls.get());network.release.countDown();delivered(done);assertSame(((BitmapDrawable)a.getDrawable()).getBitmap(),((BitmapDrawable)b.getDrawable()).getBitmap());}finally{artwork.close();}
    }
    @Test public void cancellingOneSubscriberKeepsTheOtherDownload()throws Exception{
        Network network=new Network();MediaArtwork artwork=new MediaArtwork(network);ImageView a=new ImageView(context),b=new ImageView(context);CountDownLatch done=new CountDownLatch(1);AtomicInteger wrong=new AtomicInteger();
        try{artwork.load(context,"https://example.test/shared",a,image->wrong.incrementAndGet());artwork.load(context,"https://example.test/shared",b,image->done.countDown());assertTrue(network.entered.await(3,TimeUnit.SECONDS));artwork.cancel(a);network.release.countDown();delivered(done);assertEquals(0,wrong.get());assertEquals(1,network.calls.get());assertNull(a.getDrawable());}finally{artwork.close();}
    }
    @Test public void leavingHomeDisconnectsAndResumeRestartsPendingView()throws Exception{
        Network network=new Network();MediaArtwork artwork=new MediaArtwork(network);ImageView image=new ImageView(context);CountDownLatch done=new CountDownLatch(1);
        try{artwork.load(context,"https://example.test/resume",image,b->done.countDown());assertTrue(network.entered.await(3,TimeUnit.SECONDS));artwork.pause();assertTrue(network.disconnected);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(1,done.getCount());network.blocked=false;artwork.resume();delivered(done);assertEquals(2,network.calls.get());}finally{artwork.close();}
    }
    @Test public void anotherLoaderUsesDiskWithoutDownloadingAgain()throws Exception{
        Network network=new Network();network.blocked=false;MediaArtwork first=new MediaArtwork(network);CountDownLatch a=new CountDownLatch(1);ImageView firstView=new ImageView(context),secondView=new ImageView(context);
        try{first.load(context,"https://example.test/persisted",firstView,b->a.countDown());delivered(a);}finally{first.close();}
        MediaArtwork second=new MediaArtwork(network);CountDownLatch b=new CountDownLatch(1);
        try{second.load(context,"https://example.test/persisted",secondView,image->b.countDown());delivered(b);assertEquals(1,network.calls.get());}finally{second.close();}
    }
    @Test public void closingLoaderNeverAppliesLateImage()throws Exception{
        Network network=new Network();MediaArtwork artwork=new MediaArtwork(network);AtomicInteger applied=new AtomicInteger();ImageView image=new ImageView(context);
        artwork.load(context,"https://example.test/closed",image,b->applied.incrementAndGet());assertTrue(network.entered.await(3,TimeUnit.SECONDS));artwork.close();
        java.lang.reflect.Field f=MediaArtwork.class.getDeclaredField("worker");f.setAccessible(true);assertTrue(((ExecutorService)f.get(artwork)).awaitTermination(3,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(0,applied.get());assertNull(image.getDrawable());
    }
    @Test public void cancelledRepositoryDoesNotQueryProviders(){android.os.CancellationSignal signal=new android.os.CancellationSignal();signal.cancel();try{TvContentRepository.read(context,signal);fail();}catch(android.os.OperationCanceledException expected){}}
    public static class FeedProvider extends ContentProvider {
        final AtomicInteger reads=new AtomicInteger();final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),again=new CountDownLatch(1);
        public boolean onCreate(){return true;}
        public Cursor query(Uri uri,String[] columns,String selection,String[] args,String sort){int count=reads.incrementAndGet();if(count==1){entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new OperationCanceledException();}}else again.countDown();return new MatrixCursor(columns);}
        public String getType(Uri u){return null;}public Uri insert(Uri u,ContentValues v){throw new AssertionError();}public int delete(Uri u,String s,String[] a){throw new AssertionError();}public int update(Uri u,ContentValues v,String s,String[] a){throw new AssertionError();}
    }
    @Test public void burstsOfRefreshesProduceOneFollowupRead()throws Exception{
        FeedProvider provider=new FeedProvider();org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("android.media.tv",provider);
        LocalPreferences.get(context).edit().putBoolean(DynamicRows.ALL,true).putBoolean(DynamicRows.LIVE,false).putBoolean(DynamicRows.MUSIC,false).putBoolean(RecommendationChannels.ENABLED,false).commit();
        try(ActivityController<Activity> activity=Robolectric.buildActivity(Activity.class).setup().visible()){
            LinearLayout root=new LinearLayout(activity.get());HomeMediaPanel panel=new HomeMediaPanel(activity.get(),root,new HomeMediaPanel.Host(){public void musicBackground(Bitmap b){}public void feedsChanged(){}public void focusApps(){}});
            try{panel.start();assertTrue(provider.entered.await(3,TimeUnit.SECONDS));java.lang.reflect.Method load=HomeMediaPanel.class.getDeclaredMethod("loadFeeds");load.setAccessible(true);for(int n=0;n<20;n++)load.invoke(panel);assertEquals(1,provider.reads.get());provider.release.countDown();java.lang.reflect.Field f=HomeMediaPanel.class.getDeclaredField("io");f.setAccessible(true);((ExecutorService)f.get(panel)).submit(()->{}).get(5,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();assertTrue(provider.again.await(3,TimeUnit.SECONDS));((ExecutorService)f.get(panel)).submit(()->{}).get(5,TimeUnit.SECONDS);Shadows.shadowOf(Looper.getMainLooper()).idle();assertEquals(2,provider.reads.get());}finally{provider.release.countDown();panel.close();}
        }
    }
    @Test public void trimmingPreviewKeepsAlreadyDisplayedBitmapValid(){Bitmap before=HomePreview.snapshot();ImageView image=new ImageView(context);image.setImageBitmap(before);HomePreview.trim();assertFalse(before.isRecycled());assertSame(before,((BitmapDrawable)image.getDrawable()).getBitmap());assertNotSame(before,HomePreview.snapshot());}
}
