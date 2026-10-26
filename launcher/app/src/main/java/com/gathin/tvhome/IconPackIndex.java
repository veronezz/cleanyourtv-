package com.gathin.tvhome;

import java.util.*;

/** Standard appfilter keys, with conservative fallback for differing TV/mobile activities. */
final class IconPackIndex {
  private static final java.util.regex.Pattern COMPONENT =
      java.util.regex.Pattern.compile("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+");
  private static final java.util.regex.Pattern DRAWABLE =
      java.util.regex.Pattern.compile("[a-z][a-z0-9_]*");
  static final int MAX_ITEMS = 50000, MAX_IMAGES = 10000;
  private final Map<String, String> components = new LinkedHashMap<>(), packages = new HashMap<>();
  private final Set<String> ambiguous = new HashSet<>(), images = new LinkedHashSet<>();

  static String component(String value) {
    if (value == null) return "";
    String s = value.trim();
    if (s.startsWith("ComponentInfo{") && s.endsWith("}")) s = s.substring(14, s.length() - 1);
    if (s.length() > 1024 || !COMPONENT.matcher(s).matches()) return "";
    int slash = s.indexOf('/');
    String pkg = s.substring(0, slash), activity = s.substring(slash + 1);
    if (activity.startsWith(".")) activity = pkg + activity;
    return pkg + "/" + activity;
  }

  static String drawable(String value) {
    if (value == null) return "";
    String s = value.trim();
    if (s.startsWith("@drawable/")) s = s.substring(10);
    else if (s.startsWith("drawable/")) s = s.substring(9);
    return s.length() <= 200 && DRAWABLE.matcher(s).matches() ? s : "";
  }

  void add(String rawComponent, String rawDrawable) {
    String name = drawable(rawDrawable);
    if (name.isEmpty()) return;
    if (images.size() < MAX_IMAGES) images.add(name);
    String key = component(rawComponent);
    if (key.isEmpty() || components.size() >= MAX_ITEMS) return;
    if (components.containsKey(key)) return;
    components.put(key, name);
    String pkg = key.substring(0, key.indexOf('/'));
    String old = packages.get(pkg);
    if (old != null && !old.equals(name)) {
      ambiguous.add(pkg);
      packages.remove(pkg);
    } else if (!ambiguous.contains(pkg)) packages.put(pkg, name);
  }

  String find(String rawComponent) {
    String key = component(rawComponent);
    String exact = components.get(key);
    if (exact != null) return exact;
    int slash = key.indexOf('/');
    return slash < 0 ? null : packages.get(key.substring(0, slash));
  }

  List<String> images() {
    return new ArrayList<>(images);
  }

  int size() {
    return components.size();
  }
}
