package com.gathin.tvhome;

import android.app.*;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

final class TileColors {
  static final String PREFIX = "tile_color:";

  static Integer color(Context c, String key) {
    return TileColorValue.parse(LocalPreferences.get(c).getString(PREFIX + key, "default"));
  }

  private static void save(Context c, String key, String value, Runnable changed) {
    android.content.SharedPreferences.Editor editor = LocalPreferences.get(c).edit();
    if (value.equals("default")) editor.remove(PREFIX + key);
    else editor.putString(PREFIX + key, value);
    LocalPreferences.save(c, editor);
    changed.run();
  }

  static void choose(Activity a, String key, Runnable changed) {
    String[] values = {
      "default",
      "transparent",
      "#111827",
      "#000000",
      "#243447",
      "#23473F",
      "#303F71",
      "#503971",
      "#783E53",
      "#713E28",
      "#6D621F",
      "#DDE4EC",
      "custom"
    };
    String[] labels = {
      "Padrão do launcher",
      "Sem fundo",
      "Grafite",
      "Preto",
      "Azul petróleo",
      "Verde escuro",
      "Índigo",
      "Roxo",
      "Vinho",
      "Terracota",
      "Oliva",
      "Claro",
      "Cor personalizada · HEX"
    };
    String current = LocalPreferences.get(a).getString(PREFIX + key, "default");
    ArrayAdapter<String> adapter =
        new ArrayAdapter<String>(a, android.R.layout.simple_list_item_1, labels) {
          public View getView(int n, View recycled, ViewGroup parent) {
            TextView row = (TextView) super.getView(n, recycled, parent);
            Integer color = TileColorValue.parse(values[n]);
            GradientDrawable swatch = new GradientDrawable();
            swatch.setColor(color == null ? 0xff1c1c1e : color);
            swatch.setCornerRadius(GlassUi.dp(a, 6));
            swatch.setStroke(GlassUi.dp(a, 1), 0xff8695a6);
            int size = GlassUi.dp(a, 28);
            swatch.setBounds(0, 0, size, size);
            row.setCompoundDrawables(swatch, null, null, null);
            row.setCompoundDrawablePadding(GlassUi.dp(a, 14));
            row.setText(labels[n] + (values[n].equalsIgnoreCase(current) ? " ✓" : ""));
            return row;
          }
        };
    new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
        .setTitle("Cor de fundo do cartão")
        .setAdapter(
            adapter,
            (d, n) -> {
              if (values[n].equals("custom")) custom(a, key, changed);
              else save(a, key, values[n], changed);
            })
        .setNegativeButton("Cancelar", null)
        .show();
  }

  private static void custom(Activity a, String key, Runnable changed) {
    EditText field = new EditText(a);
    field.setSingleLine(true);
    field.setHint("#RRGGBB, exemplo: #203040");
    field.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(7)});
    String value = LocalPreferences.get(a).getString(PREFIX + key, "");
    if (value.startsWith("#")) field.setText(value);
    AlertDialog dialog =
        new AlertDialog.Builder(a, GlassUi.dialogTheme(a))
            .setTitle("Cor personalizada")
            .setMessage("A cor aparece atrás das partes transparentes do ícone ou banner.")
            .setView(field)
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      String rgb = field.getText().toString().trim();
                      Integer parsed = TileColorValue.parse(rgb);
                      if (parsed == null || rgb.equals("transparent")) {
                        field.setError("Use seis dígitos: #RRGGBB");
                        return;
                      }
                      save(
                          a,
                          key,
                          String.format(java.util.Locale.ROOT, "#%06X", parsed & 0xffffff),
                          changed);
                      dialog.dismiss();
                    }));
    dialog.show();
  }
}
