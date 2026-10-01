package com.atir.molecularmanipulator.client.render.ctm;

import static com.atir.molecularmanipulator.client.render.ctm.MatterConnectedTextureRules.*;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class TaixuConnectedTexturesTest {
    private static final String PREFIX = "molecularmanipulator:block/taixu/";
    private final Map<String, BufferedImage> images = new HashMap<>();

    @Test
    void jadeWallErasesInternalSeamsButKeepsItsOuterPerimeter() throws Exception {
        for (int y = 1; y < 15; y++) {
            int seam = sample("jade", R, 15, y);
            assertTrue((seam >> 16 & 255) >= 220, "Silver seam must become white jade");
            assertEquals(texture("jade").getRGB(0, y), sample("jade", R, 0, y));
        }
    }

    @Test
    void joinedGlassRemovesOpaqueMountsAndKeepsConcaveCorners() throws Exception {
        for (int y : new int[] {0, 1, 2, 13, 14, 15}) for (int x : new int[] {0, 1, 2, 13, 14, 15}) {
            assertTrue((sample("glass", 255, x, y) >>> 24) < 100, "Interior glass join must remain transparent");
        }
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
            assertEquals(texture("glass").getRGB(x, y), sample("glass", L | T, x, y),
                    "A missing diagonal must retain its exposed corner");
        }
    }

    @Test
    void centerGlyphsAndNativeGlowsAreNeverStretchedOrErased() throws Exception {
        for (var name : List.of("controller", "collector", "stabilizer", "port", "core_seal")) {
            for (int mask = 0; mask < 256; mask++) {
                for (int y = 2; y < 14; y++) for (int x = 2; x < 14; x++) {
                    assertEquals(texture(name).getRGB(x, y), sample(name, mask, x, y), name + " mask=" + mask);
                }
            }
            assertEquals("block/taixu/jade", fillerTexture(face(name)));
        }
    }

    @Test
    void gildedJoinsRemoveRepeatedCornerFastenersWithoutTouchingTheCentralSeal() throws Exception {
        for (int y = 0; y < 5; y++) for (int x = 0; x < 5; x++) {
            int cleared = sample("gilded", 255, x, y);
            assertTrue((cleared >> 16 & 255) >= 220 && (cleared & 255) >= 215);
        }
        for (int y = 5; y < 11; y++) for (int x = 5; x < 11; x++) {
            assertEquals(texture("gilded").getRGB(x, y), sample("gilded", 255, x, y));
        }
    }

    @Test
    void directionalJoinsFollowRotatedTextureAxesAndRejectCrossedTracks() {
        var pos = new BlockPos(15, 80, -16); // A chunk boundary must not affect face-space sampling.
        for (var normal : Direction.values()) {
            Direction u = normal.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
            Direction v = normal.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
            for (var name : List.of("ring_side", "ring_top", "pillar", "conduit")) {
                for (int rotation = 0; rotation < 4; rotation++) {
                    var self = new Face(PREFIX + name, u, v);
                    int expected = name.startsWith("ring") ? L | R : T | B;
                    assertEquals(expected, connections(pos, normal, self, (p, d) -> self));
                    var crossed = new Face(PREFIX + name, v, u.getOpposite());
                    assertFalse(compatible(self, crossed));
                    var reversed = new Face(PREFIX + name, u.getOpposite(), v.getOpposite());
                    assertTrue(compatible(self, reversed));
                    Direction old = u; u = v; v = old.getOpposite();
                }
            }
        }
        assertFalse(compatible(face("jade"), face("glass")));
        assertFalse(compatible(face("jade"), new Face("othermod:block/taixu/jade", Direction.EAST, Direction.DOWN)));
    }

    private static Face face(String name) { return new Face(PREFIX + name, Direction.EAST, Direction.DOWN); }

    private BufferedImage texture(String name) throws Exception {
        if (!images.containsKey(name)) images.put(name, ImageIO.read(Path.of(
                "src/main/resources/assets/molecularmanipulator/textures/block/taixu/" + name + ".png").toFile()));
        return images.get(name);
    }

    private int sample(String name, int mask, int x, int y) throws Exception {
        float px = (x + 0.5F) / 16, py = (y + 0.5F) / 16;
        var face = face(name);
        var patch = patches(face, mask).stream().filter(p -> px >= p.x0() && px < p.x1()
                && py >= p.y0() && py < p.y1()).findFirst().orElseThrow();
        float u = patch.u0() + (patch.u1() - patch.u0()) * (px - patch.x0()) / (patch.x1() - patch.x0());
        float v = patch.v0() + (patch.v1() - patch.v0()) * (py - patch.y0()) / (patch.y1() - patch.y0());
        if (patch.useCasingBackground(face)) {
            name = "jade";
            u = Math.clamp(u, 2.5F / 16, 13.5F / 16);
            v = Math.clamp(v, 2.5F / 16, 13.5F / 16);
        }
        return texture(name).getRGB(Math.clamp((int)(u * 16), 0, 15), Math.clamp((int)(v * 16), 0, 15));
    }
}
