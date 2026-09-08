package com.atir.molecularmanipulator.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Decimal SI labels for compact displays, with lossless long values in tooltips. */
public final class DisplayNumbers {
    private static final String[] UNITS = {"", "K", "M", "G", "T", "P", "E"};

    private DisplayNumbers() {}

    public static String compact(long value) {
        return compact(BigDecimal.valueOf(value));
    }

    public static String compact(double value) {
        return Double.isFinite(value) ? compact(BigDecimal.valueOf(value)) : nonFinite(value);
    }

    private static String compact(BigDecimal value) {
        int unit = 0;
        while (value.abs().compareTo(BigDecimal.valueOf(1000)) >= 0 && unit < UNITS.length - 1) {
            value = value.movePointLeft(3);
            unit++;
        }
        if (unit > 0) {
            value = value.setScale(2, RoundingMode.HALF_UP);
            if (value.abs().compareTo(BigDecimal.valueOf(1000)) >= 0 && unit < UNITS.length - 1) {
                value = value.movePointLeft(3);
                unit++;
            }
        }
        return value.stripTrailingZeros().toPlainString() + UNITS[unit];
    }

    public static String exact(long value) {
        return exact(BigDecimal.valueOf(value));
    }

    public static String exact(double value) {
        return Double.isFinite(value) ? exact(BigDecimal.valueOf(value)) : nonFinite(value);
    }

    private static String nonFinite(double value) {
        return Double.isNaN(value) ? "—" : value < 0 ? "−∞" : "∞";
    }

    private static String exact(BigDecimal value) {
        var format = NumberFormat.getNumberInstance(Locale.ROOT);
        format.setMaximumFractionDigits(Math.max(0, value.scale()));
        return format.format(value);
    }
}
