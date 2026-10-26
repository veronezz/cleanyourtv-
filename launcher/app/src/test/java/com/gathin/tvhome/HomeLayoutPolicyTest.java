package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomeLayoutPolicyTest {
 @Test public void unknownAndMissingSpacingUseCompact(){assertEquals("compact",HomeLayoutPolicy.profile(null));assertEquals("compact",HomeLayoutPolicy.profile("unknown"));assertEquals("balanced",HomeLayoutPolicy.profile("balanced"));}
 @Test public void compactUsesLessEmptySpace(){assertEquals(8,HomeLayoutPolicy.gapDp("compact"));assertTrue(HomeLayoutPolicy.gridBottomDp("compact")<HomeLayoutPolicy.gridBottomDp("relaxed"));}
 @Test public void clockPaddingRetainsOneWholeAppLineByDefault(){assertEquals(183,HomeLayoutPolicy.clockGap(183,null));assertEquals(183,HomeLayoutPolicy.clockGap(183,"normal"));assertEquals(0,HomeLayoutPolicy.clockGap(-1,"normal"));assertTrue(HomeLayoutPolicy.clockGap(183,"small")<183);assertTrue(HomeLayoutPolicy.clockGap(183,"large")>183);}
 @Test public void largeZoomedCardsStillHaveBreathingRoom(){for(float d:new float[]{1f,1.5f,2f})for(int height:new int[]{80,150,300,450}){int pad=HomeLayoutPolicy.rowPadding(height,d,"compact");assertTrue(pad+6*d>=height*(AppAdapter.FOCUS_SCALE-1f)/2f+1.9*d);}}
 @Test public void visibleFocusDoesNotMove(){assertEquals(100,HomeLayoutPolicy.target(100,500,10,20,150,500,1000,12));}
 @Test public void lowerShelfMovesOnlyEnoughToFit(){assertEquals(282,HomeLayoutPolicy.target(100,500,10,20,600,750,1000,12));}
 @Test public void upperShelfKeepsHeadingInView(){assertEquals(28,HomeLayoutPolicy.target(100,500,10,20,50,300,1000,12));}
 @Test public void movementClampsAtTheDocumentEdges(){assertEquals(0,HomeLayoutPolicy.target(20,500,10,20,-100,100,1000,12));assertEquals(1000,HomeLayoutPolicy.target(900,500,10,20,1800,2000,1000,12));}
 @Test public void oversizedRowAlignsItsHeading(){assertEquals(578,HomeLayoutPolicy.target(100,500,10,20,600,1200,1000,12));}
 @Test public void durationIsBoundedForShortAndLongMoves(){assertEquals(0,HomeLayoutPolicy.duration(100,100,500));assertTrue(HomeLayoutPolicy.duration(0,100,500)>=180);assertEquals(300,HomeLayoutPolicy.duration(0,5000,500));}
 @Test public void retargetStartsFromCurrentScrollInsteadOfAnOldTarget(){assertEquals(482,HomeLayoutPolicy.target(180,500,10,20,800,950,1200,12));}
 @Test public void fullHeightMusicPanelAlignsItsTopInsteadOfJustRevealingAControl(){assertEquals(12,HomeLayoutPolicy.target(220,500,8,16,20,500,1200,0));}
}
