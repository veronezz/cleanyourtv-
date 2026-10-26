package com.gathin.tvhome;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

public class PerformanceDataTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void thumbnailExpiresWithoutExtendingItsLifetimeOnRead()throws Exception{ThumbnailDiskCache c=new ThumbnailDiskCache(temp.newFolder(),100,1000);c.write("https://test/a",new byte[]{1,2},1000);assertArrayEquals(new byte[]{1,2},c.read("https://test/a",1500));assertNull(c.read("https://test/a",2000));}
    @Test public void thumbnailBudgetEvictsOlderEntries()throws Exception{ThumbnailDiskCache c=new ThumbnailDiskCache(temp.newFolder(),6,10000);c.write("a",new byte[4],1000);c.write("b",new byte[]{1,2,3,4},2000);assertNull(c.read("a",2500));assertArrayEquals(new byte[]{1,2,3,4},c.read("b",2500));}
    @Test public void oversizedEntriesAndFutureClockAreNotUsed()throws Exception{File folder=temp.newFolder();ThumbnailDiskCache c=new ThumbnailDiskCache(folder,8,1000);c.write("a",new byte[9],1000);assertEquals(0,folder.list().length);c.write("b",new byte[]{2},2000);assertNull(c.read("b",1000));}
    @Test public void cacheNamesCannotEscapeDirectoryAndEntriesStayDistinct()throws Exception{File folder=temp.newFolder();ThumbnailDiskCache c=new ThumbnailDiskCache(folder,100,1000);c.write("../../outside",new byte[]{1},1000);c.write("https://a/?secret=value",new byte[]{2},1000);assertArrayEquals(new byte[]{1},c.read("../../outside",1100));assertArrayEquals(new byte[]{2},c.read("https://a/?secret=value",1100));for(File f:folder.listFiles())assertTrue(f.getName().matches("[a-f0-9]{64}\\.thumb"));}
    @Test public void integralLuminanceMatchesDirectRectangleAverage(){Random random=new Random(34);int w=16,h=9;int[] pixels=new int[w*h];for(int n=0;n<pixels.length;n++)pixels[n]=random.nextInt();LuminanceGrid g=new LuminanceGrid(pixels,w,h);for(int n=0;n<100;n++){int l=random.nextInt(w),t=random.nextInt(h),r=l+1+random.nextInt(w-l),b=t+1+random.nextInt(h-t);double sum=0;for(int y=t;y<b;y++)for(int x=l;x<r;x++){int p=pixels[y*w+x];sum+=.2126*linear(((p>>16)&255)/255d)+.7152*linear(((p>>8)&255)/255d)+.0722*linear((p&255)/255d);}assertEquals(sum/((r-l)*(b-t)),g.average(l,t,r,b),1e-12);}}
    private static double linear(double v){return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);}
    @Test public void contrastUniformColorsAndOutOfBoundsAreStable(){LuminanceGrid g=new LuminanceGrid(new int[]{-1,-1,-1,-1},2,2);assertEquals(1,g.average(-5,-5,10,10),1e-12);assertEquals(0,g.average(2,2,2,2),0);assertEquals(0,new LuminanceGrid(new int[]{0xff000000},1,1).average(0,0,1,1),0);}
    @Test public void preferenceUtilitiesRemainAfterRedditRemoval(){assertEquals(1,PreferenceValues.minutes(0));assertEquals(7,PreferenceValues.minutes(7));assertEquals(1440,PreferenceValues.minutes(Integer.MAX_VALUE));assertEquals("https://github.com/user/my-fork",PreferenceValues.github("https://github.com/user/my-fork/"));for(String value:new String[]{"https://github.com.evil.test/user/fork","https://user@github.com/a/b","http://github.com/a/b"}){try{PreferenceValues.github(value);fail(value);}catch(IllegalArgumentException expected){}}}
}
