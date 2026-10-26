package com.gathin.tvhome;

import android.content.*;
import android.content.pm.*;
import android.content.res.*;
import android.graphics.drawable.Drawable;
import android.util.Xml;
import java.io.*;
import java.util.*;
import org.xmlpull.v1.XmlPullParser;

/** Reads resources from installed icon packs; never launches the pack or changes app components. */
final class IconPacks {
  static final String KEY = "icon_pack", MODE = "icon_pack_mode", MANUAL = "pack_icon:";

  static final class Pack {
    final String pkg, label;

    Pack(String pkg, String label) {
      this.pkg = pkg;
      this.label = label;
    }
  }

  static final class Snapshot {
    final String pkg, stamp;
    final Resources resources;
    final IconPackIndex index;

    Snapshot(String pkg, String stamp, Resources resources, IconPackIndex index) {
      this.pkg = pkg;
      this.stamp = stamp;
      this.resources = resources;
      this.index = index;
    }

    Drawable image(String name) {
      if (IconPackIndex.drawable(name).isEmpty()) return null;
      int id = resources.getIdentifier(name, "drawable", pkg);
      if (id == 0) id = resources.getIdentifier(name, "mipmap", pkg);
      try {
        return id == 0 ? null : resources.getDrawable(id, null);
      } catch (Resources.NotFoundException e) {
        return null;
      }
    }

    List<String> images() {
      List<String> out = new ArrayList<>();
      for (String name : index.images())
        if (resources.getIdentifier(name, "drawable", pkg) != 0
            || resources.getIdentifier(name, "mipmap", pkg) != 0) out.add(name);
      return out;
    }
  }

  private static volatile Snapshot current;

  static String selected(Context c) {
    return LocalPreferences.get(c).getString(KEY, "");
  }

