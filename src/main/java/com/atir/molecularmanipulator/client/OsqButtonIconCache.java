package com.atir.molecularmanipulator.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Per-button resource decisions only; suppliers and drawing remain live in OsqUi. */
final class OsqButtonIconCache {
    private static final Pattern NAME = Pattern.compile("/icons/[^/]+_(ink2|ink|mute|white|acc|dis|inv)_(\\d+)$");
    private static final Pattern TINT = Pattern.compile("_(ink2|ink|mute|white|acc|dis|inv)_(\\d+)$");
    private static final Pattern SIZE = Pattern.compile("_(ink2|ink|mute|white|acc|dis|inv)_\\d+$");
    private record Key(String original, String name, String tint) {}

    private final Map<Key, String> sprites = new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Key, String> entry) { return size() > 64; }
    };
    private long generation = Long.MIN_VALUE;
    private Key lastKey;
    private String lastSprite;

    /** name == null means no rename; a supplier returning null is passed as the string "null". */
    String resolve(long generation, String original, String name, String tint, Predicate<String> exists) {
        if (this.generation != generation) {
            this.generation = generation;
            sprites.clear();
            lastKey = null;
        }
        if (lastKey != null && lastKey.original.equals(original)
                && Objects.equals(lastKey.name, name) && lastKey.tint.equals(tint)) return lastSprite;
        var key = new Key(original, name, tint);
        String sprite = sprites.get(key);
        if (sprite == null) {
            String renamed = name == null ? original : NAME.matcher(original).replaceAll("/icons/" + name + "_$1_$2");
            String candidate = TINT.matcher(renamed).replaceAll("_" + tint + "_$2");
            if (exists.test(candidate)) sprite = candidate;
            else {
                String fallback = SIZE.matcher(renamed).replaceAll("_" + tint + "_8");
                // If both are absent, retain the renamed ID, not the original icon or its tint.
                sprite = exists.test(fallback) ? fallback : renamed;
            }
            sprites.put(key, sprite);
        }
        lastKey = key;
        return lastSprite = sprite;
    }
}
