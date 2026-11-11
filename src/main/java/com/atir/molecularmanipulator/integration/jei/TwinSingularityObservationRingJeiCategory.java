package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.blockentity.HighEnergyLabPowerInterfaceBlockEntity;
import com.atir.molecularmanipulator.registry.HighEnergyLabContent;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.world.LabCrystallization;
import java.util.List;
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

/** Displays the existing manual cradle conversions without adding a second crafting route. */
public final class TwinSingularityObservationRingJeiCategory
        implements IRecipeCategory<TwinSingularityObservationRingJeiCategory.Conversion> {
    public record Conversion(ItemStack input, ItemStack output) {}

    public static final RecipeType<Conversion> TYPE = new RecipeType<>(HighEnergyLabContent.ID, Conversion.class);
    private final IDrawable icon;

    public TwinSingularityObservationRingJeiCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemStack(new ItemStack(HighEnergyLabContent.POWER_INTERFACE_ITEM.get()));
    }

    public static List<Conversion> recipes() {
        return List.of(new ItemStack(ModContent.PRIMORDIAL_CRYSTAL_CORE.get()))
                .stream().map(input -> new Conversion(input, LabCrystallization.resultFor(input))).toList();
    }

    @Override
    public RecipeType<Conversion> getRecipeType() { return TYPE; }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.molecularmanipulator.twin_singularity_observation_ring");
    }

    @Override
    public IDrawable getIcon() { return icon; }

    @Override
    public int getWidth() { return 130; }

    @Override
    public int getHeight() { return 62; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Conversion recipe, IFocusGroup focuses) {
        builder.addInputSlot(6, 24).setStandardSlotBackground().addItemStack(recipe.input())
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(instructions("start")));
        builder.addSlot(RecipeIngredientRole.CATALYST, 56, 24)
                .addItemStack(new ItemStack(HighEnergyLabContent.POWER_INTERFACE_ITEM.get()))
                .addRichTooltipCallback((slot, tooltip) -> {
                    tooltip.add(instructions("start"));
                    tooltip.add(instructions("pause"));
                    tooltip.add(instructions("location"));
                });
        builder.addOutputSlot(106, 24).setStandardSlotBackground().addItemStack(recipe.output())
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(instructions("pause")));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Conversion recipe, IFocusGroup focuses) {
        // Match AE2's in-world transformation layout: input -> circumstance -> result.
        builder.addRecipeArrow().setPosition(27, 23);
        builder.addRecipeArrow().setPosition(80, 23);
        builder.addText(instructions("condition"), 82, 20).setPosition(24, 0)
                .setTextAlignment(HorizontalAlignment.CENTER).setTextAlignment(VerticalAlignment.CENTER)
                .setColor(0x7E7E7E);
        builder.addText(Component.translatable("jei.molecularmanipulator.observation_ring.summary",
                        LabCrystallization.DURATION / 20, (int) HighEnergyLabPowerInterfaceBlockEntity.POWER_PER_TICK),
                        130, 18).setPosition(0, 44)
                .setTextAlignment(HorizontalAlignment.CENTER).setTextAlignment(VerticalAlignment.CENTER)
                .setColor(0x7E7E7E);
    }

    @Override
    public void getTooltip(ITooltipBuilder tooltip, Conversion recipe, IRecipeSlotsView slots, double x, double y) {
        if (y < 20) {
            tooltip.add(instructions("location"));
            tooltip.add(instructions("pause"));
        } else if (y >= 44) {
            tooltip.add(Component.translatable("jei.molecularmanipulator.observation_ring.batch",
                    LabCrystallization.DURATION / 20, LabCrystallization.DURATION));
            tooltip.add(Component.translatable("jei.molecularmanipulator.observation_ring.power",
                    (int) HighEnergyLabPowerInterfaceBlockEntity.POWER_PER_TICK,
                    (int) (LabCrystallization.DURATION * HighEnergyLabPowerInterfaceBlockEntity.POWER_PER_TICK)));
        } else {
            tooltip.add(instructions("start"));
            tooltip.add(instructions("pause"));
        }
    }

    private static Component instructions(String key) {
        return Component.translatable("jei.molecularmanipulator.observation_ring." + key);
    }
}
