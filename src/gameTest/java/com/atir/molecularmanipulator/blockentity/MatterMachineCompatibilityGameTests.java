package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.atir.molecularmanipulator.crafting.ForgeMachineRecipeJson;
import com.atir.molecularmanipulator.crafting.MatterFabricationPatternEncoding;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.MatterRecipeBridge;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.research.MatterResearchApi;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Actual Forge serializers, loaded recipes, dynamic research, encoding and exact matching. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MatterMachineCompatibilityGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void loadedRecipesExposeEveryMachineResearchBranch(GameTestHelper helper) {
        var level = helper.getLevel();
        var index = MatterRecipeIndex.get(level);
        var imported = MatterRecipeBridge.importRecipes(level, level.getRecipeManager().getRecipes());
        var host = controller(helper);
        for (var key : List.of("ae2_inscriber", "ae2_charger", "eae_inscriber", "eae_charger",
                "eae_circuit_slicer", "aae_reaction_chamber")) {
            var recipes = imported.stream().filter(recipe -> recipe.machineKey().equals(key)).toList();
            helper.assertTrue(!recipes.isEmpty(), "Installed machine must import real recipes: " + key);
            var research = index.research(id("research/machine/" + key));
            helper.assertTrue(research != null && research.unlocks().size() == recipes.size()
                    && research.prerequisites().equals(List.of(id("research/ae_foundation")))
                    && research.ingredients().size() == 1 && research.ingredients().get(0).count() == 1,
                    "Research screen must expose the machine, its one-machine cost, prerequisites and all recipes: " + key);
            var machine = MatterRecipeBridge.machines().stream().filter(spec -> spec.key().equals(key)).findFirst().orElseThrow();
            helper.assertTrue(research.ingredients().get(0).ingredient().test(
                    new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.get(machine.machineItem()))),
                    "Research cost must use the actual Forge machine item: " + key);
            var recipe = recipes.get(0).holder();
            helper.assertTrue(!MatterResearchApi.canUseRecipe(host, recipe), "Machine recipe must remain research-gated: " + key);
            MatterResearchApi.setCompletionCount(host, research.id().toString(), 1);
            helper.assertTrue(MatterResearchApi.canUseRecipe(host, recipe), "Completing machine research must unlock its recipe: " + key);
            MatterResearchApi.setCompletionCount(host, research.id().toString(), 0);
            helper.assertTrue(!MatterResearchApi.canUseRecipe(host, recipe), "Revoking research must restore the gate: " + key);
        }
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void inscriberPreservesPressCatalystsAndAssemblyConsumption(GameTestHelper helper) {
        var source = parse("ae2:inscriber", "inscribe", """
                {"ingredients":{"middle":{"item":"minecraft:gold_ingot"},
                 "top":{"item":"ae2:logic_processor_press"},"bottom":{"item":"ae2:silicon_press"}},
                 "mode":"inscribe","result":{"item":"ae2:printed_logic_processor"}}
                """);
        var imports = MatterRecipeBridge.importRecipes(helper.getLevel(), List.of(source));
        helper.assertTrue(imports.size() == 2, "Base and Extended Inscriber must both expose their shared recipe");
        for (var imported : imports) {
            var recipe = imported.holder();
            helper.assertTrue(recipe.ingredients().size() == 1 && recipe.ingredients().get(0).count() == 1
                    && recipe.catalysts().size() == 2 && recipe.catalysts().stream().allMatch(value -> value.count() == 1),
                    "INSCRIBE consumes the middle slot and retains both optional presses as catalysts");
            var encoded = MatterFabricationPatternEncoding.encode(recipe, List.of(new net.minecraft.world.item.ItemStack(Items.GOLD_INGOT)));
            var pattern = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
            helper.assertTrue(pattern.getInputs().length == 1 && !controller(helper).hasCatalysts(recipe),
                    "Presses must not enter processing patterns and work must require the catalyst inventory");
        }
        var pressed = parse("ae2:inscriber", "press", """
                {"ingredients":{"middle":{"item":"minecraft:redstone"},
                 "top":{"item":"ae2:printed_logic_processor"},"bottom":{"item":"ae2:printed_silicon"}},
                 "mode":"press","result":{"item":"ae2:logic_processor"}}
                """);
        for (var imported : MatterRecipeBridge.importRecipes(helper.getLevel(), List.of(pressed))) {
            helper.assertTrue(imported.holder().ingredients().size() == 3 && imported.holder().catalysts().isEmpty(),
                    "PRESS consumes all processor assembly parts, not reusable presses");
        }
        assertNetworkCopy(helper, source);
        assertNetworkCopy(helper, pressed);
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void chargerConsumesExactlyOneInputForBothMachines(GameTestHelper helper) {
        var recipe = parse("ae2:charger", "charger", """
                {"ingredient":{"item":"ae2:certus_quartz_crystal"},
                 "result":{"item":"ae2:charged_certus_quartz_crystal"}}
                """);
        var imports = MatterRecipeBridge.importRecipes(helper.getLevel(), List.of(recipe));
        helper.assertTrue(imports.size() == 2 && imports.stream().allMatch(value ->
                value.holder().ingredients().size() == 1 && value.holder().ingredients().get(0).count() == 1),
                "AE2 and Extended Charger must share the recipe without double-counting ingredients");
        assertNetworkCopy(helper, recipe);
        assertMatchesAfterResearch(helper, recipe, "ae2_charger");
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void circuitSlicerRetainsCountsFluidsAndConditionalRecipes(GameTestHelper helper) {
        var source = parse("expatternprovider:circuit_cutter", "cutter", """
                {"item_input":{"amount":3,"ingredient":{"item":"minecraft:diamond"}},
                 "fluid_input":{"amount":125,"ingredient":{"fluid":"minecraft:water"}},
                 "output":{"item":"minecraft:emerald","count":9}}
                """);
        var recipe = only(helper, source).holder();
        helper.assertTrue(recipe.ingredients().size() == 1 && recipe.ingredients().get(0).count() == 3
                && recipe.fluidInput().getFluid() == Fluids.WATER && recipe.fluidInput().getAmount() == 125
                && recipe.results().get(0).getCount() == 9,
                "Circuit Slicer must retain item, fluid and output quantities");
        var conditional = helper.getLevel().getRecipeManager().byKey(new ResourceLocation("advanced_ae:quantum_processor_print_eae")).orElseThrow();
        var importedConditional = only(helper, conditional).holder();
        helper.assertTrue(importedConditional.results().get(0).getCount() == 9
                && importedConditional.fluidInput().getAmount() == 100,
                "The actual conditionally loaded AdvancedAE/ExtendedAE recipe must not be hidden by its JSON wrapper");
        assertNetworkCopy(helper, source);
        assertMatchesAfterResearch(helper, source, "eae_circuit_slicer");
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void reactionChamberPreservesCountedNbtIngredientsAndFluid(GameTestHelper helper) {
        var source = parse("advanced_ae:reaction", "reaction", """
                {"energy":20000,
                 "input_items":[{"amount":4,"ingredient":[{"item":"minecraft:diamond"},{"item":"minecraft:emerald"}]},
                  {"amount":2,"ingredient":{"type":"forge:nbt","item":"minecraft:iron_ingot","nbt":{"marker":7}}}],
                 "fluid":{"fluidStack":{"Amount":100,"FluidName":"minecraft:water","Tag":{"variant":9}}},
                 "output":{"#":16,"#c":"ae2:i","id":"minecraft:nether_star","tag":{"marker":42}}}
                """);
        var recipe = only(helper, source).holder();
        helper.assertTrue(recipe.ingredients().stream().map(MatterFabricationRecipe.CountedIngredient::count).sorted().toList().equals(List.of(2, 4))
                && recipe.fluidInput().getAmount() == 100 && recipe.fluidInput().getFluid() == Fluids.WATER
                && recipe.fluidInput().getTag() == null
                && recipe.results().get(0).getCount() == 16 && recipe.results().get(0).getTag().getInt("marker") == 42,
                "Reaction Chamber must retain counted NBT ingredients, native fluid type/mB and AE output counts/NBT; its fluid input API ignores tags");
        var alternatives = recipe.ingredients().stream().filter(value -> value.count() == 4).findFirst().orElseThrow();
        helper.assertTrue(alternatives.ingredient().test(new net.minecraft.world.item.ItemStack(Items.DIAMOND))
                && alternatives.ingredient().test(new net.minecraft.world.item.ItemStack(Items.EMERALD)),
                "Alternative item choices must stay one counted ingredient rather than becoming two consumed inputs");
        assertNetworkCopy(helper, source);
        assertMatchesAfterResearch(helper, source, "aae_reaction_chamber");
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 100)
    public static void reactionChamberImportsFluidOutputs(GameTestHelper helper) {
        var source = parse("advanced_ae:reaction", "reaction_fluid", """
                {"energy":20000,"input_items":[{"amount":8,"ingredient":{"item":"minecraft:diamond"}}],
                 "fluid":{"fluidStack":{"Amount":100,"FluidName":"minecraft:water"}},
                 "output":{"#":250,"#c":"ae2:f","id":"minecraft:lava","tag":{"variant":3}}}
                """);
        var recipe = only(helper, source).holder();
        helper.assertTrue(recipe.results().isEmpty() && recipe.fluidResult().getFluid() == Fluids.LAVA
                && recipe.fluidResult().getAmount() == 250 && recipe.fluidResult().getTag().getInt("variant") == 3,
                "Reaction Chamber AE fluid outputs must remain exact fluid outputs, not disappear or become items");
        assertNetworkCopy(helper, source);
        assertMatchesAfterResearch(helper, source, "aae_reaction_chamber");
        helper.succeed();
    }

    private static Recipe<?> parse(String type, String name, String json) {
        var serializer = BuiltInRegistries.RECIPE_SERIALIZER.get(new ResourceLocation(type));
        if (serializer == null) throw new AssertionError("Required actual Forge serializer missing: " + type);
        return serializer.fromJson(id("verification/machines/" + name), JsonParser.parseString(json).getAsJsonObject());
    }

    private static MatterRecipeBridge.ImportedRecipe only(GameTestHelper helper, Recipe<?> source) {
        var imports = MatterRecipeBridge.importRecipes(helper.getLevel(), List.of(source));
        helper.assertTrue(imports.size() == 1, "Expected one imported recipe for " + source.getId() + ", got " + imports.size());
        return imports.get(0);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertNetworkCopy(GameTestHelper helper, Recipe<?> source) {
        var serializer = (net.minecraft.world.item.crafting.RecipeSerializer) source.getSerializer();
        var buffer = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, source);
            var copy = (Recipe<?>) serializer.fromNetwork(source.getId(), buffer);
            var expected = MatterRecipeBridge.importRecipes(helper.getLevel(), List.of(source));
            var actual = MatterRecipeBridge.importRecipes(helper.getLevel(), List.of(copy));
            helper.assertTrue(expected.size() == actual.size() && !actual.isEmpty(), "Client network copy must import every machine recipe");
            for (int index = 0; index < actual.size(); index++) {
                var left = expected.get(index).holder();
                var right = actual.get(index).holder();
                helper.assertTrue(left.id().equals(right.id()) && MatterRecipeIndex.outputAmounts(left).equals(MatterRecipeIndex.outputAmounts(right))
                        && ForgeMachineRecipeJson.encode(helper.getLevel(), source).equals(ForgeMachineRecipeJson.encode(helper.getLevel(), copy)),
                        "Client/server live machine JSON, recipe IDs and exact outputs must agree after native network sync");
            }
        } finally { buffer.release(); }
    }

    private static void assertMatchesAfterResearch(GameTestHelper helper, Recipe<?> source, String machine) {
        var manager = helper.getLevel().getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        try {
            var withFixture = new ArrayList<>(original);
            withFixture.add(source);
            manager.replaceRecipes(withFixture);
            var recipe = MatterRecipeBridge.importRecipes(helper.getLevel(), List.of(source)).stream()
                    .filter(value -> value.machineKey().equals(machine)).findFirst().orElseThrow().holder();
            var selected = recipe.ingredients().stream().map(value -> value.ingredient().getItems()[0].copy()).toList();
            var pattern = PatternDetailsHelper.decodePattern(MatterFabricationPatternEncoding.encode(recipe, selected), helper.getLevel());
            var host = controller(helper);
            var supplied = new LinkedHashMap<AEKey, Long>();
            for (int index = 0; index < selected.size(); index++)
                supplied.merge(AEItemKey.of(selected.get(index)), (long) recipe.ingredients().get(index).count(), Math::addExact);
            if (!recipe.fluidInput().isEmpty()) supplied.put(AEFluidKey.of(recipe.fluidInput()), (long) recipe.fluidInput().getAmount());
            helper.assertTrue(MatterFabricationBatch.match(host, List.of(recipe), supplied, 1) == null,
                    "The real pattern must remain locked before machine research");
            MatterResearchApi.setCompletionCount(host, id("research/machine/" + machine).toString(), 1);
            var match = MatterFabricationBatch.match(host, pattern, supplied, 1);
            helper.assertTrue(match != null && match.id().equals(recipe.id()),
                    "Unlocked machine recipe must be reachable by the real pattern matcher with exact inputs: " + machine);
            if (!recipe.fluidInput().isEmpty()) {
                var noFluid = new LinkedHashMap<>(supplied);
                noFluid.remove(AEFluidKey.of(recipe.fluidInput()));
                helper.assertTrue(MatterFabricationBatch.match(host, List.of(recipe), noFluid, 1) == null,
                        "Omitting the fluid cost must reject the pattern, never grant free processing");
            }
            var shortage = new LinkedHashMap<>(supplied);
            var first = supplied.entrySet().iterator().next();
            if (first.getValue() == 1) shortage.remove(first.getKey());
            else shortage.put(first.getKey(), first.getValue() - 1);
            helper.assertTrue(MatterFabricationBatch.match(host, List.of(recipe), shortage, 1) == null,
                    "Reducing a counted input by one must reject the pattern");
            for (var entry : supplied.entrySet()) {
                if (entry.getKey() instanceof AEItemKey item && item.hasTag()) {
                    var different = new LinkedHashMap<>(supplied);
                    different.remove(item);
                    var stack = item.toStack();
                    stack.setTag(null);
                    different.put(AEItemKey.of(stack), entry.getValue());
                    helper.assertTrue(MatterFabricationBatch.match(host, List.of(recipe), different, 1) == null,
                            "Forge NBT ingredients must reject ordinary stacks with different data");
                }
            }
            helper.assertTrue(MatterFabricationBatch.takeInputs(recipe, supplied, 1, 1).equals(supplied),
                    "Exact machine inputs must be accounted for once without dropped counts or resources");
        } finally { manager.replaceRecipes(original); }
    }

    private static MatterFabricationBlockEntity controller(GameTestHelper helper) {
        var controller = new MatterFabricationBlockEntity(BlockPos.ZERO, ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState());
        controller.setLevel(helper.getLevel());
        return controller;
    }

    private static ResourceLocation id(String path) { return new ResourceLocation("molecularmanipulator", path); }
}
