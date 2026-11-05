package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("ParallelEffectGeometry")
class ParallelEffectGeometryTest {
    @AfterEach void clear() { ParallelEffectGeometry.clear(); }

    @Test void tasksOverlapAndReplayOnlyInCallerOrder() throws Exception {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        Set<Thread> threads = ConcurrentHashMap.newKeySet();
        try (var geometry = new ParallelEffectGeometry()) {
            var frame = geometry.prepare(out -> {
                threads.add(Thread.currentThread()); started.countDown(); await(release);
                quad(out, 1, 0x124578AB);
            }, out -> {
                threads.add(Thread.currentThread()); started.countDown(); await(release);
                quad(out, 2, 0x98CDEF32);
            });
            assertNotNull(frame);
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertEquals(2, threads.size());
                assertFalse(threads.contains(Thread.currentThread()));
                assertNull(geometry.prepare(out -> fail("Busy helper ran a task"), out -> fail()));
                release.countDown();
                var expected = new RecordingColorConsumer();
                quad(expected, 2, 0x98CDEF32); quad(expected, 1, 0x124578AB);
                var actual = new RecordingColorConsumer();
                frame.submit(1, actual); frame.submit(0, actual);
                assertEquals(expected.vertices(), actual.vertices());
                assertThrows(IllegalStateException.class, () -> frame.submit(0, actual));
            } finally {
                release.countDown(); frame.close();
            }
            assertThrows(IllegalStateException.class, () -> frame.submit(0, new RecordingColorConsumer()));
        }
    }

    @Test void buffersAreReusedAndAnEmptyTaskCannotRetainThePreviousFrame() {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        var firstOutput = new AtomicReference<VertexConsumer>();
        try (var geometry = new ParallelEffectGeometry()) {
            try (var frame = geometry.prepare(out -> {
                firstOutput.set(out); quad(out, 1, 0x12345678);
            }, out -> quad(out, 2, 0x87654321))) {
                assertNotNull(frame);
                frame.submit(0, new RecordingColorConsumer());
            }
            try (var frame = geometry.prepare(out -> assertSame(firstOutput.get(), out), out -> {})) {
                assertNotNull(frame);
                var actual = new RecordingColorConsumer();
                frame.submit(0, actual); frame.submit(1, actual);
                assertTrue(actual.vertices().isEmpty());
            }
        }
    }

    @Test void preparationFailureJoinsEveryTaskBeforeThrowingAndAppendsNothing() throws Exception {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new AtomicBoolean();
        try (var geometry = new ParallelEffectGeometry(); var caller = Executors.newSingleThreadExecutor()) {
            var frame = geometry.prepare(out -> {
                quad(out, 0, 0x12345678);
                throw new IllegalStateException("Geometry failed");
            }, out -> {
                started.countDown(); await(release); quad(out, 1, 0x87654321); finished.set(true);
            });
            assertNotNull(frame);
            var target = new RecordingColorConsumer();
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                var attempt = caller.submit(() -> assertThrows(CompletionException.class, () -> frame.submit(0, target)));
                assertThrows(TimeoutException.class, () -> attempt.get(50, TimeUnit.MILLISECONDS));
                release.countDown();
                attempt.get(5, TimeUnit.SECONDS);
                assertTrue(finished.get());
                assertTrue(target.vertices().isEmpty());
            } finally {
                release.countDown();
                assertThrows(CompletionException.class, frame::close);
            }
            try (var next = geometry.prepare(out -> {}, out -> {})) {
                assertNotNull(next);
                next.submit(0, target);
                assertTrue(target.vertices().isEmpty());
            }
        }
    }

    @Test void tryWithResourcesCanReportPreparationAndCloseFailuresWithoutSelfSuppression() {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        try (var geometry = new ParallelEffectGeometry()) {
            var failure = assertThrows(CompletionException.class, () -> {
                try (var frame = geometry.prepare(out -> { throw new IllegalStateException("Geometry failed"); }, out -> {})) {
                    assertNotNull(frame);
                    frame.submit(0, new RecordingColorConsumer());
                }
            });
            assertInstanceOf(CompletionException.class, failure.getCause());
            assertInstanceOf(IllegalStateException.class, failure.getCause().getCause());
            assertEquals(1, failure.getSuppressed().length);
            assertInstanceOf(CompletionException.class, failure.getSuppressed()[0]);
        }
    }

    @Test void clearReleasesEveryHelperAndResetsThePoolEvenWhenAWorkerFails() {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        var finished = new AtomicBoolean();
        try (var first = new ParallelEffectGeometry(); var second = new ParallelEffectGeometry()) {
            var failed = first.prepare(out -> { throw new IllegalStateException("Geometry failed"); }, out -> {});
            var other = second.prepare(out -> quad(out, 1, 0x12345678), out -> finished.set(true));
            assertNotNull(failed); assertNotNull(other);
            assertThrows(CompletionException.class, ParallelEffectGeometry::clear);
            assertTrue(finished.get());
            assertThrows(IllegalStateException.class, () -> other.submit(0, new RecordingColorConsumer()));
            assertThrows(IllegalStateException.class, () -> failed.submit(0, new RecordingColorConsumer()));
            try (var next = first.prepare(out -> {}, out -> {})) {
                assertNotNull(next);
                var actual = new RecordingColorConsumer(); next.submit(0, actual);
                assertTrue(actual.vertices().isEmpty());
            }
        }
    }

    @Test void incompleteLaterTaskIsRejectedBeforeAnyEarlierTaskIsSubmitted() {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        try (var geometry = new ParallelEffectGeometry();
             var frame = geometry.prepare(out -> quad(out, 0, 0x12345678),
                     out -> out.addVertex(1, 2, 3).setColor(0x87654321))) {
            assertNotNull(frame);
            var target = new RecordingColorConsumer();
            assertThrows(IllegalStateException.class, () -> frame.submit(0, target));
            assertTrue(target.vertices().isEmpty());
        }
    }

    @Test void anAppendFailureIsNeverRetriedOrReplayedOnClose() {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        var failure = new IllegalStateException("Builder append failed");
        var target = new FailingConsumer(failure);
        try (var geometry = new ParallelEffectGeometry()) {
            try (var frame = geometry.prepare(out -> quad(out, 0, 0x12345678),
                    out -> quad(out, 1, 0x87654321))) {
                assertNotNull(frame);
                assertSame(failure, assertThrows(IllegalStateException.class, () -> frame.submit(0, target)));
                assertEquals(2, target.attempts);
                assertThrows(IllegalStateException.class, () -> frame.submit(0, target));
                assertThrows(IllegalStateException.class, () -> frame.submit(1, target));
            }
            assertEquals(2, target.attempts);
        }
    }

    @Test void clearWaitsForAllHelpersClosesTheirFramesAndAllowsRecreation() throws Exception {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        var started = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var finished = ConcurrentHashMap.<Integer>newKeySet();
        try (var first = new ParallelEffectGeometry(); var second = new ParallelEffectGeometry();
             var caller = Executors.newSingleThreadExecutor()) {
            var a = first.prepare(out -> {
                started.countDown(); await(release); quad(out, 0, 0x12345678); finished.add(0);
            }, out -> {
                started.countDown(); await(release); quad(out, 1, 0x87654321); finished.add(1);
            });
            var b = second.prepare(out -> {
                quad(out, 2, 0x12345678); finished.add(2);
            }, out -> { quad(out, 3, 0x87654321); finished.add(3); });
            assertNotNull(a); assertNotNull(b);
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                var clearing = caller.submit(ParallelEffectGeometry::clear);
                assertThrows(TimeoutException.class, () -> clearing.get(50, TimeUnit.MILLISECONDS));
                release.countDown();
                clearing.get(5, TimeUnit.SECONDS);
                assertEquals(Set.of(0, 1, 2, 3), finished);
                assertThrows(IllegalStateException.class, () -> a.submit(0, new RecordingColorConsumer()));
                assertThrows(IllegalStateException.class, () -> b.submit(0, new RecordingColorConsumer()));
                try (var next = first.prepare(out -> quad(out, 4, 0x12345678), out -> {})) {
                    assertNotNull(next);
                    var actual = new RecordingColorConsumer(); next.submit(0, actual);
                    assertEquals(4, actual.vertices().size());
                    assertEquals(4, actual.vertices().getFirst().x());
                }
            } finally {
                release.countDown(); a.close(); b.close();
            }
        }
    }

    @Test void smallWorkReturnsNullWithoutCallingTasks() {
        try (var geometry = new ParallelEffectGeometry()) {
            assertNull(geometry.prepare());
            assertNull(geometry.prepare(out -> fail("Small task was scheduled")));
        }
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS), "Worker was never released"); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new AssertionError(interrupted);
        }
    }

    private static void quad(VertexConsumer out, float x, int color) {
        out.addVertex(x, 0, 0).setColor(color); out.addVertex(x + 1, 0, 0).setColor(color);
        out.addVertex(x + 1, 1, 0).setColor(color); out.addVertex(x, 1, 0).setColor(color);
    }

    private static final class FailingConsumer implements VertexConsumer {
        private final RuntimeException failure;
        private int attempts;
        private FailingConsumer(RuntimeException failure) { this.failure = failure; }
        @Override public VertexConsumer addVertex(float x, float y, float z) {
            if (++attempts == 2) throw failure;
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { throw new AssertionError(); }
        @Override public VertexConsumer setUv1(int u, int v) { throw new AssertionError(); }
        @Override public VertexConsumer setUv2(int u, int v) { throw new AssertionError(); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { throw new AssertionError(); }
    }
}
