package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.registry.SingularityContent;
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

public final class SingularityStructureJeiCategory
        implements IRecipeCategory<SingularityStructureJeiRecipe> {
    public static final RecipeType<SingularityStructureJeiRecipe> TYPE = new RecipeType<>(
            MolecularManipulator.id("singularity_structure"),
            SingularityStructureJeiRecipe.class);
    private final IDrawable icon;

    public SingularityStructureJeiCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemStack(
                new ItemStack(SingularityContent.CONTROLLER.get()));
    }

    @Override
    public RecipeType<SingularityStructureJeiRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.molecularmanipulator.singularity.jei_title");
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
    public void setRecipe(IRecipeLayoutBuilder builder, SingularityStructureJeiRecipe recipe,
            IFocusGroup focuses) {
        StructureJeiLayout.setRecipe(builder, recipe.materials(), recipe.controller());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, SingularityStructureJeiRecipe recipe,
            IFocusGroup focuses) {
        var preview = new SingularityStructurePreviewWidget();
        builder.addWidget(preview);
        builder.addGuiEventListener(preview);
    }

    @Override
    public void draw(SingularityStructureJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
            double mouseX, double mouseY) {
        StructureJeiLayout.drawMaterials(graphics);
    }

    @Override
    public boolean needsRecipeBorder() {
        return false;
    }
}
