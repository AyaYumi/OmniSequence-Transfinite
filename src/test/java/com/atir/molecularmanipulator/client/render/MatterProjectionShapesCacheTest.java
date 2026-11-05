package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReferenceArray;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class MatterProjectionShapesCacheTest {
    private static final float[] WIDTHS = {0F, .024F, .065F, .009F, .025F};
    private static final float[] PROGRESS = {0F, .25F, .5F, .75F, 1F};

    @Test
    void everyConceptAndRuntimeWidthPreservesAllVerticesColorsWindingAndOrder() {
        for (int item = 0; item < MatterProjectionShapes.COUNT; item++) for (float width : WIDTHS) {
            var pose = new PoseStack().last();
            var expected = capture(true, pose, item, 165, width);
            assertFalse(expected.isEmpty(), scenario(item, width));
            assertEquals(0, expected.size() % 4, scenario(item, width));
            assertEquals(expected, capture(false, pose, item, 165, width), scenario(item, width));
            // Replay must not consume, reorder, or otherwise mutate the cached arrays.
            assertEquals(expected, capture(false, pose, item, 165, width), scenario(item, width));
        }
    }

    @Test
    void warmCacheAppliesCallerAlphaDynamicallyIncludingClampingAndTransparentGeometry() {
        int[] alphas = {Integer.MIN_VALUE, 0, 3, 14, 30, 165, 185, 235, 255, Integer.MAX_VALUE};
        for (int item = 0; item < MatterProjectionShapes.COUNT; item++) for (float width : WIDTHS) {
            var pose = new PoseStack().last();
            var opaque = capture(true, pose, item, 255, width);
            capture(false, pose, item, 123, width);
            for (int alpha : alphas) {
                int clamped = Math.max(0, Math.min(255, alpha));
                var expected = opaque.stream().map(vertex -> new RecordingColorConsumer.Vertex(
                        vertex.x(), vertex.y(), vertex.z(), clamped << 24 | vertex.rgb())).toList();
                assertEquals(expected, capture(false, pose, item, alpha, width),
                        scenario(item, width) + ", alpha=" + alpha);
            }
            // Check the derived expectations against the procedural alpha path as well.
            assertEquals(capture(true, pose, item, -1, width), capture(false, pose, item, -1, width));
            assertEquals(capture(true, pose, item, 256, width), capture(false, pose, item, 256, width));
        }
    }

    @Test
    void nonuniformAndMirroredCallerPosesAreAppliedExactlyOnceWithoutReshadingLocalFaces() {
        for (int item = 0; item < MatterProjectionShapes.COUNT; item++) for (float width : WIDTHS) {
            var local = capture(true, new PoseStack().last(), item, 185, width);
            var poses = List.of(transformed(.28F, .40F, .18F),
                    transformed(-.28F, .40F, .18F), transformed(.28F, -.40F, .18F));
            for (int poseIndex = 0; poseIndex < poses.size(); poseIndex++) {
                var pose = poses.get(poseIndex).last();
                var positionBefore = new Matrix4f(pose.pose());
                var normalBefore = new Matrix3f(pose.normal());
                var expected = capture(true, pose, item, 185, width);
                var actual = capture(false, pose, item, 185, width);
                String context = scenario(item, width) + ", pose=" + poseIndex;
                assertEquals(expected, actual, context);
                assertEquals(positionBefore, pose.pose(), context);
                assertEquals(normalBefore, pose.normal(), context);
                for (int vertex = 0; vertex < actual.size(); vertex++) {
                    assertEquals(local.get(vertex).rgb(), actual.get(vertex).rgb(), context);
                }
            }
        }
    }

    @Test
    void layerClipperRetainsTheExactPolygonsColorsAndOrderAtEveryConstructionProgress() {
        for (int item = 0; item < MatterProjectionShapes.COUNT; item++) for (float width : WIDTHS) {
            var bounds = MatterProjectionShapes.bounds(item);
            var poses = List.of(new PoseStack(), transformed(.28F, .40F, .18F),
                    transformed(.28F, -.40F, .18F));
            for (int poseIndex = 0; poseIndex < poses.size(); poseIndex++) {
                var pose = poses.get(poseIndex).last();
                var inverse = new Matrix4f(pose.pose()).invert();
                var local = new Vector3f();
                for (float progress : PROGRESS) {
                    float height = bounds.bottom() - .03F
                            + (bounds.top() - bounds.bottom() + .06F) * progress;
                    var expected = clipped(true, pose, item, 185, width, height);
                    var actual = clipped(false, pose, item, 185, width, height);
                    String context = scenario(item, width) + ", pose=" + poseIndex + ", progress=" + progress;
                    assertEquals(expected, actual, context);
                    assertEquals(0, actual.size() % 4, context);
                    if (progress > 0 && progress < 1) assertFalse(actual.isEmpty(), context);
                    for (var vertex : actual) {
                        assertEquals(185, vertex.alpha(), context);
                        inverse.transformPosition(vertex.x(), vertex.y(), vertex.z(), local);
                        assertTrue(local.y <= height + 5E-5F, context);
                    }
                }
            }
        }
    }

    @Test
    void onlyTheFiveExactRuntimeWidthsSelectBoundedReusableCacheEntries() throws ReflectiveOperationException {
        // Observe memoization without exposing mutable cache internals or resetting shared test state.
        var field = MatterProjectionShapes.class.getDeclaredField("GEOMETRY_CACHE");
        field.setAccessible(true);
        var cache = (AtomicReferenceArray<?>) field.get(null);
        var selector = MatterProjectionShapes.class.getDeclaredMethod("cachedWidthIndex", float.class);
        selector.setAccessible(true);
        assertEquals(MatterProjectionShapes.COUNT * WIDTHS.length, cache.length());
        var entries = new Object[cache.length()];
        for (int item = 0; item < MatterProjectionShapes.COUNT; item++) {
            for (int widthIndex = 0; widthIndex < WIDTHS.length; widthIndex++) {
                float width = WIDTHS[widthIndex];
                assertEquals(widthIndex, ((Integer) selector.invoke(null, width)).intValue());
                int slot = item * WIDTHS.length + widthIndex;
                capture(false, new PoseStack().last(), item, 30, width);
                entries[slot] = cache.get(slot);
                assertNotNull(entries[slot], scenario(item, width));
                capture(false, transformed(-.28F, .40F, .18F).last(), item, 235, width);
                assertSame(entries[slot], cache.get(slot), scenario(item, width));
            }
        }
        for (float width : new float[]{.013F, -.024F, Math.nextDown(.024F), Math.nextUp(.024F),
                Float.MIN_VALUE, Float.MAX_VALUE, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
            assertEquals(-1, ((Integer) selector.invoke(null, width)).intValue(), "width=" + width);
        }
        // Signed zero is the same solid-geometry path in the original implementation.
        assertEquals(0, ((Integer) selector.invoke(null, -0F)).intValue());
        for (int item = 0; item < MatterProjectionShapes.COUNT; item++) {
            var pose = transformed(-.28F, .40F, .18F).last();
            for (float width : new float[]{.013F, -.024F, Math.nextDown(.024F), Math.nextUp(.024F), Float.MIN_VALUE, -0F}) {
                assertEquals(capture(true, pose, item, 165, width), capture(false, pose, item, 165, width),
                        scenario(item, width));
            }
        }
        for (int slot = 0; slot < entries.length; slot++) assertSame(entries[slot], cache.get(slot));
    }

    @Test
    void immutableGeometrySupportsConcurrentReplayWithIndependentTransformsAndAlpha() throws Exception {
        try (var workers = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Callable<List<RecordingColorConsumer.Vertex>>>();
            for (int index = 0; index < 8; index++) {
                int input = index;
                tasks.add(() -> capture(false, workerPose(input).last(), 2, 3 + input * 37, .065F));
            }
            var results = workers.invokeAll(tasks);
            for (int index = 0; index < results.size(); index++) {
                assertEquals(capture(true, workerPose(index).last(), 2, 3 + index * 37, .065F),
                        results.get(index).get(), "worker=" + index);
            }
        }
    }

    @Test
    void invalidConceptIndicesRetainTheProceduralExceptionWithoutEmittingVertices() {
        for (int item : new int[]{Integer.MIN_VALUE, -1, MatterProjectionShapes.COUNT, Integer.MAX_VALUE}) {
            for (float width : new float[]{0F, .024F, .013F, Float.NaN}) {
                var pose = new PoseStack().last();
                var expectedOutput = new RecordingColorConsumer();
                var actualOutput = new RecordingColorConsumer();
                var expected = assertThrows(IllegalArgumentException.class,
                        () -> MatterProjectionShapes.renderDirect(pose, expectedOutput, item, 165, width));
                var actual = assertThrows(IllegalArgumentException.class,
                        () -> MatterProjectionShapes.render(pose, actualOutput, item, 165, width));
                assertEquals(expected.getMessage(), actual.getMessage());
                assertTrue(expectedOutput.vertices().isEmpty());
                assertTrue(actualOutput.vertices().isEmpty());
            }
        }
    }

    private static List<RecordingColorConsumer.Vertex> capture(boolean direct, PoseStack.Pose pose,
            int item, int alpha, float width) {
        var output = new RecordingColorConsumer();
        render(direct, pose, output, item, alpha, width);
        return output.vertices();
    }

    private static List<RecordingColorConsumer.Vertex> clipped(boolean direct, PoseStack.Pose pose,
            int item, int alpha, float width, float height) {
        var output = new RecordingColorConsumer();
        render(direct, pose, new MatterLayerClipper(output, pose, height), item, alpha, width);
        return output.vertices();
    }

    private static void render(boolean direct, PoseStack.Pose pose, VertexConsumer output,
            int item, int alpha, float width) {
        if (direct) MatterProjectionShapes.renderDirect(pose, output, item, alpha, width);
        else MatterProjectionShapes.render(pose, output, item, alpha, width);
    }

    private static PoseStack transformed(float xScale, float yScale, float zScale) {
        var poses = new PoseStack();
        poses.translate(7.5, -3.25, 12.75);
        poses.mulPose(Axis.YP.rotationDegrees(37));
        poses.mulPose(Axis.XP.rotationDegrees(-23));
        poses.mulPose(Axis.ZP.rotationDegrees(19));
        poses.scale(xScale, yScale, zScale);
        return poses;
    }

    private static PoseStack workerPose(int input) {
        var poses = transformed(input % 2 == 0 ? -.28F : .28F, .40F, .18F);
        poses.translate(input * .25, input * -.125, input * .375);
        poses.mulPose(Axis.YP.rotationDegrees(input * 17));
        return poses;
    }

    private static String scenario(int item, float width) {
        return MatterProjectionShapes.conceptName(item) + ", width=" + width;
    }
}
