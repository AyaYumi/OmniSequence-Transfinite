package com.atir.molecularmanipulator.blockentity;

/** Transient client animation; no inventory, job or saved-world state. */
public final class SequenceCrownAnimation {
    private double sample = Double.NaN;
    private double completionAt = Double.NEGATIVE_INFINITY;
    private boolean working;
    private float activity;
    private float angle;

    public void receive(boolean working, boolean completed, double time) {
        sample(time);
        this.working = working;
        if (completed) completionAt = time;
    }

    public void sample(double time) {
        if (Double.isFinite(sample)) {
            double elapsed = time - sample;
            if (elapsed > 0 && elapsed <= 5) {
                float blend = (float) (1 - Math.pow(0.9, elapsed));
                activity += ((working ? 1 : 0) - activity) * blend;
                angle += (float) elapsed * (0.8F + activity * 3.6F);
            }
        }
        sample = time;
    }

    public float angle() { return angle; }
    public float activity() { return activity; }
    public float completion(double time) {
        return (float) com.atir.molecularmanipulator.util.MathCompat.clamp(1 - (time - completionAt) / 24.0, 0.0, 1.0);
    }
}
