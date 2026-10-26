package com.gathin.tvhome;
import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
@RunWith(org.robolectric.RobolectricTestRunner.class)
@Config(sdk=34,qualifiers="pt-rBR-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class DynamicSettingsTest {
    Context context;RecommendationChannelsTest fixture;TrackingProvider provider;
    static class TrackingProvider extends RecommendationChannelsTest.FakeProvider {
        List<String> tables=new ArrayList<>();@Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){tables.add(uri.getLastPathSegment());return super.query(uri,projection,selection,args,sort);}
    }
    @Before public void setup(){fixture=new RecommendationChannelsTest();fixture.setup();context=fixture.context;provider=new TrackingProvider();fixture.provider=provider;org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("android.media.tv",provider);}
    static Object field(Object o,String name)throws Exception{java.lang.reflect.Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static void idle(){org.robolectric.shadows.ShadowLooper.idleMainLooper(400,TimeUnit.MILLISECONDS);}
    static void layout(Activity activity){OptionalThemeTest.layout(activity.getWindow().getDecorView(),960,540);}
    @Test public void firstUseDefaultsToAppsOnlyAfterWallpaperMigration() throws Exception {
        LocalPreferences.get(context).edit().clear().commit();
        WallpaperSettings.migrate(context);DynamicRows.migrate(context);
        assertFalse(DynamicRows.feeds(context));assertFalse(DynamicRows.enabled(context,DynamicRows.MUSIC));
        fixture.installed(RecommendationChannels.COBALT);fixture.installed(RecommendationChannels.YOUTUBE);
        assertTrue(TvContentRepository.read(context).recommendations.isEmpty());assertEquals(0,provider.calls);
        LocalPreferences.get(context).edit().putBoolean(DynamicRows.ALL,true).commit();
        DynamicRows.migrate(context);assertTrue(DynamicRows.feeds(context));assertTrue(DynamicRows.enabled(context,DynamicRows.MUSIC));
    }
    @Test public void upgradePreservesImplicitEnabledMasterAndIndividualChoices(){
        LocalPreferences.get(context).edit().clear().putBoolean("dynamic_rows_migrated",true).putBoolean(DynamicRows.LIVE,false).commit();
        DynamicRows.migrate(context);assertTrue(DynamicRows.enabled(context,DynamicRows.WATCH));assertFalse(DynamicRows.enabled(context,DynamicRows.LIVE));
    }
    @Test public void migrationPreservesExplicitMasterChoices(){
        for(boolean value:new boolean[]{false,true}){
            LocalPreferences.get(context).edit().clear().putBoolean(DynamicRows.ALL,value).commit();
            DynamicRows.migrate(context);DynamicRows.migrate(context);
            assertEquals(value,LocalPreferences.get(context).getBoolean(DynamicRows.ALL,!value));
        }
    }
    @Test public void oldCombinedChoiceMigratesOnceIntoIndependentClientChoices(){
        LocalPreferences.get(context).edit().putBoolean(RecommendationChannels.ENABLED,false).putBoolean(DynamicRows.WATCH,false).commit();DynamicRows.migrate(context);assertFalse(DynamicRows.enabled(context,DynamicRows.COBALT));assertFalse(DynamicRows.enabled(context,DynamicRows.YOUTUBE));assertFalse(DynamicRows.enabled(context,DynamicRows.WATCH));assertTrue(LocalPreferences.get(context).getBoolean(RecommendationChannels.ENABLED,false));LocalPreferences.get(context).edit().putBoolean(DynamicRows.COBALT,true).commit();DynamicRows.migrate(context);assertTrue(DynamicRows.enabled(context,DynamicRows.COBALT));assertFalse(DynamicRows.enabled(context,DynamicRows.YOUTUBE));
    }
    @Test public void masterOverridePreservesIndividualChoices(){
        LocalPreferences.get(context).edit().putBoolean(DynamicRows.WATCH,false).putBoolean(DynamicRows.LIVE,true).putBoolean(DynamicRows.ALL,false).commit();assertFalse(DynamicRows.enabled(context,DynamicRows.LIVE));assertFalse(DynamicRows.feeds(context));assertFalse(DynamicRows.enabled(context,DynamicRows.MUSIC));LocalPreferences.get(context).edit().putBoolean(DynamicRows.ALL,true).commit();assertFalse(DynamicRows.enabled(context,DynamicRows.WATCH));assertTrue(DynamicRows.enabled(context,DynamicRows.LIVE));
    }
    @Test public void appOnlySkipsEveryTvProviderQueryEvenWithAppsInstalled(){
        fixture.installed(RecommendationChannels.COBALT);LocalPreferences.get(context).edit().putBoolean(DynamicRows.ALL,false).commit();TvContentRepository.Result result=TvContentRepository.read(context);assertEquals(0,provider.calls);assertTrue(result.watch.isEmpty());assertTrue(result.live.isEmpty());assertTrue(result.recommendations.isEmpty());
    }
    @Test public void hidingWatchNextAvoidsItsQueryAndHidingLiveAvoidsItsQueries(){
        LocalPreferences.get(context).edit().putBoolean(DynamicRows.WATCH,false).commit();TvContentRepository.read(context);assertFalse(provider.tables.contains("watch_next_program"));assertTrue(provider.tables.contains("preview_program"));provider.tables.clear();LocalPreferences.get(context).edit().putBoolean(DynamicRows.WATCH,true).putBoolean(DynamicRows.LIVE,false).commit();TvContentRepository.read(context);assertEquals(Collections.singletonList("watch_next_program"),provider.tables);
    }
    @Test public void eachYouTubeClientCanBeHiddenIndependentlyAndLegacyChoiceSurvives(){
        fixture.installed(RecommendationChannels.COBALT);fixture.installed(RecommendationChannels.YOUTUBE);fixture.program(RecommendationChannels.COBALT,1,1,1,-1);fixture.program(RecommendationChannels.YOUTUBE,2,2,1,-1);LocalPreferences.get(context).edit().putBoolean(DynamicRows.COBALT,false).commit();RecommendationChannels.Result result=RecommendationChannels.read(context);assertEquals(1,result.channels.size());assertEquals(RecommendationChannels.YOUTUBE,result.channels.get(0).pkg);LocalPreferences.get(context).edit().putBoolean(DynamicRows.COBALT,true).putBoolean(DynamicRows.YOUTUBE,false).commit();assertEquals(RecommendationChannels.COBALT,RecommendationChannels.read(context).channels.get(0).pkg);LocalPreferences.get(context).edit().putBoolean(RecommendationChannels.ENABLED,false).commit();assertTrue(RecommendationChannels.read(context).channels.isEmpty());
    }
    @Test public void appOnlyHasNoFeedHeadersEmptyMessagesOrObservers() throws Exception {
        LocalPreferences.get(context).edit().putBoolean(DynamicRows.ALL,false).commit();
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup().visible()){
            LinearLayout root=new LinearLayout(c.get());c.get().setContentView(root);HomeMediaPanel panel=new HomeMediaPanel(c.get(),root,new HomeMediaPanel.Host(){public void musicBackground(android.graphics.Bitmap b){assertNull(b);}public void feedsChanged(){}public void focusApps(){}});
            try{panel.start();idle();assertTrue(panel.contentReady());assertEquals(View.GONE,((View)field(panel,"content")).getVisibility());assertEquals(View.GONE,((View)field(panel,"now")).getVisibility());assertEquals(false,field(panel,"providerRegistered"));assertEquals(false,field(panel,"sessionRegistered"));assertEquals(0,provider.calls);assertFalse(panel.focusFromDock());}finally{panel.close();}
        }
    }
    @Test public void hiddenLiveHasNoFallbackAndWatchCanRemainVisible() throws Exception {
        LocalPreferences.get(context).edit().putBoolean(DynamicRows.LIVE,false).commit();
        try(ActivityController<Activity> c=Robolectric.buildActivity(Activity.class).setup().visible()){
            LinearLayout root=new LinearLayout(c.get());HomeMediaPanel panel=new HomeMediaPanel(c.get(),root,new HomeMediaPanel.Host(){public void musicBackground(android.graphics.Bitmap b){}public void feedsChanged(){}public void focusApps(){}});
            try{java.lang.reflect.Method show=HomeMediaPanel.class.getDeclaredMethod("showFeeds",TvContentRepository.Result.class);show.setAccessible(true);show.invoke(panel,new TvContentRepository.Result());MediaRow live=(MediaRow)field(panel,"liveRow");assertEquals(0,live.getItemCount());assertEquals(View.GONE,live.view.getVisibility());assertEquals(View.GONE,((View)field(panel,"liveHeading")).getVisibility());assertEquals(View.VISIBLE,((View)field(panel,"watchHeading")).getVisibility());}finally{panel.close();}
        }
    }
    @Test public void preferencesStartWithFourCollapsedGroupsAndExpandOnlyOne() throws Exception {
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            layout(c.get());idle();View root=c.get().getWindow().getDecorView();for(String key:new String[]{"home","wallpaper","weather","system"})assertEquals(View.GONE,root.findViewWithTag("category-body:"+key).getVisibility());OptionalThemeTest.save(root,"preferences-categories-v11.png");root.findViewWithTag("category:home").performClick();idle();layout(c.get());assertEquals(View.VISIBLE,root.findViewWithTag("category-body:home").getVisibility());assertTrue(PreferencesVisualTest.find(root,"Todas as linhas dinâmicas").isShown());OptionalThemeTest.save(root,"preferences-home-v11.png");root.findViewWithTag("category:weather").performClick();idle();layout(c.get());assertEquals(View.GONE,root.findViewWithTag("category-body:home").getVisibility());assertEquals(View.VISIBLE,root.findViewWithTag("category-body:weather").getVisibility());
        }
    }
    @Test public void editingAnOptionKeepsItsGroupOpenAndTheHomePreviewFrozen() throws Exception {
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            layout(c.get());View root=c.get().getWindow().getDecorView();root.findViewWithTag("category:home").performClick();idle();android.graphics.Bitmap before=HomePreview.snapshot();root.findViewWithTag("setting:Todas as linhas dinâmicas").performClick();idle();layout(c.get());assertEquals("home",field(c.get(),"expandedCategory"));assertEquals(View.VISIBLE,root.findViewWithTag("category-body:home").getVisibility());assertFalse(LocalPreferences.get(c.get()).getBoolean(DynamicRows.ALL,true));assertSame(before,HomePreview.snapshot());
        }
    }
    @Test public void backCollapsesBeforeLeavingAndSavedStateRestoresTheExpandedGroup() throws Exception {
        Bundle state=new Bundle();try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            layout(c.get());c.get().getWindow().getDecorView().findViewWithTag("category:wallpaper").performClick();idle();c.saveInstanceState(state);c.get().onBackPressed();idle();assertFalse(c.get().isFinishing());assertEquals("",field(c.get(),"expandedCategory"));c.get().onBackPressed();assertTrue(c.get().isFinishing());
        }
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).create(state).start().resume().visible()){
            layout(c.get());idle();assertEquals("wallpaper",field(c.get(),"expandedCategory"));assertEquals(View.VISIBLE,c.get().getWindow().getDecorView().findViewWithTag("category-body:wallpaper").getVisibility());
        }
    }
}
