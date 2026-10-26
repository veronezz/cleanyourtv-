package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Maps the remote's Input button to the system input switcher, from any app. Remotes such as the
 * Chromecast voice remote send KEYCODE_TV for it; on Google TV the stock launcher handles that key,
 * so with the stock launcher disabled the button does nothing. Only KEYCODE_TV is consumed; every
 * other key passes through untouched. Opt-in: INPUT_KEY_SERVICE=1 build-and-install.sh (or
 * -InputKeyService on Windows) enables it.
 */
public class InputKeyService extends AccessibilityService {

  private static final String ACTION_VIEW_INPUTS = "com.android.tv.action.VIEW_INPUTS";

  private RemoteKeys.Bindings bindings;
  private int[] inputKeycodes = new int[0];

  @Override
  protected void onServiceConnected() {
    super.onServiceConnected();
    bindings = new RemoteKeys.Bindings(this);
    inputKeycodes = getResources().getIntArray(R.array.input_key_keycodes);
  }

  @Override
  protected boolean onKeyEvent(KeyEvent event) {
    // Pass wake/power and other keys through while the display is off.
    if (bindings == null || !bindings.interactive()) return false;
    if (bindings.handle(event)) return true;
    boolean input =
        event.getKeyCode() == KeyEvent.KEYCODE_TV
            || event.getKeyCode() == KeyEvent.KEYCODE_TV_INPUT;
    for (int code : inputKeycodes) if (code == event.getKeyCode()) input = true;
    if (!input) return false;
    if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
      try {
        startActivity(new Intent(ACTION_VIEW_INPUTS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      } catch (ActivityNotFoundException | SecurityException ignored) {
      }
    }
    return true;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {}

  @Override
  public void onInterrupt() {}
}
