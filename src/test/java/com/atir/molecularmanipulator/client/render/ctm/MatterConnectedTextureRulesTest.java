package com.atir.molecularmanipulator.client.render.ctm;

import static com.atir.molecularmanipulator.client.render.ctm.MatterConnectedTextureRules.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MatterConnectedTextureRulesTest {
    @Test
    void editedPortsKeepTheirColoredFirstInsetRingInsideTheUnchangedGlyphRegion() {
        for (var name : List.of("item_input", "item_output", "fluid_input", "fluid_output")) {
            var port = face(name);
            assertEquals(1.0F / 16, edgeBand(port));
            var connected = patches(port, 255);
            var glyph = connected.stream().filter(p -> p.x0() >= 1.0F / 16 && p.x1() <= 15.0F / 16
                    && p.y0() >= 1.0F / 16 && p.y1() <= 15.0F / 16).toList();
            assertFalse(glyph.isEmpty());
            assertTrue(glyph.stream().noneMatch(Patch::remapped));
            assertTrue(connected.stream().anyMatch(p -> p.useCasingBackground(port)));
        }
    }
    private static final BlockPos ORIGIN = new BlockPos(15, 47, -16);
    private static final String PREFIX = "molecularmanipulator:block/matter_fabrication_";

    @Test
    void textureRolesAndBorderWidthsFollowTheMaterialContract() {
        assertEquals(Mode.ISOTROPIC, mode(PREFIX + "casing"));
        assertEquals(Mode.ISOTROPIC, mode(PREFIX + "glass"));
        assertEquals(Mode.ISOTROPIC, mode(PREFIX + "coil_top"));
        assertEquals(Mode.ISOTROPIC, mode(PREFIX + "stabilizer_top"));
        assertEquals(Mode.U_ONLY, mode(PREFIX + "coil"));
        assertEquals(Mode.V_ONLY, mode(PREFIX + "stabilizer"));
        for (var name : List.of("core", "controller", "controller_on", "item_input", "item_output",
                "fluid_input", "fluid_output", "pattern_assembly")) {
            assertEquals(Mode.ICON, mode(PREFIX + name));
        }
        assertEquals(1.0F / 16.0F, edgeBand(face("glass")));
        assertEquals(3.0F / 16.0F, edgeBand(face("casing")));
        assertEquals(4.0F / 16.0F, edgeBand(face("stabilizer_top")));
        assertEquals(2.0F / 16.0F, edgeBand(face("controller")));
    }

    @Test
    void allSixNormalsAndRotatedTextureAxesUseTheSameFaceSpaceMask() {
        for (var normal : Direction.values()) {
            Direction u = normal.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
            Direction v = normal.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
            for (int rotation = 0; rotation < 4; rotation++) {
                var self = new Face(PREFIX + "casing", u, v);
                var faces = neighborhood(self);
                assertEquals(255, connections(ORIGIN, normal, self, (pos, queriedNormal) -> {
                    assertEquals(normal, queriedNormal);
                    return faces.get(pos);
                }));
                faces.clear();
                faces.put(ORIGIN, self);
                faces.put(at(self, -1, 0), self);
                faces.put(at(self, 0, -1), self);
                faces.put(at(self, -1, -1), self);
                assertEquals(L | T | TL, connections(ORIGIN, normal, self, (pos, side) -> faces.get(pos)));
                Direction oldU = u;
                u = v;
                v = oldU.getOpposite();
            }
        }
    }

    @Test
    void hiddenFacesDifferentSpritesAndUnsupportedNormalsNeverConnect() {
        var self = face("casing");
        var faces = neighborhood(self);
        faces.remove(ORIGIN);
        assertEquals(0, mask(self, faces), "A hidden querying face must not connect through its cover");
        faces.clear();
        faces.put(ORIGIN, self);
        faces.put(at(self, 1, 0), face("glass"));
        faces.put(at(self, -1, 0), new Face("othermod:block/matter_fabrication_casing", self.u(), self.v()));
        assertEquals(0, mask(self, faces));
        assertEquals(0, connections(ORIGIN, Direction.EAST, self, (pos, side) -> self),
                "Texture axes in the normal direction do not describe a flat face");
    }

    @Test
    void directionalAndIconMaterialsRequireAlignedAxesAndRestrictConnectedEdges() {
        var coil = face("coil");
        var stabilizer = face("stabilizer");
        assertEquals(L | R, mask(coil, neighborhood(coil)));
        assertEquals(T | B, mask(stabilizer, neighborhood(stabilizer)));
        for (var name : List.of("coil", "stabilizer", "controller", "core", "item_input")) {
            var self = face(name);
            var rotated = new Face(self.texture(), self.v(), self.u().getOpposite());
            assertFalse(compatible(self, rotated));
            var opposite = new Face(self.texture(), self.u().getOpposite(), self.v().getOpposite());
            assertEquals(mode(self) != Mode.ICON, compatible(self, opposite));
        }
        var casing = face("casing");
        assertTrue(compatible(casing, new Face(casing.texture(), casing.v(), casing.u().getOpposite())));
        assertTrue(patches(coil, 255).stream().allMatch(p -> p.v0() == p.y0() && p.v1() == p.y1()));
        assertTrue(patches(stabilizer, 255).stream().allMatch(p -> p.u0() == p.x0() && p.u1() == p.x1()));
    }

    @Test
    void symmetricGoldTracesConnectAcrossReversedAxesButNeverAcrossQuarterTurns() {
        for (var name : List.of("coil", "stabilizer")) {
            var self = face(name);
            var neighbors = new HashMap<BlockPos, Face>();
            neighbors.put(ORIGIN, self);
            var reversed = new Face(self.texture(), self.u().getOpposite(), self.v().getOpposite());
            boolean horizontal = mode(self) == Mode.U_ONLY;
            var position = at(self, horizontal ? 1 : 0, horizontal ? 0 : 1);
            neighbors.put(position, reversed);
            assertEquals(horizontal ? R : B, mask(self, neighbors));
            neighbors.put(position, new Face(self.texture(), self.v(), self.u().getOpposite()));
            assertEquals(0, mask(self, neighbors));
        }
    }

    @Test
    void diagonalsNeedBothEdgesAndMissingDiagonalsKeepTheirConcaveCorners() {
        var self = face("casing");
        var faces = new HashMap<BlockPos, Face>();
        faces.put(ORIGIN, self);
        faces.put(at(self, -1, -1), self);
        assertEquals(0, mask(self, faces));
        faces.put(at(self, -1, 0), self);
        assertEquals(L, mask(self, faces));
        faces.put(at(self, 0, -1), self);
        assertEquals(L | T | TL, mask(self, faces));
        faces.remove(at(self, -1, -1));
        assertEquals(L | T, mask(self, faces));
        assertFalse(patches(self, L | T).get(0).remapped());
        assertTrue(patches(self, L | T | TL).get(0).remapped());
        int[][] corners = {{L | T, TL, 0}, {R | T, TR, 2}, {L | B, BL, 6}, {R | B, BR, 8}};
        for (var corner : corners) {
            assertFalse(patches(corner[0]).get(corner[2]).remapped());
            assertTrue(patches(corner[0] | corner[1]).get(corner[2]).remapped());
        }
        assertEquals(47, IntStream.range(0, 256).map(MatterConnectedTextureRules::normalizedMask).distinct().count());
    }

    @Test
    void everyMaskTilesTheWholeFaceAndLeavesTheCentralGlyphExactlyUnchanged() {
        for (var name : List.of("casing", "glass", "coil", "stabilizer", "controller", "fluid_output")) {
            var face = face(name);
            float edge = edgeBand(face);
            for (int mask = 0; mask < 256; mask++) {
                var patches = patches(face, mask);
                assertTrue(patches.size() == 9 || patches.size() == 25);
                double area = 0;
                for (var patch : patches) {
                    assertTrue(patch.x0() < patch.x1() && patch.y0() < patch.y1());
                    area += (patch.x1() - patch.x0()) * (patch.y1() - patch.y0());
                    for (float coordinate : new float[] {patch.x0(), patch.y0(), patch.x1(), patch.y1(),
                            patch.u0(), patch.v0(), patch.u1(), patch.v1()}) {
                        assertTrue(Float.isFinite(coordinate) && coordinate >= 0 && coordinate <= 1);
                    }
                }
                assertEquals(1.0, area, 1.0E-7);
                for (var center : patches.stream().filter(p -> p.x0() >= edge && p.x1() <= 1 - edge
                        && p.y0() >= edge && p.y1() <= 1 - edge).toList()) {
                    assertFalse(center.remapped());
                    assertFalse(center.useCasingBackground(face));
                }
                for (int a = 0; a < patches.size(); a++) for (int b = a + 1; b < patches.size(); b++) {
                    var first = patches.get(a);
                    var second = patches.get(b);
                    boolean intersects = Math.min(first.x1(), second.x1()) > Math.max(first.x0(), second.x0())
                            && Math.min(first.y1(), second.y1()) > Math.max(first.y0(), second.y0());
                    assertFalse(intersects, "Patches must not overlap and double-blend transparent glass");
                }
            }
            assertTrue(patches(face, 0).stream().noneMatch(Patch::remapped));
        }
    }

    @Test
    void reflectedEdgesMeetTheOriginalCenterAndIconEdgesRequestWhiteBackgroundOnlyWhenRemapped() {
        var casing = face("casing");
        var all = patches(casing, 255);
        var left = all.get(3);
        var right = all.get(5);
        var center = all.get(4);
        assertEquals(6.0F / 16.0F, left.u0());
        assertEquals(3.0F / 16.0F, left.u1());
        assertEquals(center.u0(), left.u1());
        assertEquals(center.u1(), right.u0());
        assertEquals(10.0F / 16.0F, right.u1());
        assertEquals(center.v0(), all.get(1).v1());
        assertEquals(center.v1(), all.get(7).v0());
        var glassLeft = patches(face("glass"), L).get(3);
        assertEquals(2.0F / 16.0F, glassLeft.u0());
        assertEquals(1.0F / 16.0F, glassLeft.u1());
        var icon = face("controller");
        var iconPatches = patches(icon, L);
        assertTrue(iconPatches.get(3).useCasingBackground(icon));
        assertFalse(iconPatches.get(4).useCasingBackground(icon));
        assertFalse(iconPatches.get(5).useCasingBackground(icon));
        assertFalse(patches(icon, L | T).get(0).useCasingBackground(icon), "Concave corners must keep their original frame");
        assertTrue(all.stream().noneMatch(p -> p.useCasingBackground(casing)));
        var coilTop = face("coil_top");
        assertTrue(patches(coilTop, L).get(3).useCasingBackground(coilTop));
        assertFalse(patches(coilTop, L).get(4).useCasingBackground(coilTop));
    }

    private static Face face(String material) {
        return new Face(PREFIX + material, Direction.EAST, Direction.DOWN);
    }

    private static BlockPos at(Face face, int u, int v) {
        return ORIGIN.relative(face.u(), u).relative(face.v(), v);
    }

    private static Map<BlockPos, Face> neighborhood(Face face) {
        var result = new HashMap<BlockPos, Face>();
        for (int u = -1; u <= 1; u++) for (int v = -1; v <= 1; v++) result.put(at(face, u, v), face);
        return result;
    }

    private static int mask(Face face, Map<BlockPos, Face> neighbors) {
        return connections(ORIGIN, Direction.NORTH, face, (pos, normal) -> neighbors.get(pos));
    }
}
