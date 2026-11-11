package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeRecipe.Condition;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgradeRecipe.Target;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgrades.MachineSnapshot;
import com.atir.molecularmanipulator.starcomputation.StarComputationUpgrades.TargetProgress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StarComputationUpgradesTest {
    @BeforeAll
    static void bootstrap() { StarComputationUpgradeRecipeTest.bootstrap(); }

    @Test
    void allAnyAndThresholdNeverAcceptEmptyOrInvalidResults() {
        var mixed = List.of(true, false, true);
        assertFalse(StarComputationUpgrades.matches("all", 1, mixed));
        assertTrue(StarComputationUpgrades.matches("any", 1, mixed));
        assertTrue(StarComputationUpgrades.matches("at_least", 2, mixed));
        assertFalse(StarComputationUpgrades.matches("at_least", 3, mixed));
        for (String match : List.of("all", "any", "at_least", "unknown")) {
            assertFalse(StarComputationUpgrades.matches(match, 1, List.of()));
            assertFalse(StarComputationUpgrades.matches(match, 0, mixed));
            assertFalse(StarComputationUpgrades.matches(match, 4, mixed));
        }
        assertFalse(StarComputationUpgrades.matches("unknown", 1, mixed));
    }

    @Test
    void nestedEvaluationShowsTitlesOnlyAndKeepsProgressAndGuidanceInDetailsWithoutShortCircuiting() {
        var a = leaf("nodes", "all", 1, true, new Target("foundation", 1, "Configured title"));
        var b = leaf("research", "any", 1, true,
                new Target("kubejs:alpha", 2, ""), new Target("kubejs:missing", 1, ""));
        var c = leaf("machines", "at_least", 2, true,
                new Target("minecraft:furnace", 3, ""), new Target("#c:controllers", 1, ""));
        var root = group("all", 1, a, group("any", 1, b, group("at_least", 1, c)));
        var calls = new AtomicInteger();
        var evaluation = StarComputationUpgrades.evaluate(root, condition -> {
            calls.incrementAndGet();
            if (condition == a) return StarComputationUpgrades.leaf(condition, List.of(progress(true, 1, "Foundation")));
            if (condition == b) return StarComputationUpgrades.leaf(condition, List.of(
                    progress(true, 2, "Server Research Alpha"), progress(false, 99, "Unavailable research")));
            return StarComputationUpgrades.leaf(condition, List.of(
                    progress(true, 2, "Furnace"), progress(true, 1, "Controller tag: Actual machine names")));
        });
        assertTrue(evaluation.passed());
        assertEquals(3, calls.get(), "any/all branches cannot hide target failures from the UI");
        assertEquals(List.of(0, 1, 2, 1, 2, 3, 3, 2, 3, 4, 4),
                evaluation.rows().stream().map(StarComputationUpgradeCatalog.Row::depth).toList());
        assertFalse(evaluation.rows().get(6).met(), "Unavailable targets never count, even with a stale count of 99");
        assertFalse(evaluation.rows().get(8).met(), "Two machines do not satisfy a target count of three");
        var serializedText = evaluation.rows().toString();
        assertTrue(serializedText.contains("Server Research Alpha"));
        assertFalse(serializedText.contains("kubejs:missing"));
        assertFalse(serializedText.contains("minecraft:furnace"));
        assertFalse(serializedText.contains("#c:controllers"));
        assertFalse(evaluation.rows().get(2).text().getString().contains("Foundation"), "A configured title replaces the list name");
        assertTrue(evaluation.rows().get(2).details().contains(StarComputationUpgrades.text(
                "detail.nodes", Component.literal("Foundation"), 1, 1)),
                "Completion instructions must retain the real in-game name when the list title is customized");
        assertTrue(serializedText.contains("Configured title"));
        assertTrue(serializedText.contains("Actual machine names"));
        assertTrue(serializedText.contains("unavailable"));
        assertEquals(Component.translatable("Configured title"), evaluation.rows().get(2).text());
        assertEquals(Component.literal("Server Research Alpha"), evaluation.rows().get(5).text());
        assertEquals(StarComputationUpgrades.text("row.research"), evaluation.rows().get(4).text());
        assertTrue(evaluation.rows().get(5).details().toString().contains("detail.research"));
        assertTrue(evaluation.rows().get(6).details().toString().contains("research_unavailable"));
        assertTrue(evaluation.rows().get(4).details().toString().contains("research_same"));
        for (var row : evaluation.rows()) assertFalse(row.details().isEmpty());
    }

    @Test
    void leafFailureAndMatchingInstructionsStayInTooltipInsteadOfAddingLongRows() {
        var target = new Target("molecularmanipulator:research/sequence_array", 2, "");
        var condition = leaf("research", "all", 1, true, target);
        var reason = StarComputationUpgrades.text("no_research_machine");
        var evaluation = StarComputationUpgrades.evaluate(condition, ignored ->
                new StarComputationUpgrades.LeafResult(false, List.of(progress(true, 0, "Sequence Array")), reason));
        assertEquals(2, evaluation.rows().size());
        assertEquals(StarComputationUpgrades.text("row.research"), evaluation.rows().getFirst().text());
        assertTrue(evaluation.rows().getFirst().details().contains(reason));
        assertTrue(evaluation.rows().get(1).details().contains(reason), "Hovering the target must explain the missing research well directly");
        assertEquals(Component.literal("Sequence Array"), evaluation.rows().get(1).text());
        assertFalse(evaluation.rows().toString().contains(target.id()));
        assertFalse(evaluation.passed());
    }

    @Test
    void conditionAndTargetTitlesOverrideDefaultsWhileTagOptionsStayInTooltip() {
        var condition = new Condition("machines", List.of(), List.of(new Target("#c:controllers", 2, "Connect two machines")),
                "all", 1, true, true, "Prepare machines");
        var memberNames = StarComputationUpgrades.text("block_tag_members", Component.literal("Furnace, Blast Furnace"));
        var progress = new TargetProgress(true, 1, StarComputationUpgrades.text("row.machine_options"),
                StarComputationUpgrades.text("incomplete"), List.of(memberNames));
        var evaluation = StarComputationUpgrades.evaluate(condition, ignored -> StarComputationUpgrades.leaf(condition, List.of(progress)));
        assertEquals(Component.translatable("Prepare machines"), evaluation.rows().getFirst().text());
        assertEquals(Component.translatable("Connect two machines"), evaluation.rows().get(1).text());
        assertTrue(evaluation.rows().get(1).details().contains(memberNames));
        assertEquals(StarComputationUpgrades.text("detail.machines", StarComputationUpgrades.text("row.machine_options"), 1, 2),
                evaluation.rows().get(1).details().getFirst(), "Custom checklist titles must not replace the actual machine name in instructions");
        assertFalse(evaluation.rows().toString().contains("#c:controllers"));
        assertEquals(progress.details(), progress.withCount(3).details());
        assertFalse(evaluation.passed());

        var research = leaf("research", "all", 1, true, new Target("kubejs:sequence_array", 2, "First preparation"));
        var researchEvaluation = StarComputationUpgrades.evaluate(research, ignored ->
                StarComputationUpgrades.leaf(research, List.of(progress(true, 1, "Sequence Array"))));
        assertEquals(Component.translatable("First preparation"), researchEvaluation.rows().get(1).text());
        assertEquals(StarComputationUpgrades.text("detail.research", Component.literal("Sequence Array"), 1, 2),
                researchEvaluation.rows().get(1).details().getFirst(), "The tooltip must tell players which research to look for");
        assertFalse(researchEvaluation.rows().toString().contains("kubejs:sequence_array"));
    }

    @Test
    void invalidDefinitionsShowHelpfulMessageWithoutLeakingTechnicalErrorOrId() {
        var invalid = leaf("research", "all", 1, true, new Target("bad research ID", 1, ""));
        var evaluation = StarComputationUpgrades.evaluate(invalid, ignored -> { fail("Must validate first"); return null; });
        assertEquals(StarComputationUpgrades.text("row.invalid"), evaluation.rows().getFirst().text());
        assertEquals(List.of(StarComputationUpgrades.text("detail.invalid")), evaluation.rows().getFirst().details());
        assertFalse(evaluation.rows().toString().contains("bad research ID"));
        assertFalse(evaluation.passed());
    }

    @Test
    void everyLeafUsesItsOwnTargetMatchIncludingUnavailableAndBooleanCountTargets() {
        var targets = new Target[] {new Target("foundation", 1, ""), new Target("quantum", 2, ""), new Target("unknown", 1, "")};
        var states = List.of(progress(true, 1, "Foundation"), progress(true, 1, "Quantum"), progress(false, 100, "Unknown"));
        assertFalse(StarComputationUpgrades.leaf(leaf("nodes", "all", 1, true, targets), states).passed());
        assertTrue(StarComputationUpgrades.leaf(leaf("nodes", "any", 1, true, targets), states).passed());
        assertFalse(StarComputationUpgrades.leaf(leaf("nodes", "at_least", 2, true, targets), states).passed());
        assertTrue(StarComputationUpgrades.leaf(leaf("nodes", "at_least", 1, true, targets), states).passed());
    }

    @Test
    void nodeTargetsUseOnlyTheSuppliedOwningCoreMaskAndUnknownNodesRemainUnavailable() {
        for (var node : StarComputationRecipes.NODES) {
            var target = new Target(node.id(), 1, "");
            assertTrue(StarComputationUpgrades.node(target, 1 << node.index()).met(target));
            assertFalse(StarComputationUpgrades.node(target, StarComputationRecipes.ALL_UNLOCKS & ~(1 << node.index())).met(target));
            assertFalse(StarComputationUpgrades.node(target, 0).met(target));
            assertFalse(StarComputationUpgrades.node(new Target(node.id(), 2, ""), StarComputationRecipes.ALL_UNLOCKS)
                    .met(new Target(node.id(), 2, "")), "A boolean unlock cannot satisfy a count of two");
        }
        var unknown = new Target("unknown", 1, "");
        var result = StarComputationUpgrades.node(unknown, StarComputationRecipes.ALL_UNLOCKS);
        assertFalse(result.available());
        assertFalse(result.met(unknown));
        assertTrue(result.reason().toString().contains("unknown"));
    }

    @Test
    void sameWellRequiresOneWitnessWhileCrossWellUsesIndependentMaximaNeverAddedCounts() {
        var targets = new Target[] {new Target("kubejs:alpha", 2, ""), new Target("kubejs:beta", 2, "")};
        var definitions = List.of(progress(true, 0, "Alpha actual research title"), progress(true, 0, "Beta actual title"));
        var split = List.of(List.of(2, 0), List.of(0, 2));
        var same = leaf("research", "all", 1, true, targets);
        var cross = leaf("research", "all", 1, false, targets);
        var sameResult = StarComputationUpgrades.research(same, definitions, split);
        assertFalse(sameResult.passed());
        assertNotNull(sameResult.reason());
        assertEquals(List.of(2, 0), sameResult.targets().stream().map(TargetProgress::count).toList(),
                "Failure details must show one coherent candidate, not a fabricated per-target union");
        var crossResult = StarComputationUpgrades.research(cross, definitions, split);
        assertTrue(crossResult.passed());
        assertEquals(List.of(2, 2), crossResult.targets().stream().map(TargetProgress::count).toList());
        var partial = List.of(List.of(1, 1), List.of(1, 1), List.of(1, 1));
        assertFalse(StarComputationUpgrades.research(cross, definitions, partial).passed(),
                "Three wells with one round each must not satisfy two rounds");
        var witness = List.of(List.of(2, 0), List.of(0, 2), List.of(2, 2));
        assertTrue(StarComputationUpgrades.research(same, definitions, witness).passed());
    }

    @Test
    void sameWellRespectsAnyAndThresholdInsteadOfAlwaysRequiringAll() {
        var targets = new Target[] {new Target("kubejs:a", 2, ""), new Target("kubejs:b", 2, ""), new Target("kubejs:c", 2, "")};
        var definitions = List.of(progress(true, 0, "A"), progress(true, 0, "B"), progress(true, 0, "C"));
        var split = List.of(List.of(2, 0, 0), List.of(0, 2, 0), List.of(0, 0, 2));
        assertTrue(StarComputationUpgrades.research(leaf("research", "any", 1, true, targets), definitions, split).passed());
        assertFalse(StarComputationUpgrades.research(leaf("research", "at_least", 2, true, targets), definitions, split).passed());
        assertTrue(StarComputationUpgrades.research(leaf("research", "at_least", 2, false, targets), definitions, split).passed());
        assertTrue(StarComputationUpgrades.research(leaf("research", "at_least", 2, true, targets), definitions,
                List.of(List.of(2, 2, 0))).passed());
    }

    @Test
    void removedResearchDefinitionCannotPassUsingOldCompletionCountsAndNoWellsNeverPass() {
        var condition = leaf("research", "all", 1, false, new Target("kubejs:removed", 1, ""));
        var unavailable = List.of(progress(false, 0, "Removed definition"));
        var result = StarComputationUpgrades.research(condition, unavailable, List.of(List.of(50), List.of(50)));
        assertFalse(result.passed());
        assertFalse(result.targets().getFirst().met(condition.targets().getFirst()));
        for (boolean same : List.of(true, false)) {
            result = StarComputationUpgrades.research(leaf("research", "any", 1, same,
                    new Target("kubejs:a", 1, "")), List.of(progress(true, 0, "A")), List.of());
            assertFalse(result.passed());
            assertNotNull(result.reason());
        }
    }

    @Test
    void machineCountingUsesRealGridIdentityLoadedActivePrimaryOwnersFormationAndBlockTags() {
        // Equal-but-distinct grids must not be confused with real IGrid identity (e.g. a shared-storage facade).
        Object grid = new String("grid"); Object otherGrid = new String("grid");
        var a = new Object(); var b = new Object();
        var furnace = ResourceLocation.parse("minecraft:furnace");
        var blast = ResourceLocation.parse("minecraft:blast_furnace");
        var tag = ResourceLocation.parse("c:controllers");
        var machines = List.of(
                machine(a, grid, true, true, true, true, furnace, tag),
                machine(a, grid, true, true, true, true, furnace, tag), // Two exposed nodes, one real owner.
                machine(b, grid, true, true, true, false, furnace, tag),
                machine(new Object(), grid, true, true, true, true, blast, tag),
                machine(new Object(), otherGrid, true, true, true, true, furnace, tag),
                machine(new Object(), grid, false, true, true, true, furnace, tag),
                machine(new Object(), grid, true, false, true, true, furnace, tag),
                machine(new Object(), grid, true, true, false, true, furnace, tag)); // A proxy part is not a machine.
        assertEquals(1, StarComputationUpgrades.machineCount(grid, machines, "minecraft:furnace", true));
        assertEquals(2, StarComputationUpgrades.machineCount(grid, machines, "minecraft:furnace", false));
        assertEquals(2, StarComputationUpgrades.machineCount(grid, machines, "#c:controllers", true));
        assertEquals(3, StarComputationUpgrades.machineCount(grid, machines, "#c:controllers", false));
        assertEquals(0, StarComputationUpgrades.machineCount(grid, machines, "missing:machine", false));
        assertEquals(0, StarComputationUpgrades.machineCount(grid, machines, "#missing:tag", false));
        assertEquals(0, StarComputationUpgrades.machineCount(null, machines, "minecraft:furnace", false));
    }

    @Test
    void ruleQueriesTrackSameManagerSameIdsAndInPlaceReloadsConflictsDeletionAndManagerReplacement() {
        var manager = new ReloadableRecipes();
        var first = recipe(4, 1024);
        var id = ResourceLocation.parse("kubejs:level4");
        manager.replaceRecipes(List.of(new RecipeHolder<>(id, first)));
        assertEquals(new StarComputationUpgrades.Rule(id, first), StarComputationUpgrades.rule(manager, 4));
        var replacement = recipe(4, 8192);
        manager.replaceRecipes(List.of(new RecipeHolder<>(id, replacement)));
        assertSame(replacement, StarComputationUpgrades.rule(manager, 4).recipe());
        manager.holders.add(new RecipeHolder<>(ResourceLocation.parse("other:level4"), first));
        assertNull(StarComputationUpgrades.rule(manager, 4), "Conflicts fail closed, not first/last/random wins");
        manager.holders.removeLast();
        assertSame(replacement, StarComputationUpgrades.rule(manager, 4).recipe());
        manager.replaceRecipes(List.of());
        assertNull(StarComputationUpgrades.rule(manager, 4), "Deleted rules cannot fall back to an old definition");
        assertNull(StarComputationUpgrades.rule(new ReloadableRecipes(), 4));
        assertNull(StarComputationUpgrades.rule(manager, 0));
        assertNull(StarComputationUpgrades.rule(manager, 10));
    }

    @Test
    void invalidProgrammaticRulesFailClosedWithoutCallingTargetResolver() {
        var invalid = leaf("nodes", "typo", 1, true, new Target("foundation", 1, ""));
        assertFalse(StarComputationUpgrades.evaluate(invalid, ignored -> { fail("Must validate first"); return null; }).passed());
        var manager = new ReloadableRecipes();
        manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("kubejs:bad"),
                new StarComputationUpgradeRecipe(4, 0, 0, invalid))));
        assertNull(StarComputationUpgrades.rule(manager, 4));
    }

    static StarComputationUpgradeRecipe recipe(int level, double energy) {
        return new StarComputationUpgradeRecipe(level, energy, 256,
                leaf("nodes", "all", 1, true, new Target("foundation", 1, "")));
    }

    static Condition leaf(String type, String match, int required, boolean same, Target... targets) {
        return new Condition(type, List.of(), List.of(targets), match, required, same, true, "");
    }

    private static Condition group(String type, int required, Condition... children) {
        return new Condition(type, List.of(children), List.of(), "all", required, true, true, "");
    }

    private static TargetProgress progress(boolean available, int count, String name) {
        return new TargetProgress(available, count, Component.literal(name),
                StarComputationUpgrades.text(available ? "incomplete" : "research_unavailable"));
    }

    private static MachineSnapshot machine(Object owner, Object grid, boolean loaded, boolean active,
                                            boolean primary, boolean formed, ResourceLocation block, ResourceLocation tag) {
        return new MachineSnapshot(owner, grid, loaded, active, primary, formed, block, Set.of(tag));
    }

    /** Exercise real query code while avoiding NeoForge registry registration, which is not run in plain JUnit. */
    static final class ReloadableRecipes extends RecipeManager {
        final List<RecipeHolder<?>> holders = new ArrayList<>();
        ReloadableRecipes() { super(RegistryAccess.EMPTY); }
        @Override public Collection<RecipeHolder<?>> getRecipes() { return holders; }
        @Override public void replaceRecipes(Iterable<RecipeHolder<?>> recipes) {
            holders.clear(); recipes.forEach(holders::add);
        }
    }
}
