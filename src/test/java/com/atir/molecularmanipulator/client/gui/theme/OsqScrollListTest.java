package com.atir.molecularmanipulator.client.gui.theme;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OsqScrollListTest {
    @Test void projectsScrollOneRowAtATimeWithFiveCompleteRows() {
        assertEquals(5,OsqScrollList.visibleRows(95,19));
        assertEquals(0,OsqScrollList.maxScroll(5,95,19));
        assertEquals(19,OsqScrollList.scroll(0,-1,12,95,19));
        assertEquals(38,OsqScrollList.scroll(19,-1,12,95,19));
        assertEquals(19,OsqScrollList.scroll(38,1,12,95,19));
        assertEquals(133,OsqScrollList.scroll(0,-100,12,95,19));
        assertEquals(133,OsqScrollList.clamp(10_000,12,95,19));
        assertEquals(0,OsqScrollList.clamp(133,4,95,19));
    }

    @Test void threeCompleteRowsFitInTheResearchViewport() {
        assertEquals(3,OsqScrollList.visibleRows(62,20));
        assertEquals(0,OsqScrollList.maxScroll(0,62,20));
        assertEquals(0,OsqScrollList.maxScroll(3,62,20));
        assertEquals(20,OsqScrollList.maxScroll(4,62,20));
        assertEquals(140,OsqScrollList.maxScroll(10,62,20));
    }

    @Test void lastPageUsesWholeRowsRatherThanClippingNamesAtTheTop() {
        assertEquals(20,OsqScrollList.clamp(100,4,62,20));
        assertEquals(20,OsqScrollList.scroll(0,-1,4,62,20));
        assertEquals(0,OsqScrollList.scroll(20,1,4,62,20));
        assertEquals(20,OsqScrollList.scroll(0,-.1,4,62,20));
        assertEquals(140,OsqScrollList.scroll(0,-100,10,62,20));
        assertEquals(140,OsqScrollList.clamp(141,10,62,20));
        assertEquals(0,OsqScrollList.clamp(-100,10,62,20));
    }
}
