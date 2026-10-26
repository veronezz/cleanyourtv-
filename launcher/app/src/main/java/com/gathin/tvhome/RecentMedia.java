package com.gathin.tvhome;

import android.app.*;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.media.*;
import android.media.session.*;
import java.util.*;
import org.json.*;

/**
 * Opt-in latest session per selected app. No notification text, private history or guessed episode
 * links.
 */
final class RecentMedia {
  static final String ENABLED = "recent_media_enabled",
      APPS = "recent_media_apps",
      DATA = "recent_media_cache";
  private static final Map<String, PendingIntent> pending = new HashMap<>();

  static boolean enabled(Context c) {
    return LocalPreferences.get(c).getBoolean(ENABLED, false);
  }

  static Set<String> apps(Context c) {
    return new HashSet<>(LocalPreferences.get(c).getStringSet(APPS, Collections.emptySet()));
  }

  static synchronized void clear(Context c) {
    pending.clear();
    LocalPreferences.save(
        c, LocalPreferences.get(c).edit().remove(DATA).putBoolean(ENABLED, false));
  }

  static synchronized void capture(Context c, MediaController controller) {
    try {
      captureInternal(c, controller);
    } catch (RuntimeException ignored) {
    }
  }

  private static void captureInternal(Context c, MediaController controller) {
    if (!enabled(c)) return;
    String pkg = controller.getPackageName();
    if (!apps(c).contains(pkg) || UniTvRepository.PKG.equals(pkg)) return;
    MediaMetadata data = controller.getMetadata();
    PlaybackState state = controller.getPlaybackState();
    if (data == null || state == null) return;
    if (state.getState() != PlaybackState.STATE_PLAYING
        && state.getState() != PlaybackState.STATE_PAUSED
        && state.getState() != PlaybackState.STATE_BUFFERING) return;
    String title = data.getString(MediaMetadata.METADATA_KEY_TITLE);
    if (title == null || title.trim().isEmpty()) return;
    title = DisplayLabel.clean(title);
    long pos = state.getPosition(), duration = data.getLong(MediaMetadata.METADATA_KEY_DURATION);
    Intent launch = c.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
    if (launch == null) launch = c.getPackageManager().getLaunchIntentForPackage(pkg);
    if (launch == null) return;
    PendingIntent session = controller.getSessionActivity();
    if (session != null) pending.put(pkg, session);
    else pending.remove(pkg);
    try {
      JSONArray old = stored(c), next = new JSONArray();
      JSONObject previous = null;
      for (int i = 0; i < old.length(); i++) {
        JSONObject r = old.optJSONObject(i);
        if (r != null && pkg.equals(r.optString("pkg"))) previous = r;
      }
      if (previous != null
          && title.equals(previous.optString("title"))
          && pos == previous.optLong("position")
          && duration == previous.optLong("duration")
          && state.getState() == previous.optInt("state")) return;
      JSONObject row = new JSONObject();
      row.put("pkg", pkg);
      row.put("title", title);
      row.put("position", pos);
      row.put("duration", duration);
      row.put("state", state.getState());
      row.put("time", System.currentTimeMillis());
      row.put("intent", launch.toUri(Intent.URI_INTENT_SCHEME));
      String art = data.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
      if (art == null || art.isEmpty()) art = data.getString(MediaMetadata.METADATA_KEY_ART_URI);
      if ((art == null || art.isEmpty()) && data.getDescription().getIconUri() != null)
        art = data.getDescription().getIconUri().toString();
      row.put("art", art == null ? "" : art);
      next.put(row);
      for (int i = 0; i < old.length() && next.length() < 32; i++) {
        JSONObject r = old.optJSONObject(i);
        if (r != null && !pkg.equals(r.optString("pkg"))) next.put(r);
      }
      Set<String> retained = new HashSet<>();
      for (int i = 0; i < next.length(); i++) retained.add(next.getJSONObject(i).optString("pkg"));
      pending.keySet().retainAll(retained);
      LocalPreferences.get(c).edit().putString(DATA, next.toString()).apply();
    } catch (JSONException ignored) {
    }
  }

  private static JSONArray stored(Context c) {
    try {
      return new JSONArray(LocalPreferences.get(c).getString(DATA, "[]"));
    } catch (JSONException e) {
      return new JSONArray();
    }
  }

  static void sample(Context c) {
    if (!enabled(c) || !MediaAccess.listenerAllowed(c)) return;
    try {
      MediaSessionManager m = c.getSystemService(MediaSessionManager.class);
      if (m != null)
        for (MediaController controller :
            m.getActiveSessions(new ComponentName(c, PlaybackAccessService.class)))
          capture(c, controller);
    } catch (RuntimeException ignored) {
    }
  }

