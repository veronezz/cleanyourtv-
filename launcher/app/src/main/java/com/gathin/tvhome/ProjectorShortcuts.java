package com.gathin.tvhome;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/**
 * Vendor-neutral picker: only enabled, exported activities accessible to this launcher are offered.
 */
final class ProjectorShortcuts {
  static final class Shortcut {
    final String component, label;

    Shortcut(String component, String label) {
      this.component = component;
      this.label = label;
    }
  }

  static boolean enabled(Context c) {
    return LocalPreferences.get(c).getBoolean("projector_mode", false);
  }

  static List<Shortcut> load(Context c) {
    List<Shortcut> out = new ArrayList<>();
    for (ProjectorShortcutStore.Record r :
        ProjectorShortcutStore.parse(
            LocalPreferences.get(c).getString("projector_shortcuts", "[]")))
      out.add(new Shortcut(r.component, r.label));
    return out;
  }

  static void save(Context c, List<Shortcut> rows) {
    List<ProjectorShortcutStore.Record> out = new ArrayList<>();
    for (Shortcut r : rows) out.add(new ProjectorShortcutStore.Record(r.component, r.label));
    LocalPreferences.save(
        c,
        LocalPreferences.get(c)
            .edit()
            .putString("projector_shortcuts", ProjectorShortcutStore.encode(out)));
  }

  static void remove(Context c, String component) {
    List<Shortcut> rows = load(c);
    rows.removeIf(r -> r.component.equals(component));
    save(c, rows);
    LocalPreferences.save(
        c,
        LocalPreferences.get(c)
            .edit()
            .remove(ProjectorIcons.PREFIX + component)
            .remove(TileColors.PREFIX + component));
  }

  private static boolean allowed(Context c, ActivityInfo info) {
    return info.enabled
        && info.applicationInfo.enabled
        && info.exported
        && !info.packageName.equals(c.getPackageName())
        && (info.permission == null
            || info.permission.isEmpty()
            || c.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED);
  }

  static boolean allowed(Context c, ComponentName component) {
    try {
      return allowed(c, c.getPackageManager().getActivityInfo(component, 0));
    } catch (PackageManager.NameNotFoundException | SecurityException e) {
      return false;
    }
  }

