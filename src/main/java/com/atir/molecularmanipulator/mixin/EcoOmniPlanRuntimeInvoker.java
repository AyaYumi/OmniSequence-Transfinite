package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExecutionRuntime;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ECOExecutionRuntime.class, remap = false)
public interface EcoOmniPlanRuntimeInvoker {
    @Invoker("fromNBT")
    static ECOExecutionRuntime molecularmanipulator$restore(ECOExecutionPlan plan,
            Map<Integer, IPatternDetails> bindings, CompoundTag tag) {
        throw new AssertionError("ECO runtime invoker was not applied");
    }
}
