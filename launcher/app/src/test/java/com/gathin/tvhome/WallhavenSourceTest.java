package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class WallhavenSourceTest {
    @Test public void searchUsesPublicSfwAndTelevisionFilters(){WallhavenSource s=new WallhavenSource("nature","fhd",true);assertTrue(s.endpoint.startsWith("https://wallhaven.cc/api/v1/search?"));assertTrue(s.endpoint.contains("purity=100"));assertTrue(s.endpoint.contains("atleast=1920x1080"));assertTrue(s.endpoint.contains("ratios=16x9"));assertFalse(s.endpoint.contains("apikey"));}
    @Test public void webSearchKeepsOnlySafeParameters(){WallhavenSource s=new WallhavenSource("https://wallhaven.cc/search?q=city%20lights&purity=001&sorting=toplist&apikey=secret","4k",false);assertTrue(s.endpoint.contains("q=city+lights"));assertTrue(s.endpoint.contains("atleast=3840x2160"));assertTrue(s.endpoint.contains("purity=100"));assertFalse(s.endpoint.contains("secret"));assertFalse(s.endpoint.contains("ratios="));}
    @Test public void tagLinksBecomeTagSearches(){assertTrue(new WallhavenSource("https://wallhaven.cc/tag/123","fhd",true).endpoint.contains("q=id%3A123"));}
    @Test public void collectionLinksUseThePublicCollectionEndpoint(){assertTrue(new WallhavenSource("https://wallhaven.cc/user/test-user/favorites/42","fhd",true).endpoint.startsWith("https://wallhaven.cc/api/v1/collections/test-user/42?"));}
    @Test public void rejectsArbitraryHostsSchemesAndEmbeddedCredentials(){for(String v:new String[]{"https://evil.example/search?q=hi","https://wallhaven.cc.evil.example/search","https://user@wallhaven.cc/search","http://wallhaven.cc/search","https://wallhaven.cc:444/search","https://wallhaven.cc/w/abc123","file:///tmp/abc"}){try{new WallhavenSource(v,"fhd",true);fail(v);}catch(IllegalArgumentException expected){}}}
    @Test public void filtersResolutionAndOrientation(){WallhavenSource s=new WallhavenSource("nature","fhd",false);assertFalse(s.accepts(1280,720));assertFalse(s.accepts(2160,3840));assertTrue(s.accepts(3840,2160));assertTrue(s.accepts(2560,1080));}
    @Test public void optionalWidescreenFilterRejectsUltrawide(){assertFalse(new WallhavenSource("nature","fhd",true).accepts(2560,1080));}
    @Test public void validatesImageHostsAndPaths(){assertTrue(WallhavenSource.imageUrl("https://w.wallhaven.cc/full/ab/wallhaven-abc123.jpg"));assertFalse(WallhavenSource.imageUrl("https://evil.example/full/ab/wallhaven-abc123.jpg"));assertFalse(WallhavenSource.imageUrl("https://w.wallhaven.cc@evil.example/a.jpg"));assertFalse(WallhavenSource.imageUrl("https://w.wallhaven.cc/full/ab/wallhaven-abc123.jpg?redirect=evil"));}
    @Test public void parserRejectsUnsafeLowResolutionAndNonSfwImages() throws Exception {
        String row="{\"id\":\"abc123\",\"url\":\"https://wallhaven.cc/w/abc123\",\"path\":\"https://w.wallhaven.cc/full/ab/wallhaven-abc123.jpg\",\"dimension_x\":3840,\"dimension_y\":2160,\"purity\":\"sfw\"}";
        WallhavenSource source=new WallhavenSource("nature","fhd",true);assertEquals(1,WallhavenImages.parse("{\"data\":["+row+","+row+"]}",source).size());
        assertTrue(WallhavenImages.parse("{\"data\":["+row.replace("\"sfw\"","\"nsfw\"")+","+row.replace("3840","1280").replace("2160","720")+"]}",source).isEmpty());
    }
    @Test public void metadataRoundTripPreservesOnlyValidatedImageData() throws Exception {java.util.List<WallhavenImages.Image> list=java.util.Collections.singletonList(new WallhavenImages.Image("https://w.wallhaven.cc/full/ab/wallhaven-abc123.jpg","https://wallhaven.cc/w/abc123","abc123",3840,2160));assertEquals("abc123",WallhavenImages.parse(WallhavenImages.encode(list),new WallhavenSource("nature","fhd",true)).get(0).id);}
}
