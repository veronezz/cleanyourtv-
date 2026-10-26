package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/**
 * The standard settings intent, or, when nothing usable answers it, the first vendor settings entry
 * from settings_fallback_components that is installed (Xiaomi China, TCL / FFALCON, Fire OS).
 */
final class SystemSettings {
  private SystemSettings() {}

  static Intent appInfo(Context context, String pkg) {
    Intent intent =
        new Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:" + pkg));
    return context.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            == null
        ? null
        : intent;
  }

  static Intent intent(Context context, String action) {
    PackageManager pm = context.getPackageManager();
    Intent standard = new Intent(action);
    ResolveInfo resolved = pm.resolveActivity(standard, PackageManager.MATCH_DEFAULT_ONLY);
    // Fire OS resolves the standard actions to a CTS compliance stub that opens nothing;
    // treat it as missing so the vendor fallback runs.
    if (resolved != null && !resolved.activityInfo.name.contains("CTSDummy")) return standard;

    for (String value :
        context.getResources().getStringArray(R.array.settings_fallback_components)) {
      ComponentName component = ComponentName.unflattenFromString(value);
      if (component == null) continue;
      // Explicit resolveActivity() always succeeds, so check the package exists instead.
      try {
        android.content.pm.ActivityInfo info = pm.getActivityInfo(component, 0);
        if (!info.enabled || !info.applicationInfo.enabled) continue;
      } catch (PackageManager.NameNotFoundException e) {
        continue;
      }
      return new Intent().setComponent(component);
    }
    return standard;
  }
}
