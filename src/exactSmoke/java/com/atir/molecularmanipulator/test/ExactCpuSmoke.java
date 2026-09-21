package com.atir.molecularmanipulator.test;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import appeng.api.networking.crafting.*;
import appeng.crafting.CraftingPlan;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.*;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.appliedenhancements.api.*;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.mixin.CraftingCPUClusterAccessor;
import java.math.BigInteger;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Real transformed CPU insert/serialization/task-commit checks, with synthetic provider acceptance. */
@EventBusSubscriber(modid = "molecularmanipulator")
public final class ExactCpuSmoke {
    @SubscribeEvent public static void verify(ServerStartedEvent event) {
        try {
            var level = event.getServer().overworld();
            var core = new OmniComputationCoreBlockEntity(BlockPos.ZERO,
                    com.atir.molecularmanipulator.registry.ModContent.TRANSFINITE_COMPUTE_NEXUS.get().defaultBlockState());
            core.setLevel(level);
            var cpu = new CraftingCPUCluster(BlockPos.ZERO, BlockPos.ZERO);
            ((CraftingCPUClusterAccessor) (Object) cpu).molecularmanipulator$addBlockEntity(core);
            ((CraftingCPUClusterAccessor) (Object) cpu).molecularmanipulator$finishCluster();
            var ownersField = OmniComputationCoreBlockEntity.class.getDeclaredField("CPU_OWNERS");
            ownersField.setAccessible(true);
            ((Map) ownersField.get(null)).put(cpu, core);

            var input = AEItemKey.of(Items.COAL);
            var output = AEItemKey.of(Items.DIAMOND);
            var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(input, 1)), List.of(new GenericStack(output, 1))), level);
            var exact = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.TEN);
            var plan = AelisExactCraftingPlanApi.attachExecutionMetadata(new CraftingPlan(
                    new GenericStack(output, Long.MAX_VALUE), 1, false, false, new KeyCounter(), new KeyCounter(),
                    new KeyCounter(), Map.of(pattern, Long.MAX_VALUE)), exact, Map.of(pattern, exact), Map.of(input, exact));
            var data = new CompoundTag();
            data.putUUID("craftId", UUID.randomUUID()); data.putBoolean("req", false); data.putBoolean("standalone", true);
            var link = new CraftingLink(data, cpu);
            var ctor = Arrays.stream(ExecutingCraftingJob.class.getDeclaredConstructors())
                    .filter(c -> c.getParameterTypes()[0] == ICraftingPlan.class).findFirst().orElseThrow();
            ctor.setAccessible(true);
            var listenerType = ctor.getParameterTypes()[1];
            Object listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class[]{listenerType}, (p,m,a) -> null);
            Object job = ctor.newInstance(plan, listener, link, null);
            var logic = cpu.craftingLogic;
            set(logic, "job", job);
            var callback = new CallbackInfoReturnable<ICraftingSubmitResult>("smoke", true, CraftingSubmitResult.successful(link));
            method(logic, "attachExactCraftingState").invoke(logic, null, plan,
                    appeng.api.networking.security.IActionSource.empty(), null, callback);
            check(callback.getReturnValue().successful(), "CPU exact attachment");
            check(exact.equals(((AelisExactCraftingCpu) logic).aelis$getRemainingOutput()), "initial exact output");
            var updates = new appeng.menu.me.common.IncrementalUpdateHelper();
            updates.addChange(output);
            verifyStatus(logic, updates, exact, level.registryAccess(), true);
            updates.commitChanges();

            // Rejected provider attempts must not change tasks; only call commit on acceptance.
            var state = get(logic, "molecularmanipulator$exactState");
            var remainingMethod = state.getClass().getMethod("remaining", appeng.api.crafting.IPatternDetails.class);
            var inputs = new KeyCounter[]{new KeyCounter()};
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> rejected = arguments -> false;
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> accepted = arguments -> true;
            check(!(Boolean) method(logic, "pushBatch").invoke(logic, null, pattern, inputs, rejected), "provider rejection propagated");
            check(exact.equals(remainingMethod.invoke(state, pattern)), "unaccepted task unchanged");
            check((Boolean) method(logic, "pushBatch").invoke(logic, null, pattern, inputs, accepted), "provider acceptance propagated");
            check(exact.subtract(BigInteger.ONE).equals(remainingMethod.invoke(state, pattern)), "accepted provider consumes exactly one task");
            updates.addChange(output);
            verifyStatus(logic, updates, exact.subtract(BigInteger.ONE), level.registryAccess(), false);
            updates.commitChanges();
            var commit = Arrays.stream(logic.getClass().getDeclaredMethods())
                    .filter(m -> m.getName().endsWith("commitExactCraft")
                            && m.getParameterCount() == 2 && m.getParameterTypes()[1] == long.class)
                    .findFirst().orElseThrow();
            commit.setAccessible(true);
            commit.invoke(logic, pattern, Long.MAX_VALUE - 2);
            check(BigInteger.valueOf(11).equals(remainingMethod.invoke(state, pattern)), "accepted task crosses long window");
            // Model AE2's following decrement after successful provider push.
            var tasks = (Map) get(job, "tasks");
            var task = tasks.get(pattern); set(task, "value", 11L);
            var waiting = (appeng.crafting.inv.ListCraftingInventory) get(job, "waitingFor");
            waiting.insert(output, Long.MAX_VALUE - 1, Actionable.MODULATE);
            ((com.atir.molecularmanipulator.crafting.OmniExactCraftingState) state)
                    .queueOutput(output, BigInteger.valueOf(11));
            updates.addChange(output);
            verifyActiveStatus(logic, updates, output, exact, level.registryAccess());
            updates.commitChanges();
            logic.insert(output, Long.MAX_VALUE - 1, Actionable.SIMULATE);
            check(exact.equals(((AelisExactCraftingCpu) logic).aelis$getRemainingOutput()), "simulation does not deliver");
            check(((AelisExactCraftingCpu) logic).aelis$getCompletedOutputs().isEmpty(), "simulation does not count completed output");
            check(waiting.list.get(output) == Long.MAX_VALUE - 1, "simulation does not replenish waiting window");
            logic.insert(output, Long.MAX_VALUE - 1, Actionable.MODULATE);
            check(BigInteger.valueOf(11).equals(((AelisExactCraftingCpu) logic).aelis$getRemainingOutput()), "no premature completion");
            check(waiting.list.get(output) == 11, "standalone final output replenishes next window even when link returns zero");
            updates.addChange(output);
            verifyActiveStatus(logic, updates, output, BigInteger.valueOf(11), level.registryAccess());
            updates.commitChanges();
            var saved = new CompoundTag();
            logic.writeToNBT(saved, level.registryAccess());
            ((Map) ownersField.get(null)).remove(cpu);
            logic.readFromNBT(saved, level.registryAccess());
            ((Map) ownersField.get(null)).put(cpu, core);
            check(BigInteger.valueOf(11).equals(((AelisExactCraftingCpu) logic).aelis$getRemainingOutput()), "saved final output restored");
            var restoredState = get(logic, "molecularmanipulator$exactState");
            check(BigInteger.valueOf(11).equals(remainingMethod.invoke(restoredState, pattern)), "saved tasks rebound");
            job = get(logic, "job");
            waiting = (appeng.crafting.inv.ListCraftingInventory) get(job, "waitingFor");
            check(waiting.list.get(output) == 11, "replenished waiting window survives restore");
            logic.insert(output, 10, Actionable.MODULATE);
            check(BigInteger.ONE.equals(((AelisExactCraftingCpu) logic).aelis$getRemainingOutput()), "last item still needed");
            logic.insert(output, 1, Actionable.MODULATE);
            check(((AelisExactCraftingCpu) logic).aelis$getRemainingOutput() == null, "final delivery clears exact state");
            check(get(logic, "job") == null, "actual AE2 job finished");
            check(logic.getInventory().list.get(input) == 0, "virtual infinite inputs not refunded as physical stock");
            verifyIntermediateChain(level, cpu, core);
            verifyCancelExactInventory(level, cpu, core);
            verifyReturnPerformance(level, cpu);
            if (net.neoforged.fml.ModList.get().isLoaded("useless_mod")) {
                verifyDirectReturn(level, cpu, core);
                verifyNativeScaledBridge(level);
                verifyOmniversal(level, cpu, core, ownersField);
            }
            MolecularManipulator.LOGGER.info("EXACT_CPU_SMOKE_PASSED: transformed task commit, simulated/real output, crossing long, save/restore, exact completion");
        } catch (Throwable failure) {
            MolecularManipulator.LOGGER.error("EXACT_CPU_SMOKE_FAILED", failure);
        } finally { event.getServer().halt(false); }
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void verifyIntermediateChain(net.minecraft.world.level.Level level, CraftingCPUCluster cpu,
            OmniComputationCoreBlockEntity core) throws Exception {
        var source = AEItemKey.of(Items.COAL);
        var intermediate = AEItemKey.of(Items.IRON_INGOT);
        var output = AEItemKey.of(Items.DIAMOND);
        var producer = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(source, 1)), List.of(new GenericStack(intermediate, 1))), level);
        var consumer = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(intermediate, Long.MAX_VALUE)), List.of(new GenericStack(output, 1))), level);
        var total = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);
        var tasks = new LinkedHashMap<appeng.api.crafting.IPatternDetails, Long>();
        tasks.put(producer, Long.MAX_VALUE); tasks.put(consumer, 2L);
        var plan = AelisExactCraftingPlanApi.attachExecutionMetadata(new CraftingPlan(
                new GenericStack(output, 2), 1, false, false, new KeyCounter(), new KeyCounter(), new KeyCounter(), tasks),
                BigInteger.TWO, Map.of(producer, total, consumer, BigInteger.TWO), Map.of(source, total));
        var data = new CompoundTag(); data.putUUID("craftId", UUID.randomUUID());
        data.putBoolean("req", false); data.putBoolean("standalone", true);
        var link = new CraftingLink(data, cpu);
        var ctor = Arrays.stream(ExecutingCraftingJob.class.getDeclaredConstructors())
                .filter(c -> c.getParameterTypes()[0] == ICraftingPlan.class).findFirst().orElseThrow();
        ctor.setAccessible(true);
        var listener = Proxy.newProxyInstance(ctor.getParameterTypes()[1].getClassLoader(),
                new Class[]{ctor.getParameterTypes()[1]}, (p,m,a) -> null);
        var logic = cpu.craftingLogic;
        set(logic, "job", ctor.newInstance(plan, listener, link, null));
        var attached = new CallbackInfoReturnable<ICraftingSubmitResult>("chain", true, CraftingSubmitResult.successful(link));
        method(logic, "attachExactCraftingState").invoke(logic, null, plan,
                appeng.api.networking.security.IActionSource.empty(), null, attached);
        check(attached.getReturnValue().successful(), "intermediate chain attached");
        var pending = new ArrayList<com.atir.molecularmanipulator.api.crafting.OmniBigIntegerOutput>();
        var admitted = new ArrayList<BigInteger>();
        var rejected = new java.util.concurrent.atomic.AtomicBoolean();
        var provider = new com.atir.molecularmanipulator.api.crafting.OmniBigIntegerCraftingProvider() {
            public List<appeng.api.crafting.IPatternDetails> getAvailablePatterns() { return List.of(producer, consumer); }
            public boolean isBusy() { return false; }
            public boolean usesNativeBigIntegerBatch() { return true; }
            public BigInteger getMaximumBigIntegerCrafts(appeng.api.crafting.IPatternDetails p, KeyCounter[] in, BigInteger n) { return n; }
            public boolean pushBigIntegerCraftingPattern(appeng.api.crafting.IPatternDetails p, BigInteger n, KeyCounter[] in) {
                if (p.equals(consumer) && !rejected.getAndSet(true)) return false;
                admitted.add(n);
                for (var stack : p.getOutputs()) pending.add(new com.atir.molecularmanipulator.api.crafting.OmniBigIntegerOutput(
                        stack.what(), BigInteger.valueOf(stack.amount()).multiply(n)));
                for (var counter : in) counter.clear();
                return true;
            }
            public boolean pushPattern(appeng.api.crafting.IPatternDetails p, KeyCounter[] in) {
                throw new AssertionError("Native rejection must not fall back to ordinary pushPattern");
            }
        };
        var energy = (appeng.api.networking.energy.IEnergyService) Proxy.newProxyInstance(
                getClassLoader(), new Class[]{appeng.api.networking.energy.IEnergyService.class}, (p,m,a) ->
                        m.getName().equals("extractAEPower") ? a[0] : null);
        var storage = (appeng.api.networking.storage.IStorageService) Proxy.newProxyInstance(
                getClassLoader(), new Class[]{appeng.api.networking.storage.IStorageService.class}, (p,m,a) -> null);
        var blockedProbes = new java.util.concurrent.atomic.AtomicInteger();
        var blockedProvider = (ICraftingProvider) Proxy.newProxyInstance(getClassLoader(),
                new Class[]{com.atir.molecularmanipulator.api.crafting.OmniBigIntegerCraftingProvider.class}, (p,m,a) -> switch (m.getName()) {
                    case "getAvailablePatterns" -> List.of(producer, consumer);
                    case "usesNativeBigIntegerBatch", "isBusy" -> true;
                    case "getMaximumBigIntegerCrafts" -> { blockedProbes.incrementAndGet(); yield BigInteger.ZERO; }
                    case "pushPattern", "pushBigIntegerCraftingPattern" -> throw new AssertionError("No-capacity provider must not receive input");
                    default -> null;
                });
        var service = new appeng.me.service.CraftingService(null, storage, energy) {
            @Override public Iterable<ICraftingProvider> getProviders(appeng.api.crafting.IPatternDetails p) {
                return List.of(blockedProvider, provider);
            }
        };
        var ticker = appeng.hooks.ticking.TickHandler.instance();
        long savedTick = ticker.getCurrentTick();
        try {
        for (int tick = 0; tick < 8 && get(logic, "job") != null; tick++) {
            set(ticker, "tickCounter", savedTick + tick);
            method(logic, "clearDispatch").invoke(logic);
            set(logic, "molecularmanipulator$dispatchOwner", core);
            set(logic, "molecularmanipulator$dispatchAllowance", 1000L);
            set(logic, "molecularmanipulator$dispatchUsed", 0L);
            set(logic, "molecularmanipulator$dispatchStopped", false);
            set(logic, "molecularmanipulator$unscaledDispatchAllowance", 100);
            set(logic, "molecularmanipulator$unscaledDispatchUsed", 0);
            set(logic, "molecularmanipulator$compatDispatchDeadlineNanos", Long.MAX_VALUE);
            set(logic, "molecularmanipulator$taskRotation", 0);
            // Keep the producer first: a full upstream buffer must not prevent consumption.
            var jobTasks = (Map) get(get(logic, "job"), "tasks");
            var ordered = new LinkedHashMap();
            if (jobTasks.containsKey(producer)) ordered.put(producer, jobTasks.get(producer));
            if (jobTasks.containsKey(consumer)) ordered.put(consumer, jobTasks.get(consumer));
            set(get(logic, "job"), "tasks", ordered);
            logic.executeCrafting(16, service, energy, level);
            if (tick == 2) {
                verifyCompletedStatus(logic, intermediate, total, level.registryAccess());
                check(logic.getStored(intermediate) == 0, "completed total persists after downstream consumption");
                var downstreamBatch = ((AelisExactCraftingCpu) logic).aelis$getLastBatches().get(output);
                check(downstreamBatch.outputAmount().equals(BigInteger.TWO), "last downstream push shows its own output count");
                check(downstreamBatch.inputs().get(intermediate).equals(total), "last downstream push preserves actual finite input total");
            }
            MolecularManipulator.LOGGER.info("CHAIN_TICK: tick={}, stored={}, waiting={}, pending={}, stopped={}, tasks={}",
                    tick, logic.getInventory().list.get(intermediate), logic.getWaitingFor(intermediate), pending,
                    get(logic, "molecularmanipulator$dispatchStopped"),
                    ((AelisExactCraftingCpu) logic).aelis$getPendingOutputs());
            if (tick == 0) {
                check(!admitted.isEmpty(), "native producer dispatched");
                check(admitted.getFirst().equals(total), "native intermediate batch exceeds long and uses provider capacity");
            }
            for (var produced : pending) {
                var left = produced.amount();
                while (left.signum() > 0) {
                    long chunk = left.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
                    long accepted = logic.insert(produced.key(), chunk, Actionable.MODULATE);
                    if (produced.key().equals(intermediate)) check(accepted == chunk, "intermediate output never escapes to network storage");
                    left = left.subtract(BigInteger.valueOf(chunk));
                }
            }
            pending.clear();
            if (tick == 0) {
                var stock = (com.atir.molecularmanipulator.crafting.OmniExactInventory) get(logic, "molecularmanipulator$exactInventory");
                check(total.equals(stock.amount(logic.getInventory(), intermediate)), "all over-long intermediate stock retained");
                var saved = new CompoundTag(); logic.writeToNBT(saved, level.registryAccess());
                logic.readFromNBT(saved, level.registryAccess());
                check(total.equals(stock.amount(logic.getInventory(), intermediate)), "over-long intermediate stock survives reload");
                verifyStoredStatus(logic, intermediate, total, level.registryAccess());
                verifyCompletedStatus(logic, intermediate, total, level.registryAccess());
            }
            if (tick == 1) {
                var stock = (com.atir.molecularmanipulator.crafting.OmniExactInventory) get(logic, "molecularmanipulator$exactInventory");
                check(total.equals(stock.amount(logic.getInventory(), intermediate)), "provider rejection rolls back full finite reservation and prototype");
                check(BigInteger.TWO.equals(((AelisExactCraftingCpu) logic).aelis$getPendingOutputs().get(output)), "rejection leaves task count unchanged");
                check(!((AelisExactCraftingCpu) logic).aelis$getLastBatches().containsKey(output), "rejected push does not appear as accepted batch");
            }
        }
        } finally { set(ticker, "tickCounter", savedTick); }
        check(get(logic, "job") == null, "two-stage over-long order finishes with upstream-first scheduling");
        check(logic.getInventory().list.get(intermediate) == 0, "all intermediate stock consumed exactly once");
        check(!((com.atir.molecularmanipulator.api.crafting.IOmniCraftingCpu) logic).hasExactStoredItems(), "no leftover exact intermediate stock");
        check(admitted.equals(List.of(total, BigInteger.TWO)), "finite downstream batch consumes two long windows in one native push");
        check(blockedProbes.get() > 0, "a full first machine does not starve another available native provider");
        set(logic, "molecularmanipulator$dispatchOwner", null);
        MolecularManipulator.LOGGER.info("INTERMEDIATE_CHAIN_SMOKE_PASSED: produced={}, finalOutput=2", total);
    }
    static void verifyCancelExactInventory(net.minecraft.world.level.Level level, CraftingCPUCluster cpu,
            OmniComputationCoreBlockEntity core) throws Exception {
        var key = AEItemKey.of(Items.IRON_INGOT);
        var source = AEItemKey.of(Items.COAL);
        var output = AEItemKey.of(Items.DIAMOND);
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(key, 1)), List.of(new GenericStack(output, 1))), level);
        var total = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(3)).add(BigInteger.valueOf(7));
        var plan = AelisExactCraftingPlanApi.attachExecutionMetadata(new CraftingPlan(
                new GenericStack(output, Long.MAX_VALUE), 1, false, false, new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, Long.MAX_VALUE)),
                total, Map.of(pattern, total), Map.of(source, total));
        var tag = new CompoundTag(); tag.putUUID("craftId", UUID.randomUUID()); tag.putBoolean("standalone", true); tag.putBoolean("req", false);
        var link = new CraftingLink(tag, cpu);
        var ctor = Arrays.stream(ExecutingCraftingJob.class.getDeclaredConstructors())
                .filter(c -> c.getParameterTypes()[0] == ICraftingPlan.class).findFirst().orElseThrow();
        ctor.setAccessible(true);
        var listener = Proxy.newProxyInstance(ctor.getParameterTypes()[1].getClassLoader(), new Class[]{ctor.getParameterTypes()[1]}, (p,m,a) -> null);
        var logic = cpu.craftingLogic;
        set(logic, "job", ctor.newInstance(plan, listener, link, null));
        var attached = new CallbackInfoReturnable<ICraftingSubmitResult>("cancel", true, CraftingSubmitResult.successful(link));
        method(logic, "attachExactCraftingState").invoke(logic, null, plan, appeng.api.networking.security.IActionSource.empty(), null, attached);
        var stock = (com.atir.molecularmanipulator.crafting.OmniExactInventory) get(logic, "molecularmanipulator$exactInventory");
        stock.insert(logic.getInventory(), key, total);
        logic.cancel();
        check(get(logic, "job") == null, "cancel clears job");
        check(total.equals(stock.amount(logic.getInventory(), key)), "offline cancellation preserves exact physical stock");
        check(stock.amount(logic.getInventory(), source).signum() == 0, "cancel does not materialize infinite source");
        var saved = new CompoundTag(); logic.writeToNBT(saved, level.registryAccess());
        logic.readFromNBT(saved, level.registryAccess());
        check(total.equals(stock.amount(logic.getInventory(), key)), "cancelled exact refund survives reload without a job");
        check(logic.trySubmitJob(null, plan, appeng.api.networking.security.IActionSource.empty(), null) == CraftingSubmitResult.CPU_BUSY,
                "pending overflow refunds block replacement jobs");
        BigInteger refunded = BigInteger.ZERO;
        while (stock.amount(logic.getInventory(), key).signum() > 0) {
            long amount = logic.getInventory().extract(key, Long.MAX_VALUE, Actionable.MODULATE);
            refunded = refunded.add(BigInteger.valueOf(amount));
            logic.storeItems(); // offline: exposes next refund window without losing the remainder
        }
        check(total.equals(refunded), "cancelled stock drains exactly once over multiple windows");
        check(!((com.atir.molecularmanipulator.api.crafting.IOmniCraftingCpu) logic).hasExactStoredItems(), "refund ledger eventually empty");
        MolecularManipulator.LOGGER.info("EXACT_CANCEL_SMOKE_PASSED: refunded={}", refunded);
    }
    static ClassLoader getClassLoader() { return ExactCpuSmoke.class.getClassLoader(); }
    public interface TestControllerProvider { Object getController(); }
    public static final class TestControllerHolder {
        private final Object aeManager;
        TestControllerHolder(Object manager) { aeManager = manager; }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void verifyDirectReturn(net.minecraft.world.level.Level level, CraftingCPUCluster cpu,
            OmniComputationCoreBlockEntity core) throws Exception {
        var gridType = appeng.api.networking.IGrid.class;
        var grid = (appeng.api.networking.IGrid) Proxy.newProxyInstance(getClassLoader(), new Class[]{gridType}, (p,m,a) -> null);
        var foreign = (appeng.api.networking.IGrid) Proxy.newProxyInstance(getClassLoader(), new Class[]{gridType}, (p,m,a) -> null);
        var node = (appeng.api.networking.IGridNode) Proxy.newProxyInstance(getClassLoader(), new Class[]{appeng.api.networking.IGridNode.class},
                (p,m,a) -> switch(m.getName()) { case "getGrid" -> grid; case "isActive" -> true; default -> null; });
        var managed = Proxy.newProxyInstance(getClassLoader(), new Class[]{appeng.api.networking.IManagedGridNode.class},
                (p,m,a) -> switch(m.getName()) { case "getNode" -> node; case "getGrid" -> grid; default -> null; });
        var nodeField = appeng.blockentity.grid.AENetworkedBlockEntity.class.getDeclaredField("mainNode"); nodeField.setAccessible(true);
        var oldNode = nodeField.get(core); var oldFormed = get(core, "structureFormed");
        var ticker = appeng.hooks.ticking.TickHandler.instance(); long oldTick = ticker.getCurrentTick();
        var logic = cpu.craftingLogic;
        var stock = (com.atir.molecularmanipulator.crafting.OmniExactInventory) get(logic, "molecularmanipulator$exactInventory");
        try {
            nodeField.set(core, managed); set(core, "structureFormed", true);
            check(cpu.getGrid() == grid && cpu.isActive(), "direct return fixture has an active bound grid");
            for (boolean partial : List.of(false, true)) {
                var input = AEItemKey.of(Items.COAL); var output = AEItemKey.of(Items.IRON_INGOT); var finalKey = AEItemKey.of(Items.DIAMOND);
                var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                        List.of(new GenericStack(input, 1)), List.of(new GenericStack(output, 1))), level);
                var total = BigInteger.TEN.pow(24);
                var owed = partial ? total.divide(BigInteger.TWO) : total;
                var plan = AelisExactCraftingPlanApi.attachExecutionMetadata(new CraftingPlan(new GenericStack(finalKey, 1), 1,
                        false, false, new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, Long.MAX_VALUE)),
                        BigInteger.ONE, Map.of(pattern, total), Map.of(input, total));
                var tag = new CompoundTag(); tag.putUUID("craftId", UUID.randomUUID()); tag.putBoolean("standalone", true); tag.putBoolean("req", false);
                var link = new CraftingLink(tag, cpu);
                var ctor = Arrays.stream(ExecutingCraftingJob.class.getDeclaredConstructors()).filter(c -> c.getParameterTypes()[0] == ICraftingPlan.class).findFirst().orElseThrow(); ctor.setAccessible(true);
                var listener = Proxy.newProxyInstance(ctor.getParameterTypes()[1].getClassLoader(), new Class[]{ctor.getParameterTypes()[1]}, (p,m,a) -> null);
                set(logic, "job", ctor.newInstance(plan, listener, link, null));
                var attached = new CallbackInfoReturnable<ICraftingSubmitResult>("direct", true, CraftingSubmitResult.successful(link));
                method(logic, "attachExactCraftingState").invoke(logic, null, plan, appeng.api.networking.security.IActionSource.empty(), null, attached);
                var token = new java.util.concurrent.atomic.AtomicReference<Object>();
                var provider = (com.atir.molecularmanipulator.api.crafting.OmniBigIntegerCraftingProvider) Proxy.newProxyInstance(getClassLoader(),
                        new Class[]{com.atir.molecularmanipulator.api.crafting.OmniBigIntegerCraftingProvider.class}, (p,m,a) -> switch(m.getName()) {
                            case "usesNativeBigIntegerBatch" -> true;
                            case "getMaximumBigIntegerCrafts" -> a[2];
                            case "pushBigIntegerCraftingPattern" -> { token.set(a[3]); yield true; }
                            default -> null;
                        });
                var prototype = new KeyCounter[]{new KeyCounter()}; prototype[0].add(input, 1);
                method(logic, "pushExactBigIntegerBatch").invoke(logic, provider, pattern, prototype, 1L);
                var state = (com.atir.molecularmanipulator.crafting.OmniExactCraftingState) get(logic, "molecularmanipulator$exactState");
                var waiting = (appeng.crafting.inv.ListCraftingInventory) get(get(logic, "job"), "waitingFor");
                state.queueOutput(output, owed); waiting.insert(output, state.claimOutputWindow(output, 0), Actionable.MODULATE);
                var receiver = (com.atir.molecularmanipulator.api.crafting.OmniBigIntegerOutputReceiver) token.get();
                java.util.function.Consumer<BigInteger> forbidden = n -> { throw new AssertionError("Rejected direct return debited the source"); };
                check(receiver.transferOutput(foreign, output, total, forbidden).signum() == 0, "different grid rejected");
                check(receiver.transferOutput(grid, finalKey, total, forbidden).signum() == 0, "final products retain network/requester route");
                set(core, "structureFormed", false);
                check(receiver.transferOutput(grid, output, total, forbidden).signum() == 0, "inactive CPU rejected");
                set(core, "structureFormed", true);
                String pkg = "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.";
                var hostType = Class.forName(pkg + "AlloyFurnaceAeHost");
                var dirtied = new java.util.concurrent.atomic.AtomicInteger();
                var fallbackCalls = new java.util.concurrent.atomic.AtomicInteger();
                var returnTargetType = Class.forName(pkg + "CraftingAeOutputTarget");
                var returnTarget = Proxy.newProxyInstance(getClassLoader(), new Class[]{returnTargetType}, (p,m,a) -> {
                    fallbackCalls.incrementAndGet();
                    if (m.getReturnType() == boolean.class) return false;
                    if (m.getReturnType() == long.class) return 0L;
                    return null;
                });
                var sourceOnline = new java.util.concurrent.atomic.AtomicBoolean(true);
                var host = Proxy.newProxyInstance(getClassLoader(), new Class[]{hostType}, (p,m,a) -> switch(m.getName()) {
                    case "getLevel" -> level;
                    case "getAeGrid" -> grid;
                    case "supportsBigIntegerRecipeBatches", "isTaskExecutionEnabled" -> true;
                    case "getMaxAETaskCount" -> 2_000_000_000;
                    case "resolveAeOutputTarget" -> sourceOnline.get() ? returnTarget : null;
                    case "markChanged" -> { dirtied.incrementAndGet(); yield null; }
                    default -> null;
                });
                var managerType = Class.forName(pkg + "AdvancedAlloyFurnaceAeManager");
                var manager = managerType.getConstructor(hostType).newInstance(host);
                var ledgerType = Class.forName(pkg + "CraftingAeAmountAccumulator");
                var ledgerCtor = ledgerType.getDeclaredConstructor(); ledgerCtor.setAccessible(true); var ledger = ledgerCtor.newInstance();
                var add = ledgerType.getDeclaredMethod("add", AEKey.class, BigInteger.class); add.setAccessible(true); add.invoke(ledger, output, total);
                var contextType = Class.forName("com.sorrowmist.useless.api.crafting.bigint.cpu.AlloyFurnaceBigIntegerBatchContext");
                var outputType = Class.forName("com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerOutput");
                var exactOutputs = List.of(outputType.getConstructor(AEKey.class, BigInteger.class).newInstance(output, total));
                var context = contextType.getConstructors()[0].newInstance(UUID.randomUUID(), token.get(), "test", pattern, total, exactOutputs, 0L);
                var pendingType = Class.forName(managerType.getName() + "$PendingCraftingOutput");
                var pendingCtor = Arrays.stream(pendingType.getDeclaredConstructors()).filter(c -> c.getParameterCount() == 5).findFirst().orElseThrow(); pendingCtor.setAccessible(true);
                var pending = pendingCtor.newInstance(partial ? level.getGameTime() - 1 : level.getGameTime(), ledger, exactOutputs, context,
                        net.minecraft.resources.ResourceLocation.parse("omnisequence:useless_bigint_cpu"));
                ((List) get(manager, "queuedCraftingOutputs")).add(pending); set(manager, "pendingOutputAmount", total);
                var flush = managerType.getDeclaredMethod("flushQueuedCraftingOutputs", boolean.class); flush.setAccessible(true);
                sourceOnline.set(false); flush.invoke(manager, false); sourceOnline.set(true);
                check(total.equals(get(manager, "pendingOutputAmount")), "unavailable source return route blocks direct transfer");
                var directConfig = com.atir.molecularmanipulator.config.ModConfig.OMNI_DIRECT_NATIVE_OUTPUT_RETURN;
                directConfig.set(false);
                try { flush.invoke(manager, false); } finally { directConfig.set(true); }
                check(total.equals(get(manager, "pendingOutputAmount")), "disabled bridge leaves original queue untouched");
                var contextField = pendingType.getDeclaredField("cpuContext"); contextField.setAccessible(true);
                contextField.set(pending, null);
                flush.invoke(manager, false);
                check(total.equals(get(manager, "pendingOutputAmount")), "unbound/restored queue keeps original return route");
                contextField.set(pending, context);
                fallbackCalls.set(0);
                var nativeProviderType = Class.forName("com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerProvider");
                var nativeTargetType = Class.forName("com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerTarget");
                var nativeBatchType = Class.forName("com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerBatch");
                var capacityType = Class.forName("com.sorrowmist.useless.api.crafting.bigint.AlloyFurnaceBigIntegerCapacity");
                var segmentGetter = managerType.getMethod("outputSegmentBudget");
                var nativeMath = Class.forName(pkg + "AlloyFurnaceBigIntegerCrafting").getMethod("maximumSegmentedCount", List.class, long.class);
                Object target = Proxy.newProxyInstance(getClassLoader(), new Class[]{nativeTargetType}, (p,m,a) -> {
                    if (m.getName().equals("capacity") || m.getName().equals("admit")) {
                        var cap = (BigInteger) nativeMath.invoke(null, ((appeng.api.crafting.IPatternDetails) a[0]).getOutputs(), segmentGetter.invoke(manager));
                        var count = cap.min((BigInteger) a[2]);
                        if (m.getName().equals("capacity")) return capacityType.getMethod("of", BigInteger.class).invoke(null, count);
                        var prototypeIdentity = a[1];
                        return Proxy.newProxyInstance(getClassLoader(), new Class[]{nativeBatchType}, (b,bm,ba) -> switch(bm.getName()) {
                            case "count" -> count;
                            case "commit" -> { check(ba[0] == prototypeIdentity, "Y admission uses same prototype array"); yield true; }
                            default -> false;
                        });
                    }
                    if (m.getName().equals("grid")) return grid;
                    return null;
                });
                var holder = new TestControllerHolder(manager);
                var flushProvider = (ICraftingProvider) Proxy.newProxyInstance(getClassLoader(),
                        new Class[]{ICraftingProvider.class, nativeProviderType, TestControllerProvider.class}, (p,m,a) -> switch(m.getName()) {
                            case "getController" -> holder;
                            case "bigIntegerTarget" -> target;
                            case "getAvailablePatterns" -> List.of(pattern);
                            case "isBusy" -> false;
                            default -> null;
                        });
                var adapter = com.atir.molecularmanipulator.api.crafting.OmniBigIntegerProviderAdapterRegistry.resolve(flushProvider, pattern);
                var oneY = BigInteger.TEN.pow(24);
                try (var scope = com.atir.molecularmanipulator.integration.useless.OmniDirectAdmission.open(grid, oneY)) {
                    check(adapter.getMaximumBigIntegerCrafts(pattern, prototype, oneY).compareTo(oneY) < 0, "unverified sender retains native segment limit");
                }
                var oldForcedFlushes = new java.util.concurrent.atomic.AtomicInteger();
                com.atir.molecularmanipulator.api.crafting.OmniPostAccountingOutputAdapterRegistry.register(
                        "test:old_force_flush", 100, candidate -> candidate == flushProvider, candidate -> oldForcedFlushes.incrementAndGet());
                // Include the real optional mixin and the real Useless source-ledger cleanup.
                try { for (int attempt = 0; attempt < 8 && stock.amount(logic.getInventory(), output).signum() == 0; attempt++) {
                    set(ticker, "tickCounter", oldTick + 100 + attempt + (partial ? 20 : 0)); flush.invoke(manager, false);
                    if (!partial) com.atir.molecularmanipulator.api.crafting.OmniPostAccountingOutputAdapterRegistry.flushAfterCpuAccounting(flushProvider);
                } } finally { com.atir.molecularmanipulator.api.crafting.OmniPostAccountingOutputAdapterRegistry.unregister("test:old_force_flush"); }
                check(oldForcedFlushes.get() == 0, "same-tick direct adapter overrides legacy forced long drain");
                check(owed.equals(stock.amount(logic.getInventory(), output)), "exact direct stock delivered without long segmentation");
                check(total.subtract(owed).equals(get(manager, "pendingOutputAmount")), "source backlog debited exactly once");
                check(logic.getWaitingFor(output) == 0 && state.uncreditedOutput(output).signum() == 0, "direct delivery settles all exact debt");
                check(dirtied.get() > 0, "source marked dirty after ownership transfer");
                if (!partial) check(((List) get(manager, "queuedCraftingOutputs")).isEmpty(), "empty original queue entry completed and removed");
                if (!partial) check((Boolean) get(pending, "cpuNotified"), "original native completion callback runs after full transfer");
                if (!partial) check(fallbackCalls.get() == 0, "whole direct transfer uses zero long storage inserts");
                if (partial) check(fallbackCalls.get() > 0, "unclaimed remainder still attempts ordinary network return");
                if (!partial) {
                    long ordinaryBudget = (Long) segmentGetter.invoke(manager);
                    set(ticker, "tickCounter", ticker.getCurrentTick() + 1);
                    BigInteger learned = com.atir.molecularmanipulator.integration.useless.UselessExactOutputReturn.adaptiveWindow(manager);
                    try (var scope = com.atir.molecularmanipulator.integration.useless.OmniDirectAdmission.open(grid)) {
                        var offered = adapter.getMaximumBigIntegerCrafts(pattern, prototype, learned.multiply(BigInteger.TEN));
                        check(offered.equals(learned), "verified route uses learned window rather than fixed Y limit");
                        check(adapter.pushBigIntegerCraftingPattern(pattern, offered, prototype), "learned budget persists through admit and commit");
                    }
                    check((Long) segmentGetter.invoke(manager) == ordinaryBudget, "direct budget never changes ordinary queue budget");
                    prototype[0].add(input, 1);
                    try (var scope = com.atir.molecularmanipulator.integration.useless.OmniDirectAdmission.open(foreign, oneY)) {
                        check(adapter.getMaximumBigIntegerCrafts(pattern, prototype, oneY).compareTo(oneY) < 0, "foreign CPU scope cannot raise source capacity");
                    }
                    MolecularManipulator.LOGGER.info("ADAPTIVE_NATIVE_ADMISSION_SMOKE_PASSED: sameTick=true, learnedWindow={}", learned);
                }
                flush.invoke(manager, false);
                check(owed.equals(stock.amount(logic.getInventory(), output)), "repeat flush cannot duplicate transferred output");
                nodeField.set(core, oldNode); set(core, "structureFormed", oldFormed);
                stock.clear(); logic.getInventory().clear(); logic.cancel();
                check(receiver.transferOutput(grid, output, total, forbidden).signum() == 0, "cancelled binding does not accept late output");
                MolecularManipulator.LOGGER.info("DIRECT_RETURN_SMOKE_PASSED: partial={}, amount={}, equivalentLongSegments={}", partial, owed,
                        owed.add(BigInteger.valueOf(Long.MAX_VALUE - 1)).divide(BigInteger.valueOf(Long.MAX_VALUE)));
                nodeField.set(core, managed); set(core, "structureFormed", true);
            }
        } finally {
            nodeField.set(core, oldNode); set(core, "structureFormed", oldFormed); set(ticker, "tickCounter", oldTick);
            stock.clear(); logic.getInventory().clear(); if (get(logic, "job") != null) logic.cancel();
        }
    }
    static void verifyReturnPerformance(net.minecraft.world.level.Level level, CraftingCPUCluster cpu) throws Exception {
        var config = com.atir.molecularmanipulator.config.ModConfig.OMNI_COALESCE_RETURN_NOTIFICATIONS;
        var profiling = com.atir.molecularmanipulator.config.ModConfig.OMNI_PROFILE_EXACT_RETURNS;
        var sampling = com.atir.molecularmanipulator.config.ModConfig.OMNI_RETURN_PROFILE_SAMPLE_INTERVAL;
        int oldSampling = sampling.get();
        boolean oldCoalescing = config.get(), oldProfiling = profiling.get();
        var baseline = new ArrayList<Long>(); var optimized = new ArrayList<Long>();
        try {
            profiling.set(true);
            sampling.set(1);
            // Alternate order and exclude warmup; this is a synthetic CPU-path comparison, not pack TPS.
            for (int round = 0; round < 8; round++) {
                for (int position = 0; position < 2; position++) {
                    boolean coalesce = (round + position) % 2 == 0;
                    config.set(coalesce);
                    var sample = benchmarkReturnPass(level, cpu, 4096);
                    check(sample.calls() == 4096, "profiler counts actual insert calls");
                    if (coalesce) check(sample.notificationsSent() == 4096, "one changed-key notification per output insertion");
                    else check(sample.notificationsSent() > 4096, "baseline exposes repeated notifications");
                    if (round >= 3) (coalesce ? optimized : baseline).add(sample.totalNanos());
                    if (round == 7) MolecularManipulator.LOGGER.info("RETURN_PROFILE_SAMPLE: coalescing={}, sample={}", coalesce, sample);
                }
            }
            baseline.sort(Long::compare); optimized.sort(Long::compare);
            MolecularManipulator.LOGGER.info("RETURN_PERFORMANCE_SMOKE_PASSED: callsPerPass=4096, baselineMedianMs={}, optimizedMedianMs={}",
                    baseline.get(baseline.size()/2) / 1_000_000.0, optimized.get(optimized.size()/2) / 1_000_000.0);
            sampling.set(64);
            config.set(true);
            var sampled = benchmarkReturnPass(level, cpu, 4096);
            check(sampled.calls() == 64 && sampled.observedCalls() == 4096, "sampled profiling retains exact total call count");
        } finally { config.set(oldCoalescing); profiling.set(oldProfiling); sampling.set(oldSampling); }
    }
    static com.atir.molecularmanipulator.crafting.OmniExactReturnProfiler.Snapshot benchmarkReturnPass(
            net.minecraft.world.level.Level level, CraftingCPUCluster cpu, int calls) throws Exception {
        var key = AEItemKey.of(Items.IRON_INGOT); var output = AEItemKey.of(Items.DIAMOND); var source = AEItemKey.of(Items.COAL);
        var total = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(calls));
        var pattern = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(source, 1)), List.of(new GenericStack(key, 1))), level);
        var plan = AelisExactCraftingPlanApi.attachExecutionMetadata(new CraftingPlan(
                new GenericStack(output, 1), 1, false, false, new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, Long.MAX_VALUE)),
                BigInteger.ONE, Map.of(pattern, total), Map.of(source, total));
        var tag = new CompoundTag(); tag.putUUID("craftId", UUID.randomUUID()); tag.putBoolean("standalone", true); tag.putBoolean("req", false);
        var link = new CraftingLink(tag, cpu);
        var ctor = Arrays.stream(ExecutingCraftingJob.class.getDeclaredConstructors()).filter(c -> c.getParameterTypes()[0] == ICraftingPlan.class).findFirst().orElseThrow();
        ctor.setAccessible(true);
        var listener = Proxy.newProxyInstance(ctor.getParameterTypes()[1].getClassLoader(), new Class[]{ctor.getParameterTypes()[1]}, (p,m,a) -> null);
        var logic = cpu.craftingLogic;
        set(logic, "job", ctor.newInstance(plan, listener, link, null));
        var attached = new CallbackInfoReturnable<ICraftingSubmitResult>("profile", true, CraftingSubmitResult.successful(link));
        method(logic, "attachExactCraftingState").invoke(logic, null, plan, appeng.api.networking.security.IActionSource.empty(), null, attached);
        var state = (com.atir.molecularmanipulator.crafting.OmniExactCraftingState) get(logic, "molecularmanipulator$exactState");
        var waiting = (appeng.crafting.inv.ListCraftingInventory) get(get(logic, "job"), "waitingFor");
        state.queueOutput(key, total); waiting.insert(key, state.claimOutputWindow(key, 0), Actionable.MODULATE);
        var notices = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.Consumer<AEKey> observer = changed -> notices.incrementAndGet();
        logic.addListener(observer);
        com.atir.molecularmanipulator.crafting.OmniExactReturnProfiler.snapshotAndReset();
        try {
            for (int i = 0; i < calls; i++) check(logic.insert(key, Long.MAX_VALUE, Actionable.MODULATE) == Long.MAX_VALUE, "profile run preserves output acceptance");
            var result = com.atir.molecularmanipulator.crafting.OmniExactReturnProfiler.snapshotAndReset();
            if (result.calls() == calls) check(result.notificationsSent() == notices.get(), "profiler notification counts match real observers");
            check(total.equals(((AelisExactCraftingCpu) logic).aelis$getStoredOutputs().get(key)), "baseline and optimized retain identical exact stock");
            check(logic.getWaitingFor(key) == 0 && state.uncreditedOutput(key).signum() == 0, "all profile output debt settled");
            return result;
        } finally {
            logic.removeListener(observer);
            ((com.atir.molecularmanipulator.api.crafting.IOmniCraftingCpu) logic).clearExactStoredItems();
            logic.getInventory().clear(); logic.cancel();
        }
    }
    static void verifyCompletedStatus(CraftingCpuLogic logic, AEKey key, BigInteger expected, net.minecraft.core.RegistryAccess registries) {
        check(expected.equals(((AelisExactCraftingCpu) logic).aelis$getLastBatches().get(key).outputAmount()), "last accepted batch survives reload and downstream consumption");
        var all = new KeyCounter(); logic.getAllItems(all);
        check(all.get(key) > 0, "fully consumed completed key remains discoverable for full status");
        var changes = new appeng.menu.me.common.IncrementalUpdateHelper(); changes.addChange(key);
        for (int update = 0; update < 2; update++) {
            var status = appeng.menu.me.crafting.CraftingStatus.create(changes, logic);
            check(changes.getSerial(key) != null, "completed-only row retains incremental serial");
            var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), registries);
            try {
                status.write(buffer);
                var decoded = appeng.menu.me.crafting.CraftingStatus.read(buffer);
                check(buffer.readableBytes() == 0, "completed status packet fully read");
                var incoming = decoded.getEntries().getFirst();
                var entry = com.appliedenhancements.runtime.ExactCraftingStatus.copy(incoming,
                        new appeng.menu.me.crafting.CraftingStatusEntry(incoming.getSerial(), key,
                                incoming.getStoredAmount(), incoming.getActiveAmount(), incoming.getPendingAmount()));
                var batch = ((com.appliedenhancements.ae2.ExactCraftingStatusEntry) entry).appliedenhancements$getLastBatch();
                check(expected.equals(batch.outputAmount()), "last batch survives full/incremental packet and copy");
                check(expected.equals(batch.inputs().get(AEItemKey.of(Items.COAL))), "last batch packet includes actual scaled materials");
                check(!entry.isDeleted(), "completed-only row is not removed on client");
                var full = com.appliedenhancements.runtime.ExactCraftingStatusLabels.replace(entry,
                        List.of(appeng.core.localization.GuiText.FromStorage.text("old-window")), true);
                var storedLine = (net.minecraft.network.chat.contents.TranslatableContents) full.getFirst().getContents();
                check(storedLine.getArgs()[0].equals(com.appliedenhancements.util.AmountFormatter.formatFull(
                        com.appliedenhancements.runtime.ExactCraftingStatus.stored(entry), key.getAmountPerUnit())), "actual FromStorage line replaces long projection");
                var craftingKey = ((net.minecraft.network.chat.contents.TranslatableContents) appeng.core.localization.GuiText.Crafting.text("").getContents()).getKey();
                var activeLine = full.stream().map(line -> (net.minecraft.network.chat.contents.TranslatableContents) line.getContents())
                        .filter(line -> line.getKey().equals(craftingKey)).findFirst().orElseThrow();
                check(activeLine.getArgs()[0].equals("18,446,744,073,709,551,614"), "crafting tooltip retains full batch after return");
                var inputLine = (net.minecraft.network.chat.contents.TranslatableContents) full.getLast().getContents();
                check(inputLine.getKey().equals("gui.appliedenhancements.crafting.batch_input"), "batch input tooltip present");
                check(inputLine.getArgs()[1].equals("18,446,744,073,709,551,614"), "full input amount shown without long truncation");
                check(full.stream().noneMatch(line -> ((net.minecraft.network.chat.contents.TranslatableContents) line.getContents()).getKey()
                        .equals("gui.appliedenhancements.crafting.completed")), "completed line removed");
                var compact = com.appliedenhancements.runtime.ExactCraftingStatusLabels.replace(entry, List.of(), false);
                var compactLine = compact.stream().map(line -> (net.minecraft.network.chat.contents.TranslatableContents) line.getContents())
                        .filter(line -> line.getKey().equals(craftingKey)).findFirst().orElseThrow();
                check(compactLine.getArgs()[0].equals("18.4E"), "list shows last batch in Crafting row");
                for (var line : full) check(!((net.minecraft.network.chat.contents.TranslatableContents) line.getContents()).getKey()
                        .equals(((net.minecraft.network.chat.contents.TranslatableContents) appeng.core.localization.GuiText.Stored.text().getContents()).getKey()), "no spurious Stored heading");
            } finally { buffer.release(); }
            changes.commitChanges(); changes.addChange(key);
        }
        MolecularManipulator.LOGGER.info("LAST_BATCH_STATUS_SMOKE_PASSED: batch={}, stored={}", expected, logic.getStored(key));
    }
    static void verifyStoredStatus(CraftingCpuLogic logic, AEKey key, BigInteger expected, net.minecraft.core.RegistryAccess registries) {
        check(expected.equals(((AelisExactCraftingCpu) logic).aelis$getStoredOutputs().get(key)), "exact stored total includes overflow");
        var changes = new appeng.menu.me.common.IncrementalUpdateHelper(); changes.addChange(key);
        var status = appeng.menu.me.crafting.CraftingStatus.create(changes, logic);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), registries);
        try {
            status.write(buffer);
            var decoded = appeng.menu.me.crafting.CraftingStatus.read(buffer);
            check(buffer.readableBytes() == 0, "stored status packet completely decoded");
            var incoming = decoded.getEntries().getFirst();
            check(expected.equals(com.appliedenhancements.runtime.ExactCraftingStatus.stored(incoming)), "exact stored count survives status packet");
            var copied = com.appliedenhancements.runtime.ExactCraftingStatus.copy(incoming,
                    new appeng.menu.me.crafting.CraftingStatusEntry(incoming.getSerial(), key,
                            incoming.getStoredAmount(), incoming.getActiveAmount(), incoming.getPendingAmount()));
            check(expected.equals(com.appliedenhancements.runtime.ExactCraftingStatus.stored(copied)), "client entry copy retains exact stock");
            MolecularManipulator.LOGGER.info("EXACT_STORED_STATUS_SMOKE_PASSED: stored={}", expected);
        } finally { buffer.release(); }
    }
    static void verifyActiveStatus(CraftingCpuLogic logic, appeng.menu.me.common.IncrementalUpdateHelper changes,
            AEKey key, BigInteger expected, net.minecraft.core.RegistryAccess registries) {
        check(expected.equals(((AelisExactCraftingCpu) logic).aelis$getActiveOutputs().get(key)),
                "active count includes long window and unwindowed credits");
        var status = appeng.menu.me.crafting.CraftingStatus.create(changes, logic);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), registries);
        try {
            status.write(buffer);
            var decoded = appeng.menu.me.crafting.CraftingStatus.read(buffer);
            check(buffer.readableBytes() == 0, "active status packet completely decoded");
            var incoming = decoded.getEntries().getFirst();
            check(expected.equals(((com.appliedenhancements.ae2.ExactCraftingStatusEntry) incoming).appliedenhancements$getActive()),
                    "incremental packet transports exact active output");
            var copied = com.appliedenhancements.runtime.ExactCraftingStatus.copy(incoming,
                    new appeng.menu.me.crafting.CraftingStatusEntry(incoming.getSerial(), key,
                            incoming.getStoredAmount(), incoming.getActiveAmount(), incoming.getPendingAmount()));
            check(expected.equals(com.appliedenhancements.runtime.ExactCraftingStatus.active(copied)),
                    "client incremental entry replacement retains active amount");
            MolecularManipulator.LOGGER.info("EXACT_ACTIVE_STATUS_SMOKE_PASSED: active={}", expected);
        } finally { buffer.release(); }
    }
    static void verifyNativeScaledBridge(net.minecraft.world.level.Level level) throws Exception {
        var input = AEItemKey.of(Items.COAL);
        var output = AEItemKey.of(Items.DIAMOND);
        var original = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(input, 9)), List.of(new GenericStack(output, 1))), level);
        long factor = Long.MAX_VALUE / 10;
        var pattern = (appeng.api.crafting.IPatternDetails) Class.forName(
                "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPatterns")
                .getMethod("scale", appeng.api.crafting.IPatternDetails.class, long.class)
                .invoke(null, original, factor);
        String api = "com.sorrowmist.useless.api.crafting.bigint.";
        var providerType = Class.forName(api + "AlloyFurnaceBigIntegerProvider");
        var targetType = Class.forName(api + "AlloyFurnaceBigIntegerTarget");
        var batchType = Class.forName(api + "AlloyFurnaceBigIntegerBatch");
        var capacityType = Class.forName(api + "AlloyFurnaceBigIntegerCapacity");
        var produced = new java.util.concurrent.atomic.AtomicReference<>(BigInteger.ZERO);
        Object target = Proxy.newProxyInstance(targetType.getClassLoader(), new Class[]{targetType}, (p,m,a) -> {
            if (m.getName().equals("capacity") || m.getName().equals("admit")) {
                check(a[0] == original, "real smart-doubling wrapper unwrapped for public API");
                var prototype = (KeyCounter[]) a[1];
                check(prototype[0].get(input) == 9, "public API receives base recipe prototype");
                var count = (BigInteger) a[2];
                if (m.getName().equals("capacity")) return capacityType.getMethod("of", BigInteger.class).invoke(null, count);
                return Proxy.newProxyInstance(batchType.getClassLoader(), new Class[]{batchType}, (b,bm,ba) -> {
                    if (bm.getName().equals("count")) return count;
                    if (bm.getName().equals("commit")) {
                        check(ba[0] == prototype, "native admission preserves array identity");
                        produced.set(produced.get().add(count));
                        for (var counter : prototype) counter.clear();
                        return true;
                    }
                    return false;
                });
            }
            return null;
        });
        var provider = (ICraftingProvider) Proxy.newProxyInstance(providerType.getClassLoader(),
                new Class[]{providerType, ICraftingProvider.class}, (p,m,a) -> switch(m.getName()) {
                    case "bigIntegerTarget" -> target;
                    case "getAvailablePatterns" -> List.of(pattern);
                    case "isBusy" -> false;
                    default -> null;
                });
        var adapter = com.atir.molecularmanipulator.api.crafting.OmniBigIntegerProviderAdapterRegistry.resolve(provider, pattern);
        check(adapter != null && adapter.usesNativeBigIntegerBatch(), "actual native API bridge registered");
        var prototype = new KeyCounter[]{new KeyCounter()}; prototype[0].add(input, 9 * factor);
        var tasks = BigInteger.valueOf(2_222_222);
        check(tasks.equals(adapter.getMaximumBigIntegerCrafts(pattern, prototype, tasks)), "capacity remains in CPU task units");
        check(adapter.pushBigIntegerCraftingPattern(pattern, tasks, prototype), "scaled native commit accepted");
        check(produced.get().equals(tasks.multiply(BigInteger.valueOf(pattern.getOutputs().getFirst().amount()))),
                "native machine output equals scaled CPU expected output");
        MolecularManipulator.LOGGER.info("NATIVE_SCALED_BRIDGE_SMOKE_PASSED: tasks={}, baseOperations={}", tasks, produced.get());
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void verifyOmniversal(net.minecraft.world.level.Level level, CraftingCPUCluster cpu,
            OmniComputationCoreBlockEntity core, Field ownersField) throws Exception {
        String pkg = "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.";
        var input = AEItemKey.of(Items.COBBLESTONE);
        var output = AEItemKey.of(Items.STONE);
        var encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(input, 1)), List.of(new GenericStack(output, 1)));
        var plain = PatternDetailsHelper.decodePattern(encoded, level);
        var catalog = Class.forName("com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog");
        var entries = (List<?>) catalog.getMethod("findPatternCandidates", net.minecraft.world.level.Level.class,
                appeng.api.crafting.IPatternDetails.class).invoke(null, level, plain);
        check(!entries.isEmpty(), "stone furnace catalog entry exists");
        var entry = entries.getFirst();
        var stack = (net.minecraft.world.item.ItemStack) Class.forName(pkg + "OmniversalPatternEncoding")
                .getMethod("encode", net.minecraft.world.item.ItemStack.class, entry.getClass(), net.minecraft.world.level.Level.class)
                .invoke(null, encoded, entry, level);
        var original = PatternDetailsHelper.decodePattern(stack, level);
        check(original != null && original.getClass().getSimpleName().equals("OmniversalPatternDetails"), "real omniversal pattern");
        var marker = Class.forName("com.sorrowmist.useless.api.crafting.SmartDoublingCraftingProvider");
        var provider = (ICraftingProvider) Proxy.newProxyInstance(marker.getClassLoader(),
                new Class[]{marker, ICraftingProvider.class}, (p,m,a) -> switch(m.getName()) {
                    case "getAvailablePatterns" -> List.of(original);
                    case "isBusy" -> false;
                    case "pushPattern" -> true;
                    default -> throw new AssertionError(m.getName());
                });
        var rewrite = Class.forName(pkg + "SmartDoublingPlanner").getMethod("rewrite", Map.class, java.util.function.Function.class);
        for (var exact : List.of(new BigInteger("9999999999999999999"), new BigInteger("99999999999999999999"),
                new BigInteger("100000000000000000000000000000000000000000000000000"))) {
            var rewritten = (Map<appeng.api.crafting.IPatternDetails, Long>) rewrite.invoke(null,
                    Map.of(original, Long.MAX_VALUE), (java.util.function.Function<Object, Object>) p -> List.of(provider));
            check(!rewritten.containsKey(original), "real smart doubling replaces original task");
            var nativePlan = new CraftingPlan(new GenericStack(output, Long.MAX_VALUE), 1, false, false,
                    new KeyCounter(), new KeyCounter(), new KeyCounter(), rewritten);
            var plan = AelisExactCraftingPlanApi.attachExecutionMetadata(nativePlan, exact, Map.of(original, exact), Map.of(input, exact));
            // Exercise the actual second rewrite at submission, including our metadata-preserving Mixin.
            var submitRewrite = Class.forName(pkg + "SmartDoublingPlans").getMethod("rewriteForSubmission",
                    ICraftingPlan.class, java.util.function.Function.class);
            plan = (ICraftingPlan) submitRewrite.invoke(null, plan,
                    (java.util.function.Function<Object, Object>) p -> List.of(provider));
            check(exact.equals(AelisExactCraftingPlanApi.getFinalOutputAmount(plan)), "submission rewrite retains exact final output");
            var exactTasks = AelisExactCraftingPlanApi.getPatternTimes(plan);
            check(plan.patternTimes().keySet().equals(exactTasks.keySet()), "projection and exact task identities agree");
            check(exact.equals(com.appliedenhancements.runtime.ExactCraftingStatus.pending(exactTasks.keySet(), exactTasks::get).get(output)),
                    "scaled batch plus remainder conserves full output");
            var copied = AelisCycleExecutionApi.copyMetadata(plan, plan);
            check(AelisExactCraftingPlanApi.getPatternTimes(copied).equals(exactTasks), "plan copying is idempotent");
            var data = new CompoundTag();
            data.putUUID("craftId", UUID.randomUUID()); data.putBoolean("req", false); data.putBoolean("standalone", true);
            var link = new CraftingLink(data, cpu);
            var ctor = Arrays.stream(ExecutingCraftingJob.class.getDeclaredConstructors())
                    .filter(c -> c.getParameterTypes()[0] == ICraftingPlan.class).findFirst().orElseThrow();
            ctor.setAccessible(true);
            var listenerType = ctor.getParameterTypes()[1];
            var listener = Proxy.newProxyInstance(listenerType.getClassLoader(), new Class[]{listenerType}, (p,m,a) -> null);
            var logic = cpu.craftingLogic;
            set(logic, "job", ctor.newInstance(plan, listener, link, null));
            var callback = new CallbackInfoReturnable<ICraftingSubmitResult>("omniSmoke", true, CraftingSubmitResult.successful(link));
            method(logic, "attachExactCraftingState").invoke(logic, null, plan,
                    appeng.api.networking.security.IActionSource.empty(), null, callback);
            check(callback.getReturnValue().successful(), "omniversal CPU exact attachment accepted");
            check(exact.equals(((AelisExactCraftingCpu) logic).aelis$getPendingOutputs().get(output)), "omniversal initial pending exact");
            var scaled = exactTasks.keySet().stream().filter(p -> p != original)
                    .max(java.util.Comparator.comparingLong(p -> p.getOutputs().getFirst().amount())).orElseThrow();
            var inputs = new KeyCounter[]{new KeyCounter()};
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> rejected = arguments -> false;
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Boolean> accepted = arguments -> true;
            check(!(Boolean) method(logic, "pushBatch").invoke(logic, provider, scaled, inputs, rejected), "omniversal rejection propagated");
            check(exact.equals(((AelisExactCraftingCpu) logic).aelis$getPendingOutputs().get(output)), "rejection retains exact amount");
            check((Boolean) method(logic, "pushBatch").invoke(logic, provider, scaled, inputs, accepted), "omniversal acceptance propagated");
            var expected = exact.subtract(BigInteger.valueOf(scaled.getOutputs().getFirst().amount()));
            check(expected.equals(((AelisExactCraftingCpu) logic).aelis$getPendingOutputs().get(output)), "accepted scaled task deducted once");
            // Mimic AE2's subsequent task decrement before saving, including exhausted-task removal.
            var state = (com.atir.molecularmanipulator.crafting.OmniExactCraftingState) get(logic, "molecularmanipulator$exactState");
            var tasks = (Map) get(get(logic, "job"), "tasks");
            if (state.remaining(scaled).signum() == 0) tasks.remove(scaled);
            else set(tasks.get(scaled), "value", com.atir.molecularmanipulator.crafting.OmniExactCraftingState.window(state.remaining(scaled)));
            var saved = new CompoundTag();
            logic.writeToNBT(saved, level.registryAccess());
            ((Map) ownersField.get(null)).remove(cpu);
            logic.readFromNBT(saved, level.registryAccess());
            ((Map) ownersField.get(null)).put(cpu, core);
            check(expected.equals(((AelisExactCraftingCpu) logic).aelis$getPendingOutputs().get(output)), "real scaled pattern and remainder restored");
            check(exact.equals(((AelisExactCraftingCpu) logic).aelis$getRemainingOutput()), "full output survives restore");
            logic.cancel();
            check(logic.getInventory().list.get(input) == 0, "cancel does not refund virtual cobblestone");
            MolecularManipulator.LOGGER.info("OMNIVERSAL_EXACT_SMOKE_PASSED: amount={}, pendingAfterBatch={}", exact, expected);
        }
    }
    static Method method(Object object, String suffix) {
        var result = Arrays.stream(object.getClass().getDeclaredMethods()).filter(m -> m.getName().endsWith(suffix)).findFirst().orElseThrow();
        result.setAccessible(true); return result;
    }
    static void verifyStatus(CraftingCpuLogic logic, appeng.menu.me.common.IncrementalUpdateHelper changes,
            BigInteger expected, net.minecraft.core.RegistryAccess registries, boolean full) {
        var status = appeng.menu.me.crafting.CraftingStatus.create(changes, logic);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), registries);
        try {
            status.write(buffer);
            var decoded = appeng.menu.me.crafting.CraftingStatus.read(buffer);
            check(buffer.readableBytes() == 0, "status packet completely decoded");
            check(decoded.isFullStatus() == full, "full/incremental status");
            var entry = decoded.getEntries().getFirst();
            check(expected.equals(((com.appliedenhancements.ae2.ExactCraftingStatusEntry) entry).appliedenhancements$getPending()),
                    "status transports exact pending output, not execution window");
            check(full == (entry.getWhat() != null), "incremental status can omit the item key");
            MolecularManipulator.LOGGER.info("EXACT_STATUS_SMOKE_PASSED: full={}, pending={}", full, expected);
        } finally { buffer.release(); }
    }
    static Object get(Object object, String name) throws Exception { var f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
    static void set(Object object, String name, Object value) throws Exception { var f=object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object,value); }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
