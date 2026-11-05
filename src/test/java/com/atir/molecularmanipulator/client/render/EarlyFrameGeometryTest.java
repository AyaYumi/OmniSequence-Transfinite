package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.junit.jupiter.api.Test;

class EarlyFrameGeometryTest {
    private static final class Snapshot implements EarlyFrameGeometry.Snapshot {
        int closes;
        @Override public void close() { closes++; }
    }

    @Test void claimsOnlyOnceAndHandsLifetimeToTheConsumer() {
        var pending = new EarlyFrameGeometry.Pending<Snapshot>();
        var snapshot = new Snapshot();
        var base = poses();
        pending.set(snapshot, .375F, base);
        assertSame(snapshot, pending.take(true, .375F, base));
        assertNull(pending.take(true, .375F, base));
        pending.close();
        assertEquals(0, snapshot.closes);
        snapshot.close();
        assertEquals(1, snapshot.closes);
    }

    @Test void anotherViewOrPartialTickDiscardsAndClosesPendingOutput() {
        for (int variant = 0; variant < 5; variant++) {
            var pending = new EarlyFrameGeometry.Pending<Snapshot>();
            var snapshot = new Snapshot();
            pending.set(snapshot, .375F, poses());
            var current = poses();
            if (variant == 0) current.translate(.00001, 0, 0);
            if (variant == 1) current.last().normal().rotateX(.001F);
            if (variant == 2) current.mulPose(Axis.YP.rotationDegrees(1));
            assertNull(pending.take(variant != 3, variant == 4 ? .5F : .375F, current));
            pending.close();
            assertEquals(1, snapshot.closes);
        }
    }

    @Test void capturesPoseValuesAndDrainsUnconsumedFrames() {
        var pending = new EarlyFrameGeometry.Pending<Snapshot>();
        var first = new Snapshot();
        var input = poses();
        pending.set(first, .375F, input);
        input.translate(100, 0, 0);
        assertSame(first, pending.take(true, .375F, poses()));
        first.close();
        var second = new Snapshot();
        var third = new Snapshot();
        pending.set(second, .5F, poses());
        pending.set(third, .75F, poses());
        assertEquals(1, second.closes);
        pending.close();
        pending.close();
        assertEquals(1, third.closes);
    }

    @Test void separateMachinesHaveIndependentClaimsAndCleanup() {
        var a = new EarlyFrameGeometry.Pending<Snapshot>();
        var b = new EarlyFrameGeometry.Pending<Snapshot>();
        var first = new Snapshot();
        var second = new Snapshot();
        a.set(first, .25F, poses());
        b.set(second, .25F, poses());
        assertSame(second, b.take(true, .25F, poses()));
        a.close();
        assertEquals(1, first.closes);
        assertEquals(0, second.closes);
        second.close();
    }

    private static PoseStack poses() {
        var stack = new PoseStack();
        stack.translate(17.25, -23.5, 51.75);
        return stack;
    }
}
