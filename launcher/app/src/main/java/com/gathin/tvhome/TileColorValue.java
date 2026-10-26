package com.gathin.tvhome;

/** Persistence format distinguishes default, transparent, and opaque RGB. */
final class TileColorValue {
  static Integer parse(String value) {
    if (value == null || value.equals("default")) return null;
    if (value.equals("transparent")) return 0;
    String rgb = value.startsWith("#") ? value.substring(1) : value;
    if (!rgb.matches("[0-9a-fA-F]{6}")) return null;
    return (int) (0xff000000L | Long.parseLong(rgb, 16));
  }

  static boolean light(Integer value) {
    if (value == null || (value >>> 24) == 0) return false;
    return .2126 * ((value >> 16) & 255) + .7152 * ((value >> 8) & 255) + .0722 * (value & 255)
        > 165;
  }
}
