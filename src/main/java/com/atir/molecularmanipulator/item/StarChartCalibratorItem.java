package com.atir.molecularmanipulator.item;

import com.atir.molecularmanipulator.building.BuildingTransfer;
import com.atir.molecularmanipulator.building.BuildingType;
import com.atir.molecularmanipulator.network.BuildingPayloads;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

/** Reusable site survey and whole-machine sealing tool. */
public final class StarChartCalibratorItem extends Item {
    public StarChartCalibratorItem(Properties properties) { super(properties); }
    @Override public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player) open(player,
                context.getClickedPos().relative(context.getClickedFace()), context.getClickedPos());
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide());
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            BuildingTransfer.collect(server);
            var hit = player.pick(8, 0, false);
            var target = BlockPos.containing(hit.getLocation());
            open(server, target.above(), target);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
    private static void open(ServerPlayer player, BlockPos base, BlockPos target) {
        var entity = player.level().getBlockEntity(target);
        if (entity == null) entity = com.atir.molecularmanipulator.blockentity.StarComputationEngineCoreBlockEntity.findCore(player.level(), target);
        boolean seal = player.isShiftKeyDown() && BuildingType.forController(entity) != null;
        PacketDistributor.sendToPlayer(player, new BuildingPayloads.Open(base, seal ? entity.getBlockPos() : target, seal));
    }
}
