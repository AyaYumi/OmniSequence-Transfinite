package com.atir.molecularmanipulator.client.gui.theme;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Tests the actual packaged union silhouettes, not a flat-color imitation of glass. */
class OsqConnectedTabsTest {
    private static final List<Path> ROOTS=List.of(
            Path.of("src/main/resources/assets/molecularmanipulator"),
            Path.of("resourcepacks/OmniSequence Dark/assets/molecularmanipulator"));

    private static OsqLayout load(Path file) throws Exception {
        var constructor=OsqLayout.class.getDeclaredConstructor(JsonObject.class);constructor.setAccessible(true);
        return constructor.newInstance(JsonParser.parseString(Files.readString(file)).getAsJsonObject());
    }
    private static BufferedImage image(Path root,OsqLayout.Entry surface) throws Exception {
        return ImageIO.read(root.resolve("textures/gui/sprites/"+surface.sprite.split(":",2)[1]+".png").toFile());
    }
    private static List<OsqLayout.Entry> tabs(OsqLayout layout) {
        return layout.draw.stream().filter(e->e.tabSurface!=null).toList();
    }
    private static int rgbDelta(int first,int second) {
        int delta=0;
        for(int shift:new int[]{0,8,16})delta=Math.max(delta,Math.abs((first>>>shift&255)-(second>>>shift&255)));
        return delta;
    }

    @Test void everyTabShipsItsOwnConnectedGlassInBothThemesWithIdenticalGeometry() throws Exception {
        int total=0;
        try(var paths=Files.list(ROOTS.getFirst().resolve("osq_light/layout"))){
            for(var file:paths.filter(p->p.toString().endsWith(".json")).toList()){
                var light=load(file);var dark=load(ROOTS.getLast().resolve("osq_light/layout/"+file.getFileName()));
                assertEquals(tabs(light).size(),tabs(dark).size());
                for(var tab:tabs(light)){
                    var other=dark.findControl(tab.id);assertNotNull(other.tabSurface);
                    assertEquals(tab.x,other.x);assertEquals(tab.y,other.y);assertEquals(tab.w,other.w);assertEquals(22,tab.h);
                    assertEquals(tab.tabPanel,other.tabPanel);
                    assertEquals(tab.tabSurface.x,other.tabSurface.x);assertEquals(tab.tabSurface.y,other.tabSurface.y);
                    assertEquals(tab.tabSurface.w,other.tabSurface.w);assertEquals(tab.tabSurface.h,other.tabSurface.h);
                    for(int i=0;i<2;i++){
                        var source=i==0?tab:other;var pixels=image(ROOTS.get(i),source.tabSurface);
                        assertEquals(Math.round(source.tabSurface.w*4),pixels.getWidth());
                        assertEquals(Math.round(source.tabSurface.h*4),pixels.getHeight());
                        assertNotNull((i==0?light:dark).find(source.tabPanel));
                    }
                    total++;
                }
            }
        }
        assertEquals(41,total,"All five navigation families, including compact layouts, have real surfaces");
    }

