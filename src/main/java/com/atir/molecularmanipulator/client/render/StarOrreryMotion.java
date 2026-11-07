package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import org.joml.Quaternionf;

/**
 * Shared rigid ring-plane motion around the controller centre. The belt mesh,
 * inset ornaments and both rail passes use this exact orientation. Functional
 * anchor gems stay at the real, static block positions outside this transform.
 */
public final class StarOrreryMotion {
    // Every period divides the renderer's 12,000-second clock. Wrap the phases
    // before converting to floats so reloads and the clock seam remain stable.
    private static final int[] TUMBLE_SECONDS = {40, 48, 60, 75, 80, 96, 100, 120, 150};
    private static final int[] PRECESSION_SECONDS = {100, 120, 150, 160, 200, 240, 250, 300, 375};
    private static final double TAU = Math.PI * 2D;
    private static final long CLOCK_TICKS = 240000L;

    private StarOrreryMotion() {}

    /** Visible model motion stays continuous even when distant light effects are culled. */
    public static float timeSeconds(long gameTime, float partialTick, int effectLevel) {
        return effectLevel > 0 ? (float) ((gameTime % CLOCK_TICKS + partialTick) / 20D) : 0F;
    }

    /** Original layout pose, also used by fixed anchors and belt-side data rain. */
    public static Quaternionf restRotation(Quaternionf target, double yaw, double pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return target.rotationTo(0, 1, 0,
                (float) (Math.cos(p) * Math.cos(y)), (float) Math.sin(p), (float) (Math.cos(p) * Math.sin(y)));
    }

    /**
     * Entire active ring tumbles about a diameter through the core while that
     * axis slowly precesses. Both rotations are about the origin: no orbital
     * translation, radius change or motion of the core/pedestal is introduced.
     * In-plane belt self-spin is applied separately by the physical mesh pass.
     * Callers pass time zero when the user disables animation; distance-based
     * light-effect LOD must not reset the pose of a still-visible physical ring.
     */
    public static Quaternionf planeRotation(Quaternionf target, int ring, int litRings, float time) {
        restRotation(target, StarOrreryLayout.yaw(ring), StarOrreryLayout.pitch(ring));
        if (ring >= Math.clamp(litRings, 0, StarOrreryLayout.RINGS)) return target;
        double direction = ring % 2 == 0 ? 1D : -1D;
        float precession = phase(time, PRECESSION_SECONDS[ring], -direction);
        float tumble = phase(time, TUMBLE_SECONDS[ring], direction);
        return target.rotateY(precession).rotateX(tumble);
    }

    private static float phase(float time, int period, double direction) {
        return (float) ((time % period) * (TAU / period) * direction);
    }
}
