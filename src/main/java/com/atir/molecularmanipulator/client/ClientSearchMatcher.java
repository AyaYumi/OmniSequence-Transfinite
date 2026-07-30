package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.network.PatternSearchIndexEntry;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Client-only text matching with optional Just Enough Characters support.
 *
 * <p>JEC is deliberately accessed by name so common code never links against its client-only classes.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientSearchMatcher {
    private static final String JEC_MATCH_CLASS = "me.towdium.jecharacters.utils.Match";
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static volatile boolean jecLookupComplete;
    private static volatile MethodHandle jecContains;

    private ClientSearchMatcher() {
    }

    /**
     * Matches every whitespace-separated query token against the candidate.
     */
    public static boolean matchesTokens(CharSequence candidate, String query) {
        var tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return true;
        }
        if (candidate == null) {
            return false;
        }

        var text = candidate.toString();
        for (var token : tokens) {
            if (!contains(text, token)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Matches every query token against at least one candidate. Tokens may match different candidates.
     */
    public static boolean matchesTokens(Iterable<? extends CharSequence> candidates, String query) {
        var tokens = tokenize(query);
        if (tokens.isEmpty()) {
            return true;
        }
        if (candidates == null) {
            return false;
        }

        var values = new ArrayList<String>();
        for (var candidate : candidates) {
            if (candidate != null) {
                values.add(candidate.toString());
            }
        }
        for (var token : tokens) {
            boolean matched = false;
            for (var candidate : values) {
                if (contains(candidate, token)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    /**
     * Matches a decoded pattern using client-localized input/output names and
     * stable registry identifiers.
     */
    public static boolean matchesPattern(PatternSearchIndexEntry entry, String query) {
        var candidates = new ArrayList<CharSequence>((entry.inputs().size() + entry.outputs().size()) * 3);
        addKeyCandidates(candidates, entry.outputs());
        addKeyCandidates(candidates, entry.inputs());
        return matchesTokens(candidates, query);
    }

    /**
     * Uses JEC's pinyin-aware matcher when available, otherwise performs a locale-stable,
     * case-insensitive substring match.
     */
    public static boolean contains(String candidate, CharSequence query) {
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

        return candidate.toLowerCase(Locale.ROOT)
                .contains(query.toString().toLowerCase(Locale.ROOT));
    }

    private static List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return List.of(WHITESPACE.split(query.strip()));
    }

    private static void addKeyCandidates(List<CharSequence> candidates,
            Iterable<appeng.api.stacks.AEKey> keys) {
        for (var key : keys) {
            candidates.add(key.getDisplayName().getString());
            candidates.add(key.getId().toString());
            candidates.add(key.getId().getPath());
        }
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
