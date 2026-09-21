package com.atir.molecularmanipulator.integration.useless;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.hooks.ticking.TickHandler;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.api.crafting.OmniBigIntegerOutputReceiver;
import com.atir.molecularmanipulator.config.ModConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.level.Level;

/** Optional, schema-checked adapter for the public-API build's existing persistent output queue. */
public final class UselessExactOutputReturn {
    private static final long GLOBAL_DIRECT_BUDGET_NANOS = 2_000_000L;
    private static long budgetTick = Long.MIN_VALUE, spentThisTick;
    private static long keysTransferred, batchesCompleted, totalNanos, nextReport;
    private static BigInteger equivalentSegments = BigInteger.ZERO;
    private static final BigInteger MAX_LONG = BigInteger.valueOf(Long.MAX_VALUE);
    private static final Map<Object, Route> VERIFIED = new java.util.WeakHashMap<>();
    /** Managers that have accepted a batch through Omni's CPU-bound API. */
    private static final java.util.Set<Object> CANDIDATES =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static final class Route {
        final java.lang.ref.WeakReference<IGrid> grid;
        Route(IGrid grid) {
            this.grid = new java.lang.ref.WeakReference<>(grid);
        }
    }
    private static final Map<String, Long> REASONS = new java.util.LinkedHashMap<>();
    private static long diagnosticDeadline;
    private static final ClassValue<Optional<Method>> CONTROLLERS = new ClassValue<>() {
        @Override protected Optional<Method> computeValue(Class<?> provider) {
            try { return Optional.of(provider.getMethod("getController")); }
            catch (NoSuchMethodException missing) { return Optional.empty(); }
        }
    };
    private static final ClassValue<Optional<Field>> MANAGERS = new ClassValue<>() {
        @Override protected Optional<Field> computeValue(Class<?> controller) {
            try { var field = controller.getDeclaredField("aeManager"); field.setAccessible(true); return Optional.of(field); }
            catch (ReflectiveOperationException | RuntimeException missing) { return Optional.empty(); }
        }
    };
    private static final ClassValue<Optional<Layout>> LAYOUTS = new ClassValue<>() {
        @Override protected Optional<Layout> computeValue(Class<?> type) {
            try { return Optional.of(new Layout(type)); }
            catch (ReflectiveOperationException | RuntimeException failure) {
                MolecularManipulator.LOGGER.warn("Native exact output queue layout unavailable; using ordinary network return: {}", type.getName());
                return Optional.empty();
            }
        }
    };
    private UselessExactOutputReturn() { }

    public static Object managerOf(Object provider) {
        try {
            var getter = CONTROLLERS.get(provider.getClass());
            if (getter.isEmpty()) return null;
            Object controller = getter.get().invoke(provider);
            if (controller == null) return null;
            var field = MANAGERS.get(controller.getClass());
            return field.isEmpty() ? null : field.get().get(controller);
        } catch (ReflectiveOperationException failure) { return null; }
    }

    /** Enable the direct queue pass only after Omni has submitted a bound batch. */
    public static synchronized void enable(Object manager) {
        if (manager != null) CANDIDATES.add(manager);
    }

    public static boolean isVerified(Object manager, IGrid grid) {
        var verified = VERIFIED.get(manager);
        if (!ModConfig.OMNI_DIRECT_NATIVE_OUTPUT_RETURN.get() || manager == null || grid == null || verified == null || verified.grid.get() != grid) return false;
        var layout = LAYOUTS.get(manager.getClass());
        if (layout.isEmpty()) return false;
        try {
            var host = layout.get().owner.get(manager);
            return layout.get().grid.invoke(host) == grid && layout.get().outputTarget.invoke(host) != null;
        }
        catch (ReflectiveOperationException failure) { return false; }
    }

    public static BigInteger adaptiveWindow(Object manager) {
        return BigInteger.ZERO;
    }

    /**
     * UselessMod's normal hard chunk guard is sized for segmented AE output.
     * A verified direct CPU route does not leave those chunks in the network;
     * keep the guard, but scale it to one direct window plus one in-flight window.
     */
    public static long directChunkBudget(Object manager, long original) {
        if (original <= 0L || !ModConfig.OMNI_DIRECT_NATIVE_OUTPUT_RETURN.get()) {
            return original;
        }
        return original;
    }

    static void admissionRejected(Object manager) {
        // The next attempt probes Useless capacity again directly.
    }

    private static long oldestAge(Object manager, Layout layout, Level level) throws ReflectiveOperationException {
        var queue = (List<?>) layout.queue.get(manager);
        return queue.isEmpty() || level == null ? 0 : Math.max(0, level.getGameTime() - layout.queuedTick.getLong(queue.getFirst()));
    }

    /** Invoked only by Omni's post-accounting registry, never a cancellation/force flush. */
    public static void flushAfterAccounting(Object provider) {
        Object manager = managerOf(provider);
        if (manager != null) flush(manager, true);
    }

