package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class HistoryDismissalsTest {
 private TvContentRepository.Item item(String pkg,String id,String detail,long time,long position,long duration){return new TvContentRepository.Item(pkg,"Title",detail,"intent://resume?id="+id+"&source=history#Intent;scheme=unitvhome;end","",position,duration,time,"",true,-1);}
 private TvContentRepository.Item history(long time){return item(UniTvShelf.PKG,"A","Retomar",time,-1,-1);}
 private TvContentRepository.Item followed(){return item(UniTvShelf.PKG,"A","UniTV · 12 episódio(s) disponíveis",999999,-1,-1);}
 private List<TvContentRepository.Item> list(TvContentRepository.Item... items){return new ArrayList<>(Arrays.asList(items));}
 private HistoryDismissals.State state(){HistoryDismissals.State s=new HistoryDismissals.State("{}");s.markers.put(HistoryDismissals.key(history(1)),new ContinuePolicy.Removed(1000,12));return s;}
 @Test public void removedHistoryDoesNotReturnThroughFollowedOrOldNews(){HistoryDismissals.State s=state();List<TvContentRepository.Item> h=list(history(900)),u=list(item(UniTvShelf.PKG,"A","UniTV · 2 novo(s) episódio(s) · 12 disponíveis",99999,-1,-1)),f=list(followed());s.unitv(h,u,f,false);assertTrue(h.isEmpty());assertTrue(u.isEmpty());assertTrue(f.isEmpty());assertEquals(1,s.markers.size());}
 @Test public void refreshingFollowedTimestampDoesNotRestore(){HistoryDismissals.State s=state();List<TvContentRepository.Item> f=list(followed());s.unitv(list(),list(),f,false);assertTrue(f.isEmpty());assertEquals(1,s.markers.size());}
 @Test public void newPlaybackRestoresTheSameContent(){HistoryDismissals.State s=state();List<TvContentRepository.Item> h=list(history(1001)),f=list(followed());s.unitv(h,list(),f,false);assertEquals(1,h.size());assertEquals(1,f.size());assertTrue(s.markers.isEmpty());assertTrue(s.changed);}
 @Test public void additionalEpisodesRestore(){HistoryDismissals.State s=state();List<TvContentRepository.Item> u=list(item(UniTvShelf.PKG,"A","UniTV · 3 novo(s) episódio(s) · 13 disponíveis",99,-1,-1));s.unitv(list(),u,list(followed()),false);assertEquals(1,u.size());assertTrue(s.markers.isEmpty());}
 @Test public void markersSurviveSerialization(){HistoryDismissals.State s=new HistoryDismissals.State(state().json());assertTrue(s.hidden(history(1)));assertEquals(1000,s.markers.get(HistoryDismissals.key(history(1))).after);assertEquals(12,s.markers.get(HistoryDismissals.key(history(1))).available);}
 @Test public void sameIdInAnotherAppIsNotHidden(){HistoryDismissals.State s=state();List<TvContentRepository.Item> other=list(item("other.app","A","",1,-1,-1));s.generic(other,false);assertEquals(1,other.size());}
 @Test public void unknownUnitvDurationKeepsHistoryEvenWithAutoOn(){List<TvContentRepository.Item> h=list(history(1));new HistoryDismissals.State("{}").unitv(h,list(),list(followed()),true);assertEquals(1,h.size());}
 @Test public void completionHidesContinuationAndItsFollowedDuplicate(){List<TvContentRepository.Item> h=list(item(UniTvShelf.PKG,"A","",1,2_100_000,2_400_000)),f=list(followed());new HistoryDismissals.State("{}").unitv(h,list(),f,true);assertTrue(h.isEmpty());assertTrue(f.isEmpty());}
 @Test public void completedEpisodeCanStillRecommendActualNewEpisodes(){List<TvContentRepository.Item> h=list(item(UniTvShelf.PKG,"A","",1,2_100_000,2_400_000)),u=list(item(UniTvShelf.PKG,"A","UniTV · 1 novo(s) episódio(s) · 13 disponíveis",1,-1,-1)),f=list(followed());new HistoryDismissals.State("{}").unitv(h,u,f,true);assertTrue(h.isEmpty());assertEquals(1,u.size());assertEquals(1,f.size());}
 @Test public void genericAutoCanBeDisabled(){List<TvContentRepository.Item> h=list(item("other.app","A","",1,2_100_000,2_400_000));new HistoryDismissals.State("{}").generic(h,false);assertEquals(1,h.size());new HistoryDismissals.State("{}").generic(h,true);assertTrue(h.isEmpty());}
 @Test public void shelfPropagatesContinuationRoleAndAvailableEpisodeCount(){TvContentRepository.Item h=history(1);List<TvContentRepository.Item> shelf=UniTvShelf.merge(list(h),list(),list(followed()));assertTrue(shelf.get(0).continuation);assertEquals(12,shelf.get(0).availableEpisodes);shelf=UniTvShelf.merge(list(),list(),list(followed()));assertFalse(shelf.get(0).continuation);}
}
