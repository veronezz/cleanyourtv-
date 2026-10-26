package com.gathin.tvhome;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import java.util.*;
/** Original app metadata; failure in one feed does not suppress the other feeds. */
final class UniTvRepository {
    static final String PKG="com.global.unitviptv";
    static final Uri ROOT=Uri.parse("content://com.global.unitviptv.homebridge");
    static String read(Context context,CancellationSignal signal,TvContentRepository.Result result) {
        if(!DynamicRows.enabled(context,DynamicRows.UNITV))return "UniTV: OFF — ative a linha exclusiva do UniTV";
        List<TvContentRepository.Item> watch=new ArrayList<>(),updates=new ArrayList<>(),followed=new ArrayList<>();
        StringBuilder errors=new StringBuilder();Bundle history=new Bundle();
        try{history=readFeed(context,"continue",signal,watch);}catch(Exception e){signal.throwIfCanceled();errors.append("histórico: ").append(e.getClass().getSimpleName()).append("; ");}
        try{readFeed(context,"updates",signal,updates);}catch(Exception e){signal.throwIfCanceled();errors.append("novidades: ").append(e.getClass().getSimpleName()).append("; ");}
        try{readFeed(context,"followed",signal,followed);}catch(Exception e){signal.throwIfCanceled();errors.append("acompanhadas: ").append(e.getClass().getSimpleName()).append("; ");}
        HistoryDismissals.unitv(context,watch,updates,followed);
        List<TvContentRepository.Item> shelf=UniTvShelf.merge(watch,updates,followed);
        addShelf(result,"unitv-home","UniTV · Continuar assistindo e séries",shelf);
        String status="";
        try(Cursor c=context.getContentResolver().query(Uri.withAppendedPath(ROOT,"status"),null,null,null,null,signal)) {
            if(c!=null&&c.moveToFirst())status=TvContentRepository.string(c,"detail");
        }catch(Exception e){signal.throwIfCanceled();errors.append("estado: ").append(e.getClass().getSimpleName());}
        return "UniTV: linha exclusiva; "+watch.size()+" no histórico, "+updates.size()+" com novidades, "+followed.size()+" acompanhadas; "+shelf.size()+" cartões únicos · "+status+(errors.length()==0?"":"\n"+errors);
    }
    private static void addShelf(TvContentRepository.Result out,String key,String title,List<TvContentRepository.Item> items) {
        if(items.isEmpty())return;
        RecommendationChannels.Channel shelf=new RecommendationChannels.Channel(key,PKG,title);shelf.items.addAll(items);out.recommendations.add(shelf);
    }
    private static Bundle readFeed(Context context,String path,CancellationSignal signal,List<TvContentRepository.Item> items) {
        try(Cursor c=context.getContentResolver().query(Uri.withAppendedPath(ROOT,path),null,null,null,null,signal)) {
            if(c==null)throw new IllegalStateException("Provedor não encontrado");
            while(c.moveToNext()&&items.size()<48){signal.throwIfCanceled();TvContentRepository.Item item=item(c);if(item!=null)items.add(item);}
            return c.getExtras();
        }
    }
    private static TvContentRepository.Item item(Cursor c) {
        String title=TvContentRepository.string(c,"title"),link=TvContentRepository.string(c,"intent_uri");
        if(title.isEmpty()||link.isEmpty())return null;
        return new TvContentRepository.Item(PKG,title,TvContentRepository.string(c,"detail"),link,TvContentRepository.string(c,"poster_art_uri"),TvContentRepository.number(c,"position",-1),TvContentRepository.number(c,"duration",-1),TvContentRepository.number(c,"engagement",0));
    }
}
