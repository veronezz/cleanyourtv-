package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class MediaMathTest {
    @Test public void playingPositionAccountsForElapsedTime() {assertEquals(15_000,MediaMath.position(10_000,1000,1,true,6000,60_000));}
    @Test public void pauseDoesNotAdvance() {assertEquals(10_000,MediaMath.position(10_000,1000,1,false,6000,60_000));}
    @Test public void playbackSpeedIsRespected() {assertEquals(20_000,MediaMath.position(10_000,1000,2,true,6000,60_000));}
    @Test public void durationAndNegativeValuesAreClamped() {assertEquals(60_000,MediaMath.position(59_000,1000,1,true,6000,60_000));assertEquals(-1,MediaMath.position(-1,1000,1,true,6000,60_000));}
    @Test public void unknownDurationAndFutureClockDoNotInventAValue() {assertEquals(10_000,MediaMath.position(10_000,6000,1,true,1000,0));assertEquals(0,MediaMath.progress(10_000,0));assertEquals("--:--",MediaMath.time(-1));}
    @Test public void progressHandlesLongMoviesWithoutIntegerOverflow() {assertEquals(500,MediaMath.progress(7_200_000,14_400_000));assertEquals(1000,MediaMath.progress(20_000,10_000));}
    @Test public void timeSupportsMoviesLongerThanAnHour() {assertEquals("2:03:04",MediaMath.time(7_384_000));assertEquals("3:04",MediaMath.time(184_000));}
    @Test public void nonFiniteSpeedDoesNotCorruptPosition() {assertEquals(10_000,MediaMath.position(10_000,1000,Float.NaN,true,6000,60_000));}
}
