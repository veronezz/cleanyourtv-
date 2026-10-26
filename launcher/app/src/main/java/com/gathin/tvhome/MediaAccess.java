package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;

/** Reads the public listener setting; does not grant itself access or poll media. */
final class MediaAccess {
  static void openListenerSettings(android.app.Activity activity) {
    try {
      activity.startActivity(
          new android.content.Intent(
              android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    } catch (android.content.ActivityNotFoundException | SecurityException unavailable) {
      new android.app.AlertDialog.Builder(activity, GlassUi.dialogTheme(activity))
          .setMessage("Este sistema não oferece a tela de autorização de mídia.")
          .setPositiveButton("OK", null)
          .show();
    }
  }

  static boolean listenerAllowed(Context c) {
    String value =
        android.provider.Settings.Secure.getString(
            c.getContentResolver(), "enabled_notification_listeners");
    if (value == null) return false;
    ComponentName own = new ComponentName(c, PlaybackAccessService.class);
    for (String item : value.split(":")) {
      ComponentName found = ComponentName.unflattenFromString(item);
      if (own.equals(found)) return true;
    }
    return false;
  }
}
