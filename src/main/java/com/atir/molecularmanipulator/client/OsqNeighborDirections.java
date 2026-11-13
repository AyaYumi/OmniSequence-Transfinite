package com.atir.molecularmanipulator.client;

import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.core.localization.ButtonToolTips;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Block-relative output controls with live, world-coordinate neighbor previews. */
final class OsqNeighborDirections {
    static final Direction[] SIDES = {Direction.NORTH, Direction.WEST, Direction.EAST,
            Direction.SOUTH, Direction.UP, Direction.DOWN};
    // Stable layout IDs describe the unrotated net, not the world side sent to the server.
    static final String[] IDS = {"d北", "d西", "d东", "d南", "d上", "d下"};
    static final RelativeSide[] OUTPUT_SIDES = {RelativeSide.FRONT, RelativeSide.LEFT, RelativeSide.RIGHT,
            RelativeSide.BACK, RelativeSide.TOP, RelativeSide.BOTTOM};

    private OsqNeighborDirections() {}

    static Direction worldSide(BlockState state, RelativeSide side) {
        // The autocrafter has its own facing property but inherits AE2's "none" strategy.
        // Honor that property rather than AEBaseBlockEntity.getFront(), which stays north.
        var orientation = state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? BlockOrientation.get(state.getValue(BlockStateProperties.HORIZONTAL_FACING))
                : BlockOrientation.get(state);
        return orientation.getSide(side);
    }

    static Component label(RelativeSide side) {
        return switch (side) {
            case TOP -> label(Direction.UP);
            case BOTTOM -> label(Direction.DOWN);
            case FRONT, BACK, LEFT, RIGHT -> Component.translatableWithFallback(
                    "gui.molecularmanipulator.fabrication.port.side_short."+side.name().toLowerCase(java.util.Locale.ROOT),
                    side.name().substring(0, 1));
        };
    }

    static Component label(Direction side) {
        return text("side_short."+side.getName());
    }

    static ItemStack icon(Level level, BlockPos origin, Direction side) {
        BlockPos pos=origin.relative(side);
        if (level==null || !level.hasChunkAt(pos)) return ItemStack.EMPTY;
        var state=level.getBlockState(pos);
        return state.isAir() ? ItemStack.EMPTY : new ItemStack(state.getBlock());
    }

    static Component tooltip(Level level, BlockPos origin, Direction side, int enabledSides) {
        BlockPos pos=origin.relative(side);
        Component neighbor=level==null || !level.hasChunkAt(pos) ? text("neighbor_unloaded")
                : level.getBlockState(pos).isAir() ? text("neighbor_empty")
                : level.getBlockState(pos).getBlock().getName();
        return text("side_hint",text("side."+side.getName()),
                text((enabledSides & 1 << side.get3DDataValue())!=0 ? "enabled" : "disabled")).copy()
                .append("\n").append(text("neighbor",neighbor));
    }

    static Component tooltip(Level level, BlockPos origin, RelativeSide relative, Direction worldSide, int enabledSides) {
        var name = switch (relative) {
            case FRONT -> ButtonToolTips.SideFront;
            case BACK -> ButtonToolTips.SideBack;
            case LEFT -> ButtonToolTips.SideLeft;
            case RIGHT -> ButtonToolTips.SideRight;
            case TOP -> ButtonToolTips.SideTop;
            case BOTTOM -> ButtonToolTips.SideBottom;
        };
        // Retain the full localized world-side/state/click/neighbor explanation.
        return name.text().copy().append(" · ").append(tooltip(level, origin, worldSide, enabledSides));
    }

    private static Component text(String key,Object...args) {
        return OsqUi.tr("gui.molecularmanipulator.fabrication.port."+key,args);
    }
}
