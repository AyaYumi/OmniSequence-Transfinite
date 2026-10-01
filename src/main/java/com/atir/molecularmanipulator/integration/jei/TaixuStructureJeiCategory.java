package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.registry.TaixuContent;
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

public final class TaixuStructureJeiCategory
        implements IRecipeCategory<TaixuStructureJeiRecipe> {
    public static final RecipeType<TaixuStructureJeiRecipe> TYPE = new RecipeType<>(
            MolecularManipulator.id("taixu_structure"),
            TaixuStructureJeiRecipe.class);
    private final IDrawable icon;

    public TaixuStructureJeiCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemStack(
                new ItemStack(TaixuContent.CONTROLLER.get()));
    }

    @Override
    public RecipeType<TaixuStructureJeiRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.molecularmanipulator.taixu.jei_title");
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
    public void setRecipe(IRecipeLayoutBuilder builder, TaixuStructureJeiRecipe recipe,
            IFocusGroup focuses) {
        StructureJeiLayout.setRecipe(builder, recipe.materials(), recipe.controller());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, TaixuStructureJeiRecipe recipe,
            IFocusGroup focuses) {
        var preview = new TaixuStructurePreviewWidget();
        builder.addWidget(preview);
        builder.addGuiEventListener(preview);
    }

    @Override
    public void draw(TaixuStructureJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
            double mouseX, double mouseY) {
        StructureJeiLayout.drawMaterials(graphics);
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }
}
