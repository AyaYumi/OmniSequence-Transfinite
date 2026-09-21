package com.atir.molecularmanipulator.integration.useless;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.api.crafting.OmniBigIntegerBatchCallbacks;
import com.atir.molecularmanipulator.api.crafting.OmniBigIntegerCraftingProvider;
import com.atir.molecularmanipulator.api.crafting.OmniBigIntegerOutput;
import com.atir.molecularmanipulator.api.crafting.OmniBigIntegerOutputReceiver;
import com.atir.molecularmanipulator.api.crafting.OmniBigIntegerProviderAdapterRegistry;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.List;

/**
 * Optional bridge for UselessMod's public multiblock BigInteger API.
 * Everything is resolved reflectively so OmniSequence remains loadable without
 * UselessMod installed, while the provider identity stays the original AE2
 * provider object.
 */
public final class UselessBigIntegerApiBridge {
    private static final String PROVIDER_CLASS =
            "com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerProvider";
    private static final String TARGET_CLASS =
            "com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerTarget";
    private static final String BATCH_CLASS =
            "com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerBatch";
    private static final String BINDING_CLASS =
            "com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuBinding";
    private static final String ADAPTER_CLASS =
            "com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuAdapter";
    private static final String ADAPTERS_CLASS =
            "com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerCpuAdapters";
    private static final ResourceLocation CPU_ADAPTER_ID =
            ResourceLocation.fromNamespaceAndPath("omnisequence", "useless_bigint_cpu");
    /**
     * A capacity probe and the following atomic admission can observe different
     * lane budgets.  When Useless returns a native count that cuts through a
     * smart-doubling wrapper, retry with the largest complete wrapper count
     * instead of abandoning the native CPU-bound route.
     */
    private static final int WHOLE_TASK_ADMISSION_RETRIES = 8;

    private static volatile boolean attempted;
    private static volatile boolean available;

    private UselessBigIntegerApiBridge() {
    }

