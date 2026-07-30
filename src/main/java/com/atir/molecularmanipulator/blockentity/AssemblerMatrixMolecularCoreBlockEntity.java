package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.CraftingEvent;
import appeng.me.helpers.MachineSource;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.crafting.MolecularBatchCancellationData;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchContext;
import com.atir.molecularmanipulator.integration.ae2.AEKeyTransferScheduler;
import com.atir.molecularmanipulator.integration.extendedae.MolecularMatrixCluster;
import com.atir.molecularmanipulator.registry.ModContent;
import com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixFunction;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

public final class AssemblerMatrixMolecularCoreBlockEntity extends TileAssemblerMatrixFunction implements IGridTickable {
    public static final long VIRTUAL_PARALLEL_LIMIT = Integer.MAX_VALUE;
    private static final int MAX_BUFFERED_TYPES = 256;
    private static final long MAX_REUSABLE_CRAFTS_PER_TICK = 65_536;
    private static final String OUTPUT_BUFFER_TAG = "output_buffer";
    private static final String OUTPUT_READY_TICK_TAG = "output_ready_tick";
    private static final String ACTIVE_REUSABLE_BATCH_TAG =
            "active_reusable_batch";

    private final MachineSource actionSource = new MachineSource(this);
    private final MolecularCraftingBatcher craftingBatcher = new MolecularCraftingBatcher();
    private final Object2LongOpenHashMap<AEKey> bufferedOutputs = new Object2LongOpenHashMap<>();
    private final AEKeyTransferScheduler outputTransferScheduler = new AEKeyTransferScheduler();
    private final ReferenceOpenHashSet<IPatternDetails> craftingEventsThisTick = new ReferenceOpenHashSet<>();
    private boolean assembling;
    private boolean availableThisTick;
    private long availabilityTick = Long.MIN_VALUE;
    private long craftingEventTick = Long.MIN_VALUE;
    private long bufferDirtyTick = Long.MIN_VALUE;
    private long outputReadyTick = Long.MIN_VALUE;
    private MolecularReusableBatchJob activeReusableBatch;
    private CompoundTag quarantinedReusableBatchTag;

    public AssemblerMatrixMolecularCoreBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModContent.ASSEMBLER_MATRIX_MOLECULAR_CORE_BLOCK_ENTITY.get(), pos, blockState);
        getMainNode().setIdlePowerUsage(16.0);
        getMainNode().addService(IGridTickable.class, this);
    }

    @Override
    public void add(ClusterAssemblerMatrix cluster) {
        ((MolecularMatrixCluster) cluster).molecularmanipulator$registerCore(this);
    }

    public boolean canAcceptCrafting() {
        if (assembling || activeReusableBatch != null
                || quarantinedReusableBatchTag != null) {
            return false;
        }
        var level = getLevel();
        if (level == null) {
            return false;
        }
        long currentTick = level.getGameTime();
        if (availabilityTick != currentTick) {
            availabilityTick = currentTick;
            availableThisTick = isFormed() && getMainNode().isActive();
        }
        return availableThisTick;
    }

    public boolean acceptCrafting(IPatternDetails patternDetails, KeyCounter[] inputs) {
        if (!canAcceptCrafting() || !(patternDetails instanceof IMolecularAssemblerSupportedPattern pattern)) {
            return false;
        }

        var level = getLevel();
        var grid = getMainNode().getGrid();
        if (level == null || grid == null) {
            return false;
        }

        assembling = true;
        try {
            var reusableContext = MolecularBatchDispatchContext.current(
                    patternDetails, inputs);
            if (reusableContext != null) {
                return acceptReusableCrafting(patternDetails, pattern, inputs,
                        reusableContext);
            }
            if (!craftingBatcher.prepare(patternDetails, inputs, level, VIRTUAL_PARALLEL_LIMIT)) {
                return false;
            }
            var outputAmounts = craftingBatcher.getOutputAmounts();
            if (!canQueueOutputs(outputAmounts)) {
                return false;
            }

            boolean wakeDevice = bufferedOutputs.isEmpty();
            for (var entry : outputAmounts.object2LongEntrySet()) {
                bufferedOutputs.put(entry.getKey(),
                        bufferedOutputs.getLong(entry.getKey()) + entry.getLongValue());
            }

            craftingBatcher.consumeInputs(inputs);
            fireCraftingEventOncePerTick(level, patternDetails, pattern);
            outputReadyTick = Math.max(outputReadyTick, level.getGameTime() + 1);
            markOutputBufferChanged(level.getGameTime());
            if (wakeDevice) {
                wakeForBufferedOutputs();
            }
            return true;
        } finally {
            assembling = false;
        }
    }

    private boolean acceptReusableCrafting(IPatternDetails patternDetails,
            IMolecularAssemblerSupportedPattern pattern, KeyCounter[] inputs,
            MolecularBatchDispatchContext.Context context) {
        var level = getLevel();
        if (level == null || activeReusableBatch != null) {
            return false;
        }

        var job = craftingBatcher.prepareReusable(patternDetails, inputs, level,
                context.craftingId(), context.plan());
        if (job == null
                || !canQueueOutputs(job.projectedPrimaryOutputs(),
                        job.projectedFinalRemainders())
                || !canQueueOutputs(job.cancellationRefunds())) {
            return false;
        }

        // Commit point: after this assignment and holder clear, the machine owns
        // every input. No later event hook may turn acceptance back into rejection.
        activeReusableBatch = job;
        craftingBatcher.consumeInputs(inputs);
        saveChanges();
        try {
            fireCraftingEventOncePerTick(level, patternDetails, pattern);
        } catch (RuntimeException exception) {
            MolecularManipulator.LOGGER.warn(
                    "Crafting event failed after assembler-matrix reusable batch commit at {}",
                    getBlockPos(), exception);
        }
        wakeForBufferedOutputs();
        return true;
    }

    @SafeVarargs
    private final boolean canQueueOutputs(
            Object2LongOpenHashMap<AEKey>... outputGroups) {
        int outputTypes = bufferedOutputs.size();
        var totals = new Object2LongOpenHashMap<AEKey>();
        totals.putAll(bufferedOutputs);
        try {
            for (var outputAmounts : outputGroups) {
                for (var entry : outputAmounts.object2LongEntrySet()) {
                    if (!totals.containsKey(entry.getKey())) {
                        outputTypes++;
                        if (outputTypes > MAX_BUFFERED_TYPES) {
                            return false;
                        }
                    }
                    totals.put(entry.getKey(), Math.addExact(
                            totals.getLong(entry.getKey()),
                            entry.getLongValue()));
                }
            }
        } catch (ArithmeticException exception) {
            return false;
        }
        return true;
    }

    private void fireCraftingEventOncePerTick(net.minecraft.world.level.Level level, IPatternDetails patternDetails,
            IMolecularAssemblerSupportedPattern pattern) {
        long gameTime = level.getGameTime();
        if (craftingEventTick != gameTime) {
            craftingEventTick = gameTime;
            craftingEventsThisTick.clear();
        }
        if (craftingEventsThisTick.add(patternDetails)) {
            CraftingEvent.fireAutoCraftingEvent(level, pattern, craftingBatcher.getCraftedOutput().copy(),
                    craftingBatcher.getCraftingGrid());
        }
    }

    private void markOutputBufferChanged(long gameTime) {
        if (bufferDirtyTick != gameTime) {
            bufferDirtyTick = gameTime;
            saveChanges();
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20,
                bufferedOutputs.isEmpty() && activeReusableBatch == null);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        boolean progressed = processReusableBatch();
        var result = flushBufferedOutputs();
        if (bufferedOutputs.isEmpty() && activeReusableBatch == null) {
            return TickRateModulation.SLEEP;
        }
        return progressed || result.transferred() > 0
                ? TickRateModulation.FASTER
                : TickRateModulation.SLOWER;
    }

    @Override
    public void onReady() {
        super.onReady();
        if (!bufferedOutputs.isEmpty() || activeReusableBatch != null) {
            wakeForBufferedOutputs();
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        var outputList = new ListTag();
        for (var entry : bufferedOutputs.object2LongEntrySet()) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                outputList.add(GenericStack.writeTag(registries,
                        new GenericStack(entry.getKey(), entry.getLongValue())));
            }
        }
        tag.put(OUTPUT_BUFFER_TAG, outputList);
        tag.putLong(OUTPUT_READY_TICK_TAG, outputReadyTick);
        if (activeReusableBatch != null) {
            tag.put(ACTIVE_REUSABLE_BATCH_TAG,
                    activeReusableBatch.writeToTag(registries));
        } else if (quarantinedReusableBatchTag != null) {
            tag.put(ACTIVE_REUSABLE_BATCH_TAG,
                    quarantinedReusableBatchTag.copy());
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        bufferedOutputs.clear();
        var outputList = tag.getList(OUTPUT_BUFFER_TAG, Tag.TAG_COMPOUND);
        for (var entryTag : outputList) {
            var stack = GenericStack.readTag(registries, (CompoundTag) entryTag);
            if (stack != null && stack.amount() > 0) {
                bufferedOutputs.addTo(stack.what(), stack.amount());
            }
        }
        outputReadyTick = tag.contains(OUTPUT_READY_TICK_TAG, Tag.TAG_LONG)
                ? tag.getLong(OUTPUT_READY_TICK_TAG)
                : Long.MIN_VALUE;
        activeReusableBatch = null;
        quarantinedReusableBatchTag = null;
        if (tag.contains(ACTIVE_REUSABLE_BATCH_TAG, Tag.TAG_COMPOUND)) {
            var jobTag = tag.getCompound(ACTIVE_REUSABLE_BATCH_TAG);
            activeReusableBatch = MolecularReusableBatchJob.readFromTag(
                    jobTag, registries);
            if (activeReusableBatch == null) {
                quarantinedReusableBatchTag = jobTag.copy();
                MolecularManipulator.LOGGER.error(
                        "Invalid assembler-matrix reusable batch at {}; preserving its NBT and locking the core",
                        getBlockPos());
            }
        }
    }

    private boolean processReusableBatch() {
        var level = getLevel();
        var job = activeReusableBatch;
        if (level == null || job == null || assembling) {
            return false;
        }

        if (MolecularBatchCancellationData.isCanceled(
                level, job.craftingId())) {
            var refunds = job.cancellationRefunds();
            if (!canQueueOutputs(refunds)) {
                return false;
            }
            addOutputs(bufferedOutputs, refunds);
            activeReusableBatch = null;
            outputReadyTick = Math.max(
                    outputReadyTick, level.getGameTime() + 1);
            saveChanges();
            return true;
        }

        if (!isFormed() || !getMainNode().isActive()) {
            return false;
        }

        long step = job.nextStep(MAX_REUSABLE_CRAFTS_PER_TICK);
        if (step <= 0) {
            return false;
        }
        var primary = job.primaryOutputsFor(step);
        boolean completes =
                step == job.totalCrafts() - job.completedCrafts();
        var remainders = completes
                ? job.projectedFinalRemainders()
                : new Object2LongOpenHashMap<AEKey>();
        if (!canQueueOutputs(primary, remainders)) {
            return false;
        }

        job.advance(step);
        addOutputs(bufferedOutputs, primary);
        if (job.isComplete()) {
            addOutputs(bufferedOutputs, job.completedRemainders());
            activeReusableBatch = null;
        }
        outputReadyTick = Math.max(
                outputReadyTick, level.getGameTime() + 1);
        saveChanges();
        return true;
    }

    private static void addOutputs(
            Object2LongOpenHashMap<AEKey> destination,
            Object2LongOpenHashMap<AEKey> outputs) {
        for (var entry : outputs.object2LongEntrySet()) {
            destination.put(entry.getKey(), Math.addExact(
                    destination.getLong(entry.getKey()),
                    entry.getLongValue()));
        }
    }

    private AEKeyTransferScheduler.FlushResult flushBufferedOutputs() {
        var level = getLevel();
        if (level == null || assembling || bufferedOutputs.isEmpty() || level.getGameTime() < outputReadyTick) {
            return AEKeyTransferScheduler.FlushResult.EMPTY;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return AEKeyTransferScheduler.FlushResult.EMPTY;
        }

        assembling = true;
        try {
            var storage = grid.getStorageService().getInventory();
            var result = outputTransferScheduler.flush(bufferedOutputs,
                    AEKeyTransferScheduler.defaultBudget(),
                    (key, amount) -> storage.insert(key, amount, Actionable.MODULATE, actionSource));
            outputReadyTick = bufferedOutputs.isEmpty() ? Long.MIN_VALUE : level.getGameTime() + 1;
            if (result.changed()) {
                saveChanges();
            }
            return result;
        } finally {
            assembling = false;
        }
    }

    private void wakeForBufferedOutputs() {
        getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
    }

}
