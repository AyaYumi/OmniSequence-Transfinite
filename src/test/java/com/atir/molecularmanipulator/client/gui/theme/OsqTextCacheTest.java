package com.atir.molecularmanipulator.client.gui.theme;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.client.UiRenderRecorder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OsqTextCacheTest {
    private static final ResourceLocation FACE = ResourceLocation.parse("test:cache");
    @AfterEach void clear() { OsqFonts.clearInkMetrics(); }

    @Test void widthsReuseRealSplitterResultsAndInvalidateOnReloadAndFontReplacement() throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        var calls = new AtomicInteger();
        var first = font(calls, 3.25F);
        float width = OsqFonts.widthF(first, "A中文😀", FACE);
        int measured = calls.get();
        assertTrue(measured > 0);
        for (int i = 0; i < 100; i++) assertEquals(width, OsqFonts.widthF(first, new String("A中文😀"), FACE));
        assertEquals(measured, calls.get());
        long generation = OsqFonts.resourceGeneration();
        OsqFonts.clearInkMetrics();
        assertTrue(OsqFonts.resourceGeneration() > generation);
        assertEquals(width, OsqFonts.widthF(first, "A中文😀", FACE));
        assertTrue(calls.get() > measured);
        var second = font(calls, 6.5F);
        assertEquals(width * 2, OsqFonts.widthF(second, "A中文😀", FACE));
    }

    @Test void cachedWrappingKeepsOriginalWordAndCodepointBreaksForEveryWidth() throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        var calls = new AtomicInteger();
        var font = font(calls, 3.25F);
        for (String text : List.of("这是中文换行😀测试", "some long words 12345 here", "混合 words 😀 tail", "")) {
            for (float width : new float[]{0, 3, 7.25F, 20, 52}) {
                var expected = reference(font, text, width);
                var first = OsqFonts.wrap(font, text, FACE, width);
                assertEquals(expected, first);
                int measured = calls.get();
                assertSame(first, OsqFonts.wrap(font, text, FACE, width));
                assertEquals(measured, calls.get());
                assertThrows(UnsupportedOperationException.class, () -> first.add("mutated"));
            }
        }
    }

    @Test void entryTextTracksChangedLiteralTranslationAndArguments() throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        var entry = new OsqLayout.Entry();
        entry.text = "first";
        var first = entry.staticText();
        assertSame(first, entry.staticText());
        entry.text = "second";
        assertEquals("second", entry.staticText().getString());
        assertNotSame(first, entry.staticText());
        entry.lang = "test.%s";
        entry.langArgs = new com.google.gson.JsonArray();
        entry.langArgs.add("one");
        var translated = entry.staticText();
        entry.langArgs.set(0, new com.google.gson.JsonPrimitive("two"));
        assertNotSame(translated, entry.staticText());
        assertSame(entry.staticText(), entry.staticText());
    }

    private static Font font(AtomicInteger calls, float advance) {
        var splitter = new StringSplitter((codepoint, style) -> { calls.incrementAndGet(); return advance; });
        return new Font(id -> null, false) {
            @Override public StringSplitter getSplitter() { return splitter; }
        };
    }

    private static List<String> reference(Font font, String text, float maxWidth) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i); i += Character.charCount(cp);
            String next = line.toString() + new String(Character.toChars(cp));
            float width = font.getSplitter().stringWidth(OsqFonts.styled(Component.literal(next), FACE));
            if (line.length() > 0 && width > maxWidth) {
                int sp = line.lastIndexOf(" ");
                boolean latin = cp < 0x2E80 && cp != ' ';
                if (latin && sp > 0 && sp > line.length() - 24) {
                    out.add(line.substring(0, sp)); line = new StringBuilder(line.substring(sp + 1));
                } else { out.add(line.toString()); line = new StringBuilder(); }
                if (cp == ' ') continue;
            }
            line.appendCodePoint(cp);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
