package com.atir.molecularmanipulator.client;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GravityViewTransitionTest {
    @Test void entryAndExitHaveContinuousEndpointsAndARealHalfwayTurn() {
        var view = new GravityViewTransition();
        var flat = new Quaternionf();
        var wall = new Quaternionf().rotationZ((float) (-Math.PI / 2));
        assertRotation(flat, view.beginFrame(Direction.DOWN, flat, 100));
        assertRotation(flat, view.beginFrame(Direction.WEST, wall, 101));
        var halfway = view.beginFrame(Direction.WEST, wall, 106);
        var up = new Vector3f(0, 1, 0).rotate(halfway);
        assertEquals(Math.sqrt(.5), up.x, 1E-5);
        assertEquals(Math.sqrt(.5), up.y, 1E-5);
        assertRotation(wall, view.beginFrame(Direction.WEST, wall, 111));
        assertRotation(wall, view.beginFrame(Direction.DOWN, flat, 112));
        assertRotation(flat, view.beginFrame(Direction.DOWN, flat, 122));
    }

    @Test void interruptedTurnsAndMouseInputRemainContinuous() {
        var view = new GravityViewTransition();
        var flat = new Quaternionf();
        var wall = new Quaternionf().rotationZ((float) (-Math.PI / 2));
        var ceiling = new Quaternionf().rotationX((float) Math.PI);
        view.beginFrame(Direction.DOWN, flat, 0);
        view.beginFrame(Direction.WEST, wall, 1);
        var previous = view.beginFrame(Direction.WEST, wall, 5);
        assertRotation(previous, view.beginFrame(Direction.UP, ceiling, 5));
        var input = new Quaternionf().rotationY(.2F);
        assertRotation(new Quaternionf(previous).mul(input), view.beginFrame(Direction.UP,
                new Quaternionf(ceiling).mul(input), 5));
        assertRotation(new Quaternionf(ceiling).mul(input), view.beginFrame(Direction.UP,
                new Quaternionf(ceiling).mul(input), 15));
        view.reset();
        assertRotation(ceiling, view.beginFrame(Direction.UP, ceiling, 0));
    }

    @Test void eyesInterpolateWithoutDelayingOrdinaryWalkingOrPerspectiveReversal() {
        var view = new GravityViewTransition();
        var flat = new Quaternionf();
        var wall = new Quaternionf().rotationZ((float) (-Math.PI / 2));
        var oldEye = new Vec3(0, 1.6, 0);
        var newEye = new Vec3(1.6, 0, 0);
        view.beginFrame(Direction.DOWN, flat, 10);
        assertEquals(oldEye, view.eye(oldEye, 10));
        view.beginFrame(Direction.WEST, wall, 11);
        assertEquals(oldEye, view.eye(newEye, 11));
        var base = view.beginFrame(Direction.WEST, wall, 16);
        assertEquals(new Vec3(.8, .8, 0), view.eye(newEye, 16));
        var reverse = new Quaternionf(wall).rotateY((float) Math.PI);
        assertRotation(new Quaternionf(base).rotateY((float) Math.PI), view.apply(reverse, 16));
        view.beginFrame(Direction.WEST, wall, 21);
        assertEquals(newEye, view.eye(newEye, 21));
        view.beginFrame(Direction.WEST, wall, 22);
        assertEquals(newEye.add(0, 0, 1), view.eye(newEye.add(0, 0, 1), 22));
    }

    private static void assertRotation(Quaternionf expected, Quaternionf actual) {
        assertEquals(1, Math.abs(expected.dot(actual)), 1E-5);
    }
}
