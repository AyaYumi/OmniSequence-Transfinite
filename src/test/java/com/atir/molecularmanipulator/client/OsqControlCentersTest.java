package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OsqControlCentersTest {
    @Test void buttonLabelsFollowTheVisibleAe2FaceInEveryState() {
        assertEquals(8.5f, OsqUi.controlCenter("button", "normal", 20, false));
        assertEquals(9.5f, OsqUi.controlCenter("button", "hover", 20, false));
        for (String state : new String[]{"active", "pressed", "disabled"})
            assertEquals(10.5f, OsqUi.controlCenter("button", state, 20, false));
        assertEquals(5.5f, OsqUi.controlCenter("key13", "normal", 13, false));
        assertEquals(6.5f, OsqUi.controlCenter("key13", "active", 13, false));
        assertEquals(10f, OsqUi.controlCenter("edge_key", "normal", 22, false));
        assertEquals(11f, OsqUi.controlCenter("edge_key", "active", 22, false));
    }

    @Test void tabsNeverDepressTheirTextLikeButtons() {
        for (String state : new String[]{"normal", "hover", "active", "disabled"})
            assertEquals(10f, OsqUi.controlCenter("tab", state, 22, false));
    }

    @Test void unselectedTabsUseTheReferenceGreyFrameWithoutAnyExteriorBottomHighlight() {
        for(var palette:com.atir.molecularmanipulator.client.gui.theme.OsqPalette.values())
            for(String state:new String[]{"normal","hover","disabled"}){
                var g=new UiRenderRecorder();var before=new org.joml.Matrix4f(g.pose().last().pose());
                com.atir.molecularmanipulator.client.gui.theme.OsqTabs.draw(g,20,30,80,22,palette,state,false,true);
                assertEquals(3,g.rects.size());assertEquals(before,g.pose().last().pose());
                for(var rect:g.rects)assertEquals(before,rect.transform(),"The frame does not move the label pose");
                int border=palette.dark?palette.ink4:0xFF696D88;
                int rim=palette.dark?palette.disT:0xFFADB0C4;
                int fill=state.equals("disabled")?palette.dis:state.equals("hover")
                        ?palette.dark?palette.slot:palette.sunk:palette.raise;
                for(int[] point:new int[][]{{60,30},{20,40},{99,40},{60,49}})
                    assertEquals(border,colorAt(g,point[0],point[1]),"Reference-style grey outer frame, not the old heavy dark outline");
                for(int[] point:new int[][]{{60,31},{21,40},{98,40},{60,48},{21,48},{98,48}})
                    assertEquals(rim,colorAt(g,point[0],point[1]),"Pale inner rim closes across the bottom and both corners");
                assertEquals(fill,colorAt(g,60,47));
                for(int x=20;x<100;x++)for(int y=50;y<52;y++)
                    assertEquals(0,colorAt(g,x,y),"No exterior highlight or flat backing strip below the dark frame");
            }
    }

    private static int colorAt(UiRenderRecorder graphics,int x,int y) {
        int color=0;
        for(var rect:graphics.rects)
            if(x>=rect.left()&&x<rect.right()&&y>=rect.top()&&y<rect.bottom())color=rect.color();
        return color;
    }

    @Test void paginationKeepsArrowsAndPageTextOnOneSharedCenter() {
        for (String state : new String[]{"normal", "hover", "active", "disabled"})
            assertEquals(6.5f, OsqUi.controlCenter("key13", state, 13, true));
    }
}
