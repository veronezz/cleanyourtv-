package com.gathin.tvhome;
import android.content.Context;
import org.json.*;
import java.util.*;
/** Launcher-only removal. No write is made to another app's database or subscriptions. */
final class HistoryDismissals {
    static final String KEY="removed_continue_cards_v14",AUTO="hide_near_end_five_minutes";
    static String key(TvContentRepository.Item item){return item.pkg+"|"+item.stableKey;}
    static final class State {
        final Map<String,ContinuePolicy.Removed> markers=new LinkedHashMap<>();boolean changed;
        State(String json){try{JSONObject o=new JSONObject(json);Iterator<String> keys=o.keys();while(keys.hasNext()){String k=keys.next();JSONObject v=o.getJSONObject(k);markers.put(k,new ContinuePolicy.Removed(v.optLong("after"),v.optInt("available",-1)));}}catch(Exception ignored){}}
        boolean hidden(TvContentRepository.Item item){return markers.containsKey(key(item));}
        void playback(TvContentRepository.Item item){String k=key(item);if(ContinuePolicy.newPlayback(markers.get(k),item.engagement)){markers.remove(k);changed=true;}}
        void episodes(TvContentRepository.Item item){String k=key(item);if(ContinuePolicy.newEpisodes(markers.get(k),ContinuePolicy.available(item.detail))){markers.remove(k);changed=true;}}
        void generic(List<TvContentRepository.Item> items,boolean auto) {
            for(TvContentRepository.Item item:items)playback(item);
            items.removeIf(item->hidden(item)||(auto&&ContinuePolicy.nearEnd(item.position,item.duration)));
        }
        void unitv(List<TvContentRepository.Item> history,List<TvContentRepository.Item> updates,List<TvContentRepository.Item> followed,boolean auto) {
            for(TvContentRepository.Item item:history)playback(item);
            for(TvContentRepository.Item item:updates)episodes(item);
            Set<String> news=new HashSet<>(),finished=new HashSet<>();
            for(TvContentRepository.Item item:updates)news.add(key(item));
            if(auto)for(TvContentRepository.Item item:history)if(ContinuePolicy.nearEnd(item.position,item.duration))finished.add(key(item));
            history.removeIf(item->hidden(item)||finished.contains(key(item)));
            updates.removeIf(this::hidden);
            followed.removeIf(item->hidden(item)||(finished.contains(key(item))&&!news.contains(key(item))));
        }
        String json(){JSONObject o=new JSONObject();for(Map.Entry<String,ContinuePolicy.Removed> e:markers.entrySet())try{o.put(e.getKey(),new JSONObject().put("after",e.getValue().after).put("available",e.getValue().available));}catch(JSONException ignored){}return o.toString();}
    }
    static State read(Context c){return new State(LocalPreferences.get(c).getString(KEY,"{}"));}
    static void save(Context c,State state){if(state.changed)LocalPreferences.get(c).edit().putString(KEY,state.json()).apply();}
    static synchronized void remove(Context c,TvContentRepository.Item item) {
        State state=read(c);state.markers.remove(key(item));
        state.markers.put(key(item),new ContinuePolicy.Removed(Math.max(System.currentTimeMillis(),item.engagement),item.availableEpisodes));
        while(state.markers.size()>512)state.markers.remove(state.markers.keySet().iterator().next());
        state.changed=true;save(c,state);
    }
    static synchronized int count(Context c){return read(c).markers.size();}
    static synchronized void restore(Context c){LocalPreferences.save(c,LocalPreferences.get(c).edit().remove(KEY));}
    static synchronized void generic(Context c,List<TvContentRepository.Item> items) {
        State state=read(c);state.generic(items,LocalPreferences.get(c).getBoolean(AUTO,false));save(c,state);
    }
    static synchronized void unitv(Context c,List<TvContentRepository.Item> history,List<TvContentRepository.Item> updates,List<TvContentRepository.Item> followed) {
        State state=read(c);state.unitv(history,updates,followed,LocalPreferences.get(c).getBoolean(AUTO,false));save(c,state);
    }
}
