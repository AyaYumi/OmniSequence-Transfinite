package com.atir.molecularmanipulator.blockentity;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.util.AEColor;
import appeng.me.GridConnection;
import com.atir.molecularmanipulator.block.StarComputationEngineCasingBlock;
import com.atir.molecularmanipulator.block.StarComputationEngineCoreBlock;
import com.atir.molecularmanipulator.block.StarComputationObservationPortBlock;
import com.atir.molecularmanipulator.block.StarPositionAnchorBlock;
import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Forge AE2 discovers passive orrery sockets through a block entity instead of a block capability. */
public final class StarComputationProxyBlockEntity extends BlockEntity implements IInWorldGridNodeHost {
    public StarComputationProxyBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.STAR_COMPUTATION_PROXY_BE.get(), pos, state);
    }

    @Override
    @Nullable
    public IGridNode getGridNode(Direction direction) {
        if (level == null || isRemoved()) return null;
        var core = StarComputationEngineCoreBlockEntity.findCore(level, worldPosition);
        return core == null ? null : core.getGridNode(direction);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Defer until the chunk's block entities are installed, just like AE2's own hosts.
        if (level != null && !level.isClientSide) GridHelper.onFirstTick(this, proxy -> {
            if (!proxy.isRemoved()) updateNetworkNeighbors(proxy.level, proxy.worldPosition, proxy.getBlockState().getBlock(), proxy);
        });
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        updateNetworkNeighbors(level, worldPosition, getBlockState().getBlock(), null);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        updateNetworkNeighbors(level, worldPosition, getBlockState().getBlock(), null);
    }

    static void updateNetworkNeighbors(Level level, BlockPos pos) {
        if (!level.isClientSide && level.hasChunkAt(pos)) {
            updateNetworkNeighbors(level, pos, level.getBlockState(pos).getBlock(), GridHelper.getNodeHost(level, pos));
        }
    }

    private static void updateNetworkNeighbors(@Nullable Level level, BlockPos pos, Block source,
            @Nullable IInWorldGridNodeHost host) {
        if (level == null || level.isClientSide) return;
        for (var direction : Direction.values()) {
            var adjacent = pos.relative(direction);
            if (!level.hasChunkAt(adjacent)) continue;
            var block = level.getBlockState(adjacent).getBlock();
            // Internal members alias the same node; scanning them would create self-connections.
            if (block instanceof StarPositionAnchorBlock || block instanceof StarComputationEngineCasingBlock
                    || block instanceof StarComputationObservationPortBlock || block instanceof StarComputationEngineCoreBlock) continue;
            var node = host == null ? null : host.getGridNode(direction);
            var side = direction.getOpposite();
            var neighbor = GridHelper.getExposedNode(level, adjacent, side);
            if (neighbor != null && neighbor != node) synchronizeConnection(neighbor, node, side);
            level.neighborChanged(adjacent, source, pos);
        }
    }

    /** AE2 15 does not rescan full-block hosts on neighborChanged, nor remove an edge just because a host returns null. */
    private static void synchronizeConnection(IGridNode neighbor, @Nullable IGridNode node, Direction side) {
        boolean compatible = node != null && (node.getGridColor() == AEColor.TRANSPARENT
                || neighbor.getGridColor() == AEColor.TRANSPARENT || node.getGridColor() == neighbor.getGridColor());
        // getConnections() is a snapshot in AE2 15.4.10; destroying an edge also repaths/splits its grid.
        for (var connection : neighbor.getConnections()) {
            if (connection.isInWorld() && connection.getDirection(neighbor) == side
                    && (!compatible || connection.getOtherSide(neighbor) != node)) connection.destroy();
        }
        if (!compatible) return;
        // AE2 permits only one connection per node pair, including connections made by a neighbor's onReady.
        for (var connection : neighbor.getConnections()) {
            if (connection.getOtherSide(neighbor) == node) return;
        }
        // Use the same directional connection as InWorldGridNode, not a persistent virtual link.
        GridConnection.create(neighbor, node, side);
    }
}
