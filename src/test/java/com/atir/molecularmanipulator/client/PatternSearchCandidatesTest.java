package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.atir.molecularmanipulator.network.PatternSearchIndexChunk;
import com.atir.molecularmanipulator.network.PatternSearchIndexEntry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PatternSearchCandidatesTest {
    @BeforeAll static void bootstrap() throws Exception { Class.forName(UiRenderRecorder.class.getName()); }

    @Test void repeatedQueriesReuseCandidateStringsAndResolveSharedKeysOnlyOnce() {
        var copper = new CountingKey("test:copper_ingot", "Copper Ingot");
        var stone = new CountingKey("other:polished_stone", "Polished Stone");
        var index = new PatternSearchIndexState();
        index.accept(chunk(1, 0, true, true, List.of(
                new PatternSearchIndexEntry(70, List.of(stone, copper), List.of(copper)),
                new PatternSearchIndexEntry(4, List.of(copper), List.of(stone)))));
        var cache = new PatternSearchCandidates();
        var language = new Object();
        var snapshot = cache.snapshot(index, language);
        assertEquals(1, copper.idReads);
        assertEquals(1, stone.idReads);
        assertEquals(List.of(70, 4), slots(snapshot, "copper polished"), "Keep index order, not numeric slot order");
        assertEquals(List.of(70, 4), slots(snapshot, "test:copper_ingot"));
        assertEquals(List.of(), slots(snapshot, "not_present"));
        for (int i = 0; i < 20; i++) {
            assertSame(snapshot, cache.snapshot(index, language));
            slots(snapshot, "copper stone");
        }
        assertEquals(1, copper.idReads, "Matching must not revisit AEKey display/ID methods");
        assertEquals(1, stone.idReads);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
    }

    @Test void indexChunksLanguageReloadAndScreenRemovalInvalidateOnlyTheLocalSnapshot() {
        var key = new CountingKey("test:input", "Localized Input");
        var index = new PatternSearchIndexState();
        var entry = new PatternSearchIndexEntry(2, List.of(key), List.of());
        index.accept(chunk(3, 0, true, true, List.of(entry)));
        var cache = new PatternSearchCandidates();
        var language = new Object();
        var initial = cache.snapshot(index, language);
        assertTrue(cache.isCurrent(index, language));
        var changedLanguage = new Object();
        assertFalse(cache.isCurrent(index, changedLanguage));
        var reloaded = cache.snapshot(index, changedLanguage);
        assertNotSame(initial, reloaded);
        assertEquals(2, key.idReads);
        var translated = cache.snapshot(index, new Object());
        assertNotSame(reloaded, translated);
        assertEquals(3, key.idReads);

        var activeLanguage = new Object();
        var beforeReset = cache.snapshot(index, activeLanguage);
        assertFalse(index.accept(chunk(2, 0, true, true, List.of())), "Stale snapshot remains ignored");
        assertSame(beforeReset, cache.snapshot(index, activeLanguage));
        assertTrue(index.accept(chunk(4, 0, true, false, List.of(entry))));
        assertFalse(cache.isCurrent(index, activeLanguage));
        assertThrows(IllegalStateException.class, () -> cache.snapshot(index, activeLanguage),
                "Partial index data must not replace a complete candidate snapshot");
        assertTrue(index.accept(chunk(4, 1, false, true, List.of(new PatternSearchIndexEntry(9, List.of(), List.of(key))))));
        var completed = cache.snapshot(index, activeLanguage);
        assertEquals(List.of(2, 9), slots(completed, "input"));
        assertNotSame(beforeReset, completed);
        assertFalse(index.accept(chunk(4, 1, false, true, List.of())), "Duplicate chunks do not invalidate the cache");
        assertSame(completed, cache.snapshot(index, activeLanguage));

        cache.clear();
        assertFalse(cache.isCurrent(index, activeLanguage));
        assertTrue(index.complete(), "Removing a screen must not mutate menu/index lifecycle");
        assertNotSame(completed, cache.snapshot(index, activeLanguage));
        index.clear();
        assertFalse(cache.isCurrent(index, activeLanguage));
    }

    @Test void sourceIndexAndContainerIdentityCannotAliasAnotherMenusSnapshot() {
        var cache = new PatternSearchCandidates();
        var first = new PatternSearchIndexState();
        first.accept(chunk(0, 0, true, true, List.of(new PatternSearchIndexEntry(7, List.of(), List.of()))));
        var language = new Object();
        var initial = cache.snapshot(first, language);
        var second = new PatternSearchIndexState();
        second.accept(chunk(0, 0, true, true, List.of(new PatternSearchIndexEntry(8, List.of(), List.of()))));
        assertFalse(cache.isCurrent(second, language));
        assertNotSame(initial, cache.snapshot(second, language));
        second.accept(new PatternSearchIndexChunk(23, 0, 0, true, true, List.of()));
        assertFalse(cache.isCurrent(second, language));
        assertTrue(cache.snapshot(second, language).isEmpty());
    }

    @Test void minecraftResourceReloadReplacesTheLanguageIdentityUsedForInvalidation() {
        var previous = net.minecraft.locale.Language.getInstance();
        var index = new PatternSearchIndexState();
        index.accept(chunk(0, 0, true, true, List.of(new PatternSearchIndexEntry(1, List.of(), List.of()))));
        var cache = new PatternSearchCandidates();
        try (var resources = new net.minecraft.server.packs.resources.MultiPackResourceManager(
                net.minecraft.server.packs.PackType.CLIENT_RESOURCES, List.of())) {
            var manager = new net.minecraft.client.resources.language.LanguageManager("en_us", language -> {});
            manager.onResourceManagerReload(resources);
            var language = net.minecraft.locale.Language.getInstance();
            var before = cache.snapshot(index, language);
            manager.onResourceManagerReload(resources);
            var reloaded = net.minecraft.locale.Language.getInstance();
            assertNotSame(language, reloaded, "Even a same-language pack reload installs a new Language");
            assertFalse(cache.isCurrent(index, reloaded));
            assertNotSame(before, cache.snapshot(index, reloaded));
        } finally { net.minecraft.locale.Language.inject(previous); }
    }

    private static PatternSearchIndexChunk chunk(long generation, int revision, boolean reset, boolean complete,
            List<PatternSearchIndexEntry> entries) {
        return new PatternSearchIndexChunk(19, generation, revision, reset, complete, entries);
    }

    private static List<Integer> slots(List<PatternSearchCandidates.Entry> candidates, String query) {
        var compiled = ClientSearchMatcher.compile(query);
        return candidates.stream().filter(entry -> compiled.matches(entry.candidates()))
                .map(PatternSearchCandidates.Entry::sourceSlot).toList();
    }

    /** Only string extraction is legal in these tests; codec/render operations would indicate leaked work. */
    private static final class CountingKey extends AEKey {
        private final ResourceLocation id;
        private final String name;
        int idReads;
        CountingKey(String id, String name) { this.id = ResourceLocation.parse(id); this.name = name; }
        @Override public ResourceLocation getId() { idReads++; return id; }
        @Override protected Component computeDisplayName() { return Component.literal(name); }
        @Override public Object getPrimaryKey() { return id; }
        @Override public boolean hasComponents() { return false; }
        @Override public AEKey dropSecondary() { return this; }
        @Override public AEKeyType getType() { throw new AssertionError("Not needed for text search"); }
        @Override public CompoundTag toTag(HolderLookup.Provider registries) { throw new AssertionError("No encoding"); }
        @Override public void writeToPacket(RegistryFriendlyByteBuf buffer) { throw new AssertionError("No packets"); }
        @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) { throw new AssertionError("No world access"); }
    }
}
