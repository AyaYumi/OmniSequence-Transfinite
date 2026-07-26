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
import com.atir.molecularmanipulator.integration.ae2.AEKeyTransferScheduler;
import com.atir.molecularmanipulator.integration.extendedae.MolecularMatrixCluster;
import com.atir.molecularmanipulator.registry.ModContent;
import com.glodblock.github.extendedae.common.me.matrix.ClusterAssemblerMatrix;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixFunction;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;

public final class AssemblerMatrixMolecularCoreBlockEntity extends TileAssemblerMatrixFunction implements IGridTickable {
    public static final long VIRTUAL_PARALLEL_LIMIT = Integer.MAX_VALUE;
    private static final int MAX_BUFFERED_TYPES = 256;
    private static final String OUTPUT_BUFFER_TAG = "output_buffer";
    private static final String OUTPUT_READY_TICK_TAG = "output_ready_tick";

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
        if (assembling) {
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

    private boolean canQueueOutputs(Object2LongOpenHashMap<AEKey> outputAmounts) {
        int outputTypes = bufferedOutputs.size();
        try {
            for (var entry : outputAmounts.object2LongEntrySet()) {
                if (!bufferedOutputs.containsKey(entry.getKey())) {
                    outputTypes++;
                    if (outputTypes > MAX_BUFFERED_TYPES) {
                        return false;
                    }
                }
                Math.addExact(bufferedOutputs.getLong(entry.getKey()), entry.getLongValue());
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
        return new TickingRequest(1, 20, bufferedOutputs.isEmpty(), true);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        var result = flushBufferedOutputs();
        if (bufferedOutputs.isEmpty()) {
            return TickRateModulation.SLEEP;
        }
        return result.transferred() > 0 ? TickRateModulation.FASTER : TickRateModulation.SLOWER;
    }

    @Override
    public void onReady() {
        super.onReady();
        if (!bufferedOutputs.isEmpty()) {
            wakeForBufferedOutputs();
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        var outputList = new ListTag();
        for (var entry : bufferedOutputs.object2LongEntrySet()) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                outputList.add(GenericStack.writeTag(
                        new GenericStack(entry.getKey(), entry.getLongValue())));
            }
        }
        tag.put(OUTPUT_BUFFER_TAG, outputList);
        tag.putLong(OUTPUT_READY_TICK_TAG, outputReadyTick);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        bufferedOutputs.clear();
        var outputList = tag.getList(OUTPUT_BUFFER_TAG, Tag.TAG_COMPOUND);
        for (var entryTag : outputList) {
            var stack = GenericStack.readTag( (CompoundTag) entryTag);
            if (stack != null && stack.amount() > 0) {
                bufferedOutputs.addTo(stack.what(), stack.amount());
            }
        }
        outputReadyTick = tag.contains(OUTPUT_READY_TICK_TAG, Tag.TAG_LONG)
                ? tag.getLong(OUTPUT_READY_TICK_TAG)
                : Long.MIN_VALUE;
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