  static void open(Context c, ComponentName component) {
    if (!enabled(c) || !allowed(c, component)) {
      Toast.makeText(c, "Função indisponível ou protegida pelo fabricante.", Toast.LENGTH_LONG)
          .show();
      return;
    }
    try {
      c.startActivity(
          new Intent(Intent.ACTION_MAIN)
              .setComponent(component)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    } catch (ActivityNotFoundException | SecurityException e) {
      Toast.makeText(c, "Esta função não pode ser aberta por outro aplicativo.", Toast.LENGTH_LONG)
          .show();
    }
  }

  static void edit(Activity a, String component, String name, Runnable changed) {
    EditText field = new EditText(a);
    field.setSingleLine(true);
    field.setText(name);
    field.setSelectAllOnFocus(true);
    field.setHint("Ex.: Ajuste de imagem ou enquadramento");
    String[] glyph = {ProjectorIcons.selected(a, component)};
    LinearLayout form = new LinearLayout(a);
    form.setOrientation(LinearLayout.VERTICAL);
    int padding = GlassUi.dp(a, 18);
    form.setPadding(padding, 0, padding, 0);
    form.addView(field);
    Button icon = new Button(a);
    icon.setText("Ícone · " + ProjectorIcons.label(glyph[0]));
    form.addView(icon);
    icon.setOnClickListener(
        v ->
            ProjectorIcons.choose(
                a,
                glyph[0],
                selected -> {
                  glyph[0] = selected;
                  icon.setText("Ícone · " + ProjectorIcons.label(selected));
                }));
    AlertDialog dialog =
        new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
            .setTitle("Atalho do projetor")
            .setMessage(
                "Teste a tela e dê um nome ao atalho. Algumas funções exigem dados próprios do"
                    + " fabricante.")
            .setView(form)
            .setPositiveButton("Salvar", null)
            .setNeutralButton("Testar", null)
            .setNegativeButton("Cancelar", null)
            .create();
    dialog.setOnShowListener(
        d -> {
          dialog
              .getButton(AlertDialog.BUTTON_NEUTRAL)
              .setOnClickListener(v -> open(a, ComponentName.unflattenFromString(component)));
          dialog
              .getButton(AlertDialog.BUTTON_POSITIVE)
              .setOnClickListener(
                  v -> {
                    String label = DisplayLabel.clean(field.getText().toString());
                    if (label.isEmpty()) {
                      field.setError("Informe um nome");
                      return;
                    }
                    ComponentName target = ComponentName.unflattenFromString(component);
                    if (!enabled(a) || target == null || !allowed(a, target)) {
                      Toast.makeText(a, "Função indisponível.", 0).show();
                      return;
                    }
                    List<Shortcut> rows = load(a);
                    boolean exists = false;
                    for (Shortcut row : rows) if (row.component.equals(component)) exists = true;
                    if (!exists && rows.size() >= ProjectorShortcutStore.LIMIT) {
                      Toast.makeText(a, "Limite de 48 atalhos. Remova um antes de adicionar.", 0)
                          .show();
                      return;
                    }
                    rows.removeIf(r -> r.component.equals(component));
                    rows.add(new Shortcut(component, label));
                    save(a, rows);
                    ProjectorIcons.save(a, component, glyph[0]);
                    dialog.dismiss();
                    changed.run();
                  });
        });
    dialog.show();
  }

  static void manage(Activity a, Runnable changed) {
    List<Shortcut> rows = load(a);
    if (rows.isEmpty()) {
      Toast.makeText(a, "Nenhum atalho adicionado.", 0).show();
      return;
    }
    String[] labels = new String[rows.size()];
    for (int i = 0; i < labels.length; i++) labels[i] = rows.get(i).label;
    new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
        .setTitle("Atalhos do projetor")
        .setItems(
            labels,
            (d, n) -> {
              Shortcut row = rows.get(n);
              new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
                  .setTitle(row.label)
                  .setItems(
                      new String[] {"Renomear ou testar", "Remover atalho"},
                      (q, k) -> {
                        if (k == 0) edit(a, row.component, row.label, changed);
                        else {
                          remove(a, row.component);
                          changed.run();
                        }
                      })
                  .setNegativeButton("Cancelar", null)
                  .show();
            })
        .setNegativeButton("Fechar", null)
        .show();
  }

  private static final class Candidate {
    final String component, name, text;
    List<Candidate> children;

    Candidate(String component, String name, String text) {
      this.component = component;
      this.name = name;
      this.text = text;
    }

    public String toString() {
      return text;
    }
  }

  static void pick(Activity a, Runnable changed) {
    new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
        .setTitle("Tipo de atalho")
        .setItems(
            new String[] {"Abrir aplicativo", "Abrir função / activity"},
            (dialog, index) -> pickTarget(a, changed, index == 0))
        .setNegativeButton("Cancelar", null)
        .show();
  }

  private static void pickTarget(Activity a, Runnable changed, boolean appsOnly) {
    AlertDialog progress =
        new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
            .setMessage("Buscando funções disponíveis…")
            .setNegativeButton("Cancelar", null)
            .show();
    new Thread(
            () -> {
              try {
                List<Candidate> found = new ArrayList<>();
                PackageManager pm = a.getPackageManager();
                if (appsOnly) {
                  for (ResolveInfo app : AppLibrary.discover(a))
                    if (allowed(a, app.activityInfo)) {
                      String name =
                          Cards.cleanLabel(
                              String.valueOf(app.loadLabel(pm)), app.activityInfo.packageName);
                      String component =
                          new ComponentName(app.activityInfo.packageName, app.activityInfo.name)
                              .flattenToShortString();
                      found.add(
                          new Candidate(
                              component, name, name + "\n" + app.activityInfo.packageName));
                    }
                } else
                  for (PackageInfo pkg : pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)) {
                    if (Thread.currentThread().isInterrupted()) return;
                    if (pkg.activities == null) continue;
                    String app =
                        Cards.cleanLabel(
                            String.valueOf(pkg.applicationInfo.loadLabel(pm)), pkg.packageName);
                    List<Candidate> children = new ArrayList<>();
                    for (ActivityInfo info : pkg.activities)
                      if (allowed(a, info)) {
                        String name =
                            Cards.cleanLabel(String.valueOf(info.loadLabel(pm)), info.name);
                        String component =
                            new ComponentName(info.packageName, info.name).flattenToShortString();
                        children.add(new Candidate(component, name, name + "\n" + info.name));
                      }
                    if (!children.isEmpty()) {
                      children.sort(
                          Comparator.comparing(c -> c.text, String.CASE_INSENSITIVE_ORDER));
                      Candidate group =
                          new Candidate(
                              pkg.packageName,
                              app,
                              app + " · " + children.size() + " funções\n" + pkg.packageName);
                      group.children = children;
                      found.add(group);
                    }
                  }
                found.sort(Comparator.comparing(c -> c.text, String.CASE_INSENSITIVE_ORDER));
                a.runOnUiThread(
                    () -> {
                      if (a.isDestroyed() || a.isFinishing() || !progress.isShowing()) return;
                      progress.dismiss();
                      showCandidates(a, found, changed, appsOnly);
                    });
              } catch (RuntimeException e) {
                a.runOnUiThread(
                    () -> {
                      if (progress.isShowing()) {
                        progress.dismiss();
                        Toast.makeText(a, "Não foi possível consultar as funções.", 0).show();
                      }
                    });
              }
            },
            "projector-picker")
        .start();
  }

