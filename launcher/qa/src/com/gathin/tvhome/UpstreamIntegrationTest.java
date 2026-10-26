package com.gathin.tvhome;

import android.content.*;
import android.content.pm.*;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="en-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class UpstreamIntegrationTest {
    Context context;
    @Before public void setup(){context=RuntimeEnvironment.getApplication();LocalPreferences.get(context).edit().clear().commit();HomePreview.trim();}
    @Test public void categoriesDoNotAllocateTheirOptionViewsUntilOpened(){
        try(var controller=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            var activity=controller.get();View root=activity.getWindow().getDecorView();
            for(String name:new String[]{"home","wallpaper","weather","system"})assertEquals(0,((LinearLayout)root.findViewWithTag("category-body:"+name)).getChildCount());
            root.findViewWithTag("category:home").performClick();LinearLayout home=root.findViewWithTag("category-body:home");int count=home.getChildCount();assertTrue(count>10);
            assertEquals(0,((LinearLayout)root.findViewWithTag("category-body:wallpaper")).getChildCount());
            root.findViewWithTag("category:home").performClick();root.findViewWithTag("category:home").performClick();assertEquals(count,home.getChildCount());
        }
    }
    @Test public void originalLanguagesAndBrazilianPortugueseAreSelectableAndHaveResources(){
        assertEquals(26,AppLanguage.TAGS.length);assertEquals(26,AppLanguage.labels(context).length);
        for(String tag:AppLanguage.TAGS){AppLanguage.select(context,tag);assertEquals(tag,AppLanguage.choice(context));assertFalse(AppLanguage.labels(context)[Arrays.asList(AppLanguage.TAGS).indexOf(tag)].isEmpty());}
        for(String tag:new String[]{"fr","de","ru","he","ar","zh-TW","id","nb"}){
            Configuration config=new Configuration(context.getResources().getConfiguration());config.setLocales(LocaleList.forLanguageTags(tag));Context translated=context.createConfigurationContext(config);
            assertNotEquals(tag,"Language",translated.getString(R.string.language_title));assertNotEquals(tag,"Follow system",translated.getString(R.string.language_system));
            assertFalse(translated.getString(R.string.library_help).isEmpty());
        }
    }
    @Test public void moduleRecordsAreNotReadWhenThereAreNoModuleApps(){
        List<Cards.Card> cards=Cards.discover(context,Collections.emptySet(),()->{throw new AssertionError("No reason to read disk");},Collections.emptyList());assertTrue(cards.isEmpty());
    }
    @Test public void canceledInputReleaseDoesNotOpenTheInputSwitcher(){
        var service=Robolectric.buildService(InputKeyService.class).create();try{
            KeyEvent canceled=new KeyEvent(0,500,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TV_INPUT,0,0,0,0,KeyEvent.FLAG_CANCELED);
            assertTrue(service.get().onKeyEvent(canceled));assertNull(Shadows.shadowOf(service.get()).getNextStartedActivity());
            assertFalse(service.get().onKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_VOLUME_UP)));
            assertTrue(service.get().onKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TV_INPUT)));
            assertEquals("com.android.tv.action.VIEW_INPUTS",Shadows.shadowOf(service.get()).getNextStartedActivity().getAction());
        }finally{service.destroy();}
    }
    static ResolveInfo app(String pkg,String name){ResolveInfo info=new ResolveInfo();info.activityInfo=new ActivityInfo();info.activityInfo.packageName=pkg;info.activityInfo.name=name;info.activityInfo.enabled=true;info.activityInfo.applicationInfo=new ApplicationInfo();info.activityInfo.applicationInfo.packageName=pkg;info.activityInfo.applicationInfo.enabled=true;info.nonLocalizedLabel=pkg;return info;}
    @Test public void libraryIncludesHiddenAndPhoneAppsAndDeduplicatesPackages(){
        Intent tv=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),phone=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        var pm=Shadows.shadowOf(context.getPackageManager());pm.addResolveInfoForIntent(tv,app("test.tv","test.tv.Main"));pm.addResolveInfoForIntent(phone,app("test.tv","test.tv.Phone"));pm.addResolveInfoForIntent(phone,app("test.phone","test.phone.Main"));LocalPreferences.hideApp(context,"test.tv");
        List<ResolveInfo> found=AppLibrary.discover(context);assertEquals(2,found.size());assertEquals("test.phone",found.get(0).activityInfo.packageName);assertEquals("test.tv",found.get(1).activityInfo.packageName);assertEquals("test.tv.Main",found.get(1).activityInfo.name);
    }
    @Test public void rtlMoveModeFollowsPhysicalButtonsWithoutWrapping(){
        assertEquals(1,AppOrder.target(0,KeyEvent.KEYCODE_DPAD_LEFT,6,12,true));
        assertEquals(0,AppOrder.target(0,KeyEvent.KEYCODE_DPAD_RIGHT,6,12,true));
        assertEquals(5,AppOrder.target(5,KeyEvent.KEYCODE_DPAD_LEFT,6,12,true));
        assertEquals(4,AppOrder.target(5,KeyEvent.KEYCODE_DPAD_RIGHT,6,12,true));
    }
    @Test public void preferencesAttachGlassBeforeLoadingItsBackdropAndReleaseObservers() throws Exception {
        LauncherTheme.select(context,LauncherTheme.SMOKED);LauncherPreferencesActivity activity;
        java.lang.reflect.Field background=HomePreview.class.getDeclaredField("background");background.setAccessible(true);
        java.lang.reflect.Field observers=HomePreview.class.getDeclaredField("listeners");observers.setAccessible(true);
        int before=((List<?>)observers.get(null)).size();
        try(var controller=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            activity=controller.get();assertNull(background.get(null));assertEquals(before+1,((List<?>)observers.get(null)).size());
            View root=activity.getWindow().getDecorView();OptionalThemeTest.layout(root,960,540);OptionalThemeTest.save(root,"preferences-first-frame-v14.png");root.getViewTreeObserver().dispatchOnDraw();
            org.robolectric.shadows.ShadowLooper.idleMainLooper();assertNotNull(background.get(null));
        }
        assertEquals(before,((List<?>)observers.get(null)).size());assertNull(background.get(null));
    }
    @Test public void systemSettingsRejectsCtsDummyAndKeepsRealStandardHandler(){
        Intent standard=new Intent(android.provider.Settings.ACTION_SETTINGS);var pm=Shadows.shadowOf(context.getPackageManager());
        PackageInfo vendor=new PackageInfo();vendor.packageName="com.xiaomi.mitv.settings";vendor.applicationInfo=app(vendor.packageName,"com.xiaomi.mitv.settings.entry.MainActivity").activityInfo.applicationInfo;vendor.activities=new ActivityInfo[]{app(vendor.packageName,"com.xiaomi.mitv.settings.entry.MainActivity").activityInfo};pm.installPackage(vendor);
        pm.addResolveInfoForIntent(standard,app("test.cts","test.cts.CTSDummySettings"));assertEquals("com.xiaomi.mitv.settings",SystemSettings.intent(context,standard.getAction()).getComponent().getPackageName());
        pm.removeResolveInfosForIntent(standard,"test.cts");pm.addResolveInfoForIntent(standard,app("test.real","test.real.Settings"));assertNull(SystemSettings.intent(context,standard.getAction()).getComponent());
    }
}
