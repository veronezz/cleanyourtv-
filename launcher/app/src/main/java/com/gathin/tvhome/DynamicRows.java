package com.gathin.tvhome;
import android.content.Context;
/** Master override preserves each individual choice so switching everything back on restores it. */
final class DynamicRows {
    static final String UNITV="unitv_updates_row",ALL="dynamic_rows_enabled",WATCH="watch_next_row",LIVE="live_tv_row",MUSIC="now_playing_row",COBALT="tizentube_row",YOUTUBE="youtube_row";
    static void migrate(Context c){
        android.content.SharedPreferences p=LocalPreferences.get(c);
        boolean migrated=p.getBoolean("dynamic_rows_migrated",false);
        android.content.SharedPreferences.Editor edit=p.edit();
        // Older installations used an implicit true master switch. Materialize it
        // before changing the default, without overriding any explicit choice.
        if(!p.contains(ALL)){
            boolean existing=migrated||p.contains(RecommendationChannels.ENABLED)
                    ||p.contains(WATCH)||p.contains(LIVE)||p.contains(MUSIC)
                    ||p.contains(COBALT)||p.contains(YOUTUBE);
            edit.putBoolean(ALL,existing);
        }
        if(!migrated){
            edit.putBoolean("dynamic_rows_migrated",true).putBoolean(RecommendationChannels.ENABLED,true);
            if(!p.getBoolean(RecommendationChannels.ENABLED,true)){
                if(!p.contains(COBALT))edit.putBoolean(COBALT,false);
                if(!p.contains(YOUTUBE))edit.putBoolean(YOUTUBE,false);
            }
        }
        edit.apply();
    }
    static boolean enabled(Context c,String key){return LocalPreferences.get(c).getBoolean(ALL,false)&&LocalPreferences.get(c).getBoolean(key,true);}
    static boolean recommendations(Context c){return enabled(c,RecommendationChannels.ENABLED)&&(enabled(c,COBALT)||enabled(c,YOUTUBE));}
    static boolean feeds(Context c){return enabled(c,WATCH)||enabled(c,LIVE)||recommendations(c)||enabled(c,UNITV);}
    static boolean app(Context c,String pkg){return UniTvRepository.PKG.equals(pkg)?enabled(c,UNITV):recommendations(c)&&enabled(c,RecommendationChannels.isCobalt(pkg)?COBALT:YOUTUBE);}
}