  static synchronized List<TvContentRepository.Item> read(Context c) {
    List<TvContentRepository.Item> result = new ArrayList<>();
    if (!enabled(c) || !MediaAccess.listenerAllowed(c)) return result;
    Set<String> chosen = apps(c);
    try {
      JSONArray rows = new JSONArray(LocalPreferences.get(c).getString(DATA, "[]"));
      for (int i = 0; i < rows.length() && i < 32; i++) {
        JSONObject r = rows.optJSONObject(i);
        if (r == null) continue;
        String pkg = r.optString("pkg");
        if (!chosen.contains(pkg) || UniTvRepository.PKG.equals(pkg)) continue;
        try {
          if (!c.getPackageManager().getApplicationInfo(pkg, 0).enabled) continue;
        } catch (Exception e) {
          continue;
        }
        long pos = r.optLong("position", -1), duration = r.optLong("duration", -1);
        if (duration > 0 && pos >= duration) continue;
        TvContentRepository.Item item =
            new TvContentRepository.Item(
                pkg,
                r.optString("title"),
                "Última sessão · retome dentro do app",
                r.optString("intent"),
                r.optString("art"),
                pos,
                duration,
                r.optLong("time"),
                "Mídia recente",
                true,
                -1);
        result.add(item);
      }
    } catch (JSONException ignored) {
    }
    return result;
  }

  static boolean openActive(Context c, String pkg) {
    PendingIntent intent;
    synchronized (RecentMedia.class) {
      intent = pending.get(pkg);
    }
    if (!enabled(c) || !apps(c).contains(pkg) || !MediaAccess.listenerAllowed(c) || intent == null)
      return false;
    try {
      intent.send();
      return true;
    } catch (PendingIntent.CanceledException | SecurityException e) {
      return false;
    }
  }

  static void options(Activity a, Runnable changed) {
    GlassUi.menu(
        a,
        "Mídias recentes · celular/projetor",
        "Ative a linha Continuar assistindo. Guarda a última sessão dos apps escolhidos; a retomada"
            + " exata depende do app.",
        new String[] {
          enabled(a) ? "Desativar captura de sessões" : "Ativar captura de sessões",
          "Escolher apps · " + apps(a).size() + " selecionados",
          "Autorizar acesso às sessões de mídia",
          "Limpar e desativar captura"
        },
        new String[] {
          "Alternativa independente da programação de TV",
          "Só esses aplicativos terão suas sessões salvas",
          "O Android solicita acesso às sessões pelo serviço de notificações",
          "Apaga os dados locais desta alternativa"
        },
        n -> {
          if (n == 0) {
            LocalPreferences.save(
                a, LocalPreferences.get(a).edit().putBoolean(ENABLED, !enabled(a)));
            changed.run();
          } else if (n == 1) chooseApps(a, changed);
          else if (n == 2) MediaAccess.openListenerSettings(a);
          else {
            clear(a);
            changed.run();
          }
        });
  }

  static void chooseApps(Activity a, Runnable changed) {
    AlertDialog progress =
        new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
            .setMessage("Buscando aplicativos…")
            .setNegativeButton("Cancelar", null)
            .show();
    new Thread(
            () -> {
              try {
                List<ResolveInfo> found = AppLibrary.discover(a);
                found.removeIf(r -> UniTvRepository.PKG.equals(r.activityInfo.packageName));
                String[] labels = new String[found.size()];
                boolean[] checked = new boolean[found.size()];
                Set<String> selected = apps(a);
                for (int i = 0; i < found.size(); i++) {
                  labels[i] = String.valueOf(found.get(i).loadLabel(a.getPackageManager()));
                  checked[i] = selected.contains(found.get(i).activityInfo.packageName);
                }
                a.runOnUiThread(
                    () -> {
                      if (a.isDestroyed() || a.isFinishing() || !progress.isShowing()) return;
                      progress.dismiss();
                      new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
                          .setTitle("Apps para mídias recentes")
                          .setMultiChoiceItems(labels, checked, (d, n, on) -> checked[n] = on)
                          .setPositiveButton(
                              "Salvar",
                              (d, n) -> {
                                Set<String> chosen = new HashSet<>();
                                for (int i = 0; i < checked.length; i++)
                                  if (checked[i]) chosen.add(found.get(i).activityInfo.packageName);
                                LocalPreferences.save(
                                    a, LocalPreferences.get(a).edit().putStringSet(APPS, chosen));
                                changed.run();
                              })
                          .setNegativeButton("Cancelar", null)
                          .show();
                    });
              } catch (RuntimeException unavailable) {
                a.runOnUiThread(
                    () -> {
                      if (a.isDestroyed() || a.isFinishing() || !progress.isShowing()) return;
                      progress.dismiss();
                      android.widget.Toast.makeText(
                              a,
                              "Não foi possível listar os aplicativos.",
                              android.widget.Toast.LENGTH_SHORT)
                          .show();
                    });
              }
            },
            "recent-media-apps")
        .start();
  }
}
