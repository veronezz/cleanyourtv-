package com.gathin.tvhome;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

/** Two-column remote UI. The preview is captured once per entry, never rebuilt on each edit. */
public final class LauncherPreferencesActivity extends LocalizedActivity {
  private LinearLayout rows, shell, destination;
  private PreferenceGroup destinationGroup;
  private ImageView previewImage, atmosphere;
  private boolean visualsStarted;
  private android.app.Dialog library;
  private final java.util.function.Consumer<android.graphics.Bitmap> previewReady =
      bitmap -> {
        if (previewImage != null && !isDestroyed()) previewImage.setImageBitmap(bitmap);
      };
  private String expandedCategory = "", focusedSetting = "";
  private final java.util.Map<String, PreferenceGroup> groups = new java.util.LinkedHashMap<>();

  private static final class PreferenceGroup {
    final String key;
    final LinearLayout header, body;
    android.animation.ValueAnimator animator;
    final java.util.List<Runnable> pending = new java.util.ArrayList<>();

    void materialize() {
      for (Runnable row : pending) row.run();
      pending.clear();
    }

    PreferenceGroup(String key, LinearLayout header, LinearLayout body) {
      this.key = key;
      this.header = header;
      this.body = body;
    }
  }

  private ScrollView settingsScroll;
  private TextView projectLink;
  private GlassScene glassScene;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private boolean cityBusy;
  private int focusedRow;
  private boolean mediaDiagnosticBusy, favoriteBusy;
  private static final int PICK_WALLPAPER = 41;
  private static final String BASE_GITHUB = "https://github.com/Gathin23/cleanyourtv";

