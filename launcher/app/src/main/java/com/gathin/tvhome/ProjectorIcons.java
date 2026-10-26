package com.gathin.tvhome;

import android.app.*;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import java.util.function.Consumer;

final class ProjectorIcons {
  static final String PREFIX = "projector_icon:";
  static final String[] KEYS = {"original", "usb", "focus", "keystone", "brightness"};
  static final String[] LABELS = {
    "Ícone original do app", "Mídias USB", "Ajuste de foco", "Enquadramento de imagem", "Brilho"
  };

  static int resource(String key) {
    switch (key) {
      case "usb":
        return R.drawable.ic_projector_usb;
      case "focus":
        return R.drawable.ic_projector_focus;
      case "keystone":
        return R.drawable.ic_projector_keystone;
      case "brightness":
        return R.drawable.ic_projector_brightness;
      default:
        return 0;
    }
  }

  static String selected(Context c, String component) {
    return LocalPreferences.get(c).getString(PREFIX + component, "original");
  }

  static String label(String key) {
    for (int i = 0; i < KEYS.length; i++) if (KEYS[i].equals(key)) return LABELS[i];
    return LABELS[0];
  }

  static void save(Context c, String component, String key) {
    LocalPreferences.save(c, LocalPreferences.get(c).edit().putString(PREFIX + component, key));
  }

  static void choose(Activity a, String initial, Consumer<String> choice) {
    ArrayAdapter<String> adapter =
        new ArrayAdapter<String>(a, android.R.layout.simple_list_item_1, LABELS) {
          public View getView(int position, View recycled, ViewGroup parent) {
            TextView row = (TextView) super.getView(position, recycled, parent);
            int res = resource(KEYS[position]);
            Drawable icon =
                a.getDrawable(res == 0 ? R.drawable.ic_preferences_projector : res).mutate();
            icon.setTint(IosStyle.text(a, 0xffc5f3fa));
            int size = GlassUi.dp(a, 32);
            icon.setBounds(0, 0, size, size);
            row.setCompoundDrawables(icon, null, null, null);
            row.setCompoundDrawablePadding(GlassUi.dp(a, 14));
            row.setText(LABELS[position] + (KEYS[position].equals(initial) ? " ✓" : ""));
            return row;
          }
        };
    new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
        .setTitle("Ícone do atalho")
        .setAdapter(adapter, (d, n) -> choice.accept(KEYS[n]))
        .setNegativeButton("Cancelar", null)
        .show();
  }
}
