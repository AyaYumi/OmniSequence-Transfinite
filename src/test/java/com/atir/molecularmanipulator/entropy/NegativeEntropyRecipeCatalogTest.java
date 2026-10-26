package com.atir.molecularmanipulator.entropy;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;
import appeng.menu.guisync.DataSynchronization;
import appeng.menu.guisync.GuiSync;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.entropy.NegativeEntropyRecipeCatalog.Entry;
import com.atir.molecularmanipulator.entropy.NegativeEntropyRecipeCatalog.ServerCache;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.core.NonNullList;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.RegistryBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Real recipe extraction and registry-aware packet round trips, without launching a client. */
public class NegativeEntropyRecipeCatalogTest {
    private static RegistryAccess registries;

    @BeforeAll
    static void bootstrap() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        // Emulate registries negotiated by NeoForge on a connection, without mutating vanilla globals.
        registries = new RegistryAccess.ImmutableRegistryAccess(List.of(
                syncedCopy(BuiltInRegistries.ITEM), syncedCopy(BuiltInRegistries.FLUID),
                syncedCopy(BuiltInRegistries.DATA_COMPONENT_TYPE)));
        // Unit tests do not run AE2's registration events. Register its real built-in key codecs locally.
        try {
            AEKeyTypesInternal.getRegistry();
        } catch (IllegalStateException notInitialized) {
            var types = new RegistryBuilder<>(AEKeyType.REGISTRY_KEY).create();
            AEKeyTypesInternal.setRegistry(types);
            Registry.register(types, AEKeyType.items().getId(), AEKeyType.items());
            Registry.register(types, AEKeyType.fluids().getId(), AEKeyType.fluids());
            types.freeze();
        }
    }

    @Test
    void allFiveNodesUseActualServerOutputsWithComponentsAmountsAndOrder() {
        var manager = new ReloadableRecipes();
        var named = new ItemStack(Items.DIAMOND, 2);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Server-only product"));
        var recipe = matter(List.of(named, new ItemStack(Items.GOLD_INGOT, 3)),
                new FluidStack(Fluids.WATER, 1250), List.of(
                        new GenericStack(AEFluidKey.of(Fluids.LAVA), 2500),
                        new GenericStack(AEItemKey.of(Items.EMERALD), 4_000_000_000L)));
        for (var node : NegativeEntropyRecipes.NODES) for (var id : node.recipeIds()) manager.put(id, recipe);
        var catalog = new ServerCache().get(manager, registries, 0);
        assertEquals(5, catalog.nodes().size());
        for (var node : NegativeEntropyRecipes.NODES) {
            var entries = catalog.recipes(node.index());
            assertEquals(node.recipeIds(), entries.stream().map(Entry::id).toList());
            assertTrue(entries.size() <= 8);
            for (var entry : entries) {
                assertTrue(entry.present());
                assertEquals(List.of(new GenericStack(AEItemKey.of(named), 2),
                        new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 3),
                        new GenericStack(AEFluidKey.of(Fluids.WATER), 1250),
                        new GenericStack(AEFluidKey.of(Fluids.LAVA), 2500),
                        new GenericStack(AEItemKey.of(Items.EMERALD), 4_000_000_000L)), entry.outputs());
            }
        }
        assertEquals(catalog, roundTrip(catalog));
    }

    @Test
    void supportsAllThirteenOutputsAndFluidOnlyOrAeOnlyRecipes() {
        var items = java.util.Collections.nCopies(6, new ItemStack(Items.DIAMOND));
        var ae = java.util.Collections.nCopies(6, new GenericStack(AEItemKey.of(Items.EMERALD), 3));
        var manager = new ReloadableRecipes();
        var ids = NegativeEntropyRecipes.recipeIds(0);
        manager.put(ids.get(0), matter(items, new FluidStack(Fluids.WATER, 1000), ae));
        manager.put(ids.get(1), matter(List.of(), new FluidStack(Fluids.WATER, 750), List.of()));
        manager.put(ids.get(2), matter(List.of(), FluidStack.EMPTY, ae));
        var catalog = new ServerCache().get(manager, registries, 0);
        assertEquals(13, catalog.recipes(0).get(0).outputs().size());
        assertEquals(List.of(new GenericStack(AEFluidKey.of(Fluids.WATER), 750)), catalog.recipes(0).get(1).outputs());
        assertEquals(ae, catalog.recipes(0).get(2).outputs());
        assertEquals(catalog, roundTrip(catalog));
    }

    @Test
    void absentRecipesAreReportedNotReplacedBySameNamedItems() {
        var catalog = new ServerCache().get(new ReloadableRecipes(), registries, 0);
        for (var node : NegativeEntropyRecipes.NODES) {
            assertEquals(node.recipeIds(), catalog.recipes(node.index()).stream().map(Entry::id).toList());
            for (var entry : catalog.recipes(node.index())) {
                assertFalse(entry.present());
                assertTrue(entry.outputs().isEmpty());
            }
        }
        assertEquals(catalog, roundTrip(catalog));
    }

    @Test
    void sameManagerReloadUpdatesChangedAndRemovedRecipesWithinTenTicksWithoutRepackingEachTick() {
        var manager = new ReloadableRecipes();
        var id = NegativeEntropyRecipes.recipeIds(4).getFirst();
        manager.put(id, matter(List.of(new ItemStack(Items.DIAMOND, 2)), FluidStack.EMPTY, List.of()));
        var cache = new ServerCache();
        var initial = cache.get(manager, registries, 0);
        int lookups = manager.lookups;
        manager.put(id, matter(List.of(new ItemStack(Items.GOLD_INGOT, 7)), FluidStack.EMPTY, List.of()));
        for (int tick = 1; tick < ServerCache.CHECK_INTERVAL_TICKS; tick++) {
            assertSame(initial, cache.get(manager, registries, tick));
            assertEquals(lookups, manager.lookups, "No recipe lookup or output packing between checks");
        }
        var reloaded = cache.get(manager, registries, 10);
        assertNotEquals(initial, reloaded);
        assertEquals(List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 7)), reloaded.recipes(4).getFirst().outputs());
        assertSame(reloaded, cache.get(manager, registries, 20), "Unchanged holders reuse the complete snapshot");
        manager.recipes.clear();
        var removed = cache.get(manager, registries, 30).recipes(4).getFirst();
        assertFalse(removed.present());
        assertTrue(removed.outputs().isEmpty());
        manager.put(id, matter(List.of(new ItemStack(Items.IRON_INGOT)), FluidStack.EMPTY, List.of()));
        assertTrue(cache.get(manager, registries, 40).recipes(4).getFirst().present());
        assertFalse(initial.recipes(4).getFirst().outputs().isEmpty(), "Published snapshots remain immutable");
    }

    @Test
    void changingRecipeManagerBypassesThePollingDelay() {
        var cache = new ServerCache();
        var initial = cache.get(new ReloadableRecipes(), registries, 0);
        var replacement = new ReloadableRecipes();
        replacement.put(NegativeEntropyRecipes.recipeIds(1).getFirst(),
                matter(List.of(new ItemStack(Items.DIAMOND)), FluidStack.EMPTY, List.of()));
        assertNotEquals(initial, cache.get(replacement, registries, 1));
    }

    @Test
    void ordinaryReplacementRecipesUseTheirRealResultAndEmptyResultsRemainPresent() {
        var manager = new RecipeManager(registries);
        var ids = NegativeEntropyRecipes.recipeIds(0);
        var ingredient = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE));
        manager.replaceRecipes(List.of(
                new RecipeHolder<>(ids.get(0), new ShapelessRecipe("", CraftingBookCategory.MISC,
                        new ItemStack(Items.DIAMOND, 4), ingredient)),
                new RecipeHolder<>(ids.get(1), new ShapelessRecipe("", CraftingBookCategory.MISC,
                        ItemStack.EMPTY, ingredient))));
        var cache = new ServerCache();
        var catalog = cache.get(manager, registries, 0);
        assertEquals(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 4)), catalog.recipes(0).getFirst().outputs());
        assertTrue(catalog.recipes(0).get(1).present());
        assertTrue(catalog.recipes(0).get(1).outputs().isEmpty());
        manager.replaceRecipes(List.of(new RecipeHolder<>(ids.get(0), new ShapelessRecipe("", CraftingBookCategory.MISC,
                new ItemStack(Items.GOLD_INGOT, 9), ingredient))));
        var reloaded = cache.get(manager, registries, 10);
        assertEquals(List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 9)), reloaded.recipes(0).getFirst().outputs());
        assertFalse(reloaded.recipes(0).get(1).present());
    }

    @Test
    void ae2GuiSyncCustomRecordUsesRegistryAwareConstructorAndOnlySendsChanges() {
        var manager = new ReloadableRecipes();
        var id = NegativeEntropyRecipes.recipeIds(0).getFirst();
        manager.put(id, matter(List.of(new ItemStack(Items.DIAMOND, 3)), FluidStack.EMPTY, List.of()));
        var cache = new ServerCache();
        var server = new SyncedView();
        var client = new SyncedView();
        server.catalog = cache.get(manager, registries, 0);
        var outgoing = new DataSynchronization(server);
        var incoming = new DataSynchronization(client);
        assertTrue(outgoing.hasChanges());
        var buffer = buffer();
        try {
            outgoing.writeFull(buffer);
            var changed = new ShortOpenHashSet();
            incoming.readUpdate(buffer, changed);
            assertTrue(changed.contains((short) 14));
            assertEquals(server.catalog, client.catalog);
            assertFalse(outgoing.hasChanges());
            server.catalog = cache.get(manager, registries, 10);
            assertFalse(outgoing.hasChanges(), "Unchanged catalog does not produce a GuiSync packet");
            manager.recipes.clear();
            server.catalog = cache.get(manager, registries, 20);
            assertTrue(outgoing.hasChanges());
            // AE2 owns the packet envelope/trailer; each update has its own packet buffer.
            buffer.clear();
            outgoing.writeUpdate(buffer);
            incoming.readUpdate(buffer, changed);
            assertEquals(server.catalog, client.catalog);
            assertFalse(client.catalog.recipes(0).getFirst().present());
        } finally { buffer.release(); }
    }

    public static final class SyncedView {
        @GuiSync(14) public NegativeEntropyRecipeCatalog catalog = NegativeEntropyRecipeCatalog.EMPTY;
    }

    @Test
    void packetRejectsInvalidNodeRecipeAndOutputBoundsBeforeAllocatingLists() {
        for (int size : new int[] {-1, 0, 4, 6, Integer.MAX_VALUE}) {
            var buffer = buffer();
            try {
                buffer.writeVarInt(size);
                assertThrows(IllegalArgumentException.class, () -> new NegativeEntropyRecipeCatalog(buffer));
            } finally { buffer.release(); }
        }
        for (int size : new int[] {-1, 9, Integer.MAX_VALUE}) {
            var buffer = buffer();
            try {
                buffer.writeVarInt(5); buffer.writeVarInt(size);
                assertThrows(IllegalArgumentException.class, () -> new NegativeEntropyRecipeCatalog(buffer));
            } finally { buffer.release(); }
        }
        for (int size : new int[] {-1, 14, Integer.MAX_VALUE}) {
            var buffer = buffer();
            try {
                buffer.writeVarInt(5); buffer.writeVarInt(1);
                buffer.writeResourceLocation(NegativeEntropyRecipes.recipeIds(0).getFirst());
                buffer.writeBoolean(true); buffer.writeVarInt(size);
                assertThrows(IllegalArgumentException.class, () -> new NegativeEntropyRecipeCatalog(buffer));
            } finally { buffer.release(); }
        }
    }

    @Test
    void snapshotsAreDeeplyImmutableAndMissingEntriesCannotHaveInventedOutputs() {
        var entries = new ArrayList<Entry>();
        var id = NegativeEntropyRecipes.recipeIds(0).getFirst();
        var entry = new Entry(id, false, List.of());
        entries.add(entry);
        var nodes = new ArrayList<>(java.util.Collections.<List<Entry>>nCopies(5, List.of()));
        nodes.set(0, entries);
        var catalog = new NegativeEntropyRecipeCatalog(nodes);
        entries.clear(); nodes.clear();
        assertEquals(List.of(entry), catalog.recipes(0));
        assertThrows(UnsupportedOperationException.class, () -> catalog.recipes(0).clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.nodes().clear());
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        assertThrows(IllegalArgumentException.class, () -> new Entry(id, false, List.of(output)));
        assertThrows(IllegalArgumentException.class, () -> new Entry(id, true,
                java.util.Collections.nCopies(14, output)));
        assertThrows(IllegalArgumentException.class, () -> new Entry(id, true,
                List.of(new GenericStack(output.what(), 0))));
        assertEquals(NegativeEntropyRecipeCatalog.EMPTY, roundTrip(NegativeEntropyRecipeCatalog.EMPTY));
    }

    private static MatterFabricationRecipe matter(List<ItemStack> items, FluidStack fluid, List<GenericStack> ae) {
        return new MatterFabricationRecipe(List.of(new MatterFabricationRecipe.CountedIngredient(
                Ingredient.of(Items.STONE), 1)), items, FluidStack.EMPTY, fluid, List.of(), ae, 400, 1, true);
    }

    private static <T> Registry<T> syncedCopy(Registry<T> original) {
        var copy = new RegistryBuilder<>(original.key()).sync(true).create();
        original.forEach(value -> Registry.register(copy, original.getKey(value), value));
        return copy.freeze();
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
    }

    private static NegativeEntropyRecipeCatalog roundTrip(NegativeEntropyRecipeCatalog catalog) {
        var buffer = buffer();
        try {
            catalog.writeToPacket(buffer);
            var decoded = new NegativeEntropyRecipeCatalog(buffer);
            assertEquals(0, buffer.readableBytes());
            return decoded;
        } finally { buffer.release(); }
    }

    /** Only replaces the manager's lookup table; the tested recipes and AE stack codecs are real. */
    private static final class ReloadableRecipes extends RecipeManager {
        final Map<ResourceLocation, RecipeHolder<?>> recipes = new HashMap<>();
        int lookups;
        ReloadableRecipes() { super(registries); }
        void put(ResourceLocation id, MatterFabricationRecipe recipe) {
            recipes.put(id, new RecipeHolder<>(id, recipe));
        }
        @Override public Optional<RecipeHolder<?>> byKey(ResourceLocation id) {
            lookups++;
            return Optional.ofNullable(recipes.get(id));
        }
    }
}
