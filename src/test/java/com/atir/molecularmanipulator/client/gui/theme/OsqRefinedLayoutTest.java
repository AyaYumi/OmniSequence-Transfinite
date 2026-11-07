package com.atir.molecularmanipulator.client.gui.theme;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class OsqRefinedLayoutTest {
    private static final String ASSETS = "assets/molecularmanipulator/";
    private static final List<String> ROOTS = List.of("src/main/resources", "resourcepacks/OmniSequence Dark");

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
            for (String prefix : List.of("osq_light/fab/", "osq_dark/fab/"))
                assertArrayEquals(new int[4], OsqSprites.pad(prefix + name));
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
        assertEquals(7, square.fitHeight(7, 8), "A square dark pause sprite must not become seven by eight");
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

    @Test void numericAndEngineProviderChainsCoverLiveChineseLabels() throws Exception {
        var root = Path.of(ROOTS.getFirst(), ASSETS, "font");
        for (String id : List.of("osq_inter_500_6_5", "osq_intertight_600_6_4", "osq_jetbrainsmono_600_6", "osq_entropy_600_5_4")) {
            var definition = JsonParser.parseString(Files.readString(root.resolve(id + ".json"))).getAsJsonObject();
            var faces = new ArrayList<Font>();
            for (var item : definition.getAsJsonArray("providers")) {
                var provider = item.getAsJsonObject();
                if (provider.get("type").getAsString().endsWith("ttf"))
                    faces.add(Font.createFont(Font.TRUETYPE_FONT,
                            root.resolve(provider.get("file").getAsString().split(":", 2)[1]).toFile()));
            }
            for (int codepoint : "第 1/8 页 · 9223372036854775807 格/次 · 有效算力 · 星讯流".codePoints().toArray())
                if (!Character.isWhitespace(codepoint))
                    assertTrue(faces.stream().anyMatch(face -> face.canDisplay(codepoint)), id + ": U+" + Integer.toHexString(codepoint));
        }
        var locale = JsonParser.parseString(Files.readString(Path.of(ROOTS.getFirst(), ASSETS, "lang/zh_cn.json"))).getAsJsonObject();
        for (String weight : List.of("medium", "semibold", "bold", "black")) {
            Font face = Font.createFont(Font.TRUETYPE_FONT, root.resolve("osq_sc_" + weight + ".ttf").toFile());
            for (var value : locale.entrySet()) for (int cp : value.getValue().getAsString().codePoints().toArray())
                if (cp >= 0x2E80 && cp <= 0x9FFF) assertTrue(face.canDisplay(cp), weight + ": " + value.getKey() + " U+" + Integer.toHexString(cp));
        }
    }

    @Test void refinedSurfaceIsAnIndividualHighDensitySprite() throws Exception {
        var layout = layout(ROOTS.getFirst(), "fab_overview");
        for (var entry : List.of(layout.find("window"), layout.find("quantum"))) {
            assertTrue(entry.sprite.contains("/glass/"));
            var file = Path.of(ROOTS.getFirst(), ASSETS, "textures/gui/sprites/" + entry.sprite.split(":", 2)[1] + ".png");
            var image = ImageIO.read(file.toFile());
            assertEquals(Math.round((entry.w + entry.pad[0] + entry.pad[2]) * 4), image.getWidth());
            assertEquals(Math.round((entry.h + entry.pad[1] + entry.pad[3]) * 4), image.getHeight());
            assertFalse(Files.exists(Path.of(file + ".mcmeta")), "Fixed artwork must not be nine-sliced");
        }
    }

    @Test void smallCopyUsesLargerProvidersInBothThemesWithoutChangingBindingAddresses() throws Exception {
        for (String root : ROOTS) {
            try (var files = Files.list(Path.of(root, ASSETS, "osq_light/layout"))) {
                for (var file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    var page = layout(root, file.getFileName().toString().replace(".json", ""));
                    for (var entry : page.draw) if ("text".equals(entry.type)) {
                        assertTrue(entry.size >= 7.5f, file + ": " + entry.text);
                        var provider = JsonParser.parseString(Files.readString(Path.of(ROOTS.getFirst(), ASSETS,
                                "font/" + entry.fontId.split(":", 2)[1] + ".json"))).getAsJsonObject()
                                .getAsJsonArray("providers").get(0).getAsJsonObject();
                        assertEquals(entry.size, provider.get("size").getAsFloat(), .01f, file + ": " + entry.fontId);
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
        Font face = Font.createFont(Font.TRUETYPE_FONT,
                Path.of(ROOTS.getFirst(), ASSETS, "font/osq_sc_semibold.ttf").toFile()).deriveFont(7.5f);
        var metrics = new java.awt.font.FontRenderContext(null, true, true);
        for (String root : ROOTS) {
            var page = layout(root, "fab_research");
            for (String key : List.of("gui.molecularmanipulator.fab.s037", "gui.molecularmanipulator.fab.s058",
                    "gui.molecularmanipulator.fab.s059")) {
                var text = page.findText(key);
                assertTrue(face.getStringBounds(text.text, metrics).getWidth() <= text.w + 1, key);
            }
            var row = page.findControl("proj0");
            assertTrue(face.getStringBounds("一阶：AE 材料构筑 · 0/9", metrics).getWidth() <= row.w - 12);
            var center = layout(root, "seq_center_overview");
            var total = center.findText("gui.molecularmanipulator.seq.s016");
            assertTrue(total.w >= 7.5f);
            assertTrue(OsqLayout.textX(total, total.w) + total.w <= center.width - 8);
        }
    }

    private static OsqLayout.Entry label(float x, float baseline) {
        var entry = new OsqLayout.Entry();
        entry.type = "text"; entry.x = x; entry.baseline = baseline;
        return entry;
    }
}
