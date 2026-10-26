package com.atir.molecularmanipulator.entropy;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import com.atir.molecularmanipulator.block.NegativeEntropyEngineCoreBlock;
import com.atir.molecularmanipulator.blockentity.NegativeEntropyEngineCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** All formed members resolve to the real controller; no client-only dummy menu or network. */
public final class NegativeEntropyInteraction {
    private NegativeEntropyInteraction() {}

    public static InteractionResult open(Level level, BlockPos part, Player player) {
        var state = level.getBlockState(part);
        if (!state.hasProperty(NegativeEntropyEngineCoreBlock.FORMED)
                || !state.getValue(NegativeEntropyEngineCoreBlock.FORMED)) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            var core = NegativeEntropyEngineCoreBlockEntity.findCore(level, part);
            if (core == null) return InteractionResult.PASS;
            core.openMenu(player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    public static IInWorldGridNodeHost gridHost(Level level, BlockPos part) {
        return new IInWorldGridNodeHost() {
            @Override public IGridNode getGridNode(Direction direction) {
                var core = NegativeEntropyEngineCoreBlockEntity.findCore(level, part);
                return core == null ? null : core.getGridNode(direction);
            }
        };
    }
}
