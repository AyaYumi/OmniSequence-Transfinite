package com.atir.molecularmanipulator.client.gui.theme;

/** Pure metric conversion shared by rendering, wrapping and input hit testing. */
final class OsqFontMetrics {
    private OsqFontMetrics() {}

    static float providerSize(String fontPath, float fallback) {
        // osq_nsc_600_6_4 encodes a 6.4px provider, not a 600px provider.
        String[] parts = fontPath.split("_", 4);
        if (parts.length != 4 || !parts[0].equals("osq")) return fallback;
        try {
            float value = Float.parseFloat(parts[3].replace('_', '.'));
            return Float.isFinite(value) && value > 0 ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static float scale(float designSize, float nativeSize) {
        return Float.isFinite(designSize) && designSize > 0
                && Float.isFinite(nativeSize) && nativeSize > 0 ? designSize / nativeSize : 1;
    }

    static float baseline(float value) {
        return Float.isFinite(value) && value >= 0 && value <= 32 ? value : 7;
    }
}
