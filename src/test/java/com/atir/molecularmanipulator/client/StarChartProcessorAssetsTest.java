package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class StarChartProcessorAssetsTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/molecularmanipulator");
    private static final String[] ITEMS = {
        "star_chart_processor", "star_chart_circuit_board"
    };

    @Test
    void bothItemsHaveGeneratedModelsAndTheRequestedNames() throws IOException {
        var chinese = json(ASSETS.resolve("lang/zh_cn.json"));
        var english = json(ASSETS.resolve("lang/en_us.json"));
        var names = new String[] {"星图处理器", "星图电路板"};
        var englishNames = new String[] {"Star Chart Processor", "Star Chart Circuit Board"};
        for (int i = 0; i < ITEMS.length; i++) {
            var item = ITEMS[i];
            var model = json(ASSETS.resolve("models/item/" + item + ".json"));
            assertEquals("minecraft:item/generated", model.get("parent").getAsString());
            assertEquals("molecularmanipulator:item/" + item,
                    model.getAsJsonObject("textures").get("layer0").getAsString());
            assertEquals(names[i], chinese.get("item.molecularmanipulator." + item).getAsString());
            assertEquals(englishNames[i], english.get("item.molecularmanipulator." + item).getAsString());
            image(item);
        }
    }

    @Test
    void bothTexturesContain96AnimatedSixteenPixelFrames() throws IOException {
        for (var item : ITEMS) {
            var image = image(item);
            assertEquals(16, image.getWidth(), item);
            assertEquals(16 * 96, image.getHeight(), item);
            var animation = json(ASSETS.resolve("textures/item/" + item + ".png.mcmeta"))
                    .getAsJsonObject("animation");
            assertEquals(2, animation.get("frametime").getAsInt(), item);
            assertFalse(animation.get("interpolate").getAsBoolean(), item);
            assertFalse(animation.has("frames"), "The strip should play all 96 frames in order");
            var first = pixels(image, 0);
            boolean animated = false;
            for (int frame = 0; frame < 96; frame++) {
                var pixels = pixels(image, frame);
                assertTrue(Arrays.stream(pixels).anyMatch(pixel -> (pixel >>> 24) > 0), item + " frame " + frame);
                assertTrue(Arrays.stream(pixels).anyMatch(pixel -> (pixel >>> 24) == 0), item + " keeps its transparent silhouette");
                animated |= !Arrays.equals(first, pixels);
            }
            assertTrue(animated, item + " must animate rather than repeat a static image");
        }
    }

    @Test
    void circuitBoardAndFinishedProcessorKeepTheirDistinctArtwork() throws IOException {
        assertFalse(Arrays.equals(pixels(image(ITEMS[0]), 0), pixels(image(ITEMS[1]), 0)));
    }

    @Test
    void processorStartsWithTheApprovedV3Frame() throws IOException {
        assertArrayEquals(pixels(processorSource(), 0), pixels(image(ITEMS[0]), 0));
    }

    @Test
    void processorOnlyAnimatesCyanPixelsAndKeepsTheApprovedShellAndSilhouette() throws IOException {
        var source = pixels(processorSource(), 0);
        var strip = image(ITEMS[0]);
        for (int frame = 0; frame < 96; frame++) {
            var current = pixels(strip, frame);
            for (int pixel = 0; pixel < source.length; pixel++) {
                assertEquals(source[pixel] >>> 24, current[pixel] >>> 24,
                        "Edited silhouette changed in frame " + frame + ", pixel " + pixel);
                if (!isCyan(source[pixel])) {
                    assertEquals(source[pixel], current[pixel],
                            "Shell or background changed in frame " + frame + ", pixel " + pixel);
                } else {
                    assertTrue(((current[pixel] >>> 8) & 255) >= 30,
                            "Cyan circuitry must not blink off in frame " + frame);
                }
            }
        }
    }

    @Test
    void processorGlowHasSmoothStepsIncludingTheLoopBoundary() throws IOException {
        var strip = image(ITEMS[0]);
        long largestOrdinaryStep = 0;
        long loopStep = 0;
        for (int frame = 0; frame < 96; frame++) {
            var current = pixels(strip, frame);
            var next = pixels(strip, (frame + 1) % 96);
            long total = 0;
            for (int pixel = 0; pixel < current.length; pixel++) {
                // The v3 circuitry pulses faster while keeping each two-tick step below 1/8 of the RGB range.
                for (int channel = 0; channel < 24; channel += 8) {
                    int step = Math.abs(((current[pixel] >>> channel) & 255) - ((next[pixel] >>> channel) & 255));
                    assertTrue(step <= 32, "Abrupt glow change in frame " + frame);
                    total += step;
                }
            }
            assertTrue(total > 0, "Consecutive frames must not freeze");
            if (frame == 95) loopStep = total;
            else largestOrdinaryStep = Math.max(largestOrdinaryStep, total);
        }
        assertTrue(loopStep <= largestOrdinaryStep, "Loop boundary must not flash");
    }

    private static boolean isCyan(int pixel) {
        int red = (pixel >>> 16) & 255;
        int green = (pixel >>> 8) & 255;
        int blue = pixel & 255;
        return (pixel >>> 24) > 0 && green >= 45 && blue >= 45 && green >= red + 20 && blue >= red + 20;
    }

    private static BufferedImage processorSource() throws IOException {
        // The supplied v3 processor's first frame is kept separately from the animated production strip.
        var path = Path.of("src/test/resources/assets/molecularmanipulator/textures/item/star_chart_processor_frame0.png");
        var image = ImageIO.read(path.toFile());
        assertNotNull(image);
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        return image;
    }

    private static int[] pixels(BufferedImage image, int frame) {
        return image.getRGB(0, frame * 16, 16, 16, null, 0, 16);
    }

    private static BufferedImage image(String item) throws IOException {
        var path = ASSETS.resolve("textures/item/" + item + ".png");
        assertTrue(Files.isRegularFile(path), path.toString());
        var image = ImageIO.read(path.toFile());
        assertNotNull(image, path.toString());
        return image;
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
