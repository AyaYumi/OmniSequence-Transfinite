package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class StarComputationEngineGuiAssetsTest {
    private static final Path JAVA = Path.of("src/main/java/com/atir/molecularmanipulator");
    private static final Path ASSETS = Path.of("src/main/resources/assets/molecularmanipulator");

    @Test
    void newCountsUseUnusedSyncIdsAndActualServerCounts() throws Exception {
        String menu = Files.readString(JAVA.resolve("menu/StarComputationEngineMenu.java"));
        var matcher = Pattern.compile("@GuiSync\\((\\d+)\\) public \\w+ (\\w+)").matcher(menu);
        var ids = new HashMap<String,Integer>();
        var used = new HashSet<Integer>();
        while (matcher.find()) {
            int id = Integer.parseInt(matcher.group(1));
            assertTrue(used.add(id), "No GuiSync collision at "+id);
            ids.put(matcher.group(2),id);
        }
        assertEquals(20, used.size());
        assertEquals(15, ids.get("armillary")); assertEquals(16, ids.get("litRings"));
        assertEquals(17, ids.get("anchorsBase")); assertEquals(18, ids.get("anchorsQuantum"));
        assertEquals(19, ids.get("anchorsTerminal"));
        assertTrue(menu.contains("litRings = core.getLitRings()"));
        assertTrue(menu.contains("anchorsBase = core.getAnchorCount(0)"));
        assertTrue(menu.contains("anchorsQuantum = core.getAnchorCount(1)"));
        assertTrue(menu.contains("anchorsTerminal = core.getAnchorCount(2)"));
        String core = Files.readString(JAVA.resolve("blockentity/StarComputationEngineCoreBlockEntity.java"));
        int start = core.indexOf("public int getAnchorCount(int tier)");
        int end = core.indexOf("private long[] countAnchors()", start);
        String counts = core.substring(start,end);
        assertTrue(counts.contains("!isFormed() || !armillaryValidated"));
        assertTrue(counts.contains("countAnchors()[tier]"), "Legacy still reports its validated columns");
        assertTrue(counts.contains("EntropyArmillaryLayout.slotCount(ring)"));
        assertFalse(counts.contains("getBenefits()"));
    }

    @Test
    void bothEngineTypesUseTheSuppliedNineRingSkin() throws Exception {
        String screen = Files.readString(JAVA.resolve("client/StarComputationEngineScreen.java"));
        assertTrue(screen.contains("extends ResponsiveContainerScreen<StarComputationEngineMenu>"));
        assertTrue(screen.contains("menu.benefitLevel <= 3 ? 0 : menu.benefitLevel <= 6 ? 1 : 2"));
        assertTrue(screen.contains("\"color.\" + name + \".\" + tier()"));
        assertTrue(screen.contains("ui.layout.find(\"color.\" + name)"));
        assertTrue(screen.contains("drawRings(g);"));
        assertTrue(screen.contains("drawRingList(g);"));
        for (String old : List.of("drawHologram", "isoBox", "isoLine", "tr(\"hologram\")",
                "tr(\"engine_body\")", "tr(\"anchor_schematic\")")) {
            assertFalse(screen.contains(old), "The old column diagram must never reappear: " + old);
        }
        assertTrue(screen.contains("menu.armillary ? menu.litRings : menu.benefitLevel"));
        assertTrue(screen.contains("EntropyArmillaryLayout.slotCount(ring)"));
        assertTrue(screen.contains("color(\"hair\")"), "The current ring has the reference skin's pale track");
        assertFalse(screen.contains("24 + 8 *"));
        assertTrue(screen.contains("menu.nodeThreshold(node)"));
        assertTrue(screen.contains("menu.levelThreshold(next)"));
        assertTrue(screen.contains("menu::requestToggleOverclock"));
        assertTrue(screen.contains("menu.requestUnlock(selectedNode)"));
        assertTrue(screen.contains("OsqGuide.open(\"star_computation_engine\")"));
        assertTrue(screen.contains("BENEFIT_ICONS"));
    }

    @Test
    void onlyOrdinaryEnginesShowTheReferenceSkinsEntropyProgress() throws Exception {
        String screen = Files.readString(JAVA.resolve("client/StarComputationEngineScreen.java"));
        int start = screen.indexOf("private int currentRingFill()");
        int end = screen.indexOf("private boolean waiting()", start);
        String fill = screen.substring(start, end);
        assertTrue(fill.contains("if (menu.armillary || level >= EntropyArmillaryLayout.RINGS) return 0"),
                "Real armillaries must not invent partially upgraded rings");
        assertTrue(fill.contains("menu.levelThreshold(level)"));
        assertTrue(fill.contains("menu.levelThreshold(level + 1)"));
        assertTrue(fill.contains("menu.effectiveEntropy - from"));
        assertTrue(fill.contains("EntropyArmillaryLayout.slotCount(level)"));
        assertTrue(screen.contains("current = !menu.armillary && ring == lit"),
                "Physical ring rows remain verified/locked, ordinary engines retain the supplied progress view");
    }

    @Test
    void palettesAndTranslationsCoverAllThreeBandsWithoutMovingControls() throws Exception {
        JsonObject layout = JsonParser.parseString(Files.readString(
                ASSETS.resolve("osq_light/layout/star_computation_engine.json"))).getAsJsonObject();
        var colours = new HashMap<String,String>();
        var controls = new HashMap<String,JsonObject>();
        for (var element : layout.getAsJsonArray("draw")) {
            var entry = element.getAsJsonObject();
            if (!entry.has("id")) continue;
            String id = entry.get("id").getAsString();
            if (id.startsWith("color.")) {
                assertNull(colours.put(id.substring(6),entry.get("color").getAsString()), "Unique colour "+id);
                assertTrue(entry.get("external").getAsBoolean());
            } else if (entry.get("type").getAsString().equals("control")) controls.put(id,entry);
        }
        assertEquals("[400,300]", layout.getAsJsonArray("size").toString());
        assertEquals("[204,253,188,20]", controls.get("overclock").get("rect").toString());
        assertEquals("[204,253,188,20]", controls.get("unlock").get("rect").toString());
        assertEquals("[300,277,92,17]", controls.get("guide").get("rect").toString());
        assertEquals("#FFEFEBE0", colours.get("paper"));
        assertEquals("#FF18876D", colours.get("ring_full.bronze"));
        assertEquals("#FF8E95B4", colours.get("ring_full.violet"));
        assertEquals("#FFB8902A", colours.get("ring_full.gold"));
        for (String key : List.of("accent", "accent_fill", "feature", "ink", "ink2", "level", "holo_line")) {
            var variants = new HashSet<String>();
            for (String tier : List.of("bronze", "violet", "gold")) {
                assertNotNull(colours.get(key+"."+tier));
                variants.add(colours.get(key+"."+tier));
            }
            assertEquals(3,variants.size(),key+" follows the real capability band");
        }
        for (String language : List.of("en_us", "zh_cn")) {
            var lang = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/"+language+".json"))).getAsJsonObject();
            for (String key : List.of("armillary", "armillary_hint", "armillary_rings", "ring", "ring_locked",
                    "anchor_base", "anchor_quantum", "anchor_terminal", "tier.bronze", "tier.violet", "tier.gold")) {
                assertTrue(lang.has("gui.molecularmanipulator.star_computation."+key),language+" "+key);
            }
        }
    }
}
