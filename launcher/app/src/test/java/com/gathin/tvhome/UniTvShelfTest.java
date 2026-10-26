package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class UniTvShelfTest {
    private TvContentRepository.Item item(String id,String source,long time) {
        return new TvContentRepository.Item(UniTvShelf.PKG,"Title "+id,"detail",link(id,source),"art "+id,-1,-1,time);
    }
    private String link(String id,String source) {
        return "intent://resume?id="+id+"&type=0&source="+source+"&ts=false#Intent;scheme=unitvhome;component=com.global.unitviptv/com.global.unitvhome.HomeResumeActivity;end";
    }
    private List<TvContentRepository.Item> list(TvContentRepository.Item... items){return Arrays.asList(items);}
    @Test public void historyPrecedesSeriesEvenWhenFollowedWasUpdatedLater(){
        TvContentRepository.Item old=item("film","history",1),series=item("series","subscribe",999999);
        List<TvContentRepository.Item> out=UniTvShelf.merge(list(old),list(),list(series));
        assertEquals(old.intent,out.get(0).intent);assertEquals(series.intent,out.get(1).intent);
        assertEquals("Continuar",out.get(0).badge);assertEquals("Acompanhando",out.get(1).badge);
    }
    @Test public void historyIsNewestFirstAndDoesNotReorderTheInput(){
        List<TvContentRepository.Item> history=list(item("old","history",1),item("new","history",9));
        List<TvContentRepository.Item> out=UniTvShelf.merge(history,list(),list());
        assertEquals("id:new",UniTvShelf.contentKey(out.get(0)));assertEquals("id:old",UniTvShelf.contentKey(history.get(0)));
    }
    @Test public void sameSeriesInThreeFeedsKeepsTheHistoryDestination(){
        TvContentRepository.Item watch=item("A","history",1),series=item("A","subscribe",100);
        List<TvContentRepository.Item> out=UniTvShelf.merge(list(watch),list(series),list(series));
        assertEquals(1,out.size());assertEquals(watch.intent,out.get(0).intent);assertEquals(watch.art,out.get(0).art);
        assertEquals("Continuar · Novos episódios",out.get(0).badge);
    }
    @Test public void followedAndHistoryKeepTheSameIdentityAfterGoingToDetailsAndBack(){
        TvContentRepository.Item watch=item("A","history",9),series=item("A","subscribe",1);
        List<TvContentRepository.Item> before=UniTvShelf.merge(list(watch),list(),list(series));
        List<TvContentRepository.Item> after=UniTvShelf.merge(list(),list(),list(series));
        assertEquals(1,before.size());assertEquals(1,after.size());
        assertEquals(UniTvShelf.contentKey(before.get(0)),UniTvShelf.contentKey(after.get(0)));
        // A read without history retains the actual followed card, without inventing playback.
        assertEquals("Acompanhando",after.get(0).badge);
    }
    @Test public void newsComesAfterHistoryAndBeforeOtherSeries(){
        List<TvContentRepository.Item> out=UniTvShelf.merge(list(item("watch","history",1)),list(item("new","subscribe",1)),list(item("other","subscribe",100),item("new","subscribe",1)));
        assertEquals(3,out.size());assertEquals("id:watch",UniTvShelf.contentKey(out.get(0)));
        assertEquals("id:new",UniTvShelf.contentKey(out.get(1)));assertEquals("Novos episódios",out.get(1).badge);
        assertEquals("id:other",UniTvShelf.contentKey(out.get(2)));
    }
    @Test public void otherApplicationsCannotEvictUniTv(){
        List<TvContentRepository.Item> unrelated=new ArrayList<>();
        for(int i=0;i<100;i++)unrelated.add(new TvContentRepository.Item("other.app","Other","","other:"+i,"",-1,-1,Long.MAX_VALUE));
        unrelated.add(item("watch","history",1));
        List<TvContentRepository.Item> out=UniTvShelf.merge(unrelated,list(),list(item("series","subscribe",1)));
        assertEquals(2,out.size());assertEquals("id:watch",UniTvShelf.contentKey(out.get(0)));
    }
    @Test public void ownLimitAllowsHistoryPlusSeriesBeyondTheOldGlobal48(){
        List<TvContentRepository.Item> history=new ArrayList<>(),followed=new ArrayList<>();
        for(int i=0;i<48;i++)history.add(item("watch"+i,"history",100-i));
        for(int i=0;i<80;i++)followed.add(item("series"+i,"subscribe",10000));
        List<TvContentRepository.Item> out=UniTvShelf.merge(history,list(),followed);
        assertEquals(96,out.size());assertEquals("id:watch47",UniTvShelf.contentKey(out.get(47)));
        assertEquals("id:series0",UniTvShelf.contentKey(out.get(48)));assertEquals("id:series47",UniTvShelf.contentKey(out.get(95)));
    }
    @Test public void encodedIdentifiersAreDeduplicatedByContentNotByRoute(){
        TvContentRepository.Item a=item("A%2FB%2BC%3D","history",1);
        TvContentRepository.Item b=new TvContentRepository.Item(UniTvShelf.PKG,"Same","","intent://resume?source=subscribe&id=A%2fB%2bC%3d&type=3#Intent;scheme=unitvhome;end","",-1,-1,9);
        assertEquals("id:A/B+C=",UniTvShelf.contentKey(a));
        assertEquals(1,UniTvShelf.merge(list(a),list(),list(b)).size());
    }
    @Test public void matchingTitlesDoNotCollapseDifferentCatalogIds(){
        TvContentRepository.Item a=item("A","history",1),b=new TvContentRepository.Item(UniTvShelf.PKG,a.title,"",link("B","subscribe"),"",-1,-1,2);
        assertEquals(2,UniTvShelf.merge(list(a),list(),list(b)).size());
    }
    @Test public void emptyHistoryDoesNotCreatePlaybackFromFollowedSeries(){
        List<TvContentRepository.Item> out=UniTvShelf.merge(list(),list(),list(item("A","subscribe",1)));
        assertEquals("Acompanhando",out.get(0).badge);assertEquals(-1,out.get(0).position);
    }
    @Test public void malformedLinksFallBackToTheirExactIdentity(){
        assertEquals("link:not a uri",UniTvShelf.contentKey("not a uri"));
        assertNotEquals(UniTvShelf.contentKey("not a uri"),UniTvShelf.contentKey("another invalid uri"));
    }
    @Test public void ordinaryMediaItemsKeepNoExtraBadge(){
        assertEquals("",item("A","history",1).badge);
    }
}
