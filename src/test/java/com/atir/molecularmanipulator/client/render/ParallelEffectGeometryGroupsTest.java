package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("ParallelEffectGeometry")
class ParallelEffectGeometryGroupsTest {
    @AfterEach void clear() { ParallelEffectGeometry.clear(); }

    @Test void launchOrderAndCallerGroupOrderNeverChangeVertexOrderWithinAPass() {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        var executions = new AtomicIntegerArray(4);
        int[] ends = {2, 4};
        int[] launch = {3, 1, 2, 0};
        try (var geometry = new ParallelEffectGeometry();
             var frame = geometry.prepareGroups(ends, launch,
                     out -> { executions.incrementAndGet(0); quad(out, 0); },
                     out -> { executions.incrementAndGet(1); quad(out, 1); },
                     out -> { executions.incrementAndGet(2); quad(out, 2); },
                     out -> { executions.incrementAndGet(3); quad(out, 3); })) {
            assertNotNull(frame);
            ends[0] = 1; ends[1] = 1;
            launch[0] = 0;
            var expected = new RecordingColorConsumer();
            quad(expected, 2); quad(expected, 3); quad(expected, 0); quad(expected, 1);
            var actual = new RecordingColorConsumer();
            frame.submit(1, actual); frame.submit(0, actual);
            assertEquals(expected.vertices(), actual.vertices());
            for (int index = 0; index < 4; index++) assertEquals(1, executions.get(index));
            assertThrows(IllegalStateException.class, () -> frame.submit(0, actual));
            assertThrows(IllegalStateException.class, () -> frame.submitRange(2, 3, actual));
            assertThrows(IndexOutOfBoundsException.class, () -> frame.submit(2, actual));
        }
    }

    @Test void anOverlappingRangeIsRejectedBeforeAnyFreshOutputIsAppended() {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        try (var geometry = new ParallelEffectGeometry();
             var frame = geometry.prepare(out -> quad(out, 0), out -> quad(out, 1), out -> quad(out, 2))) {
            assertNotNull(frame);
            var actual = new RecordingColorConsumer();
            frame.submit(1, actual);
            assertThrows(IllegalStateException.class, () -> frame.submitRange(0, 3, actual));
            assertEquals(4, actual.vertices().size());
            frame.submitRange(0, 1, actual); frame.submitRange(2, 3, actual);
            var expected = new RecordingColorConsumer();
            quad(expected, 1); quad(expected, 0); quad(expected, 2);
            assertEquals(expected.vertices(), actual.vertices());
        }
    }

    @Test void anInvalidQuadOutsideTheRequestedGroupPreventsEveryBuilderWrite() {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        try (var geometry = new ParallelEffectGeometry();
             var frame = geometry.prepareGroups(new int[]{2, 3},
                     out -> quad(out, 0), out -> quad(out, 1), out -> out.addVertex(0, 0, 0).setColor(0xFFFFFFFF))) {
            assertNotNull(frame);
            var actual = new RecordingColorConsumer();
            assertThrows(IllegalStateException.class, () -> frame.submit(0, actual));
            assertTrue(actual.vertices().isEmpty());
        }
    }

    @Test void failureInALaterChunkPoisonsTheEntireFrameAndCloseNeverReplays() {
        assumeTrue(ParallelEffectGeometry.isAvailable());
        var target = new FailingConsumer();
        try (var geometry = new ParallelEffectGeometry()) {
            try (var frame = geometry.prepareGroups(new int[]{2, 3},
                    out -> quad(out, 0), out -> quad(out, 1), out -> quad(out, 2))) {
                assertNotNull(frame);
                assertSame(target.failure, assertThrows(IllegalStateException.class, () -> frame.submit(0, target)));
                assertEquals(5, target.attempts);
                assertThrows(IllegalStateException.class, () -> frame.submit(0, target));
                assertThrows(IllegalStateException.class, () -> frame.submit(1, target));
                assertThrows(IllegalStateException.class, () -> frame.submitRange(1, 2, target));
            }
            assertEquals(5, target.attempts);
        }
    }

    @Test void malformedGroupsOrLaunchOrdersCannotScheduleAnyTask() {
        try (var geometry = new ParallelEffectGeometry()) {
            java.util.function.Consumer<VertexConsumer> never = out -> fail("Invalid work was scheduled");
            assertThrows(IllegalArgumentException.class, () -> geometry.prepareGroups(new int[]{1}, never, never));
            assertThrows(IllegalArgumentException.class, () -> geometry.prepareGroups(new int[]{2, 2}, never, never));
            assertThrows(IllegalArgumentException.class, () -> geometry.prepareGroups(new int[]{3}, never, never));
            assertThrows(IllegalArgumentException.class, () -> geometry.prepareGroups(new int[]{2}, new int[]{0, 0}, never, never));
            assertThrows(IllegalArgumentException.class, () -> geometry.prepareGroups(new int[]{2}, new int[]{0}, never, never));
            assertThrows(IndexOutOfBoundsException.class, () -> geometry.prepareGroups(new int[]{2}, new int[]{0, 2}, never, never));
            assertThrows(NullPointerException.class, () -> geometry.prepareGroups((int[]) null, never, never));
            assertThrows(NullPointerException.class, () -> geometry.prepare(never, null));
        }
    }

    private static void quad(VertexConsumer out, float x) {
        out.addVertex(x, 0, 0).setColor(0x12345678); out.addVertex(x + 1, 0, 0).setColor(0x23456789);
        out.addVertex(x + 1, 1, 0).setColor(0x3456789A); out.addVertex(x, 1, 0).setColor(0x456789AB);
    }

    private static final class FailingConsumer implements VertexConsumer {
        final IllegalStateException failure = new IllegalStateException("Append failed in second chunk");
        int attempts;
        public VertexConsumer addVertex(float x, float y, float z) {
            if (++attempts == 5) throw failure;
            return this;
        }
        public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        public VertexConsumer setUv(float u, float v) { throw new AssertionError(); }
        public VertexConsumer setUv1(int u, int v) { throw new AssertionError(); }
        public VertexConsumer setUv2(int u, int v) { throw new AssertionError(); }
        public VertexConsumer setNormal(float x, float y, float z) { throw new AssertionError(); }
    }
}
