package com.atir.molecularmanipulator.mixin;

import java.util.ArrayList;
import java.util.Optional;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.items.storage.StorageCellTooltipComponent;
import com.atir.molecularmanipulator.storage.InfiniteStorageAmounts;
import com.atir.molecularmanipulator.storage.InfiniteStorageDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Rewrites infinite storage-cell content previews to use the shared sentinel. */
@Mixin(ItemStack.class)
public abstract class ItemStackStorageCellTooltipMixin {
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
            if (storage == null) {
                return;
            }

            var available = new KeyCounter();
            storage.getAvailableStacks(available);

            boolean changed = false;
            var content = new ArrayList<GenericStack>(component.content().size());
            for (var entry : component.content()) {
                long advertisedAmount = available.get(entry.what());
                if (advertisedAmount <= 0) {
                    advertisedAmount = entry.amount();
                }

                if (InfiniteStorageDetector.isUnbounded(
                        storage, entry.what(), advertisedAmount)) {
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
            // Third-party cell tooltips remain usable if their client inventory cannot be probed.
        }
    }
}
