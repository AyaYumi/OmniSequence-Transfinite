package com.atir.molecularmanipulator.integration.eco;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergyService;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import java.math.BigDecimal;
import java.math.BigInteger;
import net.minecraft.nbt.CompoundTag;

/** CPU-owned prepaid balance. Rejections retain real debits as reusable, persistent energy credit. */
public final class EcoCpuDispatchEnergy {
    private BigDecimal credit = BigDecimal.ZERO;

    public BigDecimal credit() { return credit; }

    public BigInteger maximum(IEnergyService service, double unitPower, BigInteger requested) {
        if (!Double.isFinite(unitPower) || unitPower < 0 || requested.signum() <= 0) return BigInteger.ZERO;
        if (unitPower == 0) return requested;
        var unit = new BigDecimal(unitPower);
        var deficit = unit.multiply(new BigDecimal(requested)).subtract(credit).max(BigDecimal.ZERO);
        double probe = Math.min(Double.MAX_VALUE, deficit.doubleValue());
        if (new BigDecimal(probe).compareTo(deficit) < 0 && probe < Double.MAX_VALUE) probe = Math.nextUp(probe);
        double available = probe == 0 ? 0
                : service.extractAEPower(probe, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (!Double.isFinite(available) || available < 0 || available > probe)
            throw new IllegalStateException("Invalid energy simulation result");
        return credit.add(new BigDecimal(available)).divideToIntegralValue(unit).toBigInteger()
                .min(requested).max(BigInteger.ZERO);
    }

    public ECOFastPathFacade.Reservation reserve(IEnergyService service, double unitPower, BigInteger count) {
        if (!Double.isFinite(unitPower) || unitPower < 0 || count.signum() <= 0) return null;
        var cost = new BigDecimal(unitPower).multiply(new BigDecimal(count));
        var deficit = cost.subtract(credit);
        if (deficit.signum() > 0) {
            double request = deficit.doubleValue();
            if (!Double.isFinite(request)) return null;
            if (new BigDecimal(request).compareTo(deficit) < 0) request = Math.nextUp(request);
            if (!Double.isFinite(request)) return null;
            double paid = service.extractAEPower(request, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (!Double.isFinite(paid) || paid < 0 || paid > request)
                throw new IllegalStateException("Invalid energy debit result");
            credit = credit.add(new BigDecimal(paid));
            if (credit.compareTo(cost) < 0) return null;
        }
        credit = credit.subtract(cost);
        return new ECOFastPathFacade.Reservation() {
            private boolean settled;
            public void commit() { settled = true; }
            public void refund() {
                if (!settled) { settled = true; credit = credit.add(cost); }
            }
        };
    }

    /** Preparation only probes this view; actual reservations always debit the real service. */
    public IEnergyService simulation(IEnergyService service) {
        return new IEnergyService() {
            public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
                if (mode != Actionable.SIMULATE) throw new IllegalStateException("Preparation mutated energy");
                double balance = credit.doubleValue();
                if (Double.isFinite(balance) && new BigDecimal(balance).compareTo(credit) > 0)
                    balance = Math.nextDown(balance);
                double covered = Math.min(amount, balance);
                return Math.min(amount, covered + service.extractAEPower(
                        Math.max(0, amount - covered), mode, multiplier));
            }
            public double getIdlePowerUsage() { return service.getIdlePowerUsage(); }
            public double getChannelPowerUsage() { return service.getChannelPowerUsage(); }
            public double getAvgPowerUsage() { return service.getAvgPowerUsage(); }
            public double getAvgPowerInjection() { return service.getAvgPowerInjection(); }
            public boolean isNetworkPowered() { return service.isNetworkPowered(); }
            public double injectPower(double amount, Actionable mode) { throw new UnsupportedOperationException(); }
            public double getStoredPower() { return service.getStoredPower(); }
            public double getMaxStoredPower() { return service.getMaxStoredPower(); }
            public double getEnergyDemand(double amount) { return service.getEnergyDemand(amount); }
        };
    }

    public void write(CompoundTag tag) { tag.putString("credit", credit.toString()); }
    public void read(CompoundTag tag) {
        var restored = tag.contains("credit") ? new BigDecimal(tag.getString("credit")) : BigDecimal.ZERO;
        if (restored.signum() < 0) throw new IllegalArgumentException("Negative energy credit");
        credit = restored;
    }
}
