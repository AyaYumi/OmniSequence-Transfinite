package com.atir.molecularmanipulator.integration.jei;

import appeng.menu.slot.FakeSlot;
import com.atir.molecularmanipulator.client.MolecularCenterScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;

import java.util.List;

final class MolecularCenterGhostIngredientHandler
        implements IGhostIngredientHandler<MolecularCenterScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(MolecularCenterScreen screen,
            ITypedIngredient<I> ingredient, boolean doStart) {
        var itemStack = ingredient.getItemStack().orElse(ItemStack.EMPTY);
        var slot = screen.getMenu().getSequenceSlots().get(0);
        if (itemStack.isEmpty()
                || !slot.isActive()
                || !(slot instanceof FakeSlot fakeSlot)
                || !fakeSlot.canSetFilterTo(itemStack)) {
            return List.of();
        }

        var area = new Rect2i(
                screen.getGuiLeft() + slot.x,
                screen.getGuiTop() + slot.y,
                16,
                16);
        return List.of(new Target<>() {
            @Override
            public Rect2i getArea() {
                return area;
            }

            @Override
            public void accept(I droppedIngredient) {
                if (droppedIngredient instanceof ItemStack droppedStack) {
                    fakeSlot.setFilterTo(droppedStack.copyWithCount(1));
                }
            }
        });
    }

    @Override
    public void onComplete() {
    }
}
