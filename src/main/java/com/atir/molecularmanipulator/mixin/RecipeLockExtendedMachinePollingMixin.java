package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickingRequest;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;

/** An authorization change is not an inventory change: denied machines must be able to retry. */
@Mixin(targets = {
        "com.glodblock.github.extendedae.common.tileentities.TileExInscriber",
        "com.glodblock.github.extendedae.common.tileentities.TileCircuitCutter"
}, remap = false)
public abstract class RecipeLockExtendedMachinePollingMixin {
    @WrapMethod(method = "getTickingRequest")
    private TickingRequest recipeLock$pollAfterLoad(IGridNode node, Operation<TickingRequest> original) {
        var request = original.call(node);
        var level = ((BlockEntity) (Object) this).getLevel();
        if (level != null && !level.isClientSide && RecipeLocks.hasRules(level) && request.isSleeping()) {
            return new TickingRequest(request.minTickRate(), request.maxTickRate(), false,
                    request.canBeAlerted(), request.initialTickRate());
        }
        return request;
    }
}
