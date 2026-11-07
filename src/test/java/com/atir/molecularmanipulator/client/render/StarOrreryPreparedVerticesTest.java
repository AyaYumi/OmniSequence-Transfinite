package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Exercises the real native pack/upload boundary without Sodium, an Iris buffer, or a GL context. */
class StarOrreryPreparedVerticesTest {
    private static final SingularityVertexLayout.State FULL = new SingularityVertexLayout.State(
            SingularityVertexLayout.of(DefaultVertexFormat.NEW_ENTITY), 0, false);
    private static final long MATERIAL_A = 0x0000123456789ABCL, MATERIAL_B = 0x0000FEDCBA987654L;

    @Test void fullAttributesPackIntoCompactBatchesWithTheCurrentTargetMaterial() {
        var expected = new RecordingColorConsumer();
        quads(expected, 131);
        try (var prepared = new PreparedEffectVertices()) {
            quads(prepared.begin(FULL), 131);
            var target = compact(MATERIAL_B);
            prepared.submit(target.consumer);
            assertEquals(List.of(512, 12), target.sizes,
                    "A full→compact mismatch must pack batches, not replay one quad at a time");
            assertEquals(expected.vertices(), target.vertices());
            target.assertMaterials(MATERIAL_B);
            assertNotEquals(address(prepared), target.addresses.getFirst().longValue());
            var ordinary = new RecordingColorConsumer();
            prepared.submit(ordinary);
            assertEquals(expected.vertices(), ordinary.vertices());
        }
    }

    @Test void adaptiveColorStrideValidatesMaterialsWithoutRelabelingAlreadyPreparedBytes() {
        var expected = new RecordingColorConsumer();
        quads(expected, 2);
        try (var prepared = new PreparedColorVertices()) {
            quads(prepared.begin(), 2);
            assertEquals(16, field(prepared, "stride"));
            var cold = compact(MATERIAL_A);
            prepared.submit(cold.consumer);
            cold.assertMaterials(MATERIAL_A);

            quads(prepared.begin(), 2);
            assertEquals(24, field(prepared, "stride"));
            long source = address(prepared);
            byte[] original = bytes(source, prepared.count() * 24);
            var stable = compact(MATERIAL_A);
            prepared.submit(stable.consumer);
            assertEquals(List.of(source), stable.addresses, "Stable materials must submit worker storage directly");
            assertArrayEquals(original, stable.bytes());

            // Submitting the same storage again must still validate its ORIGINAL material, not
            // the next-frame hint learned from the preceding submission to a different target.
            for (int repeat = 0; repeat < 2; repeat++) {
                var changed = compact(MATERIAL_B);
                prepared.submit(changed.consumer);
                changed.assertMaterials(MATERIAL_B);
                assertEquals(expected.vertices(), changed.vertices());
                assertNotEquals(source, changed.addresses.getFirst().longValue());
                assertArrayEquals(original, bytes(source, original.length));
            }
            quads(prepared.begin(), 2);
            var nowStable = compact(MATERIAL_B);
            prepared.submit(nowStable.consumer);
            assertEquals(List.of(address(prepared)), nowStable.addresses);
            nowStable.assertMaterials(MATERIAL_B);

            var colorOnly = new RecordingBatch(new SingularityVertexLayout.State(
                    SingularityVertexLayout.of(DefaultVertexFormat.POSITION_COLOR), 0, false));
            prepared.submit(colorOnly.consumer);
            assertEquals(expected.vertices(), colorOnly.vertices(), "24→16 must drop only material bytes");
            quads(prepared.begin(), 2);
            assertEquals(16, field(prepared, "stride"));
            var plain = new RecordingColorConsumer();
            prepared.submit(plain);
            assertEquals(expected.vertices(), plain.vertices());
        }
    }

    @Test void cpuNativeFallbackRetainsTheCompleteSynchronousAttributes() {
        var expected = new RecordingBatch(FULL);
        try (var batch = SingularityShaderVertices.batch(expected.consumer)) { quads(expected.consumer, 131); }
        try (var full = new PreparedEffectVertices(); var colors = new PreparedColorVertices()) {
            quads(full.begin(FULL), 131);
            var direct = new RecordingBatch(FULL);
            full.submit(direct.consumer);
            assertEquals(List.of(address(full)), direct.addresses);
            assertArrayEquals(expected.bytes(), direct.bytes());

            // This is the transition frame after GPU attributes become unavailable. Complete
            // UVs, overlay, light, winding and normals must be regenerated for the CPU target.
            colors.captureTarget(compact(MATERIAL_A).consumer);
            quads(colors.begin(), 131);
            var fallback = new RecordingBatch(FULL);
            try (var batch = SingularityShaderVertices.batch(fallback.consumer)) { colors.submit(fallback.consumer); }
            assertArrayEquals(expected.bytes(), fallback.bytes());
            assertEquals(16, field(colors, "nextStride"));
        }
    }

