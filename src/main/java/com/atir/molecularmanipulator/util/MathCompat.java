package com.atir.molecularmanipulator.util;

/** Java 17 equivalents that keep long quantities out of floating-point arithmetic. */
public final class MathCompat {
    private MathCompat() {}
    public static long clamp(long value, long min, long max) { return Math.max(min, Math.min(max, value)); }
    public static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    public static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    public static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
}
