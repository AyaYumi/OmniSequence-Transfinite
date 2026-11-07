package com.atir.molecularmanipulator.client.render;

/** Local motion of the floating pedestal dial; the other parts and real blocks stay fixed. */
public final class StarOrreryPedestalMotion {
    public static final double DIAL_Y = -35.5D;
    public static final float DIAL_TILT = .12F;
    public static final double DIAL_SPEED = .05D;

    private StarOrreryPedestalMotion() {}

    /** Use unwrapped game time: 0.05 rad/s does not divide the ring clock's 12,000-second period. */
    public static float dialRotation(long gameTime, float partialTick, int litRings, int quality) {
        if (litRings <= 0 || quality <= 0) return 0F;
        double seconds = (gameTime + (double) partialTick) / 20D;
        return (float) ((seconds * DIAL_SPEED) % (Math.PI * 2D));
    }
}
