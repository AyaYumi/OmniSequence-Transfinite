package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.client.render.NexusFormedLayout.Box;
import java.util.EnumSet;
import java.util.HashSet;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class NexusFormedLayoutTest {
    @Test
    void isolatedCubeKeepsAFullRingAndInsetPanelOnEveryFace() {
        for (var side : Direction.values()) {
            var corners = NexusFormedLayout.corners(side);
            var stripes = NexusFormedLayout.stripes(side);
            assertEquals(4, corners.size(), side.name());
            assertEquals(4, stripes.size(), side.name());
            assertTrue(corners.stream().allMatch(NexusFormedLayoutTest::isRingCap));
            assertTrue(stripes.stream().allMatch(stripe -> isRingStripe(stripe.box())));
            Box inner = NexusFormedLayout.inner(side);
            assertEquals(side.getAxis() == Direction.Axis.X ? 0 : NexusFormedLayout.INNER_MIN, inner.x1());
            assertEquals(side.getAxis() == Direction.Axis.X ? 16 : NexusFormedLayout.INNER_MAX, inner.x2());
            assertEquals(side.getAxis() == Direction.Axis.Y ? 0 : NexusFormedLayout.INNER_MIN, inner.y1());
            assertEquals(side.getAxis() == Direction.Axis.Y ? 16 : NexusFormedLayout.INNER_MAX, inner.y2());
            assertEquals(side.getAxis() == Direction.Axis.Z ? 0 : NexusFormedLayout.INNER_MIN, inner.z1());
            assertEquals(side.getAxis() == Direction.Axis.Z ? 16 : NexusFormedLayout.INNER_MAX, inner.z2());
        }
    }

    @Test
    void stripeTexturesFollowTheAe2RingAxes() {
        assertTrue(NexusFormedLayout.verticalRing(Direction.NORTH, Direction.WEST));
        assertFalse(NexusFormedLayout.verticalRing(Direction.NORTH, Direction.UP));
        assertTrue(NexusFormedLayout.verticalRing(Direction.UP, Direction.EAST));
        assertFalse(NexusFormedLayout.verticalRing(Direction.UP, Direction.NORTH));
        int stripe = 0;
        for (var edge : Direction.values()) {
            if (edge == Direction.NORTH || edge == Direction.SOUTH) continue;
            var laid = NexusFormedLayout.stripes(Direction.NORTH).get(stripe);
            var box = laid.box();
            assertEquals(NexusFormedLayout.verticalRing(Direction.NORTH, edge), laid.vertical());
            stripe++;
            if (edge.getAxis() == Direction.Axis.Y) {
                assertEquals(edge == Direction.DOWN ? 0 : 13, box.y1());
                assertEquals(edge == Direction.DOWN ? 3 : 16, box.y2());
                assertEquals(3, box.x1());
                assertEquals(13, box.x2());
            } else {
                assertEquals(edge == Direction.WEST ? 0 : 13, box.x1());
                assertEquals(edge == Direction.WEST ? 3 : 16, box.x2());
                assertEquals(3, box.y1());
                assertEquals(13, box.y2());
            }
        }
    }

    @Test
    void adjacentPoweredFacesHideTheSharedRingAndExtendTheInnerPanel() {
        var east = EnumSet.of(Direction.EAST);
        assertTrue(NexusFormedLayout.corners(Direction.NORTH, east).stream().noneMatch(
                box -> box.x1() == 13 && box.x2() == 16));
        assertTrue(NexusFormedLayout.stripes(Direction.NORTH, east).stream().noneMatch(
                stripe -> stripe.box().x1() == 13 && stripe.box().x2() == 16));
        assertEquals(16, NexusFormedLayout.inner(Direction.NORTH, east).x2());
        assertEquals(0, NexusFormedLayout.corners(Direction.EAST, east).size());
        assertEquals(4, NexusFormedLayout.stripes(Direction.EAST, east).size());
        assertEquals(0, NexusFormedLayout.inner(Direction.EAST, east).x1());
        assertEquals(16, NexusFormedLayout.inner(Direction.EAST, east).x2());
    }

    @Test
    void eachVisibleFaceGetsFourCapsOfTheEightPhysicalCorners() {
        var unique = new HashSet<Box>();
        int total = 0;
        for (var side : Direction.values()) {
            var corners = NexusFormedLayout.corners(side);
            unique.addAll(corners);
            total += corners.size();
        }
        assertEquals(24, total);
        assertEquals(8, unique.size());
    }

    private static boolean isRingCap(Box box) {
        return thickness(box.x1(), box.x2()) == NexusFormedLayout.RING
                && thickness(box.y1(), box.y2()) == NexusFormedLayout.RING
                && thickness(box.z1(), box.z2()) == NexusFormedLayout.RING;
    }

    private static boolean isRingStripe(Box box) {
        int thin = 0;
        if (thickness(box.x1(), box.x2()) == NexusFormedLayout.RING) thin++;
        if (thickness(box.y1(), box.y2()) == NexusFormedLayout.RING) thin++;
        if (thickness(box.z1(), box.z2()) == NexusFormedLayout.RING) thin++;
        return thin == 1;
    }

    private static float thickness(float a, float b) {
        return b - a;
    }
}
