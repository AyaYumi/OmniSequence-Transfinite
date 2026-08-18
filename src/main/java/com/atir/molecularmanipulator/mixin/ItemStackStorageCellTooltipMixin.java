package com.atir.molecularmanipulator.mixin;

import java.util.ArrayList;
import java.util.Optional;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.items.storage.StorageCellTooltipComponent;
import com.atir.molecularmanipulator.storage.InfiniteStorageAmounts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rewrites only AE2 creative and ExtendedAE infinity-cell previews. */
@Mixin(ItemStack.class)
public abstract class ItemStackStorageCellTooltipMixin {
    @Unique
    private static final String molecularmanipulator$AE2_CREATIVE_CELL_INVENTORY =
            "appeng.me.cells.CreativeCellInventory";
    @Unique
    private static final String molecularmanipulator$EXTENDED_AE_INFINITY_CELL_INVENTORY =
            "com.glodblock.github.extendedae.common.inventory.InfinityCellInventory";

    @Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
    private void molecularmanipulator$markInfiniteCellContents(
            CallbackInfoReturnable<Optional<TooltipComponent>> callback) {
        var original = callback.getReturnValue();
        if (original.isEmpty()
                || !(original.get() instanceof StorageCellTooltipComponent component)) {
            return;
        }

        var stack = (ItemStack) (Object) this;
        try {
            var storage = StorageCells.getCellInventory(stack, null);
            if (!molecularmanipulator$usesLongMaximumDisplay(storage)) {
                return;
            }

            boolean changed = false;
            var content = new ArrayList<GenericStack>(component.content().size());
            for (var entry : component.content()) {
                if (entry.amount() != InfiniteStorageAmounts.DISPLAY_AMOUNT) {
                    content.add(new GenericStack(
                            entry.what(), InfiniteStorageAmounts.DISPLAY_AMOUNT));
                    changed = true;
                } else {
                    content.add(entry);
                }
            }

            if (changed) {
                callback.setReturnValue(Optional.of(new StorageCellTooltipComponent(
                        component.upgrades(),
                        content,
                        component.hasMoreContent(),
                        true)));
            }
        } catch (RuntimeException ignored) {
            // A malformed cell tooltip must not break the client tooltip pipeline.
        }
    }

    @Unique
    private static boolean molecularmanipulator$usesLongMaximumDisplay(StorageCell storage) {
        if (storage == null) {
            return false;
        }
        String className = storage.getClass().getName();
        return molecularmanipulator$AE2_CREATIVE_CELL_INVENTORY.equals(className)
                || molecularmanipulator$EXTENDED_AE_INFINITY_CELL_INVENTORY.equals(className);
    }
}
