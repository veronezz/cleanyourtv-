package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.LocaleList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class LanguagePerformanceTest {
    Context context;
    @Before public void reset(){context=org.robolectric.RuntimeEnvironment.getApplication();LocalPreferences.get(context).edit().clear().commit();}
    private Context locale(String tag){Configuration c=new Configuration(context.getResources().getConfiguration());c.setLocales(LocaleList.forLanguageTags(tag));return context.createConfigurationContext(c);}
    @Test public void followsSystemAndFallsBackToEnglish(){
        assertEquals("",AppLanguage.choice(context));
        assertEquals("Seguir o sistema",AppLanguage.wrap(locale("pt-BR")).getString(R.string.language_system));
        assertEquals("Seguir el sistema",AppLanguage.wrap(locale("es-MX")).getString(R.string.language_system));
        assertEquals("Follow system",AppLanguage.wrap(locale("ja-JP")).getString(R.string.language_system));
        assertEquals("Seguir o sistema",AppLanguage.wrap(locale("pt-PT")).getString(R.string.language_system));
    }
    @Test public void manualChoicePersistsAndResetUsesTheSystemAgain(){
        Context portuguese=locale("pt-BR");AppLanguage.select(portuguese,"es");
        assertEquals("es",AppLanguage.choice(portuguese));assertEquals("Seguir el sistema",AppLanguage.wrap(portuguese).getString(R.string.language_system));
        AppLanguage.select(portuguese,"");assertEquals("Seguir o sistema",AppLanguage.wrap(portuguese).getString(R.string.language_system));
        try{AppLanguage.select(context,"bad");fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void preferencesActuallyUseEnglishAndSpanishResources(){
        for(String tag:new String[]{"en","es","pt-BR"}){
            AppLanguage.select(context,tag);
            try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
                View root=c.get().getWindow().getDecorView();layout(root,960,540);
                root.findViewWithTag("category:system").performClick();
                assertNotNull(PreferencesVisualTest.find(root,c.get().getString(R.string.language_title)));
                root.findViewWithTag("category:home").performClick();
                String expected=tag.equals("en")?"Grid and dock":tag.equals("es")?"Cuadrícula y dock":"Grade e dock";
                assertNotNull(PreferencesVisualTest.find(root,expected));
                Bitmap shot=Bitmap.createBitmap(960,540,Bitmap.Config.ARGB_8888);root.draw(new Canvas(shot));
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(System.getProperty("tvhome.qa.output"),"preferences-"+tag+".png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}catch(java.io.IOException error){throw new AssertionError(error);}shot.recycle();
            }
        }
    }
    @Test @Config(sdk=34) public void frameworkLanguageChoiceAndResetAreSynchronized(){
        AppLanguage.select(context,"es");assertEquals("es",context.getSystemService(android.app.LocaleManager.class).getApplicationLocales().toLanguageTags());assertEquals("es",AppLanguage.choice(context));
        AppLanguage.select(context,"");assertTrue(context.getSystemService(android.app.LocaleManager.class).getApplicationLocales().isEmpty());assertEquals("",AppLanguage.choice(context));
    }
    @Test public void mediaRowRecyclesOffscreenCardsAndPreservesFocusedHolderOnProgressUpdate(){
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            MediaArtwork images=new MediaArtwork();try{
                TextView heading=new TextView(c.get());MediaRow row=new MediaRow(c.get(),heading,images,i->{});
                List<TvContentRepository.Item> items=new ArrayList<>();for(int n=0;n<48;n++)items.add(item(n,1000));row.submit(items);
                c.get().setContentView(row.view);layout(row.view,800,row.rowHeight());
                assertTrue("Only visible cards should be attached",row.view.getChildCount()>0&&row.view.getChildCount()<12);
                MediaRow.Holder before=(MediaRow.Holder)row.view.findViewHolderForAdapterPosition(0);assertNotNull(before);assertTrue("Card should accept remote focus",before.card.requestFocusFromTouch());
                Object request=before.image.getTag();items.set(0,item(0,9000));row.submit(items);layout(row.view,800,row.rowHeight());
                MediaRow.Holder after=(MediaRow.Holder)row.view.findViewHolderForAdapterPosition(0);assertSame(before,after);assertTrue(after.card.hasFocus());assertEquals(request,after.image.getTag());assertTrue(after.resume.getText().toString().contains("0:09"));assertEquals("Show 0",heading.getText().toString());
            }finally{images.close();}
        }
    }
    @Test public void harmlessPreferenceEditKeepsTheHomeViewAndHomeIntentKeepsItsInstance() throws Exception {
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            MainActivity activity=c.get();View root=activity.findViewById(R.id.root);
            c.pause();LocalPreferences.save(activity,LocalPreferences.get(activity).edit().putString("project_github","https://github.com/example/launcher"));c.resume();
            assertFalse(activity.isChangingConfigurations());assertSame(root,activity.findViewById(R.id.root));
            android.content.Intent home=new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_HOME);c.newIntent(home);assertSame(root,activity.findViewById(R.id.root));
        }
    }
    @Test public void optimizedBlurMatchesThePreviousPixelsExactly(){
        Bitmap source=Bitmap.createBitmap(13,9,Bitmap.Config.ARGB_8888);Random random=new Random(12);for(int y=0;y<9;y++)for(int x=0;x<13;x++)source.setPixel(x,y,random.nextInt()|0xff000000);
        Bitmap optimized=MediaArtwork.blur(source);Bitmap expected=referenceBlur(source);
        assertTrue("The optimization must preserve the requested blur appearance",optimized.sameAs(expected));source.recycle();optimized.recycle();expected.recycle();
    }
    @Test @Config(sdk=34) public void legacyAdbPortugueseOverrideIsRetiredOnlyOnce(){
        android.app.LocaleManager manager=context.getSystemService(android.app.LocaleManager.class);
        manager.setApplicationLocales(LocaleList.forLanguageTags("pt-BR"));AppLanguage.initialize(context);assertEquals("",AppLanguage.choice(context));
        manager.setApplicationLocales(LocaleList.forLanguageTags("pt-BR"));AppLanguage.initialize(context);assertEquals("pt-BR",AppLanguage.choice(context));
    }
    @Test public void customAppArtworkIsCachedAndInvalidatedAfterAnEdit() throws Exception {
        android.content.pm.ResolveInfo ri=new android.content.pm.ResolveInfo();ri.activityInfo=new android.content.pm.ActivityInfo();ri.activityInfo.name="test.Main";ri.activityInfo.packageName="test.cache";ri.activityInfo.applicationInfo=new android.content.pm.ApplicationInfo();ri.activityInfo.applicationInfo.packageName="test.cache";
        java.lang.reflect.Method m=AppAppearance.class.getDeclaredMethod("file",Context.class,String.class);m.setAccessible(true);java.io.File file=(java.io.File)m.invoke(null,context,"test.cache");
        Bitmap image=Bitmap.createBitmap(512,288,Bitmap.Config.ARGB_8888);image.eraseColor(0xff00aa88);try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();AppArtwork.invalidate("test.cache");
        android.graphics.drawable.BitmapDrawable a=(android.graphics.drawable.BitmapDrawable)AppArtwork.load(context,ri,true),b=(android.graphics.drawable.BitmapDrawable)AppArtwork.load(context,ri,true);assertNotNull(a);assertSame(a.getBitmap(),b.getBitmap());
        AppArtwork.invalidate("test.cache");android.graphics.drawable.BitmapDrawable changed=(android.graphics.drawable.BitmapDrawable)AppArtwork.load(context,ri,true);assertNotSame(a.getBitmap(),changed.getBitmap());assertEquals(0xff00aa88,changed.getBitmap().getPixel(10,10));
        file.delete();AppArtwork.invalidate("test.cache");
    }
    @Test public void appEnumerationPublishesOnTheMainThreadAndMoveDoesNotResetTheAdapter() throws Exception {
        android.content.Intent probe=new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER);
        for(String pkg:new String[]{"test.first","test.second"}){
            android.content.pm.ResolveInfo ri=new android.content.pm.ResolveInfo();ri.activityInfo=new android.content.pm.ActivityInfo();ri.activityInfo.name=pkg+".Main";ri.activityInfo.packageName=pkg;ri.activityInfo.applicationInfo=new android.content.pm.ApplicationInfo();ri.activityInfo.applicationInfo.packageName=pkg;ri.nonLocalizedLabel=pkg;
            org.robolectric.Shadows.shadowOf(context.getPackageManager()).addResolveInfoForIntent(probe,ri);
        }
        AppAdapter adapter=new AppAdapter(context,null,6,com.gathin.tvhome.Config.load(context));try{
            final boolean[] applied={false},reset={false};adapter.registerAdapterDataObserver(new androidx.recyclerview.widget.RecyclerView.AdapterDataObserver(){@Override public void onChanged(){reset[0]=true;}});
            adapter.reload(Collections.emptyList(),()->{assertSame(android.os.Looper.getMainLooper(),android.os.Looper.myLooper());applied[0]=true;});assertFalse(applied[0]);
            java.lang.reflect.Field field=AppAdapter.class.getDeclaredField("loader");field.setAccessible(true);((java.util.concurrent.ExecutorService)field.get(adapter)).submit(()->{}).get(10,java.util.concurrent.TimeUnit.SECONDS);org.robolectric.shadows.ShadowLooper.idleMainLooper();
            assertTrue(applied[0]);assertTrue(adapter.getItemCount()>=2);String key=adapter.keyAt(0);adapter.move(0,1);assertEquals(key,adapter.keyAt(1));assertFalse("Partial updates must not invalidate every holder",reset[0]);
        }finally{adapter.close();}
    }
    private static TvContentRepository.Item item(int n,long position){return new TvContentRepository.Item("test.app","Show "+n,"Resume","https://example.com/"+n,"",position,60000,0);}
    private static void layout(View view,int width,int height){view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));view.layout(0,0,width,height);}
    private static Bitmap referenceBlur(Bitmap source){
        int width=640,height=360,radius=6,diameter=13;Bitmap small=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        float scale=Math.max((float)width/source.getWidth(),(float)height/source.getHeight());int cropWidth=Math.max(1,Math.round(width/scale)),cropHeight=Math.max(1,Math.round(height/scale)),left=(source.getWidth()-cropWidth)/2,top=(source.getHeight()-cropHeight)/2;
        new Canvas(small).drawBitmap(source,new Rect(left,top,left+cropWidth,top+cropHeight),new Rect(0,0,width,height),new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG));
        int[] pixels=new int[width*height],temp=new int[pixels.length];small.getPixels(pixels,0,width,0,0,width,height);
        for(int pass=0;pass<2;pass++){
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){int r=0,g=0,b=0;for(int k=-radius;k<=radius;k++){int pixel=pixels[y*width+Math.max(0,Math.min(width-1,x+k))];r+=(pixel>>16)&255;g+=(pixel>>8)&255;b+=pixel&255;}temp[y*width+x]=0xff000000|(r/diameter<<16)|(g/diameter<<8)|b/diameter;}
            for(int y=0;y<height;y++)for(int x=0;x<width;x++){int r=0,g=0,b=0;for(int k=-radius;k<=radius;k++){int pixel=temp[Math.max(0,Math.min(height-1,y+k))*width+x];r+=(pixel>>16)&255;g+=(pixel>>8)&255;b+=pixel&255;}pixels[y*width+x]=0xff000000|(r/diameter<<16)|(g/diameter<<8)|b/diameter;}
        }
        for(int i=0;i<pixels.length;i++){int q=pixels[i];pixels[i]=0xff000000|(((q>>16)&255)*48/100<<16)|(((q>>8)&255)*48/100<<8)|(q&255)*48/100;}small.setPixels(pixels,0,width,0,0,width,height);return small;
    }
}
