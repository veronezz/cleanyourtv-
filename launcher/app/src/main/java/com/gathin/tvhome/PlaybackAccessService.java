package com.gathin.tvhome;

import android.media.session.*;
import android.os.*;
import java.util.*;

/** Owner-authorized media sessions only. Notification messages are never read. */
public final class PlaybackAccessService
    extends android.service.notification.NotificationListenerService {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Map<MediaController, MediaController.Callback> watched = new HashMap<>();
  private MediaSessionManager manager;
  private final android.content.SharedPreferences.OnSharedPreferenceChangeListener preferences =
      (p, key) -> {
        if ((RecentMedia.ENABLED.equals(key) || RecentMedia.APPS.equals(key)) && manager != null)
          try {
            bind(
                manager.getActiveSessions(
                    new android.content.ComponentName(this, PlaybackAccessService.class)));
          } catch (RuntimeException ignored) {
          }
      };

  @Override
  public void onCreate() {
    super.onCreate();
    LocalPreferences.get(this).registerOnSharedPreferenceChangeListener(preferences);
  }

  private final MediaSessionManager.OnActiveSessionsChangedListener listener = this::bind;

  @Override
  public void onListenerConnected() {
    super.onListenerConnected();
    manager = getSystemService(MediaSessionManager.class);
    try {
      if (manager != null) {
        manager.addOnActiveSessionsChangedListener(
            listener,
            new android.content.ComponentName(this, PlaybackAccessService.class),
            handler);
        bind(
            manager.getActiveSessions(
                new android.content.ComponentName(this, PlaybackAccessService.class)));
      }
    } catch (RuntimeException ignored) {
    }
  }

  private void bind(List<MediaController> controllers) {
    unbind();
    if (!RecentMedia.enabled(this)) return;
    Set<String> apps = RecentMedia.apps(this);
    for (MediaController controller : controllers)
      if (apps.contains(controller.getPackageName())) {
        MediaController.Callback callback =
            new MediaController.Callback() {
              @Override
              public void onMetadataChanged(android.media.MediaMetadata data) {
                RecentMedia.capture(PlaybackAccessService.this, controller);
              }

              @Override
              public void onPlaybackStateChanged(PlaybackState state) {
                RecentMedia.capture(PlaybackAccessService.this, controller);
              }

              @Override
              public void onSessionDestroyed() {
                controller.unregisterCallback(this);
                watched.remove(controller);
              }
            };
        watched.put(controller, callback);
        controller.registerCallback(callback, handler);
        RecentMedia.capture(this, controller);
      }
  }

  private void unbind() {
    for (Map.Entry<MediaController, MediaController.Callback> e : watched.entrySet())
      e.getKey().unregisterCallback(e.getValue());
    watched.clear();
  }

  @Override
  public void onListenerDisconnected() {
    unbind();
    if (manager != null)
      try {
        manager.removeOnActiveSessionsChangedListener(listener);
      } catch (RuntimeException ignored) {
      }
    super.onListenerDisconnected();
  }

  @Override
  public void onDestroy() {
    LocalPreferences.get(this).unregisterOnSharedPreferenceChangeListener(preferences);
    unbind();
    if (manager != null)
      try {
        manager.removeOnActiveSessionsChangedListener(listener);
      } catch (RuntimeException ignored) {
      }
    handler.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
