package com.atir.molecularmanipulator.mixin;

import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle;
import com.atir.molecularmanipulator.crafting.MolecularBatchCancellationData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Persist ECO cancellation for unloaded molecular machines as well. */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingJobLifecycle",
        remap = false)
public abstract class EcoMolecularJobLifecycleMixin {
    @Inject(method = "finish", at = @At("HEAD"))
    private static void molecularmanipulator$recordCanceledJob(Level level, UUID jobId,
            boolean completed, CallbackInfo ci) {
        // ECO uses putIfAbsent: a later cancel cannot change an already
        // completed job into a canceled one in our provider ledger.
        if (!completed && !ECOCraftingJobLifecycle.isTerminated(level, jobId)) {
            MolecularBatchCancellationData.markCanceled(level, jobId);
        }
    }

    @Inject(method = "resumePersistedJob", at = @At("TAIL"))
    private static void molecularmanipulator$resumePersistedJob(Level level,
            CompoundTag cpuTag, CallbackInfo ci) {
        var link = cpuTag.getCompound("job").getCompound("link");
        if (link.hasUUID("craftId")) {
            var jobId = link.getUUID("craftId");
            if (!ECOCraftingJobLifecycle.isTerminated(level, jobId)) {
                MolecularBatchCancellationData.resumePersisted(level, jobId);
            }
        }
    }
}
