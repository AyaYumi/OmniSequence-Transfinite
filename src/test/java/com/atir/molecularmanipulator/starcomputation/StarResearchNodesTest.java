package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StarResearchNodesTest {
    @BeforeAll
    static void bootstrap() {
        StarResearchNodeRecipeTest.bootstrap();
    }

    @Test
    void onlyMaterialIsRegisteredWhileAllFiveOriginalSaveIdsKeepTheirPermanentOrder() {
        var catalog = StarResearchNodes.build(Map.of());
        var material = id("molecularmanipulator:material");
        var legacyIds = List.of(id("molecularmanipulator:foundation"), material, id("molecularmanipulator:quantum"),
                id("molecularmanipulator:terminal"), id("molecularmanipulator:transfinite"));
        assertEquals(legacyIds, StarComputationResearchData.LEGACY_NODE_IDS);
        assertEquals(List.of(material), new ArrayList<>(catalog.nodes().keySet()));
        assertTrue(catalog.errors().isEmpty());
        assertNull(catalog.error(material));
        for (var hidden : legacyIds) {
            if (hidden.equals(material)) continue;
            assertFalse(catalog.nodes().containsKey(hidden));
            assertNotNull(catalog.error(hidden), "A save-compatible ID is not a runtime definition: " + hidden);
            assertFalse(catalog.canUnlock(hidden, 9, Set.copyOf(legacyIds)));
        }
        assertThrows(UnsupportedOperationException.class, () -> catalog.nodes().clear());
    }

    @Test
    void materialIsFreeAtActualLevelThreeWithEmptyPermanentProgressAndNoFoundationRequirement() {
        var catalog = StarResearchNodes.build(Map.of());
        var material = id("molecularmanipulator:material");
        var node = catalog.nodes().get(material);
        assertEquals(3, node.requiredLevel());
        assertEquals(0, node.informationCost());
        assertEquals("gui.molecularmanipulator.star_computation.node.material", node.title());
        assertEquals(List.of(), node.prerequisites());
        for (int level = 0; level < 3; level++) {
            assertFalse(catalog.canUnlock(material, level, Set.of()), "Actual level L" + level);
        }
        assertTrue(catalog.canUnlock(material, 3, Set.of()));
        assertTrue(catalog.canUnlock(material, 9, Set.of()));
        assertFalse(catalog.canUnlock(material, 9, Set.of(material)), "Already purchased nodes cannot be bought again");
        var legacy = StarComputationRecipes.NODES.get(1);
        assertEquals("material", legacy.id());
        assertEquals(1, legacy.index());
        assertEquals(0, legacy.informationCost());
        assertEquals(0, legacy.prerequisiteMask());
        assertEquals(List.of(id("molecularmanipulator:molecular_manipulator"),
                id("molecularmanipulator:transfinite_compute_nexus")), StarComputationRecipes.recipeIds(1));
    }

    @Test
    void holderIdOverridesBuiltinTitleLevelPriceAndPrerequisitesInsteadOfAddingAnotherNode() {
        var material = id("molecularmanipulator:material");
        var prerequisite = id("pack:first");
        var replacement = new StarResearchNodeRecipe("Overridden", 2, 17, List.of(prerequisite));
        var catalog = StarResearchNodes.build(Map.of(material, replacement, prerequisite, node()));
        assertEquals(2, catalog.nodes().size());
        assertSame(replacement, catalog.nodes().get(material));
        assertFalse(catalog.canUnlock(material, 1, Set.of(prerequisite)));
        assertFalse(catalog.canUnlock(material, 9, Set.of()));
        assertTrue(catalog.canUnlock(material, 2, Set.of(prerequisite)));
        assertFalse(catalog.canUnlock(material, 9, Set.of(material, prerequisite)), "Already paid is never eligible again");
        assertEquals(17, catalog.nodes().get(material).informationCost());
    }

    @Test
    void unknownAndMissingPrerequisitesFailClosedIncludingAlreadySavedUnknownIdsAndDownstreamNodes() {
        var unknown = id("removed:prerequisite");
        var first = id("pack:first");
        var downstream = id("pack:downstream");
        var okay = id("pack:okay");
        var catalog = StarResearchNodes.build(Map.of(first, node(unknown), downstream, node(first), okay, node()));
        assertNotNull(catalog.error(unknown));
        assertTrue(catalog.error(first).contains("Missing prerequisite"));
        assertNotNull(catalog.error(downstream));
        assertFalse(catalog.canUnlock(unknown, 9, Set.of()));
        assertFalse(catalog.canUnlock(first, 9, Set.of(unknown)), "An undefined prerequisite cannot be treated as satisfied");
        assertFalse(catalog.canUnlock(downstream, 9, Set.of(first, unknown)));
        assertTrue(catalog.canUnlock(okay, 1, Set.of()));
    }

    @Test
    void selfCyclesMultiNodeCyclesAndCycleDependentsCannotBePurchasedEvenWithOldProgress() {
        var self = id("pack:self");
        var first = id("pack:first");
        var second = id("pack:second");
        var downstream = id("pack:downstream");
        var catalog = StarResearchNodes.build(Map.of(self, node(self), first, node(second),
                second, node(first), downstream, node(first)));
        for (var id : List.of(self, first, second, downstream)) {
            assertNotNull(catalog.error(id));
            assertTrue(catalog.error(id).contains("cycle") || catalog.error(id).contains("Cyclic"));
            assertFalse(catalog.canUnlock(id, 9, Set.of(self, first, second)));
        }
        assertTrue(catalog.canUnlock(id("molecularmanipulator:material"), 3, Set.of()));
    }

    @Test
    void invalidBuiltinOverrideCannotSilentlyFallBackOrAuthorizeItsDependents() {
        var material = id("molecularmanipulator:material");
        var dependent = id("pack:dependent");
        var bad = new StarResearchNodeRecipe("Bad override", 10, -1, List.of());
        var catalog = StarResearchNodes.build(Map.of(material, bad, dependent, node(material)));
        assertSame(bad, catalog.nodes().get(material));
        assertNotNull(catalog.error(material));
        assertNotNull(catalog.error(dependent));
        assertFalse(catalog.canUnlock(material, 9, Set.of()));
        assertFalse(catalog.canUnlock(dependent, 9, Set.of(material)));
    }

    @Test
    void unboundedIterativeGraphValidationHandlesThousandsOfNodesAndDuplicateEdges() {
        var definitions = new HashMap<ResourceLocation, StarResearchNodeRecipe>();
        for (int index = 0; index < 2048; index++) {
            var prerequisites = index == 0 ? new ResourceLocation[0]
                    : new ResourceLocation[] {id("pack:node_" + (index - 1)), id("pack:node_" + (index - 1))};
            definitions.put(id("pack:node_" + index), node(prerequisites));
        }
        var catalog = assertDoesNotThrow(() -> StarResearchNodes.build(definitions));
        assertEquals(definitions.size() + 1, catalog.nodes().size());
        assertTrue(catalog.nodes().keySet().containsAll(definitions.keySet()));
        assertTrue(catalog.errors().isEmpty());
        assertTrue(catalog.canUnlock(id("pack:node_2047"), 1, Set.of(id("pack:node_2046"))));
    }

    @Test
    void purchasePrerequisitesUseOnlyOwningTeamPermanentProgressAndCannotBeCopiedBetweenTeams() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var data = new StarComputationResearchData();
        var foundation = id("pack:permanent_parent");
        var custom = id("pack:custom");
        data.addUnlock(first, foundation);
        var catalog = StarResearchNodes.build(Map.of(foundation, node(), custom, node(foundation)));
        assertTrue(catalog.canUnlock(custom, 1, data.getUnlockedNodes(first)));
        assertFalse(catalog.canUnlock(custom, 1, data.getUnlockedNodes(second)));
        assertFalse(catalog.canUnlock(custom, 1, data.getUnlockedNodes(null)));
        assertEquals(Set.of(), data.getUnlockedNodes(second));
        assertTrue(data.addUnlock(first, custom));
        data.setDirty(false);
        assertFalse(catalog.canUnlock(custom, 1, data.getUnlockedNodes(first)));
        assertFalse(data.addUnlock(first, custom));
        assertFalse(data.isDirty());
        assertFalse(data.isUnlocked(second, custom));
    }

    @Test
    void cacheReusesSnapshotsButInvalidatesOnManagerRecipeViewOrReloadGenerationIncludingRemovals() {
        var manager = new ReloadableRecipes();
        var custom = id("pack:custom");
        var material = id("molecularmanipulator:material");
        manager.holders.add(new RecipeHolder<>(custom, node()));
        manager.holders.add(new RecipeHolder<>(material, new StarResearchNodeRecipe("Override", 2, 1, List.of())));
        var initial = StarResearchNodes.catalog(manager, 123);
        assertSame(initial, StarResearchNodes.catalog(manager, 123));
        assertEquals(2, manager.queries, "Each access reads the cheap immutable recipe view before reusing a snapshot");
        manager.holders.clear();
        assertSame(initial, StarResearchNodes.catalog(manager, 123), "The same recipe view and generation reuse the published snapshot");
        var reloaded = StarResearchNodes.catalog(manager, 124);
        assertNotSame(initial, reloaded);
        assertFalse(reloaded.nodes().containsKey(custom));
        assertEquals(Set.of(material), reloaded.nodes().keySet(), "Only the material fallback is restored");
        assertEquals(3, reloaded.nodes().get(material).requiredLevel());
        assertEquals(0, reloaded.nodes().get(material).informationCost());
        assertEquals(List.of(), reloaded.nodes().get(material).prerequisites());
        assertTrue(initial.nodes().containsKey(custom), "Published snapshots remain immutable");
        var other = StarResearchNodes.catalog(new ReloadableRecipes(), 124);
        assertNotSame(other, reloaded);
        assertEquals(4, manager.queries);
        manager.holders = new ArrayList<>(List.of(new RecipeHolder<>(custom, node())));
        var replaced = StarResearchNodes.catalog(manager, 124);
        assertNotSame(reloaded, replaced, "replaceRecipes changes the immutable view even without a reload notification");
        assertTrue(replaced.nodes().containsKey(custom));
    }

    private static StarResearchNodeRecipe node(ResourceLocation... prerequisites) {
        return new StarResearchNodeRecipe("Node", 1, 0, List.of(prerequisites));
    }

    private static ResourceLocation id(String id) { return ResourceLocation.parse(id); }

    private static final class ReloadableRecipes extends RecipeManager {
        List<RecipeHolder<?>> holders = new ArrayList<>();
        int queries;
        ReloadableRecipes() { super(RegistryAccess.EMPTY); }
        @Override public Collection<RecipeHolder<?>> getRecipes() { queries++; return holders; }
    }
}
