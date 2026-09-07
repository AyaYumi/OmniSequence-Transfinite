package com.atir.molecularmanipulator.client.render.ctm;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.atir.molecularmanipulator.client.render.ctm.MatterConnectedTextureRules.*;

class MatterConnectedOutlineTest {
    private static final List<String> PORTS = List.of("item_input", "item_output", "fluid_input", "fluid_output");

    @Test void twoByTwoPortsKeepBothOuterFramePixelsAcrossAllFourJoins() throws Exception {
        for (String name : PORTS) {
            var original = texture(name);
            // Top-left / top-right / bottom-left / bottom-right blocks of the user's 2x2 wall.
            int tl = R | B | BR, tr = L | B | BL, bl = R | T | TR, br = L | T | TL;
            for (int y : new int[] {0, 1}) {
                assertEquals(original.getRGB(8, y), sample(name, tl, 15, y));
                assertEquals(original.getRGB(8, y), sample(name, tr, 0, y));
            }
            for (int y : new int[] {14, 15}) {
                assertEquals(original.getRGB(8, y), sample(name, bl, 15, y));
                assertEquals(original.getRGB(8, y), sample(name, br, 0, y));
            }
            for (int x : new int[] {0, 1}) {
                assertEquals(original.getRGB(x, 8), sample(name, tl, x, 15));
                assertEquals(original.getRGB(x, 8), sample(name, bl, x, 0));
            }
            for (int x : new int[] {14, 15}) {
                assertEquals(original.getRGB(x, 8), sample(name, tr, x, 15));
                assertEquals(original.getRGB(x, 8), sample(name, br, x, 0));
            }
        }
    }

    @Test void connectionNeverRewritesTheFourPortGlyphsOrTheirColoredInset() throws Exception {
        for (String name : PORTS) {
            var original = texture(name);
            for (int mask : new int[] {0, L, R, T, B, R | B | BR, 255}) {
                for (int y = 1; y < 15; y++) for (int x = 1; x < 15; x++) {
                    assertEquals(original.getRGB(x, y), sample(name, mask, x, y), name + " " + mask + " " + x + "," + y);
                }
            }
        }
    }

    @Test void assemblyOuterJoinUsesStraightFrameInsteadOfRepeatedCornerFasteners() throws Exception {
        String name = "pattern_assembly";
        var original = texture(name);
        assertEquals(2.0F / 16, edgeBand(face(name)));
        for (int x = 12; x < 16; x++) for (int y = 0; y < 2; y++) {
            assertEquals(original.getRGB(4, y), sample(name, R | B | BR, x, y));
        }
        for (int y = 12; y < 16; y++) for (int x = 0; x < 2; x++) {
            assertEquals(original.getRGB(x, 8), sample(name, R | B | BR, x, y));
        }
        for (int y = 2; y < 14; y++) for (int x = 2; x < 14; x++) {
            if ((x < 4 || x >= 12) && (y < 4 || y >= 12)) continue;
            assertEquals(original.getRGB(x, y), sample(name, 255, x, y));
        }
        for (int y = 2; y < 4; y++) for (int x = 12; x < 16; x++) {
            int color = sample(name, R | B | BR, x, y);
            assertTrue((color >> 16 & 255) >= 230 && (color >> 8 & 255) >= 230 && (color & 255) >= 230,
                    "Connected assembly corner fasteners must not leave grey dots");
        }
    }

    @Test void trueOutsideAndConcaveCornersKeepTheirOriginalTexels() throws Exception {
        for (String name : List.of("pattern_assembly", "item_output", "fluid_output", "controller")) {
            var original = texture(name);
            int band = Math.round(edgeBand(face(name)) * 16);
            for (int y = 0; y < band; y++) for (int x = 0; x < band; x++) {
                assertEquals(original.getRGB(x, y), sample(name, R | B | BR, x, y));
                assertEquals(original.getRGB(x, y), sample(name, L | T, x, y));
            }
        }
    }

    private static Face face(String name) {
        return new Face("molecularmanipulator:block/matter_fabrication_" + name, Direction.EAST, Direction.DOWN);
    }

    private static BufferedImage texture(String name) throws Exception {
        return ImageIO.read(Path.of("src/main/resources/assets/molecularmanipulator/textures/block/matter_fabrication_" + name + ".png").toFile());
    }

    private static int sample(String name, int mask, int x, int y) throws Exception {
        float px = (x + 0.5F) / 16, py = (y + 0.5F) / 16;
        var face = face(name);
        var patch = patches(face, mask).stream().filter(p -> px >= p.x0() && px < p.x1() && py >= p.y0() && py < p.y1()).findFirst().orElseThrow();
        float u = patch.u0() + (patch.u1() - patch.u0()) * (px - patch.x0()) / (patch.x1() - patch.x0());
        float v = patch.v0() + (patch.v1() - patch.v0()) * (py - patch.y0()) / (patch.y1() - patch.y0());
        boolean filler = patch.useCasingBackground(face);
        if (filler) {
            u = Math.clamp(u, 2.5F / 16, 13.5F / 16);
            v = Math.clamp(v, 2.5F / 16, 13.5F / 16);
        }
        return texture(filler ? "casing_top" : name).getRGB(Math.clamp((int) (u * 16), 0, 15), Math.clamp((int) (v * 16), 0, 15));
    }
}
