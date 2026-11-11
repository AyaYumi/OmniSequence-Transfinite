package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class StarSignalAnimationTest {
    private static final int SIZE = StarSignalAnimation.FRAME_SIZE;
    private static final int FRAMES = StarSignalAnimation.FRAME_COUNT;
    private static final Path TEXTURE = Path.of(
            "src/main/resources/assets/molecularmanipulator/textures/gui/key/star_signal_flow.png");

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures/star_signal_flow");
    private static final int OUTLINE = 0xFF172C31;

    @Test
    void stripLayoutAndBytesMatchTheApprovedAnimation() throws IOException {
        var image = image();
        assertEquals(16, SIZE);
        assertEquals(64, FRAMES);
        assertEquals(1024, image.getHeight());
        assertArrayEquals(Files.readAllBytes(FIXTURES.resolve("animated/star-signal-flow.png")),
                Files.readAllBytes(TEXTURE), "Use the approved animation without scaling or recoloring");
    }

    @Test
    void transparentSilhouetteAndDarkOutlineRemainFixedInEveryFrame() throws IOException {
        var image = image();
        var approved = ImageIO.read(FIXTURES.resolve("star-signal-flow.png").toFile());
        assertNotNull(approved);
        int transparent = 0;
        int opaque = 0;
        int outline = 0;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int original = approved.getRGB(x, y);
                int alpha = original >>> 24;
                assertTrue(alpha == 0 || alpha == 255);
                if (alpha == 0) transparent++;
                else opaque++;
                if (original == OUTLINE) outline++;
                for (int frame = 0; frame < FRAMES; frame++) {
                    int actual = pixel(image, frame, x, y);
                    assertEquals(alpha, actual >>> 24, "The silhouette must not move or fade");
                    if (alpha == 0 || original == OUTLINE) {
                        assertEquals(original, actual, "Pulses must not enter the background or outline");
                    }
                }
            }
        }
        assertTrue(transparent > 0);
        assertTrue(opaque > 0);
        assertTrue(outline > 0);
    }

    @Test
    void signalHeadsTravelTowardTheStarAlongAllFourRightAngleRoutes() throws IOException {
        var image = image();
        int[][][] routes = {
                {{2, 3}, {3, 3}, {4, 3}, {5, 3}, {5, 4}, {5, 5}},
                {{1, 7}, {2, 7}, {3, 7}, {4, 7}, {4, 6}, {5, 6}},
                {{3, 12}, {4, 12}, {5, 12}, {6, 12}, {6, 11}, {6, 10}, {6, 9}, {7, 9}},
                {{4, 14}, {5, 14}, {6, 14}, {7, 14}, {8, 14}, {8, 13}, {8, 12}, {9, 12}, {10, 12}}
        };
        for (int[][] route : routes) {
            int previousPeak = -1;
            for (int[] point : route) {
                int peak = peakFrame(image, point[0], point[1]);
                int minimum = 255;
                int maximum = 0;
                for (int frame = 0; frame < FRAMES; frame++) {
                    int value = green(pixel(image, frame, point[0], point[1]));
                    minimum = Math.min(minimum, value);
                    maximum = Math.max(maximum, value);
                }
                assertTrue(maximum - minimum >= 40, "A visible pulse must pass each route pixel");
                if (previousPeak >= 0) {
                    int advance = Math.floorMod(peak - previousPeak, FRAMES);
                    assertTrue(advance > 0 && advance < FRAMES / 4,
                            "Each downstream pixel must peak after its upstream neighbor, including corners");
                }
                previousPeak = peak;
            }
        }
    }

    @Test
    void theStarBreathesGentlyWithoutBlinkingOrLosingItsBrightCenter() throws IOException {
        var image = image();
        int minimum = 255;
        int maximum = 0;
        for (int frame = 0; frame < FRAMES; frame++) {
            assertEquals(0xFFEDFDF7, pixel(image, frame, 10, 5), "The star center stays bright");
            int value = green(pixel(image, frame, 9, 4));
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        assertTrue(minimum >= 230, "The core must stay readable throughout its breathing cycle");
        assertTrue(maximum - minimum >= 5 && maximum - minimum <= 24, "Keep the pulse subtle");
    }

    @Test
    void allSixtyFourFramesAreDistinct() throws IOException {
        var image = image();
        var frames = new HashSet<String>();
        for (int frame = 0; frame < FRAMES; frame++) {
            frames.add(Arrays.toString(image.getRGB(0, frame * SIZE, SIZE, SIZE, null, 0, SIZE)));
        }
        assertEquals(FRAMES, frames.size());
    }

    @Test
    void theLoopBoundaryIsNoMoreAbruptThanOrdinaryPulseSteps() throws IOException {
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
    void fractionalTicksSelectAllFramesInAOnePointSixSecondLoop() {
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

    private static int peakFrame(BufferedImage image, int x, int y) {
        int peak = 0;
        for (int frame = 1; frame < FRAMES; frame++) {
            if (green(pixel(image, frame, x, y)) > green(pixel(image, peak, x, y))) {
                peak = frame;
            }
        }
        return peak;
    }

    private static int green(int pixel) {
        return (pixel >>> 8) & 255;
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
