package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Inspect the actual packaged faces, not a separate preview or rendering approximation. */
class HandheldSurfaceAssetsTest {
    private static final List<Path> ROOTS = List.of(
            Path.of("src/main/resources/assets/molecularmanipulator"),
            Path.of("resourcepacks/OmniSequence Dark/assets/molecularmanipulator"));
    private static final List<String> PAGES = List.of("building_access_terminal", "star_chart_calibrator",
            "star_chart_seal", "star_chart_details");
    private static final int DENSITY = 4;

    @Test void everyPageKeepsThreeMachineStyleReflectionsWithoutChangingPanelEdgesOrSize() throws Exception {
        int checked = 0;
        for (var root : ROOTS) for (var page : PAGES) {
            var layout = layout(root, page);
            for (var element : layout.getAsJsonArray("draw")) {
                var entry = element.getAsJsonObject();
                if (!surface(entry)) continue;
                var image = image(root, entry);
                var rect = entry.getAsJsonArray("rect");
                var pad = entry.getAsJsonArray("pad");
                int w = rect.get(2).getAsInt(), h = rect.get(3).getAsInt();
                assertEquals((w + pad.get(0).getAsInt() + pad.get(2).getAsInt()) * DENSITY, image.getWidth());
                assertEquals((h + pad.get(1).getAsInt() + pad.get(3).getAsInt()) * DENSITY, image.getHeight());
                assertEquals(255, sample(image, entry, w / 2, h / 2) >>> 24);
                assertNotEquals(sample(image, entry, w / 2, 0), sample(image, entry, w / 2, 6),
                        "Keep the shared fine top rim");
                if (entry.get("id").getAsString().equals("window")) {
                    for (double[] band : new double[][]{{.17, 40}, {.25, 12}, {.60, 24}}) {
                        int centerY = band[0] < .5 ? h / 8 : h / 2;
                        for (int dy : new int[]{-h / 10, 0, h / 10}) {
                            int y = centerY + dy;
                            int x = (int)Math.round(band[0] * (w + .75 * h) - .75 * y);
                            int outsideX = x + (int)Math.round((band[1] / 2 + 5) * 1.25);
                            int gain = (sample(image, entry, x, y) & 255)
                                    - (sample(image, entry, outsideX, y) & 255);
                            assertTrue(gain >= (root.equals(ROOTS.getFirst()) ? 6 : 5),
                                    page + " must keep each of the three approved reflection bands");
                            assertTrue(gain <= 20, "Use cold machine-glass reflections, not bright white stripes");
                        }
                    }
                }
                checked++;
            }
        }
        assertEquals(40, checked, "Both themes include the terminal, positioning, sealing and archive pages");
    }

    @Test void allFacesMatchTheApprovedPageLightingAndMaterialResponse() throws Exception {
        for (var root : ROOTS) for (var page : PAGES) {
            var layout = layout(root, page);
            var colors = new HashMap<String, Integer>();
            for (var element : layout.getAsJsonArray("draw")) {
                var entry = element.getAsJsonObject();
                if (!entry.has("id")) continue;
                String id = entry.get("id").getAsString();
                if (id.startsWith("color.")) colors.put(id.substring(6), (int)Long.parseLong(entry.get("color").getAsString().substring(1), 16));
            }
            for (var element : layout.getAsJsonArray("draw")) {
                var entry = element.getAsJsonObject();
                if (!surface(entry)) continue;
                var rect = entry.getAsJsonArray("rect");
                int w = rect.get(2).getAsInt(), h = rect.get(3).getAsInt();
                var card = image(root, entry);
                String id = entry.get("id").getAsString();
                for (int y : new int[]{6, h / 4, h / 2, h * 3 / 4, h - 6}) {
                    for (int x : new int[]{6, w / 4, w / 2, w * 3 / 4, w - 6}) {
                        int face = sample(card, entry, x, y);
                        int expected = approvedFaceColor(layout, entry, colors, !root.equals(ROOTS.getFirst()), x, y);
                        for (int shift : new int[]{0, 8, 16}) {
                            assertEquals((expected >> shift) & 255, (face >> shift) & 255, 1,
                                    page + " " + id + " differs from approved design at " + x + "," + y);
                        }
                    }
                }
            }
        }
    }

