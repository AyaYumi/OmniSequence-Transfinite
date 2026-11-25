package com.atir.molecularmanipulator.crafting.dispatch;

import static com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern.CONTAINER;
import static com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern.MATERIAL;
import static com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern.OUTPUT;
import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.api.crafting.OmniBatchAdmission;
import com.atir.molecularmanipulator.api.crafting.OmniBatchCraftingProvider;
import com.atir.molecularmanipulator.api.crafting.OmniBatchDelivery;
import com.atir.molecularmanipulator.api.crafting.OmniBatchProbe;
import com.atir.molecularmanipulator.api.crafting.OmniBigIntegerCraftingProvider;
import com.atir.molecularmanipulator.api.crafting.OmniPostAccountingOutputProvider;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.crafting.OmniExactCraftingState;
import com.atir.molecularmanipulator.crafting.OmniExactInventory;
import com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanCpuAccess;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OmniCapacityDispatcherTest {
    static { EcoMolecularTestPattern.bootstrap(); }

    private Field cachedConfig;
    private Object oldConfig;

    @BeforeEach void enableCapacity() throws Exception {
        cachedConfig = net.neoforged.neoforge.common.ModConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");
        cachedConfig.setAccessible(true);
        oldConfig = cachedConfig.get(ModConfig.OMNI_BATCH_DISPATCH_ENABLED);
        cachedConfig.set(ModConfig.OMNI_BATCH_DISPATCH_ENABLED, true);
    }

    @AfterEach void restoreCapacity() throws Exception {
        cachedConfig.set(ModConfig.OMNI_BATCH_DISPATCH_ENABLED, oldConfig);
    }

    @Test void stageCountsAndRefundsOnlyTheAcceptedPrefix() throws Exception {
        var host = new Host(10);
        host.own(CONTAINER, 8);
        host.own(MATERIAL, 5);
        var provider = new Target(request -> {
            assertEquals(host.id, request.jobId());
            assertEquals(n(4), request.maximumCrafts());
            assertEquals(1, request.unitInputs()[0].get(CONTAINER));
            assertEquals(n(8), host.ecoOmni$physicalAmount(CONTAINER));
            return admission(n(4), submitted -> {
                assertEquals(n(4), submitted);
                assertEquals(n(4), host.ledger.available(host.waiting, null, OUTPUT));
                assertEquals(BigInteger.ONE, host.ecoOmni$physicalAmount(MATERIAL));
                return BigInteger.TWO;
            });
        });
        var allowance = new Allowance(7, 32, 0);
        var network = new Energy(8);
        new OmniCapacityDispatcher().tick(host, allowance, service(provider), network.service, null);

        assertEquals(1, provider.preparations);
        assertEquals(0, provider.singles);
        assertEquals(BigInteger.TWO, allowance.accepted);
        assertEquals(n(8), host.omniDispatch$remaining(host.pattern));
        assertEquals(n(6), host.ecoOmni$physicalAmount(CONTAINER));
        assertEquals(n(3), host.ecoOmni$physicalAmount(MATERIAL));
        assertEquals(BigInteger.TWO, host.ledger.available(host.waiting, null, OUTPUT));
        assertEquals(8.0, network.paid);
        assertEquals(4.0, host.ledger.energy().extractNative(4.0, Actionable.SIMULATE, amount -> 0));
        assertEquals(1, provider.flushed);
    }

    @Test void providerCursorResumesAcrossTicksAndSkipsKnownSaturation() throws Exception {
        var host = new Host(10);
        host.own(CONTAINER, 10);
        host.own(MATERIAL, 10);
        var full = new Target(request -> OmniCapacityAdmission.UNAVAILABLE);
        var unsupported = new Provider();
        var service = service(full, unsupported, unsupported);
        var dispatcher = new OmniCapacityDispatcher();
        var network = new Energy(100);
        dispatcher.tick(host, new Allowance(10, 5, 1), service, network.service, null);
        assertEquals(1, full.preparations);
        assertEquals(0, full.singles);
        assertEquals(0, unsupported.singles);

        var allowance = new Allowance(10, 16, 1);
        dispatcher.tick(host, allowance, service, network.service, null);
        assertEquals(1, unsupported.singles);
        assertEquals(BigInteger.ONE, allowance.accepted);
        assertEquals(0, allowance.singles);
        assertEquals(1, full.preparations);
    }

    @Test void nativeSingleChecksProviderAndChargesBeforeEnteringCpu() {
        var pattern = new EcoMolecularTestPattern(CONTAINER, ignored -> null);
        var available = new Provider();
        var busy = new Provider() {
            @Override public boolean isBusy() { return true; }
        };
        var allowance = new Allowance(1, 1, 1);
        var entries = new AtomicInteger();
        var host = new OmniCpuDispatchHost() {
            @Override public int omniDispatch$executeNativeSingle(IPatternDetails selected, ICraftingProvider provider,
                    CraftingService crafting, IEnergyService energy, Level level) {
                assertSame(pattern, selected);
                assertSame(available, provider);
                assertEquals(0, allowance.singles);
                entries.incrementAndGet();
                return 1;
            }
        };

        assertFalse(OmniCapacityDispatcher.tryNativeSingle(host, allowance, pattern, busy, null, null, null));
        assertEquals(1, allowance.singles);
        assertFalse(OmniCapacityDispatcher.tryNativeSingle(host, new Allowance(1, 1, 0),
                pattern, available, null, null, null));
        assertEquals(0, entries.get());

        assertTrue(OmniCapacityDispatcher.tryNativeSingle(host, allowance, pattern, available, null, null, null));
        assertEquals(1, entries.get());
    }

    @Test void publicTerminalAcceptanceSurvivesProviderErrorAndKnownBackpressure() throws Exception {
        var host = new Host(10);
        host.own(CONTAINER, 10);
        host.own(MATERIAL, 10);
        var provider = new PublicProvider(delivery -> {
            assertEquals(host.id, delivery.request().craftingJobId());
            assertEquals(4, delivery.request().craftCount());
            delivery.accept(new OmniBatchDelivery.Receipt(OmniBatchDelivery.Ownership.PERSISTED_PROVIDER_QUEUE,
                    OmniBatchDelivery.Backpressure.SATURATED));
            throw new AssertionError("Terminal ownership was already reported");
        });
        var allowance = new Allowance(10, 32, 1);
        new OmniCapacityDispatcher().tick(host, allowance, service(provider, provider), new Energy(100).service, null);
        assertEquals(1, provider.submissions);
        assertEquals(0, provider.singles);
        assertEquals(n(4), allowance.accepted);
        assertEquals(1, provider.flushed);
        assertFalse(host.ledger.paused());
    }

    @Test void unresolvedPublicDeliveryKeepsCpuReservationAndPausesWithoutReplay() throws Exception {
        var host = new Host(10);
        host.own(CONTAINER, 10);
        host.own(MATERIAL, 10);
        var provider = new PublicProvider(delivery -> {});
        var service = service(provider);
        var network = new Energy(100);
        var dispatcher = new OmniCapacityDispatcher();
        var allowance = new Allowance(10, 32, 3);
        dispatcher.tick(host, allowance, service, network.service, null);
        dispatcher.tick(host, allowance, service, network.service, null);
        assertEquals(1, provider.submissions);
        assertEquals(0, provider.singles);
        assertEquals(BigInteger.ZERO, allowance.accepted);
        assertEquals(n(10), host.omniDispatch$remaining(host.pattern));
        assertEquals(n(6), host.ecoOmni$physicalAmount(CONTAINER));
        assertEquals(n(6), host.ecoOmni$physicalAmount(MATERIAL));
        assertEquals(n(4), host.ledger.available(host.waiting, null, OUTPUT));
        assertTrue(host.ledger.paused());
    }

    @Test void exactEndpointGetsOnePrototypeAndNoPhysicalChargeForInfiniteKey() throws Exception {
        var count = BigInteger.ONE.shiftLeft(78);
        var host = new Host(1);
        host.tasks.put(host.pattern, count);
        host.exact = OmniExactCraftingState.create(Map.of(host.pattern, count), Map.of(CONTAINER, count));
        host.stock.insertWithoutNotification(host.inventory, MATERIAL, count);
        var exact = new ExactProvider(host, count);
        var allowance = new Allowance(Long.MAX_VALUE, 32, 0);
        new OmniCapacityDispatcher().tick(host, allowance, service(exact), new Energy(Double.MAX_VALUE).service, null);
        assertEquals(1, exact.submissions);
        assertEquals(0, exact.singles);
        assertEquals(count, allowance.accepted);
        assertTrue(host.tasks.isEmpty());
        assertEquals(count, host.ledger.available(host.waiting, host.exact, OUTPUT));
        assertEquals(BigInteger.ZERO, host.ecoOmni$physicalAmount(CONTAINER));
        assertEquals(BigInteger.ZERO, host.ecoOmni$physicalAmount(MATERIAL));
    }

    @Test void inputlessRecipeUsesOneBoundedProviderCallWithoutPublicBatchProbe() throws Exception {
        var host = new Host(10, new IPatternDetails() {
            @Override public appeng.api.stacks.AEItemKey getDefinition() { return MATERIAL; }
            @Override public IInput[] getInputs() { return new IInput[0]; }
            @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(OUTPUT, 1)); }
        });
        var provider = new PublicProvider(delivery -> fail("Inputless recipe entered the public batch endpoint"));
        var allowance = new Allowance(10, 32, 1);
        new OmniCapacityDispatcher().tick(host, allowance, service(provider), new Energy(0).service, null);
        assertEquals(1, provider.singles);
        assertEquals(0, provider.submissions);
        assertEquals(BigInteger.ONE, allowance.accepted);
        assertEquals(BigInteger.ONE, host.ledger.available(host.waiting, null, OUTPUT));
    }

    private static BigInteger n(long value) { return BigInteger.valueOf(value); }

    private static OmniCapacityAdmission admission(BigInteger maximum, Function<BigInteger, BigInteger> submit) {
        return new OmniCapacityAdmission() {
            @Override public BigInteger maximumCrafts() { return maximum; }
            @Override public BigInteger submit(BigInteger crafts) { return submit.apply(crafts); }
        };
    }

    private static Service service(ICraftingProvider... providers) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var service = (Service) ((sun.misc.Unsafe) field.get(null)).allocateInstance(Service.class);
        service.providers = List.of(providers);
        return service;
    }

    private static final class Service extends CraftingService {
        List<ICraftingProvider> providers;
        Service() { super(null, null, null); }
        @Override public Iterable<ICraftingProvider> getProviders(IPatternDetails pattern) { return providers; }
    }

    private static class Provider implements ICraftingProvider, OmniPostAccountingOutputProvider {
        int singles, flushed;
        @Override public List<IPatternDetails> getAvailablePatterns() { return List.of(); }
        @Override public boolean isBusy() { return false; }
        @Override public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputHolder) {
            singles++;
            return true;
        }
        @Override public void flushOutputsAfterCpuAccounting() { flushed++; }
    }

    private static final class Target extends Provider implements OmniCapacityTarget {
        private final Function<OmniCapacityRequest, OmniCapacityAdmission> prepare;
        int preparations;
        Target(Function<OmniCapacityRequest, OmniCapacityAdmission> prepare) { this.prepare = prepare; }
        @Override public OmniCapacityAdmission omniDispatch$prepareTarget(OmniCapacityRequest request) {
            preparations++;
            return prepare.apply(request);
        }
    }

    private static final class PublicProvider extends Provider implements OmniBatchCraftingProvider {
        private final Consumer<OmniBatchDelivery> commit;
        int submissions;
        PublicProvider(Consumer<OmniBatchDelivery> commit) { this.commit = commit; }
        @Override public OmniBatchAdmission prepareOmniBatch(OmniBatchProbe probe) {
            assertFalse(probe.oneCraftInputs().isEmpty());
            return new OmniBatchAdmission() {
                @Override public long maxCrafts() { return 4; }
                @Override public void commit(OmniBatchDelivery delivery) {
                    submissions++;
                    commit.accept(delivery);
                }
            };
        }
    }

    private static final class ExactProvider extends Provider implements OmniBigIntegerCraftingProvider {
        private final Host host;
        private final BigInteger count;
        int submissions;
        ExactProvider(Host host, BigInteger count) { this.host = host; this.count = count; }
        @Override public boolean usesNativeBigIntegerBatch() { return true; }
        @Override public BigInteger getMaximumBigIntegerCrafts(IPatternDetails pattern, KeyCounter[] unit, BigInteger limit) {
            assertEquals(count, limit);
            assertEquals(1, unit[0].get(CONTAINER));
            unit[0].clear();
            return limit;
        }
        @Override public boolean pushBigIntegerCraftingPattern(IPatternDetails pattern, BigInteger crafts, KeyCounter[] unit) {
            return fail("Exact dispatch omitted the CPU token");
        }
        @Override public boolean pushBigIntegerCraftingPattern(IPatternDetails pattern, BigInteger crafts,
                KeyCounter[] unit, Object token) {
            submissions++;
            assertSame(host.token, token);
            assertEquals(count, crafts);
            assertEquals(1, unit[0].get(CONTAINER));
            return true;
        }
    }

    private static final class Energy {
        double stored, paid;
        final IEnergyService service = (IEnergyService) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IEnergyService.class}, (proxy, method, args) -> {
                    assertEquals("extractAEPower", method.getName());
                    double taken = Math.min(stored, (double) args[0]);
                    if (args[1] == Actionable.MODULATE) {
                        stored -= taken;
                        paid += taken;
                    }
                    return taken;
                });
        Energy(double stored) { this.stored = stored; }
    }

    private static final class Allowance implements OmniDispatchAllowance {
        private long copies;
        private int work;
        int singles;
        BigInteger accepted = BigInteger.ZERO;
        Allowance(long copies, int work, int singles) { this.copies = copies; this.work = work; this.singles = singles; }
        @Override public boolean capacityMode() { return true; }
        @Override public long remainingCopies() { return copies; }
        @Override public boolean tryWork() { if (work == 0) return false; work--; return true; }
        @Override public boolean trySingle() { if (singles == 0) return false; singles--; return true; }
        @Override public void recordAccepted(BigInteger crafts) {
            accepted = accepted.add(crafts);
            if (copies != Long.MAX_VALUE) copies -= crafts.longValueExact();
        }
        @Override public void close() {}
    }

    private static final class Host implements EcoOmniPlanCpuAccess {
        final UUID id = UUID.randomUUID();
        final Object token = new Object();
        final IPatternDetails pattern;
        final Map<IPatternDetails, Object> tasks = new LinkedHashMap<>();
        final OmniCpuDispatchLedger ledger = new OmniCpuDispatchLedger();
        final OmniExactInventory stock = new OmniExactInventory();
        final ListCraftingInventory inventory = new ListCraftingInventory(ignored -> {});
        final ListCraftingInventory waiting = new ListCraftingInventory(ignored -> {});
        OmniExactCraftingState exact;

        Host(long count) { this(count, new EcoMolecularTestPattern(CONTAINER, ignored -> null)); }
        Host(long count, IPatternDetails pattern) { this.pattern = pattern; tasks.put(pattern, n(count)); }
        void own(AEKey key, long amount) { stock.insertWithoutNotification(inventory, key, n(amount)); }
        @Override public boolean omniDispatch$enabled() { return true; }
        @Override public boolean omniDispatch$stopped() { return ledger.paused(); }
        @Override public UUID omniDispatch$jobId() { return id; }
        @Override public boolean omniDispatch$exactOrder() { return exact != null; }
        @Override public ICraftingInventory omniDispatch$physicalView() { return stock.wrap(inventory); }
        @Override public OmniCpuDispatchLedger.Energy omniDispatch$dispatchEnergy() { return ledger.energy(); }
        @Override public Object omniDispatch$bigIntegerToken(IPatternDetails pattern, BigInteger crafts) { return token; }
        @Override public BigInteger omniDispatch$remaining(IPatternDetails pattern) {
            return (BigInteger) tasks.getOrDefault(pattern, BigInteger.ZERO);
        }
        @Override public Lease omniDispatch$reserveExact(Map<AEKey, BigInteger> inputs) {
            return OmniCpuDispatchLedger.reserve(stock, inventory, inputs);
        }
        @Override public Accounting omniDispatch$stage(OmniCpuDispatchReceipt receipt) {
            return ledger.stage(waiting, exact, receipt, actual -> {
                var remaining = omniDispatch$remaining(actual.pattern()).subtract(actual.crafts());
                if (remaining.signum() == 0) tasks.remove(actual.pattern());
                else tasks.put(actual.pattern(), remaining);
            });
        }
        @Override public void omniDispatch$markDirty() {}
        @Override public void omniDispatch$pause(OmniCpuDispatchReceipt receipt, String reason) { ledger.pause(reason); }
        @Override public OmniExactCraftingState ecoOmni$exactState() { return exact; }
        @Override public void ecoOmni$installExactState(OmniExactCraftingState state) { exact = state; }
        @Override public Map<IPatternDetails, Object> ecoOmni$tasks() { return tasks; }
        @Override public BigInteger ecoOmni$physicalAmount(AEKey key) { return stock.amount(inventory, key); }
        @Override public void ecoOmni$storePhysical(AEKey key, BigInteger amount) { stock.insertWithoutNotification(inventory, key, amount); }
        @Override public void ecoOmni$queueEmitted(AEKey key, BigInteger amount) { throw new UnsupportedOperationException(); }
        @Override public void ecoOmni$restoreRefundSnapshot(CompoundTag data, HolderLookup.Provider registries) {
            throw new UnsupportedOperationException();
        }
    }
}
