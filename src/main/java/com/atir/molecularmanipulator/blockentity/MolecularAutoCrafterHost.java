package com.atir.molecularmanipulator.blockentity;

import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Host contract shared by the multiblock controller and the portable auto-crafter. */
public interface MolecularAutoCrafterHost {
    IManagedGridNode getMainNode();

    Level getLevel();

    BlockPos getBlockPos();

    boolean isClientSide();

    void saveChanges();

    boolean canRunAutoCrafting();

    IActionSource autoCraftActionSource();

    boolean canQueueAutoCraftOutputs(Object2LongOpenHashMap<AEKey> primary,
            Object2LongOpenHashMap<AEKey> remainders);

    void queueAutoCraftOutputs(Object2LongOpenHashMap<AEKey> primary,
            Object2LongOpenHashMap<AEKey> remainders, long gameTime, long craftCount);

    void queueAutoCraftRefund(AEKey key, long amount);

    long getBufferedAutoCraftAmount(AEKey key);
}
