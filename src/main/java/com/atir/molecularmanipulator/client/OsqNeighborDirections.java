package com.atir.molecularmanipulator.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Live world-coordinate neighbor previews shared by output-direction controls. */
final class OsqNeighborDirections {
    static final Direction[] SIDES = {Direction.NORTH, Direction.WEST, Direction.EAST,
            Direction.SOUTH, Direction.UP, Direction.DOWN};
    static final String[] IDS = {"d北", "d西", "d东", "d南", "d上", "d下"};

    private OsqNeighborDirections() {}

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

    private static Component text(String key,Object...args) {
        return OsqUi.tr("gui.molecularmanipulator.fabrication.port."+key,args);
    }
}
