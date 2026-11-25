package com.atir.molecularmanipulator.item;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.ids.AEComponents;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.registry.ModContent;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/** A provider's undelivered input, redeemable through an exposed ME storage face. */
public final class RecoveredPatternInputItem extends Item {
    public RecoveredPatternInputItem(Properties properties) {
        super(properties);
    }

    public static void addDrop(AEKey key, long amount, List<ItemStack> drops) {
        if (amount <= 0) return;
        if (key instanceof AEItemKey item && amount <= item.getMaxStackSize()) {
            drops.add(item.toStack((int) amount));
        } else {
            var parcel = ModContent.RECOVERED_PATTERN_INPUT.toStack();
            parcel.set(AEComponents.WRAPPED_STACK, new GenericStack(key, amount));
            drops.add(parcel);
        }
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack parcel, UseOnContext context) {
        var input = parcel.get(AEComponents.WRAPPED_STACK);
        var player = context.getPlayer();
        var level = context.getLevel();
        var pos = context.getClickedPos();
        if (input == null || player == null || !level.mayInteract(player, pos)) return InteractionResult.PASS;
        // An ME chest only exposes its cell inventory on the server.
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        var storage = level.getCapability(AECapabilities.ME_STORAGE, pos, context.getClickedFace());
        if (storage == null) return InteractionResult.PASS;
        long inserted = storage.insert(input.what(), input.amount(), Actionable.MODULATE, IActionSource.ofPlayer(player));
        if (inserted >= input.amount()) {
            parcel.remove(AEComponents.WRAPPED_STACK);
            parcel.shrink(1);
        } else if (inserted > 0) {
            parcel.set(AEComponents.WRAPPED_STACK, new GenericStack(input.what(), input.amount() - inserted));
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack parcel, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        var input = parcel.get(AEComponents.WRAPPED_STACK);
        if (input == null) return;
        tooltip.add(Component.translatable("tooltip.molecularmanipulator.recovered_pattern_input.contents",
                input.what().getDisplayName(), input.what().formatAmount(input.amount(), AmountFormat.FULL)));
        tooltip.add(Component.translatable("tooltip.molecularmanipulator.recovered_pattern_input.use"));
    }
}
