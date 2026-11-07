package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SingularityCollectionResourcesTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final TagKey<Item> FIRST = tag("first");
    private static final TagKey<Item> SECOND = tag("second");
    private final RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private Map<TagKey<Item>, List<Holder<Item>>> savedTags;

    @BeforeEach void bindFixtureTags() {
        savedTags = new HashMap<>();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> savedTags.put(pair.getFirst(), pair.getSecond().stream().toList()));
        var tags = new HashMap<>(savedTags);
        tags.put(FIRST, holders(Items.IRON_INGOT, Items.GOLD_INGOT));
        tags.put(SECOND, holders(Items.GOLD_INGOT, Items.DIAMOND));
        BuiltInRegistries.ITEM.bindTags(tags);
        serverTagsUpdated();
    }

    @AfterEach void restoreTags() {
        savedTags.putIfAbsent(FIRST, List.of());
        savedTags.putIfAbsent(SECOND, List.of());
        BuiltInRegistries.ITEM.bindTags(savedTags);
        serverTagsUpdated();
    }

    @Test void reusesTheAuthoritativeSnapshotWithoutLookingUpTagMembersOnOrdinaryReads() {
        var access = new CountingAccess(registries);
        var cache = new SingularityCollectionResources();
        var configured = new ArrayList<>(List.of("#" + FIRST.location()));
        var blacklist = new ArrayList<String>();
        var first = cache.get(access, configured, blacklist);
        assertEquals(List.of(Items.IRON_INGOT, Items.GOLD_INGOT), first);
        int lookups = access.lookups;
        assertTrue(lookups > 0);
        for (int read = 0; read < 1_000; read++) {
            assertSame(first, cache.get(access, new ArrayList<>(configured), new ArrayList<>(blacklist)));
        }
        assertEquals(lookups, access.lookups, "Menu refreshes and production share a cache, not repeated tag scans");
        assertThrows(UnsupportedOperationException.class, () -> first.clear());

        configured.add(SECOND.location().toString());
        var union = cache.get(access, configured, blacklist);
        assertNotSame(first, union);
        assertEquals(List.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND), union);
        blacklist.add(" minecraft:gold_ingot ");
        var excluded = cache.get(access, configured, blacklist);
        assertEquals(List.of(Items.IRON_INGOT, Items.DIAMOND), excluded);
        assertEquals(List.of(Items.IRON_INGOT, Items.GOLD_INGOT), first, "Published snapshots cannot mutate in place");
        configured.clear();
        assertTrue(cache.get(access, configured, blacklist).isEmpty());
    }

    @Test void onlyServerTagGenerationsInvalidateAndRegistryReplacementRebuildsOnce() {
        var access = new CountingAccess(registries);
        var cache = new SingularityCollectionResources();
        var configured = List.of(FIRST.location().toString());
        var first = cache.get(access, configured, List.of());
        int lookups = access.lookups;
        var rebound = new HashMap<>(savedTags);
        rebound.put(FIRST, holders(Items.EMERALD));
        rebound.put(SECOND, holders(Items.DIAMOND));
        BuiltInRegistries.ITEM.bindTags(rebound);

        SingularityCollectionResources.onTagsUpdated(new TagsUpdatedEvent(registries, true, true));
        assertSame(first, cache.get(access, configured, List.of()), "Integrated-client notification is not a server reload");
        assertEquals(lookups, access.lookups);
        serverTagsUpdated();
        var changed = cache.get(access, configured, List.of());
        assertNotSame(first, changed);
        assertEquals(List.of(Items.EMERALD), changed);
        int afterReload = access.lookups;
        for (int read = 0; read < 1_000; read++) assertSame(changed, cache.get(access, configured, List.of()));
        assertEquals(afterReload, access.lookups);

        var replacement = new CountingAccess(registries);
        var replaced = cache.get(replacement, configured, List.of());
        assertEquals(changed, replaced);
        assertTrue(replacement.lookups > 0, "Registry identity is part of the cache key");
        assertSame(replaced, cache.get(replacement, configured, List.of()));
    }

    @Test void tagUnionAndBlacklistKeepOrderAndTolerateMalformedConfiguration() {
        var cache = new SingularityCollectionResources();
        var configured = Arrays.asList(null, "INVALID ID", " #" + FIRST.location() + " ",
                SECOND.location().toString(), FIRST.location().toString(), "test:missing");
        var blacklist = Arrays.asList(null, "INVALID ID", " minecraft:gold_ingot ", "test:missing");
        assertEquals(List.of(Items.IRON_INGOT, Items.DIAMOND), cache.get(registries, configured, blacklist));
        assertTrue(cache.get(registries, List.of("test:missing"), List.of()).isEmpty());
    }

    private void serverTagsUpdated() {
        SingularityCollectionResources.onTagsUpdated(new TagsUpdatedEvent(registries, false, false));
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("molecularmanipulator", "collection_cache_test/" + path));
    }

    private static List<Holder<Item>> holders(Item... items) {
        return Arrays.stream(items).<Holder<Item>>map(Item::builtInRegistryHolder).toList();
    }

    private static final class CountingAccess implements RegistryAccess {
        private final RegistryAccess delegate;
        int lookups;
        CountingAccess(RegistryAccess delegate) { this.delegate = delegate; }
        @Override public <E> Optional<Registry<E>> registry(ResourceKey<? extends Registry<? extends E>> key) {
            lookups++;
            return delegate.registry(key);
        }
        @Override public Stream<RegistryEntry<?>> registries() { return delegate.registries(); }
    }
}
