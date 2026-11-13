package com.atir.molecularmanipulator.starcomputation;

import java.util.function.BooleanSupplier;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Per-core certification and actual energy credit; independent of team recipe unlocks and physical rings. */
public final class StarComputationUpgradeProgress {
    public static final String TAG = "star_orrery_upgrade";
    private int certified = 3;
    private int target;
    private double paid;
    private boolean running;
    private boolean migrationPending;
    private double rate;

    public enum Result { PAUSED, BLOCKED, RUNNING, NO_POWER, COMPLETE }

    public int certifiedLevel() { return certified; }
    public int targetLevel() { return target; }
    public double paidEnergy() { return paid; }
    public boolean running() { return running; }
    public boolean migrationPending() { return migrationPending; }
    public double actualRate() { return rate; }
    public int effectiveLevel(int physicalRings) { return Math.min(com.atir.molecularmanipulator.util.MathCompat.clamp(physicalRings, 0, 9), certified); }

    public boolean start(int level) {
        if (migrationPending || running || level < 4 || level > 9 || level != certified + 1
                || target != 0 && target != level) return false;
        target = level;
        running = true;
        rate = 0;
        return true;
    }

    public boolean pause() {
        if (!running) return false;
        running = false;
        rate = 0;
        return true;
    }

    /** Call only after a resolved, runtime physical scan, never with saved ring hints or an archived package's label. */
    public boolean migrateVerified(int physicalRings) {
        if (!migrationPending) return false;
        certified = Math.max(3, com.atir.molecularmanipulator.util.MathCompat.clamp(physicalRings, 0, 9));
        migrationPending = false;
        return true;
    }

    public void resetRate() { rate = 0; }

    /** Eligibility is checked before payment and once again before certification. No simulation creates credit. */
    public Result tick(double energy, double limit, BooleanSupplier eligible, DoubleUnaryOperator extract) {
        rate = 0;
        if (!running) return Result.PAUSED;
        if (!Double.isFinite(energy) || energy < 0 || !Double.isFinite(limit) || limit <= 0
                || migrationPending || target != certified + 1 || !eligible.getAsBoolean()) return Result.BLOCKED;
        double demand = Math.min(limit, Math.max(0, energy - paid));
        if (demand > 0) {
            double taken = extract.applyAsDouble(demand);
            if (Double.isFinite(taken) && taken > 0) {
                rate = Math.min(demand, taken);
                paid += rate;
            }
        }
        if (paid >= energy && eligible.getAsBoolean()) {
            certified = target;
            target = 0;
            paid = 0;
            running = false;
            return Result.COMPLETE;
        }
        return rate > 0 ? Result.RUNNING : Result.NO_POWER;
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putInt("version", 1);
        tag.putInt("certified_level", certified);
        tag.putInt("target_level", target);
        tag.putDouble("paid_ae", paid);
        tag.putBoolean("running", running);
        tag.putBoolean("migration_pending", migrationPending);
        return tag;
    }

    public void load(CompoundTag root) {
        rate = 0;
        if (!root.contains(TAG, Tag.TAG_COMPOUND)) {
            certified = 3;
            target = 0;
            paid = 0;
            running = false;
            migrationPending = true;
            return;
        }
        var tag = root.getCompound(TAG);
        certified = com.atir.molecularmanipulator.util.MathCompat.clamp(tag.getInt("certified_level"), 3, 9);
        int savedTarget = tag.getInt("target_level");
        target = savedTarget >= 4 && savedTarget <= 9 && savedTarget == certified + 1 ? savedTarget : 0;
        double savedPaid = tag.getDouble("paid_ae");
        paid = target != 0 && Double.isFinite(savedPaid) ? Math.max(0, savedPaid) : 0;
        migrationPending = tag.getBoolean("migration_pending");
        running = target != 0 && !migrationPending && tag.getBoolean("running");
    }
}
