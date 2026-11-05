package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.registry.GreenFireLanternContent;
import com.atir.molecularmanipulator.registry.ModContent;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.placement.HorizontalAlignment;
import mezz.jei.api.gui.placement.VerticalAlignment;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Player harvesting in the lantern simulation; the lantern is a reusable catalyst. */
public final class LanternSimulationJeiCategory
        implements IRecipeCategory<LanternSimulationJeiCategory.Acquisition> {
    public record Acquisition(ItemStack input, ItemStack output) {}

    public static final RecipeType<Acquisition> TYPE = new RecipeType<>(
            MolecularManipulator.id("lantern_simulation"), Acquisition.class);
    private final IDrawable icon;

    public LanternSimulationJeiCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemStack(GreenFireLanternContent.stack(true));
    }

    public static Acquisition recipe() {
        return new Acquisition(new ItemStack(ModContent.FLAWED_STAR_MARROW_PARENT_ROCK_ITEM.get()),
                new ItemStack(ModContent.FLAWLESS_STAR_MARROW_PARENT_ROCK_ITEM.get()));
    }

    @Override
    public RecipeType<Acquisition> getRecipeType() { return TYPE; }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.molecularmanipulator.lantern_simulation");
    }

    @Override
    public IDrawable getIcon() { return icon; }

    @Override
    public int getWidth() { return 130; }

    @Override
    public int getHeight() { return 62; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Acquisition recipe, IFocusGroup focuses) {
        builder.addInputSlot(6, 24).setStandardSlotBackground().addItemStack(recipe.input())
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(instructions("limits")));
        builder.addSlot(RecipeIngredientRole.CATALYST, 56, 24).addItemStack(GreenFireLanternContent.stack(true))
                .addRichTooltipCallback((slot, tooltip) -> {
                    tooltip.add(Component.translatable("jei.molecularmanipulator.lantern_simulation.activate",
                            Component.keybind("key.molecularmanipulator.close_eyes")));
                    tooltip.add(instructions("cost"));
                });
        builder.addOutputSlot(106, 24).setStandardSlotBackground().addItemStack(recipe.output())
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(instructions("collect")));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Acquisition recipe, IFocusGroup focuses) {
        builder.addRecipeArrow().setPosition(27, 23);
        builder.addRecipeArrow().setPosition(80, 23);
        builder.addText(instructions("condition"), 82, 20).setPosition(24, 0)
                .setTextAlignment(HorizontalAlignment.CENTER).setTextAlignment(VerticalAlignment.CENTER)
                .setColor(0x7E7E7E);
        builder.addText(Component.translatable("jei.molecularmanipulator.lantern_simulation.summary",
                        Component.keybind("key.molecularmanipulator.close_eyes")), 130, 18).setPosition(0, 44)
                .setTextAlignment(HorizontalAlignment.CENTER).setTextAlignment(VerticalAlignment.CENTER)
                .setColor(0x7E7E7E);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, Acquisition recipe, IRecipeSlotsView slots, double x, double y) {
        tooltip.add(Component.translatable("jei.molecularmanipulator.lantern_simulation.activate",
                Component.keybind("key.molecularmanipulator.close_eyes")));
        tooltip.add(instructions(y >= 44 ? "cost" : "limits"));
    }

    private static Component instructions(String key) {
        return Component.translatable("jei.molecularmanipulator.lantern_simulation." + key);
    }
}
