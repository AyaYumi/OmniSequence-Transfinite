package com.atir.molecularmanipulator.client.gui.theme;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Inspect production PNGs: every material surface is a crop of one window-space light field. */
class OsqWindowReflectionsTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/molecularmanipulator");
    private static final int DENSITY = 4;
    private static final int INTERIOR_MARGIN = 6 * DENSITY;
    private static final double[][] BANDS = {{114, 44, .23}, {169, 18, .16}, {343, 42, .23},
            {396, 16, .16}, {538, 40, .22}, {588, 16, .16}};

    @Test void allTwentyFiveLayoutsAndEveryNestedTabFaceSampleTheSameWindowField() throws Exception {
        var files = layouts();
        assertEquals(25, files.size());
        int glass = 0, connected = 0, single = 0;
        for (var file : files) {
            var layout = json(file);
            var window = surface(layout, "window");
            var windowPixels = image(window);
            for (var element : layout.getAsJsonArray("draw")) {
                var entry = element.getAsJsonObject();
                if (isFace(entry)) {
                    assertFace(layout, entry, feature(layout, entry.get("id").getAsString()), window, windowPixels);
                    glass++;
                }
                for (String key : List.of("tab_surface", "tab_single_surface")) {
                    if (!entry.has(key)) continue;
                    var tab = entry.getAsJsonObject(key);
                    assertTrue(isFace(tab), file + " " + key);
                    boolean feature = feature(layout, entry.get("tab_panel").getAsString());
                    var pixels = assertFace(layout, tab, feature, window, windowPixels);
                    assertTabHeader(layout, entry, tab, pixels, feature);
                    if (key.equals("tab_surface")) connected++;
                    else single++;
                }
            }
        }
        assertEquals(136, glass, "Include popups and externally drawn handheld rows, not just ordinary cards");
        assertEquals(42, connected, "Every selected tab union must be checked, including the dark feature tab");
        assertEquals(8, single, "Check the dedicated single-block silhouettes as well as their full-width parents");
    }

    @Test void everyWindowHasThreeBroadAndThreeNarrowSoftBandsRatherThanCardLocalStripes() throws Exception {
        for (var file : layouts()) {
            var layout = json(file);
            var window = surface(layout, "window");
            var pixels = image(window);
            var size = layout.getAsJsonArray("size");
            double width = size.get(0).getAsDouble(), height = size.get(1).getAsDouble();
            double scale = fieldScale(layout);
            for (double[] band : BANDS) {
                // The window diagonal reaches every band, even on tall, narrow layouts.
                double fraction = band[0] / (400 + .70 * 344);
                double x = width * fraction, y = height * fraction;
                int peak = assertWindowPoint(layout, window, pixels, x, y);
                int edge = assertWindowPoint(layout, window, pixels, x + band[1] / 2 * scale, y);
                int valley = assertWindowPoint(layout, window, pixels, x - (band[1] / 2 + 12) * scale, y);
                for (int shift : new int[]{0, 8, 16}) {
                    int high = peak >>> shift & 255, middle = edge >>> shift & 255, low = valley >>> shift & 255;
                    String message = file + " band " + band[0] + " channel " + shift;
                    assertTrue(high - low >= 5 && high - low <= 15, message + ": retain a restrained reflection");
                    assertTrue(low < middle && middle < high, message + ": retain a soft shoulder, not a hard stripe");
                }
            }
        }
    }

    @Test void expandedAutoCrafterDirectionsUseATallerWindowAndInventoryAtItsLivePosition() throws Exception {
        var layout = layout("seq_autocrafter");
        var window = variant(layout, "window", "_directions", 0, 14);
        var pixels = image(window);
        assertFace(layout, window, false, window, pixels);
        assertFace(layout, variant(layout, "inv", "_directions", 14, 0), false, window, pixels);
    }

    @Test void compactOrreryRecipesUseShorterFramesAndMovedInventoryAndNetworkCrops() throws Exception {
        var layout = layout("star_computation_engine");
        var window = variant(layout, "window", "_recipes", 0, -27);
        var pixels = image(window);
        assertFace(layout, window, false, window, pixels);
        assertFace(layout, variant(layout, "armillary", "_recipes", 0, -27), false, window, pixels);
        assertFace(layout, variant(layout, "inv", "_recipes", -27, 0), false, window, pixels);
        assertFace(layout, variant(layout, "net", "_recipes", -27, 0), false, window, pixels);
    }

    @Test void sourceScreensSelectLiveGlassVariantsWithoutRenamingNativeSlotSprites() throws Exception {
        String autoCrafter = screenSource("MolecularAutoCrafterScreen");
        assertTrue(autoCrafter.contains("DIRECTIONS_EXTRA_HEIGHT=14;"));
        assertTrue(autoCrafter.contains("ui=newOsqUi(\"seq_autocrafter\");configureDirectionPage();"));
        assertTrue(autoCrafter.contains("if(!directionsOpen)return;"));
        assertTrue(autoCrafter.contains("window.h+=DIRECTIONS_EXTRA_HEIGHT;window.sprite+=\"_directions\";"));
        assertTrue(autoCrafter.contains("if(\"inv\".equals(entry.id)||entry.external){entry.y+=DIRECTIONS_EXTRA_HEIGHT;"
                + "if(entry.sprite!=null&&entry.sprite.contains(\"/glass/\"))entry.sprite+=\"_directions\";"),
                "The inventory card moves with its slots, but only the glass card gets a different sprite");
        assertEquals(2, autoCrafter.split("\"_directions\"", -1).length - 1);

        String orrery = screenSource("StarComputationEngineScreen");
        assertTrue(orrery.contains("RECIPE_COMPACT=27;"));
        assertTrue(orrery.contains("configureRecipePage();imageWidth=ui.layout.width;"
                + "imageHeight=ui.layout.height-(recipesPage?RECIPE_COMPACT:0);"));
        assertTrue(orrery.contains("if(!recipesPage)return;"));
        assertTrue(orrery.contains("for(Stringid:List.of(\"window\",\"armillary\")){varsurface=ui.layout.find(id);"
                + "surface.h-=RECIPE_COMPACT;surface.sprite+=\"_recipes\";"));
        assertTrue(orrery.contains("List.of(\"inv\",\"net\",\"unlock\",\"prerequisites\",\"guide\").contains(entry.id)){"
                + "entry.y-=RECIPE_COMPACT;"
                + "if(entry.sprite!=null&&entry.sprite.contains(\"/glass/\"))entry.sprite+=\"_recipes\";"),
                "Do not append a glass suffix to inventory slots, controls, or other moved content");
        assertEquals(2, orrery.split("\"_recipes\"", -1).length - 1);

        for (String page : List.of("seq_autocrafter", "star_computation_engine")) {
            int inventorySlots = 0;
            for (var element : layout(page).getAsJsonArray("draw")) {
                var entry = element.getAsJsonObject();
                if (!entry.has("k") || !entry.get("k").getAsString().equals("slot")
                        || !entry.has("sprite") || entry.get("sprite").isJsonNull()) continue;
                String sprite = entry.get("sprite").getAsString();
                assertTrue(sprite.endsWith("/slot") || sprite.endsWith("/slot_ghost"),
                        page + ": keep native inventory and pattern slot sprites");
                if (entry.get("id").getAsString().equals("inv")) inventorySlots++;
            }
            assertEquals(36, inventorySlots, page + ": the shared inv id also belongs to all native inventory slots");
        }
    }

    private static BufferedImage assertFace(JsonObject layout, JsonObject face, boolean feature,
            JsonObject window, BufferedImage windowPixels) throws Exception {
        var pixels = face == window ? windowPixels : image(face);
        var rect = face.getAsJsonArray("rect");
        String name = layout.get("screen").getAsString() + " " + face.get("sprite").getAsString();
        assertEquals(Math.round((rect.get(2).getAsDouble() + pad(face, 0) + pad(face, 2)) * DENSITY), pixels.getWidth(), name);
        assertEquals(Math.round((rect.get(3).getAsDouble() + pad(face, 1) + pad(face, 3)) * DENSITY), pixels.getHeight(), name);
        int checked = 0;
        for (int py = INTERIOR_MARGIN + DENSITY / 2; py < pixels.getHeight() - INTERIOR_MARGIN; py += 12 * DENSITY) {
            for (int px = INTERIOR_MARGIN + DENSITY / 2; px < pixels.getWidth() - INTERIOR_MARGIN; px += 12 * DENSITY) {
                if (!opaqueInterior(pixels, px, py)) continue;
                double x = origin(face, 0) + (px + .5) / DENSITY;
                double y = origin(face, 1) + (py + .5) / DENSITY;
                String sample = name + " texel " + px + "," + py + " at window " + x + "," + y;
                int actual = pixels.getRGB(px, py);
                assertChannels(expectedFace(layout, feature, x, y), actual, sample);
                if (!feature && face != window) {
                    // Same world/window pixel, different panel: its reflection must not restart at the card's corner.
                    int wx = (int)Math.round((x - origin(window, 0)) * DENSITY - .5);
                    int wy = (int)Math.round((y - origin(window, 1)) * DENSITY - .5);
                    assertTrue(opaqueInterior(windowPixels, wx, wy), sample + ": opaque window reference");
                    assertChannels(windowPixels.getRGB(wx, wy), actual, sample + ": continue the window field");
                }
                checked++;
            }
        }
        assertTrue(checked >= 4, name + ": exercise multiple unambiguous face samples, not bevels or transparency");
        return pixels;
    }

    private static void assertTabHeader(JsonObject layout, JsonObject control, JsonObject face,
            BufferedImage pixels, boolean feature) {
        double parentTop = surface(layout, control.get("tab_panel").getAsString()).getAsJsonArray("rect").get(1).getAsDouble();
        int checked = 0;
        for (int py = INTERIOR_MARGIN + DENSITY / 2;
                origin(face, 1) + (py + .5) / DENSITY < parentTop; py += 2 * DENSITY) {
            for (int px = INTERIOR_MARGIN + DENSITY / 2; px < pixels.getWidth() - INTERIOR_MARGIN; px += 4 * DENSITY) {
                if (!opaqueInterior(pixels, px, py)) continue;
                double x = origin(face, 0) + (px + .5) / DENSITY;
                double y = origin(face, 1) + (py + .5) / DENSITY;
                assertChannels(expectedFace(layout, feature, x, y), pixels.getRGB(px, py),
                        face.get("sprite") + ": tab header at window " + x + "," + y);
                checked++;
            }
        }
        assertTrue(checked > 0, face.get("sprite") + ": check the protruding tab, not only the parent card below it");
    }

    private static int assertWindowPoint(JsonObject layout, JsonObject window, BufferedImage pixels, double x, double y) {
        int px = (int)Math.floor((x - origin(window, 0)) * DENSITY);
        int py = (int)Math.floor((y - origin(window, 1)) * DENSITY);
        String name = layout.get("screen") + " band sample at window " + x + "," + y;
        assertTrue(opaqueInterior(pixels, px, py), name + ": avoid the window frame");
        int actual = pixels.getRGB(px, py);
        assertChannels(expectedFace(layout, false, origin(window, 0) + (px + .5) / DENSITY,
                origin(window, 1) + (py + .5) / DENSITY), actual, name);
        return actual;
    }

    /** Scalar equivalent of the approved generator, with one phase, angle, scale, and tone per layout. */
    private static int expectedFace(JsonObject layout, boolean feature, double x, double y) {
        double scale = fieldScale(layout);
        double projection = (x + .70 * y) / scale;
        double shine = 0;
        for (int i = 0; i < BANDS.length; i++) {
            double[] band = BANDS[i];
            double distance = Math.abs(projection - band[0]);
            double edge = band[1] >= 30 ? 2.6 : 1.9;
            double plane = .5 * (1 + Math.tanh((band[1] / 2 - distance) / edge));
            double shoulder = Math.exp(-Math.pow(distance / (band[1] * .75 + edge), 2));
            double fade = .92 + .08 * Math.cos((y / scale - i * 29) / 200.0);
            shine += fade * (band[2] * plane + .012 * shoulder);
        }
        shine = Math.min(shine, .27) * (feature ? .30 : 1);
        int base = feature ? 0x55536C : 0xCBCCD4;
        double tone = .65 - .004 * y;
        int result = 0xFF000000;
        for (int shift : new int[]{0, 8, 16}) {
            double ground = (base >>> shift & 255) + tone;
            result |= Math.clamp((int)Math.rint(ground + (255 - ground) * shine), 0, 255) << shift;
        }
        return result;
    }

    private static double fieldScale(JsonObject layout) {
        var size = layout.getAsJsonArray("size");
        return (size.get(0).getAsDouble() + .70 * size.get(1).getAsDouble()) / (400 + .70 * 344);
    }

    /** Authored material identities, deliberately not inferred from the PNG color under test. */
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

    private static JsonObject variant(JsonObject layout, String id, String suffix, int dy, int dh) {
        var entry = surface(layout, id).deepCopy();
        entry.addProperty("sprite", entry.get("sprite").getAsString() + suffix);
        var rect = entry.getAsJsonArray("rect");
        rect.set(1, new JsonPrimitive(rect.get(1).getAsInt() + dy));
        rect.set(3, new JsonPrimitive(rect.get(3).getAsInt() + dh));
        // Keep layout.size unchanged: resizing/moving a panel must not rescale or translate the light field.
        return entry;
    }

    private static void assertChannels(int expected, int actual, String message) {
        assertEquals(255, actual >>> 24, message);
        for (int shift : new int[]{0, 8, 16}) {
            assertEquals(expected >>> shift & 255, actual >>> shift & 255, 1, message + " channel " + shift);
        }
    }

    private static boolean opaqueInterior(BufferedImage pixels, int px, int py) {
        if (px < INTERIOR_MARGIN || py < INTERIOR_MARGIN || px + INTERIOR_MARGIN >= pixels.getWidth()
                || py + INTERIOR_MARGIN >= pixels.getHeight()) return false;
        // A full 6-GUI-pixel neighborhood excludes rounded corners, tab cutouts, bevels, and shadows.
        for (int y = py - INTERIOR_MARGIN; y <= py + INTERIOR_MARGIN; y++) {
            for (int x = px - INTERIOR_MARGIN; x <= px + INTERIOR_MARGIN; x++) {
                if (pixels.getRGB(x, y) >>> 24 != 255) return false;
            }
        }
        return true;
    }

    private static int pad(JsonObject entry, int edge) {
        return entry.has("pad") ? entry.getAsJsonArray("pad").get(edge).getAsInt() : 0;
    }

    private static double origin(JsonObject entry, int axis) {
        String override = axis == 0 ? "draw_x" : "draw_y";
        return (entry.has(override) ? entry.get(override).getAsDouble() : entry.getAsJsonArray("rect").get(axis).getAsDouble())
                - pad(entry, axis);
    }

    private static boolean isFace(JsonObject entry) {
        if (!entry.has("sprite") || entry.get("sprite").isJsonNull()) return false;
        String sprite = entry.get("sprite").getAsString();
        return sprite.contains("/glass/") || sprite.contains("/tabs/");
    }

    private static JsonObject surface(JsonObject layout, String id) {
        return layout.getAsJsonArray("draw").asList().stream().map(e -> e.getAsJsonObject())
                .filter(e -> isFace(e) && e.has("id") && id.equals(e.get("id").getAsString())).findFirst().orElseThrow();
    }

    private static BufferedImage image(JsonObject entry) throws Exception {
        String sprite = entry.get("sprite").getAsString();
        assertTrue(sprite.startsWith("molecularmanipulator:osq_light/"), sprite);
        var file = ASSETS.resolve("textures/gui/sprites/" + sprite.split(":", 2)[1] + ".png");
        assertTrue(Files.isRegularFile(file), file.toString());
        var pixels = ImageIO.read(file.toFile());
        assertNotNull(pixels, file.toString());
        return pixels;
    }

    private static List<Path> layouts() throws Exception {
        try (var files = Files.list(ASSETS.resolve("osq_light/layout"))) {
            return files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static JsonObject layout(String page) throws Exception {
        return json(ASSETS.resolve("osq_light/layout/" + page + ".json"));
    }

    private static JsonObject json(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static String screenSource(String screen) throws Exception {
        return Files.readString(Path.of("src/main/java/com/atir/molecularmanipulator/client/" + screen + ".java"))
                .replaceAll("(?m)//.*$", "").replaceAll("\\s+", "");
    }
}
