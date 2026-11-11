package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.client.gui.font.providers.UnihexProvider;
import org.junit.jupiter.api.Test;

/** Uses Minecraft's actual bitmap and indexed Unihex data, not bundled outlines or guessed widths. */
class StarComputationEngineFontTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/molecularmanipulator");

    @Test
    void defaultProviderChainUsesBitmapLatinUnihexChineseAndNativeSpaces() throws Exception {
        try (var runtime = new VanillaFontTestFixture("zh_cn")) {
            var face = VanillaFontTestFixture.DEFAULT_FONT;
            assertEquals(9, runtime.font.lineHeight);
            for (int cp : "AE 0123456789".codePoints().filter(cp -> cp != ' ').toArray()) {
                assertInstanceOf(BitmapProvider.class, runtime.providerFor(face, cp));
                var sheet = sheet(runtime, cp);
                assertEquals(8, sheet.getPixelHeight() / sheet.getOversample(), .001f);
                assertEquals(0, sheet.getTop(), .001f);
            }
            assertInstanceOf(UnihexProvider.class, runtime.providerFor(face, '熵'));
            var entropy = sheet(runtime, '熵');
            assertEquals(16, entropy.getPixelHeight());
            assertEquals(2, entropy.getOversample());
            assertEquals(8, entropy.getBottom() - entropy.getTop(), .001f);
            assertEquals(6, runtime.glyph(face, 'A').getAdvance());
            assertEquals(9, runtime.glyph(face, '熵').getAdvance());
            var space = runtime.findGlyph(face, ' ');
            assertNotNull(space);
            assertEquals(4, space.getAdvance());
            space.bake(unused -> fail("The native space provider must not draw a glyph cell"));
        }
    }

    @Test
    void realVanillaGlyphsCoverAllLocalizedEntropyUiCjk() throws Exception {
        var language = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/zh_cn.json"))).getAsJsonObject();
        var codepoints = new HashSet<Integer>();
        language.entrySet().stream().filter(entry -> entry.getKey().startsWith("gui.molecularmanipulator.star_computation.")
                || entry.getKey().equals("block.molecularmanipulator.star_computation_engine_core"))
                .forEach(entry -> entry.getValue().getAsString().codePoints()
                        .filter(cp -> cp >= 0x3400 && cp <= 0x9fff).forEach(codepoints::add));
        assertTrue(codepoints.contains((int) '熵'));
        try (var runtime = new VanillaFontTestFixture("zh_cn")) {
            for (int cp : codepoints) {
                String label = "minecraft:default lacks U+" + Integer.toHexString(cp);
                assertNotNull(runtime.findGlyph(VanillaFontTestFixture.DEFAULT_FONT, cp), label);
                assertInstanceOf(UnihexProvider.class, runtime.providerFor(VanillaFontTestFixture.DEFAULT_FONT, cp), label);
                var sheet = sheet(runtime, cp);
                assertEquals(8, sheet.getRight() - sheet.getLeft(), .001f, label);
                assertEquals(8, sheet.getBottom() - sheet.getTop(), .001f, label);
            }
        }
    }

    private static SheetGlyphInfo sheet(VanillaFontTestFixture runtime, int codepoint) {
        var glyph = runtime.findGlyph(VanillaFontTestFixture.DEFAULT_FONT, codepoint);
        assertNotNull(glyph, "Missing actual U+" + Integer.toHexString(codepoint));
        var sheet = new SheetGlyphInfo[1];
        glyph.bake(info -> { sheet[0] = info; return null; });
        assertNotNull(sheet[0], "U+" + Integer.toHexString(codepoint) + " must have a drawable glyph cell");
        return sheet[0];
    }
}