    @Test void selectedOpeningsHaveContinuousMaterialWithoutTheOldTopRuleOrShadow() throws Exception {
        for(var root:ROOTS)try(var paths=Files.list(root.resolve("osq_light/layout"))){
            for(var file:paths.filter(p->p.toString().endsWith(".json")).toList()){
                var layout=load(file);
                for(var tab:tabs(layout)){
                    var surface=tab.tabSurface;var pixels=image(root,surface);
                    int join=Math.round((tab.y+tab.h-surface.y)*4);
                    for(float fraction:new float[]{.25f,.5f,.75f}){
                        int x=Math.round((tab.x+tab.w*fraction-surface.x)*4);
                        for(int y=join-8;y<join+8;y++){
                            assertEquals(255,pixels.getRGB(x,y)>>>24);
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
        for(var root:ROOTS)for(String page:List.of("fab_assembly_patterns","fab_assembly_input","fab_assembly_catalyst","seq_center_overview")){
            var layout=load(root.resolve("osq_light/layout/"+page+".json"));
            var tab=tabs(layout).getFirst();
            // Assembly's first tab meets the parent's left edge; the last center
            // tab meets its right edge. Inspect both outside contour directions.
            if(page.startsWith("seq_"))tab=tabs(layout).getLast();
            var surface=tab.tabSurface;var pixels=image(root,surface);
            int x=Math.round(((page.startsWith("seq_")?tab.x+tab.w-.5f:tab.x+.5f)-surface.x)*4);
            int join=Math.round((tab.y+tab.h-surface.y)*4);
            int color=pixels.getRGB(x,join-8);
            for(int y=join-8;y<join+8;y++)assertEquals(color,pixels.getRGB(x,y),root+" "+page+" outside edge must not step or protrude");
            checked++;
        }
        assertEquals(8,checked);
    }

    private static BufferedImage renderedParent(Path root,OsqLayout.Entry parent) throws Exception {
        var source=image(root,parent);
        var metadata=root.resolve("textures/gui/sprites/"+parent.sprite.split(":",2)[1]+".png.mcmeta");
        int width=Math.round((parent.w+parent.pad[0]+parent.pad[2])*4);
        int height=Math.round((parent.h+parent.pad[1]+parent.pad[3])*4);
        if(!Files.exists(metadata)){
            assertEquals(width,source.getWidth());assertEquals(height,source.getHeight());
            return source;
        }
        var scaling=JsonParser.parseString(Files.readString(metadata)).getAsJsonObject()
                .getAsJsonObject("gui").getAsJsonObject("scaling");
        var border=scaling.get("border");int[] edges=new int[4];
        String[] names={"left","top","right","bottom"};
        for(int i=0;i<4;i++)edges[i]=border.isJsonPrimitive()?border.getAsInt():border.getAsJsonObject().get(names[i]).getAsInt();
        float sx=(float)source.getWidth()/scaling.get("width").getAsInt();
        float sy=(float)source.getHeight()/scaling.get("height").getAsInt();
        int[] xx={0,Math.round(edges[0]*sx),source.getWidth()-Math.round(edges[2]*sx),source.getWidth()};
        int[] yy={0,Math.round(edges[1]*sy),source.getHeight()-Math.round(edges[3]*sy),source.getHeight()};
        int[] dx={0,edges[0]*4,width-edges[2]*4,width},dy={0,edges[1]*4,height-edges[3]*4,height};
        var result=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);var graphics=result.createGraphics();
        graphics.setComposite(java.awt.AlphaComposite.Src);
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)graphics.drawImage(source,
                dx[col],dy[row],dx[col+1],dy[row+1],xx[col],yy[row],xx[col+1],yy[row+1],null);
        graphics.dispose();return result;
    }

    private static int[] solidBounds(BufferedImage pixels) {
        int left=pixels.getWidth(),top=pixels.getHeight(),right=0,bottom=0;
        for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++)if((pixels.getRGB(x,y)>>>24)>=220){
            left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x+1);bottom=Math.max(bottom,y+1);
        }
        return new int[]{left,top,right,bottom};
    }

    @Test void connectedPanelsKeepTheirActualCardEdgesAndShadowsOutsideTheOpening() throws Exception {
        int checked=0;
        for(var root:ROOTS)try(var paths=Files.list(root.resolve("osq_light/layout"))){
            for(var file:paths.filter(p->p.toString().endsWith(".json")).toList()){
                var layout=load(file);
                for(var tab:tabs(layout)){
                    var original=renderedParent(root,layout.find(tab.tabPanel));var bounds=solidBounds(original);
                    for(var surface:java.util.stream.Stream.of(tab.tabSurface,tab.tabSingleSurface).filter(java.util.Objects::nonNull).toList()){
                        var pixels=image(root,surface);
                        int parentTop=Math.round((tab.y+tab.h-surface.y)*4);
                        int parentBottom=pixels.getHeight()-12,parentRight=pixels.getWidth()-12;
                        int sourceY=(bounds[1]+bounds[3])/2,targetY=(parentTop+parentBottom)/2;
                        int sourceX=(bounds[0]+bounds[2])/2,targetX=pixels.getWidth()/2;
                        for(int offset=-8;offset<12;offset++){
                            assertEquals(original.getRGB(bounds[0]+offset,sourceY),pixels.getRGB(12+offset,targetY),
                                    file+" "+tab.id+" original LEFT edge/shadow");
                            assertEquals(original.getRGB(bounds[2]-1-offset,sourceY),pixels.getRGB(parentRight-1-offset,targetY),
                                    file+" "+tab.id+" original RIGHT edge/shadow");
                            assertEquals(original.getRGB(sourceX,bounds[3]-1-offset),pixels.getRGB(targetX,parentBottom-1-offset),
                                    file+" "+tab.id+" original BOTTOM edge/shadow");
                        }
                        checked++;
                    }
                }
            }
        }
        assertEquals(98,checked,"Both themes and dedicated single-block silhouettes retain the real parent frame");
    }

    private static int brightness(int color) { return ((color>>>16&255)+(color>>>8&255)+(color&255))/3; }

    @Test void selectedTabsContinueTheOrdinaryCardsTopLeftLightingRatherThanAWhiteRing() throws Exception {
        try(var paths=Files.list(ROOTS.getFirst().resolve("osq_light/layout"))){
            for(var file:paths.filter(p->p.toString().endsWith(".json")).toList())for(var tab:tabs(load(file))){
                var surface=tab.tabSurface;var pixels=image(ROOTS.getFirst(),surface);
                int y=Math.round((tab.y+tab.h/2-surface.y)*4);
                int left=Math.round((tab.x-surface.x)*4),right=Math.round((tab.x+tab.w-surface.x)*4);
                assertTrue(brightness(pixels.getRGB(left+1,y))>brightness(pixels.getRGB(right-2,y))+20,
                        file+" "+tab.id+" has the same light-left/shaded-right bevel as normal cards");
            }
        }
    }

    @Test void assemblyMetadataFramesKeepTheirThinEdgesAtBothLiveCardSizes() throws Exception {
        for(var root:ROOTS)for(String page:List.of("fab_assembly_patterns","fab_assembly_input","fab_assembly_catalyst")){
            var layout=load(root.resolve("osq_light/layout/"+page+".json"));var card=layout.find("meta");
            var pixels=image(root,card);boolean dark=root.equals(ROOTS.getLast());
            assertArrayEquals(new int[]{3,3,3,3},card.pad);
            assertFalse(Files.exists(root.resolve("textures/gui/sprites/"+card.sprite.split(":",2)[1]+".png.mcmeta")),
                    "A fixed baked card must not be nine-slice stretched again");
            assertEquals(Math.round((card.w+6)*4),pixels.getWidth());
            assertEquals(Math.round((card.h+6)*4),pixels.getHeight());
            int x=pixels.getWidth()/2,y=pixels.getHeight()/2;
            if(!dark){
                assertTrue(brightness(pixels.getRGB(13,y))>brightness(pixels.getRGB(pixels.getWidth()-14,y))+25,
                        "The metadata card uses the ordinary card's directional bevel, not a symmetric ring");
                assertTrue(brightness(pixels.getRGB(x,13))>brightness(pixels.getRGB(x,pixels.getHeight()-14))+25);
                assertTrue(brightness(pixels.getRGB(13,y))>230);
                assertTrue(brightness(pixels.getRGB(16,y))<225,"The highlight is subpixel, not a stretched white frame");
            }else{
                assertTrue(rgbDelta(pixels.getRGB(13,y),pixels.getRGB(pixels.getWidth()-14,y))<=2,
                        "Dark cards keep their subdued native edges, not a bright four-sided outline");
            }
        }
    }

    @Test void singleBlockWidthsUseTheirOwnBakedSilhouettesRatherThanStretchingTheOriginalTab() throws Exception {
        for(var root:ROOTS)for(String page:List.of("sing_overview","sing_collection","sing_duplication","sing_compact")){
            var layout=load(root.resolve("osq_light/layout/"+page+".json"));
            OsqSingularityPages.configure(layout,true);
            assertEquals(2,tabs(layout).size());
            for(var tab:tabs(layout)){
                assertSame(tab.tabSingleSurface,tab.tabSurface);
                var pixels=image(root,tab.tabSurface);
                int top=Math.round((tab.y-tab.tabSurface.y)*4);
                int x=Math.round((tab.x+tab.w/2-tab.tabSurface.x)*4);
                assertTrue((pixels.getRGB(x,top)>>>24)>=220,"The real card bevel retains its antialiased top edge");
                assertTrue(tab.x>=tab.tabSurface.x&&tab.x+tab.w<=tab.tabSurface.x+tab.tabSurface.w);
            }
        }
    }
}
