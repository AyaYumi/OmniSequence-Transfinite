package com.atir.molecularmanipulator.integration.thunderbolt;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityAdmission;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityRequest;
import com.moakiee.thunderbolt.api.crafting.batch.BatchDispatchMode;
import com.moakiee.thunderbolt.api.crafting.batch.BatchJobView;
import com.moakiee.thunderbolt.api.crafting.batch.BatchProviderAdapters;
import com.moakiee.thunderbolt.api.crafting.batch.BatchTaskHandle;
import com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.UUID;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Adapts one Thunderbolt provider endpoint to Omni's CPU-owned reservation. */
public final class OmniThunderboltCapacity {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private static final ClassValue<Boolean> EXPLICIT_CAPACITY = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getBatchCapacity", IPatternDetails.class)
                        .getDeclaringClass() != IBatchCraftingProvider.class;
            } catch (NoSuchMethodException unavailable) {
                return false;
            }
        }
    };

    private OmniThunderboltCapacity() {
    }

    public static @Nullable OmniCapacityAdmission prepare(OmniCapacityRequest request) {
        var provider = request.provider();
        if (usesLightningProbes(provider)) return null;

        var pattern = request.pattern();
        ProviderJob job = null;
        IBatchCraftingProvider endpoint;
        if (provider instanceof IBatchCraftingProvider nativeProvider) {
            endpoint = nativeProvider;
        } else {
            var adapters = BatchProviderAdapters.entries();
            if (adapters.isEmpty()) return null;
            job = new ProviderJob(request.level(), request.jobId());
            endpoint = null;
            for (var entry : adapters) {
                endpoint = entry.adapter().adapt(provider, pattern, job);
                if (endpoint != null) break;
            }
            if (endpoint == null) return null;
            endpoint.beginDispatchTick(TickHandler.instance().getCurrentTick());
        }

        long capacity = endpoint.getBatchCapacity(pattern);
        if (capacity <= 0) return OmniCapacityAdmission.UNAVAILABLE;
        // The older contract also uses one to advertise ordinary pushPattern support.
        if (capacity == 1) return null;

        long maximum = Math.min(capacity, request.maximumCrafts().min(LONG_MAX).longValueExact());
        if (maximum <= 0) return OmniCapacityAdmission.UNAVAILABLE;

        var preparedApi = PreparedApi.INSTANCE;
        if (preparedApi != null && PreparedApi.OVERRIDES_PREPARE.get(endpoint.getClass())) {
            var jobContext = job != null ? job : new ProviderJob(request.level(), request.jobId());
            var prepared = preparedApi.prepare(endpoint, pattern, copyPrototype(request.unitInputs()), maximum,
                    jobContext);
            if (prepared != null) {
                long admitted = Math.min(maximum, preparedApi.capacity(prepared));
                if (admitted <= 0) return OmniCapacityAdmission.UNAVAILABLE;
                return new ThunderboltBatchAdmission(admitted) {
                    @Override long push(long count) { return preparedApi.push(prepared, count); }
                };
            }
        }

        if (!hasLegacyCapacity(endpoint, pattern, capacity)) return null;
        var template = copyPrototype(request.unitInputs());
        var jobContext = job != null ? job : new ProviderJob(request.level(), request.jobId());
        var selected = endpoint;
        return new ThunderboltBatchAdmission(maximum) {
            @Override long push(long count) { return selected.pushBatch(pattern, template, count, jobContext); }
        };
    }

    private static boolean hasLegacyCapacity(IBatchCraftingProvider endpoint, IPatternDetails pattern, long capacity) {
        var type = endpoint.getClass();
        if (type.getName().equals("com.moakiee.thunderbolt.compat.extendedaeplus.ExtendedAePlusBatchAdapter$Endpoint")) {
            // Its legacy cap is an arithmetic/configuration limit for a scaled push, independent of the target.
            return false;
        }
        // The default Long.MAX_VALUE conveys readiness only. Require an explicit
        // capacity declaration before treating an UNBOUNDED endpoint as batchable.
        return capacity < Long.MAX_VALUE || (endpoint.getBatchDispatchMode(pattern) == BatchDispatchMode.UNBOUNDED
                && EXPLICIT_CAPACITY.get(type));
    }

    private static boolean usesLightningProbes(ICraftingProvider provider) {
        for (Class<?> type = provider.getClass(); type != null; type = type.getSuperclass()) {
            if (type.getName().equals("com.moakiee.ae2lt.logic.provider.OverloadedPatternProviderLogic")) {
                // Its Thunderbolt capacity reports eligibility for adaptive insertion probes.
                return true;
            }
        }
        return false;
    }

    private static KeyCounter[] copyPrototype(KeyCounter[] source) {
        var result = new KeyCounter[source.length];
        for (int i = 0; i < source.length; i++) {
            var slot = new KeyCounter();
            slot.addAll(source[i]);
            result[i] = slot;
        }
        return result;
    }

    /** Exposes job identity and level while retaining Omni's task and output accounting. */
    private record ProviderJob(Level level, UUID craftingId) implements BatchJobView {
        @Override public Iterator<BatchTaskHandle> taskIterator() {
            throw new UnsupportedOperationException("Omni owns the crafting tasks");
        }

        @Override public ListCraftingInventory waitingFor() {
            throw new UnsupportedOperationException("Omni owns the expected outputs");
        }

        @Override public void addContainerMaxItems(long count, AEKeyType type) {
            throw new UnsupportedOperationException("Omni owns the expected remainders");
        }
    }

    /** PreparedBatch is absent from older compatible Thunderbolt API builds. */
    private record PreparedApi(MethodHandle prepare, MethodHandle capacity, MethodHandle push) {
        private static final PreparedApi INSTANCE = find();
        private static final ClassValue<Boolean> OVERRIDES_PREPARE = new ClassValue<>() {
            @Override protected Boolean computeValue(Class<?> type) {
                try {
                    return type.getMethod("prepareBatch", IPatternDetails.class,
                            KeyCounter[].class, long.class, BatchJobView.class)
                            .getDeclaringClass() != IBatchCraftingProvider.class;
                } catch (NoSuchMethodException unavailable) {
                    return false;
                }
            }
        };

        private static @Nullable PreparedApi find() {
            try {
                var method = IBatchCraftingProvider.class.getMethod("prepareBatch", IPatternDetails.class,
                        KeyCounter[].class, long.class, BatchJobView.class);
                var type = method.getReturnType();
                var lookup = MethodHandles.publicLookup();
                var prepare = lookup.unreflect(method);
                var capacity = lookup.findVirtual(type, "capacity", MethodType.methodType(long.class));
                var push = lookup.findVirtual(type, "push", MethodType.methodType(long.class, long.class));
                return new PreparedApi(prepare.asType(prepare.type().changeReturnType(Object.class)),
                        capacity.asType(capacity.type().changeParameterType(0, Object.class)),
                        push.asType(push.type().changeParameterType(0, Object.class)));
            } catch (ReflectiveOperationException unavailable) {
                return null;
            }
        }

        private Object prepare(IBatchCraftingProvider provider, IPatternDetails pattern,
                KeyCounter[] inputs, long maximum, BatchJobView job) {
            try {
                return (Object) prepare.invokeExact(provider, pattern, inputs, maximum, job);
            } catch (Throwable failure) {
                throw propagate(failure);
            }
        }

        private long capacity(Object prepared) {
            try {
                return (long) capacity.invokeExact(prepared);
            } catch (Throwable failure) {
                throw propagate(failure);
            }
        }

        private long push(Object prepared, long count) {
            try {
                return (long) push.invokeExact(prepared, count);
            } catch (Throwable failure) {
                throw propagate(failure);
            }
        }

        private static RuntimeException propagate(Throwable failure) {
            if (failure instanceof RuntimeException runtime) return runtime;
            if (failure instanceof Error error) throw error;
            return new IllegalStateException("Thunderbolt provider call failed", failure);
        }
    }
}