    @Test void compactPackingFlushesPrecedingQuadsAndPreservesTheFollowingBatchOrder() {
        var expected = new RecordingColorConsumer();
        quads(expected, 1);
        quads(expected, 131);
        quads(expected, 2);
        try (var prepared = new PreparedEffectVertices()) {
            quads(prepared.begin(FULL), 131);
            var target = compact(MATERIAL_B);
            try (var batch = SingularityShaderVertices.batch(target.consumer)) {
                quads(target.consumer, 1);
                prepared.submit(target.consumer);
                quads(target.consumer, 2);
            }
            assertEquals(List.of(4, 512, 12, 8), target.sizes);
            assertEquals(expected.vertices(), target.vertices());
            target.assertMaterials(MATERIAL_B);
        }
    }

    @Test void nativeSubmissionCannotInterruptAnIncompleteQuad() {
        try (var full = new PreparedEffectVertices(); var colors = new PreparedColorVertices()) {
            quads(full.begin(FULL), 1);
            quads(colors.begin(), 1);
            var target = compact(MATERIAL_A);
            target.consumer.addVertex(0, 0, 0).setColor(0x12345678);
            assertThrows(IllegalStateException.class, () -> full.submit(target.consumer));
            assertThrows(IllegalStateException.class, () -> colors.submit(target.consumer));
            assertTrue(target.sizes.isEmpty());
        }
    }

    @Test void reusableWorkerFramesSwitchGpuCpuAndOrdinaryModesWithoutChangingAnyPass() {
        assumeTrue(Runtime.getRuntime().availableProcessors() >= 4);
        // First CPU frame after compact uses color fallback; the next restores full attributes.
        int[] modes = {24, 24, 36, 36, 16, 24, 24};
        boolean[] fullPreparation = {true, false, false, true, false, true, false};
        try (var parallel = new StarOrreryParallelEffects()) {
            for (int index = 0; index < modes.length; index++) {
                int mode = modes[index];
                try (var frame = start(parallel, mode == 16 ? null : FULL, index)) {
                    assertNotNull(frame);
                    for (int pass = 0; pass < 4; pass++) {
                        var expected = new RecordingColorConsumer();
                        renderPass(expected, pass, index);
                        if (mode == 16) {
                            var target = new RecordingColorConsumer();
                            submit(frame, target, pass);
                            assertEquals(expected.vertices(), target.vertices());
                        } else {
                            long material = material(index, pass);
                            var target = mode == 24 ? compact(material) : new RecordingBatch(FULL);
                            try (var batch = SingularityShaderVertices.batch(target.consumer)) {
                                submit(frame, target.consumer, pass);
                            }
                            assertEquals(expected.vertices(), target.vertices(), "Pass order/ARGB: " + pass);
                            if (mode == 24) target.assertMaterials(material);
                            else {
                                var attributes = new RecordingBatch(FULL);
                                try (var batch = SingularityShaderVertices.batch(attributes.consumer)) {
                                    renderPass(attributes.consumer, pass, index);
                                }
                                assertArrayEquals(attributes.bytes(), target.bytes(), "Complete CPU attributes: " + pass);
                            }
                        }
                    }
                    for (var output : outputs(parallel)) {
                        assertEquals(fullPreparation[index], field(output, "fullAttributes"));
                        if (mode == 24 && !fullPreparation[index])
                            assertEquals(24, field(field(output, "colors"), "stride"));
                    }
                }
            }
        }
    }

