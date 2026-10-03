package com.atir.molecularmanipulator.client.render;

/** The white horizon swells on assembly, then settles to a quiet luminous sphere. */
public final class SingularityWhiteHoleProfile {
    /** The lens surrounds the enlarged horizon instead of clipping it at the old radius. */
    public static final float CORE_SCALE = 2.0F;
    public static final float FIELD_RADIUS = 12.5F;

    private SingularityWhiteHoleProfile() {}

    public static float radius(SingularityStructureEffects.Frame frame) {
        double reveal = smooth((frame.age() - 60) / 24);
        double settle = smooth((frame.age() - 88) / 64);
        double time = ((frame.time() % 24000) + 24000) % 24000;
        return (float) (CORE_SCALE * ((.45 + .55 * reveal) * (1.9 + 1.5 * (1 - settle))
                + .018 * Math.sin(time * Math.PI * 2 / 400)));
    }

    private static double smooth(double t) {
        t = com.atir.molecularmanipulator.util.MathCompat.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
