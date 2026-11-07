package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class StarOrreryPreparedEffectsTest {
    @Test void preparedVerticesRetainGeometryAndColorsOnAnOrdinaryConsumer() {
        var poses = poses(17);
        var expected = new RecordingColorConsumer();
        StarOrreryEffects.render(poses, expected, 9, 3.5F, true, false);
        var state = new SingularityVertexLayout.State(SingularityVertexLayout.of(DefaultVertexFormat.NEW_ENTITY), 0, false);
        try (var prepared = new PreparedEffectVertices()) {
            StarOrreryEffects.render(poses, prepared.begin(state), 9, 3.5F, true, false);
            var actual = new RecordingColorConsumer();
            prepared.submit(actual);
            assertEquals(expected.vertices(), actual.vertices());
            // Reusing an output for an empty pass must not retain the previous frame.
            prepared.begin(state);
            var empty = new RecordingColorConsumer();
            prepared.submit(empty);
            assertTrue(empty.vertices().isEmpty());
        }
    }

    @Test void splitAnchorRangesConcatenateInTheOriginalOrderForEveryPass() {
        for (int effects : new int[]{0, 1, 2}) for (boolean glow : new boolean[]{false, true}) {
            var expected = new RecordingColorConsumer();
            var actual = new RecordingColorConsumer();
            StarOrreryAnchorEffects.render(poses(31), expected, 9, effects, Vec3.ZERO, 3.5F, glow);
            for (int from = 0; from < 9; from += 3)
                StarOrreryAnchorEffects.renderRange(poses(31), actual, from, from + 3,
                        effects, Vec3.ZERO, 3.5F, glow);
            assertEquals(expected.vertices(), actual.vertices());
        }
    }

    @Test void simultaneousRailPreparationHasIndependentTransformsAndCircleCaches() throws Exception {
        var expected = new ArrayList<List<RecordingColorConsumer.Vertex>>();
        for (int index = 0; index < 8; index++) expected.add(rails(index));
        try (var workers = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<Callable<List<RecordingColorConsumer.Vertex>>>();
            for (int index = 0; index < 8; index++) {
                int input = index;
                tasks.add(() -> rails(input));
            }
            var results = workers.invokeAll(tasks);
            for (int index = 0; index < results.size(); index++) assertEquals(expected.get(index), results.get(index).get());
        }
    }

    @Test void actualWorkerFramesMatchEverySynchronousPassWithAndWithoutCapturedAttributes() {
        assumeWorkersAvailable();
        var samples = List.of(
                new Sample(17, 6, 2, Vec3.ZERO, 3.5F, 1.25F, 0),
                new Sample(31, 7, 1, new Vec3(48, -12, 7), -4.25F, 8.5F, 64D * 64D),
                new Sample(83, 8, 2, new Vec3(112, 3, -15), 12.75F, 2.5F, 113D * 113D),
                new Sample(137, 9, 2, new Vec3(500, 500, 500), 120F, 37.25F, 0));
        try (var parallel = new StarOrreryParallelEffects()) {
            // Switch representations on the same reusable frame as shader state changes.
            for (var state : new SingularityVertexLayout.State[]{null, capturedState(), null}) {
                for (var sample : samples) {
                    var actual = prepare(parallel, sample, state);
                    assertFalse(actual.crystal().isEmpty());
                    assertFalse(actual.rain().isEmpty());
                    assertFalse(actual.glow().isEmpty());
                    assertFalse(actual.rainGlow().isEmpty());
                }
            }
        }
    }

    @Test void ordinaryFramesReuseOutputsWithoutRetainingEmptyGlowRainOrAnchorRanges() {
        assumeWorkersAvailable();
        try (var parallel = new StarOrreryParallelEffects()) {
            for (int effects : new int[]{2, 0, 1, 0, 2, 0, 2}) {
                int lit = effects == 0 ? 6 : effects == 1 ? 8 : 9;
                var sample = new Sample(effects * 37, lit, effects, Vec3.ZERO, effects + .5F, 7.25F, 0);
                var actual = prepare(parallel, sample, null);
                assertFalse(actual.crystal().isEmpty(), "Static anchors remain when effects are off");
                if (effects == 0) {
                    assertTrue(actual.rain().isEmpty());
                    assertTrue(actual.glow().isEmpty());
                    assertTrue(actual.rainGlow().isEmpty());
                }
            }
        }
    }

    @Test void gpuRainHandoffRetainsTheOtherPassesAndResumesCurrentCpuRainAfterSwitchingBack() {
        assumeWorkersAvailable();
        var sample = new Sample(31, 9, 2, Vec3.ZERO, 3.5F, 7.25F, 0);
        var expected = synchronous(poses(sample.angle()), sample);
        try (var parallel = new StarOrreryParallelEffects()) {
            prepare(parallel, sample, null);
            try (var frame = parallel.start(poses(sample.angle()), sample.lit(), sample.effects(), sample.camera(),
                    sample.time(), sample.flowTime(), sample.distance(), sample.detailed(), false)) {
                assertNotNull(frame);
                var crystal = new RenderThreadConsumer();
                var glow = new RenderThreadConsumer();
                var rain = new RenderThreadConsumer();
                frame.crystal(crystal);
                frame.glow(glow);
                assertIterableEquals(expected.crystal(), crystal.vertices());
                assertIterableEquals(expected.glow(), glow.vertices());
                assertThrows(IllegalStateException.class, () -> frame.rain(rain, false));
                assertThrows(IllegalStateException.class, () -> frame.rain(rain, true));
                assertTrue(rain.vertices().isEmpty());
            }
            prepare(parallel, new Sample(83, 9, 2, Vec3.ZERO, 4.5F, 8.25F, 0), null);
        }
    }

    @Test void actualWorkersUseTheCapturedPoseAfterTheInputStackChanges() throws Exception {
        assumeWorkersAvailable();
        var sample = new Sample(71, 9, 2, new Vec3(19, -7, 42), 3.5F, 6.75F, 0);
        for (var state : new SingularityVertexLayout.State[]{null, capturedState()}) {
            try (var workers = new ControlledWorkers(0); var parallel = new StarOrreryParallelEffects()) {
                installWorkers(parallel, workers);
                var poses = poses(sample.angle());
                var expected = synchronous(poses, sample);
                var frame = start(parallel, poses, sample, state);
                try {
                    assertNotNull(frame);
                    assertTrue(workers.started.await(5, TimeUnit.SECONDS));
                    assertEquals(4, workers.threads.size());
                    assertFalse(workers.threads.contains(Thread.currentThread()));
                    assertNull(start(parallel, poses, sample, state), "An active frame cannot be reused");
                    poses.last().pose().identity();
                    poses.translate(-500, 900, 1200);
                    poses.mulPose(Axis.XP.rotationDegrees(83));
                    poses.scale(-2, 3, 4);
                    workers.release.countDown();
                    assertPassesEqual(expected, submit(frame));
                } finally {
                    workers.release.countDown();
                    if (frame != null) frame.close();
                }
            }
        }
    }

    @Test void failedPreparationAppendsNothingAndReportsDistinctSubmitAndCloseFailures() {
        assumeWorkersAvailable();
        var sample = new Sample(17, 9, 2, null, 3.5F, 1.25F, 0);
        try (var parallel = new StarOrreryParallelEffects()) {
            var target = new RenderThreadConsumer();
            var failure = assertThrows(CompletionException.class, () -> {
                try (var frame = start(parallel, poses(sample.angle()), sample, null)) {
                    assertNotNull(frame);
                    // Traces can succeed while anchors fail on the deliberately absent camera.
                    frame.rain(target, false);
                }
            });
            assertInstanceOf(CompletionException.class, failure.getCause());
            assertInstanceOf(NullPointerException.class, failure.getCause().getCause());
            assertEquals(1, failure.getSuppressed().length);
            assertInstanceOf(CompletionException.class, failure.getSuppressed()[0]);
            assertNotSame(failure, failure.getSuppressed()[0]);
            assertTrue(target.vertices().isEmpty());
            prepare(parallel, new Sample(29, 6, 0, Vec3.ZERO, 0, 0, 0), null);
        }
    }

    @Test void closingAfterAFailedJoinWaitsForEveryWorkerAndResetsThePool() throws Exception {
        assumeWorkersAvailable();
        var sample = new Sample(17, 9, 2, null, 3.5F, 1.25F, 0);
        try (var workers = new ControlledWorkers(4); var parallel = new StarOrreryParallelEffects();
             var caller = Executors.newSingleThreadExecutor()) {
            installWorkers(parallel, workers);
            assertNotNull(start(parallel, poses(sample.angle()), sample, null));
            try {
                assertTrue(workers.started.await(5, TimeUnit.SECONDS));
                var closingStarted = new CountDownLatch(1);
                var closing = caller.submit(() -> {
                    closingStarted.countDown();
                    return assertThrows(CompletionException.class, parallel::close);
                });
                assertTrue(closingStarted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> closing.get(50, TimeUnit.MILLISECONDS));
                assertFalse(workers.isShutdown(), "Worker buffers must remain alive until preparation finishes");
                workers.release.countDown();
                closing.get(5, TimeUnit.SECONDS);
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
                assertEquals(Set.of(1, 2, 3, 4), workers.finished);
                // A fresh pool must be created: the previous executor is already shut down.
                prepare(parallel, new Sample(43, 7, 2, Vec3.ZERO, 6.25F, 2.5F, 0), null);
            } finally { workers.release.countDown(); }
        }
    }

    @Test void firstSubmitStillWaitsForEveryWriterAfterAnEarlierPreparationFailure() throws Exception {
        assumeWorkersAvailable();
        var sample = new Sample(17, 9, 2, null, 3.5F, 1.25F, 0);
        try (var workers = new ControlledWorkers(4); var parallel = new StarOrreryParallelEffects();
             var caller = Executors.newSingleThreadExecutor()) {
            installWorkers(parallel, workers);
            var frame = start(parallel, poses(sample.angle()), sample, null);
            assertNotNull(frame);
            try {
                assertTrue(workers.started.await(5, TimeUnit.SECONDS));
                var target = new RenderThreadConsumer();
                var submittingStarted = new CountDownLatch(1);
                var submitting = caller.submit(() -> {
                    submittingStarted.countDown();
                    return assertThrows(CompletionException.class, () -> frame.rain(target, false));
                });
                assertTrue(submittingStarted.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> submitting.get(50, TimeUnit.MILLISECONDS));
                assertTrue(target.vertices().isEmpty(), "Even a successful pass waits for all writers to succeed");
                workers.release.countDown();
                submitting.get(5, TimeUnit.SECONDS);
                assertTrue(target.vertices().isEmpty());
            } finally {
                workers.release.countDown();
                assertThrows(CompletionException.class, frame::close);
            }
            prepare(parallel, new Sample(29, 6, 0, Vec3.ZERO, 0, 0, 0), null);
        }
    }

    @Test void aPartialAppendCannotBeRetriedInAnyPassOrReplayedOnClose() {
        assumeWorkersAvailable();
        var sample = new Sample(17, 9, 2, Vec3.ZERO, 3.5F, 1.25F, 0);
        try (var parallel = new StarOrreryParallelEffects()) {
            for (var state : new SingularityVertexLayout.State[]{null, capturedState()}) {
                var failure = new IllegalStateException("Builder append failed");
                var target = new RenderThreadConsumer(failure);
                var frame = start(parallel, poses(sample.angle()), sample, state);
                assertNotNull(frame);
                try (frame) {
                    assertSame(failure, assertThrows(IllegalStateException.class, () -> frame.crystal(target)));
                    assertEquals(2, target.attempts);
                    assertThrows(IllegalStateException.class, () -> frame.crystal(target));
                    assertThrows(IllegalStateException.class, () -> frame.rain(target, false));
                    assertThrows(IllegalStateException.class, () -> frame.glow(target));
                    assertThrows(IllegalStateException.class, () -> frame.rain(target, true));
                }
                assertEquals(2, target.attempts);
                assertThrows(IllegalStateException.class, () -> frame.crystal(target));
                prepare(parallel, sample, state);
            }
        }
    }

    @Test void smallWorkStillReturnsNullBeforeCapturingOrScheduling() {
        try (var parallel = new StarOrreryParallelEffects()) {
            var poses = poses(0);
            assertNull(parallel.start(poses, 5, 2, Vec3.ZERO, 0, 0, 0, true));
            assertNull(parallel.startCaptured(poses, 5, 2, Vec3.ZERO, 0, 0, 0, true, null));
            assertNull(parallel.startCaptured(poses, 5, 2, Vec3.ZERO, 0, 0, 0, true, capturedState()));
            // Below the lit threshold the batch stays idle, so the same instance is reusable.
            assertNull(parallel.start(poses, 3, 2, Vec3.ZERO, 0, 0, 0, true));
        }
    }

    private static void assumeWorkersAvailable() {
        assumeTrue(Runtime.getRuntime().availableProcessors() >= 4);
    }

    private static SingularityVertexLayout.State capturedState() {
        return new SingularityVertexLayout.State(SingularityVertexLayout.of(DefaultVertexFormat.NEW_ENTITY), 0, false);
    }

    private record Sample(int angle, int lit, int effects, Vec3 camera, float time, float flowTime, double distance) {
        boolean detailed() { return StarOrreryEffects.isDetailed(effects, distance); }
    }

    private record Passes(List<RecordingColorConsumer.Vertex> crystal, List<RecordingColorConsumer.Vertex> rain,
            List<RecordingColorConsumer.Vertex> glow, List<RecordingColorConsumer.Vertex> rainGlow) {}

    private static StarOrreryParallelEffects.Frame start(StarOrreryParallelEffects parallel, PoseStack poses,
            Sample sample, SingularityVertexLayout.State state) {
        return parallel.startCaptured(poses, sample.lit(), sample.effects(), sample.camera(), sample.time(),
                sample.flowTime(), sample.distance(), sample.detailed(), state);
    }

    private static Passes prepare(StarOrreryParallelEffects parallel, Sample sample, SingularityVertexLayout.State state) {
        var expected = synchronous(poses(sample.angle()), sample);
        try (var frame = start(parallel, poses(sample.angle()), sample, state)) {
            assertNotNull(frame);
            var actual = submit(frame);
            assertPassesEqual(expected, actual);
            return actual;
        }
    }

    private static void assertPassesEqual(Passes expected, Passes actual) {
        assertIterableEquals(expected.crystal(), actual.crystal(), "Crystal vertex/range order");
        assertIterableEquals(expected.rain(), actual.rain(), "Rain vertex order");
        assertIterableEquals(expected.glow(), actual.glow(), "Glow vertex/range order");
        assertIterableEquals(expected.rainGlow(), actual.rainGlow(), "Rain glow vertex order");
    }

    private static Passes synchronous(PoseStack poses, Sample sample) {
        var crystal = new RecordingColorConsumer();
        var rain = new RecordingColorConsumer();
        var glow = new RecordingColorConsumer();
        var rainGlow = new RecordingColorConsumer();
        StarOrreryAnchorEffects.render(poses, crystal, sample.lit(), sample.effects(), sample.camera(), sample.time(), false);
        StarOrreryEffects.render(poses, crystal, sample.lit(), sample.effects(), sample.distance(), sample.time(), sample.detailed(), false);
        StarOrreryCoreEffects.renderRain(poses, rain, sample.lit(), sample.effects(), sample.distance(), sample.flowTime(), false);
        StarOrreryCoreEffects.renderBuildingRain(poses, rain, sample.lit(), sample.effects(), sample.distance(), sample.flowTime(), sample.time(), false);
        StarOrreryEffects.render(poses, glow, sample.lit(), sample.effects(), sample.distance(), sample.time(), sample.detailed(), true);
        StarOrreryAnchorEffects.render(poses, glow, sample.lit(), sample.effects(), sample.camera(), sample.time(), true);
        StarOrreryCoreEffects.renderRain(poses, rainGlow, sample.lit(), sample.effects(), sample.distance(), sample.flowTime(), true);
        StarOrreryCoreEffects.renderBuildingRain(poses, rainGlow, sample.lit(), sample.effects(), sample.distance(), sample.flowTime(), sample.time(), true);
        return new Passes(crystal.vertices(), rain.vertices(), glow.vertices(), rainGlow.vertices());
    }

    private static Passes submit(StarOrreryParallelEffects.Frame frame) {
        var crystal = new RenderThreadConsumer();
        var rain = new RenderThreadConsumer();
        var glow = new RenderThreadConsumer();
        var rainGlow = new RenderThreadConsumer();
        frame.crystal(crystal);
        frame.rain(rain, false);
        frame.glow(glow);
        frame.rain(rainGlow, true);
        return new Passes(crystal.vertices(), rain.vertices(), glow.vertices(), rainGlow.vertices());
    }

    /** Test-only executor control leaves production inputs and task bodies unchanged. */
    private static void installWorkers(StarOrreryParallelEffects parallel, ExecutorService workers) throws Exception {
        var field = StarOrreryParallelEffects.class.getDeclaredField("workers");
        field.setAccessible(true);
        field.set(parallel, workers);
    }

    private static final class ControlledWorkers extends ThreadPoolExecutor {
        final CountDownLatch started, release = new CountDownLatch(1);
        final Set<Thread> threads = ConcurrentHashMap.newKeySet();
        final Set<Integer> finished = ConcurrentHashMap.newKeySet();
        private final int gatedTask;
        private int submitted;

        ControlledWorkers(int gatedTask) {
            super(4, 4, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
            this.gatedTask = gatedTask;
            started = new CountDownLatch(gatedTask == 0 ? 4 : 1);
        }

        @Override public void execute(Runnable task) {
            int index = ++submitted;
            super.execute(() -> {
                threads.add(Thread.currentThread());
                if (gatedTask == 0 || index == gatedTask) {
                    started.countDown();
                    // Always run the command so a failed test cannot strand its future.
                    try { release.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
                try { task.run(); }
                finally { finished.add(index); }
            });
        }
    }

    private static final class RenderThreadConsumer implements VertexConsumer {
        private final Thread renderThread = Thread.currentThread();
        private final RecordingColorConsumer colors = new RecordingColorConsumer();
        private final RuntimeException appendFailure;
        int attempts;

        RenderThreadConsumer() { this(null); }
        RenderThreadConsumer(RuntimeException appendFailure) { this.appendFailure = appendFailure; }
        List<RecordingColorConsumer.Vertex> vertices() { return colors.vertices(); }
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            assertSame(renderThread, Thread.currentThread(), "Workers cannot touch a live submission target");
            if (++attempts == 2 && appendFailure != null) throw appendFailure;
            colors.addVertex(x, y, z);
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) {
            assertSame(renderThread, Thread.currentThread());
            colors.setColor(r, g, b, a);
            return this;
        }
        @Override public VertexConsumer setUv(float u, float v) { throw new AssertionError("Unexpected UV"); }
        @Override public VertexConsumer setUv1(int u, int v) { throw new AssertionError("Unexpected overlay"); }
        @Override public VertexConsumer setUv2(int u, int v) { throw new AssertionError("Unexpected light"); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { throw new AssertionError("Unexpected normal"); }
    }

    private static List<RecordingColorConsumer.Vertex> rails(int input) {
        var out = new RecordingColorConsumer();
        StarOrreryEffects.render(poses(input * 17), out, 6 + input % 4, input + .25F,
                input % 2 == 0, input % 3 == 0);
        return out.vertices();
    }

    private static PoseStack poses(int angle) {
        var poses = new PoseStack();
        poses.translate(17.5, -23.25, 51.75);
        poses.mulPose(Axis.YP.rotationDegrees(angle));
        poses.scale(.28F, .40F, -.18F);
        return poses;
    }
}
