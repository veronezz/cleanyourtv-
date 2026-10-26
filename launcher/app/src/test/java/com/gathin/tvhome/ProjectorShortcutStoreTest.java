package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
public class ProjectorShortcutStoreTest {
 @Test public void corruptInputDoesNotPreventStartup(){assertTrue(ProjectorShortcutStore.parse("bad json").isEmpty());assertTrue(ProjectorShortcutStore.parse(null).isEmpty());}
 @Test public void componentsAndNamesSurviveRestart(){List<ProjectorShortcutStore.Record> rows=Arrays.asList(new ProjectorShortcutStore.Record("com.vendor.settings/.ImageActivity","Ajuste de imagem"));List<ProjectorShortcutStore.Record> read=ProjectorShortcutStore.parse(ProjectorShortcutStore.encode(rows));assertEquals(1,read.size());assertEquals(rows.get(0).component,read.get(0).component);assertEquals(rows.get(0).label,read.get(0).label);}
 @Test public void badRecordsAreSkippedWithoutLosingGoodRecords(){assertEquals(1,ProjectorShortcutStore.parse("[null,{}, {\"component\":\"https://url\",\"label\":\"bad\"},{\"component\":\"com.vendor/.Image\",\"label\":\"Imagem\"}]").size());}
 @Test public void duplicateComponentsHaveOneTile(){List<ProjectorShortcutStore.Record> rows=Arrays.asList(new ProjectorShortcutStore.Record("com.vendor/.Image","A"),new ProjectorShortcutStore.Record("com.vendor/.Image","B"));assertEquals(1,ProjectorShortcutStore.parse(ProjectorShortcutStore.encode(rows)).size());assertEquals(1,ProjectorShortcutStore.parse("[{\"component\":\"com.vendor/.Image\",\"label\":\"A\"},{\"component\":\"com.vendor/.Image\",\"label\":\"B\"}]").size());}
 @Test public void recordCountIsBounded(){List<ProjectorShortcutStore.Record> rows=new ArrayList<>();for(int i=0;i<100;i++)rows.add(new ProjectorShortcutStore.Record("com.vendor/.Image"+i,"A"));assertEquals(48,ProjectorShortcutStore.parse(ProjectorShortcutStore.encode(rows)).size());}
 @Test public void blankOrMisleadingNamesAreCleaned(){assertEquals("Imagem",DisplayLabel.clean(" \u202eImagem\n "));assertEquals("",DisplayLabel.clean(null));assertTrue(ProjectorShortcutStore.parse("[{\"component\":\"com.vendor/.Image\",\"label\":\"  \"}]").isEmpty());}
 @Test public void longNamesKeepUnicodePairsIntact(){assertEquals(80,DisplayLabel.clean("a".repeat(200)).length());assertEquals(79,DisplayLabel.clean("a".repeat(79)+"😀").length());}
 @Test public void storageAcceptsActivityAliasesAndRejectsIntents(){assertTrue(ProjectorShortcutStore.valid("com.vendor/.Image$Inner"));assertFalse(ProjectorShortcutStore.valid("intent:#Intent;end"));assertFalse(ProjectorShortcutStore.valid("com.vendor/.Image\n"));}
}
