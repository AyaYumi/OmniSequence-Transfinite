package com.atir.molecularmanipulator.client;

import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Per-screen, client-thread snapshot of pattern search strings. Does not own menu/index lifecycle. */
final class PatternSearchCandidates {
    private PatternSearchIndexState source;
    private int containerId, revision;
    private long generation;
    // LanguageManager installs a new Language on both language changes and resource-pack reloads.
    // Only localized search strings are cached here, not font metrics or rendered text.
    private Object language;
    private List<Entry> cached = List.of();

    boolean isCurrent(PatternSearchIndexState index, Object activeLanguage) {
        return source == index && index.complete() && containerId == index.containerId()
                && generation == index.generation() && revision == index.revision()
                && language == activeLanguage;
    }

    List<Entry> snapshot(PatternSearchIndexState index, Object activeLanguage) {
        if (!index.complete()) throw new IllegalStateException("Search candidates require a complete index");
        if (isCurrent(index, activeLanguage)) return cached;
        var entries = new ArrayList<Entry>();
        // Many patterns share a key. Resolve its name/ID and fold the strings just once per snapshot.
        var keyCache = new HashMap<AEKey, ClientSearchMatcher.Candidates>();
        for (var entry : index.entries()) {
            entries.add(new Entry(entry.sourceSlot(), ClientSearchMatcher.snapshotPattern(entry, keyCache)));
        }
        cached = List.copyOf(entries);
        source = index;
        containerId = index.containerId();
        generation = index.generation();
        revision = index.revision();
        language = activeLanguage;
        return cached;
    }

    void clear() {
        source = null;
        language = null;
        cached = List.of();
    }

    record Entry(int sourceSlot, ClientSearchMatcher.Candidates candidates) { }
}
