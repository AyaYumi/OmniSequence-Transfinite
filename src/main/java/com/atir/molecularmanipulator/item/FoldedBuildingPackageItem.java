package com.atir.molecularmanipulator.item;

import com.atir.molecularmanipulator.building.BuildingPackage;
import com.atir.molecularmanipulator.building.BuildingTransfer;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class FoldedBuildingPackageItem extends Item {
    public FoldedBuildingPackageItem(Properties properties) { super(properties); }
    @Override public Component getName(ItemStack stack) {
        var type = BuildingPackage.type(stack);
        return type == null ? super.getName(stack) : Component.translatable("item.molecularmanipulator.folded_building_package.named",
                Component.translatable(type.translationKey()));
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        var tag = BuildingPackage.data(stack);
        tooltip.add(Component.translatable("tooltip.molecularmanipulator.folded_package.calibrate").withStyle(ChatFormatting.GRAY));
        if (BuildingPackage.type(stack) == com.atir.molecularmanipulator.building.BuildingType.STAR_COMPUTATION_ENGINE
                && BuildingPackage.archive(stack) == null)
            tooltip.add(Component.translatable("tooltip.molecularmanipulator.folded_package.retired_engine").withStyle(ChatFormatting.GOLD));
        if (BuildingPackage.archive(stack) != null) {
            tooltip.add(Component.translatable("tooltip.molecularmanipulator.folded_package.sealed", tag.getInt("blocks")).withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.molecularmanipulator.folded_package.world_archive").withStyle(ChatFormatting.GRAY));
        }
        if (BuildingPackage.reservation(stack) != null) {
            tooltip.add(Component.translatable("tooltip.molecularmanipulator.folded_package.deploying").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.molecularmanipulator.folded_package.cancel").withStyle(ChatFormatting.GRAY));
        }
    }
    @Override public void inventoryTick(ItemStack stack, Level level, net.minecraft.world.entity.Entity holder, int slot, boolean selected) {
        if (holder instanceof net.minecraft.server.level.ServerPlayer player && level.getGameTime() % 100 == 0)
            BuildingTransfer.settleReservation(player, stack);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof net.minecraft.server.level.ServerPlayer server) {
            BuildingTransfer.explainPackage(server, player.getItemInHand(hand));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
