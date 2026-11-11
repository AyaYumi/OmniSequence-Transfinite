package com.atir.molecularmanipulator.recipelock;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.recipelock.RecipeLocks.ResearchRequirement;
import com.atir.molecularmanipulator.starcomputation.StarResearchNodeRecipe;
import com.atir.molecularmanipulator.starcomputation.StarResearchNodes;
import com.atir.molecularmanipulator.starcomputation.StarComputationResearchData;
import com.atir.molecularmanipulator.starcomputation.StarResearchTestLevel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RecipeLockIndexTest {
    private static final ResourceLocation A = id("pack:research_a");
    private static final ResourceLocation B = id("pack:research_b");
    private static final ResourceLocation C = id("pack:research_c");
    private static final ResourceLocation D = id("pack:research_d");
    private static final ResourceLocation CRAFTING = id("minecraft:crafting");
    private static final ResourceLocation SMELTING = id("minecraft:smelting");
    private static final ResourceLocation BLASTING = id("minecraft:blasting");
    private static final Set<ResourceLocation> VALID = Set.of(A, B, C, D);

    @BeforeAll
    static void bootstrap() { RecipeLockRecipeTest.bootstrap(); }

    @Test
    void exactIdAlternativesOnlyLockTheirTargetsIncludingBridgeIdsOutsideTheManager() {
        var first = id("pack:first");
        var second = id("other:second");
        var bridgeOnly = id("bridge:not_in_recipe_manager");
        var open = id("pack:open");
        var index = build(Map.of(first, CRAFTING, second, SMELTING, open, CRAFTING),
                rule("exact", List.of(A), "all", selector(List.of(first, second, bridgeOnly), List.of(), List.of(), List.of(), List.of())));
        for (var target : List.of(first, second, bridgeOnly)) {
            assertTrue(index.locked(target), target.toString());
            assertFalse(index.allowed(target, Set.of()));
            assertTrue(index.allowed(target, Set.of(A)));
        }
        assertFalse(index.locked(open));
        assertTrue(index.allowed(open, Set.of()));
        assertTrue(index.allowed(id("another:unmentioned"), Set.of()));
    }

    @Test
    void regexAlternativesUseTheEntireNamespacedIdAndAreNotSubstringOrPathMatches() {
        var first = id("pack:locked_one");
        var second = id("other:special");
        var substring = id("pack:prefix_locked_one");
        var wrongNamespace = id("elsewhere:locked_one");
        var index = build(Map.of(first, CRAFTING, second, CRAFTING, substring, CRAFTING, wrongNamespace, CRAFTING),
                rule("regex", List.of(A), "all", selector(List.of(), List.of("pack:locked_.*", "other:special"), List.of(), List.of(), List.of())));
        assertLockedOnly(index, Set.of(first, second), Set.of(substring, wrongNamespace));
        var pathOnly = build(Map.of(first, CRAFTING),
                rule("path_only", List.of(A), "all", selector(List.of(), List.of("locked_.*"), List.of(), List.of(), List.of())));
        assertFalse(pathOnly.hasRules(), "Regex sees namespace:path, not just path");
        assertTrue(pathOnly.allowed(first, Set.of()));
    }

    @Test
    void recipeTypeAlternativesAndNamespaceAlternativesEachUseOr() {
        var first = id("pack:first");
        var second = id("other:second");
        var third = id("third:third");
        var recipes = Map.of(first, CRAFTING, second, SMELTING, third, BLASTING);
        var byType = build(recipes, rule("types", List.of(A), "all",
                selector(List.of(), List.of(), List.of(CRAFTING, SMELTING), List.of(), List.of())));
        assertLockedOnly(byType, Set.of(first, second), Set.of(third));
        var byNamespace = build(recipes, rule("namespaces", List.of(A), "all",
                selector(List.of(), List.of(), List.of(), List.of("pack", "other"), List.of())));
        assertLockedOnly(byNamespace, Set.of(first, second), Set.of(third));
    }

    @Test
    void allNonemptySelectorFieldsAreAndButAlternativesWithinEachFieldAreOr() {
        var first = id("pack:locked_first");
        var second = id("other:special");
        var wrongRegex = id("pack:plain");
        var wrongType = id("pack:locked_blasting");
        var wrongNamespace = id("third:locked_third");
        var notListed = id("pack:locked_not_listed");
        var excluded = id("pack:locked_excluded");
        var recipes = Map.of(first, CRAFTING, second, SMELTING, wrongRegex, CRAFTING, wrongType, BLASTING,
                wrongNamespace, CRAFTING, notListed, CRAFTING, excluded, CRAFTING);
        var index = build(recipes, rule("intersection", List.of(A), "all", selector(
                List.of(first, second, wrongRegex, wrongType, wrongNamespace, excluded),
                List.of("[^:]+:locked_.*", "other:special"), List.of(CRAFTING, SMELTING),
                List.of("pack", "other"), List.of(excluded))));
        assertLockedOnly(index, Set.of(first, second), Set.of(wrongRegex, wrongType, wrongNamespace, notListed, excluded));
    }

    @Test
    void exclusionsWinEvenAgainstAnExplicitIdAndEveryOtherMatchingField() {
        var target = id("pack:target");
        var index = build(Map.of(target, CRAFTING), rule("excluded", List.of(A), "all",
                selector(List.of(target), List.of("pack:.*"), List.of(CRAFTING), List.of("pack"), List.of(target))));
        assertFalse(index.locked(target));
        assertFalse(index.hasRules());
        assertTrue(index.allowed(target, Set.of()));
    }

    @Test
    void anExplicitUnloadedIdCannotSatisfyARequiredTypeField() {
        var absent = id("bridge:absent");
        var index = build(Map.of(), rule("typed_bridge", List.of(A), "all",
                selector(List.of(absent), List.of(), List.of(CRAFTING), List.of(), List.of())));
        assertFalse(index.locked(absent), "Missing type is not a wildcard for an otherwise explicit ID");
        assertTrue(index.allowed(absent, Set.of()));
    }

    @Test
    void singleAllRuleRequiresEveryNodeWhileSingleAnyRuleAcceptsEitherNode() {
        var target = id("pack:target");
        var all = build(Map.of(target, CRAFTING), exact("all", target, "all", A, B));
        var any = build(Map.of(target, CRAFTING), exact("any", target, "any", A, B));
        assertFalse(all.allowed(target, Set.of()));
        assertFalse(any.allowed(target, Set.of()));
        for (var grant : List.of(A, B)) {
            assertFalse(all.allowed(target, Set.of(grant)));
            assertTrue(any.allowed(target, Set.of(grant)));
        }
        assertTrue(all.allowed(target, Set.of(A, B)));
        assertTrue(any.allowed(target, Set.of(A, B)));
        assertFalse(any.allowed(target, Set.of(C)), "Unrelated research never satisfies any");
    }

    @Test
    void overlappingRulesAreAlwaysAndEvenWhenOneRuleUsesAnyAndDeclarationOrderChanges() {
        var target = id("pack:target");
        var alternative = exact("alternative", target, "any", A, B);
        var mandatory = rule("mandatory", List.of(C, D), "all",
                selector(List.of(), List.of(), List.of(), List.of("pack"), List.of()));
        for (var rules : List.of(List.of(alternative, mandatory), List.of(mandatory, alternative))) {
            var index = RecipeLockIndex.build(Map.of(target, CRAFTING), rules, VALID);
            for (var incomplete : List.of(Set.<ResourceLocation>of(), Set.of(A), Set.of(C, D), Set.of(B, C), Set.of(A, D))) {
                assertFalse(index.allowed(target, incomplete), incomplete.toString());
            }
            assertTrue(index.allowed(target, Set.of(A, C, D)));
            assertTrue(index.allowed(target, Set.of(B, C, D)));
        }
    }

    @Test
    void anyRuleWithEvenOneUndefinedNodeFailsClosedIncludingPreviouslySavedUnknownGrants() {
        var target = id("pack:target");
        var undefined = id("removed:research");
        for (var mode : List.of("all", "any")) {
            var index = build(Map.of(target, CRAFTING), exact("unknown_" + mode, target, mode, A, undefined));
            assertTrue(index.locked(target));
            assertTrue(index.hasRules());
            assertFalse(index.allowed(target, Set.of()));
            assertFalse(index.allowed(target, Set.of(A)), "A known alternative must not rescue an invalid declaration");
            assertFalse(index.allowed(target, Set.of(A, undefined)), "Saved progress does not define a missing node");
            assertTrue(index.allowed(id("pack:unmatched"), Set.of()), "Fail-closed applies only to matched recipes");
        }
    }

    @Test
    void rulesThatMatchNothingAndAnEmptyDirectoryLeaveUnmentionedRecipesOpen() {
        var target = id("pack:target");
        var index = build(Map.of(target, CRAFTING), rule("unmatched", List.of(A), "all",
                selector(List.of(), List.of("elsewhere:.*"), List.of(), List.of(), List.of())));
        assertFalse(index.hasRules());
        assertTrue(index.allowed(target, Set.of()));
        var empty = RecipeLockIndex.build(Map.of(target, CRAFTING), List.of(), Set.of());
        assertFalse(empty.hasRules());
        assertTrue(empty.allowed(target, Set.of()));
    }

    @Test
    void malformedProgrammaticRulesFailAtBuildRatherThanSilentlyOpeningRecipes() {
        var target = id("pack:target");
        var validSelector = selector(List.of(target), List.of(), List.of(), List.of(), List.of());
        for (var invalid : List.of(
                rule("empty_requirements", List.of(), "all", validSelector),
                rule("bad_mode", List.of(A), "xor", validSelector),
                rule("empty_selector", List.of(A), "all", selector(List.of(), List.of(), List.of(), List.of(), List.of())),
                rule("bad_regex", List.of(A), "all", selector(List.of(), List.of("["), List.of(), List.of(), List.of())))) {
            var error = assertThrows(IllegalArgumentException.class,
                    () -> RecipeLockIndex.build(Map.of(target, CRAFTING), List.of(invalid), VALID));
            assertTrue(error.getMessage().contains(invalid.id().toString()));
        }
    }

    @Test
    void definitionQueryPreservesOverlappingModesAndReturnsImmutableDescriptions() {
        var target = id("pack:target");
        var index = RecipeLockIndex.build(Map.of(target, CRAFTING), List.of(
                exact("alternative", target, "any", A, B), exact("mandatory", target, "all", C)), VALID);
        var requirements = index.requirements(target);
        assertEquals(List.of(new ResearchRequirement(Set.of(A, B), true, true),
                new ResearchRequirement(Set.of(C), false, true)), requirements);
        assertThrows(UnsupportedOperationException.class, () -> requirements.clear());
        assertThrows(UnsupportedOperationException.class, () -> requirements.getFirst().nodes().clear());
        assertFalse(index.allowed(target, Set.of(A)), "Reading definitions does not grant research");
        assertTrue(index.allowed(target, Set.of(A, C)));

        var mutableNodes = new HashSet<>(Set.of(A, B));
        var copied = new ResearchRequirement(mutableNodes, true, true);
        mutableNodes.clear();
        assertEquals(requirements.getFirst(), copied, "Public display records copy caller-owned node sets");
    }

    @Test
    void definitionQueryRetainsInvalidNodesButOmitsExcludedAndUnmatchedRecipes() {
        var target = id("pack:target");
        var excluded = id("pack:excluded");
        var open = id("pack:open");
        var missing = id("removed:research");
        var index = build(Map.of(target, CRAFTING, excluded, CRAFTING, open, CRAFTING),
                rule("invalid_display", List.of(A, missing), "any", selector(
                        List.of(target, excluded), List.of(), List.of(), List.of(), List.of(excluded))));
        assertEquals(List.of(new ResearchRequirement(Set.of(A, missing), true, false)), index.requirements(target));
        assertTrue(index.requirements(excluded).isEmpty(), "An exclusion does not advertise an applicable lock");
        assertTrue(index.requirements(open).isEmpty());
        assertTrue(index.requirements(id("bridge:unmentioned")).isEmpty());
    }

    @Test
    void builtRequirementsAreSnapshotsAndDoNotRetainMutableInputsOrGrantSets() {
        var target = id("pack:target");
        var recipes = new HashMap<>(Map.of(target, CRAFTING));
        var declarations = new ArrayList<>(List.of(exact("snapshot", target, "all", A)));
        var nodes = new HashSet<>(VALID);
        var index = RecipeLockIndex.build(recipes, declarations, nodes);
        recipes.clear();
        declarations.clear();
        nodes.clear();
        var grants = new HashSet<ResourceLocation>();
        assertFalse(index.allowed(target, grants));
        grants.add(A);
        assertTrue(index.allowed(target, grants));
        grants.clear();
        assertFalse(index.allowed(target, grants), "Allowed answers are never cached per recipe or caller");
        assertTrue(index.locked(target));
    }

    @Test
    void compileUsesRecipeIdentityAndExcludesDeclarationsAndGraphInvalidResearch() {
        var firstId = id("pack:first");
        var secondId = id("pack:second");
        var invalidId = id("pack:invalid_research");
        var first = new EqualRecipe(RecipeType.CRAFTING);
        var second = new EqualRecipe(RecipeType.CRAFTING);
        assertEquals(first, second, "A value-equal recipe still has a different runtime identity");
        assertNotSame(first, second);
        var node = new StarResearchNodeRecipe("Node", 1, 0, List.of());
        var firstLock = exact("valid", firstId, "all", A);
        var secondLock = exact("invalid_graph", secondId, "all", invalidId);
        var catalog = new StarResearchNodes.Catalog(Map.of(A, node, invalidId, node), Map.of(invalidId, "Cyclic prerequisites"));
        var index = RecipeLockIndex.compile(List.of(new RecipeHolder<>(firstId, first), new RecipeHolder<>(secondId, second),
                new RecipeHolder<>(A, node), firstLock, secondLock), catalog);
        assertEquals(firstId, index.idOf(first));
        assertEquals(secondId, index.idOf(second));
        assertNull(index.idOf(new EqualRecipe(RecipeType.CRAFTING)), "Equal is not identical; stale copies must not acquire an ID");
        assertNull(index.idOf(node));
        assertNull(index.idOf(firstLock.value()));
        assertTrue(index.allowed(firstId, Set.of(A)));
        assertFalse(index.allowed(secondId, Set.of(A, invalidId)), "A catalog error invalidates a node even when the node exists");
        assertEquals(List.of(new ResearchRequirement(Set.of(A), false, true)), index.requirements(firstId));
        assertEquals(List.of(new ResearchRequirement(Set.of(invalidId), false, false)), index.requirements(secondId));
    }

    @Test
    void retainedLegacySaveIdsAreInvalidLockRequirementsUntilExplicitDefinitionsAreProvided() throws Exception {
        var target = id("pack:legacy_fixture_target");
        var material = id("molecularmanipulator:material");
        var emptyLevel = StarResearchTestLevel.create(new RecipeManager(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        var defaults = StarResearchNodes.catalog(emptyLevel);
        assertEquals(Set.of(material), defaults.nodes().keySet());
        for (var legacy : StarComputationResearchData.LEGACY_NODE_IDS) {
            var rule = exact("legacy_" + legacy.getPath(), target, "all", legacy);
            var targetHolder = new RecipeHolder<>(target, new EqualRecipe(RecipeType.CRAFTING));
            var original = RecipeLockIndex.compile(List.of(targetHolder, rule), defaults);
            assertEquals(legacy.equals(material), original.allowed(target, Set.of(legacy)),
                    "Retained paid IDs do not restore removed runtime definitions: " + legacy);
            if (legacy.equals(material)) continue;
            // A fixture/pack that intentionally uses a retired ID must declare it, not rely on a fallback grant.
            var definition = new RecipeHolder<>(legacy, new StarResearchNodeRecipe("Explicit legacy fixture", 1, 0, List.of()));
            var manager = new RecipeManager(RegistryAccess.EMPTY) {
                @Override public java.util.Collection<RecipeHolder<?>> getRecipes() { return List.of(definition); }
            };
            var level = StarResearchTestLevel.create(manager, RegistryAccess.EMPTY);
            var restored = RecipeLockIndex.compile(List.of(targetHolder, definition, rule), StarResearchNodes.catalog(level));
            assertFalse(restored.allowed(target, Set.of()));
            assertTrue(restored.allowed(target, Set.of(legacy)));
            assertEquals(List.of(new ResearchRequirement(Set.of(legacy), false, true)), restored.requirements(target));
        }
    }

    private static RecipeLockIndex build(Map<ResourceLocation, ResourceLocation> recipes, RecipeHolder<RecipeLockRecipe> rule) {
        return RecipeLockIndex.build(recipes, List.of(rule), VALID);
    }

    private static RecipeHolder<RecipeLockRecipe> exact(String name, ResourceLocation target, String mode, ResourceLocation... requires) {
        return rule(name, List.of(requires), mode, selector(List.of(target), List.of(), List.of(), List.of(), List.of()));
    }

    private static RecipeHolder<RecipeLockRecipe> rule(String name, List<ResourceLocation> requires, String mode,
                                                       RecipeLockRecipe.Selector selector) {
        return new RecipeHolder<>(id("locks:" + name), new RecipeLockRecipe(requires, mode, selector));
    }

    private static RecipeLockRecipe.Selector selector(List<ResourceLocation> ids, List<String> regex,
            List<ResourceLocation> types, List<String> namespaces, List<ResourceLocation> excludes) {
        return new RecipeLockRecipe.Selector(ids, regex, types, namespaces, excludes);
    }

    private static void assertLockedOnly(RecipeLockIndex index, Set<ResourceLocation> locked, Set<ResourceLocation> open) {
        for (var target : locked) {
            assertTrue(index.locked(target), target.toString());
            assertFalse(index.allowed(target, Set.of()), target.toString());
            assertTrue(index.allowed(target, Set.of(A)), target.toString());
        }
        for (var target : open) {
            assertFalse(index.locked(target), target.toString());
            assertTrue(index.allowed(target, Set.of()), target.toString());
        }
    }

    /** Value equality is deliberate: the production identity map must not collapse these instances. */
    private record EqualRecipe(RecipeType<?> type) implements Recipe<CraftingInput> {
        @Override public boolean matches(CraftingInput input, Level level) { return false; }
        @Override public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) { return ItemStack.EMPTY; }
        @Override public boolean canCraftInDimensions(int width, int height) { return false; }
        @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return ItemStack.EMPTY; }
        @Override public RecipeSerializer<?> getSerializer() { return RecipeSerializer.SHAPELESS_RECIPE; }
        @Override public RecipeType<?> getType() { return type; }
    }

    private static ResourceLocation id(String name) { return ResourceLocation.parse(name); }
}
