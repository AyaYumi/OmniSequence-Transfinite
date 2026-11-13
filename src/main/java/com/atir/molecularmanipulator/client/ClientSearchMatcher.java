package com.atir.molecularmanipulator.client;

import appeng.api.stacks.AEKey;
import com.atir.molecularmanipulator.network.PatternSearchIndexEntry;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-only text matching with optional Just Enough Characters support.
 *
 * <p>JEC is deliberately accessed by name so common code never links against its client-only classes.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSearchMatcher {
    private static final String JEC_MATCH_CLASS = "me.towdium.jecharacters.utils.Match";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Query EMPTY_QUERY = new Query(List.of());
    private static final Candidates NO_CANDIDATES = new Candidates(List.of());

    private static volatile boolean jecLookupComplete;
    private static volatile MethodHandle jecContains;

    private ClientSearchMatcher() {
    }

    /** Compile once per search, rather than splitting and case-folding for every pattern. */
    public static Query compile(String query) {
        if (query == null || query.isBlank()) return EMPTY_QUERY;
        var tokens = new ArrayList<Text>();
        for (var token : WHITESPACE.split(query.strip())) tokens.add(new Text(token));
        return new Query(List.copyOf(tokens));
    }

    /** Copy candidate strings now; no mutable CharSequence or localized AEKey is retained. */
    public static Candidates snapshot(Iterable<? extends CharSequence> candidates) {
        if (candidates == null) return NO_CANDIDATES;
        var values = new ArrayList<Text>();
        for (var candidate : candidates) {
            if (candidate != null) values.add(new Text(candidate.toString()));
        }
        return new Candidates(List.copyOf(values));
    }

    /** Matches every whitespace-separated query token against the candidate. */
    public static boolean matchesTokens(CharSequence candidate, String query) {
        return compile(query).matches(candidate);
    }

    /** Tokens may match different candidates, just as in the uncached matcher. */
    public static boolean matchesTokens(Iterable<? extends CharSequence> candidates, String query) {
        return compile(query).matches(candidates);
    }

    /** Matches localized input/output names and stable registry identifiers. */
    public static boolean matchesPattern(PatternSearchIndexEntry entry, String query) {
        var compiled = compile(query);
        return compiled.isEmpty() || compiled.matches(snapshotPattern(entry, null));
    }

    /** Immutable query data, but matching must stay on the client thread: JEC may not be thread-safe. */
    public static final class Query {
        private final List<Text> tokens;

        private Query(List<Text> tokens) { this.tokens = tokens; }

        public boolean isEmpty() { return tokens.isEmpty(); }

        public boolean matches(CharSequence candidate) {
            if (isEmpty()) return true;
            if (candidate == null) return false;
            var text = new Text(candidate.toString());
            for (var token : tokens) {
                if (!contains(text.value(), token.value(), text.folded(), token.folded())) return false;
            }
            return true;
        }

        public boolean matches(Iterable<? extends CharSequence> candidates) {
            return isEmpty() || matches(snapshot(candidates));
        }

        public boolean matches(Candidates candidates) {
            if (isEmpty()) return true;
            if (candidates == null) return false;
            for (var token : tokens) {
                boolean matched = false;
                for (var candidate : candidates.values) {
                    if (contains(candidate.value(), token.value(), candidate.folded(), token.folded())) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) return false;
            }
            return true;
        }
    }

    /** Both original text for JEC and locale-stable folded text for the fallback. */
    public static final class Candidates {
        private final List<Text> values;

        private Candidates(List<Text> values) { this.values = values; }
    }

    private record Text(String value, String folded) {
        private Text(String value) { this(value, value.toLowerCase(Locale.ROOT)); }
    }

    static Candidates snapshotPattern(PatternSearchIndexEntry entry, Map<AEKey, Candidates> keyCache) {
        var values = new ArrayList<Text>(entry.keyCount() * 3);
        addKeyCandidates(values, entry.outputs(), keyCache);
        addKeyCandidates(values, entry.inputs(), keyCache);
        return new Candidates(List.copyOf(values));
    }

    /**
     * Uses JEC's pinyin-aware matcher when available, otherwise performs a locale-stable,
     * case-insensitive substring match.
     */
    public static boolean contains(String candidate, CharSequence query) {
        return contains(candidate, query, null, null);
    }

    private static boolean contains(String candidate, CharSequence query, String foldedCandidate, String foldedQuery) {
        if (candidate == null || query == null) {
            return false;
        }

        var handle = getJecContains();
        if (handle != null) {
            try {
                return (boolean) handle.invokeExact(candidate, query);
            } catch (Throwable failure) {
                if (failure instanceof Error error && !(error instanceof LinkageError)) {
                    throw error;
                }
                jecContains = null;
            }
        }

        return (foldedCandidate != null ? foldedCandidate : candidate.toLowerCase(Locale.ROOT))
                .contains(foldedQuery != null ? foldedQuery : query.toString().toLowerCase(Locale.ROOT));
    }

    private static void addKeyCandidates(List<Text> candidates, Iterable<AEKey> keys,
            Map<AEKey, Candidates> keyCache) {
        for (var key : keys) {
            var values = keyCache == null ? snapshotKey(key) : keyCache.computeIfAbsent(key, ClientSearchMatcher::snapshotKey);
            candidates.addAll(values.values);
        }
    }

    private static Candidates snapshotKey(AEKey key) {
        var id = key.getId();
        return new Candidates(List.of(new Text(key.getDisplayName().getString()),
                new Text(id.toString()), new Text(id.getPath())));
    }

    private static MethodHandle getJecContains() {
        if (!jecLookupComplete) {
            synchronized (ClientSearchMatcher.class) {
                if (!jecLookupComplete) {
                    jecContains = findJecContains();
                    jecLookupComplete = true;
                }
            }
        }
        return jecContains;
    }

    private static MethodHandle findJecContains() {
        try {
            var matchClass = Class.forName(
                    JEC_MATCH_CLASS,
                    false,
                    ClientSearchMatcher.class.getClassLoader());
            return MethodHandles.publicLookup().findStatic(
                    matchClass,
                    "contains",
                    MethodType.methodType(boolean.class, String.class, CharSequence.class));
        } catch (ReflectiveOperationException | LinkageError | SecurityException ignored) {
            return null;
        }
    }
}
