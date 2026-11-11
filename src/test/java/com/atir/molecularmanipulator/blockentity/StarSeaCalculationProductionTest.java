package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.atir.molecularmanipulator.ae2.StarSignalKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class StarSeaCalculationProductionTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final IActionSource SOURCE = IActionSource.empty();

    @Test
    void aBitIsFullyPaidBeforeInsertionAndNeverDebitsPowerAfterOutput() {
        var rig = new Rig();
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.tick());
        assertEquals(List.of("simulate", "pay", "insert"), rig.events);
        assertEquals(1, rig.storage.inserted);
        assertEquals(32, rig.energy.extracted);
        assertEquals(0, rig.production.energyCredit());
        assertEquals(0, rig.energy.simulations, "Operating ticks use actual extraction, not a power simulation");
        assertEquals(List.of(32.0), rig.energy.demands);

        rig.tick();
        assertEquals(2, rig.storage.inserted, "At most one bit is inserted by each tick");
        assertEquals(64, rig.energy.extracted);
    }

    @Test
    void partialExtractionsAccumulateAndRequestOnlyTheUnpaidRemainder() {
        var rig = new Rig();
        rig.energy.perCallLimit = 8;
        for (int tick = 1; tick <= 3; tick++) {
            assertEquals(StarSeaCalculationProduction.Result.NO_POWER, rig.tick());
            assertEquals(tick * 8, rig.production.energyCredit());
            assertEquals(0, rig.storage.realAttempts, "Insufficient private credit must never reach real output");
        }
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.tick());
        assertEquals(List.of(32.0, 24.0, 16.0, 8.0), rig.energy.demands);
        assertEquals(1, rig.storage.inserted);
        assertEquals(32, rig.energy.extracted);
        assertEquals(0, rig.production.energyCredit());
    }

    @Test
    void noPowerDoesNotCreateAnySignalAndRecoversWhenPowerArrives() {
        var rig = new Rig();
        rig.energy.balance = 0;
        assertEquals(StarSeaCalculationProduction.Result.NO_POWER, rig.tick());
        assertEquals(0, rig.production.energyCredit());
        assertEquals(0, rig.storage.realAttempts);
        rig.energy.balance = 32;
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.tick());
        assertEquals(1, rig.storage.inserted);
        assertEquals(32, rig.energy.extracted);
    }

    @Test
    void almostPaidIsNotAFreeBitThroughAnEpsilonAllowance() {
        var rig = new Rig();
        double almostPaid = Math.nextDown(32.0);
        rig.production.restoreEnergyCredit(almostPaid);
        rig.energy.balance = 0;
        assertEquals(StarSeaCalculationProduction.Result.NO_POWER, rig.tick());
        assertEquals(almostPaid, rig.production.energyCredit());
        assertEquals(0, rig.storage.realAttempts);
        assertEquals(List.of(32.0 - almostPaid), rig.energy.demands);
    }

    @Test
    void fullOutputDoesNotDrawPowerEvenAfterRepeatedTicks() {
        var rig = new Rig();
        rig.storage.capacity = 0;
        for (int tick = 0; tick < 10; tick++) {
            assertEquals(StarSeaCalculationProduction.Result.OUTPUT_FULL, rig.tick());
        }
        assertEquals(0, rig.energy.extractions);
        assertEquals(0, rig.storage.realAttempts);
        assertEquals(0, rig.production.energyCredit());
        rig.storage.capacity = 1;
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.tick());
        assertEquals(1, rig.storage.inserted);
        assertEquals(32, rig.energy.extracted);
    }

    @Test
    void fillingStoragePreservesPartialCreditAndResumingPaysOnlyTheRemainder() {
        var rig = new Rig();
        rig.energy.perCallLimit = 12;
        assertEquals(StarSeaCalculationProduction.Result.NO_POWER, rig.tick());
        rig.storage.capacity = 0;
        assertEquals(StarSeaCalculationProduction.Result.OUTPUT_FULL, rig.tick());
        assertEquals(StarSeaCalculationProduction.Result.OUTPUT_FULL, rig.tick());
        assertEquals(12, rig.production.energyCredit());
        assertEquals(12, rig.energy.extracted);
        assertEquals(1, rig.energy.extractions);
        rig.storage.capacity = 1;
        rig.energy.perCallLimit = 32;
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.tick());
        assertEquals(List.of(32.0, 20.0), rig.energy.demands);
        assertEquals(32, rig.energy.extracted);
        assertEquals(1, rig.storage.inserted);
    }

    @Test
    void simulationAcceptingButRealInsertionRejectingRetainsAllPaidEnergy() {
        var rig = new Rig();
        rig.storage.acceptReal = false;
        assertEquals(StarSeaCalculationProduction.Result.OUTPUT_FULL, rig.tick());
        assertEquals(32, rig.production.energyCredit());
        assertEquals(32, rig.energy.extracted);
        assertEquals(0, rig.storage.inserted, "A failed real insertion is not a pending signal");

        // Repeated races must not charge the same bit again.
        for (int tick = 0; tick < 4; tick++) {
            assertEquals(StarSeaCalculationProduction.Result.OUTPUT_FULL, rig.tick());
        }
        rig.storage.capacity = 0;
        assertEquals(StarSeaCalculationProduction.Result.OUTPUT_FULL, rig.tick());
        assertEquals(32, rig.production.energyCredit());
        assertEquals(1, rig.energy.extractions);

        rig.reload();
        rig.storage.capacity = 1;
        rig.storage.acceptReal = true;
        rig.energy.balance = 0;
        rig.events.clear();
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.tick());
        assertEquals(List.of("simulate", "insert"), rig.events);
        assertEquals(1, rig.storage.inserted);
        assertEquals(0, rig.production.energyCredit());
        assertEquals(1, rig.energy.extractions, "The restored full payment needs no second external debit");
    }

    @Test
    void aRaceAfterPartialPaymentAlsoPreservesTheEntirePaymentAcrossReload() {
        var rig = new Rig();
        rig.energy.perCallLimit = 20;
        assertEquals(StarSeaCalculationProduction.Result.NO_POWER, rig.tick());
        rig.reload();
        rig.storage.acceptReal = false;
        assertEquals(StarSeaCalculationProduction.Result.OUTPUT_FULL, rig.tick());
        assertEquals(List.of(32.0, 12.0), rig.energy.demands);
        assertEquals(32, rig.production.energyCredit());
        rig.reload();
        rig.storage.acceptReal = true;
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.tick());
        assertEquals(32, rig.energy.extracted);
        assertEquals(1, rig.storage.inserted);
        assertEquals(0, rig.production.energyCredit());
    }

    @Test
    void repeatedPartialCyclesAndReloadsConserveAllPaidEnergy() {
        var rig = new Rig();
        rig.energy.perCallLimit = 5.5;
        for (int tick = 0; tick < 600; tick++) {
            long before = rig.storage.inserted;
            rig.tick();
            assertTrue(rig.storage.inserted - before <= 1);
            assertEquals(rig.energy.extracted, rig.storage.inserted * 32 + rig.production.energyCredit());
            rig.reload();
        }
        assertEquals(100, rig.storage.inserted);
        assertEquals(3_200, rig.energy.extracted);
        assertEquals(0, rig.production.energyCredit());
    }

    @Test
    void mixedPartialPowerFullStorageRacesAndReloadsNeverLoseOrDuplicateCredit() {
        var rig = new Rig();
        var random = new Random(0x57A25EA);
        for (int tick = 0; tick < 2_000; tick++) {
            rig.energy.perCallLimit = random.nextInt(129) / 4.0;
            rig.storage.capacity = random.nextInt(4) == 0 ? 0 : 1;
            rig.storage.acceptReal = random.nextInt(3) != 0;
            long before = rig.storage.inserted;
            rig.tick();
            assertTrue(rig.storage.inserted - before >= 0 && rig.storage.inserted - before <= 1);
            assertTrue(rig.production.energyCredit() >= 0 && rig.production.energyCredit() <= 32);
            assertEquals(rig.energy.extracted, rig.storage.inserted * 32 + rig.production.energyCredit(),
                    "Every extracted AE unit must belong to accepted output or persistent private credit");
            rig.reload();
        }
        assertTrue(rig.storage.inserted > 100, "The conservation check must include many complete cycles");
    }

    @Test
    void aRestoredFullPaymentCanBeUsedOnceAndOnlyOnce() {
        var rig = new Rig();
        rig.production.restoreEnergyCredit(32);
        rig.energy.balance = 0;
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.tick());
        assertEquals(1, rig.storage.inserted);
        assertEquals(0, rig.energy.extractions);
        rig.reload();
        assertEquals(StarSeaCalculationProduction.Result.NO_POWER, rig.tick());
        assertEquals(1, rig.storage.inserted);
        assertEquals(0, rig.production.energyCredit());
    }

    @Test
    void statusInspectionOnlySimulatesStorageAndMissingEnergy() {
        var rig = new Rig();
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.inspect());
        assertEquals(0, rig.energy.extracted);
        assertEquals(0, rig.energy.extractions);
        assertEquals(0, rig.production.energyCredit());
        assertEquals(0, rig.storage.realAttempts);
        assertEquals(List.of("simulate", "power-simulation"), rig.events);

        rig.production.restoreEnergyCredit(20);
        rig.energy.balance = 11;
        assertEquals(StarSeaCalculationProduction.Result.NO_POWER, rig.inspect());
        rig.energy.balance = 12;
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.inspect());
        assertEquals(20, rig.production.energyCredit());
        assertEquals(12, rig.energy.balance, "Status reads must not buy power");
        assertEquals(0, rig.storage.inserted);
        assertEquals(List.of(32.0, 12.0, 12.0), rig.energy.simulatedDemands);
    }

    @Test
    void inspectingFullOutputOrFullCreditDoesNotTouchEnergy() {
        var rig = new Rig();
        rig.production.restoreEnergyCredit(32);
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.inspect());
        rig.storage.capacity = 0;
        assertEquals(StarSeaCalculationProduction.Result.OUTPUT_FULL, rig.inspect());
        assertEquals(0, rig.energy.simulations);
        assertEquals(0, rig.energy.extractions);
        assertEquals(32, rig.production.energyCredit());
        assertEquals(0, rig.storage.inserted);
    }

    @Test
    void restoredCreditIsFiniteNonnegativeAndCappedAtTheLargestSupportedPayment() {
        var production = new StarSeaCalculationProduction();
        for (double invalid : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -1}) {
            production.restoreEnergyCredit(invalid);
            assertEquals(0, production.energyCredit());
        }
        production.restoreEnergyCredit(12.25);
        assertEquals(12.25, production.energyCredit());
        production.restoreEnergyCredit(1_000);
        assertEquals(1_000, production.energyCredit(), "A configured payment larger than 32 AE survives reload");
        production.restoreEnergyCredit(StarSeaCalculationProduction.MAX_ENERGY_PER_TICK + 1_000);
        assertEquals(StarSeaCalculationProduction.MAX_ENERGY_PER_TICK, production.energyCredit());
        production.restoreEnergyCredit(0);
        assertEquals(0, production.energyCredit());
    }

    @Test
    void invalidOrNegativeEnergyReturnsCannotCreateCreditOrOutput() {
        var rig = new Rig();
        rig.production.restoreEnergyCredit(6);
        for (double invalid : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -1, 0}) {
            assertEquals(StarSeaCalculationProduction.Result.NO_POWER,
                    rig.production.tick(rig.storage, (amount, mode, multiplier) -> invalid, SOURCE, 1, 32));
            assertEquals(6, rig.production.energyCredit());
            assertEquals(0, rig.storage.realAttempts);
        }
    }

    @Test
    void outputAndOperatingEnergyCanBeConfiguredIndependently() {
        var rig = new Rig();
        rig.bitsPerTick = 7;
        rig.energyPerTick = 80.5;
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.inspect());
        assertEquals(0, rig.energy.extracted);
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.tick());
        assertEquals(7, rig.storage.inserted);
        assertEquals(80.5, rig.energy.extracted);
        assertEquals(0, rig.production.energyCredit());
    }

    @Test
    void boostedBatchDoesNotIncreaseTheOperatingEnergyPrice() {
        var rig = new Rig();
        rig.bitsPerTick = 5;
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.tick());
        assertEquals(5, rig.storage.inserted);
        assertEquals(32, rig.energy.extracted);
        rig.storage.capacity = 4;
        assertEquals(StarSeaCalculationProduction.Result.OUTPUT_FULL, rig.tick());
        assertEquals(32, rig.energy.extracted, "A boosted batch needs enough storage before drawing power");
    }

    @Test
    void partialRealBatchAcceptanceSpendsOnlyTheAcceptedShare() {
        var rig = new Rig();
        rig.bitsPerTick = 5;
        rig.storage.realLimit = 2;
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.tick());
        assertEquals(2, rig.storage.inserted);
        assertEquals(19.2, rig.production.energyCredit(), 1e-12);
        rig.reload();
        rig.storage.realLimit = Long.MAX_VALUE;
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.tick());
        assertEquals(7, rig.storage.inserted);
        assertEquals(44.8, rig.energy.extracted, 1e-12);
        assertEquals(0, rig.production.energyCredit());
    }

    @Test
    void higherConfiguredPricesAndPartialPaymentsSurviveReloads() {
        var rig = new Rig();
        rig.bitsPerTick = 12;
        rig.energyPerTick = 128;
        rig.energy.perCallLimit = 64;
        assertEquals(StarSeaCalculationProduction.Result.NO_POWER, rig.tick());
        rig.reload();
        assertEquals(64, rig.production.energyCredit());
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.tick());
        assertEquals(12, rig.storage.inserted);
        assertEquals(128, rig.energy.extracted);
    }

    @Test
    void priceChangesKeepUnusedPaidEnergyAndPayOnlyTheNewRemainder() {
        var rig = new Rig();
        rig.production.restoreEnergyCredit(32);
        rig.energyPerTick = 8;
        rig.energy.balance = 0;
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.tick());
        assertEquals(24, rig.production.energyCredit());
        rig.reload();
        assertEquals(24, rig.production.energyCredit());
        rig.energyPerTick = 64;
        rig.energy.balance = 40;
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.tick());
        assertEquals(List.of(40.0), rig.energy.demands);
        assertEquals(0, rig.production.energyCredit());
    }

    @Test
    void zeroConfiguredEnergyProducesWithoutCallingTheEnergySource() {
        var rig = new Rig();
        rig.bitsPerTick = 9;
        rig.energyPerTick = 0;
        rig.energy.balance = 0;
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.inspect());
        assertEquals(StarSeaCalculationProduction.Result.RUNNING, rig.tick());
        assertEquals(9, rig.storage.inserted);
        assertEquals(0, rig.energy.extractions);
        assertEquals(0, rig.energy.simulations);
    }

    private static final class Rig {
        StarSeaCalculationProduction production = new StarSeaCalculationProduction();
        final List<String> events = new ArrayList<>();
        final FakeEnergy energy = new FakeEnergy(this);
        final FakeStorage storage = new FakeStorage(this);
        long bitsPerTick = 1;
        double energyPerTick = 32;

        StarSeaCalculationProduction.Result tick() {
            return production.tick(storage, energy, SOURCE, bitsPerTick, energyPerTick);
        }

        StarSeaCalculationProduction.Result inspect() {
            return production.inspect(storage, energy, SOURCE, bitsPerTick, energyPerTick);
        }

        void reload() {
            var saved = new CompoundTag();
            saved.putDouble("credit", production.energyCredit());
            production = new StarSeaCalculationProduction();
            production.restoreEnergyCredit(saved.getDouble("credit"));
        }
    }

    private static final class FakeEnergy implements IEnergySource {
        private final Rig rig;
        double balance = 100_000;
        double perCallLimit = Double.MAX_VALUE;
        double extracted;
        int extractions;
        int simulations;
        final List<Double> demands = new ArrayList<>();
        final List<Double> simulatedDemands = new ArrayList<>();

        FakeEnergy(Rig rig) {
            this.rig = rig;
        }

        @Override
        public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
            assertSame(PowerMultiplier.CONFIG, multiplier);
            assertTrue(amount > 0 && amount <= rig.energyPerTick, "Only the missing batch price may be requested");
            double available = Math.min(amount, Math.min(balance, perCallLimit));
            if (mode == Actionable.MODULATE) {
                rig.events.add("pay");
                demands.add(amount);
                extractions++;
                extracted += available;
                balance -= available;
            } else {
                rig.events.add("power-simulation");
                simulatedDemands.add(amount);
                simulations++;
            }
            return available;
        }
    }

    private static final class FakeStorage implements MEStorage {
        private final Rig rig;
        long capacity = Long.MAX_VALUE;
        boolean acceptReal = true;
        long realLimit = Long.MAX_VALUE;
        long inserted;
        int realAttempts;

        FakeStorage(Rig rig) {
            this.rig = rig;
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            assertSame(StarSignalKey.INSTANCE, key);
            assertSame(SOURCE, source);
            assertEquals(rig.bitsPerTick, amount);
            if (mode == Actionable.SIMULATE) {
                rig.events.add("simulate");
                return Math.min(capacity, amount);
            }
            rig.events.add("insert");
            realAttempts++;
            assertTrue(rig.production.energyCredit() >= rig.energyPerTick, "External output must see a fully paid private balance");
            if (!acceptReal) return 0;
            long accepted = Math.min(amount, Math.min(capacity, realLimit));
            capacity -= accepted;
            inserted += accepted;
            return accepted;
        }

        @Override
        public Component getDescription() {
            return Component.literal("Star Sea production test storage");
        }
    }
}
