package com.atir.molecularmanipulator.integration.eco;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import java.math.BigInteger;
import java.util.UUID;

/** UUID-directed output bridge; null means this is not an enabled Omni target. */
public final class EcoCpuDispatchOutput {
    private EcoCpuDispatchOutput() { }
    public static BigInteger insert(Iterable<ICraftingCPU> cpus, UUID jobId, AEKey key,
            BigInteger amount, Actionable mode) {
        if (jobId == null || key == null || amount == null || amount.signum() <= 0 || mode == null) return null;
        for (var cpu : cpus) {
            if (!(cpu instanceof CraftingCPUCluster cluster)) continue;
            if (!(cluster.craftingLogic instanceof EcoCpuDispatchHost host) || !host.ecoDispatch$enabled()
                    || !jobId.equals(host.ecoDispatch$jobId())) continue;
            if (cluster.craftingLogic instanceof EcoCpuDispatchAccess access
                    && !access.ecoDispatch$dispatcher().receivingAllowed()) return BigInteger.ZERO;
            var inserted = host.ecoDispatch$insertExact(jobId, key, amount, mode);
            if (inserted == null || inserted.signum() < 0 || inserted.compareTo(amount) > 0)
                throw new IllegalStateException("Invalid Omni ECO output acceptance");
            return inserted;
        }
        return null;
    }
}
