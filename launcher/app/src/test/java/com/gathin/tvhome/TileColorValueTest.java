package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class TileColorValueTest {
 @Test public void transparentAndDefaultHaveDistinctPersistence(){assertNull(TileColorValue.parse("default"));assertNull(TileColorValue.parse(null));assertEquals(Integer.valueOf(0),TileColorValue.parse("transparent"));}
 @Test public void rgbIsAlwaysOpaque(){assertEquals(Integer.valueOf(0xff203040),TileColorValue.parse("#203040"));assertEquals(Integer.valueOf(0xffabcdef),TileColorValue.parse("abcdef"));}
 @Test public void blackAndWhiteArePreserved(){assertEquals(Integer.valueOf(0xff000000),TileColorValue.parse("#000000"));assertEquals(Integer.valueOf(0xffffffff),TileColorValue.parse("#FFFFFF"));}
 @Test public void invalidSavedValuesFallBackToDefault(){for(String s:new String[]{"","#12","#GGHHII","#00112233","#1234567","#-12345"})assertNull(TileColorValue.parse(s));}
 @Test public void lightBackgroundsReceiveDarkText(){assertTrue(TileColorValue.light(0xffeeeeee));assertFalse(TileColorValue.light(0xff203040));assertFalse(TileColorValue.light(0));assertFalse(TileColorValue.light(null));}
}
