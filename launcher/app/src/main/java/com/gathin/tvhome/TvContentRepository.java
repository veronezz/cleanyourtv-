package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Read-only public TV Provider data; no selection clause, private database or invented playback URI. */
final class TvContentRepository {
    static final Uri WATCH=Uri.parse("content://android.media.tv/watch_next_program");
    static final Uri PREVIEW=Uri.parse("content://android.media.tv/preview_program");
    static final String GLOBO="com.globo.globotv";
    static final class Item {
        final String pkg,title,detail,intent,art,badge,stableKey;
        final long position,duration,engagement;
        final boolean continuation;final int availableEpisodes;
        Item(String pkg,String title,String detail,String intent,String art,long position,long duration,long engagement){
            this(pkg,title,detail,intent,art,position,duration,engagement,"");
        }
        Item(String pkg,String title,String detail,String intent,String art,long position,long duration,long engagement,String badge){
            this(pkg,title,detail,intent,art,position,duration,engagement,badge,false,-1);
        }
        Item(String pkg,String title,String detail,String intent,String art,long position,long duration,long engagement,String badge,boolean continuation,int availableEpisodes){
            this.continuation=continuation;this.availableEpisodes=availableEpisodes;
            this.pkg=pkg;this.title=title;this.detail=detail;this.intent=intent;this.art=art;this.badge=badge;
            this.stableKey=UniTvShelf.PKG.equals(pkg)?UniTvShelf.contentKey(intent):intent.isEmpty()?title:intent;
            this.position=position;this.duration=duration;this.engagement=engagement;
        }
    }
    static final class Result {
        final List<Item> watch=new ArrayList<>(),live=new ArrayList<>();
        String watchStatus="Watch Next: OFF",liveStatus="Globoplay: OFF";
        final List<RecommendationChannels.Channel> recommendations=new ArrayList<>();
    }
    static String string(Cursor c,String key){int col=c.getColumnIndex(key);return col>=0&&!c.isNull(col)?c.getString(col):"";}
    static long number(Cursor c,String key,long fallback){int col=c.getColumnIndex(key);return col>=0&&!c.isNull(col)?c.getLong(col):fallback;}
    private static String art(Cursor c){String s=string(c,"poster_art_uri");return s.isEmpty()?string(c,"thumbnail_uri"):s;}
    static Result read(Context context){return read(context,new android.os.CancellationSignal());}
    static Result read(Context context,android.os.CancellationSignal signal){
        signal.throwIfCanceled();
        Result result=new Result();Set<String> seen=new HashSet<>();
        String[] watchColumns={"package_name","title","intent_uri","poster_art_uri","thumbnail_uri","last_playback_position_millis","duration_millis","watch_next_type","last_engagement_time_utc_millis"};
        if(MediaCapabilities.provider(context)&&DynamicRows.enabled(context,DynamicRows.WATCH))try(Cursor c=context.getContentResolver().query(WATCH,watchColumns,null,null,null,signal)){
            int n=0;
            while(c!=null&&c.moveToNext()&&n++<400){
                signal.throwIfCanceled();
                String pkg=string(c,"package_name"),title=string(c,"title"),link=string(c,"intent_uri");
                long type=number(c,"watch_next_type",0),pos=number(c,"last_playback_position_millis",-1),duration=number(c,"duration_millis",-1);
                if(UniTvShelf.PKG.equals(pkg))continue;
                if(pkg.isEmpty()||title.isEmpty()||link.isEmpty()||type>1||!seen.add(pkg+"|"+link))continue;
                if(type==0&&duration>0&&pos>=duration)continue;
                String detail=type==1?context.getString(R.string.ui_proximo_episodio_0ceb):pos>=0?context.getString(R.string.resume_at,MediaMath.time(pos)):context.getString(R.string.ui_posicao_nao_informada_pelo_app_3c86);
                if(type==0&&duration>0)detail+=" / "+MediaMath.time(duration);
                result.watch.add(new Item(pkg,title,detail,link,art(c),pos,duration,number(c,"last_engagement_time_utc_millis",0),"",true,-1));
            }
            result.watchStatus=result.watch.size()+" itens acessíveis no Watch Next";
        }catch(Exception e){signal.throwIfCanceled();result.watchStatus="Watch Next indisponível: "+e.getClass().getSimpleName();}
        if(DynamicRows.enabled(context,DynamicRows.WATCH)){
            if(!MediaCapabilities.provider(context))result.watchStatus="Watch Next: serviço de TV ausente";
            RecentMedia.sample(context);Set<String> published=new HashSet<>();for(Item item:result.watch)published.add(item.pkg);
            int recent=0;for(Item item:RecentMedia.read(context))if(!published.contains(item.pkg)){result.watch.add(item);recent++;}
            HistoryDismissals.generic(context,result.watch);Collections.sort(result.watch,(a,b)->Long.compare(b.engagement,a.engagement));
            if(result.watch.size()>48)result.watch.subList(48,result.watch.size()).clear();
            if(RecentMedia.enabled(context))result.watchStatus+="; "+recent+" sessões recentes complementares";
        }
        signal.throwIfCanceled();
        String[] liveColumns={"package_name","title","intent_uri","poster_art_uri","thumbnail_uri","live"};
        if(MediaCapabilities.provider(context)&&DynamicRows.enabled(context,DynamicRows.LIVE))try(Cursor c=context.getContentResolver().query(PREVIEW,liveColumns,null,null,null,signal)){
            int n=0;
            while(c!=null&&c.moveToNext()&&n++<600){
                signal.throwIfCanceled();
                if(!GLOBO.equals(string(c,"package_name")))continue;
                String title=string(c,"title"),link=string(c,"intent_uri");if(title.isEmpty()||link.isEmpty())continue;
                if(number(c,"live",0)!=1&&!LiveTvOrder.isMainGlobo(title))continue;
                result.live.add(new Item(GLOBO,title,context.getString(R.string.ui_ao_vivo_globoplay_7da9),link,art(c),-1,-1,0));
            }
            result.liveStatus=result.live.size()+" itens ao vivo publicados pelo Globoplay";
        }catch(Exception e){signal.throwIfCanceled();result.liveStatus="Globoplay ao vivo indisponível: "+e.getClass().getSimpleName();}
        if(MediaCapabilities.provider(context)&&DynamicRows.enabled(context,DynamicRows.LIVE)&&result.live.isEmpty()){
            String[] columns={"package_name","display_name","app_link_intent_uri","app_link_poster_art_uri"};
            try(Cursor c=context.getContentResolver().query(Uri.parse("content://android.media.tv/channel"),columns,null,null,null,signal)){
                int n=0;
                while(c!=null&&c.moveToNext()&&n++<400){
                signal.throwIfCanceled();
                    if(!GLOBO.equals(string(c,"package_name")))continue;
                    String title=string(c,"display_name"),link=string(c,"app_link_intent_uri");
                    String lower=title.toLowerCase(java.util.Locale.ROOT);
                    if(!link.isEmpty()&&(lower.contains("ao vivo")||lower.contains("agora na tv")||LiveTvOrder.isMainGlobo(title)))
                        result.live.add(new Item(GLOBO,title,context.getString(R.string.ui_ao_vivo_globoplay_7da9),link,string(c,"app_link_poster_art_uri"),-1,-1,0));
                }
                if(!result.live.isEmpty()||!result.liveStatus.contains("indisponível"))result.liveStatus=result.live.size()+" itens/canais ao vivo publicados pelo Globoplay";
            }catch(Exception ignored){signal.throwIfCanceled();}
        }
        // Rank before limiting: the free/main Globo entry may occur after the first 24 rows.
        result.live.sort((a,b)->Integer.compare(LiveTvOrder.priority(a.title),LiveTvOrder.priority(b.title)));
        if(result.live.size()>24)result.live.subList(24,result.live.size()).clear();
        String unitv=UniTvRepository.read(context,signal,result);
        RecommendationChannels.Result recommendations=RecommendationChannels.read(context,signal);result.recommendations.addAll(recommendations.channels);
        signal.throwIfCanceled();
        LocalPreferences.get(context).edit().putString("tv_content_diagnostic",result.watchStatus+"\n"+result.liveStatus+"\n"+recommendations.status+"\n"+unitv).apply();
        return result;
    }
    static Intent intent(Item item) throws Exception {
        Intent parsed=Intent.parseUri(item.intent,Intent.URI_INTENT_SCHEME);
        Intent safe=new Intent(Intent.ACTION_VIEW,parsed.getData()).setPackage(item.pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(parsed.getComponent()!=null&&item.pkg.equals(parsed.getComponent().getPackageName()))safe.setComponent(parsed.getComponent());
        String type=parsed.getType();if(type!=null)safe.setDataAndType(parsed.getData(),type);
        android.os.Bundle extras=parsed.getExtras();
        if(extras!=null)for(String key:extras.keySet()){
            Object value=extras.get(key);
            if(value instanceof String)safe.putExtra(key,(String)value);
            else if(value instanceof Long)safe.putExtra(key,((Long)value).longValue());
            else if(value instanceof Integer)safe.putExtra(key,((Integer)value).intValue());
            else if(value instanceof Boolean)safe.putExtra(key,((Boolean)value).booleanValue());
        }
        if(safe.getData()==null&&safe.getComponent()==null)throw new IllegalArgumentException("Link sem destino");
        return safe;
    }
}
