package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.atir.molecularmanipulator.blockentity.SingularityMotionGeometry;
import com.atir.molecularmanipulator.client.render.ParallelEffectGeometry;
import com.atir.molecularmanipulator.client.render.RecordingColorConsumer;
import com.atir.molecularmanipulator.client.render.SingularityStructureEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Arrays;
import org.joml.Matrix4f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("ParallelEffectGeometry")
class CurrentParallelGeometryTest {
    private static final int FIELD = 0x123456, CORE = 0x234567, PRIMARY = 0x345678;
    private static final int SECONDARY = 0x456789, LATTICE = 0x56789A;

    @AfterEach void clear() { ParallelEffectGeometry.clear(); }

    @Test void omniOrbitTasksRetainEveryVertexAndPassOrderWithFrozenTransforms() {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        try (var geometry = new ParallelEffectGeometry()) {
            for (int variant = 0; variant < 3; variant++) {
                double angle = variant == 2 ? 10000000 : variant * 67.25;
                float activity = variant * .6F, completion = variant == 1 ? .75F : 0;
                var poses = poses(variant);
                var before = new Matrix4f(poses.last().pose());
                try (var frame = OmniOrbitalEffects.prepare(geometry, poses, angle, activity, completion, true)) {
                    assertNotNull(frame);
                    assertEquals(before, poses.last().pose());
                    poses.translate(900, -500, 200);
                    for (boolean glow : new boolean[]{false, true}) {
                        var expected = new RecordingColorConsumer();
                        OmniOrbitalEffects.render(poses(variant), expected, angle, activity, completion, true, glow);
                        var actual = new RecordingColorConsumer();
                        OmniOrbitalEffects.submit(frame, actual, glow);
                        assertEquals(expected.vertices(), actual.vertices(), "Omni variant=" + variant + " glow=" + glow);
                    }
                }
            }
        }
    }

    @Test void omniDoubleSidedFacesMatchTheOriginalJomlCornerTransform() {
        for (boolean detailed : new boolean[]{false, true}) for (boolean glow : new boolean[]{false, true}) {
            var local = new RecordingColorConsumer();
            OmniOrbitalEffects.render(new PoseStack(), local, 67.25, .7F, .5F, detailed, glow);
            for (int variant = 0; variant < 3; variant++) {
                var poses = poses(variant);
                var expected = new RecordingColorConsumer();
                for (var vertex : local.vertices()) {
                    expected.addVertex(poses.last(), vertex.x(), vertex.y(), vertex.z()).setColor(vertex.argb());
                }
                var actual = new RecordingColorConsumer();
                OmniOrbitalEffects.render(poses, actual, 67.25, .7F, .5F, detailed, glow);
                assertEquals(expected.vertices(), actual.vertices(), "Omni transformed variant=" + variant);
            }
        }
    }

    @Test void molecularDepthSurfaceAndGlowMatchWithFrozenClockAndCustomColors() {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        try (var geometry = new ParallelEffectGeometry()) {
            for (int variant = 0; variant < 3; variant++) {
                float angle = 67.25F + variant * 91, activity = variant * .5F;
                float completion = variant == 1 ? .75F : 0;
                double clockTicks = 86399 * 20.0 + variant * 7;
                var poses = poses(variant);
                var before = new Matrix4f(poses.last().pose());
                try (var frame = MolecularCenterRenderer.prepareFeatherPasses(geometry, poses,
                        angle, activity, completion, 2, FIELD, CORE, PRIMARY, SECONDARY, LATTICE, clockTicks)) {
                    assertNotNull(frame);
                    assertEquals(before, poses.last().pose());
                    poses.translate(900, -500, 200);
                    for (int pass = 0; pass < 3; pass++) {
                        var expected = new RecordingColorConsumer();
                        if (pass == 1) {
                            FeatherResonanceEffects.renderCoreSurface(poses(variant), expected, angle, CORE, SECONDARY);
                        } else {
                            MolecularCenterRenderer.renderFeatherPass(poses(variant), expected, angle,
                                    activity, completion, 2, pass == 2, FIELD, CORE, PRIMARY, SECONDARY, LATTICE, clockTicks);
                        }
                        var actual = new RecordingColorConsumer(); frame.submit(pass, actual);
                        assertEquals(expected.vertices(), actual.vertices(), "Molecular variant=" + variant + " pass=" + pass);
                    }
                }
            }
        }
    }

