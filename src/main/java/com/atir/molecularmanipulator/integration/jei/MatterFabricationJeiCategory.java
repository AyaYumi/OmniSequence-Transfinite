package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.registry.ModContent;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

public final class MatterFabricationJeiCategory implements IRecipeCategory<MatterFabricationRecipe> {
    public static final RecipeType<MatterFabricationRecipe> TYPE = new RecipeType<>(
            MolecularManipulator.id("matter_fabrication_processing"), MatterFabricationRecipe.class);
    private final IDrawable icon;

    public MatterFabricationJeiCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemStack(
                new ItemStack(ModContent.MATTER_FABRICATION_CONTROLLER_ITEM.get()));
    }

    @Override
    public RecipeType<MatterFabricationRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.molecularmanipulator.fabrication.jei_title");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return 150;
    }

    @Override
    public int getHeight() {
        return 70;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MatterFabricationRecipe recipe, IFocusGroup focuses) {
        for (int index = 0; index < recipe.ingredients().size(); index++) {
            var counted = recipe.ingredients().get(index);
            var stacks = Arrays.stream(counted.ingredient().getItems())
                    .map(stack -> stack.copyWithCount(counted.count()))
                    .toList();
            builder.addInputSlot(8 + index % 2 * 22, 12 + index / 2 * 22)
                    .setStandardSlotBackground()
                    .addItemStacks(stacks);
        }
        for (int index = 0; index < recipe.results().size(); index++) {
            builder.addOutputSlot(108 + index * 22, 23)
                    .setOutputSlotBackground()
                    .addItemStack(recipe.results().get(index));
        }
    }

    @Override
    public void draw(MatterFabricationRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
            double mouseX, double mouseY) {
        graphics.fillGradient(0, 0, getWidth(), getHeight(), 0xFF171024, 0xFF08131C);
        graphics.fill(50, 31, 98, 35, 0xFF251936);
        graphics.fill(50, 31, 94, 35, 0xFF8D57BF);
        graphics.fill(94, 29, 98, 37, 0xFF6DE6FF);
        var font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font,
                Component.translatable("gui.molecularmanipulator.fabrication.jei_time",
                        recipe.processingTime() / 20.0F),
                74, 48, 0xFFB9C7D5);
        graphics.drawCenteredString(font,
                Component.translatable("gui.molecularmanipulator.fabrication.power",
                        String.format(java.util.Locale.ROOT, "%.1f", recipe.aePerTick())),
                74, 59, 0xFFC1F2FF);
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }
}
