package com.atir.molecularmanipulator.client.gui.theme;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OsqSpriteGeometryTest {
    @Test void windowRetainsTilesButNeedsOnlyOneSubmission() {
        var quads = OsqSpriteGeometry.nineSlice(434, 306, 18, 18, 5, 5, 5, 5);
        assertEquals((53 + 2) * (37 + 2), quads.size()); // ceil(424/8) by ceil(296/8), plus borders
        assertEquals(434L * 306, area(quads));
        assertEquals(new OsqSpriteGeometry.Quad(0, 0, 5, 5, 0, 0, 5/18f, 5/18f), quads.getFirst());
        assertTrue(quads.stream().allMatch(q -> q.u0() >= 0 && q.u1() <= 1 && q.v0() >= 0 && q.v1() <= 1));
    }

    @Test void tilesClipRemainderUvsInsteadOfStretchingArtwork() {
        var quads = OsqSpriteGeometry.tile(19, 10, 8, 8);
        assertEquals(6, quads.size());
        assertEquals(new OsqSpriteGeometry.Quad(16, 8, 3, 2, 0, 0, 3/8f, 2/8f), quads.getLast());
        assertEquals(190, area(quads));
    }

    @Test void unscaledAndOneAxisSlicesMatchVanilla() {
        assertEquals(OsqSpriteGeometry.stretch(18, 18), OsqSpriteGeometry.nineSlice(18, 18, 18, 18, 5, 5, 5, 5));
        assertEquals(7, OsqSpriteGeometry.nineSlice(50, 18, 18, 18, 5, 5, 5, 5).size());
        assertEquals(7, OsqSpriteGeometry.nineSlice(18, 50, 18, 18, 5, 5, 5, 5).size());
        assertTrue(OsqSpriteGeometry.stretch(0, 10).isEmpty());
        assertTrue(OsqSpriteGeometry.nineSlice(10, 0, 18, 18, 5, 5, 5, 5).isEmpty());
    }

    @Test void everyBundledNineSliceHasValidUvsAtRealisticSizes() throws Exception {
        for (String root : new String[]{"src/main/resources", "resourcepacks/OmniSequence Dark"}) {
            try (var files = Files.walk(Path.of(root))) {
                for (var path : files.filter(p -> p.toString().endsWith(".png.mcmeta")).toList()) {
                    var metadata = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                    if (!metadata.has("gui")) continue;
                    var scaling = metadata.getAsJsonObject("gui").getAsJsonObject("scaling");
                    if (!"nine_slice".equals(scaling.get("type").getAsString())) continue;
                    var border = scaling.getAsJsonObject("border");
                    for (int[] size : new int[][]{{434,306}, {73,17}, {4,3}}) {
                        var quads = OsqSpriteGeometry.nineSlice(size[0], size[1], scaling.get("width").getAsInt(),
                                scaling.get("height").getAsInt(), border.get("left").getAsInt(),
                                border.get("top").getAsInt(), border.get("right").getAsInt(), border.get("bottom").getAsInt());
                        assertTrue(quads.stream().allMatch(q -> q.width() > 0 && q.height() > 0
                                && q.u0() >= 0 && q.u1() <= 1 && q.v0() >= 0 && q.v1() <= 1), path.toString());
                    }
                }
            }
        }
    }

    private long area(java.util.List<OsqSpriteGeometry.Quad> quads) {
        return quads.stream().mapToLong(q -> (long) q.width() * q.height()).sum();
    }
}