    @Test void pointCancellationRecoveryAndDeploymentRemainSeparateNonOverlappingControlsInBothThemes() throws Exception {
        for (var root : ROOTS) {
            var page = layout(root, "star_chart_calibrator");
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
    }

    /** Independent sample of design 01, including the weaker feature-card reflection. */
    private static int approvedFaceColor(JsonObject layout, JsonObject entry, Map<String, Integer> colors,
            boolean dark, int localX, int localY) {
        var size = layout.getAsJsonArray("size");
        double w = size.get(0).getAsDouble(), h = size.get(1).getAsDouble();
        var rect = entry.getAsJsonArray("rect");
        double pixelCenter = (DENSITY / 2 + .5) / DENSITY;
        double x = rect.get(0).getAsInt() + localX + pixelCenter;
        double y = rect.get(1).getAsInt() + localY + pixelCenter;
        double u = (x + .75 * y) / 1.25, length = (w + .75 * h) / 1.25;
        double hi = 0;
        for (double[] band : new double[][]{{.17, 40, .38}, {.25, 12, .30}, {.60, 24, .24}}) {
            double distance = Math.abs(u - band[0] * length);
            double edge = unit(.5 - (distance - band[1] / 2) / 2);
            double inner = 1 - .25 * unit(distance / (band[1] / 2));
            hi = Math.max(hi, band[2] * edge * inner);
        }
        double corner = .30 * Math.pow(unit(1 - Math.hypot(x / (.45 * w), y / (.55 * h))), 1.3);
        hi = 1 - (1 - hi) * (1 - corner);
        double shade = .10 * unit((x / w + y / h) / 2 - .45) / .55 * (1 - unit(hi * 3));
        String id = entry.get("id").getAsString();
        boolean window = id.equals("window"), feature = id.equals("panel.5") || id.equals("target");
        int base = colors.get(window ? "paper" : feature ? "feature" : "card");
        hi *= dark ? feature ? .10 : .18 : feature ? .30 : 1;
        if (feature) shade *= .30;
        int[] glint = dark ? new int[]{143, 161, 191} : new int[]{238, 240, 248};
        int[] shadow = dark ? new int[]{8, 12, 19} : new int[]{92, 94, 120};
        double start = dark ? .8 : 2.3;
        double ramp = window ? 0 : start + (-.8 - start)
                * (localY * DENSITY + DENSITY / 2) / (rect.get(3).getAsInt() * DENSITY - 1);
        int result = 0xFF000000;
        for (int c = 0; c < 3; c++) {
            int shift = 16 - 8 * c;
            double value = ((base >> shift) & 255) * (1 - shade) + shadow[c] * shade;
            value = value * (1 - hi) + glint[c] * hi + ramp;
            result |= Math.clamp((int)Math.round(value), 0, 250) << shift;
        }
        return result;
    }

    private static double unit(double value) { return Math.clamp(value, 0, 1); }

    private static JsonObject layout(Path root, String page) throws Exception {
        return JsonParser.parseString(Files.readString(root.resolve("osq_light/layout/" + page + ".json"))).getAsJsonObject();
    }

    private static boolean surface(JsonObject entry) {
        return entry.has("sprite") && entry.has("k")
                && List.of("window", "card").contains(entry.get("k").getAsString());
    }

    private static BufferedImage image(Path root, JsonObject entry) throws Exception {
        return ImageIO.read(root.resolve("textures/gui/sprites/" + entry.get("sprite").getAsString().split(":", 2)[1] + ".png").toFile());
    }

    private static int sample(BufferedImage image, JsonObject entry, int x, int y) {
        var pad = entry.getAsJsonArray("pad");
        return image.getRGB((pad.get(0).getAsInt() + x) * DENSITY + DENSITY / 2,
                (pad.get(1).getAsInt() + y) * DENSITY + DENSITY / 2);
    }
}
