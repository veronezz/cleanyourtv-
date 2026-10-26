package com.gathin.tvhome;
import android.content.Context;
/** Explicit fresh read using the launcher UID, off the main thread. */
final class MediaDiagnostics {
    static String read(Context c){
        android.content.SharedPreferences p=LocalPreferences.get(c);StringBuilder out=new StringBuilder();
        out.append(c.getString(R.string.rows_master)).append(": ").append(p.getBoolean(DynamicRows.ALL,false)?c.getString(R.string.rows_show):c.getString(R.string.rows_apps_only));
        out.append("\n").append(MediaCapabilities.summary(c));
        out.append("\nSessões de mídia: ").append(MediaAccess.listenerAllowed(c)?"autorizadas":"não autorizadas");
        out.append("\nMídias recentes: ").append(RecentMedia.enabled(c)?"ON":"OFF").append("; apps escolhidos: ").append(RecentMedia.apps(c).size());
        out.append("\nREAD_TV_LISTINGS: ").append(c.checkSelfPermission("android.permission.READ_TV_LISTINGS")==0?"OK":"DENIED");
        out.append("\nREAD_EPG_DATA: ").append(c.checkSelfPermission("com.android.providers.tv.permission.READ_EPG_DATA")==0?"OK":"DENIED");
        for(String key:new String[]{DynamicRows.WATCH,DynamicRows.LIVE,DynamicRows.COBALT,DynamicRows.YOUTUBE,DynamicRows.UNITV})out.append("\n").append(key).append(": ").append(DynamicRows.enabled(c,key)?"ON":"OFF");
        for(String pkg:new String[]{TvContentRepository.GLOBO,RecommendationChannels.COBALT,RecommendationChannels.COBALT_TEST,RecommendationChannels.YOUTUBE,UniTvRepository.PKG}){
            out.append("\n").append(pkg).append(": ");try{out.append(c.getPackageManager().getApplicationInfo(pkg,0).enabled?"ON":"DISABLED");}catch(Exception e){out.append("NOT VISIBLE / NOT INSTALLED");}
        }
        try{TvContentRepository.read(c);out.append("\n\n").append(p.getString("tv_content_diagnostic",""));}catch(Exception e){out.append("\n").append(e.getClass().getSimpleName());}
        out.append("\n\nSpotify row: ").append(DynamicRows.enabled(c,DynamicRows.MUSIC)?"ON":"OFF");
        if(DynamicRows.enabled(c,DynamicRows.MUSIC))try{android.media.session.MediaSessionManager manager=c.getSystemService(android.media.session.MediaSessionManager.class);java.util.List<android.media.session.MediaController> sessions=manager.getActiveSessions(new android.content.ComponentName(c,PlaybackAccessService.class));int spotify=0;for(android.media.session.MediaController session:sessions)if("com.spotify.tv.android".equals(session.getPackageName())||"com.spotify.music".equals(session.getPackageName()))spotify++;out.append("\nMedia access: OK; Spotify sessions: ").append(spotify);}catch(SecurityException e){out.append("\nMedia access: DENIED (SecurityException)");}catch(Exception e){out.append("\nMedia access: ").append(e.getClass().getSimpleName());}
        out.append("\n\n").append(c.getString(R.string.media_reset_help));return out.toString();
    }
}
