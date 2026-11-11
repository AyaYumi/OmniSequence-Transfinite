package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.crafting.PatternRecipeReloads;
import com.atir.molecularmanipulator.integration.FtbTeamOwnership;
import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLockRecipe;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.atir.molecularmanipulator.recipelock.RecipeMachineOwnership;
import com.atir.molecularmanipulator.starcomputation.StarComputationResearchData;
import com.atir.molecularmanipulator.starcomputation.StarResearchNodeRecipe;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native server lookups and actual vanilla Crafter execution, not source-text or mocked-manager checks. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class RecipeLockGameTests {
    static final ResourceLocation NODE_A = id("research_a");
    static final ResourceLocation NODE_B = id("research_b");
    private static final String DATA_NAME = "molecularmanipulator_star_computation_research";

    @GameTest(template = ServerRegressionFixture.TEMPLATE, batch = "recipe_lock_admission")
    public static void crafterDenialPreservesMaterialsAndTeamUnlocksNeverCrossOwners(GameTestHelper helper) {
        try (var fixture = new ServerRegressionFixture(helper)) {
            var first = fixture.place(fixture.origin, Blocks.CRAFTER.defaultBlockState(), CrafterBlockEntity.class);
            var second = fixture.place(fixture.origin.east(4), Blocks.CRAFTER.defaultBlockState(), CrafterBlockEntity.class);
            var firstOutput = fixture.place(first.getBlockPos().north(), Blocks.CHEST.defaultBlockState(), ChestBlockEntity.class);
            var secondOutput = fixture.place(second.getBlockPos().north(), Blocks.CHEST.defaultBlockState(), ChestBlockEntity.class);
            var playerA = fixture.player("lock-owner-a", first.getBlockPos());
            var playerB = fixture.player("lock-owner-b", second.getBlockPos());
            var teamA = team(helper, playerA);
            var teamB = team(helper, playerB);
            helper.assertTrue(!teamA.equals(teamB), "The two real regression players resolve to distinct teams");
            helper.assertTrue(RecipeMachineOwnership.bind(first, playerA) && RecipeMachineOwnership.bind(second, playerB),
                    "Bind both actual vanilla machines through the production first-bind entry point");
            helper.assertTrue(!RecipeMachineOwnership.bind(second, playerA) && teamB.equals(RecipeMachineOwnership.team(second)),
                    "Opening or explicitly rebinding an already-owned machine cannot replace its owning team");
            first.setItem(0, new ItemStack(Items.BARRIER, 3));
            second.setItem(0, new ItemStack(Items.BARRIER, 3));
            var recipe = ordinary(fixture, "admission", Items.BARRIER, Items.AMETHYST_SHARD);
            var lock = lock(fixture, "admission_lock", recipe.id(), NODE_A);

            withTemporaryRecipes(fixture, List.of(recipe, node(NODE_A), lock), data -> {
                var manager = fixture.level.getRecipeManager();
                helper.assertTrue(manager.byKey(recipe.id()).isPresent()
                                && manager.getAllRecipesFor(RecipeType.CRAFTING).size() == 1,
                        "Denied recipes remain visible in the shared catalogue");
                helper.assertTrue(CrafterBlock.getPotentialResults(fixture.level, first.asCraftInput()).isPresent(),
                        "Warm vanilla's ingredient-only static recipe cache before any owner is authorized");
                assertMatch(helper, fixture, first, false);
                assertMatch(helper, fixture, second, false);
                craft(fixture, first);
                craft(fixture, second);
                helper.assertTrue(first.getItem(0).getCount() == 3 && second.getItem(0).getCount() == 3
                                && firstOutput.isEmpty() && secondOutput.isEmpty(),
                        "Actual denied Crafter ticks neither consume materials nor emit output despite the warm static cache");
                helper.assertTrue(!RecipeExecutionContext.active(), "Native execution closes its invocation-local actor scope");

                helper.assertTrue(data.addUnlock(teamA, NODE_A), "Grant only team A permanent custom-ID progress");
                assertMatch(helper, fixture, first, true);
                assertMatch(helper, fixture, second, false);
                craft(fixture, first);
                craft(fixture, second);
                helper.assertTrue(first.getItem(0).getCount() == 2 && count(firstOutput, Items.AMETHYST_SHARD) == 1,
                        "The authorized native craft consumes exactly one input and inserts the ordinary recipe output");
                helper.assertTrue(second.getItem(0).getCount() == 3 && secondOutput.isEmpty()
                                && data.getUnlockedNodes(teamB).isEmpty(),
                        "Team B cannot reuse team A's positive result or gain permanent progress from the shared recipe cache");
                helper.assertTrue(!RecipeLocks.allowedForPlayer(playerB, recipe), "An unauthorized manual player stays denied too");
                helper.assertTrue(RecipeLocks.allowedForPlayer(playerA, recipe), "The owning player's permanent grant is live");
                try (var actorA = RecipeExecutionContext.open(playerA)) {
                    helper.assertTrue(manager.getRecipeFor(RecipeType.CRAFTING, first.asCraftInput(), fixture.level).isPresent(),
                            "The real player actor takes the permanent team A authorization path");
                    try (var actorB = RecipeExecutionContext.open(playerB)) {
                        helper.assertTrue(manager.getRecipeFor(RecipeType.CRAFTING, first.asCraftInput(), fixture.level).isEmpty(),
                                "A nested real player B actor does not inherit the outer player's grants");
                    }
                    helper.assertTrue(manager.getRecipeFor(RecipeType.CRAFTING, first.asCraftInput(), fixture.level).isPresent(),
                            "Closing the nested player scope restores the authorized caller rather than leaking B's denial");
                }
                helper.assertTrue(!RecipeExecutionContext.active(), "Closing player scopes leaves no actor for unrelated recipe lookups");

                helper.assertTrue(data.addUnlock(teamB, NODE_A), "A separate permanent team B unlock is required off-network");
                assertMatch(helper, fixture, second, true);
                craft(fixture, second);
                helper.assertTrue(second.getItem(0).getCount() == 2 && count(secondOutput, Items.AMETHYST_SHARD) == 1,
                        "The same previously-denied machine matches and executes immediately after its own team's unlock");
            });
        }
        helper.succeed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, batch = "recipe_lock_reload")
    public static void cachedLookupAndRecipeObjectReferencesRevalidateAfterSameManagerReload(GameTestHelper helper) {
        try (var fixture = new ServerRegressionFixture(helper)) {
            var machine = fixture.place(fixture.origin, Blocks.CRAFTER.defaultBlockState(), CrafterBlockEntity.class);
            var player = fixture.player("lock-reload", machine.getBlockPos());
            var owner = team(helper, player);
            helper.assertTrue(RecipeMachineOwnership.bind(machine, player), "Bind a real recipe executor");
            machine.setItem(0, new ItemStack(Items.STRUCTURE_VOID, 4));
            var original = ordinary(fixture, "reload", Items.STRUCTURE_VOID, Items.IRON_NUGGET);
            var initialLock = lock(fixture, "reload_lock", original.id(), NODE_A);
            withTemporaryRecipes(fixture, List.of(original, node(NODE_A), node(NODE_B), initialLock), data -> {
                var manager = fixture.level.getRecipeManager();
                var check = RecipeManager.<CraftingInput, CraftingRecipe>createCheck(RecipeType.CRAFTING);
                data.addUnlock(owner, NODE_A);
                helper.assertTrue(RecipeLocks.allowed(machine, original.value()), "The current recipe object's identity resolves");
                try (var scope = RecipeExecutionContext.open(machine)) {
                    helper.assertTrue(check.getRecipeFor(machine.asCraftInput(), fixture.level).orElseThrow().value() == original.value(),
                            "Warm the actual vanilla CachedCheck with an authorized recipe ID");
                }

                var replacement = ordinary(fixture, "reload", Items.STRUCTURE_VOID, Items.GOLD_NUGGET);
                var replacementLock = lock(fixture, "reload_lock", replacement.id(), NODE_B);
                replaceAndNotify(fixture, List.of(replacement, node(NODE_A), node(NODE_B), replacementLock));
                helper.assertTrue(manager == fixture.level.getRecipeManager(), "Reload reuses the same RecipeManager instance");
                helper.assertTrue(!RecipeLocks.allowed(machine, original.value()), "A retained pre-reload recipe reference fails closed");
                helper.assertTrue(!RecipeLocks.allowed(machine, replacement), "The new declaration requires the new research ID, not the old grant");
                try (var scope = RecipeExecutionContext.open(machine)) {
                    helper.assertTrue(check.getRecipeFor(machine.asCraftInput(), fixture.level).isEmpty(),
                            "CachedCheck's remembered ID is re-authorized against the new rule");
                    helper.assertTrue(manager.getRecipeFor(RecipeType.CRAFTING, machine.asCraftInput(), fixture.level, original).isEmpty(),
                            "The RecipeHolder fast path cannot reuse a previously authorized holder after a rule change");
                }
                helper.assertTrue(machine.getItem(0).getCount() == 4, "Lookup and rejection do not consume inventory");
                data.addUnlock(owner, NODE_B);
                try (var scope = RecipeExecutionContext.open(machine)) {
                    var selected = check.getRecipeFor(machine.asCraftInput(), fixture.level).orElseThrow();
                    helper.assertTrue(selected.value() == replacement.value()
                                    && selected.value().assemble(machine.asCraftInput(), fixture.level.registryAccess()).is(Items.GOLD_NUGGET),
                            "The same CachedCheck re-queries the current holder after a live grant; it cannot execute the old output");
                }
                helper.assertTrue(!RecipeLocks.allowed(machine, original.value()) && RecipeLocks.allowed(machine, replacement.value()),
                        "Only the current object identity is executable while locks exist");

                replaceAndNotify(fixture, List.of(replacement, node(NODE_A), replacementLock));
                helper.assertTrue(data.isUnlocked(owner, NODE_B) && !RecipeLocks.allowed(machine, replacement),
                        "Removing a research definition invalidates a rule even when its ID remains in permanent saved progress");
                try (var scope = RecipeExecutionContext.open(machine)) {
                    helper.assertTrue(check.getRecipeFor(machine.asCraftInput(), fixture.level).isEmpty(),
                            "A previously positive CachedCheck fails closed on an undefined research ID after reload");
                }
                replaceAndNotify(fixture, List.of(replacement));
                helper.assertTrue(!RecipeLocks.hasRules(fixture.level) && RecipeLocks.allowed(machine, replacement),
                        "Removing the declaration restores the default-open policy rather than retaining a stale lock");
                try (var scope = RecipeExecutionContext.open(machine)) {
                    helper.assertTrue(check.getRecipeFor(machine.asCraftInput(), fixture.level).orElseThrow().value() == replacement.value(),
                            "The same cached check recovers after lock removal");
                }
            });
        }
        helper.succeed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, batch = "recipe_lock_fallback")
    public static void aDeniedCachedFirstMatchDoesNotHideALaterOpenMatchingRecipe(GameTestHelper helper) {
        try (var fixture = new ServerRegressionFixture(helper)) {
            var machine = fixture.place(fixture.origin, Blocks.CRAFTER.defaultBlockState(), CrafterBlockEntity.class);
            var output = fixture.place(machine.getBlockPos().north(), Blocks.CHEST.defaultBlockState(), ChestBlockEntity.class);
            machine.setItem(0, new ItemStack(Items.JIGSAW, 2));
            var locked = ordinary(fixture, "first_locked", Items.JIGSAW, Items.DIAMOND);
            var open = ordinary(fixture, "later_open", Items.JIGSAW, Items.EMERALD);
            withTemporaryRecipes(fixture, List.of(locked, open, node(NODE_A), lock(fixture, "fallback_lock", locked.id(), NODE_A)), data -> {
                var manager = fixture.level.getRecipeManager();
                helper.assertTrue(RecipeMachineOwnership.team(machine) == null, "An unbound machine has no anonymous permanent team");
                var check = RecipeManager.<CraftingInput, CraftingRecipe>createCheck(RecipeType.CRAFTING);
                helper.assertTrue(check.getRecipeFor(machine.asCraftInput(), fixture.level).orElseThrow().id().equals(locked.id()),
                        "Outside execution context the shared catalogue still selects its first matching recipe");
                try (var scope = RecipeExecutionContext.open(machine)) {
                    helper.assertTrue(manager.getRecipeFor(RecipeType.CRAFTING, machine.asCraftInput(), fixture.level, locked)
                                    .orElseThrow().id().equals(open.id()),
                            "An explicitly retained locked holder falls through to the later open candidate");
                    helper.assertTrue(check.getRecipeFor(machine.asCraftInput(), fixture.level).orElseThrow().id().equals(open.id()),
                            "The actual remembered-ID CachedCheck also falls through rather than returning an empty result");
                }
                craft(fixture, machine);
                helper.assertTrue(machine.getItem(0).getCount() == 1 && count(output, Items.EMERALD) == 1
                                && count(output, Items.DIAMOND) == 0,
                        "The native Crafter executes only the later default-open recipe, never the locked first output");
            });
        }
        helper.succeed();
    }

    static RecipeHolder<CraftingRecipe> ordinary(ServerRegressionFixture fixture, String name, Item input, Item output) {
        var json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shapeless");
        var ingredients = new JsonArray();
        var ingredient = new JsonObject();
        ingredient.addProperty("item", BuiltInRegistries.ITEM.getKey(input).toString());
        ingredients.add(ingredient);
        json.add("ingredients", ingredients);
        var result = new JsonObject();
        result.addProperty("id", BuiltInRegistries.ITEM.getKey(output).toString());
        result.addProperty("count", 1);
        json.add("result", result);
        var decoded = decode(fixture, json);
        if (!(decoded instanceof CraftingRecipe crafting)) throw new IllegalStateException("Expected an ordinary crafting recipe");
        return new RecipeHolder<>(id(name), crafting);
    }

    static RecipeHolder<RecipeLockRecipe> lock(ServerRegressionFixture fixture, String name, ResourceLocation target,
                                               ResourceLocation... requires) {
        var json = new JsonObject();
        json.addProperty("type", "molecularmanipulator:recipe_lock");
        var required = new JsonArray();
        for (var node : requires) required.add(node.toString());
        json.add("requires", required);
        var match = new JsonObject();
        var ids = new JsonArray();
        ids.add(target.toString());
        match.add("recipe_ids", ids);
        json.add("match", match);
        var decoded = decode(fixture, json);
        if (!(decoded instanceof RecipeLockRecipe declaration)) throw new IllegalStateException("Recipe lock serializer was not registered");
        return new RecipeHolder<>(id(name), declaration);
    }

    private static Recipe<?> decode(ServerRegressionFixture fixture, JsonObject json) {
        return Recipe.CODEC.parse(fixture.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), json).getOrThrow();
    }

    static RecipeHolder<StarResearchNodeRecipe> node(ResourceLocation id) {
        return new RecipeHolder<>(id, new StarResearchNodeRecipe("Recipe lock regression", 1, 0, List.of()));
    }

    /** Replace real server recipes only within one synchronous server callback, always restoring even after assertion failure. */
    static void withTemporaryRecipes(ServerRegressionFixture fixture, List<RecipeHolder<?>> recipes,
                                     Consumer<StarComputationResearchData> assertions) {
        var manager = fixture.level.getRecipeManager();
        var originals = List.copyOf(manager.getRecipes());
        var storage = fixture.level.getServer().overworld().getDataStorage();
        var originalData = StarComputationResearchData.get(fixture.level);
        var data = new StarComputationResearchData();
        storage.set(DATA_NAME, data);
        try {
            replaceAndNotify(fixture, recipes);
            assertions.accept(data);
        } finally {
            try {
                manager.replaceRecipes(originals);
            } finally {
                storage.set(DATA_NAME, originalData);
                notifyReload(fixture);
            }
        }
    }

    static void replaceAndNotify(ServerRegressionFixture fixture, List<RecipeHolder<?>> recipes) {
        fixture.level.getRecipeManager().replaceRecipes(new ArrayList<>(recipes));
        notifyReload(fixture);
    }

    private static void notifyReload(ServerRegressionFixture fixture) {
        // replaceRecipes itself does not emit a completed-reload event. Exercise the real listener,
        // not private cache reflection; this models notification, not an asynchronous /reload command.
        PatternRecipeReloads.onDatapackSync(new OnDatapackSyncEvent(fixture.level.getServer().getPlayerList(), null));
    }

    static void assertMatch(GameTestHelper helper, ServerRegressionFixture fixture, CrafterBlockEntity machine, boolean expected) {
        try (var scope = RecipeExecutionContext.open(machine)) {
            helper.assertTrue(fixture.level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, machine.asCraftInput(), fixture.level).isPresent() == expected,
                    "RecipeManager admission for machine " + machine.getBlockPos() + " must be " + expected);
        }
    }

    static void craft(ServerRegressionFixture fixture, CrafterBlockEntity machine) {
        // Public BlockState tick dispatch reaches vanilla CrafterBlock.tick -> dispenseFrom and its actual mixins.
        machine.getBlockState().tick(fixture.level, machine.getBlockPos(), fixture.level.random);
    }

    static int count(Container container, Item item) {
        int result = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            var stack = container.getItem(slot);
            if (stack.is(item)) result += stack.getCount();
        }
        return result;
    }

    static UUID team(GameTestHelper helper, ServerPlayer player) {
        var team = FtbTeamOwnership.forPlayer(player);
        helper.assertTrue(team != null, "The server must resolve ownership for a real online regression player");
        return team;
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("molecularmanipulator", "recipe_lock_regression/" + path);
    }
}