  private static void showCandidates(
      Activity a, List<Candidate> found, Runnable changed, boolean appsOnly) {
    LinearLayout box = new LinearLayout(a);
    box.setOrientation(LinearLayout.VERTICAL);
    int pad = GlassUi.dp(a, 18);
    box.setPadding(pad, 0, pad, 0);
    EditText search = new EditText(a);
    search.setSingleLine(true);
    box.addView(search);
    TextView help = new TextView(a);
    help.setPadding(0, pad / 2, 0, pad / 2);
    box.addView(help);
    ListView list = new ListView(a);
    box.addView(list, new LinearLayout.LayoutParams(-1, GlassUi.dp(a, 280)));
    ArrayAdapter<Candidate> adapter =
        new ArrayAdapter<>(a, android.R.layout.simple_list_item_1, new ArrayList<>());
    list.setAdapter(adapter);
    AlertDialog dialog =
        new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
            .setView(box)
            .setNegativeButton("Fechar", null)
            .create();
    class Navigation {
      List<Candidate> current = found;
      String app = "", rootSearch = "";
      int rootPosition;

      void filter() {
        String query = search.getText().toString().toLowerCase(Locale.ROOT);
        adapter.clear();
        for (Candidate c : current)
          if (c.text.toLowerCase(Locale.ROOT).contains(query)) adapter.add(c);
        adapter.notifyDataSetChanged();
      }

      void labels() {
        dialog.setTitle(
            app.isEmpty()
                ? (appsOnly ? "Abrir aplicativo" : "Escolher aplicativo")
                : app + " · Funções");
        search.setHint(app.isEmpty() ? "Buscar aplicativo…" : "Buscar função / activity…");
        help.setText(
            app.isEmpty()
                ? (appsOnly
                    ? "Escolha o aplicativo que executa a função desejada."
                    : "Escolha um app para ver somente as funções dele.")
                : "Escolha a função deste app. Só aparecem telas acessíveis ao launcher.");
        if (dialog.isShowing())
          dialog
              .getButton(AlertDialog.BUTTON_NEGATIVE)
              .setText(app.isEmpty() ? "Fechar" : "Voltar aos aplicativos");
      }

      void enter(Candidate group) {
        rootPosition = list.getFirstVisiblePosition();
        rootSearch = search.getText().toString();
        current = group.children;
        app = group.name;
        search.setText("");
        labels();
        filter();
        list.setSelection(0);
      }

      boolean back() {
        if (app.isEmpty()) return false;
        current = found;
        app = "";
        search.setText(rootSearch);
        labels();
        filter();
        list.setSelection(rootPosition);
        return true;
      }
    }
    Navigation nav = new Navigation();
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            nav.filter();
          }

          public void afterTextChanged(Editable e) {}
        });
    list.setOnItemClickListener(
        (parent, v, index, id) -> {
          Candidate selected = adapter.getItem(index);
          if (selected.children != null) {
            nav.enter(selected);
            return;
          }
          dialog.dismiss();
          edit(a, selected.component, selected.name, changed);
        });
    dialog.setOnKeyListener(
        (d, key, event) -> {
          if (key == KeyEvent.KEYCODE_BACK && !nav.app.isEmpty()) {
            if (event.getAction() == KeyEvent.ACTION_UP) nav.back();
            return true;
          }
          return false;
        });
    dialog.setOnShowListener(
        d -> {
          dialog
              .getButton(AlertDialog.BUTTON_NEGATIVE)
              .setOnClickListener(
                  v -> {
                    if (!nav.back()) dialog.dismiss();
                  });
          nav.labels();
          nav.filter();
        });
    dialog.show();
    dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  }
}
