package com.atir.molecularmanipulator.client.gui.theme;

import com.atir.molecularmanipulator.client.ResponsiveContainerScreen;
import com.atir.molecularmanipulator.client.ResponsiveScreenTestFixture;
import com.atir.molecularmanipulator.client.SingularityScreen;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.geom.Rectangle2D;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OsqRequestedUiTest {
    private static final String[] ROOTS={"src/main/resources"};
    private OsqLayout layout(String root,String name) throws Exception {
        var constructor=OsqLayout.class.getDeclaredConstructor(JsonObject.class);constructor.setAccessible(true);
        return constructor.newInstance(JsonParser.parseString(Files.readString(Path.of(root,
                "assets/molecularmanipulator/osq_light/layout/"+name+".json"))).getAsJsonObject());
    }
    @Test void compactOverviewRemovesIllustrationAndQuantumAndReflows() throws Exception {
        for(String root:ROOTS) {
            var l=layout(root,"sing_compact");
            assertNull(l.find("hero"));assertNull(l.find("quantum"));assertNull(l.find("qes"));
            assertTrue(l.draw.stream().noneMatch(e->"holo".equals(e.type)));
            assertEquals(260,l.width);
            var inventory=l.find("inv");assertEquals(l.width/2f,inventory.x+inventory.w/2);
            var info=l.find("info");
            for(String key:new String[]{"compact_collection","compact_duplication","compact_network","compact_chunks"}) {
                var text=l.findText("gui.molecularmanipulator.singularity."+key);
                assertTrue(text.wrap);assertTrue(text.size>=8);assertTrue(text.w>=220);
                assertTrue(text.x>=info.x&&text.x+text.w<=info.x+info.w);
                assertTrue(text.baseline>=info.y&&text.baseline<info.y+info.h);
            }
        }
    }
    @Test void centerQuantumRowsAndInventoryFitTheirReflowedCards() throws Exception {
        for(String root:ROOTS)for(String name:List.of("seq_center_overview","seq_center_autocraft","seq_center_colors")) {
            var l=layout(root,name);var quantum=l.find("quantum");var inventory=l.find("inv");var slot=l.find("qes");
            boolean overview=name.equals("seq_center_overview");int dy=overview?20:0;
            if(overview) {
                assertRect(quantum,192,165,232,87);assertRect(slot,200,173,18,18);assertRect(inventory,8,196,178,84);
                var environment=l.find("env");assertEquals(environment.y+environment.h+6,inventory.y);
            } else {
                assertRect(quantum,8,145,178,87);assertRect(slot,16,153,18,18);assertRect(inventory,8,238,178,84);
                assertEquals(quantum.y+quantum.h+6,inventory.y);
            }
            assertEquals(inventory.y+inventory.h+8,l.height);
            var title=l.findText("gui.molecularmanipulator.quantum_title");
            var state=l.findText("gui.molecularmanipulator.quantum_state.connected");
            var caption=l.findText("gui.molecularmanipulator.fab.s025");
            var upkeep=l.findText("gui.molecularmanipulator.overview_quantum_requirements");
            var frequency=l.dynamicTexts().stream().filter(e->e.x==65.39f&&e.baseline==179.5f).toList();
            assertEquals(1,frequency.size(),name+" retains the live frequency binding");
            assertEquals(155f+dy,title.centerY);assertEquals(168f+dy,state.centerY);
            assertEquals(46f,state.x);assertEquals(165.89f,state.baseline);
            var dot=l.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/dot_")&&e.x==39&&e.y==167).findFirst().orElseThrow();
            assertEquals(state.centerY,dot.drawY+dot.h/2);
            assertEquals(181f+dy,caption.centerY);assertEquals(caption.centerY,frequency.getFirst().centerY);assertEquals(194f+dy,upkeep.centerY);
            assertTrue(OsqLayout.textX(title,title.w)>=slot.x+slot.w+5);
            assertTrue(state.drawX>=slot.x+slot.w+5);assertEquals(quantum.x+quantum.w-8,state.drawX+state.w);
            assertTrue(caption.centerY-caption.size/2>=slot.y+slot.h+6);
            var slots=l.draw.stream().filter(e->"inv".equals(e.id)&&"slot".equals(e.kind)).toList();
            assertEquals(36,slots.size());int[] rows=overview?new int[]{200,218,236,258}:new int[]{242,260,278,300};
            for(int i=0;i<slots.size();i++)assertRect(slots.get(i),16+18*(i%9),rows[i/9],18,18);
            for(var card:List.of(quantum,inventory)) {
                var file=Path.of(root,"assets/molecularmanipulator/textures/gui/sprites/"+card.sprite.split(":",2)[1]+".png");
                if(Files.exists(Path.of(file+".mcmeta")))continue;
                var pixels=ImageIO.read(file.toFile());
                assertEquals(Math.round((card.w+card.pad[0]+card.pad[2])*4),pixels.getWidth(),name+" "+card.id);
                assertEquals(Math.round((card.h+card.pad[1]+card.pad[3])*4),pixels.getHeight(),name+" "+card.id);
            }
        }
    }
    @Test void singularityQuantumLabelsAlignWithTheSlotWithoutMovingLiveBindingAddresses() throws Exception {
        for(String root:ROOTS) {
            var l=layout(root,"sing_overview");var slot=l.find("qes");assertRect(slot,16,153,18,18);
            var title=l.findText("gui.molecularmanipulator.singularity.quantum_input");
            var hint=l.findText("gui.molecularmanipulator.singularity.quantum_input_hint");
            var caption=l.findText("gui.molecularmanipulator.fab.s025");var status=l.findText("gui.molecularmanipulator.sing.s001");
            var frequency=l.dynamicTexts().stream().filter(e->e.x==66.02f&&e.baseline==187.5f).toList();
            assertEquals(1,frequency.size());assertEquals(slot.y+slot.h/2,title.centerY);
            assertEquals(172f,hint.centerY);assertEquals(183f,caption.centerY);assertEquals(caption.centerY,frequency.getFirst().centerY);
            assertEquals(195f,status.centerY);assertEquals(96.72f,status.x);assertEquals(185.8f,status.baseline);
            for(var text:List.of(title,hint))assertEquals(slot.x+slot.w+6,text.drawX);
            assertEquals(16f,caption.drawX);assertEquals(56f,frequency.getFirst().drawX);assertEquals(16f,status.drawX);
            assertTrue(caption.drawX+caption.w+6<=frequency.getFirst().drawX);
        }
    }
    @Test void duplicationMetricsShareTheRightCardAndTheLeftGeneratorIsVerticallyCentered() throws Exception {
        for(String root:ROOTS) {
            var l=layout(root,"sing_duplication");var count=l.findText("gui.molecularmanipulator.sing.s014");
            var rate=l.find("rate");var hole=l.find("hole");
            assertEquals(hole.y,rate.y);assertEquals(hole.h,rate.h);
            assertNull(l.find("count"));assertNull(l.findText("gui.molecularmanipulator.sing.s017"));
            var generator=l.findText("gui.molecularmanipulator.singularity.duplication_black_hole");
            var input=l.draw.stream().filter(e->"hole".equals(e.id)&&"slot".equals(e.kind)).findFirst().orElseThrow();
            assertEquals(hole.y+hole.h/2,input.y+input.h/2);
            assertEquals(input.y+input.h/2,generator.centerY);assertEquals(18,input.w);assertEquals(18,input.h);
            assertTrue(l.owns(hole,generator));assertTrue(input.x+input.w+5<=generator.x);
            var divider=l.find("metrics_divider");assertTrue(l.owns(rate,divider));
            assertTrue(count.wrap);assertEquals(7.5f,count.size);assertEquals(164f,count.centerY);
            assertTrue(l.owns(rate,count));assertTrue(count.drawX>=divider.x+divider.w+7);
            assertTrue(count.drawX+count.w<=rate.x+rate.w-8);
            for(var entry:l.dynamicTexts())if(entry.baseline==203) {
                assertEquals(183f,entry.centerY);assertTrue(l.owns(rate,entry));
                assertTrue(entry.drawX>=count.drawX&&entry.drawX+entry.w<=rate.x+rate.w-8);
            }
            var interval=l.findText("gui.molecularmanipulator.sing.s016");
            for(var entry:l.draw)if("text".equals(entry.type)&&entry.baseline==194) {
                assertEquals(interval.centerY,entry.centerY);assertTrue(l.owns(rate,entry));
                assertTrue(entry.drawX>=count.drawX&&entry.drawX+entry.w<=rate.x+rate.w-8);
            }
            var inventory=l.find("inv");var energy=l.find("energy");
            assertEquals(energy.y,inventory.y);assertEquals(rate.y+rate.h+6,inventory.y);
            assertEquals(energy.y+energy.h,inventory.y+inventory.h);
            assertEquals(inventory.y+inventory.h+8,l.height);
            var slots=l.draw.stream().filter(e->"inv".equals(e.id)&&"slot".equals(e.kind)).toList();
            assertEquals(36,slots.size());
            assertEquals(inventory.y+6,slots.getFirst().y);
            assertEquals(inventory.y+64,slots.getLast().y);
            var sample=l.find("sample");var matter=l.findText("block.molecularmanipulator.singularity_sequence_matter");
            var destination=l.findText("gui.molecularmanipulator.sing.s013");
            assertEquals(113f,matter.centerY);assertEquals(matter.centerY,destination.centerY);
            assertEquals(matter.centerY,sample.y+sample.h/2);
            var flow=l.find("flow");
            for(var entry:l.draw)if(entry.sprite!=null&&(entry.sprite.endsWith("/flow_line")||entry.sprite.contains("/icons/net_")&&entry.y>80))
                assertEquals(matter.centerY,entry.y+entry.h/2);
            for(var text:List.of(matter,destination,l.findText("gui.molecularmanipulator.singularity.duplication_cost")))assertTrue(l.owns(flow,text));
            var footer=l.findText("gui.molecularmanipulator.singularity.duplication_fluid_storage");
            assertTrue(footer.wrap);assertEquals(287f,footer.centerY);assertEquals(132f,footer.w);
            assertTrue(OsqLayout.textX(footer,footer.w)>=energy.x+8);
            assertTrue(OsqLayout.textX(footer,footer.w)+footer.w<=energy.x+energy.w-8);
        }
    }
    @Test void duplicationFlowAndBottomHintRenderClearOfIconsCancelAndCardEdges() throws Exception {
        for(String language:List.of("zh_cn","en_us"))try(var runtime=new RuntimeText(false,language)) {
            var l=layout(ROOTS[0],"sing_duplication");var cancel=runtime.duplicationCancel();
            assertEquals(173,cancel.getX());assertEquals(85,cancel.getY());assertEquals(68,cancel.getWidth());assertEquals(17,cancel.getHeight());
            var icon=l.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/icons/net_")&&e.y>80).findFirst().orElseThrow();
            var rendered=new ArrayList<Rectangle2D>();
            for(String key:List.of("gui.molecularmanipulator.singularity.duplication_sample","block.molecularmanipulator.singularity_sequence_matter",
                    "gui.molecularmanipulator.sing.s013","gui.molecularmanipulator.singularity.duplication_cost")) {
                var entry=l.findText(key);var ink=runtime.render(l,entry);assertEquals(1,ink.size(),language+" "+key);
                var bounds=ink.getFirst();
                assertEquals(entry.centerY,(bounds.getMinY()+bounds.getMaxY())/2,.8,language+" "+key);
                assertFalse(bounds.intersects(cancel.getX(),cancel.getY(),cancel.getWidth(),cancel.getHeight()),key);
                assertFalse(bounds.intersects(icon.x,icon.y,icon.w,icon.h),key);
                if(key.endsWith("sing.s013"))assertTrue(bounds.getMinX()>=icon.x+icon.w+5);
                if(key.endsWith("duplication_cost"))assertTrue(bounds.getMinY()>=icon.y+icon.h+3);
                for(var previous:rendered)assertFalse(previous.intersects(bounds),key);
                rendered.add(bounds);
            }
            var footer=l.findText("gui.molecularmanipulator.singularity.duplication_fluid_storage");var lines=runtime.render(l,footer);
            assertEquals(2,lines.size(),language+" keeps both full hint clauses readable");
            var energy=l.find("energy");
            for(var line:lines) {
                assertTrue(line.getMinX()>=energy.x+8&&line.getMaxX()<=energy.x+energy.w-8,language+" "+line);
                assertTrue(line.getMaxY()<=energy.y+energy.h-6,language+" bottom margin "+line);
            }
            var lastEnergyRow=l.dynamicTexts().stream().filter(e->e.x==17&&e.baseline==272).findFirst().orElseThrow();
            assertTrue(lines.getFirst().getMinY()>=lastEnergyRow.baseline+3,language+" hint clears the final energy row");
            assertTrue(lines.getFirst().getMaxY()+2<=lines.getLast().getMinY(),language);
            assertEquals(footer.centerY,(lines.getFirst().getMinY()+lines.getLast().getMaxY())/2,.8);
        }
    }
    @Test void compactHubsHaveTwoTabsAndNoQuantumCollectionStatus() throws Exception {
        for(String root:ROOTS)for(String page:new String[]{"sing_collection","sing_duplication"}) {
            var l=layout(root,page);var network=l.findText("gui.molecularmanipulator.singularity.collection_network");
            var originalTabs=l.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/tab_")).toList();
            float left=originalTabs.getFirst().x;float right=originalTabs.getLast().x+originalTabs.getLast().w;
            OsqSingularityPages.configure(l,true);
            assertNull(l.findText("gui.molecularmanipulator.singularity.overview"));
            assertNull(l.find("collection_quantum_status"));
            assertSame(network,l.findText("gui.molecularmanipulator.singularity.collection_network"));
            var tabs=l.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/tab_")).toList();
            assertEquals(2,tabs.size());assertEquals(left,tabs.getFirst().x);
            assertEquals(right,tabs.getLast().x+tabs.getLast().w);
            assertEquals(tabs.getFirst().w,tabs.getLast().w);
            assertEquals(tabs.getFirst().x+tabs.getFirst().w,tabs.getLast().x);
            for(String key:new String[]{"collection","duplication"}) {
                var label=l.findText("gui.molecularmanipulator.singularity."+key);
                assertEquals("center",label.alignment);assertNotNull(label.centerY);
            }
        }
    }
    @Test void multiblockHubsKeepOverviewAndQuantumCollectionStatus() throws Exception {
        for(String root:ROOTS) {
            var l=layout(root,"sing_collection");int original=l.draw.size();
            OsqSingularityPages.configure(l,false);
            assertEquals(original,l.draw.size());
            assertNotNull(l.findText("gui.molecularmanipulator.singularity.overview"));
            assertEquals(3,l.draw.stream().filter(e->"collection_quantum_status".equals(e.id)).count());
            assertNotNull(l.findText("gui.molecularmanipulator.fab.s025"));
        }
    }
    @Test void assemblyNameFieldMatchesSaveButtonAndMetadataIsCentered() throws Exception {
        for(String root:ROOTS)for(String page:new String[]{"fab_assembly_patterns","fab_assembly_input","fab_assembly_catalyst"}) {
            var l=layout(root,page);var field=l.findControl("name");var save=l.findControl("save");
            assertEquals(save.y,field.y);assertEquals(save.h,field.h);assertEquals(22,field.h);
            if(page.endsWith("patterns")) {
                for(String id:List.of("assembly_capacity","assembly_capacity_icon","assembly_queue","assembly_queue_icon"))
                    assertNull(l.find(id),"Pattern page removes the capacity and queued-batch metadata, including icons");
                assertNull(l.findText("gui.molecularmanipulator.fabrication.buffer.capacity"));
                assertNull(l.findText("gui.molecularmanipulator.fabrication.buffer.queued"));
                assertNotNull(l.findControl("return"),"Keep the server-backed refund action");
            } else if(page.endsWith("catalyst")) {
                var count=l.find("assembly_catalyst_count");var hint=l.find("assembly_catalyst_hint");
                assertEquals("gui.molecularmanipulator.fabrication.buffer.catalyst_types",count.lang);
                assertEquals("gui.molecularmanipulator.fabrication.buffer.catalyst_location",hint.lang);
                assertEquals("center",count.alignment);assertEquals("center",hint.alignment);
                assertTrue(l.draw.stream().noneMatch(e->e.dynamic&&e.baseline>=180));
            }
        }
    }
    @Test void patternMetadataKeepsOnlyRefundAndClosesTheRemovedRows() throws Exception {
        for(String root:ROOTS) {
            var l=layout(root,"fab_assembly_patterns");var body=l.find("body");var meta=l.find("meta");var inventory=l.find("inv");
            assertEquals(190,l.width);assertEquals(342,l.height);
            assertEquals(91,body.y);assertEquals(114,body.h);
            assertEquals(body.y+body.h+6,meta.y);assertEquals(174,meta.w);assertEquals(28,meta.h);
            assertEquals(meta.y+meta.h+7,inventory.y);assertEquals(88,inventory.h);
            assertEquals(inventory.y+inventory.h+8,l.height);
            var refund=l.findControl("return");
            assertEquals(14,refund.x);assertEquals(meta.y+3,refund.y);assertEquals(162,refund.w);assertEquals(22,refund.h);
            assertEquals(meta.y+meta.h-3,refund.y+refund.h);
            var refundContents=l.draw.stream().filter(e->l.owns(refund,e)).toList();
            assertEquals(3,refundContents.size(),"Keep the button frame, recycle icon and label");
            assertTrue(refundContents.stream().anyMatch(e->"gui.molecularmanipulator.fab.s005".equals(e.lang)));
            for(String id:List.of("pat","inv")) {
                var slots=l.draw.stream().filter(e->id.equals(e.id)&&"slot".equals(e.kind)).toList();
                assertEquals(36,slots.size());
                int[] rows=id.equals("pat")?new int[]{114,132,150,168}:new int[]{252,270,288,310};
                for(int i=0;i<slots.size();i++) {
                    var slot=slots.get(i);assertEquals(14+18*(i%9),slot.x);assertEquals(rows[i/9],slot.y);
                    assertEquals(18,slot.w);assertEquals(18,slot.h);
                }
            }
            for(int i=0;i<4;i++) {
                var surface=l.findControl("tab"+i).tabSurface;assertNotNull(surface);
                assertEquals(5,surface.x);assertEquals(66,surface.y);assertEquals(180,surface.w);assertEquals(142,surface.h);
            }
            for(var card:List.of(meta,inventory,l.find("window"))) {
                var file=Path.of(root,"assets/molecularmanipulator/textures/gui/sprites/"+card.sprite.split(":",2)[1]+".png");
                var pixels=ImageIO.read(file.toFile());
                assertEquals(Math.round((card.w+card.pad[0]+card.pad[2])*4),pixels.getWidth());
                assertEquals(Math.round((card.h+card.pad[1]+card.pad[3])*4),pixels.getHeight());
            }
        }
    }
    @Test void catalystMetadataUsesOnlyTwoRowsAndInventoryClosesTheUnusedRefundSpace() throws Exception {
        for(String root:ROOTS) {
            var l=layout(root,"fab_assembly_catalyst");var body=l.find("body");var meta=l.find("meta");var inventory=l.find("inv");
            assertEquals(190,l.width);assertEquals(343,l.height);
            assertEquals(91,body.y);assertEquals(114,body.h);
            assertEquals(body.y+body.h+6,meta.y);assertEquals(174,meta.w);assertEquals(30,meta.h);
            assertEquals(meta.y+meta.h+6,inventory.y);assertEquals(88,inventory.h);
            assertEquals(inventory.y+inventory.h+8,l.height);
            var count=l.find("assembly_catalyst_count");var hint=l.find("assembly_catalyst_hint");
            assertEquals(21,count.x);assertEquals(188,count.baseline);
            assertEquals(12,hint.x);assertEquals(200,hint.baseline);
            assertEquals(meta.y+9,count.centerY);assertEquals(meta.y+21,hint.centerY);
            for(var text:List.of(count,hint)) {
                assertEquals(7.5f,text.size);assertFalse(text.wrap);
                assertEquals("minecraft:default",text.fontId);
                assertTrue(text.drawX>=meta.x+6&&text.drawX+text.w<=meta.x+meta.w-6);
                assertTrue(text.centerY-text.size/2>=meta.y+5);
                assertTrue(text.centerY+text.size/2<=meta.y+meta.h-5);
            }
            // Keep the source control/ownership, but do not reserve a visible row for it:
            // the catalyst screen already hides exactly this control and its contents.
            var refund=l.findControl("return");assertEquals(22,refund.h);
            assertTrue(refund.y>=meta.y&&refund.y+refund.h<=meta.y+meta.h);
            assertEquals(3,l.draw.stream().filter(e->l.owns(refund,e)).count());
            assertFalse(l.owns(refund,count));assertFalse(l.owns(refund,hint));
            for(String id:List.of("name","save","tab0","tab1","tab2","tab3"))assertEquals(22,l.findControl(id).h);
            for(String id:List.of("cat","inv")) {
                var slots=l.draw.stream().filter(e->id.equals(e.id)&&"slot".equals(e.kind)).toList();
                assertEquals(36,slots.size());
                int[] rows=id.equals("cat")?new int[]{114,132,150,168}:new int[]{253,271,289,311};
                for(int i=0;i<slots.size();i++) {
                    var slot=slots.get(i);assertEquals(14+18*(i%9),slot.x);assertEquals(rows[i/9],slot.y);
                    assertEquals(18,slot.w);assertEquals(18,slot.h);
                }
            }
            for(int i=0;i<4;i++) {
                var surface=l.findControl("tab"+i).tabSurface;assertNotNull(surface);
                assertEquals(5,surface.x);assertEquals(66,surface.y);assertEquals(180,surface.w);assertEquals(142,surface.h);
            }
            for(int i=0;i<3;i++)assertEquals(inventory.y+2+18*i,l.find("ext"+i).y);
            for(var card:List.of(meta,l.find("window"))) {
                var file=Path.of(root,"assets/molecularmanipulator/textures/gui/sprites/"+card.sprite.split(":",2)[1]+".png");
                if(Files.exists(Path.of(file+".mcmeta")))continue; // Shared native nine-slices retain their metadata.
                var pixels=ImageIO.read(file.toFile());
                assertEquals(Math.round((card.w+card.pad[0]+card.pad[2])*4),pixels.getWidth());
                assertEquals(Math.round((card.h+card.pad[1]+card.pad[3])*4),pixels.getHeight());
            }
        }
    }
    @Test void compactCatalystMetadataStillFitsBothFullTranslationsAtTheExistingFontSize() throws Exception {
        for(String root:ROOTS)for(String language:List.of("zh_cn","en_us"))try(var runtime=new RuntimeText(false,language)) {
            var l=layout(root,"fab_assembly_catalyst");var count=l.find("assembly_catalyst_count");var hint=l.find("assembly_catalyst_hint");
            float countWidth=l.find("meta").w-16-l.find("assembly_catalyst_count_icon").w-3;
            for(int types:new int[]{0,36,Integer.MAX_VALUE}) {
                String value=net.minecraft.network.chat.Component.translatable(count.lang,types).getString();
                float width=runtime.width(count,value);
                assertTrue(width<=countWidth+.5f,language+": "+value+" width="+width+" available="+countWidth);
            }
            String value=hint.staticText().getString();
            float width=runtime.width(hint,value);
            assertTrue(width<=hint.w+.5f,language+": "+value+" width="+width+" available="+hint.w);
        }
    }
    @Test void fabricationNetworkHintSharesTheNetworkIconVerticalCenter() throws Exception {
        for(String root:ROOTS) {
            var l=layout(root,"fab_overview");
            var label=l.findText("gui.molecularmanipulator.fab.s027");
            var icon=l.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/icons/net_")&&e.y>160)
                    .findFirst().orElseThrow();
            assertEquals(icon.y+icon.h/2,label.centerY);
        }
    }
    @Test void spawnLabelsAndMaterialHeadingShareTheIconVerticalCenter() throws Exception {
        for(String root:ROOTS) {
            var l=layout(root,"seq_center_overview");
            var first=l.findText("gui.molecularmanipulator.spawn_protection");
            var second=l.findText("gui.molecularmanipulator.spawn_protection_area");
            assertEquals(168f,first.centerY);assertEquals(181f,second.centerY);
            assertTrue(second.centerY-first.centerY>=first.size);
            var a=layout(root,"seq_center_autocraft");
            var heading=a.findText("gui.molecularmanipulator.seq.s008");
            var icon=a.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/shield_")).findFirst().orElseThrow();
            assertEquals(icon.y+icon.h/2,heading.centerY);
        }
    }
    private static void assertRect(OsqLayout.Entry entry,float x,float y,float width,float height) {
        assertNotNull(entry);assertArrayEquals(new float[]{x,y,width,height},new float[]{entry.x,entry.y,entry.w,entry.h},.01f,entry.id);
    }

    // Reuse the neighboring test's real vanilla-font context and GL-free glyph-cell recorder.
    private static final class RuntimeText implements AutoCloseable {
        private static final String OWNER="com.atir.molecularmanipulator.client.OsqRuntimeReadabilityTest";
        private final AutoCloseable context;
        private final net.minecraft.client.gui.Font font;
        private final Constructor<?> recorder;
        RuntimeText(boolean dark,String language) throws Exception {
            var type=Class.forName(OWNER+"$Context");var constructor=type.getDeclaredConstructor(boolean.class,String.class);constructor.setAccessible(true);
            context=(AutoCloseable)constructor.newInstance(dark,language);font=(net.minecraft.client.gui.Font)runtimeField(context,"font");
            recorder=Class.forName(OWNER+"$InkRecorder").getDeclaredConstructor(type);recorder.setAccessible(true);
        }
        float width(OsqLayout.Entry entry,String value) {
            return OsqFonts.widthF(font,value,OsqFonts.id(entry.fontId),entry.size);
        }
        List<Rectangle2D.Float> render(OsqLayout layout,OsqLayout.Entry entry) throws Exception {
            var graphics=(GuiGraphics)recorder.newInstance(context);var value=entry.staticText();
            layout.renderSingleLine(graphics,font,entry,value,0,0);
            var result=new ArrayList<Rectangle2D.Float>();var text=new StringBuilder();
            for(var ink:(List<?>)runtimeField(graphics,"ink")) {
                text.append(runtimeField(ink,"text"));
                assertEquals("minecraft:default",runtimeField(ink,"face").toString(),entry.lang);
                assertEquals(entry.size/font.lineHeight,(float)runtimeField(ink,"scale"),.001f,entry.lang);
                float left=(float)runtimeField(ink,"left"),top=(float)runtimeField(ink,"top");
                result.add(new Rectangle2D.Float(left,top,(float)runtimeField(ink,"right")-left,(float)runtimeField(ink,"bottom")-top));
            }
            assertEquals(value.getString().replaceAll("\\s+",""),text.toString().replaceAll("\\s+",""),entry.lang+" renders the full translation");
            return result;
        }
        Button duplicationCancel() throws Exception {
            var allocate=ResponsiveScreenTestFixture.class.getDeclaredMethod("allocate",Class.class);allocate.setAccessible(true);
            var screen=(SingularityScreen)allocate.invoke(null,SingularityScreen.class);
            var uiType=Class.forName("com.atir.molecularmanipulator.client.OsqUi");var constructor=uiType.getDeclaredConstructor(String.class);constructor.setAccessible(true);
            var ui=constructor.newInstance("sing_duplication");
            var state=Class.forName(OWNER).getDeclaredMethod("screenState",ResponsiveContainerScreen.class,Object.class,uiType,net.minecraft.client.gui.Font.class);
            state.setAccessible(true);state.invoke(null,screen,null,ui,font);ResponsiveScreenTestFixture.set(screen,"duplicationPage",true);
            var init=SingularityScreen.class.getDeclaredMethod("initReclaimControls",boolean.class);init.setAccessible(true);init.invoke(screen,true);
            return (Button)runtimeField(screen,"cancel");
        }
        @Override public void close() throws Exception {context.close();}
    }
    private static Object runtimeField(Object target,String name) throws Exception {
        var field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }

    @Test void everyCurrentCardDetailIsSelectionGatedButEmptyHintIsNot() throws Exception {
        for(String root:ROOTS) {
            var l=layout(root,"seq_center_autocraft");
            assertTrue(l.find("product").requiresSelection);
            assertTrue(l.findText("gui.molecularmanipulator.auto_craft_output_limit").requiresSelection);
            assertTrue(l.findText("gui.molecularmanipulator.seq.s008").requiresSelection);
            assertTrue(l.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/dot_")&&e.y>122&&e.x>192)
                    .allMatch(e->e.requiresSelection));
            var hint=l.findText("gui.molecularmanipulator.auto_craft_select_hint");
            assertFalse(hint.requiresSelection);assertEquals("selection_hint",hint.kind);assertEquals("center",hint.alignment);
        }
    }
}
