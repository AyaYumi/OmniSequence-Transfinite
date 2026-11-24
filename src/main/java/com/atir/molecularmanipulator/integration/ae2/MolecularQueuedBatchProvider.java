package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.atir.molecularmanipulator.api.crafting.OmniBatchAdmission;
import com.atir.molecularmanipulator.api.crafting.OmniBatchDelivery;
import com.atir.molecularmanipulator.api.crafting.OmniBatchProbe;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** Internal provider capability for complete processing batches backed by AE2's send queue. */
public interface MolecularQueuedBatchProvider {
    static boolean supports(ICraftingProvider provider, IPatternDetails pattern) {
        // ExtendedAE's regular block and part instantiate this exact logic class too.
        return provider instanceof MolecularQueuedBatchProvider
                && provider.getClass() == PatternProviderLogic.class
                && pattern != null && pattern.getClass() == AEProcessingPattern.class
                && pattern.supportsPushInputsToExternalInventory();
    }

    @Nullable
    OmniBatchAdmission molecularmanipulator$prepareQueuedBatch(OmniBatchProbe probe);

    /** Attempt delivery after the CPU has registered the accepted request's outputs. */
    boolean molecularmanipulator$flushQueuedBatch();

    static OmniBatchAdmission admission(long maxCrafts, Consumer<OmniBatchDelivery> commit) {
        return new OmniBatchAdmission() {
            private boolean used;

            @Override
            public long maxCrafts() {
                return maxCrafts;
            }

            @Override
            public void commit(OmniBatchDelivery delivery) {
                if (used) {
                    throw new IllegalStateException("Queued batch admission is no longer active");
                }
                used = true;
                commit.accept(delivery);
            }

            @Override
            public void close() {
                used = true;
            }
        };
    }
}
