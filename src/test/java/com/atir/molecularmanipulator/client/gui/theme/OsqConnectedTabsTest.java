package com.atir.molecularmanipulator.client.gui.theme;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.client.UiRenderRecorder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Inspect the actual frame/contour vertices with the shared PNGs before GPU submission. */
class OsqConnectedTabsTest {
    private static final Path ASSETS=Path.of("src/main/resources/assets/molecularmanipulator");

    private static OsqLayout load(Path file) throws Exception {
        var constructor=OsqLayout.class.getDeclaredConstructor(JsonObject.class);constructor.setAccessible(true);
        var layout=constructor.newInstance(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
        for(var tab:tabs(layout)){
            tab.tabSurface.joinedTab=tab;
            if(tab.tabSingleSurface!=null)tab.tabSingleSurface.joinedTab=tab;
        }
        return layout;
    }
    private static BufferedImage image(OsqLayout.Entry surface) throws Exception {
        return UiRenderRecorder.surface(surface);
    }
    private static List<OsqLayout.Entry> tabs(OsqLayout layout) {
        return layout.draw.stream().filter(e->e.tabSurface!=null).toList();
    }
    private static int rgbDelta(int first,int second) {
        int delta=0;
        for(int shift:new int[]{0,8,16})delta=Math.max(delta,Math.abs((first>>>shift&255)-(second>>>shift&255)));
        return delta;
    }

    @Test void everyTabUsesTheSharedFrameAtItsAuthoredGeometry() throws Exception {
        int total=0;
        try(var paths=Files.list(ASSETS.resolve("osq_light/layout"))){
            for(var file:paths.filter(p->p.toString().endsWith(".json")).toList()){
                var layout=load(file);
                for(var tab:tabs(layout)){
                    assertEquals(22,tab.h);
                    assertTrue(tab.w>0);
                    assertNotNull(layout.find(tab.tabPanel));
                    var parent=layout.find(tab.tabPanel);
                    var surface=tab.tabSurface;var pixels=image(surface);
                    assertEquals(parent.material,surface.material);
                    assertTrue(surface.sprite.startsWith("molecularmanipulator:osq_light/common/glass_"));
                    assertEquals(Math.round(surface.w*4),pixels.getWidth());
                    assertEquals(Math.round(surface.h*4),pixels.getHeight());
                    assertTrue(tab.x>=surface.x&&tab.x+tab.w<=surface.x+surface.w);
                    assertTrue(tab.y>=surface.y&&tab.y+tab.h<=surface.y+surface.h);
                    total++;
                }
            }
        }
        assertEquals(42,total,"All navigation families, including the orrery upgrade tab and compact layouts, have real surfaces");
    }

    @Test void selectedOpeningsHaveContinuousMaterialWithoutTheOldTopRuleOrShadow() throws Exception {
        try(var paths=Files.list(ASSETS.resolve("osq_light/layout"))){
            for(var file:paths.filter(p->p.toString().endsWith(".json")).toList()){
                var layout=load(file);
                for(var tab:tabs(layout)){
                    var surface=tab.tabSurface;var pixels=image(surface);
                    int join=Math.round((tab.y+tab.h-surface.y)*4);
                    for(float fraction:new float[]{.25f,.5f,.75f}){
                        int x=Math.round((tab.x+tab.w*fraction-surface.x)*4);
                        for(int y=join-8;y<join+8;y++){
                            assertEquals(pixels.getRGB(x,join+32)>>>24,pixels.getRGB(x,y)>>>24);
                            assertTrue(rgbDelta(pixels.getRGB(x,y),pixels.getRGB(x,y+1))<=8,
                                    file+" "+tab.id+" has a rule/shadow through its opening at "+x+","+y);
                        }
                    }
                }
            }
        }
    }

    @Test void flushOuterEdgesAreStraightAcrossTheJoinWithoutCornerNubs() throws Exception {
        int checked=0;
        for(String page:List.of("fab_assembly_patterns","fab_assembly_input","fab_assembly_catalyst","seq_center_overview")){
            var layout=load(ASSETS.resolve("osq_light/layout/"+page+".json"));
            var tab=tabs(layout).getFirst();
            // Assembly's first tab meets the parent's left edge; the last center
            // tab meets its right edge. Inspect both outside contour directions.
            if(page.startsWith("seq_"))tab=tabs(layout).getLast();
            var surface=tab.tabSurface;var pixels=image(surface);
            int x=Math.round(((page.startsWith("seq_")?tab.x+tab.w-.5f:tab.x+.5f)-surface.x)*4);
            int join=Math.round((tab.y+tab.h-surface.y)*4);
            int color=pixels.getRGB(x,join-8);
            for(int y=join-8;y<join+8;y++)assertEquals(color,pixels.getRGB(x,y),page+" outside edge must not step or protrude");
            checked++;
        }
        assertEquals(4,checked);
    }

    private static BufferedImage renderedParent(OsqLayout.Entry parent,OsqLayout.Entry selected) throws Exception {
        var frame=new OsqLayout.Entry();
        frame.sprite=selected.sprite;frame.material=selected.material;
        frame.w=parent.w;frame.h=parent.h;frame.pad=parent.pad;
        return UiRenderRecorder.surface(frame);
    }

    private static int[] solidBounds(BufferedImage pixels) {
        int left=pixels.getWidth(),top=pixels.getHeight(),right=0,bottom=0;
        for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++)if((pixels.getRGB(x,y)>>>24)>=220){
            left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);
        }
        return new int[]{left,top,right,bottom};
    }

    @Test void connectedPanelsKeepTheirSharedCardEdgesAndShadowsOutsideTheOpening() throws Exception {
        int checked=0;
        try(var paths=Files.list(ASSETS.resolve("osq_light/layout"))){
            for(var file:paths.filter(p->p.toString().endsWith(".json")).toList()){
                var layout=load(file);
                for(var tab:tabs(layout)){
                    var parent=layout.find(tab.tabPanel);
                    for(var surface:java.util.stream.Stream.of(tab.tabSurface,tab.tabSingleSurface).filter(java.util.Objects::nonNull).toList()){
                        // Feature tabs retain the dark material in the shared card contour; their
                        // unselected broad feature frame has a different bevel and shadow metadata.
                        var original=renderedParent(parent,surface);
                        var pixels=image(surface);
                        int parentTop=Math.round((tab.y+tab.h-surface.y)*4);
                        int sourceY=original.getHeight()/2,targetY=(parentTop+pixels.getHeight()-12)/2;
                        int sourceX=original.getWidth()/2,targetX=pixels.getWidth()/2;
                        for(int offset=0;offset<24;offset++){
                            assertPixel(original.getRGB(offset,sourceY),pixels.getRGB(offset,targetY),file+" "+tab.id+" LEFT edge/shadow");
                            assertPixel(original.getRGB(original.getWidth()-1-offset,sourceY),pixels.getRGB(pixels.getWidth()-1-offset,targetY),
                                    file+" "+tab.id+" RIGHT edge/shadow");
                            assertPixel(original.getRGB(sourceX,original.getHeight()-1-offset),pixels.getRGB(targetX,pixels.getHeight()-1-offset),
                                    file+" "+tab.id+" BOTTOM edge/shadow");
                        }
                        checked++;
                    }
                }
            }
        }
        assertEquals(50,checked,"All navigation families, including orrery upgrades and dedicated single-block silhouettes, retain the real parent frame");
    }

    private static void assertPixel(int expected,int actual,String message){
        for(int shift:new int[]{0,8,16,24})assertEquals(expected>>>shift&255,actual>>>shift&255,1,message);
    }

    private static int brightness(int color) { return ((color>>>16&255)+(color>>>8&255)+(color&255))/3; }

    @Test void selectedTabsContinueTheOrdinaryCardsTopLeftLightingRatherThanAWhiteRing() throws Exception {
        try(var paths=Files.list(ASSETS.resolve("osq_light/layout"))){
            for(var file:paths.filter(p->p.toString().endsWith(".json")).toList())for(var tab:tabs(load(file))){
                var surface=tab.tabSurface;var pixels=image(surface);
                int y=Math.round((tab.y+tab.h/2-surface.y)*4);
                int left=Math.round((tab.x-surface.x)*4),right=Math.round((tab.x+tab.w-surface.x)*4);
                assertTrue(brightness(pixels.getRGB(left+1,y))>brightness(pixels.getRGB(right-2,y))+20,
                        file+" "+tab.id+" has the same light-left/shaded-right bevel as normal cards");
            }
        }
    }

    @Test void assemblyMetadataFramesKeepTheirThinEdgesAtBothLiveCardSizes() throws Exception {
        for(String page:List.of("fab_assembly_patterns","fab_assembly_input","fab_assembly_catalyst")){
            var layout=load(ASSETS.resolve("osq_light/layout/"+page+".json"));var card=layout.find("meta");
            var pixels=image(card);
            assertArrayEquals(new int[]{3,3,3,3},card.pad);
            assertTrue(Files.exists(ASSETS.resolve("textures/gui/sprites/"+card.sprite.split(":",2)[1]+".png.mcmeta")),
                    "Shared frames retain their fixed logical border through nine-slice metadata");
            assertEquals(Math.round((card.w+6)*4),pixels.getWidth());
            assertEquals(Math.round((card.h+6)*4),pixels.getHeight());
            int x=pixels.getWidth()/2,y=pixels.getHeight()/2;
            assertTrue(brightness(pixels.getRGB(13,y))>brightness(pixels.getRGB(pixels.getWidth()-14,y))+25,
                    "The metadata card uses the ordinary card's directional bevel, not a symmetric ring");
            assertTrue(brightness(pixels.getRGB(x,13))>brightness(pixels.getRGB(x,pixels.getHeight()-14))+25);
            assertTrue(brightness(pixels.getRGB(13,y))>220,"Linear sampling softens the subpixel highlight without widening it");
            assertEquals(16,pixels.getRGB(16,y)>>>24,"The thin bevel gives way to the translucent card interior");
        }
    }

    @Test void singleBlockWidthsUseLiveContoursWithoutStretchingTheTab() throws Exception {
        for(String page:List.of("sing_overview","sing_collection","sing_duplication","sing_compact")){
            var layout=load(ASSETS.resolve("osq_light/layout/"+page+".json"));
            OsqSingularityPages.configure(layout,true);
            assertEquals(2,tabs(layout).size());
            for(var tab:tabs(layout)){
                assertSame(tab.tabSingleSurface,tab.tabSurface);
                var pixels=image(tab.tabSurface);
                int top=Math.round((tab.y-tab.tabSurface.y)*4);
                int x=Math.round((tab.x+tab.w/2-tab.tabSurface.x)*4);
                assertTrue((pixels.getRGB(x,top)>>>24)>0,"The tab's top edge retains antialiasing");
                assertTrue((pixels.getRGB(x,top+1)>>>24)>=230,"The bevel is solid within one quarter GUI pixel");
                assertTrue(tab.x>=tab.tabSurface.x&&tab.x+tab.w<=tab.tabSurface.x+tab.tabSurface.w);
            }
        }
    }
}
