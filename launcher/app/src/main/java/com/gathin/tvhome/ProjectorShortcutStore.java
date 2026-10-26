package com.gathin.tvhome;

import java.util.*;
import org.json.*;

/** Bounded, local, component-only records: no remote URLs, shell commands or stored credentials. */
final class ProjectorShortcutStore {
  static final int LIMIT = 48;

  static final class Record {
    final String component, label;

    Record(String component, String label) {
      this.component = component;
      this.label = label;
    }
  }

  static boolean valid(String component) {
    return component != null
        && component.length() <= 512
        && component.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+");
  }

  static List<Record> parse(String text) {
    List<Record> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    try {
      JSONArray data = new JSONArray(text);
      for (int i = 0; i < data.length() && out.size() < LIMIT; i++) {
        JSONObject row = data.optJSONObject(i);
        if (row == null) continue;
        String c = row.optString("component", ""),
            name = DisplayLabel.clean(row.optString("label", ""));
        if (valid(c) && !name.isEmpty() && seen.add(c)) out.add(new Record(c, name));
      }
    } catch (Exception ignored) {
    }
    return out;
  }

  static String encode(List<Record> rows) {
    JSONArray data = new JSONArray();
    Set<String> seen = new HashSet<>();
    for (Record row : rows) {
      if (data.length() >= LIMIT) break;
      if (!valid(row.component)
          || DisplayLabel.clean(row.label).isEmpty()
          || !seen.add(row.component)) continue;
      try {
        JSONObject object = new JSONObject();
        object.put("component", row.component);
        object.put("label", DisplayLabel.clean(row.label));
        data.put(object);
      } catch (JSONException ignored) {
      }
    }
    return data.toString();
  }
}
