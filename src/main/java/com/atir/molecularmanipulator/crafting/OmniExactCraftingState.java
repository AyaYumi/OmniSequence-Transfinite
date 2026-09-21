package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

/** BigInteger task ledger whose long values are exposed to AE2 as rolling windows. */
public final class OmniExactCraftingState {
    public static final long MAX_WINDOW = Long.MAX_VALUE - 1;
    private static final BigInteger BIG_MAX_WINDOW = BigInteger.valueOf(MAX_WINDOW);
    private static final String TASKS_TAG = "tasks";
    private static final String INFINITE_KEYS_TAG = "infiniteKeys";
    private static final String OUTPUT_CREDITS_TAG = "outputCredits";
    private static final String AMOUNT_TAG = "#remaining";

    private final Map<AEItemKey, BigInteger> remaining;
    private final Set<AEKey> infiniteKeys;
    private final Map<AEKey, BigInteger> uncreditedOutputs = new LinkedHashMap<>();
    private final Map<AEKey, BigInteger> completedOutputs = new LinkedHashMap<>();
    private final Map<AEKey, com.appliedenhancements.api.AelisCraftingBatch> lastBatches = new LinkedHashMap<>();
    private com.appliedenhancements.api.AelisExactOutputProgress outputProgress;
    public void setOutputRemaining(BigInteger amount) { outputProgress = new com.appliedenhancements.api.AelisExactOutputProgress(amount); }
    public com.appliedenhancements.api.AelisExactOutputProgress outputProgress() { return outputProgress; }

    private OmniExactCraftingState(
            Map<AEItemKey, BigInteger> remaining, Set<AEKey> infiniteKeys) {
        this.remaining = new LinkedHashMap<>(remaining);
        this.infiniteKeys = Set.copyOf(infiniteKeys);
    }

