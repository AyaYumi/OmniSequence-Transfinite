package com.atir.molecularmanipulator.integration.jei;

import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.client.AEStackIcon;
import com.atir.molecularmanipulator.client.DisplayNumbers;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;

/** Keeps addon AE keys in the same JEI ingredient type as their native recipe categories. */
final class MatterFabricationJeiIngredients {
    private MatterFabricationJeiIngredients() {}

    static boolean add(IRecipeSlotBuilder slot, GenericStack stack) {
        try {
            var converters = Class.forName("tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters");
            var registered = (Iterable<?>) converters.getMethod("getConverters").invoke(null);
            for (var converter : registered) {
                if (addWithConverter(slot, stack, converter)) return true;
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // The optional integration has no stable Forge API on every supported pack.
        }
        return false;
    }

    static boolean addWithConverter(IRecipeSlotBuilder slot, GenericStack stack, Object converter)
            throws ReflectiveOperationException {
        var type = converter.getClass();
        Object ingredient = type.getMethod("getIngredientFromStack", GenericStack.class).invoke(converter, stack);
        if (ingredient == null) return false;
        @SuppressWarnings("unchecked")
        var ingredientType = (IIngredientType<Object>) type.getMethod("getIngredientType").invoke(converter);
        slot.setSlotName(JeiAeStackAmounts.slotName(stack.amount()));
        slot.addIngredient(ingredientType, ingredient);
        slot.setCustomRenderer(ingredientType, new KeyRenderer<>(stack));
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
