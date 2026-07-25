package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.blockentity.AssemblerMatrixMolecularCoreBlockEntity;
import com.glodblock.github.extendedae.common.me.matrix.CalculatorAssemblerMatrix;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixCrafter;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = CalculatorAssemblerMatrix.class, remap = false)
public abstract class CalculatorAssemblerMatrixMixin {
    @Definition(id = "TileAssemblerMatrixCrafter", type = TileAssemblerMatrixCrafter.class)
    @Expression("? instanceof TileAssemblerMatrixCrafter")
    @WrapOperation(method = "verifyInternalStructure", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean molecularmanipulator$acceptMolecularCore(Object blockEntity, Operation<Boolean> original) {
        return blockEntity instanceof AssemblerMatrixMolecularCoreBlockEntity || original.call(blockEntity);
    }
}
