package com.atir.molecularmanipulator.world.gravity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.EntityDataAccessor;

/** State supplied by the Player mixin, on both sides. */
public interface GravityPlayerAccess {
    Direction omnisequence$gravity();
    void omnisequence$gravity(Direction direction);
    SynchedEntityData.DataValue<?> omnisequence$gravityData();
    boolean omnisequence$isGravityData(EntityDataAccessor<?> accessor);
    boolean omnisequence$localMotion();
    void omnisequence$localMotion(boolean local);
    BlockPos omnisequence$gravitySource();
    void omnisequence$gravitySource(BlockPos source);
    BlockPos omnisequence$gravityCandidate();
    int omnisequence$gravityCandidateTicks();
    void omnisequence$gravityCandidate(BlockPos source, int ticks);
}
