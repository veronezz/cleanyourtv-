package com.gathin.tvhome;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Home-only observers. No audio player, credential access, video extraction or background polling. */
final class HomeMediaPanel {
    interface Host { default void musicVisibility(boolean shown){} void musicBackground(Bitmap bitmap); void feedsChanged(); void focusApps(); }
    private final Activity activity;
    private final LinearLayout root,now,content;
    private final FrameLayout hero;
    private MediaRow watchRow,liveRow;
    private final java.util.Map<String,RecommendationShelf> recommendationShelves=new java.util.LinkedHashMap<>();
    private static final class RecommendationShelf {final MediaRow row;final View heading;RecommendationShelf(MediaRow row,View heading){this.row=row;this.heading=heading;}}
    private TextView emptyWatch;
    private View watchHeading,liveHeading;
    private boolean feedsReady;
    private final boolean reducedMotion;
    private android.animation.ValueAnimator gapAnimator,musicAnimator;
    private int gapTarget=-1;
    private boolean musicShown;
    private final Host host;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final MediaArtwork images=new MediaArtwork();
    private final java.util.concurrent.ThreadPoolExecutor artworkWorker=(java.util.concurrent.ThreadPoolExecutor)Executors.newFixedThreadPool(1);
    private String blurKey="";private Bitmap blurred;

