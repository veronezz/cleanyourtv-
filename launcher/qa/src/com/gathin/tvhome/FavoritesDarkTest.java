package com.gathin.tvhome;
import android.content.*;
import android.graphics.*;
import android.app.Dialog;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import java.io.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=34,qualifiers="pt-rBR-w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
public class FavoritesDarkTest {
 Context c;
 @Before public void reset(){c=RuntimeEnvironment.getApplication();LocalPreferences.get(c).edit().clear().putBoolean("reduced_motion",true).commit();FavoriteWallpapers.present(c,null);for(File f:FavoriteWallpapers.directory(c).listFiles())f.delete();}
 Bitmap image(int color){Bitmap b=Bitmap.createBitmap(800,450,Bitmap.Config.ARGB_8888);b.eraseColor(color);return b;}
 String save(int color) throws Exception {Bitmap b=image(color);FavoriteWallpapers.present(c,b);String id=FavoriteWallpapers.saveCurrent(c);b.recycle();return id;}
 @Test public void favoritesSurviveSourceThemeAndProviderCacheChangesAndDeduplicate() throws Exception {
  LocalPreferences.get(c).edit().putString("background_style","wallhaven").putString("wallhaven_slot_0_id","abc123").commit();Bitmap a=image(Color.BLUE);FavoriteWallpapers.present(c,a);String id=FavoriteWallpapers.saveCurrent(c);assertEquals(id,FavoriteWallpapers.saveCurrent(c));assertEquals(1,FavoriteWallpapers.list(c).size());
  LocalPreferences.get(c).edit().putString("wallhaven_source","space").putString("background_style","wallpaper").commit();LauncherTheme.select(c,LauncherTheme.IOS_DARK);assertTrue(FavoriteWallpapers.file(c,id).isFile());assertEquals(1,FavoriteWallpapers.list(c).size());
  String second=save(Color.RED);assertNotEquals(id,second);FavoriteWallpapers.select(c,id);assertEquals(id+".jpg",FavoriteWallpapers.current(c,false).getName());assertEquals(second+".jpg",FavoriteWallpapers.current(c,true).getName());assertEquals(id+".jpg",FavoriteWallpapers.current(c,true).getName());FavoriteWallpapers.remove(c,id);assertFalse(FavoriteWallpapers.file(c,id).exists());assertEquals(second+".jpg",FavoriteWallpapers.current(c,false).getName());Bitmap restored=BitmapFactory.decodeFile(FavoriteWallpapers.file(c,second).getPath());FavoriteWallpapers.present(c,restored);assertEquals(second,FavoriteWallpapers.saveCurrent(c));assertEquals(1,FavoriteWallpapers.list(c).size());restored.recycle();a.recycle();
 }
 @Test public void plainBackgroundCannotSaveAndIdsCannotEscapeCollection() throws Exception {
  try{FavoriteWallpapers.saveCurrent(c);fail();}catch(IOException expected){}assertTrue(FavoriteWallpapers.list(c).isEmpty());
  try{FavoriteWallpapers.file(c,"../owner-wallpaper");fail();}catch(IllegalArgumentException expected){}
 }
 @Test public void darkThemeConfirmsWithOkHasReadableTextAndAllThemesKeepAppsAboveFeeds() throws Exception {
  try(var controller=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
   ThemePicker.show(controller.get(),()->{});Dialog d=org.robolectric.shadows.ShadowDialog.getLatestDialog();View root=d.getWindow().getDecorView();OptionalThemeTest.layout(root,860,490);org.robolectric.shadows.ShadowLooper.idleMainLooper();View dark=root.findViewWithTag("theme-choice-ios_dark");dark.requestFocusFromTouch();OptionalThemeTest.layout(root,860,490);OptionalThemeTest.save(root,"theme-ios-dark-preview.png");assertFalse(LauncherTheme.dark(c));dark.performClick();assertTrue(LauncherTheme.dark(c));assertFalse(d.isShowing());assertEquals(0xffeef3fa,GlassUi.text(c,"Title",16,Color.WHITE).getCurrentTextColor());
  }
  for(String theme:new String[]{LauncherTheme.DEFAULT,LauncherTheme.SMOKED,LauncherTheme.IOS,LauncherTheme.IOS_DARK}){
   LauncherTheme.select(c,theme);try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
    MainActivity a=controller.get();View grid=a.findViewById(R.id.grid),box=(View)grid.getParent();ViewGroup parent=(ViewGroup)box.getParent();assertEquals(3,parent.getChildCount());assertSame(box,parent.getChildAt(1));
    if(LauncherTheme.dark(c)){View root=a.findViewById(R.id.root);OptionalThemeTest.layout(root,960,540);org.robolectric.shadows.ShadowLooper.idleMainLooper(700,java.util.concurrent.TimeUnit.MILLISECONDS);OptionalThemeTest.save(root,"home-ios-dark.png");assertEquals(0xffeef3fa,((ImageView)a.findViewById(R.id.btnWifi)).getImageTintList().getDefaultColor());}
   }
  }
 }
 @Test public void favoritesPauseWithSpotifyAndWhileHomeIsPaused() throws Exception {
  String first=save(Color.BLUE),second=save(Color.RED);FavoriteWallpapers.select(c,first);
  try(var controller=Robolectric.buildActivity(MainActivity.class).setup().visible()){
   MainActivity a=controller.get();java.lang.reflect.Method next=MainActivity.class.getDeclaredMethod("scheduleFavorites",boolean.class);next.setAccessible(true);java.lang.reflect.Field spotify=MainActivity.class.getDeclaredField("spotifyVisible");spotify.setAccessible(true);spotify.setBoolean(a,true);next.invoke(a,true);assertEquals(first,LocalPreferences.get(c).getString(FavoriteWallpapers.SELECTED,""));spotify.setBoolean(a,false);next.invoke(a,true);assertEquals(second,LocalPreferences.get(c).getString(FavoriteWallpapers.SELECTED,""));controller.pause();next.invoke(a,true);assertEquals(second,LocalPreferences.get(c).getString(FavoriteWallpapers.SELECTED,""));assertFalse(new File(c.getFilesDir(),"wallhaven-wallpapers").exists());
  }
 }
 @Test public void favoritePickerUsesThumbnailsAndEmptyCollectionIsExplained() throws Exception {
  save(Color.BLUE);save(Color.RED);LauncherTheme.select(c,LauncherTheme.IOS_DARK);
  try(var controller=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
   View prefs=controller.get().getWindow().getDecorView();OptionalThemeTest.layout(prefs,960,540);org.robolectric.shadows.ShadowLooper.idleMainLooper(300,java.util.concurrent.TimeUnit.MILLISECONDS);OptionalThemeTest.save(prefs,"preferences-ios-dark.png");FavoritesPicker.show(controller.get(),()->{});Dialog d=org.robolectric.shadows.ShadowDialog.getLatestDialog();View root=d.getWindow().getDecorView();OptionalThemeTest.layout(root,650,400);ScrollView scroll=(ScrollView)SizingLayoutTest.findType(root,ScrollView.class);LinearLayout rows=(LinearLayout)scroll.getChildAt(0);ImageView thumbnail=(ImageView)((LinearLayout)rows.getChildAt(0)).getChildAt(0);long deadline=System.nanoTime()+2_000_000_000L;while(thumbnail.getDrawable()==null&&System.nanoTime()<deadline){Thread.sleep(10);org.robolectric.shadows.ShadowLooper.idleMainLooper();}assertNotNull(thumbnail.getDrawable());OptionalThemeTest.layout(root,650,400);OptionalThemeTest.save(root,"favorite-wallpapers.png");assertTrue(d.isShowing());d.dismiss();
  }
 }
}
