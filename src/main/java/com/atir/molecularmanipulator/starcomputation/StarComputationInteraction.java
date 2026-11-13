package com.atir.molecularmanipulator.starcomputation;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import com.atir.molecularmanipulator.block.StarComputationEngineCoreBlock;
import com.atir.molecularmanipulator.block.StarComputationEngineCasingBlock;
import com.atir.molecularmanipulator.block.StarComputationObservationPortBlock;
import com.atir.molecularmanipulator.block.StarPositionAnchorBlock;
import com.atir.molecularmanipulator.blockentity.StarComputationEngineCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Only verified central body members and real orrery anchors proxy the controller. */
public final class StarComputationInteraction {
    private StarComputationInteraction() {}

    public static InteractionResult open(Level level, BlockPos part, Player player) {
        var state = level.getBlockState(part);
        if (state.getBlock() instanceof StarComputationEngineCoreBlock) {
            if (!level.isClientSide()
                    && level.getBlockEntity(part) instanceof StarComputationEngineCoreBlockEntity core) {
                core.refreshStructureNow();
                if (core.isFormed()) {
                    core.openMenu(player);
                    return InteractionResult.CONSUME;
                }
                var body = core.getCoreScan();
                if (body != null && !body.complete()) {
                    if (!body.resolved()) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                "message.molecularmanipulator.star_orrery.core_unloaded"), false);
                    } else {
                        var block = body.expected() == StarOrreryCoreStructure.Material.CASING
                                ? com.atir.molecularmanipulator.registry.ModContent.STAR_COMPUTATION_ENGINE_CASING.get()
                                : com.atir.molecularmanipulator.registry.ModContent.STAR_COMPUTATION_OBSERVATION_PORT.get();
                        var problem = body.problem();
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                "message.molecularmanipulator.star_orrery.core_missing",
                                block.getName(), problem.getX(), problem.getY(), problem.getZ()), false);
                    }
                    return InteractionResult.CONSUME;
                }
                var scan = core.getFoundationScan();
                if (scan != null && !scan.complete()) {
                    var problem = scan.problem();
                    if (!scan.resolved()) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                "message.molecularmanipulator.star_orrery.foundation_unloaded"), false);
                    } else {
                        var block = scan.expected() == StarOrreryFoundation.Material.PLINTH
                                ? com.atir.molecularmanipulator.registry.ModContent.STAR_ORRERY_ENGRAVED_PLINTH.get()
                                : com.atir.molecularmanipulator.registry.ModContent.STAR_ORRERY_GILDED_PLINTH.get();
                        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                                "message.molecularmanipulator.star_orrery.foundation_missing",
                                block.getName(), problem.getX(), problem.getY(), problem.getZ()), false);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        boolean central = state.getBlock() instanceof StarComputationEngineCasingBlock
                || state.getBlock() instanceof StarComputationObservationPortBlock;
        boolean ring = state.getBlock() instanceof StarPositionAnchorBlock && StarPositionAnchorBlock.isStarOrrery(state);
        if ((!central && !ring) || !state.getValue(StarComputationEngineCoreBlock.FORMED)) return InteractionResult.PASS;
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