    @Test void mixedGpuAndCpuPassesTrainIndependentRepresentationsAndMaterialHints() {
        assumeTrue(Runtime.getRuntime().availableProcessors() >= 4);
        try (var parallel = new StarOrreryParallelEffects()) {
            for (int index = 0; index < 2; index++) {
                try (var frame = start(parallel, FULL, index)) {
                    assertNotNull(frame);
                    for (int pass = 0; pass < 4; pass++) {
                        long material = material(index, pass);
                        var target = (pass & 1) == 0 ? compact(material) : new RecordingBatch(FULL);
                        try (var batch = SingularityShaderVertices.batch(target.consumer)) {
                            submit(frame, target.consumer, pass);
                        }
                        var expected = new RecordingColorConsumer();
                        renderPass(expected, pass, index);
                        assertEquals(expected.vertices(), target.vertices());
                        if ((pass & 1) == 0) target.assertMaterials(material);
                    }
                    if (index == 1) {
                        for (var output : anchorOutputs(parallel)) assertEquals(false, field(output, "fullAttributes"));
                        var traces = field(field(parallel, "frame"), "traces");
                        assertEquals(false, field(field(traces, "depth"), "fullAttributes"));
                        assertEquals(false, field(field(traces, "glow"), "fullAttributes"));
                        assertEquals(true, field(field(traces, "rain"), "fullAttributes"));
                        assertEquals(true, field(field(traces, "rainGlow"), "fullAttributes"));
                    }
                }
            }
        }
    }

    @Test void failedNativeAppendIsNotRetriedOrReplayedByEitherClose() {
        assumeTrue(Runtime.getRuntime().availableProcessors() >= 4);
        // Check both cold full→compact packing and the trained color direct path.
        for (boolean trained : new boolean[]{false, true}) {
            try (var parallel = new StarOrreryParallelEffects()) {
                if (trained) try (var warmup = start(parallel, FULL, 0)) {
                    assertNotNull(warmup);
                    for (int pass = 0; pass < 4; pass++) submit(warmup, compact(material(0, pass)).consumer, pass);
                }
                var failure = new IllegalStateException("Partial native append");
                var target = compact(material(0, 0));
                target.failAt = 2;
                target.failure = failure;
                var frame = start(parallel, FULL, 1);
                assertNotNull(frame);
                try (frame; var batch = SingularityShaderVertices.batch(target.consumer)) {
                    assertSame(failure, assertThrows(IllegalStateException.class, () -> frame.crystal(target.consumer)));
                    assertEquals(2, target.sizes.size());
                    assertThrows(IllegalStateException.class, () -> frame.crystal(target.consumer));
                    assertThrows(IllegalStateException.class, () -> frame.glow(target.consumer));
                    assertThrows(IllegalStateException.class, () -> frame.rain(target.consumer, false));
                    assertThrows(IllegalStateException.class, () -> frame.rain(target.consumer, true));
                }
                assertEquals(2, target.sizes.size(), "Neither batch.close nor frame.close may replay a failed push");
            }
        }
    }

    private static StarOrreryParallelEffects.Frame start(StarOrreryParallelEffects parallel,
            SingularityVertexLayout.State state, int sample) {
        return parallel.startCaptured(poses(sample), 6, 1, Vec3.ZERO, sample + 3.5F,
                sample + 1.25F, 0, false, state);
    }

    private static void submit(StarOrreryParallelEffects.Frame frame, VertexConsumer target, int pass) {
        switch (pass) {
            case 0 -> frame.crystal(target);
            case 1 -> frame.rain(target, false);
            case 2 -> frame.glow(target);
            case 3 -> frame.rain(target, true);
            default -> throw new AssertionError(pass);
        }
    }

    private static void renderPass(VertexConsumer target, int pass, int sample) {
        var poses = poses(sample);
        float time = sample + 3.5F, flowTime = sample + 1.25F;
        switch (pass) {
            case 0 -> {
                StarOrreryAnchorEffects.render(poses, target, 6, 1, Vec3.ZERO, time, false);
                StarOrreryEffects.render(poses, target, 6, 1, 0, time, false, false);
            }
            case 2 -> {
                StarOrreryEffects.render(poses, target, 6, 1, 0, time, false, true);
                StarOrreryAnchorEffects.render(poses, target, 6, 1, Vec3.ZERO, time, true);
            }
            case 1, 3 -> {
                StarOrreryCoreEffects.renderRain(poses, target, 6, 1, 0, flowTime, pass == 3);
                StarOrreryCoreEffects.renderBuildingRain(poses, target, 6, 1, 0, flowTime, time, pass == 3);
            }
            default -> throw new AssertionError(pass);
        }
    }

    private static PoseStack poses(int sample) {
        var poses = new PoseStack();
        poses.translate(13.25 + sample, -7.5, 21.75);
        poses.scale(.28F, -.4F, .18F);
        return poses;
    }

    private static long material(int sample, int pass) {
        return (MATERIAL_A ^ (sample + 1L) << 32 ^ (pass + 1L) << 16) & 0xFFFFFFFFFFFFL;
    }

