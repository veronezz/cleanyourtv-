package com.gathin.tvhome;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;
import java.util.concurrent.*;

final class IconPackPicker {
  private static final String WEBSITE = "https://github.com/SicMundus86/ProjectivyIconPack";

  static void settings(Activity a, Runnable changed) {
    String mode = LocalPreferences.get(a).getString(IconPacks.MODE, "missing");
    new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
        .setTitle("Pacote de ícones")
        .setItems(
            new String[] {
              "Escolher pack · " + IconPacks.label(a),
              "Aplicação · " + ("all".equals(mode) ? "Todos os apps" : "Só apps sem banner"),
              "Conhecer o Projectivy Icon Pack"
            },
            (d, n) -> {
              if (n == 0) choosePack(a, changed);
              else if (n == 1)
                new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
                    .setTitle("Aplicar imagens do pack")
                    .setSingleChoiceItems(
                        new String[] {"Só apps sem banner", "Todos os apps compatíveis"},
                        "all".equals(mode) ? 1 : 0,
                        (q, k) -> {
                          LocalPreferences.save(
                              a,
                              LocalPreferences.get(a)
                                  .edit()
                                  .putString(IconPacks.MODE, k == 0 ? "missing" : "all"));
                          IconPacks.resetCache();
                          q.dismiss();
                          changed.run();
                        })
                    .setNegativeButton("Cancelar", null)
                    .show();
              else
                try {
                  a.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(WEBSITE)));
                } catch (ActivityNotFoundException e) {
                  Toast.makeText(
                          a,
                          "Abra github.com/SicMundus86/ProjectivyIconPack para baixar o APK do"
                              + " pack.",
                          Toast.LENGTH_LONG)
                      .show();
                }
            })
        .setNegativeButton("Fechar", null)
        .show();
  }

  static void choosePack(Activity a, Runnable changed) {
    AlertDialog progress =
        new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
            .setMessage("Buscando pacotes de ícones instalados…")
            .setNegativeButton("Cancelar", null)
            .show();
    new Thread(
            () -> {
              List<IconPacks.Pack> packs;
              try {
                packs = IconPacks.installed(a);
              } catch (RuntimeException e) {
                a.runOnUiThread(
                    () -> {
                      if (progress.isShowing()) {
                        progress.dismiss();
                        Toast.makeText(a, "Não foi possível consultar os packs.", 0).show();
                      }
                    });
                return;
              }
              List<IconPacks.Pack> result = packs;
              a.runOnUiThread(
                  () -> {
                    if (a.isDestroyed() || a.isFinishing() || !progress.isShowing()) return;
                    progress.dismiss();
                    String[] labels = new String[result.size() + 1];
                    labels[0] = "Originais dos apps";
                    int selected = 0;
                    for (int i = 0; i < result.size(); i++) {
                      labels[i + 1] = result.get(i).label;
                      if (result.get(i).pkg.equals(IconPacks.selected(a))) selected = i + 1;
                    }
                    AlertDialog dialog =
                        new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
                            .setTitle(
                                result.isEmpty()
                                    ? "Instale um pack para aparecer aqui"
                                    : "Escolher pacote de ícones")
                            .setSingleChoiceItems(
                                labels,
                                selected,
                                (d, n) -> {
                                  LocalPreferences.save(
                                      a,
                                      LocalPreferences.get(a)
                                          .edit()
                                          .putString(
                                              IconPacks.KEY, n == 0 ? "" : result.get(n - 1).pkg));
                                  IconPacks.resetCache();
                                  d.dismiss();
                                  changed.run();
                                })
                            .setNegativeButton("Cancelar", null)
                            .create();
                    dialog.show();
                  });
            },
            "icon-pack-discovery")
        .start();
  }

  static void chooseImage(Activity a, String target, Runnable changed) {
    if (IconPacks.selected(a).isEmpty()) {
      new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
          .setTitle("Selecione um pack primeiro")
          .setMessage("Preferências → Aplicativos → Pacote de ícones.")
          .setPositiveButton("Escolher pack", (d, n) -> choosePack(a, changed))
          .setNegativeButton("Cancelar", null)
          .show();
      return;
    }
    AlertDialog progress =
        new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
            .setMessage("Carregando imagens do pack…")
            .setNegativeButton("Cancelar", null)
            .show();
    new Thread(
            () -> {
              IconPacks.Snapshot pack = IconPacks.prepare(a);
              List<String> images = pack == null ? Collections.emptyList() : pack.images();
              a.runOnUiThread(
                  () -> {
                    if (a.isDestroyed() || a.isFinishing() || !progress.isShowing()) return;
                    progress.dismiss();
                    if (pack == null || images.isEmpty()) {
                      Toast.makeText(
                              a,
                              "O pack está indisponível ou não possui imagens compatíveis.",
                              Toast.LENGTH_LONG)
                          .show();
                      return;
                    }
                    showImages(a, target, pack, images, changed);
                  });
            },
            "icon-pack-catalogue")
        .start();
  }

  private static final class Holder extends RecyclerView.ViewHolder {
    final ImageView image;
    final TextView name;
    String key = "";

    Holder(LinearLayout row, ImageView image, TextView name) {
      super(row);
      this.image = image;
      this.name = name;
    }
  }

  private static void showImages(
      Activity a, String target, IconPacks.Snapshot pack, List<String> images, Runnable changed) {
    Dialog dialog = new Dialog(a, GlassUi.dialogTheme(a));
    ExecutorService executor = Executors.newFixedThreadPool(2);
    Handler main = new Handler(Looper.getMainLooper());
    android.util.LruCache<String, Bitmap> cache =
        new android.util.LruCache<String, Bitmap>(2 * 1024 * 1024) {
          protected int sizeOf(String key, Bitmap b) {
            return b.getAllocationByteCount();
          }
        };
    LinearLayout panel = new LinearLayout(a);
    panel.setOrientation(LinearLayout.VERTICAL);
    int pad = GlassUi.dp(a, 20);
    panel.setPadding(pad, pad, pad, pad);
    panel.setBackground(GlassUi.panel(a, false));
    panel.addView(GlassUi.text(a, "Escolher imagem do pack", 22, Color.WHITE));
    EditText search = new EditText(a);
    search.setSingleLine(true);
    search.setHint("Buscar: netflix, youtube, settings…");
    panel.addView(search, new LinearLayout.LayoutParams(-1, -2));
    TextView count = GlassUi.text(a, images.size() + " imagens", 12, 0xffb3c7d8);
    panel.addView(count);
    RecyclerView list = new RecyclerView(a);
    list.setLayoutManager(new LinearLayoutManager(a));
    panel.addView(list, new LinearLayout.LayoutParams(-1, GlassUi.dp(a, 290)));
    List<String> visible = new ArrayList<>(images);
    RecyclerView.Adapter<Holder> adapter =
        new RecyclerView.Adapter<Holder>() {
          public int getItemCount() {
            return visible.size();
          }

          public Holder onCreateViewHolder(ViewGroup parent, int type) {
            LinearLayout row = new LinearLayout(a);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(pad / 2, pad / 3, pad / 2, pad / 3);
            row.setFocusable(true);
            row.setClickable(true);
            row.setBackground(GlassUi.panel(a, false));
            row.setOnFocusChangeListener((v, f) -> v.setBackground(GlassUi.panel(a, f)));
            ImageView image = new ImageView(a);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(
                image, new LinearLayout.LayoutParams(GlassUi.dp(a, 112), GlassUi.dp(a, 64)));
            TextView name = GlassUi.text(a, "", 16, Color.WHITE);
            name.setPadding(pad / 2, 0, 0, 0);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            return new Holder(row, image, name);
          }

          public void onBindViewHolder(Holder h, int position) {
            String key = visible.get(position);
            h.key = key;
            h.name.setText(key.replace('_', ' '));
            h.image.setImageBitmap(cache.get(key));
            h.itemView.setOnClickListener(
                v -> {
                  AppAppearance.clearImage(a, target);
                  IconPacks.assign(a, target, pack.pkg + "|" + key);
                  dialog.dismiss();
                  changed.run();
                });
            if (cache.get(key) == null && !executor.isShutdown())
              executor.execute(
                  () -> {
                    Bitmap bitmap = null;
                    try {
                      Drawable d = pack.image(key);
                      if (d != null) {
                        int w = Math.max(1, d.getIntrinsicWidth()),
                            height = Math.max(1, d.getIntrinsicHeight());
                        float scale = Math.min(160f / w, 90f / height);
                        w = Math.max(1, Math.round(w * scale));
                        height = Math.max(1, Math.round(height * scale));
                        bitmap = Bitmap.createBitmap(w, height, Bitmap.Config.ARGB_8888);
                        d.setBounds(0, 0, w, height);
                        d.draw(new Canvas(bitmap));
                        cache.put(key, bitmap);
                      }
                    } catch (Exception | OutOfMemoryError ignored) {
                    }
                    Bitmap result = bitmap;
                    main.post(
                        () -> {
                          if (dialog.isShowing() && key.equals(h.key))
                            h.image.setImageBitmap(result);
                        });
                  });
          }

          public void onViewRecycled(Holder h) {
            h.key = "";
            h.image.setImageDrawable(null);
          }
        };
    list.setAdapter(adapter);
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int n) {
            visible.clear();
            String q = s.toString().toLowerCase(Locale.ROOT).replace(' ', '_');
            for (String name : images) if (name.contains(q)) visible.add(name);
            count.setText(visible.size() + " imagens");
            adapter.notifyDataSetChanged();
          }

          public void afterTextChanged(Editable e) {}
        });
    TextView close = GlassUi.text(a, "Fechar", 16, Color.WHITE);
    close.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
    close.setFocusable(true);
    close.setBackground(GlassUi.panel(a, false));
    close.setOnFocusChangeListener((v, f) -> v.setBackground(GlassUi.panel(a, f)));
    close.setOnClickListener(v -> dialog.dismiss());
    panel.addView(close);
    dialog.setContentView(panel);
    dialog.setOnDismissListener(
        d -> {
          executor.shutdownNow();
          main.removeCallbacksAndMessages(null);
          cache.evictAll();
        });
    dialog.show();
    if (dialog.getWindow() != null) {
      dialog
          .getWindow()
          .setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
      dialog
          .getWindow()
          .setLayout(
              Math.min(GlassUi.dp(a, 640), a.getResources().getDisplayMetrics().widthPixels - pad),
              -2);
      dialog
          .getWindow()
          .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
  }
}
