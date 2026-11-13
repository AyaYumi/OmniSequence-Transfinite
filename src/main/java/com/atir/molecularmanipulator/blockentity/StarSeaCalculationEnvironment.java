package com.atir.molecularmanipulator.blockentity;

/** Numeric environment policy, independent of Minecraft worlds and weather. */
public final class StarSeaCalculationEnvironment {
    public static final int DAY_LENGTH = 24_000;
    /** Real normal-dimension night is [13000, 23000), not weather-darkened day. */
    public static final int NIGHT_START = 13_000;
    public static final int NIGHT_END = 23_000;
    /** Inclusive maximum raw local brightness above every roof column. */
    public static final int MAX_ROOF_BRIGHTNESS = 7;
    /** The reserved body occupies y=0..8 relative to the bottom-front core. */
    public static final int ROOF_SAMPLE_HEIGHT = 9;

    private StarSeaCalculationEnvironment() {}

    public static boolean isNight(boolean hasSkyLight, boolean hasFixedTime, long dayTime) {
        long time = Math.floorMod(dayTime, (long) DAY_LENGTH);
        return hasSkyLight && !hasFixedTime && time >= NIGHT_START && time < NIGHT_END;
    }

    public static boolean isDarkEnough(int maximumRoofBrightness) {
        return maximumRoofBrightness <= MAX_ROOF_BRIGHTNESS;
    }
}
