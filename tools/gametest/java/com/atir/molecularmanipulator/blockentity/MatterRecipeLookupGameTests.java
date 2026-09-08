package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.research.MatterResearchApi;
import com.atir.molecularmanipulator.research.MatterResearchRecipe;
import com.atir.molecularmanipulator.research.ResearchDepth;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MatterRecipeLookupGameTests {
    private static final int RECIPES = 128, RESEARCHES = 32, LOOKUPS = 2000;

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 400)
    public static void repeatedPatternLookup(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        var controller = new MatterFabricationBlockEntity(BlockPos.ZERO, ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState());
        controller.setLevel(level);
        var recipes = new ArrayList<Recipe<?>>();
        for (int i = 1; i <= RECIPES; i++) {
            var recipe = new MatterFabricationRecipe(List.of(new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.DIAMOND), 1)),
                    List.of(new ItemStack(Items.STONE, i)), FluidStack.EMPTY, FluidStack.EMPTY, 20, 0);
            recipes.add(recipe.withId(id("recipe_" + i)));
        }
        for (int i = 0; i < RESEARCHES; i++) {
            recipes.add(new MatterResearchRecipe("Verification research", List.of(), List.of(),
                    20, 0, List.of(id("unrelated_" + i)), List.of(), RESEARCHES - i, 1).withId(id("research_" + i)));
        }
        try {
            manager.replaceRecipes(recipes);
            var encoded = encodePattern(List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)),
                    List.of(new GenericStack(AEItemKey.of(Items.STONE), RECIPES)));
            var pattern = PatternDetailsHelper.decodePattern(encoded, level);
            Map<AEKey, Long> inputs = Map.of(AEItemKey.of(Items.DIAMOND), 1L);
            for (int i = 0; i < LOOKUPS; i++) MatterFabricationBatch.match(controller, pattern, inputs, 1);
            for (int round = 0; round < 3; round++) {
                long start = System.nanoTime();
                for (int i = 0; i < LOOKUPS; i++) {
                    var match = MatterFabricationBatch.match(controller, pattern, inputs, 1);
                    helper.assertTrue(match != null && match.id().equals(id("recipe_" + RECIPES)), "Repeated lookup must select the matching output");
                }
                System.out.println("MATTER_LOOKUP_BENCH: recipes=" + RECIPES + " research=" + RESEARCHES + " lookups=" + LOOKUPS
                        + " round=" + round + " elapsedMs=" + (System.nanoTime() - start) / 1_000_000.0);
            }
            helper.succeed();
        } finally { manager.replaceRecipes(original); }
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 400)
    public static void lookupTracksResearchAndRecipeReload(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var manager = level.getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        var state = ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState();
        var controller = new MatterFabricationBlockEntity(BlockPos.ZERO, state); controller.setLevel(level);
        var otherController = new MatterFabricationBlockEntity(BlockPos.ZERO, state); otherController.setLevel(level);
        var first = fabrication("first", Items.DIAMOND, Items.STONE, false);
        var second = fabrication("second", Items.DIAMOND, Items.STONE, false);
        var strict = fabrication("strict", Items.DIAMOND, Items.GOLD_INGOT, true);
        var ownerA = new MatterResearchRecipe("Owner A", List.of(), List.of(), 20, 0,
                List.of(first.id()), List.of(), 10, 1).withId(id("owner_a"));
        var ownerB = new MatterResearchRecipe("Owner B", List.of(), List.of(), 20, 0,
                List.of(first.id()), List.of(), 0, 1, List.of(new ResearchDepth(1, 8, 4, 0, Optional.empty()))).withId(id("owner_b"));
        var unavailable = new MatterResearchRecipe("Unavailable owner", List.of(), List.of(), 20, 0,
                List.of(second.id()), List.of("molecularmanipulator_nonexistent_test_mod"), -1, 1).withId(id("unavailable_owner"));
        var pattern = PatternDetailsHelper.decodePattern(encodePattern(
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)), List.of(new GenericStack(AEItemKey.of(Items.STONE), 1))), level);
        Map<AEKey, Long> supplied = Map.of(AEItemKey.of(Items.DIAMOND), 1L);
        try {
            manager.replaceRecipes(List.of(first, second, strict, ownerA, ownerB, unavailable));
            var index = MatterRecipeIndex.get(level);
            helper.assertTrue(MatterRecipeIndex.get(level) == index, "An unchanged recipe manager must reuse its index");
            helper.assertTrue(MatterResearchApi.definitions(level).equals(List.of(ownerB, ownerA)), "Research must keep display order and optional-mod filtering");
            helper.assertTrue(MatterFabricationBatch.match(controller, pattern, supplied, 1).id().equals(second.id()), "Locked earlier candidate must not hide an unlocked alternative");
            helper.assertTrue(!MatterResearchApi.canUseRecipe(controller, strict), "Explicit research requirement without an owner must stay locked");
            MatterResearchApi.setCompletionCount(controller, ownerA.id().toString(), 2);
            var expectedFirst = manager.getAllRecipesFor(ModContent.MATTER_FABRICATION_RECIPE_TYPE.get()).stream()
                    .filter(r -> r.id().equals(first.id()) || r.id().equals(second.id())).findFirst().orElseThrow();
            helper.assertTrue(MatterResearchApi.canUseRecipe(controller, first)
                    && MatterFabricationBatch.match(controller, pattern, supplied, 1).id().equals(expectedFirst.id()), "Unlock must immediately respect Forge's recipe-manager order");
            helper.assertTrue(MatterFabricationBatch.match(otherController, pattern, supplied, 1).id().equals(second.id()), "Controllers sharing an index must not share research permission");
            MatterResearchApi.setCompletionCount(controller, ownerB.id().toString(), 1);
            var profile = MatterResearchApi.productionProfile(controller, first);
            helper.assertTrue(profile.parallel() == 256 && profile.ticks() == 5, "Multiple owners must keep maximum parallelism and minimum duration");
            MatterResearchApi.setCompletionCount(controller, ownerA.id().toString(), 0);
            helper.assertTrue(MatterResearchApi.canUseRecipe(controller, first), "Any completed owner must grant access");
            MatterResearchApi.setCompletionCount(controller, ownerB.id().toString(), 0);
            helper.assertTrue(MatterFabricationBatch.match(controller, pattern, supplied, 1).id().equals(second.id()), "Revoked research must take effect without waiting for a tick");
            var stored = new CompoundTag(); var completions = new CompoundTag(); completions.putInt(ownerA.id().toString(), 1); stored.put("completions", completions);
            controller.getResearch().load(stored);
            helper.assertTrue(MatterResearchApi.canUseRecipe(controller, first), "Loaded research progress must be visible immediately");

            var changed = fabrication("first", Items.EMERALD, Items.STONE, false);
            manager.replaceRecipes(List.of(changed, second, strict, ownerA, ownerB, unavailable));
            helper.assertTrue(MatterRecipeIndex.get(level) != index, "Replacing identical recipe IDs and counts must invalidate the index");
            helper.assertTrue(MatterFabricationBatch.match(controller, pattern, supplied, 1).id().equals(second.id()), "Reloaded input requirements must replace the old ones");
            manager.replaceRecipes(List.of(second, first, strict, ownerA, ownerB, unavailable));
            var expectedReloaded = manager.getAllRecipesFor(ModContent.MATTER_FABRICATION_RECIPE_TYPE.get()).stream()
                    .filter(r -> r.id().equals(first.id()) || r.id().equals(second.id())).findFirst().orElseThrow();
            helper.assertTrue(MatterFabricationBatch.match(controller, pattern, supplied, 1).id().equals(expectedReloaded.id()), "Reloaded Forge candidate order must be preserved");
            helper.assertTrue(MatterRecipeIndex.get(level).candidates(Map.of(AEItemKey.of(Items.STONE), 2L)).isEmpty(), "Output quantity is part of the index key");
            helper.assertTrue(MatterRecipeIndex.get(level).candidates(Map.of(AEItemKey.of(Items.STONE), 1L, AEItemKey.of(Items.DIAMOND), 1L)).isEmpty(), "Extra pattern outputs must not be ignored");

            // Exercise the data-pack apply path too, not just replaceRecipes used by sync/KubeJS.
            var replacementOwner = new MatterResearchRecipe("Changed owner", List.of(), List.of(), 20, 0,
                    List.of(second.id()), List.of(), 10, 1).withId(ownerA.id());
            var data = new LinkedHashMap<ResourceLocation, JsonElement>();
            var ops = JsonOps.INSTANCE;
            for (var holder : List.of(first, second, strict, replacementOwner)) {
                var json = holder instanceof MatterFabricationRecipe fabrication
                        ? MatterFabricationRecipe.Serializer.CODEC.codec().encodeStart(ops, fabrication).getOrThrow(false, message -> {})
                        : MatterResearchRecipe.CODEC.codec().encodeStart(ops, (MatterResearchRecipe) holder).getOrThrow(false, message -> {});
                json.getAsJsonObject().addProperty("type", holder instanceof MatterFabricationRecipe
                        ? "molecularmanipulator:matter_fabrication" : "molecularmanipulator:matter_research");
                data.put(holder.getId(), json);
            }
            var apply = RecipeManager.class.getDeclaredMethod("apply", Map.class, ResourceManager.class, ProfilerFiller.class); apply.setAccessible(true);
            apply.invoke(manager, data, level.getServer().getResourceManager(), InactiveProfiler.INSTANCE);
            otherController.getResearch().load(new CompoundTag());
            helper.assertTrue(MatterResearchApi.canUseRecipe(otherController, first) && !MatterResearchApi.canUseRecipe(otherController, second),
                    "Data-pack reload must replace recipe-to-research ownership");
            helper.assertTrue(MatterResearchApi.definitions(level).size() == 1, "Removed research definitions must leave the cache");
            System.out.println("MATTER_LOOKUP_SEMANTICS_PASS: candidate order, alternatives, per-controller unlock/revoke, loaded progress, depth bonuses and both reload paths");
            helper.succeed();
        } finally { manager.replaceRecipes(original); }
    }

    private static MatterFabricationRecipe fabrication(String name, net.minecraft.world.item.Item input,
            net.minecraft.world.item.Item output, boolean requiresResearch) {
        return new MatterFabricationRecipe(
                List.of(new MatterFabricationRecipe.CountedIngredient(Ingredient.of(input), 1)),
                List.of(new ItemStack(output)), FluidStack.EMPTY, FluidStack.EMPTY, 20, 0, requiresResearch).withId(id(name));
    }

    private static ResourceLocation id(String path) { return new ResourceLocation("molecularmanipulator", "verification/" + path); }
    private static ItemStack encodePattern(List<GenericStack> inputs, List<GenericStack> outputs) {
        return PatternDetailsHelper.encodeProcessingPattern(inputs.toArray(GenericStack[]::new), outputs.toArray(GenericStack[]::new));
    }
}