    @Test void singularityPassesMatchWithIndependentStacksAndACopiedMotionArray() {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        try (var geometry = new ParallelEffectGeometry()) {
            for (int variant = 0; variant < 3; variant++) {
                var motion = new SingularityMotionGeometry.Pose[SingularityMotionGeometry.GROUP_COUNT];
                for (int group = 0; group < motion.length; group++) {
                    if (group % 3 != variant) motion[group] = SingularityMotionGeometry.runningPose(group, 31 + variant * 151.25);
                }
                var frozenMotion = motion.clone();
                var effect = new SingularityStructureEffects.Frame(237.25F + variant * 12000, variant * 80, variant * .4F);
                var poses = poses(variant);
                var before = new Matrix4f(poses.last().pose());
                try (var frame = SingularityRenderer.preparePasses(geometry, poses, effect, true, motion, true)) {
                    assertNotNull(frame);
                    assertEquals(before, poses.last().pose());
                    poses.translate(900, -500, 200);
                    Arrays.fill(motion, new SingularityMotionGeometry.Pose(150, 100));
                    for (int pass = 0; pass < 4; pass++) {
                        var expectedPoses = poses(variant);
                        var expected = new RecordingColorConsumer();
                        SingularityRenderer.renderPass(expectedPoses, expected, pass, effect, true, frozenMotion, true);
                        assertEquals(before, expectedPoses.last().pose());
                        var actual = new RecordingColorConsumer(); frame.submit(pass, actual);
                        assertEquals(expected.vertices(), actual.vertices(), "Singularity variant=" + variant + " pass=" + pass);
                    }
                }
            }
        }
    }

    @Test void foundryPassesRetainClippedWorkpiecesAndFacingsWithFrozenSnapshots() {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        try (var geometry = new ParallelEffectGeometry()) {
            for (int item = 0; item < 5; item++) for (var facing : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                for (float progress : new float[]{0, .43F, 1}) {
                    var raster = new com.atir.molecularmanipulator.client.render.MatterRasterEffects.Frame(
                            item, .8F, progress, true, progress < 1, progress == 1 ? .65F : 0, 37);
                    var stellar = new com.atir.molecularmanipulator.client.render.MatterStellarEffects.Frame(
                            .7F, 197.25F, com.atir.molecularmanipulator.research.ResearchVisualState.EMPTY, 0, 0);
                    var input = poses(item % 3);
                    try (var frame = MatterFabricationRenderer.preparePasses(geometry, input,
                            237.25F, true, facing, raster, stellar)) {
                        assertNotNull(frame);
                        input.translate(900, -500, 200);
                        for (var pass : MatterFabricationRenderer.FoundryPass.values()) {
                            var expected = new RecordingColorConsumer();
                            MatterFabricationRenderer.renderFabricationPass(poses(item % 3), expected,
                                    237.25F, true, true, facing, pass, raster, stellar);
                            var actual = new RecordingColorConsumer(); frame.submit(pass.ordinal(), actual);
                            assertEquals(expected.vertices(), actual.vertices(),
                                    "Matter item=" + item + " facing=" + facing + " progress=" + progress + " pass=" + pass);
                        }
                    }
                }
            }
        }
    }

    @Test void smallCurrentEffectsKeepTheSynchronousPath() {
        try (var geometry = new ParallelEffectGeometry()) {
            assertNull(OmniOrbitalEffects.prepare(geometry, poses(0), 67, 1, .5F, false));
            assertNull(MolecularCenterRenderer.prepareFeatherPasses(geometry, poses(0), 67, 1, .5F,
                    1, FIELD, CORE, PRIMARY, SECONDARY, LATTICE, 20));
            var effect = SingularityStructureEffects.Frame.steady(67);
            assertNull(SingularityRenderer.preparePasses(geometry, poses(0), effect, false, null, true));
            assertNull(SingularityRenderer.preparePasses(geometry, poses(0), effect, true, null, false));
            // The synchronous test/render entry points remain usable without prepared geometry.
            var out = new RecordingColorConsumer();
            OmniComputationRenderer.renderCrownPass(poses(0), out, 67, 1, .5F, true, false);
            assertFalse(out.vertices().isEmpty());
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
}
