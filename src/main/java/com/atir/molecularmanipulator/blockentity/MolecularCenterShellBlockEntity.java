package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Legacy block entity retained so existing saves can deserialize old Molecular
 * Center casing data. Casings no longer create this block entity or expose an
 * AE2 grid node; only the controller may connect to the network.
 */
public final class MolecularCenterShellBlockEntity extends BlockEntity {
    public MolecularCenterShellBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MOLECULAR_CENTER_SHELL_BE.get(), pos, state);
    }
}
