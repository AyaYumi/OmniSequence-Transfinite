package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.MolecularManipulator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/** One controller's authoritative collection snapshot, shared by production and its open menus. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class SingularityCollectionResources {
    private static final AtomicLong TAG_GENERATION = new AtomicLong();
    private RegistryAccess registries;
    private long tagGeneration = -1;
    private List<? extends String> tags = List.of();
    private List<? extends String> blacklist = List.of();
    private List<Item> items = List.of();

    public List<Item> get(RegistryAccess registries, List<? extends String> tags, List<? extends String> blacklist) {
        long generation = TAG_GENERATION.get();
        if (this.registries != registries || tagGeneration != generation
                || !this.tags.equals(tags) || !this.blacklist.equals(blacklist)) {
            var collected = new LinkedHashSet<Item>();
            for (var configured : tags) {
                if (configured == null) continue;
                String text = configured.trim();
                if (text.startsWith("#")) text = text.substring(1);
                var id = ResourceLocation.tryParse(text);
                if (id == null) continue;
                registries.lookupOrThrow(Registries.ITEM).get(TagKey.create(Registries.ITEM, id))
                        .ifPresent(holders -> holders.forEach(holder -> collected.add(holder.value())));
            }
            var excluded = new HashSet<ResourceLocation>();
            for (var configured : blacklist) {
                if (configured == null) continue;
                var id = ResourceLocation.tryParse(configured.trim());
                if (id != null) excluded.add(id);
            }
            collected.removeIf(item -> excluded.contains(BuiltInRegistries.ITEM.getKey(item)));
            items = List.copyOf(collected);
            this.registries = registries;
            tagGeneration = generation;
            // Copy only on a rebuild. Ordinary reads compare the small config lists, not tag members.
            this.tags = new java.util.ArrayList<>(tags);
            this.blacklist = new java.util.ArrayList<>(blacklist);
        }
        return items;
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        // The menu receives authoritative server snapshots; remote-client tag notifications
        // must not invalidate caches in an integrated server a second time.
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) TAG_GENERATION.incrementAndGet();
    }
}
