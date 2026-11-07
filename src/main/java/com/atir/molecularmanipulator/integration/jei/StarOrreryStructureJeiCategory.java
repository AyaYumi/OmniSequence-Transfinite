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

/** One category for the first one, two and three rings (L1/L2/L3), not three-ring tier groups. */
public final class StarOrreryStructureJeiCategory implements IRecipeCategory<StarOrreryStructureJeiRecipe> {
    public static final RecipeType<StarOrreryStructureJeiRecipe> TYPE = new RecipeType<>(
            MolecularManipulator.id("star_orrery_structure"), StarOrreryStructureJeiRecipe.class);
    private final IDrawable icon;

    public StarOrreryStructureJeiCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemStack(new ItemStack(ModContent.STAR_COMPUTATION_ENGINE_CORE_ITEM.get()));
    }

    @Override
    public RecipeType<StarOrreryStructureJeiRecipe> getRecipeType() { return TYPE; }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.molecularmanipulator.star_orrery.jei_structure_title");
    }

    @Override
    public IDrawable getIcon() { return icon; }

    @Override
    public int getWidth() { return InteractiveStructurePreviewWidget.WIDTH; }

    @Override
    public int getHeight() { return StructureJeiLayout.height(); }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, StarOrreryStructureJeiRecipe recipe, IFocusGroup focuses) {
        StructureJeiLayout.setRecipe(builder, recipe.materials(), recipe.output());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, StarOrreryStructureJeiRecipe recipe,
            IFocusGroup focuses) {
        var preview = new StarOrreryStructurePreviewWidget(recipe.type());
        builder.addWidget(preview);
        builder.addGuiEventListener(preview);
    }

    @Override
    public void draw(StarOrreryStructureJeiRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics,
            double mouseX, double mouseY) {
        StructureJeiLayout.drawMaterials(graphics,
                Component.translatable("gui.molecularmanipulator.jei_structure_package"));
    }

    @Override
    public boolean needsRecipeBorder() { return false; }
}
