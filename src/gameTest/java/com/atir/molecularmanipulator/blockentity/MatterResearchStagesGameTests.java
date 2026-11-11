package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import com.atir.molecularmanipulator.ae2.StarSignalKey;
import com.atir.molecularmanipulator.crafting.MatterFabricationPatternEncoding;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.registry.HighEnergyLabContent;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.research.MachineResearchGate;
import com.atir.molecularmanipulator.research.MatterResearchApi;
import com.atir.molecularmanipulator.research.MatterResearchRecipe;
import com.atir.molecularmanipulator.world.LabCrystallization;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Small, separately selectable native-server regressions for the research-stage migration. */
@GameTestHolder("researchregression")
@PrefixGameTestTemplate(false)
public final class MatterResearchStagesGameTests {
    @GameTest(template = "empty")
    public static void proliferationLoadsRealSignalAndWaterAndEncodesOnlyConsumedResources(GameTestHelper helper) {
        var index = MatterRecipeIndex.get(helper.getLevel());
        var holder = index.fabrication(id("fabrication/internal/star_marrow_crystal"));
        helper.assertTrue(holder != null, "Proliferation recipe must decode during real data-pack loading");
        var recipe = holder.value();
        helper.assertTrue(recipe.ingredients().isEmpty() && recipe.aeInputs().size() == 1
                && recipe.aeInputs().getFirst().what().equals(StarSignalKey.INSTANCE)
                && recipe.aeInputs().getFirst().amount() == 1024, "Only real signal is the generic AE input");
        helper.assertTrue(recipe.fluidInput().getFluid() == Fluids.WATER && recipe.fluidInput().getAmount() == 8000,
                "One execution consumes 8000mB water");
        helper.assertTrue(recipe.catalysts().size() == 1 && recipe.catalysts().getFirst().count() == 1
                && recipe.catalysts().getFirst().ingredient().test(new ItemStack(ModContent.FLAWLESS_STAR_MARROW_PARENT_ROCK.get())),
                "One retained Flawless parent rock is the catalyst");
        var encoded = MatterFabricationPatternEncoding.encode(recipe, List.of());
        var pattern = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
        helper.assertTrue(pattern != null && pattern.getInputs().length == 2 && pattern.getOutputs().size() == 1,
                "Processing pattern encodes signal and water, never the retained catalyst");
        var inputs = List.of(pattern.getInputs());
        helper.assertTrue(inputs.stream().anyMatch(input -> input.getPossibleInputs()[0].what().equals(StarSignalKey.INSTANCE))
                && inputs.stream().anyMatch(input -> input.getPossibleInputs()[0].what().equals(AEFluidKey.of(Fluids.WATER))),
                "Both generic signal and water survive native pattern encoding");
        for (var product : List.of("star_chart_circuit_board", "star_chart_processor", "star_marrow_crystal_dust",
                "star_marrow_alloy_ingot", "star_signal_storage_component", "quantum_star_position_anchor", "terminal_star_position_anchor")) {
            var production = index.fabrication(id(product));
            helper.assertTrue(production != null && index.researchFor(id(product)).size() == 1
                    && index.researchFor(id(product)).getFirst().id().equals(id("research/star_marrow_materials")),
                    "Exclusive Star Marrow owner: " + product);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void completedLegacyMachineImportStillRequiresBothThirdCompletions(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        try {
            // AAE is optional in the dev runtime. Supply only missing parent definitions to
            // test the live permission boundary independently of the installed addon set.
            var withParents = new ArrayList<RecipeHolder<?>>(original);
            for (var parent : List.of(MachineResearchGate.SEQUENCE, MachineResearchGate.COMPUTATION)) {
                if (manager.byKey(parent).isEmpty()) withParents.add(new RecipeHolder<>(parent,
                        new MatterResearchRecipe("test", List.of(), List.of(), 600, 0, List.of(), List.of(), 0, 3)));
            }
            manager.replaceRecipes(withParents);
            var index = MatterRecipeIndex.get(helper.getLevel());
            var machineResearch = index.research(id("research/machine/eae_crystal_assembler"));
            helper.assertTrue(machineResearch != null, "Installed ExtendedAE supplies its real imported machine research");
            var recipeId = machineResearch.value().unlocks().getFirst();
            var pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, ModContent.MATTER_FABRICATION_CONTROLLER.get());
            var machine = (MatterFabricationBlockEntity) helper.getBlockEntity(pos);
            MatterResearchApi.setCompletionCount(machine, machineResearch.id().toString(), 1);
            for (var pair : List.of(new int[] {2, 3}, new int[] {3, 2}, new int[] {3, 3})) {
                MatterResearchApi.setCompletionCount(machine, MachineResearchGate.SEQUENCE.toString(), pair[0]);
                MatterResearchApi.setCompletionCount(machine, MachineResearchGate.COMPUTATION.toString(), pair[1]);
                helper.assertTrue(MatterResearchApi.isRecipeUnlocked(machine, recipeId.toString())
                        == (pair[0] >= 3 && pair[1] >= 3), "Live import gate: " + pair[0] + "/" + pair[1]);
            }
            helper.succeed();
        } finally {
            manager.replaceRecipes(original);
        }
    }

    @GameTest(template = "empty")
    public static void retiredBoardBatchReturnsItsCrystalAndKeepsFinishedOutput(GameTestHelper helper) {
        var pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, HighEnergyLabContent.INPUT_CRADLE.get());
        var cradle = (HighEnergyLabCradleBlockEntity) helper.getBlockEntity(pos);
        var tag = new CompoundTag();
        var registries = helper.getLevel().registryAccess();
        tag.put("Working", new ItemStack(ModContent.STAR_MARROW_CRYSTAL.get(), 16).saveOptional(registries));
        tag.putInt("Progress", 100);
        cradle.loadWithComponents(tag, registries);
        cradle.serverTick();
        helper.assertTrue(cradle.working().isEmpty() && cradle.stored().is(ModContent.STAR_MARROW_CRYSTAL.get())
                && cradle.stored().getCount() == 16 && cradle.progress() == 0, "Legacy locked crystals return intact");
        helper.assertTrue(LabCrystallization.resultFor(cradle.stored()).isEmpty() && !cradle.start(),
                "No new board conversion may start");
        helper.assertTrue(LabCrystallization.resultFor(new ItemStack(ModContent.PRIMORDIAL_CRYSTAL_CORE.get()))
                .is(ModContent.STAR_MARROW_SEED.get()), "Core-to-seed conversion remains available");
        helper.succeed();
    }

    private static ResourceLocation id(String path) { return ResourceLocation.parse("molecularmanipulator:" + path); }
}
