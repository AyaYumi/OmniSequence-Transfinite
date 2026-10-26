package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reads the actual TTF glyphs, not just filenames; no Minecraft client is needed. */
class NegativeEntropyEngineFontTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/molecularmanipulator");
    private static final Path FONTS = ASSETS.resolve("font");
    private static final List<String> FACES = List.of("500_5", "600_5_4", "600_6", "700_6_6", "700_9", "900_13");

    @Test
    void localFallbackPrecedesVanillaAndMatchesEveryUiFaceSizeAndShift() throws Exception {
        for (var suffix : FACES) {
            var providers = JsonParser.parseString(Files.readString(FONTS.resolve("osq_entropy_" + suffix + ".json")))
                    .getAsJsonObject().getAsJsonArray("providers");
            assertEquals(3, providers.size());
            var primary = providers.get(0).getAsJsonObject();
            var fallback = providers.get(1).getAsJsonObject();
            assertTrue(primary.get("file").getAsString().startsWith("molecularmanipulator:osq_sc_"));
            assertEquals("molecularmanipulator:osq_entropy_" + suffix.split("_")[0] + ".ttf",
                    fallback.get("file").getAsString());
            for (var key : List.of("type", "size", "oversample", "shift", "skip")) {
                assertEquals(primary.get(key), fallback.get(key), suffix + ": " + key);
            }
            assertEquals("minecraft:default", providers.get(2).getAsJsonObject().get("id").getAsString());
        }
    }

    @Test
    void realGlyphsCoverAllLocalizedEntropyUiCjkWithoutVanillaFallback() throws Exception {
        var language = JsonParser.parseString(Files.readString(ASSETS.resolve("lang/zh_cn.json"))).getAsJsonObject();
        var codepoints = new HashSet<Integer>();
        language.entrySet().stream().filter(entry -> entry.getKey().startsWith("gui.molecularmanipulator.entropy.")
                || entry.getKey().equals("block.molecularmanipulator.negative_entropy_engine_core"))
                .forEach(entry -> entry.getValue().getAsString().codePoints()
                        .filter(cp -> cp >= 0x3400 && cp <= 0x9fff).forEach(codepoints::add));
        assertTrue(codepoints.contains((int) '熵'));
        var context = new FontRenderContext(new AffineTransform(), true, true);
        for (var suffix : FACES) {
            var providers = JsonParser.parseString(Files.readString(FONTS.resolve("osq_entropy_" + suffix + ".json")))
                    .getAsJsonObject().getAsJsonArray("providers");
            var faces = new java.util.ArrayList<Font>();
            for (int index = 0; index < 2; index++) {
                var path = FONTS.resolve(providers.get(index).getAsJsonObject().get("file").getAsString().split(":")[1]);
                try (var stream = Files.newInputStream(path)) { faces.add(Font.createFont(Font.TRUETYPE_FONT, stream)); }
                if (index == 1) assertTrue(Files.size(path) < 16_384, "Only tiny local subsets are permitted");
            }
            assertTrue(faces.get(1).canDisplay('熵'), suffix);
            assertTrue(faces.get(1).deriveFont(13f).createGlyphVector(context, "熵").getVisualBounds().getHeight() > 8,
                    "The fallback must contain a real full-sized outline, not a blank/notdef glyph");
            for (int cp : codepoints) assertTrue(faces.stream().anyMatch(face -> face.canDisplay(cp)),
                    suffix + " lacks U+" + Integer.toHexString(cp));
        }
    }
}
