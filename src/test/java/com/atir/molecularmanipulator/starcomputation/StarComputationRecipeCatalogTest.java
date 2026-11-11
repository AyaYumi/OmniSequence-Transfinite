package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.GenericStack;
import appeng.menu.guisync.DataSynchronization;
import appeng.menu.guisync.GuiSync;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.MatterMachineImportRecipe;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.recipelock.RecipeLockRecipe;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.atir.molecularmanipulator.registry.ModContent;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.atir.molecularmanipulator.starcomputation.StarComputationRecipeCatalog.Entry;
import com.atir.molecularmanipulator.starcomputation.StarComputationRecipeCatalog.ServerCache;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.core.NonNullList;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.RegistryBuilder;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/** Real recipe extraction and registry-aware packet round trips, without launching a client. */
@Isolated("Runtime fixtures temporarily bind deferred recipe types/serializers without a mod event bus")
public class StarComputationRecipeCatalogTest {
    // The sole default research definition is first in the live directory, not at its permanent save-bit index.
    private static final int MATERIAL_NODE = 0;
    private static final ResourceLocation MATERIAL = ResourceLocation.parse("molecularmanipulator:material");
    private static final ResourceLocation CONTROLLER =
            ResourceLocation.parse("molecularmanipulator:matter_fabrication_controller");
    private static final ResourceLocation MANIPULATOR =
            ResourceLocation.parse("molecularmanipulator:molecular_manipulator");
    private static final ResourceLocation NEXUS =
            ResourceLocation.parse("molecularmanipulator:transfinite_compute_nexus");
    private static final List<ResourceLocation> DEFAULT_TARGETS = List.of(MANIPULATOR, NEXUS);
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
    void liveDirectoryResolvesAllMaterialOutputKindsInOrder() throws Exception {
        try (var fixture = new RuntimeFixture()) {
            var named = new ItemStack(Items.DIAMOND, 2);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Server-only product"));
            var recipe = matter(List.of(named, new ItemStack(Items.GOLD_INGOT, 3)),
                    new FluidStack(Fluids.WATER, 1250), List.of(
                            new GenericStack(AEFluidKey.of(Fluids.LAVA), 2500),
                            new GenericStack(AEItemKey.of(Items.EMERALD), 4_000_000_000L)));
            fixture.replaceMaterialTargets(new RecipeHolder<>(MANIPULATOR, recipe), new RecipeHolder<>(NEXUS, recipe));
            var directory = StarResearchNodes.catalog(fixture.level);
            assertEquals(List.of(MATERIAL), directory.nodes().keySet().stream().toList());
            assertEquals(MATERIAL_NODE, StarResearchMenuNodes.from(directory, Set.of()).indexOf(MATERIAL));
            var catalog = new ServerCache().get(fixture.level, directory, 0);
            assertEquals(1, catalog.nodes().size());
            assertEquals(2, catalog.nodes().stream().mapToInt(List::size).sum());
            var entries = catalog.recipes(MATERIAL_NODE);
            assertEquals(DEFAULT_TARGETS, entries.stream().map(Entry::id).toList());
            assertTrue(entries.size() <= StarComputationRecipeCatalog.MAX_RECIPES_PER_NODE);
            for (var entry : entries) {
                assertEquals(List.of(new RecipeLocks.ResearchRequirement(Set.of(MATERIAL), false, true)),
                        RecipeLocks.requirements(fixture.level, entry.id()));
                assertTrue(entry.present());
                assertEquals(List.of(new GenericStack(AEItemKey.of(named), 2),
                        new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 3),
                        new GenericStack(AEFluidKey.of(Fluids.WATER), 1250),
                        new GenericStack(AEFluidKey.of(Fluids.LAVA), 2500),
                        new GenericStack(AEItemKey.of(Items.EMERALD), 4_000_000_000L)), entry.outputs());
            }
            assertEquals(catalog, roundTrip(catalog));
        }
    }

    @Test
    void supportsAllThirteenOutputsAndFluidOnlyOrAeOnlyRecipes() throws Exception {
        try (var fixture = new RuntimeFixture()) {
            var items = java.util.Collections.nCopies(6, new ItemStack(Items.DIAMOND));
            var ae = java.util.Collections.nCopies(6, new GenericStack(AEItemKey.of(Items.EMERALD), 3));
            var cache = new ServerCache();
            fixture.replaceMaterialTargets(new RecipeHolder<>(MANIPULATOR, matter(items, new FluidStack(Fluids.WATER, 1000), ae)));
            var allOutputs = cache.get(fixture.level, 0);
            var expected = new ArrayList<>(java.util.Collections.nCopies(6,
                    new GenericStack(AEItemKey.of(Items.DIAMOND), 1)));
            expected.add(new GenericStack(AEFluidKey.of(Fluids.WATER), 1000));
            expected.addAll(ae);
            assertEquals(13, allOutputs.recipes(MATERIAL_NODE).getFirst().outputs().size());
            assertEquals(expected, allOutputs.recipes(MATERIAL_NODE).getFirst().outputs());
            assertEquals(allOutputs, roundTrip(allOutputs));

            fixture.replaceMaterialTargets(new RecipeHolder<>(MANIPULATOR,
                    matter(List.of(), new FluidStack(Fluids.WATER, 750), List.of())));
            var fluidOnly = cache.get(fixture.level, 1);
            assertEquals(List.of(new GenericStack(AEFluidKey.of(Fluids.WATER), 750)),
                    fluidOnly.recipes(MATERIAL_NODE).getFirst().outputs());
            assertEquals(fluidOnly, roundTrip(fluidOnly));

            fixture.replaceMaterialTargets(new RecipeHolder<>(MANIPULATOR, matter(List.of(), FluidStack.EMPTY, ae)));
            var aeOnly = cache.get(fixture.level, 2);
            assertEquals(ae, aeOnly.recipes(MATERIAL_NODE).getFirst().outputs());
            assertEquals(aeOnly, roundTrip(aeOnly));
            assertEquals(expected, allOutputs.recipes(MATERIAL_NODE).getFirst().outputs(),
                    "Replacing the holder must not mutate an already published multi-output snapshot");
        }
    }

    @Test
    void packetPreservesMultipleEntriesAndTheirOrderInEveryNodeWithoutDefaultRecipeIds() {
        var nodes = new ArrayList<List<Entry>>();
        int nodeCount = 97;
        for (int node = 0; node < nodeCount; node++) {
            var entries = new ArrayList<Entry>();
            for (int index = 0; index < StarComputationRecipeCatalog.MAX_RECIPES_PER_NODE; index++) {
                var id = ResourceLocation.parse("test:node_" + node + "/recipe_" + index);
                boolean present = index != StarComputationRecipeCatalog.MAX_RECIPES_PER_NODE - 1;
                entries.add(new Entry(id, present, present
                        ? List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), (node + 1L) * (index + 1)))
                        : List.of()));
            }
            nodes.add(entries);
        }
        var catalog = new StarComputationRecipeCatalog(nodes);
        var decoded = roundTrip(catalog);
        assertEquals(catalog, decoded);
        assertEquals(nodeCount, decoded.nodes().size());
        assertEquals(nodeCount * StarComputationRecipeCatalog.MAX_RECIPES_PER_NODE,
                decoded.nodes().stream().mapToInt(List::size).sum());
        assertEquals(ResourceLocation.parse("test:node_96/recipe_0"), decoded.recipes(96).getFirst().id());
    }

    @Test
    void absentRecipesAreReportedNotReplacedBySameNamedItems() throws Exception {
        try (var fixture = new RuntimeFixture()) {
            var sameNamedItem = BuiltInRegistries.ITEM.getKey(Items.DIAMOND);
            fixture.replaceMaterialTargets(exactLock("same_named_item", sameNamedItem, MATERIAL));
            assertTrue(BuiltInRegistries.ITEM.containsKey(sameNamedItem));
            assertTrue(fixture.manager.byKey(sameNamedItem).isEmpty());
            var catalog = new ServerCache().get(fixture.level, 0);
            assertEquals(1, catalog.nodes().size());
            assertEquals(List.of(sameNamedItem, MANIPULATOR, NEXUS).stream().sorted().toList(),
                    catalog.recipes(MATERIAL_NODE).stream().map(Entry::id).toList());
            for (var entry : catalog.recipes(MATERIAL_NODE)) {
                assertFalse(entry.present());
                assertTrue(entry.outputs().isEmpty());
            }
            assertEquals(catalog, roundTrip(catalog));
        }
    }

    @Test
    void sameManagerReloadUpdatesChangedAndRemovedRecipesImmediatelyWithoutRepackingEachTick() throws Exception {
        try (var fixture = new RuntimeFixture()) {
            fixture.replaceMaterialTargets(new RecipeHolder<>(MANIPULATOR,
                    matter(List.of(new ItemStack(Items.DIAMOND, 2)), FluidStack.EMPTY, List.of())));
            var cache = new ServerCache();
            var initial = cache.get(fixture.level, 0);
            var directory = StarResearchNodes.catalog(fixture.level);
            var index = MatterRecipeIndex.get(fixture.level);
            for (int tick = 1; tick <= 20; tick++) {
                assertSame(initial, cache.get(fixture.level, tick), "Unchanged definitions reuse the complete output snapshot");
                assertSame(directory, StarResearchNodes.catalog(fixture.level));
                assertSame(index, MatterRecipeIndex.get(fixture.level));
            }
            fixture.replaceMaterialTargets(new RecipeHolder<>(MANIPULATOR,
                    matter(List.of(new ItemStack(Items.GOLD_INGOT, 7)), FluidStack.EMPTY, List.of())));
            var reloaded = cache.get(fixture.level, 20);
            assertNotEquals(initial, reloaded, "Even a reload in the same tick must replace stale outputs");
            assertEquals(List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 7)),
                    reloaded.recipes(MATERIAL_NODE).getFirst().outputs());
            for (int tick = 21; tick <= 40; tick++) {
                assertSame(reloaded, cache.get(fixture.level, tick), "Unchanged holders reuse the complete snapshot");
            }
            fixture.replaceMaterialTargets();
            var removed = cache.get(fixture.level, 41).recipes(MATERIAL_NODE).getFirst();
            assertEquals(MANIPULATOR, removed.id());
            assertFalse(removed.present());
            assertTrue(removed.outputs().isEmpty());
            fixture.replaceMaterialTargets(new RecipeHolder<>(MANIPULATOR,
                    matter(List.of(new ItemStack(Items.IRON_INGOT)), FluidStack.EMPTY, List.of())));
            var restored = cache.get(fixture.level, 42).recipes(MATERIAL_NODE).getFirst();
            assertTrue(restored.present());
            assertEquals(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)), restored.outputs());
            assertEquals(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 2)),
                    initial.recipes(MATERIAL_NODE).getFirst().outputs(), "Published snapshots remain immutable");
        }
    }

    @Test
    void changingRecipeManagerImmediatelyRefreshesTheCatalog() throws Exception {
        try (var fixture = new RuntimeFixture()) {
            fixture.replaceMaterialTargets();
            var cache = new ServerCache();
            var initial = cache.get(fixture.level, 0);
            var replacement = new RecipeManager(registries);
            var holders = new ArrayList<>(fixture.manager.getRecipes());
            holders.add(new RecipeHolder<>(MANIPULATOR,
                    matter(List.of(new ItemStack(Items.DIAMOND)), FluidStack.EMPTY, List.of())));
            replacement.replaceRecipes(holders);
            var replacementLevel = StarResearchTestLevel.create(replacement, registries);
            var refreshed = cache.get(replacementLevel, 0);
            assertNotEquals(initial, refreshed);
            assertEquals(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)),
                    refreshed.recipes(MATERIAL_NODE).getFirst().outputs());
            assertSame(refreshed, cache.get(replacementLevel, 1));
            assertFalse(initial.recipes(MATERIAL_NODE).getFirst().present(), "The old manager's snapshot is independent");
        }
    }

    @Test
    void ordinaryCraftingResultsAndEmptyOrMissingHoldersDoNotUseUnlistedRecipes() throws Exception {
        try (var fixture = new RuntimeFixture()) {
            var unlisted = ResourceLocation.parse("test:unlisted_crafting_recipe");
            var ingredient = NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE));
            var unlistedHolder = new RecipeHolder<>(unlisted, new ShapelessRecipe("", CraftingBookCategory.MISC,
                    new ItemStack(Items.EMERALD, 5), ingredient));
            fixture.replaceMaterialTargets(unlistedHolder,
                    new RecipeHolder<>(MANIPULATOR, new ShapelessRecipe("", CraftingBookCategory.MISC,
                            new ItemStack(Items.DIAMOND, 4), ingredient)));
            var cache = new ServerCache();
            var catalog = cache.get(fixture.level, 0);
            assertEquals(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 4)),
                    catalog.recipes(MATERIAL_NODE).getFirst().outputs());
            assertEquals(2, catalog.nodes().stream().mapToInt(List::size).sum(),
                    "Only the two exact lock targets are displayed, including absent targets");
            assertEquals(new Entry(NEXUS, false, List.of()), catalog.recipes(MATERIAL_NODE).getLast());

            fixture.replaceMaterialTargets(unlistedHolder,
                    new RecipeHolder<>(MANIPULATOR, new ShapelessRecipe("", CraftingBookCategory.MISC,
                            ItemStack.EMPTY, ingredient)));
            var empty = cache.get(fixture.level, 1).recipes(MATERIAL_NODE).getFirst();
            assertTrue(empty.present());
            assertTrue(empty.outputs().isEmpty());
            fixture.replaceMaterialTargets(unlistedHolder);
            var missing = cache.get(fixture.level, 2).recipes(MATERIAL_NODE).getFirst();
            assertFalse(missing.present());
            assertTrue(missing.outputs().isEmpty(), "An unlisted holder cannot supply the missing target's output");

            fixture.replaceMaterialTargets(unlistedHolder,
                    new RecipeHolder<>(MANIPULATOR, new ShapelessRecipe("", CraftingBookCategory.MISC,
                            new ItemStack(Items.GOLD_INGOT, 9), ingredient)));
            var reloaded = cache.get(fixture.level, 3);
            assertEquals(List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 9)),
                    reloaded.recipes(MATERIAL_NODE).getFirst().outputs());
            assertEquals(reloaded, roundTrip(reloaded));
        }
    }

    @Test
    void ae2GuiSyncCustomRecordUsesRegistryAwareConstructorAndOnlySendsChanges() throws Exception {
        try (var fixture = new RuntimeFixture()) {
            var holder = new RecipeHolder<>(MANIPULATOR,
                    matter(List.of(new ItemStack(Items.DIAMOND, 3)), FluidStack.EMPTY, List.of()));
            fixture.replaceMaterialTargets(holder);
            var cache = new ServerCache();
            var server = new SyncedView();
            var client = new SyncedView();
            server.catalog = cache.get(fixture.level, 0);
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
                server.catalog = cache.get(fixture.level, 1);
                assertFalse(outgoing.hasChanges(), "Unchanged catalog does not produce a GuiSync packet");
                var beforeReload = server.catalog;
                fixture.replaceMaterialTargets(holder);
                server.catalog = cache.get(fixture.level, 2);
                assertNotSame(beforeReload, server.catalog);
                assertEquals(beforeReload, server.catalog);
                assertFalse(outgoing.hasChanges(), "Reloaded definitions with equal outputs are not retransmitted");
                fixture.replaceMaterialTargets();
                server.catalog = cache.get(fixture.level, 3);
                assertTrue(outgoing.hasChanges());
                // AE2 owns the packet envelope/trailer; each update has its own packet buffer.
                buffer.clear();
                outgoing.writeUpdate(buffer);
                incoming.readUpdate(buffer, changed);
                assertEquals(server.catalog, client.catalog);
                assertFalse(client.catalog.recipes(MATERIAL_NODE).getFirst().present());
            } finally { buffer.release(); }
        }
    }

    @Test
    void bundledDefaultRulePreviewsExactlyBothSingleBlocksAndLeavesAllThreeMultiblockControllersOpen() throws Exception {
        try (var fixture = new RuntimeFixture()) {
            var path = Path.of("src/main/resources/data/molecularmanipulator/recipe/recipe_locks/material.json");
            var rule = RecipeLockRecipe.CODEC.codec().parse(JsonOps.INSTANCE,
                    JsonParser.parseString(Files.readString(path))).getOrThrow();
            var holders = new ArrayList<RecipeHolder<?>>();
            holders.add(new RecipeHolder<>(ResourceLocation.parse("molecularmanipulator:recipe_locks/material"), rule));
            holders.add(crafting(MANIPULATOR, Items.DIAMOND, 2));
            holders.add(crafting(NEXUS, Items.EMERALD, 3));
            var open = List.of(CONTROLLER,
                    ResourceLocation.parse("molecularmanipulator:molecular_center_controller"),
                    ResourceLocation.parse("molecularmanipulator:omni_computation_controller"),
                    ResourceLocation.parse("molecularmanipulator:molecular_auto_crafter"),
                    ResourceLocation.parse("molecularmanipulator:quantum_star_position_anchor"),
                    ResourceLocation.parse("molecularmanipulator:star_computation_engine_core"),
                    ResourceLocation.parse("molecularmanipulator:molecular_manipulator_extra"),
                    ResourceLocation.parse("other:molecular_manipulator"),
                    ResourceLocation.parse("ae2:crafting_unit"),
                    ResourceLocation.parse("advanced_ae:quantum_core"));
            for (var target : open) holders.add(crafting(target, Items.IRON_INGOT, 1));
            fixture.replace(holders);
            var directory = StarResearchNodes.catalog(fixture.level);
            var menu = StarResearchMenuNodes.from(directory, Set.of());
            assertEquals(List.of(MATERIAL), menu.nodes().stream().map(StarResearchMenuNodes.Node::id).toList());
            var cache = new ServerCache();
            var catalog = cache.get(fixture.level, directory, 0);
            assertEquals(1, catalog.nodes().size());
            assertEquals(List.of(
                    new Entry(MANIPULATOR, true, List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 2))),
                    new Entry(NEXUS, true, List.of(new GenericStack(AEItemKey.of(Items.EMERALD), 3)))), catalog.recipes(0));
            assertTrue(RecipeLocks.hasRules(fixture.level));
            for (var target : DEFAULT_TARGETS) {
                assertEquals(List.of(new RecipeLocks.ResearchRequirement(Set.of(MATERIAL), false, true)),
                        RecipeLocks.requirements(fixture.level, target), target.toString());
            }
            for (var target : open) {
                assertTrue(RecipeLocks.requirements(fixture.level, target).isEmpty(), target.toString());
                assertTrue(RecipeLocks.allowed(fixture.level, target, null, null), target.toString());
            }
            assertEquals(catalog, roundTrip(catalog));

            // AdvancedAE may omit the conditional nexus holder; its exact lock must not broaden or disappear.
            holders.removeIf(holder -> holder.id().equals(NEXUS));
            fixture.replace(holders);
            var absentNexus = cache.get(fixture.level, 1);
            assertEquals(1, absentNexus.nodes().size());
            assertEquals(DEFAULT_TARGETS, absentNexus.recipes(0).stream().map(Entry::id).toList());
            assertEquals(catalog.recipes(0).getFirst(), absentNexus.recipes(0).getFirst());
            assertEquals(new Entry(NEXUS, false, List.of()), absentNexus.recipes(0).getLast());
            assertEquals(List.of(new RecipeLocks.ResearchRequirement(Set.of(MATERIAL), false, true)),
                    RecipeLocks.requirements(fixture.level, NEXUS));
            assertEquals(absentNexus, roundTrip(absentNexus));
        }
    }

    @Test
    void runtimeCatalogHasOnlyMaterialAtIndexZeroAndUsesActualLockDeclarationsNotLegacyDisplayTargets() throws Exception {
        try (var fixture = new RuntimeFixture()) {
            var target = ResourceLocation.parse("pack:not_a_builtin_display_target");
            var named = new ItemStack(Items.DIAMOND, 3);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Live lock output"));
            fixture.replace(List.of(new RecipeHolder<>(target, matter(List.of(named), FluidStack.EMPTY, List.of())),
                    exactLock("material", target, MATERIAL)));
            var directory = StarResearchNodes.catalog(fixture.level);
            var menu = StarResearchMenuNodes.from(directory, Set.of());
            var cache = new ServerCache();
            var catalog = cache.get(fixture.level, directory, 0);
            assertEquals(List.of(MATERIAL), menu.nodes().stream().map(StarResearchMenuNodes.Node::id).toList());
            assertEquals(1, catalog.nodes().size());
            assertEquals(List.of(new Entry(target, true, List.of(new GenericStack(AEItemKey.of(named), 3)))), catalog.recipes(0));
            assertFalse(catalog.recipes(0).stream().anyMatch(entry -> DEFAULT_TARGETS.contains(entry.id())),
                    "Absent static display targets cannot invent runtime preview entries");
            assertEquals(List.of(new RecipeLocks.ResearchRequirement(Set.of(MATERIAL), false, true)),
                    RecipeLocks.requirements(fixture.level, target));
            assertSame(catalog, cache.get(fixture.level, directory, 1));
            assertSame(catalog, cache.get(fixture.level, 2));
            assertEquals(catalog, roundTrip(catalog));
        }
    }

    @Test
    void runtimeCatalogHonorsSelectorIntersectionsExclusionsOverlappingModesAndMissingExactTargets() throws Exception {
        try (var fixture = new RuntimeFixture()) {
            var first = ResourceLocation.parse("pack:first_node");
            var second = ResourceLocation.parse("pack:second_node");
            var selected = ResourceLocation.parse("pack:selected");
            var excluded = ResourceLocation.parse("pack:excluded");
            var wrongType = ResourceLocation.parse("pack:wrong_type");
            var wrongNamespace = ResourceLocation.parse("other:selected");
            var missing = ResourceLocation.parse("pack:missing");
            var selector = new RecipeLockRecipe.Selector(List.of(), List.of("pack:.*"),
                    List.of(ResourceLocation.parse("minecraft:crafting")), List.of("pack"), List.of(excluded));
            fixture.replace(List.of(node(first), node(second), crafting(selected, Items.DIAMOND, 4),
                    crafting(excluded, Items.EMERALD, 2), crafting(wrongNamespace, Items.GOLD_INGOT, 1),
                    new RecipeHolder<>(wrongType, matter(List.of(new ItemStack(Items.IRON_INGOT)), FluidStack.EMPTY, List.of())),
                    new RecipeHolder<>(ResourceLocation.parse("locks:selector"), new RecipeLockRecipe(List.of(first), "all", selector)),
                    new RecipeHolder<>(ResourceLocation.parse("locks:overlap"), new RecipeLockRecipe(List.of(MATERIAL, second), "any",
                            new RecipeLockRecipe.Selector(List.of(selected), List.of(), List.of(), List.of(), List.of()))),
                    exactLock("missing", missing, second)));
            var directory = StarResearchNodes.catalog(fixture.level);
            var snapshot = StarResearchMenuNodes.from(directory, Set.of());
            var catalog = new ServerCache().get(fixture.level, directory, 0);
            assertEquals(3, catalog.nodes().size());
            assertEquals(List.of(selected), catalog.recipes(snapshot.indexOf(first)).stream().map(Entry::id).toList());
            assertEquals(List.of(selected), catalog.recipes(snapshot.indexOf(MATERIAL)).stream().map(Entry::id).toList());
            assertEquals(List.of(missing, selected), catalog.recipes(snapshot.indexOf(second)).stream().map(Entry::id).toList());
            var absent = catalog.recipes(snapshot.indexOf(second)).getFirst();
            assertFalse(absent.present());
            assertTrue(absent.outputs().isEmpty());
            assertEquals(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 4)),
                    catalog.recipes(snapshot.indexOf(first)).getFirst().outputs());
            for (var open : List.of(excluded, wrongType, wrongNamespace)) {
                assertTrue(RecipeLocks.requirements(fixture.level, open).isEmpty());
                assertTrue(catalog.nodes().stream().flatMap(List::stream).noneMatch(entry -> entry.id().equals(open)));
            }
            var requirements = RecipeLocks.requirements(fixture.level, selected);
            assertEquals(2, requirements.size());
            assertTrue(requirements.contains(new RecipeLocks.ResearchRequirement(Set.of(first), false, true)));
            assertTrue(requirements.contains(new RecipeLocks.ResearchRequirement(Set.of(MATERIAL, second), true, true)));
            assertEquals(catalog, roundTrip(catalog));
        }
    }

    @Test
    void runtimeCatalogHasUnlimitedNodeCountButOnlyEightSortedPreviewsPerNode() throws Exception {
        try (var fixture = new RuntimeFixture()) {
            var holders = new ArrayList<RecipeHolder<?>>();
            var ids = new ArrayList<ResourceLocation>();
            for (int index = 0; index < 96; index++) {
                var id = ResourceLocation.parse("pack:node_" + index);
                ids.add(id);
                holders.add(node(id));
            }
            var targets = new ArrayList<ResourceLocation>();
            for (int index = 11; index >= 0; index--) {
                var id = ResourceLocation.parse("pack:recipe_" + String.format(java.util.Locale.ROOT, "%02d", index));
                targets.add(id);
                holders.add(crafting(id, Items.DIAMOND, index + 1));
            }
            holders.add(new RecipeHolder<>(ResourceLocation.parse("locks:all_custom"), new RecipeLockRecipe(ids, "all",
                    new RecipeLockRecipe.Selector(List.of(), List.of("pack:recipe_.*"), List.of(), List.of("pack"), List.of()))));
            fixture.replace(holders);
            var directory = StarResearchNodes.catalog(fixture.level);
            var snapshot = StarResearchMenuNodes.from(directory, Set.of());
            var catalog = new ServerCache().get(fixture.level, directory, 0);
            assertEquals(97, catalog.nodes().size());
            assertTrue(catalog.recipes(snapshot.indexOf(MATERIAL)).isEmpty());
            var expected = targets.stream().sorted().limit(StarComputationRecipeCatalog.MAX_RECIPES_PER_NODE).toList();
            for (var id : ids) {
                var previews = catalog.recipes(snapshot.indexOf(id));
                assertEquals(8, previews.size(), id.toString());
                assertEquals(expected, previews.stream().map(Entry::id).toList());
                assertTrue(previews.stream().allMatch(Entry::present));
            }
            assertEquals(96 * 8, catalog.nodes().stream().mapToInt(List::size).sum());
            assertEquals(catalog, roundTrip(catalog));
        }
    }

    @Test
    void runtimeCacheImmediatelyTracksSameManagerReloadAndTheExactDirectoryOrderIncludingRemoval() throws Exception {
        try (var fixture = new RuntimeFixture()) {
            var last = ResourceLocation.parse("pack:z_last");
            var inserted = ResourceLocation.parse("pack:a_inserted");
            var target = ResourceLocation.parse("pack:target");
            var cache = new ServerCache();
            fixture.replace(List.of(node(last), crafting(target, Items.DIAMOND, 2), exactLock("target", target, last)));
            var beforeDirectory = StarResearchNodes.catalog(fixture.level);
            var beforeMenu = StarResearchMenuNodes.from(beforeDirectory, Set.of());
            var before = cache.get(fixture.level, beforeDirectory, 0);
            assertEquals(List.of(target), before.recipes(beforeMenu.indexOf(last)).stream().map(Entry::id).toList());
            fixture.replace(List.of(node(inserted), node(last), crafting(target, Items.GOLD_INGOT, 7), exactLock("target", target, last)));
            var afterDirectory = StarResearchNodes.catalog(fixture.level);
            var afterMenu = StarResearchMenuNodes.from(afterDirectory, Set.of());
            var after = cache.get(fixture.level, afterDirectory, 1);
            assertNotEquals(beforeMenu.indexOf(last), afterMenu.indexOf(last));
            assertTrue(after.recipes(beforeMenu.indexOf(last)).isEmpty(), "The old slot now belongs to an unrelated node");
            assertEquals(List.of(new Entry(target, true, List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 7)))),
                    after.recipes(afterMenu.indexOf(last)));
            assertNotSame(before, after, "Reloaded holders and directory order take effect on the next call");
            assertSame(after, cache.get(fixture.level, afterDirectory, 2));
            assertEquals(2, before.recipes(beforeMenu.indexOf(last)).getFirst().outputs().getFirst().amount());
            // Even a separately ordered snapshot of the same definitions must control its own preview indices.
            var reversedNodes = new LinkedHashMap<ResourceLocation, StarResearchNodeRecipe>();
            new ArrayList<>(afterDirectory.nodes().entrySet()).reversed()
                    .forEach(entry -> reversedNodes.put(entry.getKey(), entry.getValue()));
            var reversed = new StarResearchNodes.Catalog(reversedNodes, afterDirectory.errors());
            var reordered = cache.get(fixture.level, reversed, 2);
            assertEquals(List.of(target), reordered.recipes(0).stream().map(Entry::id).toList());
            assertTrue(reordered.recipes(2).isEmpty());
            fixture.replace(List.of(node(inserted), crafting(target, Items.GOLD_INGOT, 7), exactLock("target", target, last)));
            var removedDirectory = StarResearchNodes.catalog(fixture.level);
            var removed = cache.get(fixture.level, removedDirectory, 3);
            assertEquals(2, removed.nodes().size());
            assertTrue(removed.nodes().stream().allMatch(List::isEmpty), "A removed node's target must not move to another slot");
            assertFalse(RecipeLocks.requirements(fixture.level, target).getFirst().valid());
        }
    }

    @Test
    void runtimeCatalogIncludesActualBridgeHoldersAndInheritsBothInternalAndSourceIdLocks() throws Exception {
        try (var fixture = new RuntimeFixture()) {
            var internalNode = ResourceLocation.parse("pack:bridge_node");
            var source = ResourceLocation.parse("thirdparty:source_recipe");
            var declaration = MatterMachineImportRecipe.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("""
                    {"machine":"minecraft:furnace", "serializers":["minecraft:crafting_shapeless"],
                     "research":{"title":"Imported machine", "ingredients":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":1}]},
                     "inputs":{"items":["/ingredients"]}, "outputs":{"items":["/result"]}}
                    """)).getOrThrow();
            fixture.replace(List.of(node(internalNode), crafting(source, Items.DIAMOND, 4),
                    new RecipeHolder<>(ResourceLocation.parse("pack:import_machine"), declaration),
                    exactLock("source", source, MATERIAL),
                    new RecipeHolder<>(ResourceLocation.parse("locks:internal"), new RecipeLockRecipe(List.of(internalNode), "all",
                            new RecipeLockRecipe.Selector(List.of(), List.of("molecularmanipulator:fabrication/import/.*"),
                                    List.of(), List.of(), List.of())))));
            var imported = MatterRecipeIndex.get(fixture.level).fabrication();
            assertEquals(1, imported.size(), "Use a real bridge import, not a hand-built source-ID marker");
            var bridge = imported.getFirst();
            assertEquals(source, bridge.value().sourceRecipeId());
            assertTrue(fixture.manager.byKey(bridge.id()).isEmpty(), "Bridge holders live outside RecipeManager");
            var directory = StarResearchNodes.catalog(fixture.level);
            var snapshot = StarResearchMenuNodes.from(directory, Set.of());
            var catalog = new ServerCache().get(fixture.level, directory, 0);
            assertEquals(List.of(bridge.id(), source).stream().sorted().toList(),
                    catalog.recipes(snapshot.indexOf(MATERIAL)).stream().map(Entry::id).toList());
            assertEquals(List.of(bridge.id()), catalog.recipes(snapshot.indexOf(internalNode)).stream().map(Entry::id).toList());
            assertEquals(Set.of(MATERIAL, internalNode), RecipeLocks.requirements(fixture.level, bridge).stream()
                    .flatMap(requirement -> requirement.nodes().stream()).collect(java.util.stream.Collectors.toSet()));
            assertEquals(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 4)),
                    catalog.recipes(snapshot.indexOf(internalNode)).getFirst().outputs());
            assertEquals(catalog, roundTrip(catalog));
        }
    }

    public static final class SyncedView {
        @GuiSync(14) public StarComputationRecipeCatalog catalog = StarComputationRecipeCatalog.EMPTY;
    }

    @Test
    void packetAcceptsVariableNodeCountsButRejectsImpossibleNodeRecipeAndOutputBoundsBeforeAllocation() {
        for (int count : new int[] {0, 1, 4, 6, 97}) {
            var catalog = new StarComputationRecipeCatalog(java.util.Collections.nCopies(count, List.of()));
            assertEquals(catalog, roundTrip(catalog));
        }
        for (int size : new int[] {-1, 1, 4, 6, Integer.MAX_VALUE}) {
            var buffer = buffer();
            try {
                buffer.writeVarInt(size);
                assertThrows(IllegalArgumentException.class, () -> new StarComputationRecipeCatalog(buffer));
            } finally { buffer.release(); }
        }
        for (int size : new int[] {-1, 9, Integer.MAX_VALUE}) {
            var buffer = buffer();
            try {
                buffer.writeVarInt(1); buffer.writeVarInt(size);
                assertThrows(IllegalArgumentException.class, () -> new StarComputationRecipeCatalog(buffer));
            } finally { buffer.release(); }
        }
        for (int size : new int[] {-1, 14, Integer.MAX_VALUE}) {
            var buffer = buffer();
            try {
                buffer.writeVarInt(1); buffer.writeVarInt(1);
                buffer.writeResourceLocation(CONTROLLER);
                buffer.writeBoolean(true); buffer.writeVarInt(size);
                assertThrows(IllegalArgumentException.class, () -> new StarComputationRecipeCatalog(buffer));
            } finally { buffer.release(); }
        }
    }

    @Test
    void snapshotsAreDeeplyImmutableAndMissingEntriesCannotHaveInventedOutputs() {
        var entries = new ArrayList<Entry>();
        var id = ResourceLocation.parse("test:immutable_snapshot");
        var entry = new Entry(id, false, List.of());
        entries.add(entry);
        var nodes = new ArrayList<>(java.util.Collections.<List<Entry>>nCopies(97, List.of()));
        nodes.set(0, entries);
        var catalog = new StarComputationRecipeCatalog(nodes);
        entries.clear(); nodes.clear();
        assertEquals(List.of(entry), catalog.recipes(0));
        assertThrows(UnsupportedOperationException.class, () -> catalog.recipes(0).clear());
        assertThrows(UnsupportedOperationException.class, () -> catalog.nodes().clear());
        assertEquals(List.of(), catalog.recipes(-1));
        assertEquals(List.of(), catalog.recipes(97));
        assertThrows(IllegalArgumentException.class, () -> new StarComputationRecipeCatalog(List.of(
                java.util.Collections.nCopies(StarComputationRecipeCatalog.MAX_RECIPES_PER_NODE + 1, entry))));
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        assertThrows(IllegalArgumentException.class, () -> new Entry(id, false, List.of(output)));
        assertThrows(IllegalArgumentException.class, () -> new Entry(id, true,
                java.util.Collections.nCopies(14, output)));
        assertThrows(IllegalArgumentException.class, () -> new Entry(id, true,
                List.of(new GenericStack(output.what(), 0))));
        assertEquals(StarComputationRecipeCatalog.EMPTY, roundTrip(StarComputationRecipeCatalog.EMPTY));
    }

    private static RecipeHolder<StarResearchNodeRecipe> node(ResourceLocation id) {
        return new RecipeHolder<>(id, new StarResearchNodeRecipe("Node " + id, 1, 0, List.of()));
    }

    private static RecipeHolder<ShapelessRecipe> crafting(ResourceLocation id, net.minecraft.world.item.Item output, int count) {
        return new RecipeHolder<>(id, new ShapelessRecipe("", CraftingBookCategory.MISC, new ItemStack(output, count),
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.STONE))));
    }

    private static RecipeHolder<RecipeLockRecipe> exactLock(String name, ResourceLocation target, ResourceLocation node) {
        return new RecipeHolder<>(ResourceLocation.parse("locks:" + name), new RecipeLockRecipe(List.of(node), "all",
                new RecipeLockRecipe.Selector(List.of(target), List.of(), List.of(), List.of(), List.of())));
    }

    /** Real RecipeManager and bridge/index logic; only deferred registration and unused Level infrastructure are supplied. */
    private static final class RuntimeFixture implements AutoCloseable {
        private final java.lang.reflect.Field holderField;
        private final Map<DeferredHolder<?, ?>, Object> previous = new LinkedHashMap<>();
        final RecipeManager manager = new RecipeManager(registries);
        final StarResearchTestLevel level;

        RuntimeFixture() throws Exception {
            holderField = DeferredHolder.class.getDeclaredField("holder");
            holderField.setAccessible(true);
            try {
                bind(ModContent.MATTER_FABRICATION_RECIPE_TYPE, RecipeType.simple(ResourceLocation.parse("test:matter_fabrication")));
                bind(ModContent.MATTER_RESEARCH_RECIPE_TYPE, RecipeType.simple(ResourceLocation.parse("test:matter_research")));
                bind(ModContent.STAR_RESEARCH_RECIPE_TYPE, RecipeType.simple(ResourceLocation.parse("test:star_research")));
                bind(ModContent.RECIPE_LOCK_RECIPE_TYPE, RecipeType.simple(ResourceLocation.parse("test:recipe_lock")));
                bind(ModContent.MATTER_MACHINE_IMPORT_RECIPE_TYPE, RecipeType.simple(ResourceLocation.parse("test:machine_import")));
                bind(ModContent.STAR_RESEARCH_RECIPE_SERIALIZER, new StarResearchNodeRecipe.Serializer());
                bind(ModContent.RECIPE_LOCK_RECIPE_SERIALIZER, new RecipeLockRecipe.Serializer());
                level = StarResearchTestLevel.create(manager, registries);
            } catch (Exception | Error failure) {
                close();
                throw failure;
            }
        }

        private void bind(DeferredHolder<?, ?> deferred, Object value) throws IllegalAccessException {
            previous.put(deferred, holderField.get(deferred));
            holderField.set(deferred, Holder.direct(value));
        }

        void replace(List<RecipeHolder<?>> holders) { manager.replaceRecipes(holders); }

        void replaceMaterialTargets(RecipeHolder<?>... holders) {
            var recipes = new ArrayList<RecipeHolder<?>>(List.of(holders));
            for (var target : DEFAULT_TARGETS) recipes.add(exactLock(target.getPath(), target, MATERIAL));
            replace(recipes);
        }

        @Override public void close() throws IllegalAccessException {
            for (var entry : previous.entrySet()) holderField.set(entry.getKey(), entry.getValue());
        }
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

    private static StarComputationRecipeCatalog roundTrip(StarComputationRecipeCatalog catalog) {
        var buffer = buffer();
        try {
            catalog.writeToPacket(buffer);
            var decoded = new StarComputationRecipeCatalog(buffer);
            assertEquals(0, buffer.readableBytes());
            return decoded;
        } finally { buffer.release(); }
    }
}
