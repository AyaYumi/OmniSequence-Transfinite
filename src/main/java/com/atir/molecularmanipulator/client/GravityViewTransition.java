package com.atir.molecularmanipulator.client;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** A render-only correction; collision, movement and the server always use the authoritative gravity frame. */
public final class GravityViewTransition {
    public static final double DURATION_TICKS = 10;
    private final Quaternionf displayed = new Quaternionf();
    private final Quaternionf correction = new Quaternionf();
    private Direction direction;
    private double started;
    private Vec3 eye, eyeOffset = Vec3.ZERO;
    private boolean changed;

    public void reset() {
        direction = null;
        eye = null;
        eyeOffset = Vec3.ZERO;
        correction.identity();
        changed = false;
    }

    public Quaternionf beginFrame(Direction down, Quaternionf target, double time) {
        changed = direction != null && direction != down;
        if (direction == null) {
            correction.identity();
            started = time - DURATION_TICKS;
        } else if (changed) {
            // Capture the last displayed world orientation, including a still unfinished turn and local yaw changes.
            correction.set(displayed).mul(new Quaternionf(target).conjugate()).normalize();
            started = time;
        }
        direction = down;
        displayed.set(apply(target, time));
        return new Quaternionf(displayed);
    }

    public Quaternionf apply(Quaternionf target, double time) {
        return new Quaternionf(correction).slerp(new Quaternionf(), (float) progress(time)).mul(target).normalize();
    }

    public Vec3 eye(Vec3 target, double time) {
        if (changed && eye != null) eyeOffset = eye.subtract(target);
        changed = false;
        eye = target.add(eyeOffset.scale(1 - progress(time)));
        return eye;
    }

    public void recordEye(Vec3 rendered) { eye = rendered; }

    private double progress(double time) {
        double t = Math.clamp((time - started) / DURATION_TICKS, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
