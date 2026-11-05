package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.atir.molecularmanipulator.blockentity.SingularityMotionGeometry;
import com.atir.molecularmanipulator.client.render.MatterRasterEffects;
import com.atir.molecularmanipulator.client.render.MatterStellarEffects;
import com.atir.molecularmanipulator.client.render.ParallelEffectGeometry;
import com.atir.molecularmanipulator.client.render.RecordingColorConsumer;
import com.atir.molecularmanipulator.client.render.SingularityShaderVertices;
import com.atir.molecularmanipulator.client.render.SingularityStructureEffects;
import com.atir.molecularmanipulator.research.ResearchVisualState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("ParallelEffectGeometry")
class PartitionedEffectGeometryTest {
    @AfterEach void clear() { ParallelEffectGeometry.clear(); }

    @Test void everyMatterPassMatchesAllEntityAttributesWithResearchClippingFacingsAndFrozenInputs() {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        var research = new ResearchVisualState(List.of(
                new ResearchVisualState.Task(1, 0, 8, 130, 500, true),
                new ResearchVisualState.Task(2, 1, 5, 230, 500, false),
                new ResearchVisualState.Task(3, 2, 3, 330, 500, true),
                new ResearchVisualState.Task(4, 3, 1, 430, 500, true)), 9, 3);
        try (var geometry = new ParallelEffectGeometry()) {
            for (int item = 0; item < 5; item++) for (var facing : Direction.Plane.HORIZONTAL) {
                for (int state = 0; state < 3; state++) {
                    int variant = (item + state) % 3;
                    float progress = state == 0 ? .43F : state == 1 ? 0 : 1;
                    var raster = new MatterRasterEffects.Frame(item, .8F, progress, state == 0,
                            state != 2, state == 2 ? .65F : 0, 37);
                    var stellar = new MatterStellarEffects.Frame(.7F, 197.25F, research, 2.5, .45F);
                    var input = poses(variant);
                    var before = new Matrix4f(input.last().pose());
                    try (var frame = MatterFabricationRenderer.preparePasses(geometry, input,
                            24037.25F, true, facing, raster, stellar)) {
                        assertNotNull(frame);
                        assertEquals(before, input.last().pose());
                        input.translate(900, -500, 200);
                        for (var pass : MatterFabricationRenderer.FoundryPass.values()) {
                            var expected = new Attributes();
                            MatterFabricationRenderer.renderFabricationPass(poses(variant), new SingularityShaderVertices(expected),
                                    24037.25F, true, true, facing, pass, raster, stellar);
                            var actual = new Attributes();
                            frame.submit(pass.ordinal(), new SingularityShaderVertices(actual));
                            assertFalse(expected.vertices.isEmpty());
                            assertEquals(expected.vertices, actual.vertices,
                                    "Matter item=" + item + " facing=" + facing + " state=" + state + " pass=" + pass);
                        }
                    }
                }
            }
        }
    }

    @Test void everySingularityPassMatchesAllEntityAttributesWithCopiedMotionAndIndependentStacks() {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        try (var geometry = new ParallelEffectGeometry()) {
            for (int variant = 0; variant < 4; variant++) {
                var motion = new SingularityMotionGeometry.Pose[SingularityMotionGeometry.GROUP_COUNT];
                for (int group = 0; group < motion.length; group++) {
                    if (group % 3 != variant) motion[group] = SingularityMotionGeometry.runningPose(group, 31 + variant * 151.25);
                }
                var frozen = motion.clone();
                var effect = new SingularityStructureEffects.Frame(237.25F + variant * 12000, variant * 80, variant * .4F);
                var input = poses(variant % 3);
                var before = new Matrix4f(input.last().pose());
                try (var frame = SingularityRenderer.preparePasses(geometry, input, effect, true, motion, true)) {
                    assertNotNull(frame);
                    assertEquals(before, input.last().pose());
                    input.translate(900, -500, 200);
                    Arrays.fill(motion, new SingularityMotionGeometry.Pose(150, 100));
                    for (int pass = 0; pass < 4; pass++) {
                        var expectedPoses = poses(variant % 3);
                        var expected = new Attributes();
                        SingularityRenderer.renderPass(expectedPoses, new SingularityShaderVertices(expected), pass,
                                effect, true, frozen, true);
                        assertEquals(before, expectedPoses.last().pose());
                        var actual = new Attributes();
                        frame.submit(pass, new SingularityShaderVertices(actual));
                        assertEquals(expected.vertices, actual.vertices, "Singularity variant=" + variant + " pass=" + pass);
                    }
                }
            }
        }
    }

    @Test void everyNaturalCeremonyComponentConcatenatesExactlyInBothStructureModes() {
        for (boolean detailed : new boolean[]{false, true}) for (boolean suspended : new boolean[]{false, true}) {
            for (int variant = 0; variant < 3; variant++) {
                var motion = new SingularityMotionGeometry.Pose[SingularityMotionGeometry.GROUP_COUNT];
                for (int group = 0; group < motion.length; group++) {
                    motion[group] = SingularityMotionGeometry.runningPose(group, 31 + variant * 151.25);
                }
                var effect = new SingularityStructureEffects.Frame(237.25F + variant * 12000, variant * 80, variant * .4F);
                var expected = new RecordingColorConsumer();
                SingularityStructureEffects.render(poses(variant), expected, effect, detailed, motion, suspended);
                var actual = new RecordingColorConsumer();
                for (int component = 0; component < 12; component++) {
                    var input = poses(variant);
                    var before = new Matrix4f(input.last().pose());
                    SingularityStructureEffects.renderRange(input, actual, effect, detailed, motion, suspended, component, component + 1);
                    assertEquals(before, input.last().pose());
                }
                assertEquals(expected.vertices(), actual.vertices(),
                        "Ceremony detailed=" + detailed + " suspended=" + suspended + " variant=" + variant);
            }
        }
    }

    private static PoseStack poses(int variant) {
        var poses = new PoseStack();
        poses.translate(17.5, -23.25, 51.75);
        poses.mulPose(Axis.YP.rotationDegrees(37 + variant * 90));
        poses.mulPose(Axis.ZP.rotationDegrees(-19));
        if (variant == 1) poses.scale(.28F, .40F, -.18F);
        if (variant == 2) poses.scale(.7F, .7F, .7F);
        return poses;
    }

    /** Compare positions, colors, UVs, overlay, lighting and normals in exact output order. */
    private static final class Attributes implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();
        @Override public void addVertex(float x, float y, float z, int color, float u, float v,
                int overlay, int light, float nx, float ny, float nz) {
            vertices.add(new Vertex(x, y, z, color, u, v, overlay, light, nx, ny, nz));
        }
        public VertexConsumer addVertex(float x, float y, float z) { throw new AssertionError("Expected complete entity vertex"); }
        public VertexConsumer setColor(int r, int g, int b, int a) { throw new AssertionError(); }
        public VertexConsumer setUv(float u, float v) { throw new AssertionError(); }
        public VertexConsumer setUv1(int u, int v) { throw new AssertionError(); }
        public VertexConsumer setUv2(int u, int v) { throw new AssertionError(); }
        public VertexConsumer setNormal(float x, float y, float z) { throw new AssertionError(); }
        private record Vertex(float x, float y, float z, int color, float u, float v,
                int overlay, int light, float nx, float ny, float nz) {}
    }
}