  static String label(Context c) {
    String pkg = selected(c);
    if (pkg.isEmpty()) return "Originais dos apps";
    try {
      return c.getPackageManager()
          .getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0))
          .toString();
    } catch (Exception e) {
      return "Pack indisponível";
    }
  }

  static void resetCache() {
    current = null;
    AppArtwork.trim();
  }

  static String cacheKey(Context c, String pkg) {
    Snapshot snap = current;
    return selected(c)
        + "|"
        + (snap == null ? "missing" : snap.stamp)
        + "|"
        + LocalPreferences.get(c).getString(MODE, "missing")
        + "|"
        + selection(c, pkg);
  }

  static String selection(Context c, String pkg) {
    return LocalPreferences.get(c).getString(MANUAL + pkg, "");
  }

  static void assign(Context c, String pkg, String name) {
    LocalPreferences.save(c, LocalPreferences.get(c).edit().putString(MANUAL + pkg, name));
    AppArtwork.invalidate(pkg);
  }

  static void clearAssignment(Context c, String pkg) {
    LocalPreferences.save(c, LocalPreferences.get(c).edit().remove(MANUAL + pkg));
    AppArtwork.invalidate(pkg);
  }

  static boolean contains(Resources r, String pkg) {
    return r.getIdentifier("appfilter", "xml", pkg) != 0
        || r.getIdentifier("appfilter", "raw", pkg) != 0
        || hasAsset(r, "appfilter.xml");
  }

  private static boolean hasAsset(Resources r, String name) {
    try (InputStream in = r.getAssets().open(name)) {
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  static List<Pack> installed(Context c) {
    List<Pack> out = new ArrayList<>();
    PackageManager pm = c.getPackageManager();
    for (ApplicationInfo info : pm.getInstalledApplications(0)) {
      if (!info.enabled || info.packageName.equals(c.getPackageName())) continue;
      try {
        Resources res = pm.getResourcesForApplication(info);
        if (contains(res, info.packageName))
          out.add(
              new Pack(
                  info.packageName,
                  Cards.cleanLabel(String.valueOf(info.loadLabel(pm)), info.packageName)));
      } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
      }
    }
    out.sort(Comparator.comparing(p -> p.label, String.CASE_INSENSITIVE_ORDER));
    return out;
  }

  // Called once on the app-list worker, including after returning from another app.
  static Snapshot prepare(Context c) {
    String pkg = selected(c);
    if (pkg.isEmpty()) {
      current = null;
      return null;
    }
    try {
      PackageInfo info = c.getPackageManager().getPackageInfo(pkg, 0);
      if (!info.applicationInfo.enabled) {
        current = null;
        return null;
      }
      String stamp =
          pkg
              + "/"
              + info.lastUpdateTime
              + "/"
              + (android.os.Build.VERSION.SDK_INT >= 28
                  ? info.getLongVersionCode()
                  : info.versionCode);
      Snapshot existing = current;
      if (existing != null && existing.stamp.equals(stamp)) return existing;
      Snapshot loaded = read(c, pkg, stamp);
      current = loaded;
      AppArtwork.trim();
      return loaded;
    } catch (Exception e) {
      current = null;
      return null;
    }
  }

  static Snapshot read(Context c, String pkg, String stamp) throws Exception {
    Resources res = c.getPackageManager().getResourcesForApplication(pkg);
    IconPackIndex index = new IconPackIndex();
    // Resources precede assets: the Projectivy APK keeps only template entries in assets.
    boolean compiled = parseResource(res, pkg, "appfilter", index);
    if (!compiled || index.size() == 0) parseAsset(res, "appfilter.xml", index);
    if (!parseResource(res, pkg, "drawable", index)) parseAsset(res, "drawable.xml", index);
    return new Snapshot(pkg, stamp, res, index);
  }

  private static boolean parseResource(
      Resources res, String pkg, String name, IconPackIndex index) {
    int id = res.getIdentifier(name, "xml", pkg);
    if (id != 0)
      try (XmlResourceParser parser = res.getXml(id)) {
        parse(parser, res, index);
        return true;
      } catch (Exception ignored) {
      }
    id = res.getIdentifier(name, "raw", pkg);
    if (id != 0)
      try (InputStream in = res.openRawResource(id)) {
        parseText(in, res, index);
        return true;
      } catch (Exception ignored) {
      }
    return false;
  }

  private static void parseAsset(Resources res, String name, IconPackIndex index) {
    try (InputStream in = res.getAssets().open(name)) {
      parseText(in, res, index);
    } catch (Exception ignored) {
    }
  }

  private static void parseText(InputStream in, Resources res, IconPackIndex index)
      throws Exception {
    XmlPullParser parser = Xml.newPullParser();
    parser.setInput(new LimitedInput(in), "UTF-8");
    parse(parser, res, index);
  }

  private static void parse(XmlPullParser parser, Resources res, IconPackIndex index)
      throws Exception {
    for (int event = parser.getEventType(), events = 0;
        event != XmlPullParser.END_DOCUMENT && events++ < 300000;
        event = parser.next()) {
      if (event == XmlPullParser.DOCDECL) throw new IOException("DTD unsupported");
      if (event != XmlPullParser.START_TAG || !"item".equals(parser.getName())) continue;
      String name = parser.getAttributeValue(null, "drawable");
      if (parser instanceof android.util.AttributeSet) {
        int id =
            ((android.util.AttributeSet) parser).getAttributeResourceValue(null, "drawable", 0);
        if (id != 0)
          try {
            name = res.getResourceEntryName(id);
          } catch (Exception ignored) {
          }
      }
      index.add(parser.getAttributeValue(null, "component"), name);
    }
  }

  private static final class LimitedInput extends FilterInputStream {
    int total;

    LimitedInput(InputStream in) {
      super(in);
    }

    private void count(int n) throws IOException {
      total += Math.max(0, n);
      if (total > 8 * 1024 * 1024) throw new IOException("Map too large");
    }

    public int read() throws IOException {
      int b = in.read();
      if (b >= 0) count(1);
      return b;
    }

    public int read(byte[] b, int off, int len) throws IOException {
      int n = in.read(b, off, len);
      count(n);
      return n;
    }
  }

  static Drawable artwork(Context c, ResolveInfo app, boolean hasBanner) {
    Snapshot snap = current;
    if (snap == null || !snap.pkg.equals(selected(c))) return null;
    String raw = selection(c, app.activityInfo.packageName), prefix = snap.pkg + "|";
    if (raw.startsWith(prefix)) {
      Drawable manual = snap.image(raw.substring(prefix.length()));
      if (manual != null) return manual;
    }
    if (hasBanner && !"all".equals(LocalPreferences.get(c).getString(MODE, "missing"))) return null;
    String name = snap.index.find(app.activityInfo.packageName + "/" + app.activityInfo.name);
    return name == null ? null : snap.image(name);
  }
}