    public static OmniExactCraftingState create(
            Map<IPatternDetails, BigInteger> patternTimes,
            Map<AEKey, BigInteger> infiniteInputs) {
        Objects.requireNonNull(patternTimes, "patternTimes");
        Objects.requireNonNull(infiniteInputs, "infiniteInputs");
        var remaining = new LinkedHashMap<AEItemKey, BigInteger>();
        patternTimes.forEach((pattern, amount) -> {
            if (pattern == null || pattern.getDefinition() == null
                    || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("Invalid exact crafting task");
            }
            remaining.merge(pattern.getDefinition(), amount, BigInteger::add);
        });
        if (remaining.isEmpty()) {
            throw new IllegalArgumentException("Exact crafting plan has no tasks");
        }

        var infiniteKeys = new LinkedHashSet<AEKey>();
        infiniteInputs.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("Invalid infinite crafting input");
            }
            infiniteKeys.add(key);
        });
        return new OmniExactCraftingState(remaining, infiniteKeys);
    }

    public BigInteger remaining(IPatternDetails pattern) {
        return pattern == null ? BigInteger.ZERO
                : remaining.getOrDefault(pattern.getDefinition(), BigInteger.ZERO);
    }

    public BigInteger nextRemaining(IPatternDetails pattern, long craftCount) {
        if (craftCount <= 0) {
            throw new IllegalArgumentException("Craft count must be positive");
        }
        return nextRemaining(pattern, BigInteger.valueOf(craftCount));
    }

    public BigInteger nextRemaining(IPatternDetails pattern, BigInteger craftCount) {
        if (craftCount == null || craftCount.signum() <= 0) {
            throw new IllegalArgumentException("Craft count must be positive");
        }
        var definition = Objects.requireNonNull(pattern, "pattern").getDefinition();
        var current = remaining.get(definition);
        if (current == null) {
            throw new IllegalStateException("Exact crafting task is not tracked");
        }
        var next = current.subtract(craftCount);
        if (next.signum() < 0) {
            throw new IllegalStateException("Exact crafting task was over-consumed");
        }
        return next;
    }

    public void commit(IPatternDetails pattern, BigInteger next) {
        var definition = Objects.requireNonNull(pattern, "pattern").getDefinition();
        if (!remaining.containsKey(definition) || next == null || next.signum() < 0) {
            throw new IllegalArgumentException("Invalid exact crafting progress");
        }
        if (next.signum() == 0) {
            remaining.remove(definition);
        } else {
            remaining.put(definition, next);
        }
    }

    public boolean isComplete() {
        return remaining.isEmpty();
    }

    public Set<AEItemKey> taskDefinitions() {
        return Set.copyOf(remaining.keySet());
    }

    public OmniExactCraftingState rebind(
            Iterable<IPatternDetails> patterns, Level level) {
        Objects.requireNonNull(patterns, "patterns");
        var rebound = new LinkedHashMap<AEItemKey, BigInteger>();
        var unmatched = new LinkedHashMap<>(remaining);
        for (var pattern : patterns) {
            if (pattern == null || pattern.getDefinition() == null) {
                throw new IllegalArgumentException("Invalid restored crafting task");
            }
            var direct = unmatched.remove(pattern.getDefinition());
            if (direct != null) {
                rebound.put(pattern.getDefinition(), direct);
                continue;
            }
            AEItemKey matched = null;
            for (var definition : unmatched.keySet()) {
                if (definition.equals(pattern.getDefinition())) {
                    matched = definition;
                    break;
                }
            }
            if (matched != null) {
                rebound.put(pattern.getDefinition(), unmatched.remove(matched));
                continue;
            }
            if (level == null) {
                throw new IllegalStateException(
                        "Cannot match restored exact crafting task");
            }
            for (var definition : unmatched.keySet()) {
                var decoded = appeng.api.crafting.PatternDetailsHelper.decodePattern(
                        definition, level);
                if (decoded != null && decoded.equals(pattern)) {
                    matched = definition;
                    break;
                }
            }
            if (matched == null) {
                throw new IllegalStateException(
                        "Cannot match restored exact crafting task");
            }
            rebound.put(pattern.getDefinition(), unmatched.remove(matched));
        }
        if (!unmatched.isEmpty()) {
            throw new IllegalStateException(
                    "Restored exact crafting state has unmatched tasks");
        }
        var state = new OmniExactCraftingState(rebound, infiniteKeys);
        state.outputProgress = outputProgress;
        state.uncreditedOutputs.putAll(uncreditedOutputs);
        state.completedOutputs.putAll(completedOutputs);
        state.lastBatches.putAll(lastBatches);
        return state;
    }

    public Set<AEKey> infiniteKeys() {
        return infiniteKeys;
    }

    public boolean hasOnlyInfiniteInputs(KeyCounter[] unitPrototype) {
        if (unitPrototype == null || unitPrototype.length == 0) {
            return false;
        }
        boolean found = false;
        for (var counter : unitPrototype) {
            if (counter == null) {
                return false;
            }
            for (var entry : counter) {
                if (entry.getKey() == null || entry.getLongValue() <= 0
                        || !infiniteKeys.contains(entry.getKey())) {
                    return false;
                }
                found = true;
            }
        }
        return found;
    }

    public void queueOutput(AEKey key, long unitAmount, BigInteger craftCount) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(craftCount, "craftCount");
        if (unitAmount <= 0 || craftCount.signum() <= 0) {
            throw new IllegalArgumentException("Invalid exact output credit");
        }
        uncreditedOutputs.merge(key,
                BigInteger.valueOf(unitAmount).multiply(craftCount),
                BigInteger::add);
    }

    /** Adds an already-scaled exact output from an asynchronous native batch. */
    public void queueOutput(AEKey key, BigInteger exactAmount) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(exactAmount, "exactAmount");
        if (exactAmount.signum() <= 0) {
            throw new IllegalArgumentException("Invalid exact output credit");
        }
        uncreditedOutputs.merge(key, exactAmount, BigInteger::add);
    }

    /** Moves as much pending credit as possible into one AE2 long window. */
    public long claimOutputWindow(AEKey key, long currentlyWaiting) {
        Objects.requireNonNull(key, "key");
        if (currentlyWaiting < 0) {
            throw new IllegalArgumentException("Negative waiting-for amount");
        }
        var pending = uncreditedOutputs.get(key);
        if (pending == null || pending.signum() <= 0
                || currentlyWaiting == Long.MAX_VALUE) {
            return 0;
        }
        long room = Long.MAX_VALUE - currentlyWaiting;
        long claimed = pending.min(BigInteger.valueOf(room)).longValueExact();
        var next = pending.subtract(BigInteger.valueOf(claimed));
        if (next.signum() == 0) {
            uncreditedOutputs.remove(key);
        } else {
            uncreditedOutputs.put(key, next);
        }
        return claimed;
    }

    public BigInteger uncreditedOutput(AEKey key) {
        return uncreditedOutputs.getOrDefault(key, BigInteger.ZERO);
    }

    public Map<AEKey, BigInteger> uncreditedOutputs() {
        return Map.copyOf(uncreditedOutputs);
    }

    /** Counts actual CPU settlement once; downstream consumption never subtracts it. */
    public void recordCompleted(AEKey key, long amount) {
        recordCompleted(key, BigInteger.valueOf(amount));
    }

    public void recordCompleted(AEKey key, BigInteger amount) {
        if (key == null || amount.signum() <= 0) throw new IllegalArgumentException("Invalid completed output");
        completedOutputs.merge(key, amount, BigInteger::add);
    }

    public void consumeOutputCredit(AEKey key, BigInteger amount) {
        var remaining = uncreditedOutput(key).subtract(amount);
        if (amount.signum() < 0 || remaining.signum() < 0) throw new IllegalArgumentException("Output credit over-consumed");
        if (remaining.signum() == 0) uncreditedOutputs.remove(key); else uncreditedOutputs.put(key, remaining);
    }

    public Map<AEKey, BigInteger> completedOutputs() { return Map.copyOf(completedOutputs); }

    public void recordBatch(IPatternDetails pattern, BigInteger crafts, Map<AEKey, BigInteger> inputs) {
        if (inputs.size() > 64) return; // Status metadata must never fail an accepted crafting push.
        var outputs = new LinkedHashMap<AEKey, BigInteger>();
        for (var output : pattern.getOutputs()) {
            if (output.amount() > 0) outputs.merge(output.what(), BigInteger.valueOf(output.amount()).multiply(crafts), BigInteger::add);
        }
        outputs.forEach((key, amount) -> lastBatches.put(key, new com.appliedenhancements.api.AelisCraftingBatch(amount, inputs)));
    }

    public Map<AEKey, com.appliedenhancements.api.AelisCraftingBatch> lastBatches() { return Map.copyOf(lastBatches); }

    public ICraftingInventory wrap(ICraftingInventory inventory) {
        return infiniteKeys.isEmpty()
                ? inventory
                : new OmniInfiniteCraftingInventory(inventory, infiniteKeys);
    }

    public static long window(BigInteger amount) {
        if (amount == null || amount.signum() <= 0) {
            return 0;
        }
        return amount.compareTo(BIG_MAX_WINDOW) > 0
                ? MAX_WINDOW
                : amount.longValueExact();
    }

    public CompoundTag write(HolderLookup.Provider registries) {
        var result = new CompoundTag();
        if (outputProgress != null) result.putString("outputRemaining", outputProgress.remaining().toString());
        var tasks = new ListTag();
        remaining.forEach((definition, amount) -> {
            var entry = definition.toTagGeneric(registries);
            entry.putString(AMOUNT_TAG, amount.toString());
            tasks.add(entry);
        });
        result.put(TASKS_TAG, tasks);

        var keys = new ListTag();
        for (var key : infiniteKeys) {
            keys.add(key.toTagGeneric(registries));
        }
        result.put(INFINITE_KEYS_TAG, keys);

        var outputCredits = new ListTag();
        uncreditedOutputs.forEach((key, amount) -> {
            var entry = key.toTagGeneric(registries);
            entry.putString(AMOUNT_TAG, amount.toString());
            outputCredits.add(entry);
        });
        result.put(OUTPUT_CREDITS_TAG, outputCredits);
        var completed = new ListTag();
        completedOutputs.forEach((key, amount) -> {
            var entry = key.toTagGeneric(registries);
            entry.putString(AMOUNT_TAG, amount.toString()); completed.add(entry);
        });
        result.put("completedOutputs", completed);
        var batches = new ListTag();
        lastBatches.forEach((key, batch) -> {
            var entry = key.toTagGeneric(registries); entry.putString("amount", batch.outputAmount().toString());
            var inputs = new ListTag();
            batch.inputs().forEach((inputKey, amount) -> {
                var input = inputKey.toTagGeneric(registries); input.putString("amount", amount.toString()); inputs.add(input);
            });
            entry.put("inputs", inputs); batches.add(entry);
        });
        result.put("lastBatches", batches);
        return result;
    }

    public static OmniExactCraftingState read(
            CompoundTag tag, HolderLookup.Provider registries) {
        Objects.requireNonNull(tag, "tag");
        var remaining = new LinkedHashMap<AEItemKey, BigInteger>();
        var tasks = tag.getList(TASKS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < tasks.size(); i++) {
            var entry = tasks.getCompound(i);
            var key = AEKey.fromTagGeneric(registries, entry);
            if (!(key instanceof AEItemKey definition)
                    || !entry.contains(AMOUNT_TAG, Tag.TAG_STRING)) {
                throw new IllegalArgumentException("Invalid exact crafting task tag");
            }
            var amount = new BigInteger(entry.getString(AMOUNT_TAG));
            if (amount.signum() <= 0 || remaining.putIfAbsent(definition, amount) != null) {
                throw new IllegalArgumentException("Invalid exact crafting task amount");
            }
        }
        var infiniteKeys = new LinkedHashSet<AEKey>();
        var keys = tag.getList(INFINITE_KEYS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < keys.size(); i++) {
            var key = AEKey.fromTagGeneric(registries, keys.getCompound(i));
            if (key == null) {
                throw new IllegalArgumentException("Invalid infinite crafting key tag");
            }
            infiniteKeys.add(key);
        }
        var state = new OmniExactCraftingState(remaining, infiniteKeys);
        var outputCredits = tag.getList(OUTPUT_CREDITS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < outputCredits.size(); i++) {
            var entry = outputCredits.getCompound(i);
            var key = AEKey.fromTagGeneric(registries, entry);
            if (key == null || !entry.contains(AMOUNT_TAG, Tag.TAG_STRING)) {
                throw new IllegalArgumentException("Invalid exact output credit tag");
            }
            var amount = new BigInteger(entry.getString(AMOUNT_TAG));
            if (amount.signum() <= 0
                    || state.uncreditedOutputs.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("Invalid exact output credit amount");
            }
        }
        if (tag.contains("outputRemaining", Tag.TAG_STRING)) state.setOutputRemaining(new BigInteger(tag.getString("outputRemaining")));
        var completed = tag.getList("completedOutputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < completed.size(); i++) {
            var entry = completed.getCompound(i);
            var key = AEKey.fromTagGeneric(registries, entry);
            var amount = new BigInteger(entry.getString(AMOUNT_TAG));
            if (key == null || amount.signum() <= 0 || state.completedOutputs.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("Invalid completed output tag");
            }
        }
        var batches = tag.getList("lastBatches", Tag.TAG_COMPOUND);
        for (int i = 0; i < batches.size(); i++) {
            var entry = batches.getCompound(i);
            var key = AEKey.fromTagGeneric(registries, entry);
            var inputs = new LinkedHashMap<AEKey, BigInteger>();
            var inputTags = entry.getList("inputs", Tag.TAG_COMPOUND);
            for (int j = 0; j < inputTags.size(); j++) {
                var input = inputTags.getCompound(j);
                var inputKey = AEKey.fromTagGeneric(registries, input);
                if (inputKey == null || inputs.putIfAbsent(inputKey, new BigInteger(input.getString("amount"))) != null) {
                    throw new IllegalArgumentException("Invalid saved batch input");
                }
            }
            if (key == null || state.lastBatches.putIfAbsent(key, new com.appliedenhancements.api.AelisCraftingBatch(
                    new BigInteger(entry.getString("amount")), inputs)) != null) throw new IllegalArgumentException("Invalid saved batch");
        }
        return state;
    }
}