    public static void diagnostic(String reason) {
        REASONS.merge(reason, 1L, Long::sum);
        long now = System.nanoTime();
        if (diagnosticDeadline == 0) diagnosticDeadline = now + 10_000_000_000L;
        if (now >= diagnosticDeadline) {
            MolecularManipulator.LOGGER.info("Omni direct return diagnostics: {}", REASONS);
            REASONS.clear(); diagnosticDeadline = now + 10_000_000_000L;
        }
    }

    @SuppressWarnings("unchecked")
    public static BigInteger flush(Object manager) {
        return flush(manager, false);
    }

    @SuppressWarnings("unchecked")
    private static BigInteger flush(Object manager, boolean afterAccounting) {
        if (!ModConfig.OMNI_DIRECT_NATIVE_OUTPUT_RETURN.get()) return BigInteger.ZERO;
        // UselessMod also owns ordinary Data Energistics output queues. Those
        // queues carry foreign CPU tokens and must be left to Useless itself;
        // scanning them every tick only adds latency and log noise. A manager
        // becomes a candidate when Omni successfully admits a bound batch.
        synchronized (CANDIDATES) {
            if (!CANDIDATES.contains(manager)) return BigInteger.ZERO;
        }
        var optional = LAYOUTS.get(manager.getClass());
        if (optional.isEmpty()) return BigInteger.ZERO;
        var layout = optional.get();
        long started = 0;
        long returnAge = 0;
        BigInteger transferred = BigInteger.ZERO;
        try {
            var queue = (List<Object>) layout.queue.get(manager);
            if (queue.isEmpty()) return BigInteger.ZERO;
            Object host = layout.owner.get(manager);
            var level = (Level) layout.level.invoke(host);
            if (level == null || level.isClientSide || level.getServer() == null || !level.getServer().isSameThread()
                    || !(Boolean) layout.supported.invoke(host)) return BigInteger.ZERO;
            var grid = (IGrid) layout.grid.invoke(host);
            if (grid == null || layout.outputTarget.invoke(host) == null) { diagnostic("source-offline"); return BigInteger.ZERO; }
            long tick = TickHandler.instance().getCurrentTick();
            if (budgetTick != tick) { budgetTick = tick; spentThisTick = 0; }
            if (spentThisTick >= GLOBAL_DIRECT_BUDGET_NANOS) { diagnostic("time-budget"); return BigInteger.ZERO; }
            started = System.nanoTime();
            // The Useless manager queue is shared by ordinary AE output and
            // native BigInteger batches.  The scan limit must apply to
            // bound Omni entries only: counting foreign entries can starve a
            // bound batch that happens to be behind a large ordinary backlog.
            int inspected = 0;
            var iterator = queue.iterator();
            while (iterator.hasNext()
                    && spentThisTick + System.nanoTime() - started < GLOBAL_DIRECT_BUDGET_NANOS) {
                Object pending = iterator.next();
                if ((!afterAccounting && layout.queuedTick.getLong(pending) >= level.getGameTime()) || layout.notified.getBoolean(pending)) continue;
                Object context = layout.context.get(pending);
                if (context == null || !(layout.token.invoke(context) instanceof OmniBigIntegerOutputReceiver receiver)) {
                    // This is a normal Useless/Data Energistics entry. It is
                    // owned by Useless and must remain on its ordinary return
                    // path; it is not an Omni failure.
                    continue;
                }
                if (inspected++ >= 256) break;
                Object ledger = layout.ledger.get(pending);
                var amounts = (Map<AEKey, BigInteger>) layout.amounts.get(ledger);
                var offered = new java.util.LinkedHashMap<AEKey, BigInteger>(amounts);
                if (offered.isEmpty()) continue;
                BigInteger outstanding = (BigInteger) layout.pendingAmount.get(manager);
                var debited = new java.util.LinkedHashMap<AEKey, BigInteger>();
                offered.forEach((key, value) -> {
                    if (outstanding.compareTo(value) < 0) throw new IllegalStateException("Native output backlog is inconsistent");
                });
                Map<AEKey, BigInteger> accepted;
                try {
                    accepted = receiver.transferOutputs(grid, offered, amountsTaken -> {
                        amountsTaken.forEach((key, value) -> {
                            var debit = new SourceDebit(amounts, key, offered.getOrDefault(key, BigInteger.ZERO), manager, layout.pendingAmount);
                            debit.accept(value);
                            debited.put(key, debit.debited);
                        });
                    });
                } finally {
                    // Observers may throw after both inventories committed. The
                    // source must still be saved, and must never be replayed.
                    if (!debited.isEmpty()) layout.dirty.invoke(host);
                }
                if (accepted == null) accepted = Map.of();
                if (!accepted.equals(debited)) throw new IllegalStateException("Native receiver acknowledgement differs from debit");
                if (accepted.isEmpty()) {
                    diagnostic("receiver-declined");
                } else {
                    var route = VERIFIED.get(manager);
                    if (route == null || route.grid.get() != grid) VERIFIED.put(manager, new Route(grid));
                    returnAge = Math.max(returnAge, Math.max(0, level.getGameTime() - layout.queuedTick.getLong(pending)));
                    diagnostic(afterAccounting ? "accepted-same-tick" : "accepted-block-tick");
                    for (BigInteger value : accepted.values()) {
                        transferred = transferred.add(value); keysTransferred++;
                        equivalentSegments = equivalentSegments.add(value.add(MAX_LONG).subtract(BigInteger.ONE).divide(MAX_LONG));
                    }
                }
                if (amounts.isEmpty()) {
                    iterator.remove(); batchesCompleted++;
                    layout.completed.invoke(null, pending);
                }
            }
            return transferred;
        } catch (ReflectiveOperationException failure) {
            // Never silently retry a queue after an ownership mutation failed.
            throw new IllegalStateException("Native exact output transfer failed", failure);
        } finally {
            if (started != 0) {
                long elapsed = System.nanoTime() - started;
                spentThisTick += elapsed; totalNanos += elapsed;
                try { layout.addWork.invoke(null, elapsed); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException("Native transfer budget accounting failed", failure); }
                reportIfDue();
            }
        }
    }

