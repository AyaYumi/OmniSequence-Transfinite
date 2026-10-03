package com.atir.molecularmanipulator.client.render;

/** Client-only easing; authoritative assembly age controls the opening sequence. */
public final class SingularityEffectState {
    private double previous = Double.NaN;
    private double clock;
    private double power;
    private double speed;

    public SingularityStructureEffects.Frame sample(double now, int mode, double age) {
        double target = switch (mode) { case 1 -> 1; case 2 -> .48; case 3, 4 -> .28; default -> .62; };
        double targetSpeed = switch (mode) { case 1 -> 1; case 2 -> .18; case 3, 4 -> .4; default -> .35; };
        if (Double.isNaN(previous) || now < previous - 40) {
            previous = now;
            clock = now % 24000;
            power = target;
            speed = targetSpeed;
        }
        double delta = Math.min(40, Math.max(0, now - previous));
        double oldSpeed = speed;
        double ease = 1 - Math.exp(-delta / 20);
        power += (target - power) * ease;
        speed += (targetSpeed - speed) * ease;
        clock = (clock + delta * (speed + oldSpeed) * .5) % 24000;
        previous = Math.max(previous, now);
        return new SingularityStructureEffects.Frame((float) clock, (float) Math.min(160, Math.max(0, age)), (float) power);
    }
}
