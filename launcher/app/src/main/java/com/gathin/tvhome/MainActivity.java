package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends LocalizedActivity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;
    private String liveTvAction;
    private String inputAction;
    private String sourcePanelService;
    private String sourcePanelAction;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private View ambient;
    private TextView heroName;
    private int ambientColor = -1;
    private boolean heroShown;
    private TextView greeting;
    private View btnInputs;
    private android.widget.ImageButton btnWifi;
    private TextView shamsiDate;
    private boolean shamsi;
    private String vpnApp;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    // How far the grid has scrolled the glass. springY works without it, in grid content space, so a
    // scroll moves the glass in lockstep with its tile instead of becoming a moving spring target.
    private float glassScrollShift;
    private boolean glassShown;
    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    private static int lastFocused = 0;
    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;
    private float density;
    /** Module cards (res/values/config.xml, module_cards): discovered on resume, shown only after the owner's Show. */
    private boolean moduleCards;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    private boolean reducedMotion;
    /** hidden_apps as the adapter sees them (config.xml or a theme's config.json): a hidden app gets no card either. */
    private Set<String> hiddenApps = Collections.emptySet();
    private List<Cards.Card> cards = Collections.emptyList();
    /** The grid has been laid out and focused since the last resume; the card prompt waits for this. */
    private boolean tilesReady;
    private boolean resumed,appsLoaded,appsDirty=true,packageReceiverRegistered;
    private int packageSequence;
    static final String ACTION_VIEW_INPUTS="com.android.tv.action.VIEW_INPUTS";
    private boolean pendingInputs;
    private Dialog cardPrompt;
    /** Set when a decision could not be saved: no more prompts until the next resume, instead of asking in a loop. */
    private boolean promptsPaused;
    private long ownerRevision;
    private boolean appGlow;
    private TextView weather;
    private GoogleWeatherIcon weatherIcon;
    private volatile boolean weatherBusy;
    private long weatherAttempt;
    private String movingKey;
    private boolean consumeMoveConfirmUp;
    private TextView moveHint;
    private HomeMediaPanel mediaPanel;
    private androidx.core.widget.NestedScrollView homeScroll;
    private ImageView musicBackdrop,previousMusicBackdrop;
    private ImageView wallpaperView,previousWallpaperView;
    private WallhavenWallpaperEngine wallhavenWallpapers;
    private final Runnable favoritesTick=()->scheduleFavorites(true);
    private void scheduleFavorites(boolean advance){
        handler.removeCallbacks(favoritesTick);
        if(!resumed||spotifyVisible||!FavoriteWallpapers.STYLE.equals(LocalPreferences.get(this).getString("background_style","")))return;
        File file=FavoriteWallpapers.current(this,advance);if(file==null){clearHomeWallpaper();return;}showWallpaper(file);
        long delay=PreferenceValues.minutes(LocalPreferences.get(this).getInt("wallhaven_interval",15))*60_000L;handler.postDelayed(favoritesTick,delay);
    }
    private int rowsBelowDock;
    private BackdropContrast contrast;
    private Bitmap currentWallpaper,currentMusic;
    private boolean spotifyVisible;
    private java.util.Map<String,?> appliedPreferences;
    private String appliedLanguage;
    private final java.util.concurrent.ExecutorService wallpaperLoader=java.util.concurrent.Executors.newSingleThreadExecutor();
    private int wallpaperGeneration;
    private int restoreScroll=-1;



    /** The Inputs or Module cards dropdown while one is open, so onPause can close it. */
    private PopupWindow openPopup;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    /** Shows the Inputs pill once the TV registers an input: at boot HDMI inputs can appear after Home is shown. */
    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputAdded(String inputId) {
            updateInputsButton();
        }

        @Override
        public void onInputRemoved(String inputId) {
            updateInputsButton();
        }
    };
    /** Listens for any VPN coming up or going down, even one this app's traffic is excluded from. */
    private final ConnectivityManager.NetworkCallback vpnCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final IntentFilter packageFilter = new IntentFilter();
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Updates send REMOVED followed by ADDED; keep the tile until the new APK is ready.
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            if(intent.getData()!=null){String changedPackage=intent.getData().getSchemeSpecificPart();AppArtwork.invalidate(changedPackage);if(changedPackage.equals(IconPacks.selected(context)))IconPacks.resetCache();}
            appsDirty=true;
            if(resumed){reloadApps(false);appsDirty=false;}
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWeather();
            updateWifiLabel();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);WallpaperSettings.migrate(this);DynamicRows.migrate(this);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        // config.xml, overlaid by config.json from a pushed theme (see Config and tools/theme.sh).
        Config cfg = Config.load(this);
        COLUMNS = TileSizing.columns(this,cfg.columns);
        rowsBelowDock=cfg.rowsBelowDock;
        reducedMotion = cfg.reducedMotion;
        liveTvPkg = cfg.liveTvPackage;
        liveTvAction = cfg.liveTvAction;
        inputAction = cfg.inputAction;
        sourcePanelService = cfg.sourcePanelService;
        sourcePanelAction = cfg.sourcePanelAction;

        root = findViewById(R.id.root);contrast=new BackdropContrast(root);
        ownerRevision=LocalPreferences.revision(this);
        appliedPreferences=new java.util.HashMap<>(LocalPreferences.get(this).getAll());
        appliedLanguage=AppLanguage.choice(this);
        if(savedInstanceState!=null){lastFocused=savedInstanceState.getInt("home_focus",0);lastFocusedKey=savedInstanceState.getString("home_focus_key");restoreScroll=savedInstanceState.getInt("home_scroll",0);}
        appGlow=LocalPreferences.get(this).getBoolean("app_glow",false);
        String backdrop=LocalPreferences.get(this).getString("background_style","aurora");
        root.setBackground(SoftBackground.make(getResources(),!"dark".equals(backdrop)));
        if(cfg.overlayApplied && !LocalPreferences.get(this).contains("background_style")) root.setBackgroundColor(cfg.backgroundColor);
        if (cfg.wallpaper != null) showWallpaper(cfg.wallpaper);
        glass = findViewById(R.id.glass);glass.refreshTheme();
        ambient = findViewById(R.id.ambient);
        iosVeil=new View(this);iosVeil.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);iosVeil.setBackground(IosStyle.background(this));iosVeil.setAlpha(LauncherTheme.ios(this)?1f:0f);root.addView(iosVeil,root.indexOfChild(ambient),new FrameLayout.LayoutParams(-1,-1));
        heroName = findViewById(R.id.heroName);
        greeting = findViewById(R.id.greeting);
        weather = findViewById(R.id.weather);
        moveHint=new TextView(this);moveHint.setTag(R.id.contrast_protected,true);
        moveHint.setTextColor(Color.WHITE);moveHint.setTextSize(15);
        moveHint.setPadding((int)(20*density),(int)(8*density),(int)(20*density),(int)(8*density));
        moveHint.setBackgroundColor(0xEE142339);moveHint.setVisibility(View.GONE);
        FrameLayout.LayoutParams hintLp=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
        hintLp.bottomMargin=(int)(8*density);root.addView(moveHint,hintLp);
        btnWifi = findViewById(R.id.btnWifi);
        shamsiDate = findViewById(R.id.shamsiDate);
        shamsi = cfg.shamsiDate;
        if (shamsi) {
            shamsiDate.setVisibility(View.VISIBLE);
            TextView date = findViewById(R.id.date);
            date.setTextSize(11f);
            date.setPadding(0, (int) (4 * density), 0, 0);
        }
        vpnApp = cfg.vpnApp;
        moduleCards = cfg.moduleCards;
        hiddenApps = new HashSet<>(cfg.hiddenApps);

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS){@Override public boolean canScrollVertically(){return false;}});
        grid.setNestedScrollingEnabled(false);
        grid.setHasFixedSize(false);
        grid.setItemAnimator(null);
        grid.addItemDecoration(new DockDecoration(COLUMNS, density, LocalPreferences.get(this).getBoolean("dock_band",true),LauncherTheme.smoked(this)));
        int enterAnim = reducedMotion ? R.anim.tile_enter_reduced : R.anim.tile_enter;
        float enterDelay = reducedMotion ? 0f : 0.06f;
        grid.setLayoutAnimation(new LayoutAnimationController(
                AnimationUtils.loadAnimation(this, enterAnim), enterDelay));
        adapter = new AppAdapter(this, this, COLUMNS, cfg);
        grid.setAdapter(adapter);

        homeScroll=findViewById(R.id.homeScroll);
        mediaPanel=new HomeMediaPanel(this,findViewById(R.id.mediaFeeds),new HomeMediaPanel.Host(){
            @Override public void musicVisibility(boolean shown){spotifyVisible=shown;if(shown)handler.removeCallbacks(favoritesTick);else if(resumed)scheduleFavorites(false);if(wallhavenWallpapers!=null){if(shown)wallhavenWallpapers.stop();else if(resumed)wallhavenWallpapers.start();}}
            @Override public void musicBackground(Bitmap image){
                currentMusic=image;updateContrast();
                if(musicBackdrop==null){
                    previousMusicBackdrop=new ImageView(MainActivity.this);previousMusicBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);previousMusicBackdrop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    root.addView(previousMusicBackdrop,root.indexOfChild(iosVeil),new FrameLayout.LayoutParams(-1,-1));
                    musicBackdrop=new ImageView(MainActivity.this);musicBackdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);musicBackdrop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    root.addView(musicBackdrop,root.indexOfChild(iosVeil),new FrameLayout.LayoutParams(-1,-1));
                }
                musicBackdrop.animate().cancel();previousMusicBackdrop.animate().cancel();
                if(image==null){
                    if(reducedMotion){musicBackdrop.setImageDrawable(null);previousMusicBackdrop.setImageDrawable(null);}
                    else{
                        musicBackdrop.animate().alpha(0f).setDuration(300).withEndAction(()->musicBackdrop.setImageDrawable(null)).start();
                        previousMusicBackdrop.animate().alpha(0f).setDuration(300).withEndAction(()->previousMusicBackdrop.setImageDrawable(null)).start();
                    }
                }else{
                    previousMusicBackdrop.setImageDrawable(musicBackdrop.getDrawable());previousMusicBackdrop.setAlpha(musicBackdrop.getAlpha());
                    musicBackdrop.setImageBitmap(image);musicBackdrop.setVisibility(View.VISIBLE);
                    musicBackdrop.setAlpha(reducedMotion?1f:0f);
                    if(!reducedMotion){
                        musicBackdrop.animate().alpha(1f).setDuration(350).start();
                        previousMusicBackdrop.animate().alpha(0f).setDuration(350).withEndAction(()->previousMusicBackdrop.setImageDrawable(null)).start();
                    }else previousMusicBackdrop.setImageDrawable(null);
                    ambient.animate().cancel();ambient.setAlpha(0f);
                }
            }
            @Override public void feedsChanged(){updateGridHeight();restoreHomeScroll();if(contrast!=null)contrast.schedule();}
            @Override public void focusApps(){focusTile(0);}
        });
        if("wallhaven".equals(LocalPreferences.get(this).getString("background_style","")))
            wallhavenWallpapers=new WallhavenWallpaperEngine(this,this::applyWallpaperBitmap);
        homeScroll.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener)(v,x,y,oldX,oldY)->{
            int dy=y-oldY;glassTargetY-=dy;glassScrollShift-=dy;
            glass.setTranslationY(glass.getTranslationY()-dy);
            heroName.setY(heroName.getY()-dy);
        });
        grid.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->{if(r-l!=orr-ol)updateGridHeight();});
        grid.post(this::updateGridHeight);
        findViewById(R.id.mediaFeeds).addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->{if(b-t!=ob-ot)updateGridHeight();});
        homeScroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,orr,ob)->{if(b-t!=ob-ot){updateGridHeight();mediaPanel.resizeHero();}});

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (!glassShown) return;
                glassTargetY -= dy;
                glassScrollShift -= dy;
                glass.setTranslationY(glass.getTranslationY() - dy);
            }
        });

        // Liquid feel: soft springs on the highlight's position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, new FloatPropertyCompat<View>("glassContentY") {
            @Override
            public float getValue(View v) {
                return v.getTranslationY() - glassScrollShift;
            }

            @Override
            public void setValue(View v, float value) {
                v.setTranslationY(value + glassScrollShift);
            }
        });
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            if (reducedMotion) {
                // Stiff and almost critically damped: settles in a few frames instead of oscillating,
                // and stops once the highlight is within 1.5 px of its tile.
                s.setSpring(new SpringForce().setStiffness(1400f).setDampingRatio(0.95f));
                s.setMinimumVisibleChange(1.5f);
            } else {
                s.setSpring(new SpringForce().setStiffness(260f).setDampingRatio(0.58f));
            }
        }

        btnInputs = findViewById(R.id.btnInputs);
        android.widget.ImageButton btnSettings = findViewById(R.id.btnSettings);
        applyButtonTheme(btnWifi);applyButtonTheme(btnSettings);
        btnSettings.setContentDescription(this.getString(R.string.ui_preferencias_4dc4));
        btnInputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> openNetworkPill());
        btnWifi.setOnLongClickListener(v -> {
            open(systemIntent(Settings.ACTION_WIFI_SETTINGS));
            return true;
        });
        btnSettings.setOnClickListener(v -> openLauncherPreferences());
        btnSettings.setOnLongClickListener(v -> {
            if (!moduleCards) return false;   // off: a long press does nothing, as before the feature
            showModuleCards();
            return true;
        });

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // Back to the dock tile Up came from, not wherever the focus finder lands.
                if(mediaPanel==null||!mediaPanel.focusFirst())focusTile(lastFocused % COLUMNS);
                return true;
            }
            if(keyCode==KeyEvent.KEYCODE_DPAD_LEFT||keyCode==KeyEvent.KEYCODE_DPAD_RIGHT){
                boolean towardStart=(keyCode==KeyEvent.KEYCODE_DPAD_LEFT)!=(root.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL);
                return towardStart?(v.getId()==R.id.btnInputs||(v.getId()==R.id.btnWifi&&btnInputs.getVisibility()!=View.VISIBLE)):v.getId()==R.id.btnSettings;
            }
            return false;
        };
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) {hideGlass();hideHero();}
        });

        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageFilter.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU)
            registerReceiver(packageReceiver,packageFilter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(packageReceiver,packageFilter);
        packageReceiverRegistered=true;
        applyViewInputsHandler();

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputCallback, handler);
        if (cm != null) {
            cm.registerDefaultNetworkCallback(networkCallback);
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), vpnCallback);
        }
    }

    /**
     * A theme's full-screen picture behind everything. Decoded once, downsampled to the screen so a 4K
     * JPEG costs a 1080p bitmap at most (about 8 MB), which is the price of a wallpaper on a 1 GB TV.
     */
    private void showWallpaper(File file) {
        final int request=++wallpaperGeneration;
        final int width=Math.min(1920,getResources().getDisplayMetrics().widthPixels),height=Math.min(1080,getResources().getDisplayMetrics().heightPixels);
        wallpaperLoader.execute(()->{
            try {
                android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inJustDecodeBounds=true;
                android.graphics.BitmapFactory.decodeFile(file.getPath(),opts);
                if(opts.outWidth<=0||opts.outHeight<=0)return;
                int sample=1;while(opts.outWidth/(sample*2)>=width&&opts.outHeight/(sample*2)>=height)sample*=2;
                opts.inJustDecodeBounds=false;opts.inSampleSize=sample;opts.inPreferredConfig=Bitmap.Config.RGB_565;
                Bitmap bitmap=android.graphics.BitmapFactory.decodeFile(file.getPath(),opts);
                if(bitmap!=null)handler.post(()->{if(!isDestroyed()&&request==wallpaperGeneration)applyWallpaperBitmap(bitmap);});
            }catch(Exception|OutOfMemoryError ignored){}
        });
    }

    private void restoreHomeScroll(){
        if(restoreScroll<0||homeScroll==null||!appsLoaded||(mediaPanel!=null&&!mediaPanel.contentReady()))return;
        homeScroll.post(()->{if(!isDestroyed()&&restoreScroll>=0){homeScroll.scrollTo(0,restoreScroll);restoreScroll=-1;}});
    }
    @Override protected void onSaveInstanceState(Bundle state){
        state.putInt("home_focus",lastFocused);state.putString("home_focus_key",lastFocusedKey);
        state.putInt("home_scroll",homeScroll==null?0:homeScroll.getScrollY());super.onSaveInstanceState(state);
    }
    private boolean applyOwnerPreferences(){
        android.content.SharedPreferences p=LocalPreferences.get(this);
        Config cfg=Config.load(this);
        // Language and geometry need fresh resources/holders. Android preserves the view state.
        if(!appliedLanguage.equals(AppLanguage.choice(this))||TileSizing.columns(this,cfg.columns)!=COLUMNS||!java.util.Objects.equals(p.getAll().get("icon_size_percent"),appliedPreferences.get("icon_size_percent"))||!java.util.Objects.equals(p.getAll().get("home_spacing"),appliedPreferences.get("home_spacing"))||!java.util.Objects.equals(p.getAll().get("clock_spacing"),appliedPreferences.get("clock_spacing"))||cfg.reducedMotion!=reducedMotion){recreate();return false;}
        java.util.Map<String,?> fresh=new java.util.HashMap<>(p.getAll());
        java.util.Set<String> changed=new java.util.HashSet<>(fresh.keySet());changed.addAll(appliedPreferences.keySet());
        changed.removeIf(key->java.util.Objects.equals(fresh.get(key),appliedPreferences.get(key)));
        appGlow=p.getBoolean("app_glow",false);rowsBelowDock=cfg.rowsBelowDock;
        if(changed.contains("dock_band")||changed.contains(LauncherTheme.KEY)){
            for(int n=grid.getItemDecorationCount()-1;n>=0;n--)grid.removeItemDecorationAt(n);
            grid.addItemDecoration(new DockDecoration(COLUMNS,density,p.getBoolean("dock_band",true),LauncherTheme.smoked(this)));
        }
        if(changed.contains("wallpaper_revision")||changed.contains("background_style")||changed.contains("wallhaven_source")||changed.contains("wallhaven_interval")||changed.contains("wallhaven_quality")||changed.contains("wallhaven_widescreen")||(FavoriteWallpapers.STYLE.equals(p.getString("background_style",""))&&changed.contains(FavoriteWallpapers.REVISION))){
            wallpaperGeneration++;handler.removeCallbacks(favoritesTick);
            if(wallhavenWallpapers!=null){wallhavenWallpapers.close();wallhavenWallpapers=null;}
            String style=p.getString("background_style","aurora");root.setBackground(SoftBackground.make(getResources(),!"dark".equals(style)));
            if("wallhaven".equals(style))wallhavenWallpapers=new WallhavenWallpaperEngine(this,this::applyWallpaperBitmap);
            else if(FavoriteWallpapers.STYLE.equals(style))scheduleFavorites(false);
            else if(cfg.wallpaper!=null)showWallpaper(cfg.wallpaper);
            else clearHomeWallpaper();
        }
        for(String key:changed)if(key.equals("hidden_owner_apps")||key.equals("visible_owner_apps")||key.startsWith("projector_")||key.startsWith("icon_pack")||key.startsWith(IconPacks.MANUAL)||key.startsWith(TileColors.PREFIX)||key.equals("app_order")||key.startsWith("label:")){appsDirty=true;break;}
        if(changed.contains(LauncherTheme.KEY)){glass.refreshTheme();iosVeil.animate().cancel();if(reducedMotion)iosVeil.setAlpha(LauncherTheme.ios(this)?1f:0f);else iosVeil.animate().alpha(LauncherTheme.ios(this)?1f:0f).setDuration(250).start();contrast.trim();updateContrast();applyButtonTheme(btnWifi);applyButtonTheme(findViewById(R.id.btnSettings));if(mediaPanel!=null)mediaPanel.refreshTheme();updateWifiLabel();}
        if(changed.contains("wallpaper_fit")){if(wallpaperView!=null)wallpaperView.setScaleType(WallpaperSettings.scale(this));if(previousWallpaperView!=null)previousWallpaperView.setScaleType(WallpaperSettings.scale(this));}
        if(changed.contains("wallpaper_fit")||changed.contains("adaptive_contrast")||changed.contains("background_style"))updateContrast();
        ownerRevision=LocalPreferences.revision(this);appliedPreferences=fresh;updateGridHeight();return true;
    }
    private void applyButtonTheme(View view){
        if(view==null)return;
        if(view instanceof android.widget.ImageView){android.widget.ImageView icon=(android.widget.ImageView)view;boolean ios=LauncherTheme.ios(this);icon.setImageResource(view.getId()==R.id.btnWifi?(ios?R.drawable.ic_ios_wifi:R.drawable.ic_wifi):(ios?R.drawable.ic_ios_settings:R.drawable.ic_settings));int size=GlassUi.dp(this,ios?36:42);android.view.ViewGroup.LayoutParams lp=view.getLayoutParams();lp.width=size;lp.height=size;view.setLayoutParams(lp);int pad=GlassUi.dp(this,ios?9:10);view.setPadding(pad,pad,pad,pad);}
        if(LauncherTheme.ios(this)){if(view instanceof android.widget.ImageView)((android.widget.ImageView)view).setImageTintList(android.content.res.ColorStateList.valueOf(IosStyle.ink(this)));android.graphics.drawable.StateListDrawable light=new android.graphics.drawable.StateListDrawable();light.addState(new int[]{android.R.attr.state_focused},IosStyle.surface(this,true,999));light.addState(new int[]{},IosStyle.surface(this,false,999));view.setBackground(light);return;}
        if(view instanceof android.widget.ImageView)((android.widget.ImageView)view).setImageTintList(LauncherTheme.smoked(this)?android.content.res.ColorStateList.valueOf(Color.WHITE):getColorStateList(R.drawable.pill_text));
        if(!LauncherTheme.smoked(this)){view.setBackgroundResource(R.drawable.pill_bg);return;}
        android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused},new SmokedGlassDrawable(view,null,true,999));states.addState(new int[]{},new SmokedGlassDrawable(view,null,false,999));view.setBackground(states);
    }
    private View iosVeil;
    private void updateContrast(){if(iosVeil!=null)iosVeil.setBackground(currentMusic==null&&currentWallpaper==null?IosStyle.background(this):new android.graphics.drawable.ColorDrawable(IosStyle.veil(this)));if(contrast!=null)contrast.source(currentMusic!=null?currentMusic:currentWallpaper,currentMusic!=null?ImageView.ScaleType.CENTER_CROP:WallpaperSettings.scale(this));}
    private void clearHomeWallpaper(){
        currentWallpaper=null;FavoriteWallpapers.present(this,null);updateContrast();
        for(ImageView image:new ImageView[]{wallpaperView,previousWallpaperView})if(image!=null){image.animate().cancel();if(reducedMotion)image.setImageDrawable(null);else image.animate().alpha(0f).setDuration(450).withEndAction(()->image.setImageDrawable(null)).start();}
    }

    private void applyWallpaperBitmap(Bitmap bitmap){
        if(isDestroyed())return;
        currentWallpaper=bitmap;FavoriteWallpapers.present(this,bitmap);updateContrast();
        if(wallpaperView==null){
            previousWallpaperView=new ImageView(this);previousWallpaperView.setScaleType(WallpaperSettings.scale(this));
            previousWallpaperView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);root.addView(previousWallpaperView,0,new FrameLayout.LayoutParams(-1,-1));
            wallpaperView=new ImageView(this);wallpaperView.setScaleType(WallpaperSettings.scale(this));
            wallpaperView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);root.addView(wallpaperView,1,new FrameLayout.LayoutParams(-1,-1));
        }
        wallpaperView.animate().cancel();previousWallpaperView.animate().cancel();
        previousWallpaperView.setImageDrawable(wallpaperView.getDrawable());previousWallpaperView.setAlpha(wallpaperView.getAlpha());
        wallpaperView.setImageBitmap(bitmap);wallpaperView.setAlpha(reducedMotion?1f:0f);
        if(reducedMotion)previousWallpaperView.setImageDrawable(null);
        else{
            wallpaperView.animate().alpha(1f).setDuration(450).start();
            previousWallpaperView.animate().alpha(0f).setDuration(450).withEndAction(()->previousWallpaperView.setImageDrawable(null)).start();
        }
        // These two base layers always stay below the Spotify background and the UI.
    }

    /**
     * Home brings the existing singleTask instance forward. Preserve its position and focus.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if(acceptInputsIntent(intent))return;
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) return;
        setIntent(intent);
        if(movingKey!=null)finishMove(false);
        // Keep the existing position, focus and tiles when Home brings this instance forward.

    }

    @Override
    protected void onResume() {
        super.onResume();
        if(contrast!=null)contrast.resume();
        resumed=true;
        if(ownerRevision!=LocalPreferences.revision(this)&&!applyOwnerPreferences())return;
        updateWeather();
        if(mediaPanel!=null)mediaPanel.start();
        if(wallhavenWallpapers!=null&&!spotifyVisible)wallhavenWallpapers.start();scheduleFavorites(false);
        promptsPaused = false;
        applyViewInputsHandler();
        acceptInputsIntent(getIntent());
        if(appsLoaded&&packagesChangedSinceRebuild())appsDirty=true;
        if(!appsLoaded||appsDirty){
            boolean first=!appsLoaded;tilesReady=false;
            reloadApps(first);appsLoaded=true;appsDirty=false;
        }else{
            tilesReady=true;
            if(getCurrentFocus()==null)focusAfterLayout(lastFocused,0);
        }
        updateGreeting();
        updateWifiLabel();
        updateInputsButton();
        if(pendingInputs)openPendingInputs();
        handler.postDelayed(minuteTick, MINUTE_MS);
    }

    /** Rebuilds the tiles (and the module cards), then puts focus back where it was. */
    private void reloadApps(boolean animate) {
        // Capture the app before rebinding can move focus and overwrite its key.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final boolean restoreFocus = animate || grid.hasFocus();
        if (restoreFocus) {
            glassShown = false;
            glass.setAlpha(0f);
            heroShown = false;
            heroName.setAlpha(0f);
            ambient.setAlpha(0f);
        }
        reloadTiles(()->{
        if (animate && !reducedMotion) grid.scheduleLayoutAnimation();

        if (restoreFocus) {
            // Follow the same app, or the nearest remaining slot when it was removed.
            restoreFocus();
        } else {
            // A package change must not steal focus from a top button or the Inputs popup.
            int pos = adapter.positionOf(returnKey);
            lastFocused = Math.max(0, Math.min(pos >= 0 ? pos : returnSlot, adapter.getItemCount() - 1));
            lastFocusedKey = adapter.keyAt(lastFocused);
            if (pos < 0) hideHero();
            // A newly installed app may have brought a card with it.
            tilesReady = true;
            showCardPrompt();
        }
        restoreHomeScroll();
        });
    }

    /**
     * Rebuilds the tiles; the module cards the owner chose to show stand in for their app's tile.
     * Every step into the card code runs under CardFeature: if it fails, the tiles are the ordinary
     * ones and cards stay off for this process.
     */
    private void reloadTiles(Runnable applied) {
        hiddenApps=new HashSet<>(Config.load(this).hiddenApps);
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final List<Cards.Card> none = Collections.emptyList();
        // Snapshot BEFORE enumeration: a package changed during the load must remain detectable.
        final int sequenceBeforeLoad=currentPackageSequence();
        final boolean[] loaded={false};
        adapter.reloadDiscovered(moduleCards,discovered->{cards=discovered;packageSequence=sequenceBeforeLoad;loaded[0]=true;},()->{
            if(!loaded[0])appsDirty=true;
            updateGridHeight();lastFocusedKey=returnKey;lastFocused=returnSlot;applied.run();
        });
    }

    private boolean packagesChangedSinceRebuild(){
        try{android.content.pm.ChangedPackages changed=getPackageManager().getChangedPackages(packageSequence);return changed!=null&&!changed.getPackageNames().isEmpty();}
        catch(RuntimeException unavailable){return false;}
    }
    private int currentPackageSequence(){
        try{android.content.pm.ChangedPackages changed=getPackageManager().getChangedPackages(packageSequence);return changed==null?packageSequence:changed.getSequenceNumber();}
        catch(RuntimeException unavailable){return packageSequence;}
    }
    private void applyViewInputsHandler(){
        android.content.pm.PackageManager pm=getPackageManager();ComponentName component=new ComponentName(this,InputsRequestActivity.class);
        boolean enabled=LocalPreferences.get(this).getBoolean("handle_view_inputs",getResources().getBoolean(R.bool.handle_view_inputs));
        int want=enabled?android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED:android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
        if(pm.getComponentEnabledSetting(component)!=want)pm.setComponentEnabledSetting(component,want,android.content.pm.PackageManager.DONT_KILL_APP);
    }
    private boolean acceptInputsIntent(Intent intent){
        if(intent==null||!ACTION_VIEW_INPUTS.equals(intent.getAction()))return false;
        setIntent(new Intent(Intent.ACTION_MAIN));pendingInputs=true;if(resumed)openPendingInputs();return true;
    }
    private void openPendingInputs(){
        if(!pendingInputs)return;pendingInputs=false;
        btnInputs.post(()->{if(!resumed||isFinishing()||isDestroyed()||btnInputs.getVisibility()!=View.VISIBLE)return;btnInputs.requestFocus();showInputs();});
    }

    /**
     * Return to the app we left from, or the same slot if that app is gone. Always refocus: after
     * reload() the view that holds focus may no longer show the app it had.
     */
    private void restoreFocus() {
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            focusTile(pos >= 0 ? pos : returnSlot);
            tilesReady = true;
            showCardPrompt();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed=false;handler.removeCallbacks(favoritesTick);
        if(contrast!=null)contrast.pause();
        if(weatherIcon!=null)weatherIcon.stop();
        if(mediaPanel!=null)mediaPanel.stop();
        if(wallhavenWallpapers!=null)wallhavenWallpapers.stop();
        if(movingKey!=null)finishMove(false);
        handler.removeCallbacks(minuteTick);
        // Nothing of ours stays on screen over another app; an undecided prompt simply returns next time.
        if (cardPrompt != null) cardPrompt.dismiss();
        if (openPopup != null) openPopup.dismiss();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) showCardPrompt();
    }

    private boolean homeStarted;
    @Override protected void onStart(){super.onStart();homeStarted=true;}
    @Override protected void onStop(){homeStarted=false;super.onStop();}
    @Override public void onTrimMemory(int level){
        super.onTrimMemory(level);
        if(MemoryTrimPolicy.shouldRelease(level,homeStarted)){AppArtwork.trim();HomePreview.trim();if(mediaPanel!=null)mediaPanel.trim();if(contrast!=null)contrast.trim();}
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(packageReceiverRegistered){unregisterReceiver(packageReceiver);packageReceiverRegistered=false;}
        if(weatherIcon!=null)weatherIcon.close();
        if(contrast!=null)contrast.close();
        if(adapter!=null)adapter.close();wallpaperGeneration++;wallpaperLoader.shutdownNow();
        if(mediaPanel!=null)mediaPanel.close();
        if(wallhavenWallpapers!=null)wallhavenWallpapers.close();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.unregisterCallback(inputCallback);
        if (cm != null) {
            cm.unregisterNetworkCallback(networkCallback);
            cm.unregisterNetworkCallback(vpnCallback);
        }
    }

    @Override
    public void onBackPressed() {
        if(movingKey!=null){finishMove(false);return;}
        // Home screen: Back goes nowhere.
    }

    // ---- glass highlight -------------------------------------------------------------------------

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;        // matches GlassView's glow inset
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) Math.ceil(w) || lp.height != (int) Math.ceil(h)) {
            lp.width = (int) Math.ceil(w);
            lp.height = (int) Math.ceil(h);
            glass.setLayoutParams(lp);
        }
        // The slab already insets its paint by pad; its visible corner matches the scaled card.
        glass.setRadius(AppAdapter.cornerRadius(card.getWidth(), density) * AppAdapter.FOCUS_SCALE);
        glassTargetX = x;
        glassTargetY = y;
        if (!glassShown) {
            springX.cancel();
            springY.cancel();
            glass.setTranslationX(x);
            glass.setTranslationY(y);
            glass.animate().alpha(1f).setDuration(reducedMotion ? 100 : 180).start();
            glassShown = true;
        } else {
            springX.animateToFinalPosition(x);
            springY.animateToFinalPosition(y - glassScrollShift);
        }
        View item = (View) card.getParent();
        if (position < COLUMNS) {
            // Dock: park it at the bottom of the screen and show the top shelf for this app.
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            // A 1px mismatch used to start a scroll on every dock move. Only scroll when the row is
            // actually off; reduced motion does it in one jump so it does not fight the highlight.
            boolean scroll = Math.abs(dy) > (int) (8 * density);
            if (scroll) {
                if (reducedMotion) grid.scrollBy(0, dy);
                else grid.smoothScrollBy(0, dy);
            }
            showHero(position, scroll ? r.top - dy : r.top);
        } else {
            hideHero();
            // Scroll just enough so the whole glass (not only the tile) is visible.
            Rect want = new Rect(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, want, reducedMotion);
        }
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private void showHero(int position, float dockTopInRoot) {
        // Compact content rows replace the old empty top shelf; never draw an app title over them.
        View feeds=findViewById(R.id.mediaFeeds);
        if(feeds!=null&&feeds.getHeight()>0){hideHero();return;}
        String label = adapter.labelAt(position);
        int color = adapter.ambientColorAt(position);
        float y = dockTopInRoot - 14 * density - 26 * density - heroName.getHeight();
        boolean changed = !label.contentEquals(heroName.getText());

        if (reducedMotion) {
            // Snap the shelf instead of sliding and fading it: each of those frames repaints the
            // full-screen panel, and this switch exists for the GPUs that cannot afford it.
            heroName.animate().cancel();
            heroName.setText(label);
            heroName.setY(y);
            heroName.setTranslationX(0f);
            heroName.setAlpha(1f);
            setAmbient(color);
            if (!heroShown) {
                ambient.setAlpha(0f);
                ambient.animate().alpha(1f).setDuration(160).start();
                heroShown = true;
            }
            return;
        }

        if (!heroShown) {
            heroName.setText(label);
            heroName.setY(y);
            heroName.setTranslationX(0f);
            heroName.animate().alpha(1f).setDuration(260).start();
            setAmbient(color);
            ambient.animate().alpha(1f).setDuration(600).start();
            heroShown = true;
        } else if (changed) {
            heroName.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction(() -> {
                heroName.setText(label);
                heroName.setY(y);
                heroName.setTranslationX(12 * density);
                heroName.animate().alpha(1f).translationX(0f).setDuration(200).start();
            }).start();
            if (color != ambientColor) {
                ambient.animate().alpha(0.35f).setDuration(200).withEndAction(() -> {
                    setAmbient(color);
                    ambient.animate().alpha(1f).setDuration(500).start();
                }).start();
            }
        } else {
            heroName.setY(y);
        }
    }

    /**
     * The glow used to be a full-screen radial GradientDrawable. On Android 10 that drawable is
     * drawn on the CPU, so fading it for half a second redrew 1920×1080 every frame and the
     * highlight looked like it was running at a few frames per second. A small bitmap stretched
     * over the screen is one texture; changing apps only uploads it once.
     */
    private void setAmbient(int color) {
        if(musicBackdrop!=null&&musicBackdrop.getVisibility()==View.VISIBLE){ambient.setBackgroundColor(Color.TRANSPARENT);ambientColor=-1;return;}
        if(!appGlow){ambient.setBackgroundColor(Color.TRANSPARENT);ambient.setAlpha(0f);return;}
        if (color == ambientColor && ambient.getBackground() != null) return;
        ambientColor = color;
        int c = color & 0x00FFFFFF;
        int w = 480;
        int h = 270;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new RadialGradient(
                w * 0.32f, h * 0.42f, w * 0.62f,
                new int[]{0x5C000000 | c, 0x26000000 | c, 0x00000000 | c},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        BitmapDrawable drawable = new BitmapDrawable(getResources(), bmp);
        drawable.setGravity(Gravity.FILL);
        ambient.setBackground(drawable);
    }

    private void hideHero() {
        if (!heroShown) return;
        heroShown = false;
        heroName.animate().alpha(0f).setDuration(reducedMotion ? 100 : 160).start();
        ambient.animate().alpha(0f).setDuration(reducedMotion ? 140 : 300).start();
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.animate().alpha(0f).setDuration(160).start();
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        if(movingKey!=null){
            int from=adapter.positionOf(movingKey);
            int to=AppOrder.target(from,keyCode,COLUMNS,adapter.getItemCount(),grid.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL);
            if(to!=from && to>=0){adapter.move(from,to);lastFocused=to;lastFocusedKey=movingKey;grid.post(()->focusTile(to));}
            return true;
        }
        int count = adapter.getItemCount();
        int target;
        // Horizontal: swallow the key at a row's ends so focus never wraps to the next row or
        // escapes up into the top bar. Mid-row moves fall through to the default focus search.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return GridNavigation.atHorizontalEdge(position,count,COLUMNS,grid.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL,true);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return GridNavigation.atHorizontalEdge(position,count,COLUMNS,grid.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL,false);
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) {if(mediaPanel!=null&&mediaPanel.focusFromDock())return true;return false;}          // dock row: let focus move to the top bar
            target = position - COLUMNS;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) {if(mediaPanel!=null)mediaPanel.focusRecommendations();return true;}     // already on the last row: stay put
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        return true;
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) {
            btnWifi.requestFocus();
            return;
        }
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            // Already focused (e.g. back from an app): no focus change fires, so restore the glass by hand.
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            return;
        }
        // A non-scrolling GridLayoutManager may never dispatch an IDLE transition.
        // Wait for the requested holder to be laid out instead, with a bounded fallback.
        grid.scrollToPosition(pos);
        focusAfterLayout(pos,0);
    }

    private void focusAfterLayout(int position,int attempt){
        grid.postOnAnimation(()->{
            if(isFinishing()||isDestroyed())return;
            int count=adapter.getItemCount();
            if(count==0){btnWifi.requestFocus();return;}
            int pos=Math.max(0,Math.min(position,count-1));
            RecyclerView.ViewHolder holder=grid.findViewHolderForAdapterPosition(pos);
            View target=holder==null?null:holder.itemView.findViewById(R.id.card);
            if(target!=null&&target.isLaidOut()&&target.requestFocus())return;
            if(attempt<8){focusAfterLayout(pos,attempt+1);return;}
            // Keep a reachable control even if Android has not attached a tile yet.
            btnWifi.requestFocus();
        });
    }

    private void hideLauncherApp(String pkg){
        int next=lastFocused;
        LocalPreferences.hideApp(this,pkg);
        ownerRevision=LocalPreferences.revision(this);
        hiddenApps=new HashSet<>(hiddenApps);hiddenApps.add(pkg);
        // Remove just this tile; keep all other artwork, observers and media views in memory.
        hideGlass();hideHero();adapter.hidePackage(pkg);
        lastFocused=Math.max(0,Math.min(next,adapter.getItemCount()-1));
        lastFocusedKey=adapter.keyAt(lastFocused);
        grid.post(()->{if(!isDestroyed()){updateGridHeight();focusAfterLayout(lastFocused,0);}});
    }

    private void updateGridHeight(){
        if(grid==null||adapter==null)return;
        float d=getResources().getDisplayMetrics().density;
        int width=grid.getWidth()>0?grid.getWidth():getResources().getDisplayMetrics().widthPixels-(int)(112*d);
        int item=AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(TileSizing.width(this,DockSpacing.contentWidth(width,d))),d);
        int rows=(adapter.getItemCount()+COLUMNS-1)/COLUMNS;
        String spacing=HomeLayoutPolicy.profile(LocalPreferences.get(this).getString("home_spacing","compact"));
        int bottom=Math.round(HomeLayoutPolicy.gridBottomDp(spacing)*d);
        // Retain the dock decoration's 6+12dp offsets; only the outer empty padding is reduced.
        int top=(int)(12*d);
        int side=DockSpacing.sidePadding(d);grid.setPadding(side,top,side,bottom);
        if(homeScroll!=null){
            int pad=Math.round(("relaxed".equals(spacing)?18:"balanced".equals(spacing)?12:8)*d);
            int lower=Math.round(("relaxed".equals(spacing)?32:"balanced".equals(spacing)?24:16)*d);
            if(homeScroll.getPaddingTop()!=pad||homeScroll.getPaddingBottom()!=lower)homeScroll.setPadding(homeScroll.getPaddingLeft(),pad,homeScroll.getPaddingRight(),lower);
            if(homeScroll.getChildCount()>0){View body=homeScroll.getChildAt(0);int tail=Math.round(("relaxed".equals(spacing)?56:"balanced".equals(spacing)?40:24)*d);if(body.getPaddingBottom()!=tail)body.setPadding(body.getPaddingLeft(),body.getPaddingTop(),body.getPaddingRight(),tail);}
        }
        int height=top+bottom+rows*item+(int)(18*d);
        if(grid.getLayoutParams().height!=height){ViewGroup.LayoutParams lp=grid.getLayoutParams();lp.height=height;grid.setLayoutParams(lp);}
    }
    private void openLauncherPreferences(){HomePreview.capture(this,root);startActivity(new Intent(this,LauncherPreferencesActivity.class));}
    private void appearanceChanged(){ownerRevision=LocalPreferences.revision(this);if(lastFocusedKey!=null)AppArtwork.invalidate(adapter.packageAt(lastFocused));reloadApps(false);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==AppAppearance.PICK_ICON)AppAppearance.result(this,result,data,this::appearanceChanged);
    }

    @Override public void onTileLongPressed(int position) {
        if(movingKey!=null){finishMove(true);return;}
        String key=adapter.keyAt(position),pkg=adapter.packageAt(position);
        if(key==null||pkg==null)return;
        if(adapter.isProjectorAt(position)){
            GlassUi.menu(this,adapter.labelAt(position),"Atalho do projetor",new String[]{"Mover","Renomear ou testar","Ícone do atalho","Cor de fundo","Remover atalho"},new String[]{"Posição na grade ou dock","Nome e abertura da função","USB, foco, enquadramento e brilho","Personalize este cartão","Remove apenas este atalho"},n->{
                if(n==0){movingKey=key;moveHint.setText(getString(R.string.ui_mover_use_as_setas_ok_salva_voltar_cancela_9cb2));moveHint.setVisibility(View.VISIBLE);grid.post(()->focusTile(adapter.positionOf(key)));}
                else if(n==1)ProjectorShortcuts.edit(this,key,adapter.labelAt(position),this::appearanceChanged);
                else if(n==2)ProjectorIcons.choose(this,ProjectorIcons.selected(this,key),icon->{ProjectorIcons.save(this,key,icon);appearanceChanged();});
                else if(n==3)TileColors.choose(this,key,this::appearanceChanged);
                else{ProjectorShortcuts.remove(this,key);appearanceChanged();}
            });return;
        }
        Intent appInfo = SystemSettings.appInfo(this, pkg);
        String[] labels = new String[]{this.getString(R.string.ui_mover_aplicativo_48b2),this.getString(R.string.ui_renomear_02da),this.getString(R.string.ui_trocar_icone_ou_imagem_2b0b),this.getString(R.string.ui_restaurar_nome_e_icone_0070),this.getString(R.string.ui_informacoes_do_aplicativo_4764),this.getString(R.string.ui_ocultar_aplicativo_c8f8),this.getString(R.string.ui_preferencias_da_tela_inicial_6003)};
        String[] details = new String[]{this.getString(R.string.ui_escolha_a_posicao_na_grade_ou_na_dock_3a06),this.getString(R.string.ui_altere_apenas_o_nome_do_atalho_65ae),this.getString(R.string.ui_galeria_ou_imagem_de_outro_app_instalado_5205),this.getString(R.string.ui_volte_a_aparencia_original_77e8),this.getString(R.string.ui_abra_os_detalhes_no_android_tv_9044),this.getString(R.string.ui_mantenha_instalado_mas_fora_da_tela_inicial_f368),this.getString(R.string.ui_aparencia_fundos_clima_e_organizacao_ced6)};
        int[] actions = appInfo == null ? new int[]{0, 1, 2, 3, 5, 6} : new int[]{0, 1, 2, 3, 4, 5, 6};
        String[] shownLabels = new String[actions.length], shownDetails = new String[actions.length];
        for (int i = 0; i < actions.length; i++) {shownLabels[i] = labels[actions[i]];shownDetails[i] = details[actions[i]];}
        GlassUi.menu(this, adapter.labelAt(position), getString(R.string.ui_personalize_este_aplicativo_na_sua_tela_inicial_f2ac), shownLabels, shownDetails, selected -> {
                int n = actions[selected];
                if(n==0){
                    movingKey=key;
                    moveHint.setText(this.getString(R.string.ui_mover_use_as_setas_ok_salva_voltar_cancela_9cb2));moveHint.setVisibility(View.VISIBLE);
                    grid.post(()->focusTile(adapter.positionOf(key)));
                }else if(n==1){AppAppearance.rename(this,pkg,adapter.labelAt(position),this::appearanceChanged);}
                else if(n==2){AppAppearance.chooseImage(this,pkg,this::appearanceChanged);}
                else if(n==3){AppAppearance.reset(this,pkg,this::appearanceChanged);}
                else if(n==4){open(appInfo);}
                else if(n==5){
                    new android.app.AlertDialog.Builder(this,GlassUi.dialogTheme(this)).setTitle(this.getString(R.string.ui_ocultar_aplicativo_e348))
                        .setMessage(this.getString(R.string.ui_o_app_continuara_instalado_para_reexibi_lo_abra_prefere_7d91))
                        .setPositiveButton(this.getString(R.string.ui_ocultar_4d62),(dialog,which)->{hideLauncherApp(pkg);})
                        .setNegativeButton(this.getString(R.string.ui_cancelar_9ddb),null).show();
                }else openLauncherPreferences();
            });
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(consumeMoveConfirmUp && event.getAction()==KeyEvent.ACTION_UP
                && (event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER||event.getKeyCode()==KeyEvent.KEYCODE_ENTER
                ||event.getKeyCode()==KeyEvent.KEYCODE_NUMPAD_ENTER||event.getKeyCode()==KeyEvent.KEYCODE_BACK)) {
            consumeMoveConfirmUp=false;return true;
        }
        if(movingKey!=null && (event.getKeyCode()==KeyEvent.KEYCODE_DPAD_CENTER
                ||event.getKeyCode()==KeyEvent.KEYCODE_ENTER||event.getKeyCode()==KeyEvent.KEYCODE_NUMPAD_ENTER
                ||event.getKeyCode()==KeyEvent.KEYCODE_BACK)) {
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) {
                consumeMoveConfirmUp=true;finishMove(event.getKeyCode()!=KeyEvent.KEYCODE_BACK);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    private void finishMove(boolean save) {
        String key=movingKey;movingKey=null;
        if(moveHint!=null)moveHint.setVisibility(View.GONE);
        if(save){adapter.persistOrder();ownerRevision=LocalPreferences.revision(this);}
        else {adapter.reload(cards,()->{int restored=adapter.positionOf(key);lastFocused=Math.max(0,restored);lastFocusedKey=adapter.keyAt(lastFocused);focusTile(lastFocused);});return;}
        int position=adapter.positionOf(key);
        lastFocused=Math.max(0,position);lastFocusedKey=adapter.keyAt(lastFocused);
        grid.post(()->focusTile(lastFocused));
        if(save)Toast.makeText(this,this.getString(R.string.ui_ordem_dos_aplicativos_salva_2703),Toast.LENGTH_SHORT).show();
    }
    private void updateWeather() {
        String label=WeatherData.text(this);
        weather.setText(label);weather.setVisibility(label.isEmpty()?View.GONE:View.VISIBLE);
        if(weatherIcon==null)weatherIcon=new GoogleWeatherIcon(this,weather);weatherIcon.update();
        android.content.SharedPreferences p=LocalPreferences.get(this);
        if(!p.getBoolean("weather_enabled",true)||!p.contains("weather_lat")||weatherBusy)return;
        long now=System.currentTimeMillis(),cached=p.getLong("weather_updated",0);
        if(WeatherData.coordinateKey(p).equals(p.getString("weather_cache_key","")) && now>=cached && now-cached<WeatherData.FRESH_MS&&p.contains("weather_code"))return;
        if(now>=weatherAttempt && now-weatherAttempt<5*60_000L)return;
        weatherAttempt=now;weatherBusy=true;
        Context app=getApplicationContext();
        new Thread(()->{
            try{WeatherData.refresh(app);}catch(Exception ignored){}
            finally{weatherBusy=false;handler.post(()->{if(!isDestroyed()){String text=WeatherData.text(this);weather.setText(text);weather.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE);if(weatherIcon!=null&&resumed)weatherIcon.update();}});}
        },"home-weather").start();
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateGreeting() {
        android.widget.TextClock date=findViewById(R.id.date);
        String formatted=new java.text.SimpleDateFormat(getString(R.string.date_format),AppLanguage.locale(this)).format(new java.util.Date());
        String literal="'"+formatted.replace("'","''")+"'";date.setFormat12Hour(literal);date.setFormat24Hour(literal);
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        if (hour < 5) res = R.string.good_night;
        else if (hour < 12) res = R.string.good_morning;
        else if (hour < 17) res = R.string.good_afternoon;
        else if (hour < 22) res = R.string.good_evening;
        else res = R.string.good_night;
        greeting.setText(res);
        if (shamsi) shamsiDate.setText(Shamsi.today());
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        String text = getString(R.string.wifi);
        boolean vpn = false, online = false;
        int icon = R.drawable.ic_wifi;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean wifi = false, ethernet = false;
            // All networks, not just the active one: with a VPN up the active network is the VPN itself.
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true;
                    continue;
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                online = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
            }
            if (!online) {
                text = getString(R.string.offline);
                vpn = false;
            } else if (ethernet) {
                text = getString(R.string.ethernet);
                icon = R.drawable.ic_ethernet;
            } else if (wifi) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        btnWifi.setContentDescription(text+(vpn?" · VPN":!vpnApp.isEmpty()&&online?getString(R.string.ui_vpn_desligada_a31a):""));
        btnWifi.setImageResource(LauncherTheme.ios(this)?(icon==R.drawable.ic_ethernet?R.drawable.ic_ios_ethernet:R.drawable.ic_ios_wifi):icon);
    }

    /** The network pill opens the VPN app when one is configured and installed, otherwise Wi-Fi settings. */
    private void openNetworkPill() {
        Intent i = vpnApp.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(vpnApp);
        if (i == null && !vpnApp.isEmpty()) i = getPackageManager().getLaunchIntentForPackage(vpnApp);
        open(i != null ? i : systemIntent(Settings.ACTION_WIFI_SETTINGS));
    }


    /**
     * China-market MIUI TV firmware answers no standard Settings intents at all; open Xiaomi's own
     * settings app (com.xiaomi.mitv.settings) there, so the Settings and Wi-Fi pills keep working.
     * On firmware with a standard settings handler the behaviour is unchanged.
     */
    private Intent systemIntent(String standardAction) {return SystemSettings.intent(this,standardAction);}

    /** Hides the Inputs pill when it would have nothing to list: streaming boxes have no HDMI-in, AV or tuner. */
    private void updateInputsButton() {
        boolean any = liveTvIntent() != null || !sourcePanelService.isEmpty();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (!any && tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput()) {
                    any = true;
                    break;
                }
            }
        }
        btnInputs.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    /** The Live TV app's launch intent: its leanback entry point, or the configured action if it has none. */
    private Intent liveTvIntent() {
        if (liveTvPkg.isEmpty()) return null;
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (i != null || liveTvAction.isEmpty()) return i;
        Intent byAction = new Intent(liveTvAction).setPackage(liveTvPkg);
        return getPackageManager().resolveActivity(byAction, 0) != null ? byAction : null;
    }

    /** The TV's own source-selection panel, for TVs that have no TvInputManager (TCL Android TV 9). */
    private void openSourcePanel() {
        Intent i = new Intent(sourcePanelAction);
        ComponentName c = ComponentName.unflattenFromString(sourcePanelService);
        if (c != null) i.setComponent(c);
        try {
            startService(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /** Switches to a passthrough input (HDMI, AV). The standard way is ACTION_VIEW on its channel URI,
     *  which the Live TV app handles; where nothing takes that, the configured `input_action` is sent
     *  with the same URI instead (XGIMI's HDMI player only answers com.xgimi.action.hdmiPlayer). */
    private void openInput(String inputId) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(view);
            return;
        } catch (ActivityNotFoundException e) {
            if (inputAction.isEmpty()) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        open(new Intent(inputAction).setDataAndType(uri, "vnd.android.cursor.item/channel"));
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = findViewById(R.id.btnInputs);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        final PopupWindow popup = glassPopup(panel, width);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        boolean tunerIsHw = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    // Prefer a hardware tuner: the framework names those ".../HW<n>". System apps such as
                    // AirPlay also register software inputs of type TUNER and may sort first (seen on TCL G05).
                    boolean hw = info.getId().matches(".*/HW\\d+$");
                    if (tunerId == null || (hw && !tunerIsHw) || (hw == tunerIsHw && info.getId().compareTo(tunerId) < 0)) {
                        tunerId = info.getId();
                        tunerIsHw = hw;
                    }
                }
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> openInput(id));
            }
        }
        // Nothing to list where there is no TvInputManager: the TV's own source panel switches inputs.
        if (rows.getChildCount() == 0 && !sourcePanelService.isEmpty()) {
            addInputRow(rows, popup, R.drawable.ic_input, getString(R.string.inputs), -1, this::openSourcePanel);
        }
        Intent liveTv = liveTvIntent();
        if (liveTv != null && tunerId != null) {
            liveTv = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId), "vnd.android.cursor.item/channel")
                    .setPackage(liveTvPkg);
        }
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTvIntent));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(width, panel.getMeasuredWidth()));

        dropIn(popup, panel, anchor, Gravity.START);
        rows.getChildAt(0).requestFocus();
    }

    private PopupWindow glassPopup(View panel, int width) {
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (openPopup == popup) openPopup = null;
        });
        openPopup = popup;
        return popup;
    }

    /** Drop in: fade and unfold from the top edge, hanging under the anchor pill. */
    private void dropIn(PopupWindow popup, View panel, View anchor, int gravity) {
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), gravity);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    // ---- module cards ----------------------------------------------------------------------------

    /**
     * Asks about the next card that has no decision for its current signing key. One prompt at a
     * time, only over a laid out grid with window focus (not under a popup or another app), and
     * the next one follows as soon as this one is answered. Nothing is shown until the owner says Show.
     */
    private void showCardPrompt() {
        if(movingKey!=null)return;
        if (!moduleCards || !tilesReady || promptsPaused || cardPrompt != null || !hasWindowFocus() || isFinishing()) return;
        if (cards.isEmpty() || !CardFeature.working()) return;
        CardFeature.run(() -> {
            showCardPromptNow();
            return null;
        }, null);
    }

    private void showCardPromptNow() {
        final Cards.Card card = Cards.firstPending(cards);
        if (card == null) return;

        View panel = getLayoutInflater().inflate(R.layout.dialog_card_prompt, root, false);
        ((TextView) panel.findViewById(R.id.title)).setText(getString(R.string.card_prompt_title, card.appLabel));
        ((TextView) panel.findViewById(R.id.body)).setText(card.state == Cards.State.RESIGNED
                ? getString(R.string.card_prompt_resigned, card.appLabel)
                : card.state == Cards.State.RELABELLED
                ? getString(R.string.card_prompt_relabelled, card.appLabel)
                : getString(R.string.card_prompt_body, card.appLabel));
        TextView names = panel.findViewById(R.id.names);
        if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) names.setVisibility(View.GONE);
        else names.setText(getString(R.string.card_prompt_names, card.label, card.appLabel));
        ((TextView) panel.findViewById(R.id.identity)).setText(
                getString(R.string.card_prompt_identity, card.pkg, Cards.shortDigest(card.digest)));
        TextView keep = panel.findViewById(R.id.btnKeep);
        TextView show = panel.findViewById(R.id.btnShow);

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(false);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (440 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.6f);
        }
        // One decision per prompt: the buttons and Back (cancel) all end here.
        final boolean[] decided = {false};
        View.OnClickListener choose = v -> {
            if (decided[0]) return;
            decided[0] = true;
            cardPrompt = null;
            dialog.dismiss();
            decideCard(card, v.getId() == R.id.btnShow);   // guards its own card calls; the tile reload stays outside
            restoreFocus();   // the tiles changed under the prompt; this also chains the next prompt
        };
        keep.setOnClickListener(choose);
        show.setOnClickListener(choose);
        dialog.setOnCancelListener(d -> choose.onClick(keep));
        dialog.setOnDismissListener(d -> {
            if (cardPrompt == dialog) cardPrompt = null;
        });
        // Left and Right move between the two buttons; Up and Down stay put; the ends don't wrap.
        View.OnKeyListener keys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v.getId() == R.id.btnKeep;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnShow;
        };
        keep.setOnKeyListener(keys);
        show.setOnKeyListener(keys);
        cardPrompt = dialog;
        dialog.show();
        keep.requestFocus();
    }

    /** Writes the owner's decision and rebuilds the tiles; focus is left where it is. */
    private void decideCard(Cards.Card card, boolean show) {
        boolean saved = CardFeature.run(() -> Cards.decide(Cards.storeFile(this), card, show), false);
        if (!saved) {
            Toast.makeText(this, R.string.card_not_saved, Toast.LENGTH_SHORT).show();
            promptsPaused = true;
        }
        reloadTiles(()->{});
    }

    /** Dropdown under the Settings pill (hold OK): every discovered card, its package and key; OK toggles Shown and Hidden. */
    private void showModuleCards() {
        if (cards.isEmpty() || !CardFeature.working()) {
            Toast.makeText(this, R.string.no_module_cards, Toast.LENGTH_SHORT).show();
            return;
        }
        CardFeature.run(() -> {
            showModuleCardsNow();
            return null;
        }, null);
    }

    private void showModuleCardsNow() {
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_cards, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = glassPopup(panel, (int) (400 * density));
        List<Cards.Card> listed = new ArrayList<>(cards);
        Collections.sort(listed, Cards::byLabel);
        for (Cards.Card card : listed) {
            View row = getLayoutInflater().inflate(R.layout.item_card_row, rows, false);
            row.setTag(card.pkg);
            ((TextView) row.findViewById(R.id.title)).setText(card.label);
            TextView app = row.findViewById(R.id.app);
            if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) app.setVisibility(View.GONE);
            else app.setText(getString(R.string.card_app_name, card.appLabel));
            ((TextView) row.findViewById(R.id.pkg)).setText(card.pkg);
            ((TextView) row.findViewById(R.id.digest)).setText(Cards.longDigest(card.digest));
            final String pkg = card.pkg;
            row.setOnClickListener(v -> CardFeature.run(() -> {
                // The list stays open; the decision is for the card as it is now, not as it was listed.
                Cards.Card now = cardFor(pkg);
                if (now == null) return null;
                decideCard(now, now.state != Cards.State.SHOWN);
                // Every row, not only this one: a toggle can move another card across the limit of 8.
                updateCardStates(rows);
                return null;
            }, null));
            rows.addView(row);
        }
        updateCardStates(rows);
        // Scroll inside the panel instead of running off the bottom of a 720p screen.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int room = root.getHeight() - (at[1] + anchor.getHeight()) - (int) (34 * density);
        popup.setHeight(Math.min(panel.getMeasuredHeight(), Math.max(room, (int) (120 * density))));
        dropIn(popup, panel, anchor, Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    private Cards.Card cardFor(String pkg) {
        for (Cards.Card c : cards) if (c.pkg.equals(pkg)) return c;
        return null;
    }

    /** Writes the state of every row in the Module cards list from the current card list. */
    private void updateCardStates(LinearLayout rows) {
        List<Cards.Card> drawn = Cards.shown(cards);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            Cards.Card card = row.getTag() instanceof String ? cardFor((String) row.getTag()) : null;
            ((TextView) row.findViewById(R.id.state)).setText(card != null ? stateText(card, drawn.contains(card)) : "");
        }
    }

    /** The row's state: a card the owner set to Shown but which is beyond the eight drawn says so. */
    private String stateText(Cards.Card card, boolean drawn) {
        switch (card.state) {
            case SHOWN:
                return getString(drawn ? R.string.card_state_shown : R.string.card_state_over_limit);
            case HIDDEN:
                return getString(R.string.card_state_hidden);
            case RESIGNED:
                return getString(R.string.card_state_new_key);
            case RELABELLED:
                return getString(R.string.card_state_new_label);
            default:
                return getString(R.string.card_state_new);
        }
    }
}
