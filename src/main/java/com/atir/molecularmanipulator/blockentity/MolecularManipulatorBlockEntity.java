package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AECableType;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.crafting.CraftingEvent;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.me.helpers.MachineSource;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.SettingsFrom;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.crafting.MolecularBatchCancellationData;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchContext;
import com.atir.molecularmanipulator.integration.ae2.AEKeyTransferScheduler;
import com.atir.molecularmanipulator.menu.MolecularManipulatorMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class MolecularManipulatorBlockEntity extends PatternProviderBlockEntity {
    public static final int PATTERN_SLOTS = 360;
    public static final int PATTERNS_PER_PAGE = 36;
    public static final long VIRTUAL_PARALLEL_LIMIT = Integer.MAX_VALUE;
    private static final int MAX_BUFFERED_TYPES = 256;
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
    private long craftingEventTick = Long.MIN_VALUE;
    private long bufferDirtyTick = Long.MIN_VALUE;
    private long outputReadyTick = Long.MIN_VALUE;
    private MolecularReusableBatchJob activeReusableBatch;
    private CompoundTag quarantinedReusableBatchTag;

    public MolecularManipulatorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModContent.MOLECULAR_MANIPULATOR_BLOCK_ENTITY.get(), pos, blockState);
        getMainNode().setIdlePowerUsage(8.0);
    }

    @Override
    protected PatternProviderLogic createLogic() {
        return new MolecularManipulatorLogic(getMainNode(), this);
    }

    public int getPatternRevision() {
        return ((MolecularManipulatorLogic) getLogic()).getPatternRevision();
    }

    boolean hasActiveReusableBatch() {
        return activeReusableBatch != null
                || quarantinedReusableBatchTag != null;
    }

    public boolean hasRemovalRecovery() {
        return hasActiveReusableBatch() || !bufferedOutputs.isEmpty();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos,
            List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        if (hasRemovalRecovery()) {
            drops.add(createRemovalRecovery(level.registryAccess()));
        }
    }

    @Override
    public void clearContent() {
        super.clearContent();
        bufferedOutputs.clear();
        activeReusableBatch = null;
        quarantinedReusableBatchTag = null;
        outputReadyTick = Long.MIN_VALUE;
    }

    private ItemStack createRemovalRecovery(
            HolderLookup.Provider registries) {
        var recovery = new ItemStack(getBlockState().getBlock());
        var settings = DataComponentMap.builder();
        exportSettings(SettingsFrom.DISMANTLE_ITEM, settings, null);
        recovery.applyComponents(settings.build());

        var payload = new CompoundTag();
        var outputList = new ListTag();
        for (var entry : bufferedOutputs.object2LongEntrySet()) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                outputList.add(GenericStack.writeTag(registries,
                        new GenericStack(entry.getKey(),
                                entry.getLongValue())));
            }
        }
        payload.put(OUTPUT_BUFFER_TAG, outputList);
        writeReusableBatchRecovery(payload, registries);
        BlockItem.setBlockEntityData(recovery, getType(), payload);
        return recovery;
    }

    private void writeReusableBatchRecovery(CompoundTag tag,
            HolderLookup.Provider registries) {
        if (activeReusableBatch != null) {
            tag.put(ACTIVE_REUSABLE_BATCH_TAG,
                    activeReusableBatch.writeToTag(registries));
        } else if (quarantinedReusableBatchTag != null) {
            tag.put(ACTIVE_REUSABLE_BATCH_TAG,
                    quarantinedReusableBatchTag.copy());
        }
    }

    boolean acceptPattern(IPatternDetails patternDetails, KeyCounter[] inputs) {
        if (assembling || hasActiveReusableBatch()
                || !(patternDetails instanceof IMolecularAssemblerSupportedPattern pattern)) {
            return false;
        }

        var level = getLevel();
        var grid = getMainNode().getGrid();
        if (level == null || grid == null || !getLogic().getReturnInv().isEmpty()) {
            return false;
        }

        assembling = true;
        try {
            var batchContext = MolecularBatchDispatchContext.current(
                    patternDetails, inputs);
            if (batchContext != null
                    && batchContext.reusablePlan() != null) {
                return acceptReusablePattern(patternDetails, pattern, inputs,
                        batchContext);
            }
            boolean prepared = batchContext != null
                    ? craftingBatcher.prepareSelected(
                            patternDetails, inputs, level,
                            VIRTUAL_PARALLEL_LIMIT,
                            batchContext.firstInputs(),
                            batchContext.craftCount())
                    : craftingBatcher.prepare(
                            patternDetails, inputs, level,
                            VIRTUAL_PARALLEL_LIMIT);
            if (!prepared) {
                return false;
            }
            var outputAmounts = craftingBatcher.getOutputAmounts();
            if (!canQueueOutputs(outputAmounts)) {
                return false;
            }

            for (var entry : outputAmounts.object2LongEntrySet()) {
                bufferedOutputs.put(entry.getKey(),
                        bufferedOutputs.getLong(entry.getKey()) + entry.getLongValue());
            }
            // The output buffer now owns the complete batch. No post-commit
            // hook may escape and make AE2 schedule the same work again.
            try {
                craftingBatcher.consumeInputs(inputs);
            } catch (RuntimeException exception) {
                logPostCommitFailure("input holder clear", exception);
            }
            try {
                fireCraftingEventOncePerTick(level, patternDetails, pattern);
            } catch (RuntimeException exception) {
                logPostCommitFailure("crafting event", exception);
            }
            outputReadyTick = Math.max(outputReadyTick, level.getGameTime() + 1);
            try {
                markOutputBufferChanged(level.getGameTime());
            } catch (RuntimeException exception) {
                logPostCommitFailure("dirty-state update", exception);
            }
            return true;
        } finally {
            assembling = false;
        }
    }

    private boolean acceptReusablePattern(IPatternDetails patternDetails,
            IMolecularAssemblerSupportedPattern pattern, KeyCounter[] inputs,
            MolecularBatchDispatchContext.Context context) {
        var level = getLevel();
        if (level == null || activeReusableBatch != null) {
            return false;
        }

        var job = craftingBatcher.prepareReusable(patternDetails, inputs, level,
                context.craftingId(), context.reusablePlan());
        if (job == null
                || !canQueueOutputs(job.projectedPrimaryOutputs(),
                        job.projectedFinalRemainders())
                || !canQueueOutputs(job.cancellationRefunds())) {
            return false;
        }

        // Commit point: once the holders are cleared, this persisted job owns
        // every extracted input until completion or cancellation refund.
        activeReusableBatch = job;
        try {
            craftingBatcher.consumeInputs(inputs);
        } catch (RuntimeException exception) {
            logPostCommitFailure("reusable input holder clear", exception);
        }
        try {
            saveChanges();
        } catch (RuntimeException exception) {
            logPostCommitFailure("reusable dirty-state update", exception);
        }
        try {
            fireCraftingEventOncePerTick(level, patternDetails, pattern);
        } catch (RuntimeException exception) {
            logPostCommitFailure("reusable crafting event", exception);
        }
        return true;
    }

    private void logPostCommitFailure(String stage,
            RuntimeException exception) {
        MolecularManipulator.LOGGER.warn(
                "Molecular manipulator {} failed after batch ownership committed at {}",
                stage, getBlockPos(), exception);
    }

    private void fireCraftingEventOncePerTick(Level level, IPatternDetails patternDetails,
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

    private void markOutputBufferChanged(long gameTime) {
        if (bufferDirtyTick != gameTime) {
            bufferDirtyTick = gameTime;
            saveChanges();
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
                        "Invalid molecular-manipulator reusable batch at {}; preserving its NBT and locking the machine",
                        getBlockPos());
            }
        }
    }

    public void serverTick() {
        var level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        processReusableBatch(level);
        flushBufferedOutputs(level);
    }

    private void processReusableBatch(Level level) {
        var job = activeReusableBatch;
        if (job == null || assembling) {
            return;
        }

        if (MolecularBatchCancellationData.isCanceled(
                level, job.craftingId())) {
            var refunds = job.cancellationRefunds();
            if (!canQueueOutputs(refunds)) {
                return;
            }
            addOutputs(bufferedOutputs, refunds);
            activeReusableBatch = null;
            outputReadyTick = Math.max(
                    outputReadyTick, level.getGameTime() + 1);
            saveChanges();
            return;
        }

        if (!getMainNode().isActive()) {
            return;
        }

        // Reusable batches are represented as long-count aggregates. Advancing
        // the complete remainder is O(output types + tool groups), not O(crafts),
        // so there is no reason to impose an artificial per-tick craft window.
        long step = job.nextStep(Long.MAX_VALUE);
        if (step <= 0) {
            return;
        }
        var primary = job.primaryOutputsFor(step);
        boolean completes =
                step == job.totalCrafts() - job.completedCrafts();
        var remainders = completes
                ? job.projectedFinalRemainders()
                : new Object2LongOpenHashMap<AEKey>();
        if (!canQueueOutputs(primary, remainders)) {
            return;
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

    private void flushBufferedOutputs(Level level) {
        if (assembling || bufferedOutputs.isEmpty()
                || level.getGameTime() < outputReadyTick) {
            return;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return;
        }

        assembling = true;
        try {
            var storage = grid.getStorageService().getInventory();
            var result = outputTransferScheduler.flush(bufferedOutputs,
                    AEKeyTransferScheduler.defaultBudget(),
                    (key, amount) -> storage.insert(key, amount, Actionable.MODULATE, actionSource));
            outputReadyTick = bufferedOutputs.isEmpty()
                    ? Long.MIN_VALUE
                    : level.getGameTime() + (result.transferred() > 0 ? 1 : 5);
            if (result.changed()) {
                saveChanges();
            }
        } finally {
            assembling = false;
        }
    }

    public static <T extends MolecularManipulatorBlockEntity> BlockEntityTicker<T> ticker() {
        return (level, pos, state, blockEntity) -> blockEntity.serverTick();
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.SMART;
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(MolecularManipulatorMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(MolecularManipulatorMenu.TYPE, player, subMenu.getLocator());
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(ModContent.MOLECULAR_MANIPULATOR_ITEM.get());
    }

    boolean schedulePatternRebuild(Runnable rebuild) {
        Level level = getLevel();
        if (level != null && !level.isClientSide() && level.getServer() != null) {
            level.getServer().execute(rebuild);
            return true;
        }
        return false;
    }
}
