package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Inspect shared frame vertices and bundled materials at each handheld page's live size. */
class HandheldSurfaceAssetsTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/molecularmanipulator");
    private static final List<String> PAGES = List.of("building_access_terminal", "star_chart_calibrator",
            "star_chart_seal", "star_chart_details");
    private static final int DENSITY = 4;

    @Test void everyPageKeepsItsAuthoredPanelEdgesAndSize() throws Exception {
        int checked = 0;
        for (var page : PAGES) {
            for (var element : layout(page).getAsJsonArray("draw")) {
                var entry = element.getAsJsonObject();
                if (!surface(entry)) continue;
                var image = UiRenderRecorder.surface(entry);
                var rect = entry.getAsJsonArray("rect");
                var pad = entry.getAsJsonArray("pad");
                int w = rect.get(2).getAsInt(), h = rect.get(3).getAsInt();
                assertEquals((w + pad.get(0).getAsInt() + pad.get(2).getAsInt()) * DENSITY, image.getWidth());
                assertEquals((h + pad.get(1).getAsInt() + pad.get(3).getAsInt()) * DENSITY, image.getHeight());
                assertTrue(entry.get("sprite").getAsString().startsWith("molecularmanipulator:osq_light/common/glass_"));
                String material = entry.get("material").getAsString();
                assertEquals(material.equals("card") ? 16 : 255, sample(image, entry, w / 2, h / 2) >>> 24);
                if (material.equals("card")) {
                    assertNotEquals(sample(image, entry, w / 2, 0), sample(image, entry, w / 2, 6),
                            page + " " + entry.get("id") + ": retain the thin rim around the translucent card");
                } else {
                    assertEquals(255, sample(image, entry, w / 2, 8) >>> 24);
                    assertEquals(255, sample(image, entry, w / 2, h - 9) >>> 24);
                }
                checked++;
            }
        }
        assertEquals(20, checked, "The terminal, positioning, sealing and archive pages keep their authored surfaces");
    }

    @Test void allFacesKeepTheirMaterialSeparateFromTheWindowReflection() throws Exception {
        for (var page : PAGES) {
            for (var element : layout(page).getAsJsonArray("draw")) {
                var entry = element.getAsJsonObject();
                if (!surface(entry)) continue;
                var rect = entry.getAsJsonArray("rect");
                int w = rect.get(2).getAsInt(), h = rect.get(3).getAsInt();
                var image = UiRenderRecorder.surface(entry);
                String material = entry.get("material").getAsString();
                int expected = switch (material) {
                    case "card" -> 0x10FFFFFF;
                    case "feature" -> 0xFF55536C;
                    case "window" -> 0xFFCBCCD4;
                    default -> throw new AssertionError("Unexpected handheld material " + material);
                };
                for (int y : new int[]{8, Math.max(8, h / 4), h / 2, Math.min(h - 9, h * 3 / 4), h - 9})
                    for (int x : new int[]{8, Math.max(8, w / 4), w / 2, Math.min(w - 9, w * 3 / 4), w - 9})
                        assertEquals(expected, sample(image, entry, x, y), page + " " + entry.get("id") + " at " + x + "," + y);
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

    private static JsonObject layout(String page) throws Exception {
        return JsonParser.parseString(Files.readString(ASSETS.resolve("osq_light/layout/" + page + ".json"))).getAsJsonObject();
    }

    private static boolean surface(JsonObject entry) {
        return entry.has("sprite") && entry.has("k")
                && List.of("window", "card").contains(entry.get("k").getAsString());
    }

    private static int sample(BufferedImage image, JsonObject entry, int x, int y) {
        var pad = entry.getAsJsonArray("pad");
        return image.getRGB((pad.get(0).getAsInt() + x) * DENSITY + DENSITY / 2,
                (pad.get(1).getAsInt() + y) * DENSITY + DENSITY / 2);
    }
}
