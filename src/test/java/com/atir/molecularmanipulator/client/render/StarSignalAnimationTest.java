package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class StarSignalAnimationTest {
    private static final int SIZE = StarSignalAnimation.FRAME_SIZE;
    private static final int FRAMES = StarSignalAnimation.FRAME_COUNT;
    private static final Path TEXTURE = Path.of(
            "src/main/resources/assets/molecularmanipulator/textures/gui/key/star_signal_flow.png");

    @Test
    void stripLayoutMatchesTheGuiRendererAndEveryPixelIsPresent() throws IOException {
        var image = image();
        assertEquals(SIZE, image.getWidth());
        assertEquals(StarSignalAnimation.TEXTURE_HEIGHT, image.getHeight());
        assertEquals(64, FRAMES);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < SIZE; x++) {
                assertEquals(255, image.getRGB(x, y) >>> 24, "No transparent holes in the data field");
            }
        }
    }

    @Test
    void everyFrameHasDataAcrossEveryRowAndColumnInsteadOfATravellingEmptyBand() throws IOException {
        var image = image();
        for (int frame = 0; frame < FRAMES; frame++) {
            for (int line = 0; line < SIZE; line++) {
                int row = 0;
                int column = 0;
                for (int offset = 0; offset < SIZE; offset++) {
                    if (luminous(pixel(image, frame, offset, line))) row++;
                    if (luminous(pixel(image, frame, line, offset))) column++;
                }
                assertTrue(row >= 2, "Empty row in frame " + frame + ", y=" + line);
                assertTrue(column >= 2, "Empty column in frame " + frame + ", x=" + line);
            }
        }
    }

    @Test
    void featuresScrollUpRightAndWrapInBothAxesIncludingTheEndOfTheStrip() throws IOException {
        var image = image();
        for (int frame = 0; frame < FRAMES; frame++) {
            int nextPixel = (frame + 4) % FRAMES;
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    assertEquals(pixel(image, frame, Math.floorMod(x - 1, SIZE), (y + 1) % SIZE),
                            pixel(image, nextPixel, x, y), "Discontinuous wrapped motion in frame " + frame);
                }
            }
        }
    }

    @Test
    void theLoopBoundaryIsNoMoreAbruptThanOrdinarySubpixelSteps() throws IOException {
        var image = image();
        long largestOrdinaryStep = 0;
        for (int frame = 0; frame < FRAMES - 1; frame++) {
            long difference = difference(image, frame, frame + 1);
            assertTrue(difference > 0, "The animation must not pause on duplicate frames");
            largestOrdinaryStep = Math.max(largestOrdinaryStep, difference);
        }
        assertTrue(difference(image, FRAMES - 1, 0) <= largestOrdinaryStep,
                "The last frame must continue into the first without a flash or gap");
    }

    @Test
    void fractionalTicksSelectAllSubframesAtTheOriginalScrollSpeed() {
        assertEquals(32, StarSignalAnimation.CYCLE_TICKS);
        for (int frame = 0; frame < FRAMES; frame++) {
            double ticks = frame / 2.0;
            assertEquals(frame, StarSignalAnimation.frameAt(ticks));
            assertEquals(frame, StarSignalAnimation.frameAt(ticks + 0.25));
            assertEquals(frame, StarSignalAnimation.frameAt(ticks + 32));
            assertEquals(frame, StarSignalAnimation.frameAt(ticks + 32 * 1_000_000L));
        }
        assertEquals(0, StarSignalAnimation.frameAt(32));
        assertEquals(FRAMES - 1, StarSignalAnimation.frameAt(-0.5));
        assertThrows(IllegalArgumentException.class, () -> StarSignalAnimation.frameAt(Double.NaN));
    }

    private static long difference(BufferedImage image, int a, int b) {
        long total = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int first = pixel(image, a, x, y);
                int second = pixel(image, b, x, y);
                for (int shift : new int[] {0, 8, 16}) {
                    total += Math.abs(((first >>> shift) & 255) - ((second >>> shift) & 255));
                }
            }
        }
        return total;
    }

    private static boolean luminous(int pixel) {
        return ((pixel >>> 8) & 255) >= 90;
    }

    private static int pixel(BufferedImage image, int frame, int x, int y) {
        return image.getRGB(x, frame * SIZE + y);
    }

    private static BufferedImage image() throws IOException {
        var image = ImageIO.read(TEXTURE.toFile());
        assertNotNull(image);
        assertEquals(SIZE, image.getWidth(), "GUI strip width");
        assertEquals(StarSignalAnimation.TEXTURE_HEIGHT, image.getHeight(), "GUI strip height");
        return image;
    }
}
