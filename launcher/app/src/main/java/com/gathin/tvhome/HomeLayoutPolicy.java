package com.gathin.tvhome;

/** Density-independent spacing profiles and bounded focus navigation. */
final class HomeLayoutPolicy {
  static String profile(String value) {
    return "balanced".equals(value) || "relaxed".equals(value) ? value : "compact";
  }

  static int gapDp(String p) {
    return "relaxed".equals(p) ? 24 : "balanced".equals(p) ? 16 : 8;
  }

  static int gridBottomDp(String p) {
    return "relaxed".equals(p) ? 22 : "balanced".equals(p) ? 14 : 8;
  }

  static int clockGap(int line, String p) {
    return Math.round(
        Math.max(0, line) * ("small".equals(p) ? .5f : "large".equals(p) ? 1.25f : 1f));
  }

  static int headingTopDp(String p) {
    return "relaxed".equals(p) ? 10 : "balanced".equals(p) ? 6 : 4;
  }

  static int rowPadding(int tileHeight, float density, String p) {
    int base = "relaxed".equals(p) ? 14 : "balanced".equals(p) ? 10 : 6;
    return Math.max(
        Math.round(base * density),
        (int) Math.ceil(tileHeight * (AppAdapter.FOCUS_SCALE - 1) / 2f - 4 * density));
  }

  static int target(
      int current,
      int viewport,
      int paddingTop,
      int paddingBottom,
      int top,
      int bottom,
      int maximum,
      int breathing) {
    int start = current + paddingTop + breathing,
        end = current + viewport - paddingBottom - breathing;
    int wanted = current;
    if (bottom - top > end - start || top < start) wanted += top - start;
    else if (bottom > end) wanted += bottom - end;
    return Math.max(0, Math.min(maximum, wanted));
  }

  static int duration(int from, int to, int viewport) {
    if (from == to) return 0;
    return Math.max(
        180, Math.min(300, 180 + Math.round(110f * Math.abs(to - from) / Math.max(1, viewport))));
  }
}
