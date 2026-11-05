package com.atir.molecularmanipulator.starcomputation;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import com.atir.molecularmanipulator.block.StarComputationEngineCoreBlock;
import com.atir.molecularmanipulator.blockentity.StarComputationEngineCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** All formed members resolve to the real controller; no client-only dummy menu or network. */
public final class StarComputationInteraction {
    private StarComputationInteraction() {}

    public static InteractionResult open(Level level, BlockPos part, Player player) {
        var state = level.getBlockState(part);
        if (state.getBlock() instanceof StarComputationEngineCoreBlock
                && StarComputationEngineCoreBlock.isArmillary(state)
                && !state.getValue(StarComputationEngineCoreBlock.FORMED)) {
            if (!level.isClientSide()
                    && level.getBlockEntity(part) instanceof StarComputationEngineCoreBlockEntity core) {
                core.refreshStructureNow();
                if (core.isFormed()) {
                    core.openMenu(player);
                    return InteractionResult.CONSUME;
                }
                var scan = core.getFoundationScan();
                if (scan != null && !scan.complete()) {
                    var problem = scan.problem();
                    if (!scan.resolved()) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                "message.molecularmanipulator.armillary.foundation_unloaded"), false);
                    } else {
                        var block = scan.expected() == EntropyArmillaryFoundation.Material.PLINTH
                                ? com.atir.molecularmanipulator.registry.ModContent.ENTROPY_ARMILLARY_PLINTH.get()
                                : com.atir.molecularmanipulator.registry.ModContent.ENTROPY_ARMILLARY_TRIM.get();
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                "message.molecularmanipulator.armillary.foundation_missing",
                                block.getName(), problem.getX(), problem.getY(), problem.getZ()), false);
                    }
                } else {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.molecularmanipulator.armillary.shell_missing"), false);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (!state.hasProperty(StarComputationEngineCoreBlock.FORMED)
                || !state.getValue(StarComputationEngineCoreBlock.FORMED)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            var core = StarComputationEngineCoreBlockEntity.findCore(level, part);
            if (core == null) return InteractionResult.PASS;
            core.openMenu(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    public static IInWorldGridNodeHost gridHost(Level level, BlockPos part) {
        return new IInWorldGridNodeHost() {
            @Override public IGridNode getGridNode(Direction direction) {
                var core = StarComputationEngineCoreBlockEntity.findCore(level, part);
                return core == null ? null : core.getGridNode(direction);
            }
        };
    }
}
