package com.atir.molecularmanipulator.client.gui.theme;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.client.UiRenderRecorder;
import com.atir.molecularmanipulator.client.VanillaFontTestFixture;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class OsqRefinedLayoutTest {
    private static final String ASSETS = "assets/molecularmanipulator/";
    private static final List<String> ROOTS = List.of("src/main/resources");

    private static OsqLayout layout(String root, String name) throws Exception {
        var constructor = OsqLayout.class.getDeclaredConstructor(JsonObject.class);
        constructor.setAccessible(true);
        return constructor.newInstance(JsonParser.parseString(Files.readString(
                Path.of(root, ASSETS, "osq_light/layout/" + name + ".json"))).getAsJsonObject());
    }

    @Test void directionButtonsCannotClaimUnderlyingCacheOrTooltipLabels() throws Exception {
        for (String root : ROOTS) for (String name : List.of("fab_port_item", "fab_port_fluid", "seq_autocrafter")) {
            var layout = layout(root, name);
            var popup = layout.find("dirs");
            for (String id : List.of("d北", "d西", "d东", "d南", "d上", "d下")) {
                var button = layout.findControl(id);
                assertSame(popup, layout.layerOf(button));
                assertTrue(layout.draw.stream().filter(e -> layout.owns(button, e))
                        .noneMatch(e -> "text".equals(e.type)), name + "/" + id);
            }
        }
    }

    @Test void directionButtonsKeepVisiblePaddingInsideTheirPopupWithoutShrinking() throws Exception {
        for (String root : ROOTS) for (String name : List.of("fab_port_item", "fab_port_fluid", "seq_autocrafter")) {
            var page = layout(root, name);
            var popup = page.find("dirs");
            var image = UiRenderRecorder.surface(popup);
            assertEquals(Math.round((popup.w + popup.pad[0] + popup.pad[2]) * 4), image.getWidth());
            assertEquals(Math.round((popup.h + popup.pad[1] + popup.pad[3]) * 4), image.getHeight());
            int top = -1, bottom = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                int pixel = image.getRGB(image.getWidth() / 2, y);
                if ((pixel >>> 24) >= 230 && (pixel >> 16 & 255) >= 230
                        && (pixel >> 8 & 255) >= 230 && (pixel & 255) >= 230) {
                    if (top < 0) top = y;
                    bottom = y;
                }
            }
            assertTrue(top >= 0 && bottom > top, name + " has a visible inner frame");
            float visibleTop = popup.y - popup.pad[1] + top / 4f;
            float visibleBottom = popup.y - popup.pad[1] + bottom / 4f;
            for (String id : List.of("d北", "d西", "d东", "d南", "d上", "d下")) {
                var button = page.findControl(id);
                assertEquals(22, button.w, name + "/" + id);
                assertEquals(22, button.h, name + "/" + id);
                assertTrue(button.x >= popup.x + 7 && button.x + button.w <= popup.x + popup.w - 7, name + "/" + id);
                assertTrue(button.y >= popup.y + 7 && button.y + button.h <= popup.y + popup.h - 7,
                        name + "/" + id + " must not touch the inner frame");
                assertTrue(button.y >= visibleTop + 7 && button.y + button.h <= visibleBottom - 7,
                        name + "/" + id + " needs padding to the actual painted frame, not its shadow");
            }
        }
    }

    @Test void ownershipRequiresSourceOrderAndTheSameFloatingLayer() throws Exception {
        var layout = layout(ROOTS.getFirst(), "fab_research");
        var control = new OsqLayout.Entry();
        control.type = "sprite"; control.kind = "ctl"; control.x = 0; control.y = 0; control.w = 100; control.h = 30;
        var before = label(10, 15);
        var after = label(10, 15);
        var popup = new OsqLayout.Entry();
        popup.kind = "tip"; popup.x = 0; popup.y = 0; popup.w = 100; popup.h = 30;
        var floating = label(10, 15);
        layout.draw.clear(); layout.draw.addAll(List.of(before, control, after, popup, floating));
        assertFalse(layout.owns(control, before));
        assertTrue(layout.owns(control, after));
        assertFalse(layout.owns(control, floating));
        assertSame(popup, layout.layerOf(floating));
        var runtimeControl = new OsqLayout.Entry();
        runtimeControl.x = 0; runtimeControl.y = 0; runtimeControl.w = 100; runtimeControl.h = 30;
        assertNull(layout.layerOf(runtimeControl));
        assertFalse(layout.owns(runtimeControl, floating));
    }

    @Test void movedControlsCannotClaimStatusTextAtItsOldBindingAddress() throws Exception {
        for(String root:ROOTS)for(String name:List.of("fab_research","fab_research_detail")){
            var page=layout(root,name);
            var status=page.findText("gui.molecularmanipulator.research.order_state.submitted");
            var row=page.findControl("proj4");
            assertTrue(status.baseline>=row.y&&status.baseline<=row.y+row.h);
            assertTrue(status.centerY>row.y+row.h);
            assertFalse(page.owns(row,status),"Presentation, not the old binding address, decides implicit ownership");
        }
    }

    @Test void controlsHaveNoExteriorPaddingButGlassCardsKeepTheirShadows() throws Exception {
        for (String root : ROOTS) {
            try (var paths = Files.list(Path.of(root, ASSETS, "osq_light/layout"))) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                    var layout = layout(root, path.getFileName().toString().replace(".json", ""));
                    for (var entry : layout.draw) if ("ctl".equals(entry.kind) || "edge".equals(entry.kind))
                        assertArrayEquals(new int[4], entry.pad, path + ": " + entry.id);
                }
            }
            var card = layout(root, "fab_overview").find("quantum");
            assertTrue(card.pad[3] > 0);
        }
        for (String name : List.of("button_normal", "button_hover", "tab_active", "row_normal", "key13_disabled", "edge_key", "field_normal"))
            assertArrayEquals(new int[4], OsqSprites.pad("osq_light/fab/" + name));
    }

    @Test void iconCenterUsesVisibleAlphaRatherThanTransparentMargins() {
        var image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        assertEquals(new OsqIconInk.Center(.5f, .5f), OsqIconInk.measure(image));
        for (int y = 4; y < 16; y++) for (int x = 2; x < 8; x++) image.setRGB(x, y, 0xFFFFFFFF);
        assertEquals(new OsqIconInk.Center(.25f, .5f, 1, .3f), OsqIconInk.measure(image));
    }

    @Test void stateIconReplacementsFitTheirOwnAspectInsteadOfStretchingIntoTheOriginalCanvas() {
        var square = OsqIconInk.measure(new BufferedImage(28, 28, BufferedImage.TYPE_INT_ARGB));
        assertEquals(1, square.aspect());
        assertEquals(7, square.fitWidth(7, 8));
        assertEquals(7, square.fitHeight(7, 8), "A square replacement sprite must not become seven by eight");
        var portrait = OsqIconInk.measure(new BufferedImage(7, 8, BufferedImage.TYPE_INT_ARGB));
        assertEquals(7f/8, portrait.aspect());
        assertEquals(7, portrait.fitWidth(8, 8));
        assertEquals(8, portrait.fitHeight(8, 8));
    }

    @Test void importedTextBoxOffsetsDoNotChangeBindingAddresses() {
        var entry = label(130.98f, 48.5f);
        entry.drawX = 124f; entry.w = 38; entry.alignment = "center";
        assertEquals(130.98f, entry.x);
        assertEquals(133f, OsqLayout.textX(entry, 20));
    }

    @Test void defaultProviderChainCoversLiveNumbersEngineLabelsAndAllLocalizedChinese() throws Exception {
        try (var runtime = new VanillaFontTestFixture("zh_cn")) {
            var face = VanillaFontTestFixture.DEFAULT_FONT;
            for (int codepoint : "第 1/8 页 · 9223372036854775807 格/次 · 有效算力 · 星讯流".codePoints().toArray())
                if (!Character.isWhitespace(codepoint))
                    assertNotNull(runtime.findGlyph(face, codepoint), "minecraft:default: U+" + Integer.toHexString(codepoint));
            var locale = JsonParser.parseString(Files.readString(Path.of(ROOTS.getFirst(), ASSETS, "lang/zh_cn.json"))).getAsJsonObject();
            for (var value : locale.entrySet()) for (int cp : value.getValue().getAsString().codePoints().toArray())
                if (cp >= 0x2E80 && cp <= 0x9FFF)
                    assertNotNull(runtime.findGlyph(face, cp), value.getKey() + " U+" + Integer.toHexString(cp));
        }
    }

    @Test void refinedSurfacesUseSharedFramesAtTheirLiveDimensions() throws Exception {
        var layout = layout(ROOTS.getFirst(), "fab_overview");
        for (var entry : List.of(layout.find("window"), layout.find("quantum"))) {
            assertTrue(entry.sprite.startsWith("molecularmanipulator:osq_light/common/glass_"));
            var file = Path.of(ROOTS.getFirst(), ASSETS, "textures/gui/sprites/" + entry.sprite.split(":", 2)[1] + ".png");
            var source = ImageIO.read(file.toFile());
            assertEquals(64, source.getWidth()); assertEquals(64, source.getHeight());
            var image = UiRenderRecorder.surface(entry);
            assertEquals(Math.round((entry.w + entry.pad[0] + entry.pad[2]) * 4), image.getWidth());
            assertEquals(Math.round((entry.h + entry.pad[1] + entry.pad[3]) * 4), image.getHeight());
            var metadata = JsonParser.parseString(Files.readString(Path.of(file + ".mcmeta"))).getAsJsonObject();
            assertEquals("nine_slice", metadata.getAsJsonObject("gui").getAsJsonObject("scaling").get("type").getAsString());
        }
    }

    @Test void smallCopyUsesExplicitReadableSizesAndVanillaDefaultWithoutChangingBindingAddresses() throws Exception {
        for (String root : ROOTS) {
            try (var files = Files.list(Path.of(root, ASSETS, "osq_light/layout"))) {
                for (var file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    var page = layout(root, file.getFileName().toString().replace(".json", ""));
                    for (var entry : page.draw) if ("text".equals(entry.type)) {
                        assertTrue(entry.size >= 7.5f, file + ": " + entry.text);
                        assertEquals("minecraft:default", entry.fontId, file + ": " + entry.text);
                    }
                }
            }
            var research = layout(root, "fab_research");
            assertEquals(7.5f, research.findText("gui.molecularmanipulator.fab.s037").size);
            var fluid = layout(root, "fab_port_fluid");
            var amount = fluid.draw.stream().filter(e -> "text".equals(e.type) && e.x == 96 && e.baseline == 89).findFirst().orElseThrow();
            var unit = fluid.draw.stream().filter(e -> "text".equals(e.type) && "mB".equals(e.text)).findFirst().orElseThrow();
            assertEquals(141.59f, unit.x, "Source coordinates remain live binding addresses");
            assertEquals(80, amount.w, "The live capacity and its unit share one available line");
            var screen=Files.readString(Path.of("src/main/java/com/atir/molecularmanipulator/client/MatterFabricationPortScreen.java"));
            assertTrue(screen.contains("%,d mB"));
            assertTrue(screen.contains("ui.hide(e->\"text\".equals(e.type)&&\"mB\".equals(e.text))"));
        }
    }

    @Test void enlargedPaginationKeepsItsPhysicalTextBoxBetweenTheArrows() throws Exception {
        for (String root : ROOTS) for (String name : List.of("fab_assembly_input", "seq_autocrafter_config",
                "seq_center_overview", "seq_center_autocraft", "seq_center_colors", "seq_manipulator", "sing_collection")) {
            var page = layout(root, name);
            var label = page.findText("gui.molecularmanipulator.page");
            var previous = page.findControl("prev");var next = page.findControl("next");
            float x = OsqLayout.textX(label, label.w);
            assertTrue(x >= previous.x + previous.w + 1.9f, name);
            assertTrue(x + label.w <= next.x - 1.9f, name);
            assertEquals(previous.y + previous.h / 2, label.centerY, .01f, name);
            assertEquals((previous.x + previous.w + next.x) / 2, x + label.w / 2, .01f, name);
        }
    }

    @Test void largerResearchCopyUsesFreeSpaceInsteadOfBeingShrunkBackToItsOldSize() throws Exception {
        try (var runtime = new VanillaFontTestFixture("zh_cn")) {
            for (String root : ROOTS) {
                var page = layout(root, "fab_research");
                for (String key : List.of("gui.molecularmanipulator.fab.s037", "gui.molecularmanipulator.fab.s058",
                        "gui.molecularmanipulator.fab.s059")) {
                    var text = page.findText(key);
                    float width = OsqFonts.widthF(runtime.font, text.staticText().getString(), OsqFonts.id(text.fontId), text.size);
                    assertTrue(width <= text.w + 1, key + " width=" + width + " available=" + text.w);
                }
                var row = page.findControl("proj0");
                float width = OsqFonts.widthF(runtime.font, "一阶：AE 材料构筑 · 0/9", VanillaFontTestFixture.DEFAULT_FONT, 7.5f);
                assertTrue(width <= row.w - 12, "proj0 width=" + width + " available=" + (row.w - 12));
            }
        }
    }

    @Test void centerOverviewKeepsLiveCapacityAndProductionInsteadOfDecorativeComponentCounts() throws Exception {
        for (String root : ROOTS) {
            var center = layout(root, "seq_center_overview");
            for (String key : List.of("seq.s013", "seq.s014", "fab.s016", "seq.s015", "seq.s016"))
                assertNull(center.findText("gui.molecularmanipulator." + key), root + ": " + key);
            assertTrue(center.draw.stream().noneMatch(e -> e.sprite != null && e.sprite.contains("/legend_")));
            for (String key : List.of("formed", "overview_patterns", "overview_auto_craft", "overview_pipeline", "overview_pending"))
                assertNotNull(center.findText("gui.molecularmanipulator." + key), root + ": " + key);
            for (float[] address : new float[][]{{208.5f, 87}, {217.63f, 87}, {249.58f, 87},
                    {200.5f, 181}, {211.31f, 181}, {254, 177}, {301.09f, 177}, {352, 177}})
                assertEquals(1L, center.dynamicTexts().stream()
                        .filter(e -> e.x == address[0] && e.baseline == address[1]).count(),
                        root + ": live binding at " + address[0] + ", " + address[1]);
            assertTrue(center.dynamicTexts().stream().noneMatch(e -> e.baseline == 115.5f || e.baseline == 114.8f),
                    "Remove legend demo values along with their labels and icons");
        }
    }

    private static OsqLayout.Entry label(float x, float baseline) {
        var entry = new OsqLayout.Entry();
        entry.type = "text"; entry.x = x; entry.baseline = baseline;
        return entry;
    }
}
