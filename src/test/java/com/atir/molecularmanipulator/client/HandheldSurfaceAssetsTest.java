package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Inspect the actual packaged faces, not a separate preview or rendering approximation. */
class HandheldSurfaceAssetsTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/molecularmanipulator");
    private static final List<String> PAGES = List.of("building_access_terminal", "star_chart_calibrator",
            "star_chart_seal", "star_chart_details");
    private static final int DENSITY = 4;

    @Test void everyPageKeepsItsAuthoredPanelEdgesAndSize() throws Exception {
        int checked = 0;
        for (var page : PAGES) {
            var layout = layout(page);
            for (var element : layout.getAsJsonArray("draw")) {
                var entry = element.getAsJsonObject();
                if (!surface(entry)) continue;
                var image = image(entry);
                var rect = entry.getAsJsonArray("rect");
                var pad = entry.getAsJsonArray("pad");
                int w = rect.get(2).getAsInt(), h = rect.get(3).getAsInt();
                assertEquals((w + pad.get(0).getAsInt() + pad.get(2).getAsInt()) * DENSITY, image.getWidth());
                assertEquals((h + pad.get(1).getAsInt() + pad.get(3).getAsInt()) * DENSITY, image.getHeight());
                assertEquals(255, sample(image, entry, w / 2, h / 2) >>> 24);
                if (entry.get("k").getAsString().equals("card")) {
                    assertNotEquals(sample(image, entry, w / 2, 0), sample(image, entry, w / 2, 6),
                            page + " " + entry.get("id") + ": keep the shared fine card rim");
                } else {
                    // These unpadded handheld windows are opaque to their boundary; their top row is face, not bevel.
                    for (int x : new int[]{0, w / 2, w - 1}) {
                        for (int y : new int[]{0, h - 1}) {
                            assertEquals(255, sample(image, entry, x, y) >>> 24, page + ": retain the window silhouette");
                        }
                    }
                }
                checked++;
            }
        }
        assertEquals(20, checked, "The terminal, positioning, sealing and archive pages keep their authored surfaces");
    }

    @Test void allFacesSampleTheApprovedWholeWindowLightingAndMaterialResponse() throws Exception {
        for (var page : PAGES) {
            var layout = layout(page);
            for (var element : layout.getAsJsonArray("draw")) {
                var entry = element.getAsJsonObject();
                if (!surface(entry)) continue;
                var rect = entry.getAsJsonArray("rect");
                int w = rect.get(2).getAsInt(), h = rect.get(3).getAsInt();
                var card = image(entry);
                String id = entry.get("id").getAsString();
                for (int y : new int[]{8, Math.max(8, h / 4), h / 2, Math.min(h - 9, h * 3 / 4), h - 9}) {
                    for (int x : new int[]{8, Math.max(8, w / 4), w / 2, Math.min(w - 9, w * 3 / 4), w - 9}) {
                        var pad = entry.getAsJsonArray("pad");
                        int px = (pad.get(0).getAsInt() + x) * DENSITY + DENSITY / 2;
                        int py = (pad.get(1).getAsInt() + y) * DENSITY + DENSITY / 2;
                        assertTrue(opaqueInterior(card, px, py), page + " " + id + " needs an unambiguous interior sample");
                        int face = card.getRGB(px, py);
                        int expected = approvedFaceColor(layout, entry, px, py);
                        for (int shift : new int[]{0, 8, 16}) {
                            assertEquals((expected >> shift) & 255, (face >> shift) & 255, 1,
                                    page + " " + id + " differs from approved design at " + x + "," + y);
                        }
                    }
                }
            }
        }
    }

    @Test void pointCancellationRecoveryAndDeploymentRemainSeparateNonOverlappingControls() throws Exception {
        var page = layout("star_chart_calibrator");
        var controls = new HashMap<String, JsonObject>();
        for (var element : page.getAsJsonArray("draw")) {
            var entry = element.getAsJsonObject();
            if (entry.has("k") && entry.get("k").getAsString().equals("ctl")) controls.put(entry.get("id").getAsString(), entry);
        }
        assertTrue(controls.keySet().containsAll(List.of("interrupt", "cancel", "deploy")), "Point release and construction withdrawal are different actions");
        int width = page.getAsJsonArray("size").get(0).getAsInt(), height = page.getAsJsonArray("size").get(1).getAsInt();
        for (var control : controls.values()) {
            var rect = control.getAsJsonArray("rect");
            int x = rect.get(0).getAsInt(), y = rect.get(1).getAsInt(), w = rect.get(2).getAsInt(), h = rect.get(3).getAsInt();
            assertTrue(x >= 4 && y >= 4 && x + w <= width - 4 && y + h <= height - 4, control.toString());
            for (var other : controls.values()) {
                if (control == other) continue;
                var box = other.getAsJsonArray("rect");
                assertFalse(x < box.get(0).getAsInt() + box.get(2).getAsInt() && x + w > box.get(0).getAsInt()
                        && y < box.get(1).getAsInt() + box.get(3).getAsInt() && y + h > box.get(1).getAsInt(),
                        control.get("id") + " overlaps " + other.get("id"));
            }
        }
    }

    /** Independent window-space sample of tools/generate_osq_reflections.py, not a card-local gradient. */
    private static int approvedFaceColor(JsonObject layout, JsonObject entry, int px, int py) {
        var size = layout.getAsJsonArray("size");
        var rect = entry.getAsJsonArray("rect");
        var pad = entry.getAsJsonArray("pad");
        double x = (entry.has("draw_x") ? entry.get("draw_x").getAsDouble() : rect.get(0).getAsDouble())
                - pad.get(0).getAsInt() + (px + .5) / DENSITY;
        double y = (entry.has("draw_y") ? entry.get("draw_y").getAsDouble() : rect.get(1).getAsDouble())
                - pad.get(1).getAsInt() + (py + .5) / DENSITY;
        double scale = (size.get(0).getAsDouble() + .70 * size.get(1).getAsDouble()) / (400 + .70 * 344);
        double projection = (x + .70 * y) / scale;
        double shine = 0;
        double[][] bands = {{114, 44, .23}, {169, 18, .16}, {343, 42, .23},
                {396, 16, .16}, {538, 40, .22}, {588, 16, .16}};
        for (int i = 0; i < bands.length; i++) {
            double[] band = bands[i];
            double distance = Math.abs(projection - band[0]);
            double edge = band[1] >= 30 ? 2.6 : 1.9;
            double plane = .5 * (1 + Math.tanh((band[1] / 2 - distance) / edge));
            double shoulder = Math.exp(-Math.pow(distance / (band[1] * .75 + edge), 2));
            double fade = .92 + .08 * Math.cos((y / scale - i * 29) / 200.0);
            shine += fade * (band[2] * plane + .012 * shoulder);
        }
        String id = entry.get("id").getAsString();
        boolean feature = id.equals("panel.5") || id.equals("target");
        int base = feature ? 0x55536C : 0xCBCCD4;
        shine = Math.min(shine, .27) * (feature ? .30 : 1);
        double tone = .65 - .004 * y;
        int result = 0xFF000000;
        for (int shift : new int[]{0, 8, 16}) {
            double ground = ((base >> shift) & 255) + tone;
            result |= Math.clamp((int)Math.rint(ground + (255 - ground) * shine), 0, 255) << shift;
        }
        return result;
    }

    private static boolean opaqueInterior(BufferedImage image, int px, int py) {
        int margin = 6 * DENSITY;
        if (px < margin || py < margin || px + margin >= image.getWidth() || py + margin >= image.getHeight()) return false;
        for (int y = py - margin; y <= py + margin; y++) {
            for (int x = px - margin; x <= px + margin; x++) {
                if (image.getRGB(x, y) >>> 24 != 255) return false;
            }
        }
        return true;
    }

    private static JsonObject layout(String page) throws Exception {
        return JsonParser.parseString(Files.readString(ASSETS.resolve("osq_light/layout/" + page + ".json"))).getAsJsonObject();
    }

    private static boolean surface(JsonObject entry) {
        return entry.has("sprite") && entry.has("k")
                && List.of("window", "card").contains(entry.get("k").getAsString());
    }

    private static BufferedImage image(JsonObject entry) throws Exception {
        return ImageIO.read(ASSETS.resolve("textures/gui/sprites/" + entry.get("sprite").getAsString().split(":", 2)[1] + ".png").toFile());
    }

    private static int sample(BufferedImage image, JsonObject entry, int x, int y) {
        var pad = entry.getAsJsonArray("pad");
        return image.getRGB((pad.get(0).getAsInt() + x) * DENSITY + DENSITY / 2,
                (pad.get(1).getAsInt() + y) * DENSITY + DENSITY / 2);
    }
}
