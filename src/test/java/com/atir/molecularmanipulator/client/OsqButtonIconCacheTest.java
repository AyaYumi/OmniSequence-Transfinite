package com.atir.molecularmanipulator.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OsqButtonIconCacheTest {
    private static final String ROOT = "molecularmanipulator:osq_light/seq/icons/";
    private static final String ORIGINAL = ROOT + "play_ink2_12";

    @Test
    void prefersRequestedTintAtOriginalSizeThenEightPixelFallback() {
        var cache = new OsqButtonIconCache();
        var catalog = new Catalog(ROOT + "pause_white_12", ROOT + "pause_white_8");
        assertEquals(ROOT + "pause_white_12", cache.resolve(0, ORIGINAL, "pause", "white", catalog));
        assertEquals(List.of(ROOT + "pause_white_12"), catalog.queries);

        catalog.available.remove(ROOT + "pause_white_12");
        catalog.queries.clear();
        assertEquals(ROOT + "pause_white_8", cache.resolve(1, ORIGINAL, "pause", "white", catalog));
        assertEquals(List.of(ROOT + "pause_white_12", ROOT + "pause_white_8"), catalog.queries);
    }

    @Test
    void bothMissingRetainsRenamedIdAndOriginalTintNotOriginalIcon() {
        var cache = new OsqButtonIconCache();
        var catalog = new Catalog(ORIGINAL);
        assertEquals(ROOT + "missing_ink2_12", cache.resolve(0, ORIGINAL, "missing", "dis", catalog));
        assertEquals(List.of(ROOT + "missing_dis_12", ROOT + "missing_dis_8"), catalog.queries);
        assertEquals(ORIGINAL, cache.resolve(0, ORIGINAL, null, "white", catalog));
    }

    @Test
    void unchangedOrRevisitedStatesDoNotProbeResourcesAgain() {
        var cache = new OsqButtonIconCache();
        var catalog = new Catalog(ROOT + "play_ink_12", ROOT + "pause_white_8");
        String normal = cache.resolve(4, ORIGINAL, null, "ink", catalog);
        String active = cache.resolve(4, ORIGINAL, "pause", "white", catalog);
        int probes = catalog.queries.size();
        assertSame(active, cache.resolve(4, new String(ORIGINAL), new String("pause"), "white", catalog));
        assertSame(normal, cache.resolve(4, ORIGINAL, null, "ink", catalog));
        assertEquals(probes, catalog.queries.size());
    }

    @Test
    void resourceGenerationInvalidatesPositiveAndNegativeDecisions() {
        var cache = new OsqButtonIconCache();
        var catalog = new Catalog();
        assertEquals(ORIGINAL, cache.resolve(12, ORIGINAL, null, "white", catalog));
        catalog.available.add(ROOT + "play_white_12");
        int probes = catalog.queries.size();
        assertEquals(ORIGINAL, cache.resolve(12, ORIGINAL, null, "white", catalog));
        assertEquals(probes, catalog.queries.size());
        assertEquals(ROOT + "play_white_12", cache.resolve(13, ORIGINAL, null, "white", catalog));
        catalog.available.clear();
        assertEquals(ORIGINAL, cache.resolve(14, ORIGINAL, null, "white", catalog));
    }

    @Test
    void changingTheOriginalIconIsPartOfTheCacheKey() {
        var cache = new OsqButtonIconCache();
        var catalog = new Catalog(ROOT + "play_ink_12", ROOT + "net_ink_12");
        assertEquals(ROOT + "play_ink_12", cache.resolve(0, ORIGINAL, null, "ink", catalog));
        assertEquals(ROOT + "net_ink_12", cache.resolve(0, ROOT + "net_mute_12", null, "ink", catalog));
    }

    @Test
    void noRenameAndSupplierReturningNullRemainDifferentInputs() {
        var cache = new OsqButtonIconCache();
        var catalog = new Catalog(ROOT + "play_ink_12", ROOT + "null_ink_12");
        assertEquals(ROOT + "play_ink_12", cache.resolve(0, ORIGINAL, null, "ink", catalog));
        assertEquals(ROOT + "null_ink_12", cache.resolve(0, ORIGINAL, String.valueOf((String) null), "ink", catalog));
    }

    @Test
    void preservesTheOriginalRegexReplacementAndFallbackRules() {
        String[] originals = {ORIGINAL, ROOT + "long_name_inv_24", ROOT + "plain", "other:icons/play_ink_8"};
        String[] names = {null, "out", "net_2", "$1"};
        String[] tints = {"ink", "white", "inv", "dis"};
        for (String original : originals) for (String name : names) for (String tint : tints) {
            for (int available = 0; available < 3; available++) {
                int choice = available;
                Predicate<String> exists = id -> choice == 1 || choice == 2 && id.endsWith("_8");
                assertEquals(uncached(original, name, tint, exists),
                        new OsqButtonIconCache().resolve(0, original, name, tint, exists));
            }
        }
    }

    private static String uncached(String original, String name, String tint, Predicate<String> exists) {
        String sprite = name == null ? original : original.replaceAll(
                "/icons/[^/]+_(ink2|ink|mute|white|acc|dis|inv)_(\\d+)$", "/icons/" + name + "_$1_$2");
        String candidate = sprite.replaceAll("_(ink2|ink|mute|white|acc|dis|inv)_(\\d+)$", "_" + tint + "_$2");
        if (exists.test(candidate)) return candidate;
        String fallback = sprite.replaceAll("_(ink2|ink|mute|white|acc|dis|inv)_\\d+$", "_" + tint + "_8");
        return exists.test(fallback) ? fallback : sprite;
    }

    private static final class Catalog implements Predicate<String> {
        final Set<String> available;
        final List<String> queries = new ArrayList<>();
        Catalog(String... available) { this.available = new HashSet<>(List.of(available)); }
        @Override public boolean test(String id) { queries.add(id); return available.contains(id); }
    }
}
