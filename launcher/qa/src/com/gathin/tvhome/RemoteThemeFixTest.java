package com.gathin.tvhome;
import android.app.Dialog;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=34,qualifiers="pt-rBR-w960dp-h540dp-land-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class RemoteThemeFixTest {
 @Before public void clear(){LocalPreferences.get(RuntimeEnvironment.getApplication()).edit().clear().putBoolean("reduced_motion",true).commit();}
 @Test public void remoteCanConfirmFirstMiddleAndLastWithoutCrossingSaveButton(){
  for(String theme:new String[]{LauncherTheme.DEFAULT,LauncherTheme.SMOKED,LauncherTheme.IOS,LauncherTheme.IOS_DARK}){
   LauncherTheme.select(RuntimeEnvironment.getApplication(),LauncherTheme.IOS.equals(theme)?LauncherTheme.DEFAULT:LauncherTheme.IOS);
   try(var c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
    int[] applied={0};ThemePicker.show(c.get(),()->applied[0]++);Dialog dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog();View root=dialog.getWindow().getDecorView();OptionalThemeTest.layout(root,860,440);org.robolectric.shadows.ShadowLooper.idleMainLooper();String old=LauncherTheme.selected(c.get());long revision=LocalPreferences.revision(c.get());
    for(String candidate:new String[]{LauncherTheme.DEFAULT,LauncherTheme.SMOKED,LauncherTheme.IOS,theme}){root.findViewWithTag("theme-choice-"+candidate).requestFocusFromTouch();assertEquals(old,LauncherTheme.selected(c.get()));assertEquals(revision,LocalPreferences.revision(c.get()));}
    View option=root.findViewWithTag("theme-choice-"+theme);option.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));option.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));assertEquals(theme,LauncherTheme.selected(c.get()));assertFalse(dialog.isShowing());assertEquals(1,applied[0]);assertNull(root.findViewWithTag("theme-apply"));
   }
  }
 }
 @Test public void iosAppsPrecedeFeedsAndThemeSwitchRestoresOriginalParent() throws Exception {
  LauncherTheme.select(RuntimeEnvironment.getApplication(),LauncherTheme.IOS);
  try(var c=Robolectric.buildActivity(MainActivity.class).setup().visible()){
   MainActivity a=c.get();View grid=a.findViewById(R.id.grid),gridBox=(View)grid.getParent();ViewGroup body=(ViewGroup)gridBox.getParent();java.lang.reflect.Field field=MainActivity.class.getDeclaredField("mediaPanel");field.setAccessible(true);HomeMediaPanel panel=(HomeMediaPanel)field.get(a);java.lang.reflect.Field cf=HomeMediaPanel.class.getDeclaredField("content");cf.setAccessible(true);View feeds=(View)cf.get(panel);assertSame(body,feeds.getParent());assertTrue(body.indexOfChild(feeds)>body.indexOfChild(gridBox));assertFalse(panel.focusFromDock());
   ImageView wifi=a.findViewById(R.id.btnWifi);assertEquals(36,wifi.getLayoutParams().width);assertEquals(9,wifi.getPaddingLeft());java.lang.reflect.Method refresh=MainActivity.class.getDeclaredMethod("updateWifiLabel");refresh.setAccessible(true);refresh.invoke(a);assertEquals(18,wifi.getDrawable().getIntrinsicWidth());c.pause();LauncherTheme.select(a,LauncherTheme.DEFAULT);c.resume();assertSame(body,feeds.getParent());assertEquals(42,wifi.getLayoutParams().width);assertEquals(10,wifi.getPaddingLeft());refresh.invoke(a);assertEquals(22,wifi.getDrawable().getIntrinsicWidth());c.pause();LauncherTheme.select(a,LauncherTheme.IOS);c.resume();assertSame(body,feeds.getParent());assertEquals(3,body.getChildCount());
  }
 }
 @Test public void enablingFeedsRequestsTheirDeclaredPermissionAndAppsOnlyDoesNot() throws Exception {
  try(var c=Robolectric.buildActivity(LauncherPreferencesActivity.class).setup().visible()){
   var shadow=org.robolectric.Shadows.shadowOf(c.get());assertNull(shadow.getLastRequestedPermission());LocalPreferences.get(c.get()).edit().putBoolean(DynamicRows.ALL,true).commit();java.lang.reflect.Method m=LauncherPreferencesActivity.class.getDeclaredMethod("ensureMediaAccess");m.setAccessible(true);m.invoke(c.get());assertNotNull(shadow.getLastRequestedPermission());assertArrayEquals(new String[]{"android.permission.READ_TV_LISTINGS"},shadow.getLastRequestedPermission().requestedPermissions);
  }
 }
 @Test public void listenerSettingMatchesTheExactService(){
  android.content.Context c=RuntimeEnvironment.getApplication();android.provider.Settings.Secure.putString(c.getContentResolver(),"enabled_notification_listeners","unrelated.app/.Service");assertFalse(MediaAccess.listenerAllowed(c));android.provider.Settings.Secure.putString(c.getContentResolver(),"enabled_notification_listeners","unrelated.app/.Service:com.gathin.tvhome/.PlaybackAccessService");assertTrue(MediaAccess.listenerAllowed(c));
 }
 @Test public void freshDiagnosticsDistinguishDisabledRowsAndDeniedMediaAccess(){
  android.content.Context c=RuntimeEnvironment.getApplication();DynamicRows.migrate(c);String off=MediaDiagnostics.read(c);assertTrue(off.contains("watch_next_row: OFF"));LocalPreferences.get(c).edit().putBoolean(DynamicRows.ALL,true).commit();String on=MediaDiagnostics.read(c);assertTrue(on.contains("watch_next_row: ON"));assertTrue(on.contains("Spotify row: ON"));assertTrue(on.contains("READ_TV_LISTINGS:"));assertTrue(on.contains("Media access:"));
 }
}
