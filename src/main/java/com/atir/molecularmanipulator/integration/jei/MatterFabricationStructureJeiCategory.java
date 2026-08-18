package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.registry.ModContent;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class MatterFabricationStructureJeiCategory
        implements IRecipeCategory<MatterFabricationStructureJeiRecipe> {
    public static final RecipeType<MatterFabricationStructureJeiRecipe> TYPE = new RecipeType<>(
            MolecularManipulator.id("matter_fabrication_structure"),
            MatterFabricationStructureJeiRecipe.class);
    private final IDrawable icon;

    public MatterFabricationStructureJeiCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemStack(
                new ItemStack(ModContent.MATTER_FABRICATION_CONTROLLER_ITEM.get()));
    }

    @Override
    public RecipeType<MatterFabricationStructureJeiRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.molecularmanipulator.fabrication.jei_structure_title");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return 176;
    }

    @Override
    public int getHeight() {
        return 226;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MatterFabricationStructureJeiRecipe recipe,
            IFocusGroup focuses) {
        for (int index = 0; index < recipe.materials().size(); index++) {
            builder.addInputSlot(8 + index * 25, 190)
                    .setStandardSlotBackground()
                    .addItemStack(recipe.materials().get(index));
        }
        builder.addOutputSlot(150, 196)
                .setOutputSlotBackground()
                .addItemStack(recipe.controller());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MatterFabricationStructureJeiRecipe recipe,
            IFocusGroup focuses) {
        var preview = new MatterFabricationStructurePreviewWidget();
        builder.addWidget(preview);
        builder.addInputHandler(preview);
    }

    @Override
    public void draw(MatterFabricationStructureJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
            double mouseX, double mouseY) {
        graphics.fill(0, 168, getWidth(), getHeight(), 0xFF171B21);
        graphics.fill(3, 170, 173, 225, 0xFF302A39);
        graphics.fill(4, 171, 172, 224, 0xFF17131D);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.jei_materials"),
                8, 176, 0xFFF1F1F1, false);
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.jei_controller"),
                137, 176, 0xFFF1F1F1, false);
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }
}
