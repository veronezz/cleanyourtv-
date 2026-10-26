package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;

public class LiveTvOrderTest {
    @Test public void mainGloboHandlesCaseWhitespaceAndProgrammeSuffix(){
        assertTrue(LiveTvOrder.isMainGlobo("  AGORA na GLOBO "));
        assertTrue(LiveTvOrder.isMainGlobo("Agora na Globo · Jornal Nacional"));
        assertTrue(LiveTvOrder.isMainGlobo("TV Globo"));
    }
    @Test public void otherChannelsDoNotMatch(){
        for(String value:new String[]{"Agora na GloboNews","Globoplay","Agora no BIS","Receitas Fast","",null})assertFalse(LiveTvOrder.isMainGlobo(value));
    }
    @Test public void priorityKeepsOtherChannelsInTheirPublishedOrder(){
        List<String> channels=new ArrayList<>(Arrays.asList("BIS","Sportv","Agora na Globo","Receitas Fast"));
        channels.sort(Comparator.comparingInt(LiveTvOrder::priority));
        assertEquals(Arrays.asList("Agora na Globo","BIS","Sportv","Receitas Fast"),channels);
    }
    @Test public void mainBeyondDisplayLimitIsRetained(){
        List<String> channels=new ArrayList<>();for(int i=0;i<30;i++)channels.add("Pago "+i);
        channels.add("Agora na Globo");channels.sort(Comparator.comparingInt(LiveTvOrder::priority));
        assertEquals("Agora na Globo",channels.subList(0,24).get(0));
    }
}
