package com.gathin.tvhome;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import java.util.*;

/** Public Android TV preview programs from the two supported YouTube TV clients. No account or network access. */
final class RecommendationChannels {
    static final String COBALT="io.gh.reisxd.tizentube.cobalt",COBALT_TEST="io.gh.reisxd.tizentube.testv3",YOUTUBE="com.google.android.youtube.tv";
    static final String ENABLED="youtube_recommendations";
    static final class Channel {
        final String key,pkg,name;
        final List<TvContentRepository.Item> items=new ArrayList<>();
        Channel(String key,String pkg,String name){this.key=key;this.pkg=pkg;this.name=name;}
    }
    static final class Result {final List<Channel> channels=new ArrayList<>();String status="";}
    private static final class Metadata {String pkg,name;Metadata(String pkg,String name){this.pkg=pkg;this.name=name;}}
    static String shelfTitle(Context c,String published){
        String detail=published==null?"":published.trim();
        // Publishers may already include the client/YouTube prefix. Keep it once.
        String previous;
        do{previous=detail;detail=detail.replaceFirst("(?i)^(?:TizenTube(?: Teste(?: v3)?)?|YouTube)(?:\\s*[·:|–-]\\s*|$)","").trim();}while(!previous.equals(detail));
        return "YouTube · "+(detail.isEmpty()?c.getString(R.string.recommendations_title):detail);
    }
    static boolean isCobalt(String pkg){return COBALT.equals(pkg)||COBALT_TEST.equals(pkg);}
    static boolean supported(String pkg){return isCobalt(pkg)||YOUTUBE.equals(pkg);}
    private static boolean installed(Context c,String pkg){try{return c.getPackageManager().getApplicationInfo(pkg,0).enabled;}catch(Exception ignored){return false;}}
    static Result read(Context c){return read(c,new android.os.CancellationSignal());}
    static Result read(Context c,android.os.CancellationSignal signal){
        signal.throwIfCanceled();
        Result out=new Result();
        if(!DynamicRows.recommendations(c)){out.status=c.getString(R.string.recommendations_disabled);return out;}
        if(!MediaCapabilities.provider(c)){out.status="Recomendações de TV: serviço ausente neste sistema";return out;}
        Set<String> installed=new HashSet<>();for(String pkg:new String[]{COBALT,COBALT_TEST,YOUTUBE})if(DynamicRows.app(c,pkg)&&installed(c,pkg))installed.add(pkg);
        if(installed.isEmpty()){out.status=c.getString(R.string.recommendations_status,0,0);return out;}
        Map<Long,Metadata> metadata=new HashMap<>();
        try(Cursor rows=c.getContentResolver().query(Uri.parse("content://android.media.tv/channel"),new String[]{"_id","package_name","display_name","type"},null,null,null,signal)){
            int n=0;while(rows!=null&&rows.moveToNext()&&n++<1000){signal.throwIfCanceled();String pkg=TvContentRepository.string(rows,"package_name");if(!installed.contains(pkg))continue;
                String type=TvContentRepository.string(rows,"type");if(!type.isEmpty()&&!"TYPE_PREVIEW".equals(type))continue;
                metadata.put(TvContentRepository.number(rows,"_id",-1),new Metadata(pkg,TvContentRepository.string(rows,"display_name")));
            }
        }catch(Exception ignored){signal.throwIfCanceled();/* Programs can still be read when channel labels are unavailable. */}
        signal.throwIfCanceled();
        Map<String,Channel> groups=new LinkedHashMap<>();Map<TvContentRepository.Item,Long> weights=new IdentityHashMap<>();Set<String> seen=new HashSet<>();
        String[] columns={"package_name","channel_id","title","intent_uri","poster_art_uri","thumbnail_uri","short_description","weight","duration_millis"};
        try(Cursor rows=c.getContentResolver().query(TvContentRepository.PREVIEW,columns,null,null,null,signal)){
            int n=0;while(rows!=null&&rows.moveToNext()&&n++<2000){signal.throwIfCanceled();
                String pkg=TvContentRepository.string(rows,"package_name");if(!installed.contains(pkg))continue;
                String title=TvContentRepository.string(rows,"title"),link=TvContentRepository.string(rows,"intent_uri");if(title.isEmpty()||link.isEmpty())continue;
                long channelId=TvContentRepository.number(rows,"channel_id",-1);Metadata channel=metadata.get(channelId);if(channel!=null&&!pkg.equals(channel.pkg))continue;
                String key=pkg+"|"+channelId;
                if(!seen.add(key+"|"+link))continue;
                Channel group=groups.get(key);
                if(group==null){if(groups.size()>=12)continue;String published=channel==null?"":channel.name;
                    group=new Channel(key,pkg,shelfTitle(c,published));groups.put(key,group);}
                String art=TvContentRepository.string(rows,"poster_art_uri");if(art.isEmpty())art=TvContentRepository.string(rows,"thumbnail_uri");
                TvContentRepository.Item item=new TvContentRepository.Item(pkg,title,TvContentRepository.string(rows,"short_description"),link,art,-1,TvContentRepository.number(rows,"duration_millis",-1),0);
                group.items.add(item);weights.put(item,TvContentRepository.number(rows,"weight",0));
            }
            int total=0;for(Channel group:groups.values()){
                group.items.sort((a,b)->Long.compare(weights.get(b),weights.get(a)));if(group.items.size()>48)group.items.subList(48,group.items.size()).clear();total+=group.items.size();out.channels.add(group);
            }
            out.channels.sort((a,b)->{int app=Integer.compare(isCobalt(a.pkg)?0:1,isCobalt(b.pkg)?0:1);return app!=0?app:a.key.compareTo(b.key);});
            out.status=c.getString(R.string.recommendations_status,out.channels.size(),total);
        }catch(Exception e){signal.throwIfCanceled();out.status=c.getString(R.string.recommendations_unavailable,e.getClass().getSimpleName());}
        return out;
    }
}