    private static RecordingBatch compact(long materials) {
        return new RecordingBatch(new SingularityVertexLayout.State(
                SingularityVertexLayout.of(SingularityVertexLayout.COLOR_MATERIAL_FORMAT), materials, false));
    }

    private static List<Object> anchorOutputs(StarOrreryParallelEffects parallel) {
        var result = new ArrayList<>();
        for (var task : (Object[]) field(field(parallel, "frame"), "anchors")) {
            result.add(field(task, "depth"));
            result.add(field(task, "glow"));
        }
        return result;
    }

    private static List<Object> outputs(StarOrreryParallelEffects parallel) {
        var result = anchorOutputs(parallel);
        var traces = field(field(parallel, "frame"), "traces");
        for (var name : List.of("depth", "glow", "rain", "rainGlow")) result.add(field(traces, name));
        return result;
    }

    private static Object field(Object instance, String name) {
        try {
            var field = instance.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static long address(Object prepared) {
        return MemoryUtil.memAddress((ByteBuffer) field(prepared, "storage"));
    }

    private static byte[] bytes(long address, int length) {
        byte[] bytes = new byte[length];
        MemoryUtil.memByteBuffer(address, length).get(bytes);
        return bytes;
    }

    /** Varied colors, nonplanar corners, reverse winding, and both degeneracy fallbacks. */
    private static void quads(VertexConsumer out, int count) {
        for (int quad = 0; quad < count; quad++) {
            float x = quad * 1.25F;
            float[][] corners = switch (quad % 4) {
                case 0 -> new float[][]{{x, 0, -0F}, {x + 1, 0, 0}, {x + 1, 1, .25F}, {x, 1, 0}};
                case 1 -> new float[][]{{x, 0, 0}, {x, 1, 0}, {x + 1, 1, 0}, {x + 1, 0, 0}};
                case 2 -> new float[][]{{x, 0, 0}, {x + 1, 0, 0}, {x + 2, 0, 0}, {x, 1, 0}};
                default -> new float[][]{{x, 0, 0}, {x + 1, 0, 0}, {x + 2, 0, 0}, {x + 3, 0, 0}};
            };
            for (int corner = 0; corner < 4; corner++) {
                var vertex = corners[corner];
                out.addVertex(vertex[0], vertex[1], vertex[2])
                        .setColor(0x1736598B + quad * 0x01020304 + corner * 0x10203040);
            }
        }
    }

    private static final class RecordingBatch implements SingularityShaderVertices.NativeBatch {
        final SingularityVertexLayout layout;
        final SingularityShaderVertices consumer;
        final List<Integer> sizes = new ArrayList<>();
        final List<Long> addresses = new ArrayList<>();
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final Thread renderThread = Thread.currentThread();
        int failAt;
        RuntimeException failure;

        RecordingBatch(SingularityVertexLayout.State state) {
            layout = state.layout();
            consumer = new SingularityShaderVertices(state, this);
        }

        @Override public void push(MemoryStack stack, long address, int vertices) {
            assertSame(renderThread, Thread.currentThread(), "Workers cannot write a live target");
            assertEquals(0, vertices & 3);
            sizes.add(vertices);
            addresses.add(address);
            output.writeBytes(StarOrreryPreparedVerticesTest.bytes(address, vertices * layout.stride));
            if (sizes.size() == failAt) throw failure; // Simulate an append that has already changed the builder.
        }

        byte[] bytes() { return output.toByteArray(); }

        List<RecordingColorConsumer.Vertex> vertices() {
            var data = ByteBuffer.wrap(bytes()).order(ByteOrder.nativeOrder());
            var result = new ArrayList<RecordingColorConsumer.Vertex>();
            for (int offset = 0; offset < data.limit(); offset += layout.stride) {
                int color = data.getInt(offset + layout.color);
                result.add(new RecordingColorConsumer.Vertex(data.getFloat(offset + layout.position),
                        data.getFloat(offset + layout.position + 4), data.getFloat(offset + layout.position + 8),
                        color & 0xFF00FF00 | color >>> 16 & 255 | (color & 255) << 16));
            }
            return result;
        }

        void assertMaterials(long expected) {
            assertEquals(24, layout.stride);
            var data = ByteBuffer.wrap(bytes()).order(ByteOrder.nativeOrder());
            for (int offset = 0; offset < data.limit(); offset += layout.stride)
                assertEquals(expected, data.getLong(offset + 16), "Current per-vertex material");
        }
    }
}
