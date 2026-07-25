package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure;
import com.atir.molecularmanipulator.integration.AdvancedAEIntegration;
import com.atir.molecularmanipulator.registry.ModContent;
import appeng.core.definitions.AEBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import com.atir.molecularmanipulator.client.MolecularCenterScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.List;

@JeiPlugin
public final class MolecularCenterJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return MolecularManipulator.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new MolecularCenterJeiCategory(
                registration.getJeiHelpers().getGuiHelper()));
        if (AdvancedAEIntegration.isLoaded()) {
            registration.addRecipeCategories(new OmniComputationJeiCategory(
                    registration.getJeiHelpers().getGuiHelper()));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(MolecularCenterJeiCategory.TYPE, List.of(new MolecularCenterJeiRecipe(
                createStructureMaterials(), new ItemStack(ModContent.MOLECULAR_CENTER_CONTROLLER_ITEM.get()))));
        if (AdvancedAEIntegration.isLoaded()) {
            registration.addRecipes(OmniComputationJeiCategory.TYPE, List.of(new OmniComputationJeiRecipe(
                    createOmniStructureMaterials(),
                    new ItemStack(ModContent.OMNI_COMPUTATION_CONTROLLER_ITEM.get()))));
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(
                MolecularCenterScreen.class,
                new MolecularCenterGhostIngredientHandler());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        if (!AdvancedAEIntegration.isLoaded()) {
            jeiRuntime.getIngredientManager().removeIngredientsAtRuntime(
                    VanillaTypes.ITEM_STACK,
                    omniItems());
        }
    }

    private static List<ItemStack> createStructureMaterials() {
        var counts = new EnumMap<MolecularCenterStructure.PartType, Integer>(MolecularCenterStructure.PartType.class);
        for (var part : MolecularCenterStructure.parts()) {
            if (MolecularCenterStructure.isController(part)
                    || part.partType() == MolecularCenterStructure.PartType.AIR) {
                continue;
            }
            counts.merge(part.partType(), 1, Integer::sum);
        }
        return List.of(
                new ItemStack(ModContent.MOLECULAR_CENTER_CASING_ITEM.get(),
                        counts.getOrDefault(MolecularCenterStructure.PartType.CASING, 0)),
                new ItemStack(ModContent.MOLECULAR_CENTER_GLASS_ITEM.get(),
                        counts.getOrDefault(MolecularCenterStructure.PartType.GLASS, 0)),
                new ItemStack(ModContent.MOLECULAR_CENTER_COIL_ITEM.get(),
                        counts.getOrDefault(MolecularCenterStructure.PartType.COIL, 0)),
                new ItemStack(ModContent.MOLECULAR_CENTER_STABILIZER_ITEM.get(),
                        counts.getOrDefault(MolecularCenterStructure.PartType.STABILIZER, 0)),
                new ItemStack(ModContent.MOLECULAR_CENTER_CORE_ITEM.get(),
                        counts.getOrDefault(MolecularCenterStructure.PartType.CORE, 0)),
                new ItemStack(AEBlocks.QUARTZ_BLOCK.asItem(),
                        counts.getOrDefault(MolecularCenterStructure.PartType.AE_QUARTZ, 0)),
                new ItemStack(AEBlocks.QUARTZ_VIBRANT_GLASS.asItem(),
                        counts.getOrDefault(MolecularCenterStructure.PartType.AE_VIBRANT_GLASS, 0)),
                new ItemStack(AEBlocks.FLUIX_BLOCK.asItem(),
                        counts.getOrDefault(MolecularCenterStructure.PartType.AE_FLUIX, 0)));
    }

    private static List<ItemStack> createOmniStructureMaterials() {
        var counts = new EnumMap<OmniComputationStructure.PartType, Integer>(
                OmniComputationStructure.PartType.class);
        for (var part : OmniComputationStructure.parts()) {
            if (part.type() != OmniComputationStructure.PartType.CONTROLLER) {
                counts.merge(part.type(), 1, Integer::sum);
            }
        }
        return List.of(
                stack(ModContent.OMNI_COMPUTATION_CASING_ITEM.get(), counts,
                        OmniComputationStructure.PartType.CASING),
                stack(ModContent.OMNI_COMPUTATION_GLASS_ITEM.get(), counts,
                        OmniComputationStructure.PartType.GLASS),
                stack(ModContent.INFINITE_PARALLEL_MATRIX_ITEM.get(), counts,
                        OmniComputationStructure.PartType.PARALLEL_MATRIX),
                stack(ModContent.INFINITE_CRAFTING_STORAGE_ITEM.get(), counts,
                        OmniComputationStructure.PartType.STORAGE_MATRIX),
                stack(ModContent.UNIVERSAL_PATTERN_MATRIX_ITEM.get(), counts,
                        OmniComputationStructure.PartType.PATTERN_MATRIX),
                stack(ModContent.COMPUTATION_DATA_ENTANGLER_ITEM.get(), counts,
                        OmniComputationStructure.PartType.DATA_ENTANGLER),
                stack(ModContent.COMPUTATION_ENERGY_STABILIZER_ITEM.get(), counts,
                        OmniComputationStructure.PartType.ENERGY_STABILIZER),
                stack(ModContent.COMPUTATION_OUTPUT_NODE_ITEM.get(), counts,
                        OmniComputationStructure.PartType.OUTPUT_NODE),
                stack(ModContent.COMPUTATION_CRYSTAL_PYLON_ITEM.get(), counts,
                        OmniComputationStructure.PartType.CRYSTAL_PYLON));
    }

    private static List<ItemStack> omniItems() {
        return List.of(
                new ItemStack(ModContent.OMNI_COMPUTATION_CONTROLLER_ITEM.get()),
                new ItemStack(ModContent.OMNI_COMPUTATION_CASING_ITEM.get()),
                new ItemStack(ModContent.OMNI_COMPUTATION_GLASS_ITEM.get()),
                new ItemStack(ModContent.INFINITE_PARALLEL_MATRIX_ITEM.get()),
                new ItemStack(ModContent.INFINITE_CRAFTING_STORAGE_ITEM.get()),
                new ItemStack(ModContent.UNIVERSAL_PATTERN_MATRIX_ITEM.get()),
                new ItemStack(ModContent.COMPUTATION_DATA_ENTANGLER_ITEM.get()),
                new ItemStack(ModContent.COMPUTATION_ENERGY_STABILIZER_ITEM.get()),
                new ItemStack(ModContent.COMPUTATION_OUTPUT_NODE_ITEM.get()),
                new ItemStack(ModContent.COMPUTATION_CRYSTAL_PYLON_ITEM.get()));
    }

    private static ItemStack stack(net.minecraft.world.item.Item item,
            EnumMap<OmniComputationStructure.PartType, Integer> counts,
            OmniComputationStructure.PartType type) {
        return new ItemStack(item, counts.getOrDefault(type, 0));
    }
}
