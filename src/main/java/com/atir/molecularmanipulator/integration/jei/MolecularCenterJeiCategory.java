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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class MolecularCenterJeiCategory implements IRecipeCategory<MolecularCenterJeiRecipe> {
    public static final RecipeType<MolecularCenterJeiRecipe> TYPE = new RecipeType<>(
            MolecularManipulator.id("molecular_center"), MolecularCenterJeiRecipe.class);
    private final IDrawable icon;

    public MolecularCenterJeiCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModContent.MOLECULAR_CENTER_CONTROLLER_ITEM.get()));
    }

    @Override
    public RecipeType<MolecularCenterJeiRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.molecularmanipulator.molecular_center_controller");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return InteractiveStructurePreviewWidget.WIDTH;
    }

    @Override
    public int getHeight() {
        return StructureJeiLayout.height();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MolecularCenterJeiRecipe recipe, IFocusGroup focuses) {
        StructureJeiLayout.setRecipe(builder, recipe.materials(), recipe.controller());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, MolecularCenterJeiRecipe recipe,
            IFocusGroup focuses) {
        var preview = new MolecularCenterPreviewWidget();
        builder.addWidget(preview);
        builder.addGuiEventListener(preview);
    }

    @Override
    public void draw(MolecularCenterJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
            double mouseX, double mouseY) {
        StructureJeiLayout.drawMaterials(graphics);
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }
}
