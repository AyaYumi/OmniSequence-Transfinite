package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExecutionPlanNbtCodec;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanDecodeScope;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Reuses ECO's codec without constructing a fake ECO CPU or duplicating its decoder. */
@Mixin(value = ECOExecutionPlanNbtCodec.class, remap = false)
public abstract class EcoOmniPlanCodecMixin {
    @WrapOperation(method = "decode", at = @At(value = "FIELD", opcode = 180,
            target = "Lcn/dancingsnow/neoecoae/crafting/execution/ECOCraftingCPULogic;cpu:Lcn/dancingsnow/neoecoae/crafting/execution/ECOCraftingCPU;"))
    private static ECOCraftingCPU molecularmanipulator$omniDecodeCpu(ECOCraftingCPULogic cpu,
            Operation<ECOCraftingCPU> original) {
        return EcoOmniPlanDecodeScope.current() == null ? original.call(cpu) : null;
    }

    @WrapOperation(method = "decode", at = @At(value = "INVOKE",
            target = "Lcn/dancingsnow/neoecoae/crafting/execution/ECOCraftingCPU;getLevel()Lnet/minecraft/world/level/Level;"))
    private static Level molecularmanipulator$omniDecodeLevel(ECOCraftingCPU cpu, Operation<Level> original) {
        var scope = EcoOmniPlanDecodeScope.current();
        return scope == null ? original.call(cpu) : scope.level();
    }

    @WrapOperation(method = "decode", at = @At(value = "INVOKE",
            target = "Lappeng/api/crafting/PatternDetailsHelper;decodePattern(Lappeng/api/stacks/AEItemKey;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;"))
    private static IPatternDetails molecularmanipulator$omniDecodePattern(AEItemKey definition,
            Level level, Operation<IPatternDetails> original) {
        var scope = EcoOmniPlanDecodeScope.current();
        var bound = scope == null ? null : scope.pattern(definition);
        return bound == null ? original.call(definition, level) : bound;
    }
}
