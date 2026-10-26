package com.gathin.tvhome;

import android.app.*;
import android.content.pm.ResolveInfo;
import java.util.*;

/** Explicit home selection; saving only touches the packages in the displayed snapshot. */
final class HomeAppPicker {
  static void show(Activity a, Runnable changed) {
    AlertDialog loading =
        new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
            .setMessage("Buscando aplicativos…")
            .setNegativeButton("Cancelar", null)
            .show();
    new Thread(
            () -> {
              try {
                List<ResolveInfo> apps = AppLibrary.discover(a);
                String[] labels = new String[apps.size()];
                boolean[] checked = new boolean[apps.size()];
                Set<String> hidden = new HashSet<>(Config.load(a).hiddenApps);
                for (int i = 0; i < apps.size(); i++) {
                  ResolveInfo app = apps.get(i);
                  String pkg = app.activityInfo.packageName;
                  labels[i] =
                      Cards.cleanLabel(String.valueOf(app.loadLabel(a.getPackageManager())), pkg)
                          + "\n"
                          + pkg;
                  checked[i] = !hidden.contains(pkg);
                }
                a.runOnUiThread(
                    () -> {
                      if (a.isDestroyed() || a.isFinishing() || !loading.isShowing()) return;
                      loading.dismiss();
                      new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
                          .setTitle("Apps na tela inicial")
                          .setMultiChoiceItems(
                              labels, checked, (d, index, on) -> checked[index] = on)
                          .setPositiveButton(
                              "Salvar",
                              (d, which) -> {
                                Set<String> excluded = LocalPreferences.hiddenApps(a),
                                    included =
                                        new HashSet<>(
                                            LocalPreferences.get(a)
                                                .getStringSet(
                                                    "visible_owner_apps", Collections.emptySet()));
                                for (int i = 0; i < apps.size(); i++) {
                                  String pkg = apps.get(i).activityInfo.packageName;
                                  if (checked[i]) {
                                    included.add(pkg);
                                    excluded.remove(pkg);
                                  } else {
                                    included.remove(pkg);
                                    excluded.add(pkg);
                                  }
                                }
                                LocalPreferences.save(
                                    a,
                                    LocalPreferences.get(a)
                                        .edit()
                                        .putStringSet("hidden_owner_apps", excluded)
                                        .putStringSet("visible_owner_apps", included));
                                changed.run();
                              })
                          .setNegativeButton("Cancelar", null)
                          .show();
                    });
              } catch (RuntimeException e) {
                a.runOnUiThread(
                    () -> {
                      if (loading.isShowing()) {
                        loading.dismiss();
                        android.widget.Toast.makeText(
                                a, "Não foi possível listar os aplicativos.", 0)
                            .show();
                      }
                    });
              }
            },
            "home-app-picker")
        .start();
  }
}
