package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import com.atir.molecularmanipulator.blockentity.AssemblerMatrixMolecularCoreBlockEntity;
import com.atir.molecularmanipulator.integration.extendedae.MolecularMatrixCluster;
import com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClusterAssemblerMatrix.class, remap = false)
public abstract class ClusterAssemblerMatrixMixin implements MolecularMatrixCluster {
    @Unique
    private AssemblerMatrixMolecularCoreBlockEntity molecularmanipulator$core;

    @Override
    public void molecularmanipulator$registerCore(AssemblerMatrixMolecularCoreBlockEntity core) {
        if (molecularmanipulator$getCore() == null) {
            molecularmanipulator$core = core;
        }
    }

    @Override
    public boolean molecularmanipulator$hasCore() {
        return molecularmanipulator$getCore() != null;
    }

    @Inject(method = "pushCraftingJob", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$routeCrafting(IPatternDetails patternDetails, KeyCounter[] inputHolder,
            CallbackInfoReturnable<Boolean> callback) {
        var core = molecularmanipulator$getCore();
        if (core != null) {
            callback.setReturnValue(core.acceptCrafting(patternDetails, inputHolder));
        }
    }

    @Inject(method = "isBusy", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$replaceBusyState(CallbackInfoReturnable<Boolean> callback) {
        var core = molecularmanipulator$getCore();
        if (core != null) {
            callback.setReturnValue(!core.canAcceptCrafting());
        }
    }

    @Inject(method = "getSpeedCore", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$disableSpeedCores(CallbackInfoReturnable<Integer> callback) {
        if (molecularmanipulator$getCore() != null) {
            callback.setReturnValue(0);
        }
    }

    @Inject(method = "getBusyCrafterAmount", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$hideNormalCrafterLoad(CallbackInfoReturnable<Integer> callback) {
        if (molecularmanipulator$getCore() != null) {
            callback.setReturnValue(0);
        }
    }

    @Unique
    private AssemblerMatrixMolecularCoreBlockEntity molecularmanipulator$getCore() {
        if (molecularmanipulator$core == null) {
            return null;
        }
        var cluster = (ClusterAssemblerMatrix) (Object) this;
        if (molecularmanipulator$core.isRemoved() || molecularmanipulator$core.getCluster() != cluster) {
            molecularmanipulator$core = null;
        }
        return molecularmanipulator$core;
    }
}
