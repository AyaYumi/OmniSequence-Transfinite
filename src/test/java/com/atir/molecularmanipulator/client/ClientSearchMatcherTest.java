package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientSearchMatcherTest {
    private boolean previousLookup;
    private MethodHandle previousHandle;
    private static final List<String> jecCalls = new ArrayList<>();
    private static Thread matchingThread;

    @BeforeEach void useDeterministicFallback() throws Exception {
        previousLookup = (boolean) field("jecLookupComplete").get(null);
        previousHandle = (MethodHandle) field("jecContains").get(null);
        field("jecLookupComplete").set(null, true);
        field("jecContains").set(null, null);
        jecCalls.clear();
        matchingThread = null;
    }

    @AfterEach void restoreOptionalMatcher() throws Exception {
        field("jecContains").set(null, previousHandle);
        field("jecLookupComplete").set(null, previousLookup);
    }

    @Test void compiledQueriesKeepAllTokenAndAcrossCandidateSemantics() {
        var values = Arrays.<CharSequence>asList("Copper Ingot", null, "example:polished_stone", "铜锭");
        var snapshot = ClientSearchMatcher.snapshot(values);
        for (String query : new String[]{"copper", "  COPPER\tINGOT \n", "copper stone", "example: 铜",
                "polished_stone", "stone copper missing", "铜锭", "锭", "", "\t\n", "\u2003", null}) {
            boolean expected = reference(values, query);
            assertEquals(expected, ClientSearchMatcher.compile(query).matches(snapshot), String.valueOf(query));
            assertEquals(expected, ClientSearchMatcher.matchesTokens(values, query), String.valueOf(query));
        }
        assertFalse(ClientSearchMatcher.compile("copper stone").matches("Copper Ingot"));
        assertTrue(ClientSearchMatcher.compile("copper ingot").matches("Copper Ingot"));
        assertFalse(ClientSearchMatcher.compile("copper").matches((CharSequence) null));
        assertFalse(ClientSearchMatcher.compile("copper").matches((Iterable<CharSequence>) null));
    }

    @Test void blankQueriesDoNotReadCandidatesAndEmptySnapshotsDoNotMatchNonblankQueries() {
        Iterable<CharSequence> unreadable = () -> { throw new AssertionError("Blank queries must not enumerate candidates"); };
        assertTrue(ClientSearchMatcher.compile(null).matches(unreadable));
        assertTrue(ClientSearchMatcher.matchesTokens(unreadable, " \n\t"));
        assertTrue(ClientSearchMatcher.matchesTokens((CharSequence) null, ""));
        assertTrue(ClientSearchMatcher.compile(" ").matches((ClientSearchMatcher.Candidates) null));
        assertFalse(ClientSearchMatcher.compile("iron").matches(ClientSearchMatcher.snapshot(List.of())));
        assertFalse(ClientSearchMatcher.contains(null, "iron"));
        assertFalse(ClientSearchMatcher.contains("iron", null));
    }

    @Test void snapshotCopiesMutableSequencesAndEnumeratesThemOnlyOnce() {
        var text = new StringBuilder("Copper Ingot");
        int[] enumerations = {0};
        Iterable<CharSequence> values = () -> {
            enumerations[0]++;
            return List.<CharSequence>of(text).iterator();
        };
        var snapshot = ClientSearchMatcher.snapshot(values);
        text.replace(0, text.length(), "Gold Dust");
        for (int i = 0; i < 20; i++) {
            assertTrue(ClientSearchMatcher.compile("copper ingot").matches(snapshot));
            assertFalse(ClientSearchMatcher.compile("gold").matches(snapshot));
        }
        assertEquals(1, enumerations[0]);
    }

    @Test void fallbackFoldingDoesNotDependOnTheDefaultLocale() {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertTrue(ClientSearchMatcher.compile("IRON INGOT")
                    .matches(ClientSearchMatcher.snapshot(List.of("Iron Ingot"))));
            assertTrue(ClientSearchMatcher.contains("IRON", "iron"));
        } finally { Locale.setDefault(previous); }
    }

    @Test void optionalMatcherReceivesOriginalStringsAndRunsOnTheCallingThread() throws Exception {
        install("pinyin");
        var query = ClientSearchMatcher.compile("Tong");
        assertTrue(query.matches(ClientSearchMatcher.snapshot(List.of("铜"))));
        assertEquals(List.of("铜|Tong"), jecCalls);
        assertSame(Thread.currentThread(), matchingThread);
        assertFalse(ClientSearchMatcher.compile("iron").matches(ClientSearchMatcher.snapshot(List.of("IRON"))),
                "A false JEC answer must not silently become a fallback match");
    }

    @Test void brokenOptionalMatcherDisablesItselfAndUsesPreFoldedFallback() throws Exception {
        install("brokenLinkage");
        assertTrue(ClientSearchMatcher.compile("iron").matches(ClientSearchMatcher.snapshot(List.of("IRON"))));
        assertNull(field("jecContains").get(null));
        assertTrue(ClientSearchMatcher.contains("COPPER", "copper"));
    }

    @Test void fatalOptionalMatcherErrorsAreNotSwallowed() throws Exception {
        install("fatal");
        assertThrows(AssertionError.class, () -> ClientSearchMatcher.compile("iron")
                .matches(ClientSearchMatcher.snapshot(List.of("IRON"))));
    }

    private static boolean reference(List<? extends CharSequence> candidates, String query) {
        if (query == null || query.isBlank()) return true;
        for (var token : query.strip().split("\\s+")) {
            boolean found = false;
            for (var candidate : candidates) {
                if (candidate != null && candidate.toString().toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT))) {
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    private static java.lang.reflect.Field field(String name) throws Exception {
        var field = ClientSearchMatcher.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void install(String name) throws Exception {
        field("jecContains").set(null, MethodHandles.lookup().findStatic(ClientSearchMatcherTest.class, name,
                MethodType.methodType(boolean.class, String.class, CharSequence.class)));
    }

    private static boolean pinyin(String candidate, CharSequence query) {
        matchingThread = Thread.currentThread();
        jecCalls.add(candidate + "|" + query);
        return candidate.equals("铜") && query.toString().equals("Tong");
    }
    private static boolean brokenLinkage(String candidate, CharSequence query) { throw new NoClassDefFoundError("optional JEC"); }
    private static boolean fatal(String candidate, CharSequence query) { throw new AssertionError("fatal matcher failure"); }
}