    private final MediaSessionManager manager;
    private final ComponentName listener;
    private final TextView title,artist,time,play,previous,next;
    private final ImageView cover;
    private final RoundedProgressView progress;
    private MediaController controller;
    private MediaMetadata snapshot;
    private PlaybackState playback;
    private volatile boolean active,closed;
    private boolean sessionRegistered,providerRegistered,mediaAllowed;
    private int generation;
    private java.util.concurrent.Future<?> feedTask;
    private android.os.CancellationSignal feedSignal;
    private boolean feedRunning,feedAgain;
    private String trackKey="";
    private String feedSignature="";
    private final Runnable reload=()->loadFeeds();
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener recentListener=(p,key)->{if(RecentMedia.DATA.equals(key)&&active){handler.removeCallbacks(reload);handler.postDelayed(reload,600);}};
    private final Runnable settleReload=()->loadFeeds();
    private final Runnable minute=new Runnable(){public void run(){if(!active)return;loadFeeds();handler.postDelayed(this,providerRegistered?300_000:60_000);}};
    private final Runnable tick=new Runnable(){public void run(){if(!active)return;updatePosition();handler.postDelayed(this,1000);}};
    private final ContentObserver observer=new ContentObserver(handler){
        @Override public void onChange(boolean self){handler.removeCallbacks(reload);handler.postDelayed(reload,600);}
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions=this::selectSpotify;
    private final MediaController.Callback callback=new MediaController.Callback(){
        @Override public void onMetadataChanged(MediaMetadata data){snapshot=data;renderTrack();}
        @Override public void onPlaybackStateChanged(PlaybackState state){playback=state;renderTrack();}
        @Override public void onSessionDestroyed(){selectSessions();}
    };
    HomeMediaPanel(Activity activity,LinearLayout root,Host host){
        this.activity=activity;this.root=root;this.host=host;reducedMotion=Config.load(activity).reducedMotion;
        manager=activity.getSystemService(MediaSessionManager.class);listener=new ComponentName(activity,PlaybackAccessService.class);
        root.setOrientation(LinearLayout.VERTICAL);root.setClipChildren(false);root.setClipToPadding(false);
        now=new LinearLayout(activity);now.setOrientation(LinearLayout.VERTICAL);now.setVisibility(View.GONE);root.addView(now);
        hero=new FrameLayout(activity);hero.setTag(R.id.media_player_panel,true);now.addView(hero,new LinearLayout.LayoutParams(-1,dp(310)));
        LinearLayout card=new LinearLayout(activity);card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(8),dp(8),dp(8),dp(18));
        int available=activity.getResources().getDisplayMetrics().widthPixels-dp(112);
        FrameLayout.LayoutParams cardLp=new FrameLayout.LayoutParams(Math.min(available,dp(600)),-2,Gravity.BOTTOM|Gravity.START);
        hero.addView(card,cardLp);
        cover=new ImageView(activity);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setContentDescription(activity.getString(R.string.ui_abrir_spotify_1bb6));
        round(cover,dp(8));card.addView(cover,new LinearLayout.LayoutParams(dp(100),dp(100)));
        cover.setFocusable(true);cover.setOnClickListener(v->openSpotify());
        cover.setOnFocusChangeListener((v,f)->{v.animate().scaleX(f?1.05f:1f).scaleY(f?1.05f:1f).setDuration(120).start();v.setAlpha(f?1f:.9f);});
        LinearLayout info=new LinearLayout(activity);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(18),0,0,0);card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        title=text("",26);title.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);info.addView(title);
        artist=text("",16);artist.setTextColor(0xffe0e6ec);artist.setSingleLine(true);artist.setEllipsize(TextUtils.TruncateAt.END);info.addView(artist);
        progress=new RoundedProgressView(activity);LinearLayout.LayoutParams progressLp=new LinearLayout.LayoutParams(-1,dp(4));progressLp.topMargin=dp(8);progressLp.bottomMargin=dp(2);info.addView(progress,progressLp);
        time=text("",12);time.setTextColor(0xffd8dfe6);info.addView(time);
        LinearLayout controls=new LinearLayout(activity);controls.setTag(R.id.contrast_protected,true);info.addView(controls);
        previous=control(activity.getString(R.string.ui_anterior_b8f3),controls,()->transport(0));play=control(activity.getString(R.string.ui_pausar_908b),controls,()->transport(1));next=control(activity.getString(R.string.ui_proxima_e3a5),controls,()->transport(2));
        root.post(this::resizeHero);
        content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setClipChildren(false);content.setClipToPadding(false);root.addView(content);arrangeSections();
    }
    void resizeHero(){
        View viewport=activity.findViewById(R.id.homeScroll);
        View gap=activity.findViewById(R.id.headerGap);
        if(gap!=null){
            float density=activity.getResources().getDisplayMetrics().density;
            int width=activity.getResources().getDisplayMetrics().widthPixels-dp(112);
            int line=AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(TileSizing.width(activity,width)),density);
            int wanted=musicShown?0:HomeLayoutPolicy.clockGap(line,LocalPreferences.get(activity).getString("clock_spacing","normal"));
            if(gap.getParent() instanceof View){View column=(View)gap.getParent();if(column.getHeight()>0)wanted=Math.min(wanted,Math.max(0,column.getHeight()-gap.getTop()-line-dp(70)));}
            if(gapTarget!=wanted){
                gapTarget=wanted;
                if(gapAnimator!=null)gapAnimator.cancel();
                if(active&&gap.isLaidOut()&&!reducedMotion){
                    gapAnimator=android.animation.ValueAnimator.ofInt(gap.getHeight(),wanted);
                    gapAnimator.setDuration(300);gapAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
                    gapAnimator.addUpdateListener(animation->{android.view.ViewGroup.LayoutParams lp=gap.getLayoutParams();lp.height=(Integer)animation.getAnimatedValue();gap.setLayoutParams(lp);});
                    gapAnimator.start();
                }else{android.view.ViewGroup.LayoutParams lp=gap.getLayoutParams();lp.height=wanted;gap.setLayoutParams(lp);}
            }
        }
        if(viewport==null||viewport.getHeight()==0)return;
        int height=Math.max(dp(230),viewport.getHeight()-dp(20));
        if(hero.getLayoutParams().height!=height){android.view.ViewGroup.LayoutParams lp=hero.getLayoutParams();lp.height=height;hero.setLayoutParams(lp);}
    }
    private void round(View view,float radius){
        view.setOutlineProvider(new android.view.ViewOutlineProvider(){
            @Override public void getOutline(View v,android.graphics.Outline out){out.setRoundRect(0,0,v.getWidth(),v.getHeight(),radius);}
        });view.setClipToOutline(true);
    }
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(Color.WHITE);v.setTypeface(activity.getResources().getFont(R.font.outfit));v.setFontVariationSettings("'wght' 300");v.setPadding(0,dp(5),0,dp(5));return v;}
    private GradientDrawable surface(boolean focus){if(LauncherTheme.ios(activity))return IosStyle.surface(activity,focus,16);if(LauncherTheme.smoked(activity)){GradientDrawable glass=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{focus?0x80435458:0x64313b3c,0x96101b1d});glass.setCornerRadius(dp(16));glass.setStroke(dp(1),focus?0xa3efffff:0x55cbdcde);return glass;}GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{focus?0x883d6980:0x66304460,focus?0x8832475b:0x44182338});d.setCornerRadius(dp(16));d.setStroke(dp(1),focus?0xff8fe9ff:0x35ffffff);return d;}
    private TextView control(String label,LinearLayout row,Runnable action){TextView b=text(label,15);b.setTextColor(IosStyle.text(activity,Color.WHITE));b.setGravity(Gravity.CENTER);b.setPadding(dp(14),dp(6),dp(14),dp(6));b.setFocusable(true);b.setClickable(true);b.setBackground(surface(false));b.setOnFocusChangeListener((v,f)->v.setBackground(surface(f)));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.rightMargin=dp(10);row.addView(b,lp);return b;}
    void start(){
        if(active||closed)return;active=true;images.resume();LocalPreferences.get(activity).registerOnSharedPreferenceChangeListener(recentListener);
        if(DynamicRows.enabled(activity,DynamicRows.MUSIC)&&manager!=null)try{manager.addOnActiveSessionsChangedListener(sessions,listener,handler);sessionRegistered=true;}catch(RuntimeException ignored){}
        selectSessions();loadFeeds();
        if(DynamicRows.enabled(activity,DynamicRows.UNITV))handler.postDelayed(settleReload,1500);
        if(DynamicRows.enabled(activity,DynamicRows.WATCH)||DynamicRows.enabled(activity,DynamicRows.UNITV))try{activity.getContentResolver().registerContentObserver(UniTvRepository.ROOT,true,observer);}catch(Exception ignored){}
        if(DynamicRows.enabled(activity,DynamicRows.WATCH))try{activity.getContentResolver().registerContentObserver(TvContentRepository.WATCH,true,observer);providerRegistered=true;}catch(Exception ignored){}
        if(DynamicRows.enabled(activity,DynamicRows.LIVE)||DynamicRows.recommendations(activity))try{activity.getContentResolver().registerContentObserver(TvContentRepository.PREVIEW,true,observer);providerRegistered=true;}catch(Exception ignored){}
        if(DynamicRows.enabled(activity,DynamicRows.LIVE)||DynamicRows.recommendations(activity))try{activity.getContentResolver().registerContentObserver(android.net.Uri.parse("content://android.media.tv/channel"),true,observer);providerRegistered=true;}catch(Exception ignored){}
        if(DynamicRows.enabled(activity,DynamicRows.MUSIC))handler.post(tick);if(DynamicRows.feeds(activity))handler.postDelayed(minute,providerRegistered?300_000:60_000);
    }
    void stop(){
        active=false;LocalPreferences.get(activity).unregisterOnSharedPreferenceChangeListener(recentListener);progress.stop();generation++;
        feedAgain=false;feedRunning=false;
        if(feedSignal!=null)feedSignal.cancel();feedSignal=null;
        if(feedTask!=null)feedTask.cancel(true);feedTask=null;
        images.pause();
        if(gapAnimator!=null)gapAnimator.cancel();if(musicAnimator!=null)musicAnimator.cancel();
        View gap=activity.findViewById(R.id.headerGap);
        if(gap!=null&&gapTarget>=0){android.view.ViewGroup.LayoutParams lp=gap.getLayoutParams();lp.height=gapTarget;gap.setLayoutParams(lp);}
        now.setVisibility(musicShown?View.VISIBLE:View.GONE);now.setAlpha(1f);
        android.view.ViewGroup.LayoutParams nowLp=now.getLayoutParams();nowLp.height=-2;now.setLayoutParams(nowLp);handler.removeCallbacks(tick);handler.removeCallbacks(minute);handler.removeCallbacks(reload);handler.removeCallbacks(settleReload);
        if(manager!=null&&sessionRegistered)try{manager.removeOnActiveSessionsChangedListener(sessions);}catch(Exception ignored){}sessionRegistered=false;
        if(providerRegistered)activity.getContentResolver().unregisterContentObserver(observer);providerRegistered=false;
        if(controller!=null)controller.unregisterCallback(callback);controller=null;snapshot=null;playback=null;
    }
    void refreshTheme(){arrangeSections();for(TextView button:new TextView[]{previous,play,next}){button.setBackground(surface(button.hasFocus()));button.setTextColor(IosStyle.text(activity,Color.WHITE));}}
    void trim(){images.trim();blurKey="";blurred=null;}
    void close(){stop();closed=true;images.close();io.shutdownNow();artworkWorker.shutdownNow();blurred=null;}
    private void selectSessions(){
        if(!DynamicRows.enabled(activity,DynamicRows.MUSIC)){hideNow();return;}
        if(manager==null){selectSpotify(java.util.Collections.emptyList());return;}
        try{List<MediaController> list=manager.getActiveSessions(listener);mediaAllowed=true;selectSpotify(list);}
        catch(RuntimeException e){mediaAllowed=false;activity.getSharedPreferences("owner_preferences",0).edit().putString("media_session_diagnostic",activity.getString(R.string.ui_acesso_as_sessoes_de_midia_nao_autorizado_cf79)).apply();selectSpotify(java.util.Collections.emptyList());}
    }
    private void selectSpotify(List<MediaController> controllers){
        if(!active)return;
        if(!DynamicRows.enabled(activity,DynamicRows.MUSIC)){hideNow();return;}
        MediaController best=null;
        if(controllers!=null)for(MediaController c:controllers){
            if(!"com.spotify.tv.android".equals(c.getPackageName())&&!"com.spotify.music".equals(c.getPackageName()))continue;
            PlaybackState p=c.getPlaybackState();if(p==null||p.getState()==PlaybackState.STATE_STOPPED||p.getState()==PlaybackState.STATE_NONE)continue;
            if(best==null)best=c;if(p.getState()==PlaybackState.STATE_PLAYING){best=c;break;}
        }
        if(controller!=null&&best!=null&&controller.getSessionToken().equals(best.getSessionToken())){renderTrack();return;}
        if(controller!=null)controller.unregisterCallback(callback);
        controller=best;snapshot=null;playback=null;
        if(controller!=null)controller.registerCallback(callback,handler);
        LocalPreferences.get(activity).edit().putString("media_session_diagnostic",!mediaAllowed?activity.getString(R.string.ui_acesso_as_sessoes_de_midia_nao_autorizado_cf79):controller==null?activity.getString(R.string.ui_acesso_autorizado_nenhuma_sessao_ativa_do_spotify_bd78):activity.getString(R.string.ui_sessao_ativa_do_spotify_disponivel_5f5c)).apply();
        renderTrack();
    }
    private void renderTrack(){
        MediaMetadata metadata=controller==null?null:snapshot;
        if(controller!=null&&metadata==null){metadata=controller.getMetadata();snapshot=metadata;}
        if(metadata==null){hideNow();return;}
        String name=metadata.getString(MediaMetadata.METADATA_KEY_TITLE),who=metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if(name==null||name.isEmpty()){hideNow();return;}
        showNow(true);
        title.setText(name);artist.setText(who==null?"Spotify":who);
        String key=name+"|"+who+"|"+metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);
        if(!key.equals(trackKey)){
            trackKey=key;cover.setTag(null);
            try{cover.setImageDrawable(activity.getPackageManager().getApplicationIcon(controller==null?"com.spotify.tv.android":controller.getPackageName()));}catch(Exception ignored){}
            Bitmap art=metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);if(art==null)art=metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);if(art==null)art=metadata.getDescription().getIconBitmap();
            if(art!=null){Bitmap shown=art;int edge=Math.max(art.getWidth(),art.getHeight());if(edge>512)shown=Bitmap.createScaledBitmap(art,Math.max(1,art.getWidth()*512/edge),Math.max(1,art.getHeight()*512/edge),true);cover.setImageBitmap(shown);backdrop(shown,key);}
            else{String uri=metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);if(uri==null||uri.isEmpty())uri=metadata.getString(MediaMetadata.METADATA_KEY_ART_URI);if((uri==null||uri.isEmpty())&&metadata.getDescription().getIconUri()!=null)uri=metadata.getDescription().getIconUri().toString();images.load(activity,uri==null?"":uri,cover,b->backdrop(b,key));}
        }
        renderState();
    }
    private void backdrop(Bitmap art,String key){
        if(closed)return;if(key.equals(blurKey)&&blurred!=null){host.musicBackground(blurred);return;}
        artworkWorker.getQueue().clear();
        artworkWorker.execute(()->{try{Bitmap blur=MediaArtwork.blur(art);handler.post(()->{if(!closed&&key.equals(trackKey)){blurKey=key;blurred=blur;if(active)host.musicBackground(blur);}});}catch(Exception|OutOfMemoryError ignored){}});
    }
    private void showNow(boolean show){
        host.musicVisibility(show);if(musicShown==show)return;
        musicShown=show;
        if(musicAnimator!=null)musicAnimator.cancel();
        int from=now.getVisibility()==View.VISIBLE?now.getHeight():0;
        float fromAlpha=now.getVisibility()==View.VISIBLE?now.getAlpha():0f;
        resizeHero();
        if(reducedMotion||!active||!root.isLaidOut()){
            now.setVisibility(show?View.VISIBLE:View.GONE);now.setAlpha(1f);
            android.view.ViewGroup.LayoutParams lp=now.getLayoutParams();lp.height=-2;now.setLayoutParams(lp);host.feedsChanged();return;
        }
        now.setVisibility(View.VISIBLE);
        android.view.ViewGroup.LayoutParams lp=now.getLayoutParams();lp.height=from;now.setLayoutParams(lp);
        musicAnimator=android.animation.ValueAnimator.ofFloat(0f,1f);musicAnimator.setDuration(300);
        musicAnimator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        musicAnimator.addUpdateListener(animation->{
            float fraction=(Float)animation.getAnimatedValue();int target=show?hero.getLayoutParams().height:0;
            android.view.ViewGroup.LayoutParams params=now.getLayoutParams();params.height=Math.round(from+(target-from)*fraction);now.setLayoutParams(params);
            now.setAlpha(fromAlpha+((show?1f:0f)-fromAlpha)*fraction);
        });
        musicAnimator.addListener(new android.animation.AnimatorListenerAdapter(){
            private boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator animation){cancelled=true;}
            @Override public void onAnimationEnd(android.animation.Animator animation){
                if(cancelled)return;
                now.setVisibility(show?View.VISIBLE:View.GONE);now.setAlpha(1f);
                android.view.ViewGroup.LayoutParams params=now.getLayoutParams();params.height=-2;now.setLayoutParams(params);host.feedsChanged();
            }
        });musicAnimator.start();
    }
    private void hideNow(){progress.stop();trackKey="";showNow(false);host.musicBackground(null);}
    private void renderState(){
        PlaybackState state=controller==null?null:playback;
        if(controller!=null&&state==null){state=controller.getPlaybackState();playback=state;}
        if(state==null||state.getState()==PlaybackState.STATE_NONE||state.getState()==PlaybackState.STATE_STOPPED){hideNow();return;}
        boolean playing=state.getState()==PlaybackState.STATE_PLAYING;play.setText(playing?activity.getString(R.string.ui_pausar_908b):activity.getString(R.string.ui_reproduzir_1928));
        long actions=state.getActions();enable(previous,(actions&PlaybackState.ACTION_SKIP_TO_PREVIOUS)!=0);enable(next,(actions&PlaybackState.ACTION_SKIP_TO_NEXT)!=0);
        enable(play,(actions&(PlaybackState.ACTION_PLAY_PAUSE|(playing?PlaybackState.ACTION_PAUSE:PlaybackState.ACTION_PLAY)))!=0);updatePosition();
    }
    private void enable(View v,boolean enabled){v.setEnabled(enabled);v.setAlpha(enabled?1f:.4f);}
    private void updatePosition(){
        if(controller==null||now.getVisibility()!=View.VISIBLE)return;PlaybackState state=playback;MediaMetadata data=snapshot;if(state==null||data==null)return;
        long duration=data.getLong(MediaMetadata.METADATA_KEY_DURATION),position=MediaMath.position(state.getPosition(),state.getLastPositionUpdateTime(),state.getPlaybackSpeed(),state.getState()==PlaybackState.STATE_PLAYING,SystemClock.elapsedRealtime(),duration);
        progress.timeline(state.getPosition(),duration,state.getLastPositionUpdateTime(),state.getPlaybackSpeed(),state.getState()==PlaybackState.STATE_PLAYING);
        time.setText(MediaMath.time(position)+" / "+(duration>0?MediaMath.time(duration):activity.getString(R.string.ui_total_nao_informado_4295))+(duration>0&&position>=0?activity.getString(R.string.ui_faltam_03dc)+MediaMath.time(Math.max(0,duration-position)):""));
    }
    private void transport(int action){try{
        if(controller==null)return;MediaController.TransportControls controls=controller.getTransportControls();
        if(action==0)controls.skipToPrevious();else if(action==2)controls.skipToNext();else{PlaybackState state=controller.getPlaybackState();if(state!=null&&state.getState()==PlaybackState.STATE_PLAYING)controls.pause();else controls.play();}
    }catch(Exception e){Toast.makeText(activity,activity.getString(R.string.ui_o_spotify_nao_respondeu_ao_controle_5836),Toast.LENGTH_SHORT).show();}}
    private void openSpotify(){try{PendingIntent intent=controller==null?null:controller.getSessionActivity();if(intent!=null)intent.send();else launchPackage(controller==null?"com.spotify.tv.android":controller.getPackageName());}catch(Exception e){launchPackage(controller==null?"com.spotify.tv.android":controller.getPackageName());}}
    private void loadFeeds(){
        if(!active||closed)return;
        if(!DynamicRows.feeds(activity)){
            generation++;feedAgain=false;feedRunning=false;
            if(feedSignal!=null)feedSignal.cancel();feedSignal=null;
            if(feedTask!=null)feedTask.cancel(true);feedTask=null;
            showFeeds(new TvContentRepository.Result());return;
        }
        if(feedRunning){feedAgain=true;return;}
        feedRunning=true;feedAgain=false;int request=++generation;
        Context app=AppLanguage.wrap(activity.getApplicationContext());
        android.os.CancellationSignal signal=new android.os.CancellationSignal();feedSignal=signal;
        feedTask=io.submit(()->{
            TvContentRepository.Result result=null;
            try{result=TvContentRepository.read(app,signal);}catch(android.os.OperationCanceledException ignored){}
            catch(RuntimeException error){android.util.Log.w("TvHomeFeeds","Could not refresh media rows",error);}
            final TvContentRepository.Result data=result;
            handler.post(()->{
                if(feedSignal!=signal)return;
                feedRunning=false;feedSignal=null;feedTask=null;
                if(!active||closed||request!=generation)return;
                if(data!=null)showFeeds(data);
                if(feedAgain){feedAgain=false;loadFeeds();}
            });
        });
    }
    private void showFeeds(TvContentRepository.Result data){
        if(watchRow==null){
            watchRow=row(activity.getString(R.string.ui_continuar_assistindo_0833));watchHeading=content.getChildAt(content.getChildCount()-2);
            emptyWatch=text(activity.getString(R.string.ui_nenhum_item_acessivel_os_apps_precisam_publicar_a_serie_3c96),14);emptyWatch.setTextColor(0xffc2d8ed);content.addView(emptyWatch);
            liveRow=row(activity.getString(R.string.ui_tv_ao_vivo_globoplay_731d));liveHeading=content.getChildAt(content.getChildCount()-2);
        }
        feedsReady=true;boolean watch=DynamicRows.enabled(activity,DynamicRows.WATCH),live=DynamicRows.enabled(activity,DynamicRows.LIVE);
        boolean lostFocus=!watch&&watchRow.view.hasFocus()||!live&&liveRow.view.hasFocus();
        watchHeading.setVisibility(watch?View.VISIBLE:View.GONE);liveHeading.setVisibility(live?View.VISIBLE:View.GONE);
        watchRow.submit(watch?data.watch:java.util.Collections.emptyList());watchRow.view.setVisibility(watch&&!data.watch.isEmpty()?View.VISIBLE:View.GONE);emptyWatch.setVisibility(watch&&data.watch.isEmpty()?View.VISIBLE:View.GONE);
        liveRow.view.setVisibility(live?View.VISIBLE:View.GONE);
        if(live&&data.live.isEmpty())liveRow.submit(java.util.Collections.singletonList(new TvContentRepository.Item(TvContentRepository.GLOBO,activity.getString(R.string.ui_abrir_globoplay_1f24),activity.getString(R.string.ui_escolha_agora_na_tv_dentro_do_aplicativo_7039),"","",-1,-1,0)));
        else liveRow.submit(live?data.live:java.util.Collections.emptyList());
        java.util.List<RecommendationChannels.Channel> visible=new java.util.ArrayList<>();for(RecommendationChannels.Channel channel:data.recommendations)if(DynamicRows.app(activity,channel.pkg))visible.add(channel);
        showRecommendations(visible);content.setVisibility(DynamicRows.feeds(activity)?View.VISIBLE:View.GONE);if(lostFocus)host.focusApps();
        host.feedsChanged();
    }
    private void showRecommendations(java.util.List<RecommendationChannels.Channel> channels){
        java.util.Set<String> keys=new java.util.HashSet<>();for(RecommendationChannels.Channel channel:channels)keys.add(channel.key);
        java.util.Iterator<java.util.Map.Entry<String,RecommendationShelf>> iterator=recommendationShelves.entrySet().iterator();
        boolean lostFocus=false;
        while(iterator.hasNext()){
            java.util.Map.Entry<String,RecommendationShelf> entry=iterator.next();
            if(!keys.contains(entry.getKey())){
                RecommendationShelf shelf=entry.getValue();lostFocus|=shelf.row.view.hasFocus();
                shelf.row.submit(java.util.Collections.emptyList());shelf.row.view.setAdapter(null);
                content.removeView(shelf.heading);content.removeView(shelf.row.view);iterator.remove();
            }
        }
        for(RecommendationChannels.Channel channel:channels){
            RecommendationShelf shelf=recommendationShelves.get(channel.key);
            if(shelf==null){
                MediaRow row=row(channel.name);shelf=new RecommendationShelf(row,content.getChildAt(content.getChildCount()-2));
                content.removeView(shelf.heading);content.removeView(row.view);
                int beforeLive=content.indexOfChild(liveRow.view)-1;
                content.addView(shelf.heading,beforeLive);content.addView(row.view,beforeLive+1);
                recommendationShelves.put(channel.key,shelf);
            }
            if(UniTvShelf.PKG.equals(channel.pkg)&&content.indexOfChild(shelf.heading)!=0){
                content.removeView(shelf.heading);content.removeView(shelf.row.view);
                content.addView(shelf.heading,0);content.addView(shelf.row.view,1);
            }
            ((TextView)((LinearLayout)shelf.heading).getChildAt(0)).setText(channel.name);shelf.row.submit(channel.items);
        }
        if(lostFocus)host.focusApps();
    }
    private MediaRow row(String name){
        LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView section=text(name,22);section.setSingleLine(true);section.setEllipsize(TextUtils.TruncateAt.END);section.setMaxWidth((activity.getResources().getDisplayMetrics().widthPixels-dp(112))*3/5);heading.addView(section);
        TextView selected=text("",14);selected.setSingleLine(true);selected.setEllipsize(TextUtils.TruncateAt.END);selected.setTextColor(0xffc8d9eb);
        LinearLayout.LayoutParams labelLp=new LinearLayout.LayoutParams(0,-2,1);labelLp.setMarginStart(dp(18));heading.addView(selected,labelLp);
        String spacing=HomeLayoutPolicy.profile(LocalPreferences.get(activity).getString("home_spacing","compact"));
        heading.setPadding(0,dp(HomeLayoutPolicy.headingTopDp(spacing)),0,dp(3));content.addView(heading);
        MediaRow row=new MediaRow(activity,selected,images,this::openItem,this::cardMenu);row.view.setTag(R.id.media_row_heading,heading);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,row.rowHeight());lp.leftMargin=-dp(8);lp.rightMargin=-dp(8);content.addView(row.view,lp);return row;
    }
    private boolean cardMenu(TvContentRepository.Item item){
        if(!item.continuation)return false;
        new android.app.AlertDialog.Builder(activity,GlassUi.dialogTheme(activity)).setTitle(item.title)
            .setItems(new String[]{"Remover da linha","Cancelar"},(dialog,which)->{
                if(which!=0)return;
                HistoryDismissals.remove(activity,item);boolean lost=false;
                if(watchRow!=null)lost|=watchRow.remove(item);
                for(RecommendationShelf shelf:recommendationShelves.values())lost|=shelf.row.remove(item);
                if(lost)host.focusApps();
                Toast.makeText(activity,"Cartão removido",Toast.LENGTH_SHORT).show();loadFeeds();
            }).show();
        return true;
    }
    private void openItem(TvContentRepository.Item item){
        if(item.intent.isEmpty()){launchPackage(item.pkg);return;}
        if("Mídia recente".equals(item.badge)){if(!RecentMedia.openActive(activity,item.pkg))launchPackage(item.pkg);return;}
        try{activity.startActivity(TvContentRepository.intent(item));}catch(Exception e){Toast.makeText(activity,activity.getString(R.string.ui_o_link_nao_abriu_retomada_disponivel_dentro_do_app_a0a8),Toast.LENGTH_LONG).show();launchPackage(item.pkg);}
    }
    private String appName(String pkg){try{return activity.getPackageManager().getApplicationLabel(activity.getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception e){return pkg;}}
    private void launchPackage(String pkg){try{Intent i=activity.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);if(i==null)i=activity.getPackageManager().getLaunchIntentForPackage(pkg);if(i!=null)activity.startActivity(i);else Toast.makeText(activity,activity.getString(R.string.ui_aplicativo_indisponivel_3e8b),Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(activity,activity.getString(R.string.ui_nao_foi_possivel_abrir_o_aplicativo_9335),Toast.LENGTH_SHORT).show();}}
    void arrangeSections(){
        View grid=activity.findViewById(R.id.grid);android.view.ViewGroup target=root;
        View gridBox=grid==null?null:(View)grid.getParent();
        if(gridBox!=null&&gridBox.getParent() instanceof LinearLayout)target=(android.view.ViewGroup)gridBox.getParent();
        if(content.getParent()==target)return;
        if(content.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)content.getParent()).removeView(content);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=target==root?0:dp(HomeLayoutPolicy.gapDp(HomeLayoutPolicy.profile(LocalPreferences.get(activity).getString("home_spacing","compact"))));
        target.addView(content,target==root?target.getChildCount():target.indexOfChild(gridBox)+1,lp);
    }
    boolean focusPlayer(){return now.getVisibility()==View.VISIBLE&&focusIn(now);}
    boolean focusRecommendations(){return content.getVisibility()==View.VISIBLE&&focusIn(content);}
    private boolean focusIn(View view){View first=view.getFocusables(View.FOCUS_FORWARD).stream().findFirst().orElse(null);return first!=null&&first.requestFocus();}
    boolean contentReady(){return feedsReady;}
    boolean focusFirst(){return focusPlayer();}
    boolean focusFromDock(){return focusPlayer();}
}
