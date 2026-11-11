package com.atir.molecularmanipulator.blockentity;

import static com.atir.molecularmanipulator.blockentity.RecipeLockGameTests.id;
import static com.atir.molecularmanipulator.blockentity.RecipeLockGameTests.lock;
import static com.atir.molecularmanipulator.blockentity.RecipeLockGameTests.node;
import static com.atir.molecularmanipulator.blockentity.RecipeLockGameTests.ordinary;
import static com.atir.molecularmanipulator.blockentity.RecipeLockGameTests.replaceAndNotify;
import static com.atir.molecularmanipulator.blockentity.RecipeLockGameTests.team;
import static com.atir.molecularmanipulator.blockentity.RecipeLockGameTests.withTemporaryRecipes;
import static java.util.stream.Collectors.toSet;

import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.MatterMachineImportRecipe;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.crafting.PatternRecipeReloads;
import com.atir.molecularmanipulator.recipelock.RecipeLockRecipe;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.atir.molecularmanipulator.recipelock.RecipeMachineOwnership;
import com.atir.molecularmanipulator.starcomputation.StarResearchNodeRecipe;
import com.atir.molecularmanipulator.starcomputation.StarResearchNodes;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Real codec declarations and MatterRecipeBridge/MatterRecipeIndex holders, never synthetic fabrication stand-ins.
 * Every catalogue/progress swap is synchronous and uses withTemporaryRecipes' finally restoration.
 */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class RecipeLockImportedGameTests {
    private static final ResourceLocation NODE_A = id("imported/research_a");
    private static final ResourceLocation NODE_B = id("imported/research_b");
    private static final String MATTER_TYPE = "molecularmanipulator:matter_fabrication";
    private static final String IMPORT_REGEX = "molecularmanipulator:fabrication/import/machine/.*";

    @GameTest(template = ServerRegressionFixture.TEMPLATE, batch = "recipe_lock_imported_source")
    public static void exactSourceLockCannotBeBypassedByTheBridgeInternalId(GameTestHelper helper) {
        try (var fixture = new ServerRegressionFixture(helper)) {
            var machine = fixture.place(fixture.origin, Blocks.CRAFTER.defaultBlockState(), CrafterBlockEntity.class);
            var owner = bind(fixture, machine, "import-source");
            var source = source(fixture, "exact", Items.BARRIER, Items.AMETHYST_SHARD);
            var mapping = machineImport(fixture, "exact", 7);
            var sourceLock = lock(fixture, "imported/exact_lock", source.id(), NODE_A);
            withTemporaryRecipes(fixture, List.of(source, mapping, node(NODE_A), sourceLock), data -> {
                var imported = imported(fixture, source);
                helper.assertTrue(!RecipeLocks.allowed(machine, source.id()) && !RecipeLocks.allowed(machine, source)
                                && !RecipeLocks.allowed(machine, source.value()),
                        "The authoritative source holder and exact source ID are locked before the owning team's grant");
                // The ID-only overload has no source argument. It must stay default-open for this
                // unselected internal ID; execution uses a holder/value or checks BOTH IDs.
                assertAdmission(fixture, machine, imported, true, false);
                helper.assertTrue(data.getUnlockedNodes(owner).isEmpty(), "Bridge admission does not grant permanent research");
                helper.assertTrue(data.addUnlock(owner, NODE_A), "Grant only the disposable progress object's source research");
                helper.assertTrue(RecipeLocks.allowed(machine, source.id()) && RecipeLocks.allowed(machine, source),
                        "The same source becomes executable immediately after its permanent team grant");
                assertAdmission(fixture, machine, imported, true, true);
            });
        }
        helper.succeed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, batch = "recipe_lock_imported_type")
    public static void fabricationTypeLockCoversBridgeHoldersEvenAfterImportDeclarationRename(GameTestHelper helper) {
        exerciseInternalSelector(helper, "type", "recipe_types", MATTER_TYPE);
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, batch = "recipe_lock_imported_namespace")
    public static void namespaceLockCoversBridgeHoldersEvenAfterImportDeclarationRename(GameTestHelper helper) {
        exerciseInternalSelector(helper, "namespace", "namespaces", "molecularmanipulator");
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, batch = "recipe_lock_imported_regex")
    public static void regexLockCoversBridgeHoldersEvenAfterImportDeclarationRename(GameTestHelper helper) {
        exerciseInternalSelector(helper, "regex", "recipe_id_regex", IMPORT_REGEX);
    }

    private static void exerciseInternalSelector(GameTestHelper helper, String name, String selector, String selected) {
        try (var fixture = new ServerRegressionFixture(helper)) {
            var machine = fixture.place(fixture.origin, Blocks.CRAFTER.defaultBlockState(), CrafterBlockEntity.class);
            var owner = bind(fixture, machine, "import-" + name);
            var source = source(fixture, name, Items.BARRIER, Items.AMETHYST_SHARD);
            var firstMapping = machineImport(fixture, name + "_before", 7);
            var renamedMapping = machineImport(fixture, name + "_renamed", 7);
            var rule = selectorLock(fixture, name + "_lock", selector, selected, NODE_A);
            withTemporaryRecipes(fixture, List.of(source, firstMapping, node(NODE_A), rule), data -> {
                var first = imported(fixture, source);
                helper.assertTrue(RecipeLocks.allowed(machine, source.id()) && RecipeLocks.allowed(machine, source)
                                && RecipeLocks.allowed(machine, source.value()),
                        "The ordinary source is outside the internal selector: a source denial cannot mask a missing bridge-ID rule");
                assertAdmission(fixture, machine, first, false, false);

                replaceAndNotify(fixture, List.of(source, renamedMapping, node(NODE_A), rule));
                var renamed = imported(fixture, source);
                helper.assertTrue(!first.id().equals(renamed.id())
                                && first.value().sourceRecipeId().equals(renamed.value().sourceRecipeId()),
                        "Renaming only the real import declaration generates a different internal ID for the identical source");
                helper.assertTrue(RecipeLocks.allowed(machine, source.id()), "The unchanged source is still default-open");
                assertAdmission(fixture, machine, renamed, false, false);
                helper.assertTrue(data.addUnlock(owner, NODE_A), "Pay the internal selector's research without changing the source or mapping");
                assertAdmission(fixture, machine, renamed, true, true);
            });
        }
        helper.succeed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, batch = "recipe_lock_imported_same_generation")
    public static void sameManagerReplacementRebuildsLocksResearchAndBridgeWithoutNotification(GameTestHelper helper) {
        try (var fixture = new ServerRegressionFixture(helper)) {
            var machine = fixture.place(fixture.origin, Blocks.CRAFTER.defaultBlockState(), CrafterBlockEntity.class);
            var owner = bind(fixture, machine, "import-reload");
            var firstSource = source(fixture, "same_generation", Items.BARRIER, Items.IRON_NUGGET);
            var nextSource = source(fixture, "same_generation", Items.STRUCTURE_VOID, Items.IRON_NUGGET);
            var firstMapping = machineImport(fixture, "same_generation", 7);
            var nextMapping = machineImport(fixture, "same_generation", 73);
            var nodeA = node(NODE_A);
            var firstNodeB = node(NODE_B);
            var nextNodeB = new RecipeHolder<>(NODE_B, new StarResearchNodeRecipe("Replacement research B", 6, 17, List.of()));
            var invalidNodeB = new RecipeHolder<>(NODE_B, new StarResearchNodeRecipe("Missing parent", 6, 17,
                    List.of(id("imported/missing_parent"))));
            var ruleA = selectorLock(fixture, "same_generation_lock", "recipe_types", MATTER_TYPE, NODE_A);
            var ruleB = selectorLock(fixture, "same_generation_lock", "recipe_types", MATTER_TYPE, NODE_B);
            withTemporaryRecipes(fixture, List.of(firstSource, firstMapping, nodeA, firstNodeB, ruleA), data -> {
                var manager = fixture.level.getRecipeManager();
                long generation = PatternRecipeReloads.generation();
                var firstView = manager.getRecipes();
                var firstIndex = MatterRecipeIndex.get(fixture.level);
                var firstCatalog = StarResearchNodes.catalog(fixture.level);
                var first = imported(fixture, firstSource);
                helper.assertTrue(firstCatalog == StarResearchNodes.catalog(fixture.level)
                                && firstIndex == MatterRecipeIndex.get(fixture.level),
                        "Warm both real definition caches on an unchanged manager values view");
                helper.assertTrue(data.addUnlock(owner, NODE_A), "Warm a positive lock decision using only research A");
                assertAdmission(fixture, machine, first, true, true);

                // Deliberately NO replaceAndNotify, sync event, cache reflection or generation change.
                // The count and every registered holder ID are identical; only their values change.
                manager.replaceRecipes(List.of(nextSource, nextMapping, nodeA, nextNodeB, ruleB));
                helper.assertTrue(manager == fixture.level.getRecipeManager() && generation == PatternRecipeReloads.generation()
                                && firstView != manager.getRecipes() && firstView.size() == manager.getRecipes().size()
                                && firstView.stream().map(RecipeHolder::id).collect(toSet())
                                        .equals(manager.getRecipes().stream().map(RecipeHolder::id).collect(toSet())),
                        "A native same-manager replaceRecipes replaces its collection view without changing generation, IDs or count");
                var nextIndex = MatterRecipeIndex.get(fixture.level);
                var nextCatalog = StarResearchNodes.catalog(fixture.level);
                var next = imported(fixture, nextSource);
                helper.assertTrue(nextIndex != firstIndex && nextCatalog != firstCatalog && first.id().equals(next.id())
                                && first.value() != next.value() && next.value().processingTime() == 73
                                && next.value().ingredients().getFirst().ingredient().test(new ItemStack(Items.STRUCTURE_VOID))
                                && !next.value().ingredients().getFirst().ingredient().test(new ItemStack(Items.BARRIER)),
                        "The bridge rebuilds a new real value under the SAME internal ID with the replacement mapping and ingredients");
                helper.assertTrue(firstCatalog.nodes().get(NODE_B) == firstNodeB.value()
                                && nextCatalog.nodes().get(NODE_B) == nextNodeB.value()
                                && !nextCatalog.canUnlock(NODE_B, 5, data.getUnlockedNodes(owner))
                                && nextCatalog.canUnlock(NODE_B, 6, data.getUnlockedNodes(owner)),
                        "The directory replaces B's title/level/price while its previously published snapshot stays unchanged");
                assertAdmission(fixture, machine, next, false, false);
                helper.assertTrue(data.isUnlocked(owner, NODE_A) && !data.isUnlocked(owner, NODE_B),
                        "The same imported ID now requires B, not the retained positive A grant");
                helper.assertTrue(data.addUnlock(owner, NODE_B), "Grant the new lock's separate permanent research");
                assertAdmission(fixture, machine, next, true, true);
                var savedProgress = data.save(new CompoundTag(), fixture.level.registryAccess());
                data.setDirty(false);

                // Change ONLY a research definition, keeping source, import declaration and lock unchanged.
                manager.replaceRecipes(List.of(nextSource, nextMapping, nodeA, invalidNodeB, ruleB));
                var invalidCatalog = StarResearchNodes.catalog(fixture.level);
                var invalid = imported(fixture, nextSource);
                helper.assertTrue(invalidCatalog != nextCatalog && invalidCatalog.error(NODE_B) != null
                                && nextCatalog.error(NODE_B) == null && data.isUnlocked(owner, NODE_B),
                        "An invalid replacement research graph is live even when B remains in permanent progress");
                assertAdmission(fixture, machine, invalid, false, false);

                manager.replaceRecipes(List.of(nextSource, nextMapping, nodeA, nextNodeB, ruleB));
                helper.assertTrue(StarResearchNodes.validationError(fixture.level, NODE_B) == null,
                        "Repairing the graph also invalidates the directory in this same generation");
                assertAdmission(fixture, machine, imported(fixture, nextSource), true, true);

                manager.replaceRecipes(List.of(nextSource, nextMapping, nodeA, ruleB));
                helper.assertTrue(!StarResearchNodes.nodes(fixture.level).containsKey(NODE_B) && data.isUnlocked(owner, NODE_B),
                        "Removing B's definition cannot turn its retained saved grant into valid authorization");
                assertAdmission(fixture, machine, imported(fixture, nextSource), false, false);

                manager.replaceRecipes(List.of(nextSource, nextMapping, nodeA));
                helper.assertTrue(!RecipeLocks.hasRules(fixture.level), "Removing the declaration clears the cached rule without any notification");
                assertAdmission(fixture, machine, imported(fixture, nextSource), true, true);
                helper.assertTrue(generation == PatternRecipeReloads.generation()
                                && savedProgress.equals(data.save(new CompoundTag(), fixture.level.registryAccess())) && !data.isDirty(),
                        "All replacement phases keep generation constant and only read the disposable team's saved progress");
            });
        }
        helper.succeed();
    }

    private static UUID bind(ServerRegressionFixture fixture, CrafterBlockEntity machine, String name) {
        var player = fixture.player(name, machine.getBlockPos());
        var owner = team(fixture.helper, player);
        fixture.helper.assertTrue(RecipeMachineOwnership.bind(machine, player)
                        && owner.equals(RecipeMachineOwnership.team(machine)),
                "Bind an actual disposable vanilla Crafter through the production ownership entry point");
        return owner;
    }

    private static RecipeHolder<CraftingRecipe> source(ServerRegressionFixture fixture, String name, Item input, Item output) {
        // Deliberately not molecularmanipulator: namespace rules on the internal ID must
        // be tested independently of sourceAllowed, which could otherwise conceal the bug.
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("recipe_lock_imported_source", name),
                ordinary(fixture, "imported/" + name, input, output).value());
    }

    private static RecipeHolder<MatterMachineImportRecipe> machineImport(ServerRegressionFixture fixture, String name, int time) {
        var json = JsonParser.parseString("""
                {
                  "type": "molecularmanipulator:matter_machine_import",
                  "machine": "minecraft:crafter",
                  "serializers": ["minecraft:crafting_shapeless"],
                  "research": {"title": "Imported lock regression", "ingredients": [], "duration": 1, "ae_per_tick": 0},
                  "inputs": {"items": ["/ingredients"]},
                  "outputs": {"items": ["/result"]},
                  "ae_per_tick": 0
                }
                """).getAsJsonObject();
        json.addProperty("processing_time", time);
        return new RecipeHolder<>(id("imported/mapping/" + name), (MatterMachineImportRecipe) decode(fixture, json));
    }

    private static RecipeHolder<RecipeLockRecipe> selectorLock(ServerRegressionFixture fixture, String name,
            String field, String value, ResourceLocation required) {
        var json = new JsonObject();
        json.addProperty("type", "molecularmanipulator:recipe_lock");
        var requires = new JsonArray();
        requires.add(required.toString());
        json.add("requires", requires);
        var match = new JsonObject();
        var values = new JsonArray();
        values.add(value);
        match.add(field, values);
        json.add("match", match);
        return new RecipeHolder<>(id("imported/" + name), (RecipeLockRecipe) decode(fixture, json));
    }

    private static Recipe<?> decode(ServerRegressionFixture fixture, JsonObject json) {
        return Recipe.CODEC.parse(fixture.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
    }

    private static RecipeHolder<MatterFabricationRecipe> imported(ServerRegressionFixture fixture, RecipeHolder<CraftingRecipe> source) {
        var index = MatterRecipeIndex.get(fixture.level);
        fixture.helper.assertTrue(index.fabrication().size() == 1, "The real bridge must import the sole ordinary source via the mapped declaration");
        var holder = index.fabrication().getFirst();
        fixture.helper.assertTrue(source.id().equals(holder.value().sourceRecipeId()) && !holder.id().equals(source.id())
                        && fixture.level.getRecipeManager().byKey(source.id()).orElseThrow().value() == source.value()
                        && fixture.level.getRecipeManager().byKey(holder.id()).isEmpty()
                        && fixture.level.getRecipeManager().getRecipes().stream().noneMatch(value -> value.value() == holder.value()),
                "The imported holder/value genuinely live outside RecipeManager and retain the exact registered source ID");
        fixture.helper.assertTrue(MATTER_TYPE.equals(BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()).toString())
                        && holder.value().ingredients().size() == 1 && holder.value().ingredients().getFirst().count() == 1
                        && holder.value().ingredients().getFirst().ingredient().test(source.value().getIngredients().getFirst().getItems()[0])
                        && ItemStack.matches(holder.value().getResultItem(fixture.level.registryAccess()),
                                source.value().getResultItem(fixture.level.registryAccess()))
                        && index.fabrication(holder.id()) == holder,
                "The real index resolves a mapped matter_fabrication recipe with the ordinary source's materials and output");
        return holder;
    }

    private static void assertAdmission(ServerRegressionFixture fixture, CrafterBlockEntity machine,
            RecipeHolder<MatterFabricationRecipe> imported, boolean internalAllowed, boolean executable) {
        fixture.helper.assertTrue(RecipeLocks.allowed(machine, imported.id()) == internalAllowed,
                "Internal-ID admission for " + imported.id() + " must be " + internalAllowed);
        fixture.helper.assertTrue(RecipeLocks.allowed(machine, imported) == executable,
                "Holder admission must check internal AND source rules for " + imported.id() + ": " + executable);
        fixture.helper.assertTrue(RecipeLocks.allowed(machine, imported.value()) == executable,
                "Value identity admission must check both internal selectors and the source ID: " + executable);
        fixture.helper.assertTrue((RecipeLocks.allowed(machine, imported.id())
                        && RecipeLocks.allowed(machine, imported.value().sourceRecipeId())) == executable,
                "The explicit internal-ID plus source-ID contract must agree with holder/value admission");
    }
}
