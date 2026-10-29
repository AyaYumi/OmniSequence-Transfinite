package com.atir.molecularmanipulator.integration.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.api.crafting.OmniBatchProviderAdapterRegistry;
import com.atir.molecularmanipulator.api.crafting.OmniBigIntegerProviderAdapterRegistry;
import com.atir.molecularmanipulator.integration.ae2.OmniNativeBatchHost;
import com.atir.molecularmanipulator.integration.ae2.OmniNativeDispatchScope;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.BatchTaskHandle;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import com.moakiee.thunderbolt.core.crafting.batch.BatchCpuAccounting;
import com.moakiee.thunderbolt.core.crafting.batch.BatchExecutor;
import com.moakiee.thunderbolt.core.crafting.batch.TickProviderDispatchSchedule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.level.Level;

/**
 * Uses Thunderbolt's own reservation/refund executor, never scaled pushPattern calls.
 * Its partial-acceptance contract must not be adapted to Omni's atomic-delivery API.
 */
public final class OmniThunderboltDispatch {
    private UUID jobId;
    private long tick = Long.MIN_VALUE;
    private int taskRotation;
    private IPatternDetails preferred;
    private final Map<IPatternDetails, IdentityHashMap<ICraftingProvider, Boolean>> batched = new HashMap<>();
    private TickProviderDispatchSchedule schedule = new TickProviderDispatchSchedule();

    public int run(OmniNativeBatchHost host, int maximum, CraftingService crafting,
            IEnergyService energy, Level level) {
        long copies = host.omniNative$maximumCopies();
        if (!host.omniNative$virtualCpu()) copies = Math.min(maximum, copies);
        if (maximum <= 0 || copies <= 1 || host.omniNative$stopped()) return -1;
        if (!Objects.equals(jobId, host.omniNative$jobId())) {
            jobId = host.omniNative$jobId();
            tick = Long.MIN_VALUE;
            taskRotation = 0;
            schedule = new TickProviderDispatchSchedule();
        }
        long now = level.getGameTime();
        var sourceTasks = host.omniNative$tasks();
        if (tick != now) {
            tick = now;
            preferred = null;
            if (!sourceTasks.isEmpty()) {
                int start = Math.floorMod(taskRotation++, sourceTasks.size());
                var iterator = sourceTasks.keySet().iterator();
                for (int index = 0; index <= start; index++) preferred = iterator.next();
            }
        }
        // Rotate across ALL tasks, including tasks handled by the ordinary/ECO
        // fallback. A continuously ready native producer cannot take every tick.
        host.omniNative$prefer(preferred);
        if (preferred == null || !sourceTasks.containsKey(preferred)
                || host.omniNative$taskWindow(preferred) <= 1) return -1;
        final var pattern = preferred;
        long providerCount = 0;
        var nativeProviders = new ArrayList<ICraftingProvider>();
        boolean batchCapacity = false;
        for (var provider : crafting.getProviders(pattern)) {
            if (!(provider instanceof IBatchCraftingProvider batch)
                    || OmniBatchProviderAdapterRegistry.supports(provider, pattern)
                    || OmniBigIntegerProviderAdapterRegistry.supports(provider, pattern)) return -1;
            if (++providerCount + 1 > host.omniNative$workAvailable()) return -1;
            nativeProviders.add(provider);
            try {
                batchCapacity |= batch.getBatchCapacity(pattern) > 1;
            } catch (RuntimeException | LinkageError unavailable) {
                // No input has been reserved; the existing fallback can retry it.
            }
        }
        // A capacity-one provider needs its single-recipe path, without spending
        // the entire work allowance on an executor that deliberately skips it.
        if (!batchCapacity) return -1;
        final long work = providerCount + 1;
        schedule.beginTick(now);
        var tasks = new Iterator<BatchTaskHandle>() {
            private boolean visited;
            @Override public boolean hasNext() {
                return !visited && !host.omniNative$stopped() && work <= host.omniNative$workAvailable();
            }
            @Override public BatchTaskHandle next() {
                if (!hasNext()) throw new NoSuchElementException();
                visited = true;
                // Reserve extraction plus every possible provider attempt,
                // including providers that reject the offered inputs.
                host.omniNative$attempted(work);
                return new BatchTaskHandle() {
                    private long value = Math.min(host.omniNative$taskWindow(pattern), host.omniNative$maximumCopies());
                    @Override public IPatternDetails details() { return pattern; }
                    @Override public long getValue() { return value; }
                    @Override public void setValue(long next) {
                        if (next < 0 || next > value) throw new IllegalArgumentException("Invalid native task decrement");
                        long accepted = value - next;
                        if (accepted > 0) host.omniNative$accepted(pattern, accepted);
                        value = next;
                    }
                };
            }
            // The advertised window may be smaller than the exact remaining task.
            @Override public void remove() { }
        };
        var job = new BatchJobView() {
            @Override public Level level() { return level; }
            @Override public UUID craftingId() { return host.omniNative$jobId(); }
            @Override public Iterator<BatchTaskHandle> taskIterator() { return tasks; }
            @Override public ListCraftingInventory waitingFor() { return host.omniNative$waitingFor(); }
            @Override public void insertWaitingFor(AEKey key, long amount) { host.omniNative$expect(key, amount); }
            @Override public void addContainerMaxItems(long count, AEKeyType type) {
                if (count != 0) throw new IllegalStateException("Unexpected native processing remainder");
            }
            @Override public void failDispatch(String reason, Throwable cause) { host.omniNative$fail(reason, cause); }
        };
        // This suppression map belongs to one executor call. Successful slices
        // may be retried within the tick under the CPU's remaining shared budget.
        batched.clear();
        try (var dispatchScope = OmniNativeDispatchScope.open(pattern, nativeProviders)) {
            var mode = host.omniNative$virtualCpu()
                    ? BatchCpuAccounting.Mode.SUCCESSFUL_DISPATCH : BatchCpuAccounting.Mode.LINEAR;
            var result = BatchExecutor.runBatchOnly(maximum, mode, crafting, energy, job,
                    host.omniNative$inventory(), batched, host::omniNative$markDirty,
                    Map.of(), Integer.MAX_VALUE, copies, false, schedule);
            if (host.omniNative$stopped()) return maximum;
            return result.dispatchedCopies() > 0 ? result.consumedCpuOps() : -1;
        } catch (RuntimeException failure) {
            // Never retry or refund an indeterminate ownership transfer.
            host.omniNative$fail("THUNDERBOLT_DISPATCH_FAILURE", failure);
            return maximum;
        }
    }
}
