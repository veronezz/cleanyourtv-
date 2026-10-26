package com.gathin.tvhome;

import java.net.URI;
import java.net.URLDecoder;
import java.util.*;

/** One app-owned shelf: history first, then followed series. Identity is independent of entry route. */
final class UniTvShelf {
    static final String PKG="com.global.unitviptv";
    static final int LIMIT=96;
    static String contentKey(TvContentRepository.Item item) { return item.stableKey; }
    static String contentKey(String link) {
        String id=parameter(link,"id");
        return id.isEmpty()?"link:"+link:"id:"+id;
    }
    private static String parameter(String link,String name) {
        try {
            int suffix=link.indexOf("#Intent;");String raw=suffix>=0?link.substring(0,suffix):link;
            String query=new URI(raw).getRawQuery();if(query==null)return "";
            for(String p:query.split("&")) {
                int eq=p.indexOf('=');if(eq<0)continue;
                if(name.equals(URLDecoder.decode(p.substring(0,eq),"UTF-8")))return URLDecoder.decode(p.substring(eq+1),"UTF-8");
            }
        }catch(Exception ignored){}
        return "";
    }
    static List<TvContentRepository.Item> merge(List<TvContentRepository.Item> history,List<TvContentRepository.Item> updates,List<TvContentRepository.Item> followed) {
        List<TvContentRepository.Item> orderedHistory=new ArrayList<>(history);
        orderedHistory.sort((a,b)->Long.compare(b.engagement,a.engagement));
        Map<String,Integer> counts=new HashMap<>();
        for(TvContentRepository.Item item:followed)counts.put(contentKey(item),ContinuePolicy.available(item.detail));
        for(TvContentRepository.Item item:updates)counts.put(contentKey(item),Math.max(counts.getOrDefault(contentKey(item),-1),ContinuePolicy.available(item.detail)));
        Set<String> news=new HashSet<>();for(TvContentRepository.Item item:updates)if(PKG.equals(item.pkg))news.add(contentKey(item));
        Set<String> seen=new HashSet<>();List<TvContentRepository.Item> result=new ArrayList<>();
        for(TvContentRepository.Item item:orderedHistory)add(result,seen,item,news.contains(contentKey(item))?"Continuar · Novos episódios":"Continuar",true,counts.getOrDefault(contentKey(item),-1));
        for(TvContentRepository.Item item:updates)add(result,seen,item,"Novos episódios",false,counts.getOrDefault(contentKey(item),-1));
        for(TvContentRepository.Item item:followed)add(result,seen,item,"Acompanhando",false,counts.getOrDefault(contentKey(item),-1));
        return result;
    }
    private static void add(List<TvContentRepository.Item> out,Set<String> seen,TvContentRepository.Item item,String badge,boolean continuation,int availableEpisodes) {
        if(!PKG.equals(item.pkg)||out.size()>=LIMIT||item.intent.isEmpty()||!seen.add(contentKey(item)))return;
        out.add(new TvContentRepository.Item(item.pkg,item.title,item.detail,item.intent,item.art,item.position,item.duration,item.engagement,badge,continuation,availableEpisodes));
    }
}
