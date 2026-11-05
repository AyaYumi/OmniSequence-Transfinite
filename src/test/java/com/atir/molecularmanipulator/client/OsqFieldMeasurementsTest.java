package com.atir.molecularmanipulator.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OsqFieldMeasurementsTest {
    private final Object font = new Object(), splitter = new Object();

    @Test
    void stableDrawsReuseTheScrolledSubstringAndAllMeasurements() {
        var cache = new OsqFieldMeasurements();
        var measure = new CountingMeasure(3);
        cache.layout("abcdef", 6, 0, 11, font, splitter, 1, 0, measure);
        assertEquals(3, cache.display());
        assertEquals("def", cache.visibleText());
        assertEquals(9, cache.cursorWidth());
        String visible = cache.visibleText();
        int calls = measure.calls;
        for (int frame = 0; frame < 10; frame++) {
            cache.layout(new String("abcdef"), 6, cache.display(), 11, font, splitter, 1, 0, measure);
            assertSame(visible, cache.visibleText());
            assertEquals(4, cache.positionAt("abcdef", cache.display(), 3, font, splitter, 1, 0, measure));
        }
        assertEquals(calls, measure.calls);
    }

    @Test
    void clickPrefixesAreLazyReusableAndNotClippedToTheVisibleEnd() {
        var cache = new OsqFieldMeasurements();
        var measure = new CountingMeasure(3);
        cache.layout("abcdef", 0, 0, 9, font, splitter, 0, 0, measure);
        assertEquals(3, cache.end());
        int calls = measure.calls;
        assertEquals(2, cache.positionAt("abcdef", 0, 8, font, splitter, 0, 0, measure));
        assertEquals(calls, measure.calls);
        assertEquals(6, cache.positionAt("abcdef", 0, 100, font, splitter, 0, 0, measure));
        assertEquals(calls + 2, measure.calls);
        assertEquals(6, cache.positionAt("abcdef", 0, 100, font, splitter, 0, 0, measure));
        assertEquals(calls + 2, measure.calls);
        assertEquals(0, cache.positionAt("abcdef", 0, -1, font, splitter, 0, 0, measure));
        assertEquals(1, cache.positionAt("a", 5, 100, font, splitter, 0, 0, measure));
    }

    @Test
    void cursorDisplayWidthAndTextChangesRefreshTheVisibleLayout() {
        var cache = new OsqFieldMeasurements();
        var measure = new CountingMeasure(2);
        cache.layout("abcdef", 0, 0, 20, font, splitter, 0, 0, measure);
        cache.layout("abcdef", 2, 0, 20, font, splitter, 0, 0, measure);
        assertEquals(4, cache.cursorWidth());
        cache.layout("abcdef", 2, 1, 20, font, splitter, 0, 0, measure);
        assertEquals("bcdef", cache.visibleText());
        assertEquals(2, cache.cursorWidth());
        cache.layout("abcdef", 2, 1, 4, font, splitter, 0, 0, measure);
        assertEquals("bc", cache.visibleText());
        cache.layout("aXYZef", 2, 1, 4, font, splitter, 0, 0, measure);
        assertEquals("XY", cache.visibleText());
        cache.layout("", 0, cache.display(), 4, font, splitter, 0, 0, measure);
        assertEquals(0, cache.display());
        assertEquals("", cache.visibleText());
        assertEquals(0, cache.cursorWidth());
    }

    @Test
    void reloadFontSplitterAndLiveScaleKeysInvalidateMeasurements() {
        var cache = new OsqFieldMeasurements();
        var measure = new CountingMeasure(2);
        cache.layout("abcd", 0, 0, 7, font, splitter, 0, 0, measure);
        assertEquals("abc", cache.visibleText());
        measure.advance = 4;
        cache.layout("abcd", 0, 0, 7, font, splitter, 1, 0, measure);
        assertEquals("a", cache.visibleText());
        measure.advance = 3;
        cache.layout("abcd", 0, 0, 7, font, splitter, 1, Float.floatToIntBits(12), measure);
        assertEquals("ab", cache.visibleText());
        assertEquals(2, cache.positionAt("abcd", 0, 6, font, splitter, 1, Float.floatToIntBits(12), measure));
        measure.advance = 2;
        Object replacementFont = new Object();
        cache.layout("abcd", 0, 0, 7, replacementFont, splitter, 1, Float.floatToIntBits(12), measure);
        assertEquals("abc", cache.visibleText());
        measure.advance = 1;
        cache.layout("abcd", 0, 0, 7, replacementFont, new Object(), 1, Float.floatToIntBits(12), measure);
        assertEquals("abcd", cache.visibleText());
        measure.advance = 4;
        assertEquals(1, cache.positionAt("abcd", 0, 6, replacementFont, splitter, 2, 0, measure));
    }

    @Test
    void codePointTraversalAndUtf16SelectionOffsetsAreUnchanged() {
        String value = "a\uD83D\uDE00bc";
        var cache = new OsqFieldMeasurements();
        var measure = new CountingMeasure(2.5f);
        cache.layout(value, 0, 0, 5, font, splitter, 0, 0, measure);
        assertEquals(3, cache.end());
        assertEquals("a\uD83D\uDE00", cache.visibleText());
        assertEquals(1, cache.positionAt(value, 0, 4.999, font, splitter, 0, 0, measure));
        assertEquals(3, cache.positionAt(value, 0, 5, font, splitter, 0, 0, measure));
        assertEquals(measure.width(value.substring(0, 2)), cache.widthTo(2, measure));
        cache.layout(value, 2, 0, 100, font, splitter, 0, 0, measure);
        assertEquals(measure.width(value.substring(0, 2)), cache.cursorWidth());
    }

    @Test
    void doesNotSumGlyphWidthsOrAssumeMonotonicPrefixesForClicks() {
        var cache = new OsqFieldMeasurements();
        OsqFieldMeasurements.Measure measure = text -> switch (text) {
            case "" -> 0;
            case "a" -> 8;
            case "ab" -> 2;
            default -> 10;
        };
        cache.layout("abc", 0, 0, 5, font, splitter, 0, 0, measure);
        assertEquals("", cache.visibleText());
        assertEquals(0, cache.positionAt("abc", 0, 5, font, splitter, 0, 0, measure));
        assertEquals(2, cache.positionAt("abc", 0, 8, font, splitter, 0, 0, measure));
    }

    @Test
    void matchesTheUncachedRenderAndClickLoopsForMixedUnicodeEdits() {
        var random = new Random(0x05F13D);
        var cache = new OsqFieldMeasurements();
        String[] characters = {"a", "i", "f", "中", "\uD83D\uDE00", "\u0301", " "};
        for (int iteration = 0; iteration < 600; iteration++) {
            var text = new StringBuilder();
            for (int size = random.nextInt(20); size > 0; size--) text.append(characters[random.nextInt(characters.length)]);
            String value = text.toString();
            List<Integer> boundaries = new ArrayList<>();
            for (int index = 0; ; index = value.offsetByCodePoints(index, 1)) {
                boundaries.add(index);
                if (index == value.length()) break;
            }
            int cursor = boundaries.get(random.nextInt(boundaries.size()));
            int display = boundaries.get(random.nextInt(boundaries.size()));
            int width = random.nextInt(40);
            var expected = uncached(value, cursor, display, width, OsqFieldMeasurementsTest::shapedWidth);
            cache.layout(value, cursor, display, width, font, splitter, 0, 0, OsqFieldMeasurementsTest::shapedWidth);
            assertEquals(expected.display, cache.display());
            assertEquals(expected.end, cache.end());
            assertEquals(expected.visible, cache.visibleText());
            assertEquals(expected.cursorWidth, cache.cursorWidth());
            for (double x : new double[]{-1, 0, 2.5, width, width + 10.75, 100}) {
                assertEquals(uncachedClick(value, expected.display, x, OsqFieldMeasurementsTest::shapedWidth),
                        cache.positionAt(value, cache.display(), x, font, splitter, 0, 0, OsqFieldMeasurementsTest::shapedWidth));
            }
            int selected = Math.clamp(random.nextInt(value.length() + 1), cache.display(), cache.end());
            assertEquals(shapedWidth(value.substring(cache.display(), selected)), cache.widthTo(selected, OsqFieldMeasurementsTest::shapedWidth));
        }
    }

    private record Visible(int display, int end, String visible, float cursorWidth) {}

    private static Visible uncached(String value, int cursor, int display, int width, OsqFieldMeasurements.Measure measure) {
        display = Math.clamp(display, 0, Math.min(cursor, value.length()));
        while (display < cursor && measure.width(value.substring(display, cursor)) > width - 2)
            display = value.offsetByCodePoints(display, 1);
        int end = display;
        while (end < value.length()) {
            int next = value.offsetByCodePoints(end, 1);
            if (measure.width(value.substring(display, next)) > width) break;
            end = next;
        }
        return new Visible(display, end, value.substring(display, end), measure.width(value.substring(display, Math.min(cursor, end))));
    }

    private static int uncachedClick(String value, int display, double x, OsqFieldMeasurements.Measure measure) {
        int position = Math.min(display, value.length());
        while (position < value.length()) {
            int next = value.offsetByCodePoints(position, 1);
            if (measure.width(value.substring(display, next)) > x) break;
            position = next;
        }
        return position;
    }

    private static float shapedWidth(String text) {
        float width = 0;
        for (int cp : text.codePoints().toArray()) width += cp == 'i' ? 1.25f : cp == 0x301 ? 0 : cp > 0xFFFF ? 5.5f : 3.25f;
        return width - (text.contains("fi") ? .625f : 0);
    }

    private static final class CountingMeasure implements OsqFieldMeasurements.Measure {
        int calls;
        float advance;
        CountingMeasure(float advance) { this.advance = advance; }
        @Override public float width(String text) { calls++; return text.codePointCount(0, text.length()) * advance; }
    }
}
