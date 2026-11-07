package com.atir.molecularmanipulator.client.gui.theme;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.client.UiRenderRecorder;
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

    @Test void cachedWrappingPreservesTextCodepointsAndTrimsWrapWhitespaceForEveryWidth() throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        var calls = new AtomicInteger();
        var font = font(calls, 3.25F);
        for (String text : List.of("这是中文换行😀测试", "some long words 12345 here", "混合 words 😀 tail", "")) {
            for (float width : new float[]{0, 3, 7.25F, 20, 52}) {
                var first = OsqFonts.wrap(font, text, FACE, width);
                assertEquals(text.replaceAll("\\s+", ""), String.join("",first).replaceAll("\\s+", ""));
                assertTrue(first.stream().noneMatch(line -> line.endsWith(" ")));
                assertTrue(first.stream().noneMatch(line -> !line.isEmpty() && Character.isLowSurrogate(line.charAt(0))));
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

    @Test void wrappingPreservesWholeIntegersWordsAndClosingPunctuation() {
        assertEquals(List.of("前缀", "9223372036854775807", "尾"),
                OsqFonts.wrapText("前缀 9223372036854775807 尾",19,String::length));
        var words=OsqFonts.wrapText("材料 Adapter 12,345 次。",8,String::length);
        assertTrue(words.contains("Adapter"));assertTrue(words.stream().anyMatch(line->line.contains("12,345")));
        var chinese=OsqFonts.wrapText("你好，世界。",2,String::length);
        assertTrue(chinese.stream().noneMatch(line->"，。".contains(line.substring(0,1))));
        assertEquals("你好，世界。",String.join("",chinese));
        assertEquals(List.of("one","two"),OsqFonts.wrapText("one\ntwo",20,String::length));
    }
}
