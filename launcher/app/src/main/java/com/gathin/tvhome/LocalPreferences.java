package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

/** Owner's choices override the optional external theme without modifying its JSON file. */
final class LocalPreferences {
    static SharedPreferences get(Context c) { return c.getSharedPreferences("owner_preferences", Context.MODE_PRIVATE); }
    static long revision(Context c) { return get(c).getLong("revision", 0); }
    static void save(Context c, SharedPreferences.Editor e) {
        e.putLong("revision", revision(c) + 1).apply();
    }
    static java.util.Set<String> hiddenApps(Context c) {
        return new java.util.HashSet<>(get(c).getStringSet("hidden_owner_apps",java.util.Collections.emptySet()));
    }
    static void hideApp(Context c,String pkg) {
        java.util.Set<String> visible=new java.util.HashSet<>(get(c).getStringSet("visible_owner_apps",java.util.Collections.emptySet()));visible.remove(pkg);
        java.util.Set<String> hidden=hiddenApps(c);hidden.add(pkg);
        // Publish locally now; let SharedPreferences persist in the background.
        get(c).edit().putStringSet("visible_owner_apps",visible).putStringSet("hidden_owner_apps",hidden).putLong("revision",revision(c)+1).apply();
    }
    static List<String> order(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(get(c).getString("app_order", "[]"));
            for (int i=0;i<a.length();i++) if (!a.getString(i).isEmpty() && !out.contains(a.getString(i))) out.add(a.getString(i));
        } catch (Exception ignored) { }
        return out;
    }
    static void saveOrder(Context c, List<String> order) {
        save(c, get(c).edit().putString("app_order", new JSONArray(order).toString()));
    }
}
