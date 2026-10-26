package com.gathin.tvhome;

import org.junit.Test;
import static org.junit.Assert.*;

public class AppOrderTest {
    @Test public void dockCannotEscapeAboveOrWrapHorizontally() {
        assertEquals(0,AppOrder.target(0,21,6,19));
        assertEquals(5,AppOrder.target(5,22,6,19));
        assertEquals(2,AppOrder.target(2,19,6,19));
    }
    @Test public void canMoveBetweenDockAndGrid() {
        assertEquals(8,AppOrder.target(2,20,6,19));
        assertEquals(2,AppOrder.target(8,19,6,19));
    }
    @Test public void incompleteLastRowHasNoInvalidTarget() {
        assertEquals(18,AppOrder.target(18,22,6,19));
        assertEquals(14,AppOrder.target(14,20,6,19));
    }
    @Test public void validPositionsStayInBoundsForEverySupportedColumnCount() {
        for(int cols=4;cols<=8;cols++)for(int count=1;count<=100;count++)for(int from=0;from<count;from++)for(int key=19;key<=22;key++) {
            int target=AppOrder.target(from,key,cols,count);
            assertTrue(target>=0 && target<count);
            if(key==21||key==22)assertEquals(from/cols,target/cols);
        }
    }
}
