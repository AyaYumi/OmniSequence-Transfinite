package com.atir.molecularmanipulator.integration.jei;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.client.AEStackIcon;
import com.atir.molecularmanipulator.client.DisplayNumbers;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.ingredients.IIngredientRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverter;
import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters;

/** Keeps addon AE keys in the same JEI ingredient type as their native recipe categories. */
final class MatterFabricationJeiIngredients {
    private MatterFabricationJeiIngredients() {}

    static boolean add(IRecipeSlotBuilder slot, GenericStack stack) {
        for (var converter : IngredientConverters.getConverters()) {
            if (addWithConverter(slot, stack, converter)) return true;
        }
        return false;
    }

    private static <T> boolean addWithConverter(IRecipeSlotBuilder slot, GenericStack stack,
            IngredientConverter<T> converter) {
        T ingredient = converter.getIngredientFromStack(stack);
        if (ingredient == null) return false;
        var type = converter.getIngredientType();
        slot.addIngredient(type, ingredient);
        slot.setCustomRenderer(type, new KeyRenderer<>(stack));
        return true;
    }

    private record KeyRenderer<T>(GenericStack stack) implements IIngredientRenderer<T> {
        @Override public void render(GuiGraphics graphics, T ingredient) {
            AEStackIcon.draw(graphics, stack, 0, 0);
        }

        @Override public List<Component> getTooltip(T ingredient, TooltipFlag flag) {
            var lines = new ArrayList<>(AEKeyRendering.getTooltip(stack.what()));
            lines.add(Component.translatable("gui.molecularmanipulator.jei_required_count",
                    DisplayNumbers.exact(stack.amount())));
            return lines;
        }
    }
}
