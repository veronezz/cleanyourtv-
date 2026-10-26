package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class IconPackIndexTest {
 @Test public void projectivyNetflixMappingIsRead(){IconPackIndex i=new IconPackIndex();i.add("ComponentInfo{com.netflix.ninja/com.netflix.ninja.MainActivity}","netflix");assertEquals("netflix",i.find("com.netflix.ninja/.MainActivity"));}
 @Test public void projectivySpotifyMappingIsRead(){IconPackIndex i=new IconPackIndex();i.add("ComponentInfo{com.spotify.tv.android/com.spotify.tv.android.SpotifyTVActivity}","spotify");assertEquals("spotify",i.find("com.spotify.tv.android/com.spotify.tv.android.SpotifyTVActivity"));}
 @Test public void normalizesRelativeComponentsWithoutChangingCase(){assertEquals("com.example/com.example.Main",IconPackIndex.component(" ComponentInfo{com.example/.Main} "));assertNotEquals(IconPackIndex.component("com.example/.Main"),IconPackIndex.component("com.example/.main"));}
 @Test public void packageFallbackHandlesDifferentMobileActivity(){IconPackIndex i=new IconPackIndex();i.add("com.example/.TV","video");assertEquals("video",i.find("com.example/.Mobile"));}
 @Test public void ambiguousPackagesNeverReceiveAnArbitraryIcon(){IconPackIndex i=new IconPackIndex();i.add("com.vendor/.Settings","settings");i.add("com.vendor/.Picture","picture");assertNull(i.find("com.vendor/.Unknown"));assertEquals("picture",i.find("com.vendor/.Picture"));assertEquals("settings",i.find("com.vendor/.Settings"));}
 @Test public void identicalImagesRemainSafeForPackageFallback(){IconPackIndex i=new IconPackIndex();i.add("com.example/.A","video");i.add("com.example/.B","video");assertEquals("video",i.find("com.example/.C"));}
 @Test public void ambiguityStaysAmbiguousAfterMoreEntries(){IconPackIndex i=new IconPackIndex();i.add("com.vendor/.A","a");i.add("com.vendor/.B","b");i.add("com.vendor/.C","a");assertNull(i.find("com.vendor/.D"));}
 @Test public void unmappedAppsHaveNoOverride(){assertNull(new IconPackIndex().find("com.example/.Main"));}
 @Test public void drawableCataloguesDoNotCreateFakeAppMappings(){IconPackIndex i=new IconPackIndex();i.add(null,"netflix");i.add(null,"netflix");i.add(null,"spotify");assertEquals(0,i.size());assertEquals(2,i.images().size());}
 @Test public void resourcePrefixesNormalize(){assertEquals("logo",IconPackIndex.drawable("@drawable/logo"));assertEquals("logo",IconPackIndex.drawable("drawable/logo"));}
 @Test public void rejectsPathsUrlsAndIncompleteComponents(){for(String c:new String[]{"",null,"ComponentInfo{broken}","/com.android.Foo","com.example/../../file","https://domain/x"})assertEquals("",IconPackIndex.component(c));for(String name:new String[]{"../logo","https://domain","@12345","",null})assertEquals("",IconPackIndex.drawable(name));}
 @Test public void activityAliasesAndInnerClassesRemainValid(){assertEquals("com.example/com.example.Main$Inner",IconPackIndex.component("com.example/.Main$Inner"));}
 @Test public void duplicateMappingsDoNotDestabilizeFallback(){IconPackIndex i=new IconPackIndex();i.add("com.example/.Main","a");i.add("com.example/.Main","b");assertEquals("a",i.find("com.example/.Main"));assertEquals("a",i.find("com.example/.Other"));}
 @Test public void cataloguesAreBounded(){IconPackIndex i=new IconPackIndex();for(int n=0;n<IconPackIndex.MAX_IMAGES+10;n++)i.add(null,"logo_"+n);assertEquals(IconPackIndex.MAX_IMAGES,i.images().size());}
 @Test public void returnedImageListCannotMutateTheIndex(){IconPackIndex i=new IconPackIndex();i.add(null,"logo");i.images().clear();assertEquals(1,i.images().size());}
}
