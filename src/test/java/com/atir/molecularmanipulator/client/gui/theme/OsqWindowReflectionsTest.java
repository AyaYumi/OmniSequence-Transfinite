package com.atir.molecularmanipulator.client.gui.theme;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.client.UiRenderRecorder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Shared material resources and live frame geometry; reflections use one fixed layout-space field. */
class OsqWindowReflectionsTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/molecularmanipulator");

    @Test void allTwentyFiveLayoutsAndEveryNestedTabFaceUseTheSharedMaterials() throws Exception {
        var files = layouts();
        assertEquals(25, files.size());
        int glass = 0, connected = 0, single = 0;
        for (var file : files) {
            var layout = json(file);
            assertEquals("window", surface(layout, "window").get("material").getAsString());
            for (var element : layout.getAsJsonArray("draw")) {
                var entry = element.getAsJsonObject();
                if (isFace(entry)) {
                    assertFace(entry, feature(layout, entry.get("id").getAsString()));
                    glass++;
                }
                for (String key : List.of("tab_surface", "tab_single_surface")) {
                    if (!entry.has(key)) continue;
                    var tab = entry.getAsJsonObject(key);
                    assertTrue(isFace(tab), file + " " + key);
                    assertFace(tab, feature(layout, entry.get("tab_panel").getAsString()));
                    if (key.equals("tab_surface")) connected++;
                    else single++;
                }
            }
        }
        assertEquals(136, glass, "Include popups and externally drawn handheld rows");
        assertEquals(42, connected, "Every selected tab contour, including the dark feature tab");
        assertEquals(8, single, "Compact variants use the same shared frame at their own dimensions");
    }

    @Test void sharedReflectionKeepsItsFixedSizeSoftBandsAndApprovedStrength() throws Exception {
        var image = ImageIO.read(ASSETS.resolve("textures/gui/sprites/osq_light/common/glass_reflection.png").toFile());
        assertEquals(128, image.getWidth()); assertEquals(256, image.getHeight());
        var alphas = new HashSet<Integer>();
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int pixel = image.getRGB(x, y), alpha = pixel >>> 24;
            assertEquals(0xFFFFFF, pixel & 0xFFFFFF);
            assertTrue(alpha <= 48, "Retain the approved 48/255 maximum reflection alpha");
            alphas.add(alpha);
        }
        assertTrue(alphas.containsAll(List.of(0, 48)));
        assertTrue(alphas.size() > 20, "Soft transitions retain intermediate opacity values");
        for (int y : new int[]{0, 32, 64, 128, 192, 255}) {
            int bands = 0, narrow = 0, broad = 0, start = -1;
            for (int x = 0; x <= image.getWidth(); x++) {
                int alpha = x == image.getWidth() ? 0 : image.getRGB(x, y) >>> 24;
                if (alpha >= 24 && start < 0) start = x;
                if (alpha < 24 && start >= 0) {
                    int width = x - start;
                    if (width <= 6) narrow++; else broad++;
                    bands++; start = -1;
                }
            }
            assertEquals(4, bands); assertTrue(narrow >= 1 && broad >= 1, "Keep both broad and narrow diagonal bands");
        }
        var layer = source("client/gui/theme/OsqReflectionLayer");
        assertTrue(layer.contains("TILE_WIDTH=256,TILE_HEIGHT=512"));
        assertTrue(layer.contains("Math.floorMod(layoutX+dx,TILE_WIDTH)"));
        assertTrue(layer.contains("Math.floorMod(layoutY+dy,TILE_HEIGHT)"));
        var surface = source("client/gui/theme/OsqMaterialSurface");
        assertTrue(surface.contains("if(\"card\".equals(entry.material))return;"), "Ordinary cards transmit the window field");
        assertTrue(surface.contains("Math.round(originX+left),Math.round(originY+top)"));
        assertTrue(surface.contains("\"feature\".equals(entry.material)?.30f:1"));
    }

    @Test void expandedAutoCrafterDirectionsResizeTheSharedWindowAndMoveInventory() throws Exception {
        var layout = layout("seq_autocrafter");
        assertFace(variant(layout, "window", 0, 14), false);
        assertFace(variant(layout, "inv", 14, 0), false);
    }

    @Test void compactOrreryRecipesResizeSharedFramesAndMoveInventoryAndNetwork() throws Exception {
        var layout = layout("star_computation_engine");
        assertFace(variant(layout, "window", 0, -27), false);
        assertFace(variant(layout, "armillary", 0, -27), false);
        assertFace(variant(layout, "inv", -27, 0), false);
        assertFace(variant(layout, "net", -27, 0), false);
    }

    @Test void sourceScreensKeepSharedSpriteIdsWhileChangingLiveGeometry() throws Exception {
        String autoCrafter = source("client/MolecularAutoCrafterScreen");
        assertTrue(autoCrafter.contains("DIRECTIONS_EXTRA_HEIGHT=14;"));
        assertTrue(autoCrafter.contains("ui=newOsqUi(\"seq_autocrafter\");configureDirectionPage();"));
        assertTrue(autoCrafter.contains("if(!directionsOpen)return;"));
        assertTrue(autoCrafter.contains("window.h+=DIRECTIONS_EXTRA_HEIGHT;"));
        assertTrue(autoCrafter.contains("if(\"inv\".equals(entry.id)||entry.external){entry.y+=DIRECTIONS_EXTRA_HEIGHT;"));
        assertFalse(autoCrafter.contains(".sprite+="));

        String orrery = source("client/StarComputationEngineScreen");
        assertTrue(orrery.contains("RECIPE_COMPACT=27;"));
        assertTrue(orrery.contains("configureRecipePage();imageWidth=ui.layout.width;"
                + "imageHeight=ui.layout.height-(recipesPage?RECIPE_COMPACT:0);"));
        assertTrue(orrery.contains("if(!recipesPage)return;"));
        assertTrue(orrery.contains("for(Stringid:List.of(\"window\",\"armillary\")){varsurface=ui.layout.find(id);surface.h-=RECIPE_COMPACT;"));
        assertTrue(orrery.contains("List.of(\"inv\",\"net\",\"unlock\",\"prerequisites\",\"guide\").contains(entry.id)){entry.y-=RECIPE_COMPACT;"));
        assertFalse(orrery.contains(".sprite+="));

        for (String page : List.of("seq_autocrafter", "star_computation_engine")) {
            int inventorySlots = 0;
            for (var element : layout(page).getAsJsonArray("draw")) {
                var entry = element.getAsJsonObject();
                if (!entry.has("k") || !entry.get("k").getAsString().equals("slot")
                        || !entry.has("sprite") || entry.get("sprite").isJsonNull()) continue;
                String sprite = entry.get("sprite").getAsString();
                assertTrue(sprite.endsWith("/slot") || sprite.endsWith("/slot_ghost"), page);
                if (entry.get("id").getAsString().equals("inv")) inventorySlots++;
            }
            assertEquals(36, inventorySlots, page);
        }
    }

    private static void assertFace(JsonObject face, boolean feature) throws Exception {
        String material = face.get("material").getAsString();
        assertEquals(feature, material.equals("feature"), face.toString());
        assertTrue(List.of("window", "card", "feature", "popup").contains(material));
        assertTrue(face.get("sprite").getAsString().startsWith("molecularmanipulator:osq_light/common/glass_"));
        BufferedImage pixels = UiRenderRecorder.surface(face);
        var rect = face.getAsJsonArray("rect");
        int width = rect.get(2).getAsInt(), height = rect.get(3).getAsInt();
        assertEquals((width + pad(face, 0) + pad(face, 2)) * 4, pixels.getWidth());
        assertEquals((height + pad(face, 1) + pad(face, 3)) * 4, pixels.getHeight());
        int expected = material.equals("card") ? 0x10FFFFFF : feature ? 0xFF55536C : 0xFFCBCCD4;
        assertEquals(expected, pixels.getRGB(pixels.getWidth() / 2, pixels.getHeight() / 2));
    }

    private static boolean feature(JsonObject layout, String panel) {
        return switch (layout.get("screen").getAsString()) {
            case "building_access_terminal" -> panel.equals("panel.5");
            case "fab_overview" -> panel.equals("progress");
            case "omni_core", "omni_core_legacy" -> panel.equals("compute");
            case "seq_center_overview" -> panel.equals("prod");
            case "sing_compact" -> panel.equals("info");
            case "sing_duplication" -> panel.equals("rate");
            case "sing_overview" -> panel.equals("inspect");
            case "star_chart_seal" -> panel.equals("target");
            case "star_computation_engine" -> List.of("ov_feature", "rc_power", "ov_anchors").contains(panel);
            default -> false;
        };
    }

    private static JsonObject variant(JsonObject layout, String id, int dy, int dh) {
        var entry = surface(layout, id).deepCopy();
        var rect = entry.getAsJsonArray("rect");
        rect.set(1, new JsonPrimitive(rect.get(1).getAsInt() + dy));
        rect.set(3, new JsonPrimitive(rect.get(3).getAsInt() + dh));
        return entry;
    }

    private static int pad(JsonObject entry, int edge) {
        return entry.has("pad") ? entry.getAsJsonArray("pad").get(edge).getAsInt() : 0;
    }

    private static boolean isFace(JsonObject entry) { return entry.has("material"); }

    private static JsonObject surface(JsonObject layout, String id) {
        return layout.getAsJsonArray("draw").asList().stream().map(e -> e.getAsJsonObject())
                .filter(e -> isFace(e) && e.has("id") && id.equals(e.get("id").getAsString())).findFirst().orElseThrow();
    }

    private static List<Path> layouts() throws Exception {
        try (var files = Files.list(ASSETS.resolve("osq_light/layout"))) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static JsonObject layout(String page) throws Exception { return json(ASSETS.resolve("osq_light/layout/" + page + ".json")); }
    private static JsonObject json(Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
    private static String source(String file) throws Exception {
        return Files.readString(Path.of("src/main/java/com/atir/molecularmanipulator/" + file + ".java"))
                .replaceAll("(?m)//.*$", "").replaceAll("\\s+", "");
    }
}
