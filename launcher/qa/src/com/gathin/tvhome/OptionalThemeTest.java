package com.gathin.tvhome;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
public class OptionalThemeTest {
    private Context context;
    @Before public void setup(){context=org.robolectric.RuntimeEnvironment.getApplication();LocalPreferences.get(context).edit().clear().commit();HomePreview.releaseBackground();}
    @Test public void defaultAndUnknownThemesKeepExistingAppearanceAndAllocateNoGlassScene() throws Exception {
        assertEquals(LauncherTheme.DEFAULT,LauncherTheme.selected(context));LocalPreferences.get(context).edit().putString(LauncherTheme.KEY,"future-value").commit();assertFalse(LauncherTheme.smoked(context));
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            java.lang.reflect.Field scenes=GlassUi.class.getDeclaredField("scenes");scenes.setAccessible(true);assertFalse(((java.util.Map<?,?>)scenes.get(null)).containsKey(c.get()));
            assertTrue(GlassUi.panel(c.get(),new View(c.get()),false) instanceof LiquidGlassDrawable);
        }
    }
    @Test public void previewAndCancelDoNotChangeSavedThemeOrRevision() throws Exception {
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            layout(c.get().getWindow().getDecorView(),960,540);long revision=LocalPreferences.revision(c.get());ThemePicker.show(c.get(),()->fail("Cancel must not apply"));Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();View root=dialog.getWindow().getDecorView();layout(root,860,380);
            root.findViewWithTag("theme-choice-smoked").requestFocusFromTouch();assertFalse(LauncherTheme.smoked(c.get()));assertEquals(revision,LocalPreferences.revision(c.get()));root.findViewWithTag("theme-cancel").performClick();assertFalse(LauncherTheme.smoked(c.get()));assertFalse(dialog.isShowing());
        }
    }
    @Test public void explicitApplyPersistsThePreviewedThemeAndCanReturnToDefault() throws Exception {
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            layout(c.get().getWindow().getDecorView(),960,540);int[] applied={0};ThemePicker.show(c.get(),()->applied[0]++);Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();View root=dialog.getWindow().getDecorView();layout(root,860,380);
            root.findViewWithTag("theme-choice-smoked").performClick();assertTrue(LauncherTheme.smoked(c.get()));assertEquals(1,applied[0]);
            LauncherTheme.select(c.get(),LauncherTheme.DEFAULT);assertFalse(LauncherTheme.smoked(c.get()));
            try{LauncherTheme.select(c.get(),"unknown");fail();}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void preferencesAndMenuUseSmokedSurfacesAndReleaseTheirScene() throws Exception {
        LauncherTheme.select(context,LauncherTheme.SMOKED);
        LauncherPreferencesActivity activity;
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            activity=c.get();View root=activity.getWindow().getDecorView();layout(root,960,540);
            java.lang.reflect.Field field=LauncherPreferencesActivity.class.getDeclaredField("glassScene");field.setAccessible(true);GlassScene scene=(GlassScene)field.get(activity);assertNotNull(scene);waitFor(scene);org.robolectric.shadows.ShadowLooper.idleMainLooper(300,TimeUnit.MILLISECONDS);layout(root,960,540);
            assertTrue(GlassUi.panel(activity,new View(activity),false) instanceof SmokedGlassDrawable);save(root,"preferences-smoked.png");
            GlassUi.menu(activity,"YouTube",activity.getString(R.string.theme_smoked_detail),new String[]{"Mover aplicativo","Renomear","Trocar ícone ou imagem","Ocultar aplicativo"},new String[]{"Escolha uma posição","Altere o nome do atalho","Galeria ou outro app","Reexiba nas preferências"},n->{});
            Dialog menu=org.robolectric.shadows.ShadowDialog.getLatestDialog();View menuRoot=menu.getWindow().getDecorView();layout(menuRoot,560,440);org.robolectric.shadows.ShadowLooper.idleMainLooper(300,TimeUnit.MILLISECONDS);layout(menuRoot,560,440);assertEquals(.18f,menu.getWindow().getAttributes().dimAmount,.001f);save(menuRoot,"context-menu-smoked.png");menu.dismiss();
        }
        java.lang.reflect.Field scenes=GlassUi.class.getDeclaredField("scenes");scenes.setAccessible(true);assertFalse(((java.util.Map<?,?>)scenes.get(null)).containsKey(activity));
    }
    @Test public void openingPickerKeepsAnAlreadySelectedSmokedThemeAndHasAnExplicitPreview() throws Exception {
        LauncherTheme.select(context,LauncherTheme.SMOKED);
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            layout(c.get().getWindow().getDecorView(),960,540);ThemePicker.show(c.get(),()->fail("Already selected must not reapply"));Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();View root=dialog.getWindow().getDecorView();layout(root,860,380);
            org.robolectric.shadows.ShadowLooper.idleMainLooper(300,TimeUnit.MILLISECONDS);layout(root,860,380);
            assertTrue(root.findViewWithTag("theme-choice-smoked").isSelected());assertFalse(root.findViewWithTag("theme-choice-default").isSelected());save(root,"theme-picker.png");root.findViewWithTag("theme-choice-smoked").performClick();assertTrue(LauncherTheme.smoked(c.get()));
        }
    }
    @Test public void themeChangeOnReturnDoesNotRecreateHomeAndIconsStayReadable(){
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            View root=c.get().findViewById(R.id.root);c.pause();LauncherTheme.select(c.get(),LauncherTheme.SMOKED);c.resume();assertSame(root,c.get().findViewById(R.id.root));assertFalse(c.get().isChangingConfigurations());
            android.widget.ImageView wifi=c.get().findViewById(R.id.btnWifi);assertEquals(Color.WHITE,wifi.getImageTintList().getColorForState(new int[]{android.R.attr.state_focused},0));
            c.pause();LauncherTheme.select(c.get(),LauncherTheme.DEFAULT);c.resume();assertSame(root,c.get().findViewById(R.id.root));assertEquals(0xff111111,wifi.getImageTintList().getColorForState(new int[]{android.R.attr.state_focused},0));
        }
    }
    @Test public void blurTextureKeepsBackdropColorsAndStopRejectsLateResults() throws Exception {
        View anchor=new View(context);layout(anchor,960,540);Bitmap source=Bitmap.createBitmap(80,45,Bitmap.Config.ARGB_8888);source.eraseColor(0xffa0c0e0);
        GlassScene scene=new GlassScene(anchor,source);waitFor(scene);java.lang.reflect.Field field=GlassScene.class.getDeclaredField("texture");field.setAccessible(true);Bitmap texture=(Bitmap)field.get(scene);assertNotNull(texture);assertEquals(0xffa0c0e0,texture.getPixel(10,10));scene.close();assertNull(field.get(scene));scene.update(source);assertNull(field.get(scene));source.recycle();
    }
    @Test public void iosPreviewIsTransientThenPersistsOnlyOnApply() throws Exception {
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            layout(c.get().getWindow().getDecorView(),960,540);int[] count={0};ThemePicker.show(c.get(),()->count[0]++);
            Dialog d=org.robolectric.shadows.ShadowDialog.getLatestDialog();View root=d.getWindow().getDecorView();layout(root,860,460);
            root.findViewWithTag("theme-choice-ios").requestFocusFromTouch();assertFalse(LauncherTheme.ios(c.get()));root.findViewWithTag("theme-cancel").performClick();assertEquals(0,count[0]);
            ThemePicker.show(c.get(),()->count[0]++);d=org.robolectric.shadows.ShadowDialog.getLatestDialog();root=d.getWindow().getDecorView();layout(root,860,460);
            root.findViewWithTag("theme-choice-ios").requestFocusFromTouch();layout(root,860,460);save(root,"theme-picker-ios.png");root.findViewWithTag("theme-choice-ios").performClick();assertTrue(LauncherTheme.ios(c.get()));assertEquals(1,count[0]);
        }
    }
    @Test public void iosHomeSwitchKeepsHomeAndWallpaperSettings() throws Exception {
        try(ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
            View root=c.get().findViewById(R.id.root);c.pause();LauncherTheme.select(c.get(),LauncherTheme.IOS);c.resume();assertSame(root,c.get().findViewById(R.id.root));
            android.widget.ImageView wifi=c.get().findViewById(R.id.btnWifi);assertEquals(IosStyle.INK,wifi.getImageTintList().getDefaultColor());
            layout(root,960,540);org.robolectric.shadows.ShadowLooper.idleMainLooper(600,TimeUnit.MILLISECONDS);layout(root,960,540);save(root,"home-ios.png");
            c.pause();LauncherTheme.select(c.get(),LauncherTheme.DEFAULT);c.resume();assertSame(root,c.get().findViewById(R.id.root));assertFalse(LauncherTheme.ios(c.get()));
        }
    }
    @Test public void iosPreferencesHaveDarkTextAndNoGlassWorker() throws Exception {
        LauncherTheme.select(context,LauncherTheme.IOS);
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            View root=c.get().getWindow().getDecorView();layout(root,960,540);org.robolectric.shadows.ShadowLooper.idleMainLooper(400,TimeUnit.MILLISECONDS);layout(root,960,540);
            assertEquals(IosStyle.INK,GlassUi.text(c.get(),"Text",16,Color.WHITE).getCurrentTextColor());
            java.lang.reflect.Field scenes=GlassUi.class.getDeclaredField("scenes");scenes.setAccessible(true);assertFalse(((java.util.Map<?,?>)scenes.get(null)).containsKey(c.get()));save(root,"preferences-ios.png");
            GlassUi.menu(c.get(),"YouTube","Opções",new String[]{"Mover","Ocultar"},null,n->{});Dialog d=org.robolectric.shadows.ShadowDialog.getLatestDialog();layout(d.getWindow().getDecorView(),560,360);save(d.getWindow().getDecorView(),"context-menu-ios.png");d.dismiss();
        }
    }
    @Test public void iosDarkWallpaperUsesCompositedLuminanceAndProtectsThumbnailText() throws Exception {
        LauncherTheme.select(context,LauncherTheme.IOS);LocalPreferences.get(context).edit().putBoolean("adaptive_contrast",false).putBoolean("reduced_motion",true).commit();
        FrameLayout root=new FrameLayout(context);root.setBackgroundColor(Color.BLACK);android.widget.TextView title=new android.widget.TextView(context);title.setText("Title");title.setTextColor(Color.WHITE);root.addView(title,new FrameLayout.LayoutParams(300,60));
        android.widget.TextView duration=new android.widget.TextView(context);duration.setText("12:34");duration.setTextColor(Color.WHITE);duration.setTag(R.id.contrast_protected,true);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(100,40);lp.topMargin=100;root.addView(duration,lp);
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            c.get().setContentView(root);layout(root,960,540);Bitmap black=Bitmap.createBitmap(80,45,Bitmap.Config.ARGB_8888);black.eraseColor(Color.BLACK);
            try(BackdropContrast contrast=new BackdropContrast(root)){contrast.source(black,android.widget.ImageView.ScaleType.CENTER_CROP);org.robolectric.shadows.ShadowLooper.idleMainLooper(500,TimeUnit.MILLISECONDS);assertEquals(0xff142027,title.getCurrentTextColor());assertEquals(Color.WHITE,duration.getCurrentTextColor());}black.recycle();
        }
    }
    @Test @Config(sdk=26) public void iosSurfacesSupportAndroidEightWithoutShaders(){
        LauncherTheme.select(context,LauncherTheme.IOS);assertEquals("iOS",LauncherTheme.label(context));
        try(ActivityController<LauncherPreferencesActivity> c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
            assertNotNull(GlassUi.panel(c.get(),false));assertEquals(IosStyle.INK,GlassUi.text(c.get(),"Title",16,Color.WHITE).getCurrentTextColor());
        }
    }
    static void waitFor(GlassScene scene) throws Exception {java.lang.reflect.Field worker=GlassScene.class.getDeclaredField("worker");worker.setAccessible(true);((ExecutorService)worker.get(scene)).submit(()->{}).get(10,TimeUnit.SECONDS);org.robolectric.shadows.ShadowLooper.idleMainLooper();}
    static void layout(View view,int w,int h){view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));view.layout(0,0,w,h);}
    static void save(View view,String name) throws Exception {Bitmap bitmap=Bitmap.createBitmap(view.getWidth(),view.getHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(bitmap));try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(System.getProperty("tvhome.qa.output"),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
}
