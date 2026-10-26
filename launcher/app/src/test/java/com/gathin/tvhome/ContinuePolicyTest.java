package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class ContinuePolicyTest {
 @Test public void exactlyFiveMinutesRemainingIsNearEnd(){assertTrue(ContinuePolicy.nearEnd(2_100_000,2_400_000));}
 @Test public void moreThanFiveMinutesRemainingStays(){assertFalse(ContinuePolicy.nearEnd(2_099_999,2_400_000));}
 @Test public void missingDurationOrPositionDoesNotInventCompletion(){assertFalse(ContinuePolicy.nearEnd(-1,2_400_000));assertFalse(ContinuePolicy.nearEnd(1000,-1));assertFalse(ContinuePolicy.nearEnd(0,2_400_000));}
 @Test public void shortVideosAreNotHiddenImmediately(){assertFalse(ContinuePolicy.nearEnd(1,180_000));assertFalse(ContinuePolicy.nearEnd(179_999,180_000));assertTrue(ContinuePolicy.nearEnd(180_000,180_000));}
 @Test public void completedAndOverrunPositionsAreHidden(){assertTrue(ContinuePolicy.nearEnd(500_000,500_000));assertTrue(ContinuePolicy.nearEnd(500_001,500_000));}
 @Test public void replayMustBeStrictlyNewerThanRemoval(){ContinuePolicy.Removed m=new ContinuePolicy.Removed(1000,12);assertFalse(ContinuePolicy.newPlayback(m,0));assertFalse(ContinuePolicy.newPlayback(m,999));assertFalse(ContinuePolicy.newPlayback(m,1000));assertTrue(ContinuePolicy.newPlayback(m,1001));}
 @Test public void onlyAdditionalEpisodesRestoreTheCard(){ContinuePolicy.Removed m=new ContinuePolicy.Removed(1000,12);assertFalse(ContinuePolicy.newEpisodes(m,12));assertFalse(ContinuePolicy.newEpisodes(m,11));assertFalse(ContinuePolicy.newEpisodes(m,-1));assertTrue(ContinuePolicy.newEpisodes(m,13));}
 @Test public void availabilityUsesCurrentCountNotNumberOfNewEpisodes(){assertEquals(12,ContinuePolicy.available("UniTV · 12 episódio(s) disponíveis"));assertEquals(12,ContinuePolicy.available("UniTV · 2 novo(s) episódio(s) · 12 disponíveis"));assertEquals(-1,ContinuePolicy.available("UniTV · Série acompanhada"));}
}
