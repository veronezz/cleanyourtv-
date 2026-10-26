package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class RowOptionsTest {
 @Test public void selectorKeepsEveryExistingRowKeyOnce(){assertEquals(7,RowOptions.KEYS.length);assertEquals(7,new HashSet<>(Arrays.asList(RowOptions.KEYS)).size());assertEquals(DynamicRows.ALL,RowOptions.KEYS[0]);assertTrue(Arrays.asList(RowOptions.KEYS).contains(DynamicRows.UNITV));}
 @Test public void defaultMasterIsOffWithoutErasingIndividualDefaults(){assertFalse(RowOptions.defaultValue(0));for(int i=1;i<7;i++)assertTrue(RowOptions.defaultValue(i));}
 @Test public void masterDoesNotCountAsAContentRow(){assertEquals(2,RowOptions.count(new boolean[]{true,true,true,false,false,false,false}));assertEquals(2,RowOptions.count(new boolean[]{false,true,true,false,false,false,false}));}
 @Test public void selectingOnlyUnitvDoesNotEnableYoutube(){boolean[] only={true,false,true,false,false,false,false};assertEquals(1,RowOptions.count(only));assertFalse(RowOptions.recommendations(only));}
 @Test public void bothYoutubeClientsWorkIndependently(){assertTrue(RowOptions.recommendations(new boolean[]{true,false,false,false,false,true,false}));assertTrue(RowOptions.recommendations(new boolean[]{true,false,false,false,false,false,true}));}
}
