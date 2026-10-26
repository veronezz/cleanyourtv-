package com.gathin.tvhome;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import java.util.concurrent.*;
import static org.junit.Assert.*;
@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=34,qualifiers="pt-rBR-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class SizingLayoutTest {
    Context context;
    @Before public void before(){context=org.robolectric.RuntimeEnvironment.getApplication();LocalPreferences.get(context).edit().clear().commit();}
    static View findType(View root,Class<?> type){if(type.isInstance(root))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View v=findType(((ViewGroup)root).getChildAt(n),type);if(v!=null)return v;}return null;}
    @Test public void largeCardsRemainProportionalAndFitWithFocusForEverySize(){
        for(float density:new float[]{1f,1.5f,2f})for(int columns=4;columns<=8;columns++)for(int percent=100;percent<=150;percent+=10){
            int inner=Math.round(848*density),base=AppAdapter.tileWidthPx(inner,columns,density),width=TileSizing.width(inner,columns,density,percent),count=TileSizing.columns(inner,columns,density,percent);
            assertEquals(Math.round(base*percent/100f),width);assertTrue(width*AppAdapter.FOCUS_SCALE>width);assertTrue("Focus must fit the cell",width*AppAdapter.FOCUS_SCALE+11*density<=inner/(float)count);assertTrue(count<=columns);
        }
    }
    @Test public void preferenceSizeClampsCorruptOrFutureValues(){assertEquals(100,TileSizing.percent(context));LocalPreferences.get(context).edit().putInt("icon_size_percent",300).commit();assertEquals(150,TileSizing.percent(context));LocalPreferences.get(context).edit().putInt("icon_size_percent",-1).commit();assertEquals(100,TileSizing.percent(context));}
    @Test public void previewFillsRoundedContainerAndPanelsShareBottom() throws Exception {
        LauncherTheme.select(context,LauncherTheme.SMOKED);
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            View root=c.get().getWindow().getDecorView();OptionalThemeTest.layout(root,960,540);org.robolectric.shadows.ShadowLooper.idleMainLooper(1,TimeUnit.SECONDS);OptionalThemeTest.layout(root,960,540);
            TextView link=PreferencesVisualTest.find(root,"GITHUB  ↗\nveronezz/cleanyourtv-");assertNotNull(link);assertTrue(link.getPaddingLeft()>=12);assertTrue(link.getPaddingTop()>=12);
            SafeSettingsScroll scroll=(SafeSettingsScroll)findType(root,SafeSettingsScroll.class);assertNotNull(scroll);assertTrue(scroll.getPaddingRight()>=20);assertTrue(scroll.getClipToPadding());
            Rect a=new Rect(),b=new Rect();((View)link.getParent()).getGlobalVisibleRect(a);scroll.getGlobalVisibleRect(b);assertEquals(b.bottom,a.bottom);
            LinearLayout about=root.findViewWithTag("project-info");
            View identity=about.getChildAt(0),slogan=root.findViewWithTag("project-slogan");
            int firstGap=slogan.getTop()-identity.getBottom(),secondGap=link.getTop()-slogan.getBottom();
            assertTrue("Panel should share spare height between its sections",firstGap>0);
            assertEquals(firstGap,secondGap,1);
            assertEquals("Link must keep bottom breathing room",about.getHeight()-about.getPaddingBottom(),link.getBottom());
            ImageView preview=null;LinearLayout shell=(LinearLayout)scroll.getParent();LinearLayout left=(LinearLayout)shell.getChildAt(0);
            for(int n=0;n<left.getChildCount();n++)if(left.getChildAt(n) instanceof FrameLayout){FrameLayout box=(FrameLayout)left.getChildAt(n);preview=(ImageView)box.getChildAt(0);assertEquals(0,box.getPaddingLeft());assertTrue(box.getClipToOutline());assertEquals(box.getWidth(),preview.getWidth());assertEquals(box.getHeight(),preview.getHeight());}
            assertNotNull(preview);assertEquals(ImageView.ScaleType.CENTER_CROP,preview.getScaleType());
            OptionalThemeTest.save(root,"preferences-sizing-v9.png");
        }
    }
    @Test public void settingsFocusStaysInsideBothHorizontalEdges() throws Exception {
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            View root=c.get().getWindow().getDecorView();OptionalThemeTest.layout(root,960,540);SafeSettingsScroll scroll=(SafeSettingsScroll)findType(root,SafeSettingsScroll.class);LinearLayout rows=(LinearLayout)scroll.getChildAt(0);
            for(int n=0;n<rows.getChildCount();n++){View row=rows.getChildAt(n);if(!row.isFocusable())continue;row.setFocusableInTouchMode(true);assertTrue(row.requestFocus());org.robolectric.shadows.ShadowLooper.idleMainLooper(300,TimeUnit.MILLISECONDS);assertEquals(0f,row.getTranslationX(),.01f);assertTrue(row.getRight()<=scroll.getWidth()-scroll.getPaddingRight()-scroll.getPaddingLeft());}
        }
    }
    @Test public void settingsScrollReservesMarginsDuringScrolling(){
        SafeSettingsScroll scroll=new SafeSettingsScroll(context);scroll.setPadding(20,20,20,20);LinearLayout content=new LinearLayout(context);content.setMinimumHeight(1600);scroll.addView(content,new ScrollView.LayoutParams(-1,1600));OptionalThemeTest.layout(scroll,400,240);scroll.scrollTo(0,500);
        assertTrue(scroll.computeScrollDeltaToGetChildRectOnScreen(new Rect(0,510,100,610))<=-22);
        assertTrue(scroll.computeScrollDeltaToGetChildRectOnScreen(new Rect(0,690,100,790))>=82);
    }
    @Test public void homeUsesSafeScrollingWithSpaceForFinalLabels(){
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            SafeHomeScroll scroll=c.get().findViewById(R.id.homeScroll);assertNotNull(scroll);assertTrue(scroll.getPaddingTop()>=18);assertTrue(scroll.getPaddingBottom()>=24);assertTrue(scroll.getChildAt(0).getPaddingBottom()>=72);
        }
    }
    @Test public void weatherMapsDayNightAndSevereConditionsToPublishedGoogleNames(){
        assertEquals("sunny",GoogleWeatherIcon.name(0,true));assertEquals("clear",GoogleWeatherIcon.name(0,false));assertEquals("partly_clear",GoogleWeatherIcon.name(2,false));assertEquals("heavy",GoogleWeatherIcon.name(65,true));assertEquals("strong_tstorms",GoogleWeatherIcon.name(95,false));assertEquals("",GoogleWeatherIcon.name(-1,true));
    }
    @Test public void cachedWeatherIconNeedsNoDownloadAndDisablingRemovesIt() throws Exception {
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup().visible()){
            TextView text=new TextView(c.get());c.get().setContentView(text);Bitmap image=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(c.get().getCacheDir(),"google-weather.png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}
            android.content.SharedPreferences p=LocalPreferences.get(c.get());p.edit().putBoolean("weather_google_icons",true).putString("weather_lat","0").putString("weather_lon","0").putString("weather_cache_key","0,0").putLong("weather_updated",System.currentTimeMillis()).putInt("weather_code",0).putBoolean("weather_is_day",true).putString("weather_icon_cached","sunny").commit();
            GoogleWeatherIcon icons=new GoogleWeatherIcon(c.get(),text);try{icons.update();java.lang.reflect.Field field=GoogleWeatherIcon.class.getDeclaredField("worker");field.setAccessible(true);((ExecutorService)field.get(icons)).submit(()->{}).get(5,TimeUnit.SECONDS);org.robolectric.shadows.ShadowLooper.idleMainLooper();assertNotNull(text.getCompoundDrawables()[0]);p.edit().putBoolean("weather_google_icons",false).commit();icons.update();assertNull(text.getCompoundDrawables()[0]);}finally{icons.close();}
        }
    }
}