    private static final class SourceDebit {
        final Map<AEKey, BigInteger> amounts;
        final AEKey key;
        final BigInteger offered;
        final Object manager;
        final Field pendingAmount;
        BigInteger debited = BigInteger.ZERO;
        SourceDebit(Map<AEKey, BigInteger> amounts, AEKey key, BigInteger offered, Object manager, Field pendingAmount) {
            this.amounts = amounts; this.key = key; this.offered = offered; this.manager = manager; this.pendingAmount = pendingAmount;
        }
        void accept(BigInteger count) {
            if (debited.signum() != 0 || count.signum() <= 0 || count.compareTo(offered) > 0
                    || !offered.equals(amounts.get(key))) throw new IllegalStateException("Invalid native source debit");
            try {
                var backlog = (BigInteger) pendingAmount.get(manager);
                if (backlog.compareTo(count) < 0) throw new IllegalStateException("Negative native backlog");
                pendingAmount.set(manager, backlog.subtract(count));
                var rest = offered.subtract(count);
                if (rest.signum() == 0) amounts.remove(key); else amounts.put(key, rest);
                debited = count;
            } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        }
    }

    private static void reportIfDue() {
        long now = System.nanoTime();
        if (nextReport == 0) nextReport = now + 10_000_000_000L;
        if (now < nextReport || keysTransferred == 0) return;
        MolecularManipulator.LOGGER.info(
                "Omni native output transfer: keys={}, completedBatches={}, equivalentLongSegments={}, elapsedMs={}",
                keysTransferred, batchesCompleted, equivalentSegments, totalNanos / 1_000_000.0);
        keysTransferred = batchesCompleted = totalNanos = 0; equivalentSegments = BigInteger.ZERO;
        nextReport = now + 10_000_000_000L;
    }

    private static final class Layout {
        final Field queue, owner, pendingAmount, queuedTick, context, ledger, amounts, notified;
        final Method level, grid, supported, dirty, token, completed, addWork, outputTarget;
        Layout(Class<?> manager) throws ReflectiveOperationException {
            queue = field(manager, "queuedCraftingOutputs", List.class);
            owner = manager.getDeclaredField("owner"); owner.setAccessible(true);
            pendingAmount = field(manager, "pendingOutputAmount", BigInteger.class);
            Class<?> pending = Class.forName(manager.getName() + "$PendingCraftingOutput", false, manager.getClassLoader());
            queuedTick = field(pending, "queuedTick", long.class);
            notified = field(pending, "cpuNotified", boolean.class);
            context = pending.getDeclaredField("cpuContext"); context.setAccessible(true);
            ledger = pending.getDeclaredField("ledger"); ledger.setAccessible(true);
            amounts = ledger.getType().getDeclaredField("amounts"); amounts.setAccessible(true);
            if (!Map.class.isAssignableFrom(amounts.getType())) throw new NoSuchFieldException("Unsupported output ledger");
            level = owner.getType().getMethod("getLevel"); grid = owner.getType().getMethod("getAeGrid");
            outputTarget = owner.getType().getMethod("resolveAeOutputTarget");
            supported = owner.getType().getMethod("supportsBigIntegerRecipeBatches"); dirty = owner.getType().getMethod("markChanged");
            token = context.getType().getMethod("cpuToken");
            completed = manager.getDeclaredMethod("notifyCpuBatchCompleted", pending); completed.setAccessible(true);
            addWork = Class.forName(manager.getPackageName() + ".AlloyFurnaceTickBudget", false, manager.getClassLoader()).getMethod("addWork", long.class);
        }
        private static Field field(Class<?> type, String name, Class<?> expected) throws ReflectiveOperationException {
            var result = type.getDeclaredField(name);
            if (!expected.isAssignableFrom(result.getType())) throw new NoSuchFieldException("Unsupported " + name);
            result.setAccessible(true); return result;
        }
    }
}
