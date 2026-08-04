package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.blockentity.AssemblerMatrixMolecularCoreBlockEntity;
import com.glodblock.github.extendedae.common.me.matrix.CalculatorAssemblerMatrix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CalculatorAssemblerMatrix.class, remap = false)
public abstract class CalculatorAssemblerMatrixMixin {
    @Redirect(
            method = "verifyInternalStructure",
            at = @At(
                    value = "CONSTANT",
                    args = "classValue=com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixCrafter"),
            remap = false)
    private boolean molecularmanipulator$acceptMolecularCore(Object blockEntity, Class<?> originalClass) {
        return blockEntity instanceof AssemblerMatrixMolecularCoreBlockEntity
                || originalClass.isInstance(blockEntity);
    }
}