    /** Registers the bridge once during common setup; silently absent on old Useless builds. */
    public static synchronized void register() {
        if (attempted) {
            return;
        }
        attempted = true;
        try {
            ClassLoader loader = UselessBigIntegerApiBridge.class.getClassLoader();
            Class<?> providerType = Class.forName(PROVIDER_CLASS, false, loader);
            Class<?> targetType = Class.forName(TARGET_CLASS, false, loader);
            Class<?> batchType = Class.forName(BATCH_CLASS, false, loader);
            Class<?> bindingType = Class.forName(BINDING_CLASS, false, loader);
            Class<?> adapterType = Class.forName(ADAPTER_CLASS, false, loader);
            Class<?> adaptersType = Class.forName(ADAPTERS_CLASS, false, loader);
            Method targetMethod = providerType.getMethod("bigIntegerTarget");
            Method gridMethod = targetType.getMethod("grid");
            Method capacity = targetType.getMethod("capacity", IPatternDetails.class,
                    KeyCounter[].class, BigInteger.class);
            Method admit = targetType.getMethod("admit", IPatternDetails.class,
                    KeyCounter[].class, BigInteger.class, bindingType);
            Method commit = batchType.getMethod("commit", KeyCounter[].class);
            Method accepted = capacity.getReturnType().getMethod("accepted");
            Method isAvailable = capacity.getReturnType().getMethod("isAvailable");
            Method count = batchType.getMethod("count");
            Class<?> scaledType = Class.forName(
                    "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.ScaledPattern", false, loader);
            Method original = scaledType.getMethod("getOriginal");
            Method multiplier = scaledType.getMethod("getOperationsPerPush");
            Constructor<?> bindingCtor = bindingType.getConstructor(ResourceLocation.class, Object.class);
            Method register = adaptersType.getMethod("register", adapterType);

            register.invoke(null, Proxy.newProxyInstance(
                    adapterType.getClassLoader(), new Class<?>[]{adapterType},
                    new CpuCallbackHandler()));

            OmniBigIntegerProviderAdapterRegistry.register(
                    "omnisequence:useless_multiblock_alloy_furnace_api", 250,
                    (provider, pattern) -> {
                        ICraftingProvider delegate = unwrapProvider(provider);
                        return providerType.isInstance(delegate)
                                && hasTarget(delegate, targetMethod);
                    },
                    (provider, pattern) -> {
                        ICraftingProvider delegate = unwrapProvider(provider);
                        return providerType.isInstance(delegate)
                                && hasTarget(delegate, targetMethod)
                                ? new TargetAdapter(
                                        delegate, targetMethod, capacity, admit, commit,
                                        accepted, isAvailable, count, bindingCtor,
                                        scaledType, original, multiplier, gridMethod)
                                : null;
                    });
            available = true;
            com.atir.molecularmanipulator.api.crafting.OmniPostAccountingOutputAdapterRegistry.register(
                    "omnisequence:useless_native_direct_return", 300,
                    provider -> providerType.isInstance(unwrapProvider(provider))
                            && UselessExactOutputReturn.managerOf(unwrapProvider(provider)) != null,
                    provider -> UselessExactOutputReturn.flushAfterAccounting(unwrapProvider(provider)));
            MolecularManipulator.LOGGER.info(
                    "Registered UselessMod public BigInteger API bridge with same-tick direct return");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            MolecularManipulator.LOGGER.debug(
                    "UselessMod public BigInteger API is unavailable", exception);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    private static boolean hasTarget(ICraftingProvider provider, Method targetMethod) {
        try {
            return targetMethod.invoke(provider) != null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return false;
        }
    }

    /**
     * Applied Enhancements may expose a temporary provider proxy during its
     * cycle dispatch scope. The proxy keeps the original provider in its
     * invocation handler; resolve that delegate so the public Useless API is
     * still selected ahead of the legacy reflective adapter.
     */
    private static ICraftingProvider unwrapProvider(ICraftingProvider provider) {
        if (provider == null || !Proxy.isProxyClass(provider.getClass())) {
            return provider;
        }
        try {
            InvocationHandler handler = Proxy.getInvocationHandler(provider);
            for (Class<?> type = handler.getClass(); type != null;
                    type = type.getSuperclass()) {
                try {
                    var field = type.getDeclaredField("provider");
                    if (!field.trySetAccessible()) {
                        return provider;
                    }
                    Object delegate = field.get(handler);
                    return delegate instanceof ICraftingProvider
                            ? (ICraftingProvider) delegate : provider;
                } catch (NoSuchFieldException ignored) {
                    // Continue through the handler's enclosing classes.
                }
            }
        } catch (RuntimeException | IllegalAccessException ignored) {
            // A foreign proxy is simply treated as an ordinary provider.
        }
        return provider;
    }

    static final class TargetAdapter implements OmniBigIntegerCraftingProvider {
        private final ICraftingProvider provider;
        private final Method targetMethod;
        private final Method capacity;
        private final Method admit;
        private final Method commit;
        private final Method accepted;
        private final Method isAvailable;
        private final Method count;
        private final Constructor<?> bindingCtor;
        private final Class<?> scaledType;
        private final Method original;
        private final Method multiplier;
        private final Method gridMethod;
        private volatile BigInteger lastAcceptedTasks;

        TargetAdapter(ICraftingProvider provider, Method targetMethod,
                              Method capacity, Method admit, Method commit,
                              Method accepted, Method isAvailable, Method count,
                              Constructor<?> bindingCtor, Class<?> scaledType,
                              Method original, Method multiplier, Method gridMethod) {
            this.provider = provider;
            this.targetMethod = targetMethod;
            this.capacity = capacity;
            this.admit = admit;
            this.commit = commit;
            this.accepted = accepted;
            this.isAvailable = isAvailable;
            this.count = count;
            this.bindingCtor = bindingCtor;
            this.scaledType = scaledType;
            this.original = original;
            this.multiplier = multiplier;
            this.gridMethod = gridMethod;
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return provider.getAvailablePatterns();
        }

        @Override
        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
            return provider.pushPattern(pattern, inputs);
        }

        @Override
        public boolean isBusy() {
            return provider.isBusy();
        }

        @Override
        public boolean usesNativeBigIntegerBatch() {
            return true;
        }

        @Override
        public BigInteger getMaximumBigIntegerCrafts(IPatternDetails pattern,
                                                     KeyCounter[] prototype,
                                                     BigInteger requested) {
            try {
                Object target = targetMethod.invoke(provider);
                if (target == null) return BigInteger.ZERO;
                var shape = UselessBigIntegerBatchShape.resolve(
                        pattern, prototype, scaledType, original, multiplier);
                Object result = OmniDirectAdmission.withSender(UselessExactOutputReturn.managerOf(provider), shape.minimumOutput(),
                        () -> capacity.invoke(target, shape.pattern(), shape.prototype(),
                                OmniDirectAdmission.limitRequested(shape.pattern(), shape.nativeCount(requested))));
                if (!(Boolean) isAvailable.invoke(result)) return BigInteger.ZERO;
                return shape.wholeTasks((BigInteger) accepted.invoke(result)).min(requested);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                return BigInteger.ZERO;
            }
        }

        @Override
        public boolean pushBigIntegerCraftingPattern(IPatternDetails pattern,
                                                      BigInteger craftCount,
                                                      KeyCounter[] prototype) {
            return pushBigIntegerCraftingPattern(pattern, craftCount, prototype, null);
        }

        @Override
        public boolean pushBigIntegerCraftingPattern(IPatternDetails pattern,
                                                      BigInteger craftCount,
                                                      KeyCounter[] prototype,
                                                      Object completionToken) {
            lastAcceptedTasks = BigInteger.ZERO;
            try {
                Object target = targetMethod.invoke(provider);
                if (target == null) return false;
                Object binding = completionToken == null ? null
                        : bindingCtor.newInstance(CPU_ADAPTER_ID,
                                new CpuBindingToken(completionToken, targetGrid(target)));
                var shape = UselessBigIntegerBatchShape.resolve(
                        pattern, prototype, scaledType, original, multiplier);
                BigInteger nativeCount = shape.nativeCount(craftCount);
                Object manager = UselessExactOutputReturn.managerOf(provider);
                boolean committed = OmniDirectAdmission.withSender(manager, shape.minimumOutput(), () -> {
                    if (OmniDirectAdmission.limitRequested(shape.pattern(), nativeCount).compareTo(nativeCount) < 0) {
                        UselessExactOutputReturn.diagnostic("commit-window-limit");
                        return false;
                    }
                    Object batch = admitWholeTaskBatch(target, shape, nativeCount, binding);
                    if (batch == null) {
                        UselessExactOutputReturn.diagnostic("commit-admit-null");
                        return false;
                    }
                    BigInteger admitted = (BigInteger) count.invoke(batch);
                    if (admitted.signum() <= 0 || admitted.compareTo(nativeCount) > 0) {
                        UselessExactOutputReturn.diagnostic("commit-count-invalid");
                        return false;
                    }
                    BigInteger wholeTasks = admitted.divide(shape.operationsPerTask());
                    if (wholeTasks.signum() <= 0
                            || !wholeTasks.multiply(shape.operationsPerTask()).equals(admitted)) {
                        // admitWholeTaskBatch only returns complete wrapper
                        // tasks. Keep this guard as an ABI safety net in case
                        // a future Useless build violates that contract.
                        UselessExactOutputReturn.diagnostic("commit-count-partial-task");
                        return false;
                    }
                    if (!admitted.equals(nativeCount)) {
                        // Useless 2.3.9 may shrink the admission between the
                        // capacity probe and this atomic commit when another
                        // lane consumes the remaining window. The returned
                        // batch count is authoritative; the caller reconciles
                        // its input ledger to this actual count.
                        UselessExactOutputReturn.diagnostic("commit-count-adjusted");
                    }
                    boolean accepted = (Boolean) commit.invoke(batch, (Object) shape.prototype());
                    if (!accepted) UselessExactOutputReturn.diagnostic("commit-provider-rejected");
                    if (accepted) lastAcceptedTasks = wholeTasks;
                    return accepted;
                });
                if (committed) {
                    UselessExactOutputReturn.enable(manager);
                    for (var counter : prototype) counter.clear();
                } else UselessExactOutputReturn.admissionRejected(manager);
                return committed;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                UselessExactOutputReturn.diagnostic("commit-exception-" + exception.getClass().getSimpleName());
                return false;
            }
        }

        /**
         * Admit a native batch whose count is divisible by the smart-doubling
         * multiplier.  Useless' capacity can shrink between the probe and the
         * atomic admission, so the first result may be a valid but indivisible
         * base-operation count.  Such a credential has not been committed and
         * is safe to discard; retrying with its largest complete wrapper prefix
         * preserves exact CPU accounting while keeping the native route alive.
         */
        private Object admitWholeTaskBatch(Object target,
                                           UselessBigIntegerBatchShape shape,
                                           BigInteger requested,
                                           Object binding)
                throws ReflectiveOperationException {
            BigInteger candidate = requested;
            BigInteger operations = shape.operationsPerTask();
            for (int attempt = 0;
                 attempt < WHOLE_TASK_ADMISSION_RETRIES && candidate.signum() > 0;
                 attempt++) {
                Object batch = admit.invoke(target, shape.pattern(), shape.prototype(), candidate, binding);
                if (batch == null) {
                    return null;
                }
                BigInteger admitted = (BigInteger) count.invoke(batch);
                if (admitted == null || admitted.signum() <= 0 || admitted.compareTo(candidate) > 0) {
                    UselessExactOutputReturn.diagnostic("commit-count-invalid");
                    return null;
                }
                BigInteger[] division = admitted.divideAndRemainder(operations);
                if (division[1].signum() == 0) {
                    if (!admitted.equals(candidate)) {
                        UselessExactOutputReturn.diagnostic("commit-count-adjusted");
                    }
                    return batch;
                }
                BigInteger aligned = division[0].multiply(operations);
                UselessExactOutputReturn.diagnostic("commit-count-partial-task-retry");
                if (aligned.signum() <= 0 || aligned.compareTo(candidate) >= 0) {
                    return null;
                }
                candidate = aligned;
            }
            UselessExactOutputReturn.diagnostic("commit-count-partial-task-exhausted");
            return null;
        }

        @Override
        public BigInteger lastAcceptedBigIntegerCrafts() {
            return lastAcceptedTasks;
        }

        private IGrid targetGrid(Object target) {
            try {
                if (gridMethod == null) return null;
                Object grid = gridMethod.invoke(target);
                return grid instanceof IGrid ? (IGrid) grid : null;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
    }

    private static final class CpuCallbackHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if (name.equals("id")) return CPU_ADAPTER_ID;
            if (name.equals("onBatchAdmitted") && args != null && args.length >= 1) {
                deliverAdmitted(args[0]);
            } else if (name.equals("onBatchOutputs") && args != null && args.length >= 2) {
                deliver(args[0], args[1], false);
            } else if (name.equals("onBatchFinished") && args != null && args.length >= 2) {
                Object result = args[1];
                if (result != null && "CANCELLED".equals(result.toString())) {
                    deliver(args[0], null, true);
                }
            } else if (name.equals("claimOutputs") && args != null && args.length >= 2) {
                return claim(args[0], args[1]);
            }
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            return null;
        }

        private static void deliverAdmitted(Object context) {
            try {
                Method tokenMethod = context.getClass().getMethod("cpuToken");
                Object token = tokenMethod.invoke(context);
                if (!(token instanceof OmniBigIntegerBatchCallbacks callbacks)) return;
                Method plannedMethod = context.getClass().getMethod("plannedOutputs");
                Object planned = plannedMethod.invoke(context);
                callbacks.onAdmitted(readOutputs(planned));
            } catch (ReflectiveOperationException | RuntimeException exception) {
                MolecularManipulator.LOGGER.warn(
                        "Could not relay UselessMod BigInteger admission callback", exception);
            }
        }

        private static java.util.Map<appeng.api.stacks.AEKey, BigInteger> claim(
                Object context, Object outputs) {
            try {
                if (context == null || !(context.getClass().getMethod("cpuToken")
                        .invoke(context) instanceof OmniBigIntegerOutputReceiver receiver)) {
                    return java.util.Map.of();
                }
                if (!(outputs instanceof Iterable<?> iterable)) {
                    return java.util.Map.of();
                }
                java.util.Map<appeng.api.stacks.AEKey, BigInteger> accepted = new java.util.LinkedHashMap<>();
                for (Object output : iterable) {
                    Method keyMethod = output.getClass().getMethod("what");
                    Method amountMethod = output.getClass().getMethod("amount");
                    Object key = keyMethod.invoke(output);
                    BigInteger amount = (BigInteger) amountMethod.invoke(output);
                    if (!(key instanceof appeng.api.stacks.AEKey aeKey) || amount.signum() <= 0) {
                        continue;
                    }
                    // UselessMod debits its own pending ledger after this method returns.
                    // The CPU receiver therefore gets a no-op source debit callback here.
                    BigInteger taken = receiver.transferOutput(null, aeKey, amount, ignored -> { });
                    if (taken != null && taken.signum() > 0) {
                        accepted.merge(aeKey, taken.min(amount), BigInteger::add);
                    }
                }
                return java.util.Map.copyOf(accepted);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                MolecularManipulator.LOGGER.warn(
                        "Could not claim UselessMod BigInteger outputs for CPU", exception);
                return java.util.Map.of();
            }
        }

        private static void deliver(Object context, Object outputs, boolean cancelled) {
            try {
                Method tokenMethod = context.getClass().getMethod("cpuToken");
                Object token = tokenMethod.invoke(context);
                if (!(token instanceof OmniBigIntegerBatchCallbacks callbacks)) return;
                List<OmniBigIntegerOutput> exactOutputs = readOutputs(outputs);
                BigInteger amount = BigInteger.ZERO;
                for (var output : exactOutputs) {
                    amount = amount.add(output.amount());
                }
                if (outputs instanceof Iterable<?> iterable && exactOutputs.isEmpty()) {
                    for (Object output : iterable) {
                        Method amountMethod = output.getClass().getMethod("amount");
                        amount = amount.add((BigInteger) amountMethod.invoke(output));
                    }
                } else if (cancelled) {
                    Method plannedMethod = context.getClass().getMethod("plannedOutputs");
                    Object planned = plannedMethod.invoke(context);
                    if (planned instanceof Iterable<?> iterable) {
                        for (Object output : iterable) {
                            Method amountMethod = output.getClass().getMethod("amount");
                            amount = amount.add((BigInteger) amountMethod.invoke(output));
                        }
                    }
                }
                if (cancelled) callbacks.onCancelled(amount);
                else if (!exactOutputs.isEmpty()) callbacks.onOutputs(List.copyOf(exactOutputs));
            } catch (ReflectiveOperationException | RuntimeException exception) {
                MolecularManipulator.LOGGER.warn(
                        "Could not relay UselessMod BigInteger output callback", exception);
            }
        }

        private static List<OmniBigIntegerOutput> readOutputs(Object outputs)
                throws ReflectiveOperationException {
            if (!(outputs instanceof Iterable<?> iterable)) {
                return List.of();
            }
            List<OmniBigIntegerOutput> result = new java.util.ArrayList<>();
            for (Object output : iterable) {
                Method keyMethod = output.getClass().getMethod("what");
                Method amountMethod = output.getClass().getMethod("amount");
                Object key = keyMethod.invoke(output);
                BigInteger amount = (BigInteger) amountMethod.invoke(output);
                if (key instanceof appeng.api.stacks.AEKey aeKey && amount.signum() > 0) {
                    result.add(new OmniBigIntegerOutput(aeKey, amount));
                }
            }
            return List.copyOf(result);
        }
    }

    /**
     * Keeps the original CPU callback token while supplying the machine grid to
     * the exact output receiver.  The public Useless API deliberately exposes
     * no machine object in its callback context, but both the legacy direct
     * return path and the new claimOutputs path need the source grid for route
     * validation.
     */
    private static final class CpuBindingToken
            implements OmniBigIntegerBatchCallbacks, com.atir.molecularmanipulator.api.crafting.OmniBigIntegerOutputReceiver {
        private final Object delegate;
        private final IGrid grid;

        private CpuBindingToken(Object delegate, IGrid grid) {
            this.delegate = delegate;
            this.grid = grid;
        }

        @Override
        public BigInteger transferOutput(IGrid ignored, appeng.api.stacks.AEKey key,
                                         BigInteger offered, java.util.function.Consumer<BigInteger> debitSource) {
            if (delegate instanceof com.atir.molecularmanipulator.api.crafting.OmniBigIntegerOutputReceiver receiver) {
                return receiver.transferOutput(grid, key, offered, debitSource);
            }
            return BigInteger.ZERO;
        }

        @Override
        public void onAdmitted(List<OmniBigIntegerOutput> plannedOutputs) {
            if (delegate instanceof OmniBigIntegerBatchCallbacks callbacks) {
                callbacks.onAdmitted(plannedOutputs);
            }
        }

        @Override
        public void onOutputs(List<OmniBigIntegerOutput> outputs) {
            if (delegate instanceof OmniBigIntegerBatchCallbacks callbacks) {
                callbacks.onOutputs(outputs);
            }
        }

        @Override
        public void onCancelled(BigInteger plannedAmount) {
            if (delegate instanceof OmniBigIntegerBatchCallbacks callbacks) {
                callbacks.onCancelled(plannedAmount);
            }
        }
    }
}
