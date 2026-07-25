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

public final class OmniComputationJeiCategory implements IRecipeCategory<OmniComputationJeiRecipe> {
    public static final RecipeType<OmniComputationJeiRecipe> TYPE = new RecipeType<>(
            MolecularManipulator.id("omni_computation"), OmniComputationJeiRecipe.class);
    private final IDrawable icon;

    public OmniComputationJeiCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemStack(
                new ItemStack(ModContent.OMNI_COMPUTATION_CONTROLLER_ITEM.get()));
    }

    @Override
    public RecipeType<OmniComputationJeiRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.molecularmanipulator.omni_computation_controller");
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
    public void setRecipe(IRecipeLayoutBuilder builder, OmniComputationJeiRecipe recipe, IFocusGroup focuses) {
        for (int index = 0; index < recipe.materials().size(); index++) {
            builder.addInputSlot(8 + index % 5 * 24, 186 + index / 5 * 20)
                    .addItemStack(recipe.materials().get(index));
        }
        builder.addOutputSlot(150, 196).addItemStack(recipe.controller());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, OmniComputationJeiRecipe recipe,
            IFocusGroup focuses) {
        var preview = new OmniComputationPreviewWidget();
        builder.addWidget(preview);
        builder.addInputHandler(preview);
    }

    @Override
    public void draw(OmniComputationJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
            double mouseX, double mouseY) {
        graphics.fill(0, 168, getWidth(), getHeight(), 0xFF121522);
        graphics.fill(3, 170, 173, 225, 0xFF42305A);
        graphics.fill(4, 171, 172, 224, 0xFF15101E);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.omni.jei_materials"),
                8, 174, 0xFFF1E8FF, false);
        graphics.drawString(font, Component.translatable("gui.molecularmanipulator.omni.jei_controller"),
                137, 174, 0xFFF1E8FF, false);
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }
}
