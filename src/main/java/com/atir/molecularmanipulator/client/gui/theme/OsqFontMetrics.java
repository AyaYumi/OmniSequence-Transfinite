package com.atir.molecularmanipulator.client.gui.theme;

/** Pure metric conversion shared by rendering, wrapping and input hit testing. */
final class OsqFontMetrics {
    private OsqFontMetrics() {}

    static float scale(float designSize, float nativeSize) {
        return Float.isFinite(designSize) && designSize > 0
                && Float.isFinite(nativeSize) && nativeSize > 0 ? designSize / nativeSize : 1;
    }

    static float baseline(float value) {
        return Float.isFinite(value) && value >= 0 && value <= 32 ? value : 7;
    }
}
