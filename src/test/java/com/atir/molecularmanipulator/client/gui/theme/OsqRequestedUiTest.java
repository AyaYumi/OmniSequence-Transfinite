package com.atir.molecularmanipulator.client.gui.theme;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OsqRequestedUiTest {
    private static final String[] ROOTS={"src/main/resources", "resourcepacks/OmniSequence Dark"};
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
    @Test void duplicationRateCardMatchesLeftAndStatusSharesTheIntervalRow() throws Exception {
        for(String root:ROOTS) {
            var l=layout(root,"sing_duplication");var hint=l.findText("gui.molecularmanipulator.sing.s017");
            var rate=l.find("rate");var hole=l.find("hole");
            assertEquals(hole.y,rate.y);assertEquals(hole.h,rate.h);
            assertFalse(hint.wrap);assertEquals(8,hint.size);assertTrue(hint.w>=108);
            var interval=l.findText("gui.molecularmanipulator.sing.s016");
            assertEquals(interval.centerY,hint.centerY);
            assertTrue(hint.x>=rate.x);assertTrue(hint.x+hint.w<=rate.x+rate.w);
            assertTrue(hint.centerY+hint.size/2<rate.y+rate.h);
            for(var entry:l.draw)if("text".equals(entry.type)&&entry.baseline==194) {
                assertEquals(interval.centerY,entry.centerY);
                if(entry!=hint)assertTrue(entry.x+entry.w<hint.x);
            }
            var inventory=l.find("inv");var energy=l.find("energy");
            assertEquals(energy.y,inventory.y);assertEquals(rate.y+rate.h+6,inventory.y);
            assertEquals(energy.y+energy.h,inventory.y+inventory.h);
            assertEquals(inventory.y+inventory.h+8,l.height);
            var slots=l.draw.stream().filter(e->"inv".equals(e.id)&&"slot".equals(e.kind)).toList();
            assertEquals(36,slots.size());
            assertEquals(inventory.y+6,slots.getFirst().y);
            assertEquals(inventory.y+64,slots.getLast().y);
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
            assertEquals(save.y,field.y);assertEquals(save.h,field.h);assertEquals(17,field.h);
            if(page.endsWith("patterns")) {
                var capacity=l.find("assembly_capacity");var queue=l.find("assembly_queue");
                assertEquals("center",capacity.alignment);assertEquals("center",queue.alignment);
                assertEquals(l.find("assembly_capacity_icon").y+3,capacity.centerY);
                assertEquals(l.find("assembly_queue_icon").y+3,queue.centerY);
                assertEquals("gui.molecularmanipulator.fabrication.buffer.queued",queue.lang);
            } else if(page.endsWith("catalyst")) {
                var count=l.find("assembly_catalyst_count");var hint=l.find("assembly_catalyst_hint");
                assertEquals("gui.molecularmanipulator.fabrication.buffer.catalyst_types",count.lang);
                assertEquals("gui.molecularmanipulator.fabrication.buffer.catalyst_location",hint.lang);
                assertEquals("center",count.alignment);assertEquals("center",hint.alignment);
                assertTrue(l.draw.stream().noneMatch(e->e.dynamic&&e.baseline>=180));
            }
        }
    }
    @Test void fabricationNetworkHintSharesTheNetworkIconVerticalCenter() throws Exception {
        for(String root:ROOTS) {
            var l=layout(root,"fab_overview");
            var label=l.findText("gui.molecularmanipulator.fab.s027");
            var icon=l.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/icons/net_")&&e.y>220)
                    .findFirst().orElseThrow();
            assertEquals(icon.y+icon.h/2,label.centerY);
        }
    }
    @Test void spawnLabelsAndMaterialHeadingShareTheIconVerticalCenter() throws Exception {
        for(String root:ROOTS) {
            var l=layout(root,"seq_center_overview");
            var first=l.findText("gui.molecularmanipulator.spawn_protection");
            var second=l.findText("gui.molecularmanipulator.spawn_protection_area");
            assertEquals(268.8f,first.centerY);assertEquals(first.centerY,second.centerY);
            var a=layout(root,"seq_center_autocraft");
            var heading=a.findText("gui.molecularmanipulator.seq.s008");
            var icon=a.draw.stream().filter(e->e.sprite!=null&&e.sprite.contains("/shield_")).findFirst().orElseThrow();
            assertEquals(icon.y+icon.h/2,heading.centerY);
        }
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
