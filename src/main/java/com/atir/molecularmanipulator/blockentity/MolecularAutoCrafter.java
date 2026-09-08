package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.CraftingEvent;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.filter.IAEItemFilter;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.crafting.MolecularBatchCraftingExtractor;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Quantum-crafter style passive crafting owned by one sequence-array controller.
 *
 * <p>Every configured pattern is evaluated independently. A single evaluation may
 * represent up to {@link Long#MAX_VALUE} recipe executions; the implementation
 * delegates actual-key selection, substitution and reusable-input validation to
 * AE2 and the same molecular batch helpers used by crafting CPUs.</p>
 */
public final class MolecularAutoCrafter implements InternalInventoryHost {
    public static final int PATTERN_SLOTS = MolecularAutoCraftSchema.PATTERN_SLOTS;
    public static final int MAX_INPUTS = 9;
    private static final String ROOT_TAG = "molecular_auto_crafter";
    private static final String VERSION_TAG = "version";
    static final int SCHEMA_VERSION = MolecularAutoCraftSchema.VERSION;
    private static final String PATTERN_INVENTORY_TAG = "pattern_inventory";
    private static final String CONFIGS_TAG = "patterns";
    private static final String SLOT_TAG = "slot";
    private static final String DEFINITION_TAG = "definition";
    private static final String ENABLED_TAG = "enabled";
    private static final String OUTPUT_LIMIT_TAG = "output_limit";
    private static final String PROTECTIONS_TAG = "protections";
    private static final String TOTAL_CRAFTS_TAG = "total_crafts";
    private static final double POWER_EPSILON = 0.01;

    private final MolecularCenterBlockEntity host;
    private final AppEngInternalInventory patternInventory;
    private final MolecularCraftingBatcher batcher = new MolecularCraftingBatcher();
    private final Int2ObjectOpenHashMap<PatternConfig> configs = new Int2ObjectOpenHashMap<>();
    private int[] scheduledSlots = new int[0];
    private boolean scheduleDirty = true;
    private int scheduleCursor;
    private boolean suppressInventoryEvents;

    MolecularAutoCrafter(MolecularCenterBlockEntity host) {
        this.host = host;
        this.patternInventory = new AppEngInternalInventory(this,
                PATTERN_SLOTS, 1, new IAEItemFilter() {
                    @Override
                    public boolean allowInsert(
                            appeng.api.inventories.InternalInventory inventory,
                            int slot, ItemStack stack) {
                        return MolecularCenterLogic.isSupportedPattern(stack);
                    }
                });
    }

    /** The nine pattern slots owned exclusively by the automatic crafter. */
    public AppEngInternalInventory getPatternInventory() {
        return patternInventory;
    }

    public PatternView getView(int slot) {
        var decoded = decode(slot);
        var config = decoded == null ? null : matchingConfig(slot, decoded);
        if (decoded == null) {
            return PatternView.EMPTY;
        }
        if (config == null) {
            return new PatternView(false, 0, new long[MAX_INPUTS],
                    AutoCraftState.DISABLED, 0, 0, decoded.getInputs().length);
        }
        return new PatternView(config.enabled, config.outputLimit,
                Arrays.copyOf(config.protections, config.protections.length),
                config.state, config.lastBatch, config.totalCrafts,
                decoded.getInputs().length);
    }

    public void setEnabled(int slot, boolean enabled) {
        var decoded = decode(slot);
        if (decoded == null) {
            if (configs.remove(slot) != null) {
                scheduleDirty = true;
            }
            host.saveChanges();
            return;
        }
        var config = ensureConfig(slot, decoded);
        config.enabled = enabled;
        config.state = enabled ? AutoCraftState.READY : AutoCraftState.DISABLED;
        config.lastBatch = 0;
        host.saveChanges();
    }

    public void setProtection(int slot, int inputIndex, long amount) {
        var decoded = decode(slot);
        if (decoded == null || inputIndex < 0
                || inputIndex >= Math.min(MAX_INPUTS, decoded.getInputs().length)) {
            return;
        }
        var config = ensureConfig(slot, decoded);
        config.protections[inputIndex] = Math.max(0, amount);
        if (config.enabled) {
            config.state = AutoCraftState.READY;
        }
        host.saveChanges();
    }

    public void setOutputLimit(int slot, long amount) {
        var decoded = decode(slot);
        if (decoded == null) {
            return;
        }
        var config = ensureConfig(slot, decoded);
        config.outputLimit = Math.max(0, amount);
        if (config.enabled) {
            config.state = AutoCraftState.READY;
        }
        host.saveChanges();
    }

    @Override
    public void onChangeInventory(appeng.api.inventories.InternalInventory inventory, int slot) {
        if (!suppressInventoryEvents && inventory == patternInventory
                && isIndependentSlot(slot)) {
            if (configs.remove(slot) != null) {
                scheduleDirty = true;
            }
        }
    }

    @Override
    public void saveChanges() {
        if (!suppressInventoryEvents) {
            scheduleDirty = true;
            host.saveChanges();
        }
    }

    @Override
    public boolean isClientSide() {
        return host.isClientSide();
    }

    void clear() {
        configs.clear();
        scheduledSlots = new int[0];
        scheduleDirty = false;
        scheduleCursor = 0;
        withSuppressedInventoryEvents(patternInventory::clear);
    }

    void tick(long gameTime) {
        if (configs.isEmpty()) {
            return;
        }
        if (!host.canRunAutoCrafting()) {
            for (var config : configs.values()) {
                if (config.enabled) {
                    config.state = AutoCraftState.MACHINE_OFFLINE;
                    config.lastBatch = 0;
                }
            }
            return;
        }

        // Stable slot order makes two identical controllers deterministic. No fixed
        // craft-count or pattern-count throttle is applied: each slot may aggregate a
        // Long.MAX_VALUE batch in this tick.
        int[] slots = scheduledSlots();
        int start = slots.length == 0 ? 0 : Math.floorMod(scheduleCursor, slots.length);
        for (int offset = 0; offset < slots.length; offset++) {
            int slot = slots[(start + offset) % slots.length];
            var config = configs.get(slot);
            if (config == null || !config.enabled) {
                continue;
            }
            runPattern(slot, config, gameTime);
        }
        if (slots.length > 0) {
            scheduleCursor = (start + 1) % slots.length;
        }
    }

    private int[] scheduledSlots() {
        if (scheduleDirty) {
            scheduledSlots = configs.keySet().toIntArray();
            Arrays.sort(scheduledSlots);
            scheduleCursor = scheduledSlots.length == 0
                    ? 0 : Math.floorMod(scheduleCursor, scheduledSlots.length);
            scheduleDirty = false;
        }
        return scheduledSlots;
    }

    private void runPattern(int slot, PatternConfig config, long gameTime) {
        config.lastBatch = 0;
        var details = decode(slot);
        if (details == null || !config.definition.equals(details.getDefinition())
                || !(details instanceof IMolecularAssemblerSupportedPattern pattern)) {
            config.enabled = false;
            config.state = AutoCraftState.INVALID_PATTERN;
            host.saveChanges();
            return;
        }

        var grid = host.getMainNode().getGrid();
        Level level = host.getLevel();
        if (grid == null || level == null) {
            config.state = AutoCraftState.MACHINE_OFFLINE;
            return;
        }
        var outputs = details.getOutputs();
        if (outputs == null || outputs.length == 0
                || outputs[0] == null || outputs[0].what() == null
                || outputs[0].amount() <= 0) {
            config.enabled = false;
            config.state = AutoCraftState.INVALID_PATTERN;
            host.saveChanges();
            return;
        }

        MEStorage storage = grid.getStorageService().getInventory();
        IActionSource source = host.autoCraftActionSource();
        if (host.getBufferedAutoCraftAmount(outputs[0].what()) > 0) {
            config.state = AutoCraftState.OUTPUT_BLOCKED;
            return;
        }
        var outputAllowance = outputAllowance(details, config, storage, source);
        long maxCrafts = outputAllowance.maxCrafts();
        if (maxCrafts <= 0) {
            config.state = AutoCraftState.OUTPUT_LIMIT_REACHED;
            return;
        }

        var craftingInventory = new ProtectedNetworkCraftingInventory(
                storage, source, details.getInputs(), config.protections, level,
                host::queueAutoCraftRefund);
        var expectedOutputs = new KeyCounter();
        var expectedRemainders = new KeyCounter();
        KeyCounter[] firstInputs;
        try {
            firstInputs = CraftingCpuHelper.extractPatternInputs(
                    details, craftingInventory, level, expectedOutputs, expectedRemainders);
        } catch (RuntimeException exception) {
            config.state = AutoCraftState.WAITING_MATERIALS;
            MolecularManipulator.LOGGER.debug("Auto-crafter failed to extract one craft for slot {} at {}",
                    slot, host.getBlockPos(), exception);
            return;
        }
        if (firstInputs == null) {
            config.state = AutoCraftState.WAITING_MATERIALS;
            return;
        }

        var extraction = MolecularBatchCraftingExtractor.expandFromFirst(
                details, craftingInventory, grid.getEnergyService(), level,
                firstInputs, expectedOutputs, expectedRemainders,
                maxCrafts, true);
        KeyCounter[] inputs = extraction == null ? firstInputs : extraction.inputs();
        long craftCount = extraction == null ? 1 : extraction.craftCount();
        MolecularReusableBatchPlan reusablePlan = extraction == null
                ? null : extraction.reusablePlan();

        if (!fitsOutputAllowance(outputs[0].what(), inputs,
                expectedOutputs, expectedRemainders, outputAllowance.room())) {
            if (extraction != null) {
                extraction.rollbackAdditional(craftingInventory,
                        expectedOutputs, expectedRemainders);
                extraction = null;
                inputs = firstInputs;
                craftCount = 1;
                reusablePlan = null;
            }
            long safeCrafts = outputAllowance.room() == Long.MAX_VALUE
                    ? maxCrafts
                    : outputAllowance.room() / outputs[0].amount();
            if (safeCrafts > 1) {
                var safeExtraction = MolecularBatchCraftingExtractor.expandFromFirst(
                        details, craftingInventory, grid.getEnergyService(), level,
                        firstInputs, expectedOutputs, expectedRemainders,
                        safeCrafts, true);
                if (safeExtraction != null) {
                    if (fitsOutputAllowance(outputs[0].what(), safeExtraction.inputs(),
                            expectedOutputs, expectedRemainders, outputAllowance.room())) {
                        extraction = safeExtraction;
                        inputs = safeExtraction.inputs();
                        craftCount = safeExtraction.craftCount();
                        reusablePlan = safeExtraction.reusablePlan();
                    } else {
                        safeExtraction.rollbackAdditional(craftingInventory,
                                expectedOutputs, expectedRemainders);
                    }
                }
            }
            if (!fitsOutputAllowance(outputs[0].what(), inputs,
                    expectedOutputs, expectedRemainders, outputAllowance.room())) {
                CraftingCpuHelper.reinjectPatternInputs(craftingInventory, inputs);
                config.state = AutoCraftState.OUTPUT_LIMIT_REACHED;
                return;
            }
        }

        if (!prepareOutputs(details, inputs, firstInputs, craftCount, reusablePlan, level)) {
            if (extraction == null) {
                CraftingCpuHelper.reinjectPatternInputs(craftingInventory, firstInputs);
                config.state = AutoCraftState.INVALID_PATTERN;
                return;
            }
            extraction.rollbackAdditional(craftingInventory,
                    expectedOutputs, expectedRemainders);
            inputs = firstInputs;
            craftCount = 1;
            if (!fitsOutputAllowance(outputs[0].what(), firstInputs,
                    expectedOutputs, expectedRemainders, outputAllowance.room())) {
                CraftingCpuHelper.reinjectPatternInputs(craftingInventory, firstInputs);
                config.state = AutoCraftState.OUTPUT_LIMIT_REACHED;
                return;
            }
            if (!batcher.prepare(details, firstInputs, level, 1)) {
                CraftingCpuHelper.reinjectPatternInputs(craftingInventory, firstInputs);
                config.state = AutoCraftState.INVALID_PATTERN;
                return;
            }
        }

        var primaryOutputs = copyMap(batcher.getPrimaryOutputAmounts());
        var remainderOutputs = copyMap(batcher.getRemainderOutputAmounts());
        if (!countersMatch(expectedOutputs, expectedRemainders,
                primaryOutputs, remainderOutputs)
                || !host.canQueueAutoCraftOutputs(primaryOutputs, remainderOutputs)) {
            CraftingCpuHelper.reinjectPatternInputs(craftingInventory, inputs);
            config.state = AutoCraftState.OUTPUT_BLOCKED;
            return;
        }

        double power = CraftingCpuHelper.calculatePatternPower(inputs);
        if (!consumePower(grid.getEnergyService(), power)) {
            CraftingCpuHelper.reinjectPatternInputs(craftingInventory, inputs);
            config.state = AutoCraftState.WAITING_POWER;
            return;
        }

        host.queueAutoCraftOutputs(primaryOutputs, remainderOutputs, gameTime, craftCount);
        batcher.consumeInputs(inputs);
        try {
            CraftingEvent.fireAutoCraftingEvent(level, pattern,
                    batcher.getCraftedOutput().copy(), batcher.getCraftingGrid());
        } catch (RuntimeException exception) {
            MolecularManipulator.LOGGER.warn("Auto-crafting event failed for slot {} at {}",
                    slot, host.getBlockPos(), exception);
        }
        config.state = AutoCraftState.RUNNING;
        config.lastBatch = craftCount;
        config.totalCrafts = MolecularAutoCraftMath.saturatedAdd(
                config.totalCrafts, craftCount);
    }

    private boolean prepareOutputs(IPatternDetails details, KeyCounter[] inputs,
            KeyCounter[] firstInputs, long craftCount,
            @Nullable MolecularReusableBatchPlan reusablePlan, Level level) {
        if (reusablePlan != null) {
            var job = batcher.prepareReusable(details, inputs, level,
                    UUID.randomUUID(), reusablePlan);
            if (job == null) {
                return false;
            }
            var primary = job.projectedPrimaryOutputs();
            var remainders = job.projectedFinalRemainders();
            // Reuse the batcher's public result holders so the commit path and event
            // rendering stay identical to ordinary molecular-provider execution.
            return batcher.prepareReusableOutputsForAuto(job, primary, remainders);
        }
        return craftCount > 1
                ? batcher.prepareSelected(details, inputs, level,
                        Long.MAX_VALUE, firstInputs, craftCount)
                : batcher.prepare(details, inputs, level, 1);
    }

    private OutputAllowance outputAllowance(IPatternDetails details, PatternConfig config,
            MEStorage storage, IActionSource source) {
        long result = Long.MAX_VALUE;
        for (var output : details.getOutputs()) {
            if (output == null || output.what() == null || output.amount() <= 0) {
                return OutputAllowance.NONE;
            }
            result = Math.min(result, Long.MAX_VALUE / output.amount());
        }
        if (config.outputLimit == 0) {
            return new OutputAllowance(result, Long.MAX_VALUE);
        }

        var primary = details.getOutputs()[0];
        long stored = storage.extract(primary.what(), config.outputLimit,
                Actionable.SIMULATE, source);
        long buffered = host.getBufferedAutoCraftAmount(primary.what());
        long occupied = MolecularAutoCraftMath.saturatedAdd(stored, buffered);
        if (occupied >= config.outputLimit) {
            return OutputAllowance.NONE;
        }
        long room = config.outputLimit - occupied;
        long recycled = potentialPrimaryConsumption(details, primary.what());
        long netGrowth = MolecularAutoCraftMath.netOutputGrowth(
                primary.amount(), recycled);
        long limitCrafts = netGrowth == 0 ? Long.MAX_VALUE : room / netGrowth;
        return new OutputAllowance(Math.min(result, limitCrafts), room);
    }

    private long potentialPrimaryConsumption(IPatternDetails details, AEKey primary) {
        long result = 0;
        try {
            for (var input : details.getInputs()) {
                if (input == null || input.getPossibleInputs() == null) {
                    continue;
                }
                for (var candidate : input.getPossibleInputs()) {
                    if (candidate == null || candidate.what() == null
                            || !candidate.what().equals(primary)
                            || candidate.amount() <= 0) {
                        continue;
                    }
                    AEKey remainder = input.getRemainingKey(primary);
                    if (primary.equals(remainder)) {
                        break;
                    }
                    long amount = Math.multiplyExact(
                            input.getMultiplier(), candidate.amount());
                    result = MolecularAutoCraftMath.saturatedAdd(result, amount);
                    break;
                }
            }
        } catch (RuntimeException exception) {
            return 0;
        }
        return result;
    }

    private static boolean fitsOutputAllowance(AEKey primary,
            KeyCounter[] inputs, KeyCounter outputs, KeyCounter remainders,
            long room) {
        if (room == Long.MAX_VALUE) {
            return true;
        }
        long extracted = 0;
        for (var holder : inputs) {
            if (holder != null) {
                extracted = MolecularAutoCraftMath.saturatedAdd(
                        extracted, Math.max(0, holder.get(primary)));
            }
        }
        long returned = MolecularAutoCraftMath.saturatedAdd(
                Math.max(0, outputs.get(primary)),
                Math.max(0, remainders.get(primary)));
        long growth = returned <= extracted ? 0 : returned - extracted;
        return growth <= room;
    }

    private static boolean consumePower(IEnergyService energy, double amount) {
        if (energy == null || !Double.isFinite(amount) || amount <= 0) {
            return false;
        }
        try {
            double simulated = energy.extractAEPower(amount, Actionable.SIMULATE,
                    appeng.api.config.PowerMultiplier.CONFIG);
            if (simulated < amount - POWER_EPSILON) {
                return false;
            }
            double consumed = energy.extractAEPower(amount, Actionable.MODULATE,
                    appeng.api.config.PowerMultiplier.CONFIG);
            return consumed >= amount - POWER_EPSILON;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean countersMatch(KeyCounter expectedPrimary, KeyCounter expectedRemainders,
            Object2LongOpenHashMap<AEKey> actualPrimary,
            Object2LongOpenHashMap<AEKey> actualRemainders) {
        return counterMatches(expectedPrimary, actualPrimary)
                && counterMatches(expectedRemainders, actualRemainders);
    }

    private static boolean counterMatches(KeyCounter expected,
            Object2LongOpenHashMap<AEKey> actual) {
        int count = 0;
        for (var entry : expected) {
            if (entry.getLongValue() <= 0) {
                continue;
            }
            count++;
            if (actual.getLong(entry.getKey()) != entry.getLongValue()) {
                return false;
            }
        }
        return count == actual.size();
    }

    private static Object2LongOpenHashMap<AEKey> copyMap(
            Object2LongOpenHashMap<AEKey> source) {
        var result = new Object2LongOpenHashMap<AEKey>();
        result.putAll(source);
        return result;
    }

    @Nullable
    private IPatternDetails decode(int slot) {
        if (!isIndependentSlot(slot)) {
            return null;
        }
        ItemStack stack = patternInventory.getStackInSlot(slot);
        Level level = host.getLevel();
        if (stack.isEmpty() || level == null
                || !MolecularCenterLogic.isSupportedPattern(stack)) {
            return null;
        }
        try {
            return PatternDetailsHelper.decodePattern(stack, level);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private PatternConfig ensureConfig(int slot, IPatternDetails details) {
        var config = matchingConfig(slot, details);
        if (config != null) {
            return config;
        }
        config = new PatternConfig(details.getDefinition());
        configs.put(slot, config);
        scheduleDirty = true;
        return config;
    }

    @Nullable
    private PatternConfig matchingConfig(int slot, IPatternDetails details) {
        var config = configs.get(slot);
        if (config != null && config.definition.equals(details.getDefinition())) {
            return config;
        }
        if (config != null) {
            configs.remove(slot);
            scheduleDirty = true;
        }
        return null;
    }

    void save(CompoundTag parent) {
        var root = new CompoundTag();
        root.putInt(VERSION_TAG, SCHEMA_VERSION);
        patternInventory.writeToNBT(root, PATTERN_INVENTORY_TAG);
        var list = new ListTag();
        for (var entry : configs.int2ObjectEntrySet()) {
            var config = entry.getValue();
            var tag = new CompoundTag();
            tag.putInt(SLOT_TAG, entry.getIntKey());
            tag.put(DEFINITION_TAG, config.definition.toTag());
            tag.putBoolean(ENABLED_TAG, config.enabled);
            tag.putLong(OUTPUT_LIMIT_TAG, config.outputLimit);
            tag.putLongArray(PROTECTIONS_TAG, config.protections);
            tag.putLong(TOTAL_CRAFTS_TAG, config.totalCrafts);
            list.add(tag);
        }
        root.put(CONFIGS_TAG, list);
        parent.put(ROOT_TAG, root);
    }

    void load(CompoundTag parent) {
        clear();
        scheduleDirty = true;
        if (!parent.contains(ROOT_TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        var root = parent.getCompound(ROOT_TAG);
        int version = root.contains(VERSION_TAG, Tag.TAG_INT)
                ? root.getInt(VERSION_TAG) : 0;
        if (!shouldLoadConfig(version)) {
            MolecularManipulator.LOGGER.warn(
                    "Discarding incompatible auto-crafter schema version {} at {}; "
                            + "independent pattern slots remain empty and disabled",
                    version, host.getBlockPos());
            return;
        }
        withSuppressedInventoryEvents(() -> {
            patternInventory.readFromNBT(root, PATTERN_INVENTORY_TAG);
            sanitizePatternInventory();
        });
        for (var element : root.getList(CONFIGS_TAG, Tag.TAG_COMPOUND)) {
            try {
                var tag = (CompoundTag) element;
                int slot = tag.getInt(SLOT_TAG);
                AEItemKey definition = AEItemKey.fromTag(tag.getCompound(DEFINITION_TAG));
                if (!isIndependentSlot(slot) || definition == null) {
                    continue;
                }
                AEItemKey storedDefinition = AEItemKey.of(
                        patternInventory.getStackInSlot(slot));
                if (!definition.equals(storedDefinition)) {
                    continue;
                }
                var config = new PatternConfig(definition);
                config.enabled = tag.getBoolean(ENABLED_TAG);
                config.outputLimit = Math.max(0, tag.getLong(OUTPUT_LIMIT_TAG));
                long[] storedProtections = tag.getLongArray(PROTECTIONS_TAG);
                for (int index = 0; index < Math.min(MAX_INPUTS, storedProtections.length); index++) {
                    config.protections[index] = Math.max(0, storedProtections[index]);
                }
                config.totalCrafts = Math.max(0, tag.getLong(TOTAL_CRAFTS_TAG));
                config.state = config.enabled ? AutoCraftState.READY : AutoCraftState.DISABLED;
                configs.put(slot, config);
            } catch (RuntimeException exception) {
                MolecularManipulator.LOGGER.warn("Skipping invalid auto-crafter pattern configuration at {}",
                        host.getBlockPos(), exception);
            }
        }
    }

    static boolean isIndependentSlot(int slot) {
        return MolecularAutoCraftSchema.isIndependentSlot(slot);
    }

    static boolean shouldLoadConfig(int version) {
        return MolecularAutoCraftSchema.shouldLoadConfig(version);
    }

    private void sanitizePatternInventory() {
        for (int slot = 0; slot < PATTERN_SLOTS; slot++) {
            ItemStack stack = patternInventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!MolecularCenterLogic.isSupportedPattern(stack)) {
                patternInventory.setItemDirect(slot, ItemStack.EMPTY);
            } else if (stack.getCount() != 1) {
                patternInventory.setItemDirect(slot, stack.copyWithCount(1));
            }
        }
    }

    private void withSuppressedInventoryEvents(Runnable action) {
        boolean previous = suppressInventoryEvents;
        suppressInventoryEvents = true;
        try {
            action.run();
        } finally {
            suppressInventoryEvents = previous;
        }
    }

    public enum AutoCraftState {
        EMPTY,
        DISABLED,
        READY,
        RUNNING,
        WAITING_MATERIALS,
        WAITING_POWER,
        OUTPUT_LIMIT_REACHED,
        OUTPUT_BLOCKED,
        MACHINE_OFFLINE,
        INVALID_PATTERN
    }

    public record PatternView(boolean enabled, long outputLimit, long[] protections,
            AutoCraftState state, long lastBatch, long totalCrafts, int inputCount) {
        private static final PatternView EMPTY = new PatternView(false, 0,
                new long[MAX_INPUTS], AutoCraftState.EMPTY, 0, 0, 0);

        public PatternView {
            protections = protections == null
                    ? new long[MAX_INPUTS]
                    : Arrays.copyOf(protections, MAX_INPUTS);
        }

        @Override
        public long[] protections() {
            return Arrays.copyOf(protections, protections.length);
        }
    }

    private record OutputAllowance(long maxCrafts, long room) {
        private static final OutputAllowance NONE = new OutputAllowance(0, 0);
    }

    private static final class PatternConfig {
        private final AEItemKey definition;
        private final long[] protections = new long[MAX_INPUTS];
        private boolean enabled;
        private long outputLimit;
        private AutoCraftState state = AutoCraftState.DISABLED;
        private long lastBatch;
        private long totalCrafts;

        private PatternConfig(AEItemKey definition) {
            this.definition = definition;
        }
    }

    @FunctionalInterface
    private interface RefundSink {
        void accept(AEKey key, long amount);
    }

    /**
     * Presents the ME inventory as AE2's unbounded crafting inventory while
     * enforcing the configured per-input reserve. Failed rollback insertion is
     * transferred to the controller's persistent refund escrow.
     */
    private static final class ProtectedNetworkCraftingInventory implements ICraftingInventory {
        private final MEStorage storage;
        private final IActionSource source;
        private final IPatternDetails.IInput[] inputs;
        private final long[] protections;
        private final Level level;
        private final RefundSink refundSink;
        private KeyCounter availableKeys;

        private ProtectedNetworkCraftingInventory(MEStorage storage, IActionSource source,
                IPatternDetails.IInput[] inputs, long[] protections, Level level,
                RefundSink refundSink) {
            this.storage = storage;
            this.source = source;
            this.inputs = inputs;
            this.protections = protections;
            this.level = level;
            this.refundSink = refundSink;
        }

        @Override
        public void insert(AEKey what, long amount, Actionable mode) {
            if (what == null || amount <= 0 || mode != Actionable.MODULATE) {
                return;
            }
            long inserted;
            try {
                inserted = storage.insert(what, amount, mode, source);
            } catch (RuntimeException exception) {
                refundSink.accept(what, amount);
                return;
            }
            if (inserted < amount) {
                refundSink.accept(what, amount - inserted);
            }
        }

        @Override
        public long extract(AEKey what, long amount, Actionable mode) {
            if (what == null || amount <= 0) {
                return 0;
            }
            try {
                long available = storage.extract(what, Long.MAX_VALUE,
                        Actionable.SIMULATE, source);
                long reserve = protectedAmount(what);
                long extractable = available <= reserve ? 0 : available - reserve;
                return storage.extract(what, Math.min(amount, extractable), mode, source);
            } catch (RuntimeException exception) {
                return 0;
            }
        }

        @Override
        public Iterable<AEKey> findFuzzyTemplates(AEKey input) {
            if (availableKeys == null) {
                availableKeys = new KeyCounter();
                try {
                    storage.getAvailableStacks(availableKeys);
                } catch (RuntimeException exception) {
                    return List.of();
                }
            }
            var result = new ArrayList<AEKey>();
            for (var entry : availableKeys.findFuzzy(input, FuzzyMode.IGNORE_ALL)) {
                if (entry.getLongValue() > protectedAmount(entry.getKey())) {
                    result.add(entry.getKey());
                }
            }
            return result;
        }

        private long protectedAmount(AEKey key) {
            long result = 0;
            for (int index = 0; index < Math.min(inputs.length, protections.length); index++) {
                long protection = protections[index];
                if (protection <= result) {
                    continue;
                }
                try {
                    if (inputs[index].isValid(key, level)) {
                        result = protection;
                    }
                } catch (RuntimeException ignored) {
                    // Invalid/contextual candidates are never selected by AE2 either.
                }
            }
            return result;
        }
    }
}