  private int dp(int n) {
    return GlassUi.dp(this, n);
  }

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    WallpaperSettings.migrate(this);
    DynamicRows.migrate(this);
    if (state != null) {
      focusedRow = state.getInt("settings_row", 0);
      expandedCategory = state.getString("expanded_category", "");
      focusedSetting = state.getString("focused_setting", "");
    }
    build();
    if (state != null)
      settingsScroll.post(() -> settingsScroll.scrollTo(0, state.getInt("settings_scroll", 0)));
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putString("expanded_category", expandedCategory);
    state.putString("focused_setting", focusedSetting);
    state.putInt("settings_row", focusedRow);
    state.putInt("settings_scroll", settingsScroll.getScrollY());
    super.onSaveInstanceState(state);
  }

  private void createShell() {
    FrameLayout canvas = new FrameLayout(this);
    canvas.setBackground(
        LauncherTheme.ios(this)
            ? IosStyle.background(this)
            : SoftBackground.make(getResources(), true));
    boolean smoked = LauncherTheme.smoked(this);
    atmosphere = new ImageView(this);
    atmosphere.setScaleType(ImageView.ScaleType.CENTER_CROP);
    atmosphere.setAlpha(LauncherTheme.ios(this) ? .12f : smoked ? 1f : .62f);
    canvas.addView(atmosphere, new FrameLayout.LayoutParams(-1, -1));
    android.graphics.Bitmap frozen = HomePreview.snapshot();
    if (smoked) {
      glassScene = new GlassScene(canvas, null);
      GlassUi.attachScene(this, glassScene);
    }
    HomePreview.observe(previewReady);
    canvas
        .getViewTreeObserver()
        .addOnDrawListener(
            new android.view.ViewTreeObserver.OnDrawListener() {
              @Override
              public void onDraw() {
                if (visualsStarted) return;
                visualsStarted = true;
                handler.post(
                    () -> {
                      if (canvas.getViewTreeObserver().isAlive())
                        canvas.getViewTreeObserver().removeOnDrawListener(this);
                      if (!isDestroyed()) loadBackdrop(smoked);
                    });
              }
            });
    shell = new LinearLayout(this);
    shell.setOrientation(LinearLayout.HORIZONTAL);
    shell.setPadding(dp(34), dp(26), dp(34), dp(20));
    canvas.addView(shell, new FrameLayout.LayoutParams(-1, -1));
    LinearLayout left = new LinearLayout(this);
    left.setOrientation(LinearLayout.VERTICAL);
    int screen =
        (int)
            (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
    int leftWidth = Math.max(210, Math.min(280, (screen - 90) * 30 / 100));
    LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(dp(leftWidth), -1);
    leftLp.setMarginEnd(dp(30));
    shell.addView(left, leftLp);
    TextView eyebrow =
        GlassUi.text(this, this.getString(R.string.ui_sua_tela_do_seu_jeito_ef95), 10, 0xffa4bacd);
    eyebrow.setLetterSpacing(.16f);
    left.addView(eyebrow);
    TextView title =
        GlassUi.text(this, this.getString(R.string.ui_preferencias_4dc4), 30, Color.WHITE);
    title.setPadding(0, dp(7), 0, dp(18));
    left.addView(title);
    FrameLayout preview = new FrameLayout(this);
    preview.setPadding(0, 0, 0, 0);
    preview.setBackground(GlassUi.panel(this, preview, false));
    ImageView image = new RoundedPreviewImage(this);
    previewImage = image;
    image.setScaleType(ImageView.ScaleType.CENTER_CROP);
    image.setImageBitmap(frozen);
    image.setContentDescription(this.getString(R.string.ui_previa_fixa_da_tela_inicial_42a9));
    preview.addView(image, new FrameLayout.LayoutParams(-1, -1));
    preview.setOutlineProvider(
        new android.view.ViewOutlineProvider() {
          @Override
          public void getOutline(View view, android.graphics.Outline out) {
            out.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(18));
          }
        });
    preview.setClipToOutline(true);
    left.addView(preview, new LinearLayout.LayoutParams(-1, dp(leftWidth * 9 / 16)));
    TextView note =
        GlassUi.text(this, this.getString(R.string.ui_previa_fixa_e93f), 10, 0xff92adbf);
    note.setLetterSpacing(.12f);
    note.setPadding(dp(4), dp(9), 0, dp(16));
    left.addView(note);
    LinearLayout about = new LinearLayout(this);
    about.setOrientation(LinearLayout.VERTICAL);
    about.setTag("project-info");
    about.setPadding(dp(18), dp(18), dp(18), dp(18));
    about.setBackground(GlassUi.panel(this, about, false));
    LinearLayout identity = new LinearLayout(this);
    identity.setOrientation(LinearLayout.VERTICAL);
    about.addView(identity);
    TextView name =
        GlassUi.text(
            this, getApplicationInfo().loadLabel(getPackageManager()).toString(), 23, Color.WHITE);
    identity.addView(name);
    String version = "3.0";
    try {
      version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    } catch (Exception ignored) {
    }
    TextView release =
        GlassUi.text(
            this, this.getString(R.string.ui_versao_802c) + version.split("-")[0], 12, 0xffb3c7d8);
    release.setPadding(0, dp(5), 0, 0);
    identity.addView(release);
    about.addView(new android.widget.Space(this), new LinearLayout.LayoutParams(1, 0, 1));
    TextView description =
        GlassUi.text(this, this.getString(R.string.project_slogan), 14, 0xffd9e6f0);
    description.setTag("project-slogan");
    about.addView(description);
    about.addView(new android.widget.Space(this), new LinearLayout.LayoutParams(1, 0, 1));
    projectLink = GlassUi.text(this, "", 12, 0xffcae7ff);
    projectLink.setPadding(dp(12), dp(12), dp(12), dp(12));
    projectLink.setMaxLines(3);
    projectLink.setFocusable(true);
    projectLink.setClickable(true);
    projectLink.setOnFocusChangeListener(
        (v, f) -> v.setBackground(f ? GlassUi.row(this, true, LauncherTheme.smoked(this)) : null));
    projectLink.setOnClickListener(v -> openProject());
    LinearLayout.LayoutParams linkLp = new LinearLayout.LayoutParams(-1, -2);
    about.addView(projectLink, linkLp);
    left.addView(about, new LinearLayout.LayoutParams(-1, 0, 1));
    settingsScroll = new SafeSettingsScroll(this);
    settingsScroll.setVerticalScrollBarEnabled(false);
    settingsScroll.setClipToPadding(true);
    settingsScroll.setPadding(dp(20), dp(20), dp(20), dp(20));
    settingsScroll.setFillViewport(true);
    if (smoked || LauncherTheme.ios(this))
      settingsScroll.setBackground(GlassUi.panel(this, settingsScroll, false));
    rows = new LinearLayout(this);
    rows.setOrientation(LinearLayout.VERTICAL);
    rows.setClipChildren(false);
    rows.setClipToPadding(false);
    settingsScroll.addView(rows);
    shell.addView(settingsScroll, new LinearLayout.LayoutParams(0, -1, 1));
    setContentView(canvas);
    if (!LocalPreferences.get(this).getBoolean("reduced_motion", false)) {
      shell.setAlpha(0f);
      shell.animate().alpha(1f).setDuration(220).start();
    }
  }

  private void build() {
    boolean first = shell == null;
    int oldY = first ? 0 : settingsScroll.getScrollY(), returnRow = focusedRow;
    if (first) createShell();
    for (PreferenceGroup group : groups.values())
      if (group.animator != null) group.animator.cancel();
    groups.clear();
    rows.removeAllViews();
    destination = rows;
    destinationGroup = null;
    SharedPreferences p = LocalPreferences.get(this);
    Config cfg = Config.load(this);
    projectLink.setText("GITHUB  ↗\n" + BASE_GITHUB.replace("https://github.com/", ""));
    addAppearanceSettings(p, cfg);
    addContentSettings();
    addApplicationSettings(p);
    addWallpaperSettings(p);
    addProjectorSettings();
    addWeatherSettings(p);
    addSystemSettings(p);
    rows.post(
        () -> {
          int index = Math.max(0, Math.min(returnRow, rows.getChildCount() - 1));
          View target = focusedSetting.isEmpty() ? null : rows.findViewWithTag(focusedSetting);
          if (target == null || !target.isShown()) {
            PreferenceGroup group = groups.get(expandedCategory);
            target = group == null ? rows.getChildAt(index) : group.header;
          }
          if (target != null && target.isFocusable()) target.requestFocus();
          else
            for (int i = 0; i < rows.getChildCount(); i++)
              if (rows.getChildAt(i).isFocusable()) {
                rows.getChildAt(i).requestFocus();
                break;
              }
          if (!first) settingsScroll.scrollTo(0, oldY);
        });
  }

  private void addAppearanceSettings(SharedPreferences p, Config cfg) {
    category("home", "Aparência e navegação", "Tema, grade, efeitos e espaçamento");
    setting(
        getString(R.string.theme_title),
        LauncherTheme.label(this),
        getString(R.string.theme_help),
        () -> ThemePicker.show(this, () -> recreate()));
    setting(
        "Grade e dock",
        cfg.columns + " colunas · " + TileSizing.percent(this) + "%",
        "Colunas, tamanho dos ícones e fileiras adicionais.",
        this::gridOptions);
    setting(
        "Efeitos e animações",
        cfg.reducedMotion ? "Movimento reduzido" : "Personalizar",
        "Brilho dos apps, vidro da dock e redução de movimentos.",
        this::visualOptions);
    String spacing = HomeLayoutPolicy.profile(p.getString("home_spacing", "compact"));
    setting(
        "Espaçamento da tela",
        spacing.equals("compact")
            ? "Compacto"
            : spacing.equals("balanced") ? "Equilibrado" : "Amplo",
        "Distância entre aplicativos e linhas de conteúdo.",
        () ->
            choice(
                "Espaçamento da tela",
                new String[] {"Compacto", "Equilibrado", "Amplo"},
                new String[] {"compact", "balanced", "relaxed"},
                "home_spacing"));
    String clockSpacing = p.getString("clock_spacing", "normal");
    setting(
        "Distância abaixo do relógio",
        "small".equals(clockSpacing) ? "Menor" : "large".equals(clockSpacing) ? "Maior" : "Padrão",
        "Padrão: uma linha de apps. Independente do espaçamento das linhas.",
        () ->
            choice(
                "Distância abaixo do relógio",
                new String[] {"Menor", "Padrão", "Maior"},
                new String[] {"small", "normal", "large"},
                "clock_spacing"));
  }

  private void addContentSettings() {
    category("content", "Linhas e cartões", "Escolha o conteúdo da tela inicial");
    setting(
        "Linhas na tela inicial",
        rowSummary(),
        "Selecione todas as linhas em um único menu.",
        this::chooseRows);
    setting(
        "Mídias recentes · celular/projetor",
        RecentMedia.enabled(this) ? "Ativado" : "Desativado",
        "Alternativa por sessões de mídia; independe da programação de TV.",
        () -> RecentMedia.options(this, this::build));
    setting(
        "Cartões de continuar assistindo",
        HistoryDismissals.count(this) + " removidos",
        "Limpeza perto do final e restauração de cartões.",
        this::cardOptions);
  }

  private void addApplicationSettings(SharedPreferences p) {
    category("apps", "Aplicativos", "Visibilidade, ícones e organização");
    setting(
        "Pacote de ícones",
        IconPacks.label(this),
        "Preencha apps sem banner ou aplique o pack em todos.",
        () -> IconPackPicker.settings(this, this::build));
    setting(
        "Apps na tela inicial",
        "Selecionar",
        "Escolha quais apps aparecem; desmarque para ocultar.",
        () -> HomeAppPicker.show(this, this::build));
    setting(
        getString(R.string.app_library),
        getString(R.string.ui_abrir_3110),
        getString(R.string.library_help),
        () -> {
          library = AppLibrary.show(this);
        });
    setting(
        getString(R.string.ui_restaurar_organizacao_1a61),
        getString(R.string.ui_restaurar_a703),
        getString(R.string.ui_retorna_a_ordem_inicial_sem_remover_os_apps_6a4b),
        () ->
            new AlertDialog.Builder(this, GlassUi.dialogTheme(this))
                .setTitle(getString(R.string.ui_restaurar_a_ordem_5eb0))
                .setMessage(
                    getString(
                        R.string.ui_apenas_a_organizacao_da_tela_inicial_sera_restaurada_f656))
                .setPositiveButton(
                    getString(R.string.ui_restaurar_a703),
                    (d, w) -> {
                      LocalPreferences.save(this, p.edit().remove("app_order"));
                      build();
                    })
                .setNegativeButton(getString(R.string.ui_cancelar_9ddb), null)
                .show());
  }

  private void addWallpaperSettings(SharedPreferences p) {
    category(
        "wallpaper",
        getString(R.string.ui_planos_de_fundo_56a6),
        getString(R.string.ui_seu_ambiente_com_prioridade_para_o_spotify_aac4));
    String style = p.getString("background_style", "aurora");
    setting(
        this.getString(R.string.ui_fonte_do_fundo_e0bf),
        style.equals(FavoriteWallpapers.STYLE)
            ? getString(R.string.fav_title)
            : style.equals("wallhaven")
                ? "Wallhaven"
                : style.equals("wallpaper")
                    ? this.getString(R.string.ui_galeria_d827)
                    : style.equals("dark") ? this.getString(R.string.ui_escuro_8367) : "Aurora",
        this.getString(R.string.ui_o_fundo_da_musica_aparece_acima_do_papel_de_parede_412b),
        this::background);
    if ("wallhaven".equals(style))
      setting(
          getString(R.string.wh_next),
          getString(R.string.wh_next_action),
          getString(R.string.wh_next_help),
          () -> {
            WallpaperSettings.requestNext(this);
            Toast.makeText(this, R.string.wh_next_requested, Toast.LENGTH_SHORT).show();
          });
    setting(
        getString(R.string.fav_save),
        getString(R.string.fav_add),
        getString(R.string.fav_save_help),
        this::saveFavorite);
    setting(
        getString(R.string.fav_title),
        String.valueOf(FavoriteWallpapers.list(this).size()),
        getString(R.string.fav_help),
        () -> FavoritesPicker.show(this, this::build));
    if (FavoriteWallpapers.STYLE.equals(style))
      setting(
          getString(R.string.wh_next),
          getString(R.string.wh_next_action),
          getString(R.string.fav_help),
          () -> {
            java.io.File next = FavoriteWallpapers.current(this, true);
            if (next != null)
              LocalPreferences.save(
                  this,
                  p.edit()
                      .putLong(
                          FavoriteWallpapers.REVISION,
                          p.getLong(FavoriteWallpapers.REVISION, 0) + 1));
          });
    if ("wallhaven".equals(style))
      setting(
          "Configurar Wallhaven",
          p.getString("wallhaven_source", "nature")
              + " · "
              + PreferenceValues.minutes(p.getInt("wallhaven_interval", 15))
              + " min",
          "Fonte, resolução, formato e rotação.",
          this::wallhavenOptions);
    String fit = p.getString("wallpaper_fit", "stretch");
    setting(
        getString(R.string.wp_fit),
        getString(
            "crop".equals(fit)
                ? R.string.wp_crop
                : "contain".equals(fit) ? R.string.wp_contain : R.string.wp_stretch),
        getString(R.string.wp_fit_help),
        () ->
            choice(
                getString(R.string.wp_fit),
                new String[] {
                  getString(R.string.wp_stretch),
                  getString(R.string.wp_crop),
                  getString(R.string.wp_contain)
                },
                new String[] {"stretch", "crop", "contain"},
                "wallpaper_fit"));
    setting(
        getString(R.string.wp_contrast),
        p.getBoolean("adaptive_contrast", true)
            ? getString(R.string.ui_ativado_b655)
            : getString(R.string.ui_desativado_7cfb),
        getString(R.string.wp_contrast_help),
        () -> toggle("adaptive_contrast", true));
  }

  private void addProjectorSettings() {
    category("projector", "Projetor", "Atalhos para as funções do seu aparelho");
    setting(
        "Modo projetor",
        ProjectorShortcuts.enabled(this) ? "Ativado" : "Desativado",
        "Habilita atalhos para ajustes e apps do fabricante.",
        () -> toggle("projector_mode", false));
    if (ProjectorShortcuts.enabled(this)) {
      setting(
          "Adicionar função ou app",
          "Escolher",
          "Selecione uma tela disponível no aparelho, teste e salve.",
          () -> ProjectorShortcuts.pick(this, this::build));
      setting(
          "Atalhos do projetor",
          ProjectorShortcuts.load(this).size() + " salvos",
          "Renomear, testar ou remover. Também acessível segurando OK no atalho.",
          () -> ProjectorShortcuts.manage(this, this::build));
    }
  }

  private void addWeatherSettings(SharedPreferences p) {
    category(
        "weather",
        getString(R.string.ui_clima_6165),
        getString(R.string.ui_a_previsao_para_o_seu_lugar_8690));
    setting(
        this.getString(R.string.ui_mostrar_clima_5737),
        p.getBoolean("weather_enabled", true)
            ? this.getString(R.string.ui_ativado_b655)
            : this.getString(R.string.ui_desativado_7cfb),
        this.getString(R.string.ui_temperatura_e_condicoes_abaixo_do_relogio_408a),
        () -> toggle("weather_enabled", true));
    setting(
        getString(R.string.weather_icons),
        p.getBoolean("weather_google_icons", false)
            ? "Google"
            : getString(R.string.weather_text_only),
        getString(R.string.weather_icons_help),
        () -> toggle("weather_google_icons", false));
    setting(
        this.getString(R.string.ui_cidade_7365),
        p.getString("weather_city", this.getString(R.string.ui_escolher_bcb5)),
        this.getString(R.string.ui_busque_e_selecione_sua_cidade_b168),
        this::chooseCity);
    setting(
        this.getString(R.string.ui_sobre_os_dados_a0f3),
        "Open-Meteo",
        this.getString(R.string.ui_fontes_e_atribuicoes_d126),
        () ->
            message(
                this.getString(R.string.ui_clima_open_meteo_0877),
                this.getString(
                        R.string.ui_modelos_meteorologicos_open_meteo_cc_by_4_0_cidades_geo_c619)
                    + (p.getBoolean("weather_google_icons", false)
                        ? "\n\nGoogle — "
                            + getString(R.string.weather_icons)
                            + "\n"
                            + "https://developers.google.com/maps/documentation/weather/weather-condition-icons"
                        : "")));
  }

  private void addSystemSettings(SharedPreferences p) {
    category(
        "system",
        getString(R.string.ui_midia_e_sistema_193a),
        getString(R.string.settings_system_help));
    setting(
        "Permissões de mídia",
        MediaAccess.listenerAllowed(this) ? "Sessões autorizadas" : "Revisar",
        "Programação de TV e sessões de reprodução.",
        this::mediaPermissions);
    setting(
        getString(R.string.handle_inputs_title),
        p.getBoolean("handle_view_inputs", false)
            ? getString(R.string.ui_ativado_b655)
            : getString(R.string.ui_desativado_7cfb),
        getString(R.string.handle_inputs_help),
        () -> toggle("handle_view_inputs", false));
    setting(
        getString(R.string.language_title),
        languageName(),
        getString(R.string.language_help),
        this::chooseLanguage);
    setting(
        this.getString(R.string.ui_diagnostico_de_midia_d670),
        this.getString(R.string.ui_consultar_775b),
        this.getString(R.string.ui_listas_publicadas_pelos_apps_e_sessao_de_musica_02f2),
        () -> {
          if (mediaDiagnosticBusy) return;
          mediaDiagnosticBusy = true;
          android.content.Context app = getApplicationContext();
          new Thread(
                  () -> {
                    String report = MediaDiagnostics.read(app);
                    handler.post(
                        () -> {
                          mediaDiagnosticBusy = false;
                          if (!isDestroyed() && !isFinishing())
                            message(getString(R.string.ui_diagnostico_de_midia_d670), report);
                        });
                  },
                  "media-diagnostic")
              .start();
        });
    setting(
        this.getString(R.string.ui_configuracoes_da_tv_200c),
        this.getString(R.string.ui_abrir_3110),
        this.getString(R.string.ui_preferencias_do_sistema_android_tv_df1e),
        () -> {
          try {
            startActivity(SystemSettings.intent(this, Settings.ACTION_SETTINGS));
          } catch (Exception e) {
            message(
                this.getString(R.string.ui_configuracoes_cdaf),
                this.getString(R.string.ui_nao_foi_possivel_abrir_as_configuracoes_desta_tv_ec81));
          }
        });
  }

  private void showAtmosphere(android.graphics.Bitmap image) {
    atmosphere.setImageBitmap(image);
    float alpha = LauncherTheme.ios(this) ? .12f : LauncherTheme.smoked(this) ? 1f : .62f;
    if (LocalPreferences.get(this).getBoolean("reduced_motion", false)) atmosphere.setAlpha(alpha);
    else {
      atmosphere.setAlpha(0f);
      atmosphere.animate().alpha(alpha).setDuration(180).start();
    }
  }

  private void loadBackdrop(boolean smoked) {
    if (smoked) {
      android.graphics.Bitmap backdrop = HomePreview.background();
      showAtmosphere(backdrop);
      if (glassScene != null) glassScene.update(backdrop);
    } else {
      android.graphics.Bitmap frozen = HomePreview.snapshot();
      new Thread(
              () -> {
                try {
                  android.graphics.Bitmap blur = MediaArtwork.blur(frozen);
                  handler.post(
                      () -> {
                        if (!isDestroyed()) showAtmosphere(blur);
                      });
                } catch (RuntimeException | OutOfMemoryError ignored) {
                }
              },
              "preferences-backdrop")
          .start();
    }
  }

  @Override
  protected void onDestroy() {
    if (library != null) library.dismiss();
    HomePreview.removeObserver(previewReady);
    handler.removeCallbacksAndMessages(null);
    for (PreferenceGroup group : groups.values())
      if (group.animator != null) group.animator.cancel();
    GlassUi.releaseScene(this);
    HomePreview.releaseBackground();
    glassScene = null;
    super.onDestroy();
  }

  private String languageName() {
    String tag = AppLanguage.choice(this);
    return tag.isEmpty()
        ? getString(R.string.language_system)
        : java.util.Locale.forLanguageTag(tag).getDisplayName(java.util.Locale.forLanguageTag(tag));
  }

  private void chooseLanguage() {
    String[] labels = AppLanguage.labels(this);
    int selected = java.util.Arrays.asList(AppLanguage.TAGS).indexOf(AppLanguage.choice(this));
    new AlertDialog.Builder(this, GlassUi.dialogTheme(this))
        .setTitle(R.string.language_title)
        .setSingleChoiceItems(
            labels,
            Math.max(0, selected),
            (dialog, n) -> {
              String tag = AppLanguage.TAGS[n];
              if (!tag.equals(AppLanguage.choice(this))) {
                AppLanguage.select(this, tag);
                if (android.os.Build.VERSION.SDK_INT < 33) recreate();
              }
              dialog.dismiss();
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void category(String key, String title, String detail) {
    boolean expanded = key.equals(expandedCategory);
    LinearLayout header =
        GlassUi.action(this, title, expanded ? "⌃" : "⌄", detail, () -> toggleCategory(key));
    header.setTag("category:" + key);
    TextView chevron = (TextView) header.getChildAt(1);
    chevron.setTag("category-chevron:" + key);
    chevron.setText(expanded ? "⌃" : "⌄");
    if (android.os.Build.VERSION.SDK_INT >= 30)
      header.setStateDescription(
          getString(expanded ? R.string.settings_expanded : R.string.settings_collapsed));
    ((TextView) ((LinearLayout) header.getChildAt(0)).getChildAt(0))
        .setFontVariationSettings("'wght' 500");
    ImageView icon = new ImageView(this);
    icon.setTag("category-icon:" + key);
    icon.setImageResource(categoryIcon(key));
    if (LauncherTheme.ios(this))
      icon.setImageTintList(
          android.content.res.ColorStateList.valueOf(
              LauncherTheme.dark(this) ? 0xffa6c8f4 : 0xff476b9e));
    icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    icon.setPadding(dp(8), dp(8), dp(8), dp(8));
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    GradientDrawable badge = new GradientDrawable();
    badge.setCornerRadius(dp(12));
    badge.setColor(LauncherTheme.ios(this) ? 0x164769aa : 0x123ed9ef);
    badge.setStroke(dp(1), LauncherTheme.ios(this) ? 0x304769aa : 0x284eddeb);
    icon.setBackground(badge);
    LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(40), dp(40));
    iconLp.setMarginEnd(dp(14));
    header.addView(icon, 0, iconLp);
    View.OnFocusChangeListener style = header.getOnFocusChangeListener();
    header.setOnFocusChangeListener(
        (v, f) -> {
          if (f) {
            focusedSetting = "category:" + key;
            focusedRow = rows.indexOfChild(v);
          }
          if (style != null) style.onFocusChange(v, f);
        });
    LinearLayout.LayoutParams headLp = new LinearLayout.LayoutParams(-1, -2);
    headLp.bottomMargin = dp(10);
    rows.addView(header, headLp);
    LinearLayout body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(8), 0, dp(8), dp(4));
    body.setClipChildren(false);
    body.setClipToPadding(false);
    body.setVisibility(expanded ? View.VISIBLE : View.GONE);
    body.setTag("category-body:" + key);
    rows.addView(body, new LinearLayout.LayoutParams(-1, -2));
    destinationGroup = new PreferenceGroup(key, header, body);
    groups.put(key, destinationGroup);
    destination = body;
  }

  private int categoryIcon(String key) {
    switch (key) {
      case "home":
        return R.drawable.ic_preferences_appearance;
      case "content":
        return R.drawable.ic_preferences_content;
      case "apps":
        return R.drawable.ic_preferences_home;
      case "projector":
        return R.drawable.ic_preferences_projector;
      case "wallpaper":
        return R.drawable.ic_preferences_wallpaper;
      case "weather":
        return R.drawable.ic_preferences_weather;
      default:
        return R.drawable.ic_preferences_media_system;
    }
  }

  private void toggleCategory(String key) {
    String previous = expandedCategory;
    expandedCategory = key.equals(previous) ? "" : key;
    for (PreferenceGroup group : groups.values())
      if (group.key.equals(previous) || group.key.equals(key))
        animateGroup(group, group.key.equals(expandedCategory));
  }

  private void animateGroup(PreferenceGroup group, boolean open) {
    if (group.animator != null) group.animator.cancel();
    ((TextView) group.header.findViewWithTag("category-chevron:" + group.key))
        .setText(open ? "⌃" : "⌄");
    if (android.os.Build.VERSION.SDK_INT >= 30)
      group.header.setStateDescription(
          getString(open ? R.string.settings_expanded : R.string.settings_collapsed));
    if (open) group.materialize();
    int from = group.body.getVisibility() == View.GONE ? 0 : group.body.getHeight();
    if (!open && group.body.hasFocus()) group.header.requestFocus();
    if (LocalPreferences.get(this).getBoolean("reduced_motion", false)
        || !group.body.isLaidOut() && group.header.getWidth() == 0) {
      group.body.setVisibility(open ? View.VISIBLE : View.GONE);
      group.body.getLayoutParams().height = -2;
      group.body.requestLayout();
      return;
    }
    int width = Math.max(1, group.header.getWidth());
    group.body.measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
    int to = open ? group.body.getMeasuredHeight() : 0;
    group.body.setVisibility(View.VISIBLE);
    group.body.getLayoutParams().height = from;
    android.animation.ValueAnimator animation = android.animation.ValueAnimator.ofInt(from, to);
    group.animator = animation;
    animation.setDuration(200);
    animation.setInterpolator(new android.view.animation.DecelerateInterpolator());
    animation.addUpdateListener(
        a -> {
          group.body.getLayoutParams().height = (Integer) a.getAnimatedValue();
          group.body.requestLayout();
        });
    animation.addListener(
        new android.animation.AnimatorListenerAdapter() {
          boolean cancelled;

          @Override
          public void onAnimationCancel(android.animation.Animator a) {
            cancelled = true;
          }

          @Override
          public void onAnimationEnd(android.animation.Animator a) {
            if (cancelled) return;
            group.body.setVisibility(open ? View.VISIBLE : View.GONE);
            group.body.getLayoutParams().height = -2;
            group.body.requestLayout();
            group.header.post(
                () -> {
                  if (group.header.hasFocus())
                    group.header.requestRectangleOnScreen(
                        new android.graphics.Rect(
                            0, 0, group.header.getWidth(), group.header.getHeight()),
                        false);
                });
          }
        });
    animation.start();
  }

  @Override
  public void onBackPressed() {
    if (!expandedCategory.isEmpty()) {
      PreferenceGroup group = groups.get(expandedCategory);
      toggleCategory(expandedCategory);
      if (group != null) group.header.requestFocus();
      return;
    }
    super.onBackPressed();
  }

  private boolean[] rowSelection() {
    SharedPreferences p = LocalPreferences.get(this);
    boolean[] values = new boolean[RowOptions.KEYS.length];
    for (int i = 0; i < values.length; i++)
      values[i] = p.getBoolean(RowOptions.KEYS[i], RowOptions.defaultValue(i));
    return values;
  }

  private String rowSummary() {
    boolean[] values = rowSelection();
    return values[0] ? RowOptions.count(values) + " selecionadas" : "Somente aplicativos";
  }

  private void chooseRows() {
    boolean[] draft = rowSelection();
    String[] labels = {
      "Mostrar linhas de conteúdo",
      "Continuar assistindo · outros apps",
      "UniTV · Continuar e séries",
      "Globoplay · TV ao vivo",
      "Spotify · Tocando agora",
      "TizenTube",
      "YouTube"
    };
    new AlertDialog.Builder(this, GlassUi.dialogTheme(this))
        .setTitle("Linhas na tela inicial")
        .setMultiChoiceItems(labels, draft, (dialog, index, checked) -> draft[index] = checked)
        .setPositiveButton(
            R.string.ui_salvar_ec33,
            (dialog, which) -> {
              SharedPreferences.Editor edit = LocalPreferences.get(this).edit();
              for (int i = 0; i < draft.length; i++) edit.putBoolean(RowOptions.KEYS[i], draft[i]);
              edit.putBoolean(RecommendationChannels.ENABLED, RowOptions.recommendations(draft));
              LocalPreferences.save(this, edit);
              build();
              ensureMediaAccess();
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void gridOptions() {
    Config cfg = Config.load(this);
    new AlertDialog.Builder(this, GlassUi.dialogTheme(this))
        .setTitle("Grade e dock")
        .setItems(
            new String[] {
              "Colunas: " + cfg.columns,
              "Tamanho dos ícones: " + TileSizing.percent(this) + "%",
              "Fileiras abaixo da dock: " + cfg.rowsBelowDock
            },
            (d, n) -> {
              if (n == 0)
                chooseNumber(
                    getString(R.string.ui_colunas_5d93), new int[] {4, 5, 6, 7, 8}, "columns");
              else if (n == 1)
                chooseNumber(
                    getString(R.string.icon_size),
                    new int[] {100, 110, 120, 130, 140, 150},
                    "icon_size_percent");
              else
                chooseNumber(
                    getString(R.string.ui_fileiras_abaixo_da_dock_1aa6),
                    new int[] {0, 1, 2, 3},
                    "rows_below_dock");
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void visualOptions() {
    SharedPreferences p = LocalPreferences.get(this);
    String[] keys = {"app_glow", "dock_band", "reduced_motion"};
    boolean[] values = {
      p.getBoolean(keys[0], false), p.getBoolean(keys[1], true), p.getBoolean(keys[2], false)
    };
    new AlertDialog.Builder(this, GlassUi.dialogTheme(this))
        .setTitle("Efeitos e animações")
        .setMultiChoiceItems(
            new String[] {"Brilho dos aplicativos", "Vidro da dock", "Movimento reduzido"},
            values,
            (d, n, checked) -> values[n] = checked)
        .setPositiveButton(
            R.string.ui_salvar_ec33,
            (d, n) -> {
              SharedPreferences.Editor e = p.edit();
              for (int i = 0; i < keys.length; i++) e.putBoolean(keys[i], values[i]);
              LocalPreferences.save(this, e);
              build();
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void cardOptions() {
    boolean auto = LocalPreferences.get(this).getBoolean(HistoryDismissals.AUTO, false);
    new AlertDialog.Builder(this, GlassUi.dialogTheme(this))
        .setTitle("Cartões de continuar assistindo")
        .setItems(
            new String[] {
              "Ocultar nos últimos 5 min: " + (auto ? "ativado" : "desativado"),
              "Restaurar cartões removidos (" + HistoryDismissals.count(this) + ")"
            },
            (d, n) -> {
              if (n == 0) toggle(HistoryDismissals.AUTO, false);
              else {
                HistoryDismissals.restore(this);
                build();
              }
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void wallhavenOptions() {
    new AlertDialog.Builder(this, GlassUi.dialogTheme(this))
        .setTitle("Wallhaven")
        .setItems(
            new String[] {
              "Fonte das imagens",
              "Resolução",
              "Formato horizontal / 16:9",
              "Intervalo da rotação",
              "Status da rotação",
              "Abrir imagem atual"
            },
            (d, n) -> {
              if (n == 0) wallhavenSource();
              else if (n == 1)
                choice(
                    getString(R.string.wh_quality),
                    new String[] {"Full HD", "4K"},
                    new String[] {"fhd", "4k"},
                    "wallhaven_quality");
              else if (n == 2) toggle("wallhaven_widescreen", true);
              else if (n == 3) wallhavenInterval();
              else if (n == 4) message("Wallhaven", wallhavenStatus());
              else openWallhaven();
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void setting(String label, String value, String detail, Runnable action) {
    LinearLayout container = destination;
    PreferenceGroup group = destinationGroup;
    Runnable create = () -> addSetting(container, label, value, detail, action);
    if (group != null && !group.key.equals(expandedCategory)) group.pending.add(create);
    else create.run();
  }

  private void addSetting(
      LinearLayout container, String label, String value, String detail, Runnable action) {
    LinearLayout row = GlassUi.action(this, label, value, detail, action);
    View.OnFocusChangeListener style = row.getOnFocusChangeListener();
    row.setOnFocusChangeListener(
        (v, f) -> {
          if (f) {
            focusedRow = rows.indexOfChild(container);
            focusedSetting = (String) v.getTag();
          }
          if (style != null) style.onFocusChange(v, f);
        });
    row.setTag("setting:" + label);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.bottomMargin = dp(8);
    container.addView(row, lp);
  }

  private void pickWallpaper() {
    Intent i =
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("image/*")
            .addCategory(Intent.CATEGORY_OPENABLE);
    try {
      startActivityForResult(i, PICK_WALLPAPER);
    } catch (android.content.ActivityNotFoundException e) {
      try {
        startActivityForResult(
            new Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), PICK_WALLPAPER);
      } catch (Exception ignored) {
        message(
            this.getString(R.string.ui_galeria_indisponivel_92d4),
            this.getString(
                R.string.ui_instale_um_seletor_de_arquivos_ou_escolha_outra_fonte_d_453b));
      }
    }
  }

  private EditText input(String hint, String value) {
    EditText e = new EditText(this);
    e.setSingleLine(true);
    e.setTextColor(IosStyle.text(this, Color.WHITE));
    e.setHintTextColor(0xff8b9fb5);
    e.setHint(hint);
    e.setText(value);
    return e;
  }

  private void textSetting(
      String title, String hint, String current, java.util.function.Consumer<String> save) {
    EditText field = input(hint, current);
    field.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
    LinearLayout container = new LinearLayout(this);
    container.setPadding(dp(24), dp(10), dp(24), dp(4));
    container.addView(field, new LinearLayout.LayoutParams(-1, -2));
    AlertDialog dialog =
        new AlertDialog.Builder(this, GlassUi.dialogTheme(this))
            .setTitle(title)
            .setView(container)
            .setPositiveButton(this.getString(R.string.ui_salvar_ec33), null)
            .setNegativeButton(this.getString(R.string.ui_cancelar_9ddb), null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        save.accept(field.getText().toString().trim());
                        dialog.dismiss();
                        build();
                      } catch (IllegalArgumentException error) {
                        field.setError(UiMessages.translate(this, error.getMessage()));
                      }
                    }));
    dialog.show();
    field.requestFocus();
  }

  private void choice(String title, String[] labels, String[] values, String key) {
    new AlertDialog.Builder(this, GlassUi.dialogTheme(this))
        .setTitle(title)
        .setItems(
            labels,
            (d, n) -> {
              LocalPreferences.save(
                  this, LocalPreferences.get(this).edit().putString(key, values[n]));
              build();
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private void wallhavenSource() {
    GlassUi.menu(
        this,
        getString(R.string.wh_source),
        getString(R.string.wh_source_help),
        new String[] {
          getString(R.string.wh_nature),
          getString(R.string.wh_space),
          getString(R.string.wh_cities),
          getString(R.string.wh_minimal),
          getString(R.string.wh_games),
          getString(R.string.wh_abstract),
          getString(R.string.wh_custom)
        },
        null,
        n -> {
          if (n == 6) {
            textSetting(
                getString(R.string.wh_source),
                getString(R.string.wh_input),
                LocalPreferences.get(this).getString("wallhaven_source", "nature"),
                this::saveWallhaven);
            return;
          }
          saveWallhaven(
              new String[] {"nature", "space", "cityscape", "minimalism", "video games", "abstract"}
                  [n]);
          build();
        });
  }

  private void saveWallhaven(String value) {
    SharedPreferences p = LocalPreferences.get(this);
    try {
      new WallhavenSource(
          value,
          p.getString("wallhaven_quality", "fhd"),
          p.getBoolean("wallhaven_widescreen", true));
    } catch (Exception e) {
      throw new IllegalArgumentException(getString(R.string.wh_invalid));
    }
    LocalPreferences.save(
        this,
        p.edit()
            .putString("wallhaven_source", value)
            .putString("background_style", "wallhaven")
            .putString("wallhaven_status", "pending"));
  }

  private void wallhavenInterval() {
    EditText field =
        input(
            getString(R.string.ui_minutos_1_a_1440_800e),
            Integer.toString(LocalPreferences.get(this).getInt("wallhaven_interval", 15)));
    field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    AlertDialog d =
        new AlertDialog.Builder(this, GlassUi.dialogTheme(this))
            .setTitle(R.string.wh_interval)
            .setView(field)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create();
    d.setOnShowListener(
        x ->
            d.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      try {
                        int minutes = Integer.parseInt(field.getText().toString());
                        if (minutes < 1 || minutes > 1440) throw new NumberFormatException();
                        LocalPreferences.save(
                            this,
                            LocalPreferences.get(this)
                                .edit()
                                .putInt("wallhaven_interval", minutes));
                        d.dismiss();
                        build();
                      } catch (NumberFormatException e) {
                        field.setError(getString(R.string.wh_minutes_error));
                      }
                    }));
    d.show();
  }

  private String wallhavenStatus() {
    String value = LocalPreferences.get(this).getString("wallhaven_status", "pending");
    if (value.startsWith("http:")) return getString(R.string.wh_http, value.substring(5));
    int id =
        "ready".equals(value)
            ? R.string.wh_ready
            : "single".equals(value)
                ? R.string.wh_single
                : "empty".equals(value)
                    ? R.string.wh_empty
                    : "pending".equals(value)
                        ? R.string.wh_pending
                        : "source_invalid".equals(value) ? R.string.wh_invalid : R.string.wh_error;
    return getString(id);
  }

  private void openWallhaven() {
    SharedPreferences p = LocalPreferences.get(this);
    String page =
        p.getString("wallhaven_slot_" + p.getInt("wallhaven_current_slot", 0) + "_page", "");
    if (!WallhavenSource.pageUrl(page)) {
      message("Wallhaven", getString(R.string.wh_pending));
      return;
    }
    openLink(page);
  }

  private void openProject() {
    openLink(BASE_GITHUB);
  }

  private void openLink(String link) {
    try {
      startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link)));
    } catch (Exception e) {
      message(
          this.getString(R.string.ui_abrir_link_e5bf),
          link + this.getString(R.string.ui_n_nnao_ha_um_navegador_disponivel_nesta_tv_f777));
    }
  }

  private static final int TV_LIST_ACCESS = 73;

  private void mediaPermissions() {
    GlassUi.menu(
        this,
        "Permissões de mídia",
        MediaCapabilities.summary(this),
        new String[] {"Programação de TV", "Sessões de mídia"},
        new String[] {
          checkSelfPermission(MediaCapabilities.TV_PERMISSION) == 0
              ? "Autorizada"
              : "Depende do serviço de TV deste aparelho",
          MediaAccess.listenerAllowed(this)
              ? "Autorizadas"
              : "Necessárias para Spotify e mídias recentes"
        },
        choice -> {
          if (choice == 1) {
            MediaAccess.openListenerSettings(this);
            return;
          }
          if (MediaCapabilities.canRequest(this)
              && checkSelfPermission(MediaCapabilities.TV_PERMISSION) != 0) {
            requestPermissions(new String[] {MediaCapabilities.TV_PERMISSION}, TV_LIST_ACCESS);
          } else {
            message("Programação de TV", MediaCapabilities.summary(this));
          }
        });
  }

  private void ensureMediaAccess() {
    if ((DynamicRows.enabled(this, DynamicRows.WATCH)
            || DynamicRows.enabled(this, DynamicRows.LIVE)
            || DynamicRows.recommendations(this))
        && MediaCapabilities.canRequest(this)
        && checkSelfPermission("android.permission.READ_TV_LISTINGS")
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {"android.permission.READ_TV_LISTINGS"}, TV_LIST_ACCESS);
      return;
    }
    explainMusicAccess();
  }

  private void explainMusicAccess() {
    if ((!DynamicRows.enabled(this, DynamicRows.MUSIC) && !RecentMedia.enabled(this))
        || MediaAccess.listenerAllowed(this)) return;
    new AlertDialog.Builder(this, GlassUi.dialogTheme(this))
        .setTitle(R.string.ui_controles_de_midia_9a61)
        .setMessage(
            "Autorize o acesso às sessões de mídia para mostrar o Spotify e, se ativadas, as mídias"
                + " recentes dos apps escolhidos. O launcher usa os metadados de reprodução, sem"
                + " ler mensagens das notificações.")
        .setPositiveButton(R.string.ui_abrir_3110, (d, w) -> MediaAccess.openListenerSettings(this))
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  @Override
  public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(code, permissions, grants);
    if (code == TV_LIST_ACCESS) {
      explainMusicAccess();
    }
  }

  private void toggle(String key, boolean fallback) {
    SharedPreferences p = LocalPreferences.get(this);
    LocalPreferences.save(this, p.edit().putBoolean(key, !p.getBoolean(key, fallback)));
    build();
    if (key.equals(DynamicRows.ALL)
        || key.equals(DynamicRows.WATCH)
        || key.equals(DynamicRows.LIVE)
        || key.equals(DynamicRows.COBALT)
        || key.equals(DynamicRows.YOUTUBE)
        || key.equals(DynamicRows.MUSIC))
      if (LocalPreferences.get(this).getBoolean(key, fallback)) ensureMediaAccess();
  }

  private void chooseNumber(String title, int[] values, String key) {
    String[] labels = new String[values.length];
    for (int i = 0; i < values.length; i++) labels[i] = Integer.toString(values[i]);
    new AlertDialog.Builder(this, GlassUi.dialogTheme(this))
        .setTitle(title)
        .setItems(
            labels,
            (d, n) -> {
              LocalPreferences.save(this, LocalPreferences.get(this).edit().putInt(key, values[n]));
              build();
            })
        .setNegativeButton(this.getString(R.string.ui_cancelar_9ddb), null)
        .show();
  }

  private void saveFavorite() {
    if (favoriteBusy) return;
    favoriteBusy = true;
    android.content.Context app = getApplicationContext();
    new Thread(
            () -> {
              int result = R.string.fav_saved;
              try {
                FavoriteWallpapers.saveCurrent(app);
              } catch (Exception | OutOfMemoryError e) {
                result = R.string.fav_failed;
              }
              final int message = result;
              handler.post(
                  () -> {
                    favoriteBusy = false;
                    if (!isDestroyed()) {
                      Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                      build();
                    }
                  });
            },
            "save-wallpaper-favorite")
        .start();
  }

  private void background() {
    GlassUi.menu(
        this,
        getString(R.string.ui_planos_de_fundo_56a6),
        getString(R.string.ui_escolha_o_visual_da_sua_tela_inicial_6472),
        new String[] {
          getString(R.string.ui_aurora_suave_5be8),
          getString(R.string.ui_degrade_escuro_0e69),
          getString(R.string.ui_imagem_da_galeria_0fe3),
          "Wallhaven",
          getString(R.string.fav_title)
        },
        null,
        n -> {
          if (n == 4) {
            FavoritesPicker.show(this, this::build);
            return;
          }
          if (n == 2) {
            pickWallpaper();
            return;
          }
          if (n == 3) {
            wallhavenSource();
            return;
          }
          LocalPreferences.save(
              this,
              LocalPreferences.get(this)
                  .edit()
                  .putString("background_style", n == 0 ? "aurora" : "dark"));
          build();
        });
  }

  private void message(String title, String text) {
    new AlertDialog.Builder(this, GlassUi.dialogTheme(this))
        .setTitle(title)
        .setMessage(text)
        .setPositiveButton("OK", null)
        .show();
  }

  private void chooseCity() {
    if (cityBusy) return;
    EditText input = new EditText(this);
    input.setSingleLine(true);
    input.setHint(this.getString(R.string.ui_ex_sao_paulo_dad2));
    input.setTextColor(IosStyle.text(this, Color.WHITE));
    input.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
    AlertDialog dialog =
        new AlertDialog.Builder(this, GlassUi.dialogTheme(this))
            .setTitle(this.getString(R.string.ui_cidade_do_clima_a5ce))
            .setMessage(
                this.getString(
                    R.string.ui_digite_a_cidade_a_busca_usa_a_open_meteo_e_permite_esco_8297))
            .setView(input)
            .setPositiveButton(this.getString(R.string.ui_buscar_cefb), null)
            .setNegativeButton(this.getString(R.string.ui_cancelar_9ddb), null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      String query = input.getText().toString().trim();
                      if (query.length() < 2) {
                        input.setError(
                            this.getString(R.string.ui_digite_pelo_menos_duas_letras_cf1c));
                        return;
                      }
                      if (query.length() > 100) {
                        input.setError(this.getString(R.string.ui_nome_longo_demais_34f1));
                        return;
                      }
                      dialog.dismiss();
                      cityBusy = true;
                      Toast.makeText(
                              this,
                              this.getString(R.string.ui_buscando_cidades_b3d4),
                              Toast.LENGTH_SHORT)
                          .show();
                      new Thread(
                              () -> {
                                try {
                                  List<WeatherData.City> cities =
                                      WeatherData.cities(
                                          query, AppLanguage.locale(this).getLanguage());
                                  handler.post(
                                      () -> {
                                        cityBusy = false;
                                        if (isFinishing() || isDestroyed()) return;
                                        if (cities.isEmpty()) {
                                          message(
                                              this.getString(
                                                  R.string.ui_cidade_nao_encontrada_5a7b),
                                              this.getString(
                                                  R.string
                                                      .ui_tente_apenas_o_nome_da_cidade_sem_estado_ou_pais_6073));
                                          return;
                                        }
                                        String[] labels = new String[cities.size()];
                                        for (int i = 0; i < labels.length; i++)
                                          labels[i] = cities.get(i).label;
                                        new AlertDialog.Builder(this, GlassUi.dialogTheme(this))
                                            .setTitle(
                                                this.getString(
                                                    R.string.ui_selecione_sua_cidade_0fe1))
                                            .setItems(
                                                labels,
                                                (a, n) -> {
                                                  WeatherData.City city = cities.get(n);
                                                  SharedPreferences p = LocalPreferences.get(this);
                                                  LocalPreferences.save(
                                                      this,
                                                      p.edit()
                                                          .putString("weather_city", city.label)
                                                          .putString(
                                                              "weather_lat",
                                                              Double.toString(city.latitude))
                                                          .putString(
                                                              "weather_lon",
                                                              Double.toString(city.longitude))
                                                          .remove("weather_updated")
                                                          .putBoolean("weather_enabled", true));
                                                  build();
                                                })
                                            .setNegativeButton(
                                                this.getString(R.string.ui_cancelar_9ddb), null)
                                            .show();
                                      });
                                } catch (Exception e) {
                                  handler.post(
                                      () -> {
                                        cityBusy = false;
                                        if (!isFinishing() && !isDestroyed())
                                          message(
                                              this.getString(R.string.ui_busca_indisponivel_8c19),
                                              this.getString(
                                                  R.string
                                                      .ui_confira_a_conexao_da_tv_e_tente_novamente_1454));
                                      });
                                }
                              },
                              "weather-city-search")
                          .start();
                    }));
    dialog.show();
    input.requestFocus();
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != PICK_WALLPAPER || result != RESULT_OK || data == null || data.getData() == null)
      return;
    final android.net.Uri uri = data.getData();
    Toast.makeText(
            this, this.getString(R.string.ui_preparando_papel_de_parede_08a9), Toast.LENGTH_SHORT)
        .show();
    new Thread(
            () -> {
              File temp = new File(getFilesDir(), "wallpaper.pending");
              File dest = new File(getFilesDir(), "owner-wallpaper.jpg");
              try {
                try (InputStream in = getContentResolver().openInputStream(uri);
                    FileOutputStream out = new FileOutputStream(temp)) {
                  if (in == null) throw new java.io.IOException();
                  byte[] buf = new byte[8192];
                  int n, total = 0;
                  while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > 20 * 1024 * 1024) throw new java.io.IOException();
                    out.write(buf, 0, n);
                  }
                }
                android.graphics.BitmapFactory.Options bounds =
                    new android.graphics.BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeFile(temp.getPath(), bounds);
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new java.io.IOException();
                if (!temp.renameTo(dest)) throw new java.io.IOException();
                LocalPreferences.save(
                    this,
                    LocalPreferences.get(this)
                        .edit()
                        .putString("background_style", "wallpaper")
                        .putLong("wallpaper_revision", System.currentTimeMillis()));
                handler.post(
                    () -> {
                      if (!isFinishing() && !isDestroyed()) build();
                    });
              } catch (Exception e) {
                temp.delete();
                handler.post(
                    () -> {
                      if (!isFinishing() && !isDestroyed())
                        message(
                            this.getString(R.string.ui_imagem_indisponivel_dcd9),
                            this.getString(
                                R.string.ui_escolha_uma_imagem_valida_com_ate_20_mb_e174));
                    });
              }
            },
            "wallpaper-import")
        .start();
  }
}
