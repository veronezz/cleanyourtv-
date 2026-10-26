package com.gathin.tvhome;

/** Short display labels without control characters or split surrogate pairs. */
final class DisplayLabel {
  private static final java.util.regex.Pattern CONTROLS =
      java.util.regex.Pattern.compile("[\\p{Cntrl}\\p{Cf}]");

  static String clean(String value) {
    String clean = value == null ? "" : CONTROLS.matcher(value).replaceAll("").trim();
    if (clean.length() > 80) {
      int end = Character.isHighSurrogate(clean.charAt(79)) ? 79 : 80;
      clean = clean.substring(0, end);
    }
    return clean;
  }
}
