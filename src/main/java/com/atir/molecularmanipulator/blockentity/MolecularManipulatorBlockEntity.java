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
import com.atir.molecularmanipulator.integration.ae2.AEKeyTransferScheduler;
import com.atir.molecularmanipulator.menu.MolecularManipulatorMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

public final class MolecularManipulatorBlockEntity extends PatternProviderBlockEntity {
    public static final int PATTERN_SLOTS = 360;
    public static final int PATTERNS_PER_PAGE = 36;
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
    private long craftingEventTick = Long.MIN_VALUE;
    private long bufferDirtyTick = Long.MIN_VALUE;
    private long outputReadyTick = Long.MIN_VALUE;

    public MolecularManipulatorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModContent.MOLECULAR_MANIPULATOR_BLOCK_ENTITY.get(), pos, blockState);
        getMainNode().setIdlePowerUsage(8.0);
    }

    @Override
    protected PatternProviderLogic createLogic() {
        return new MolecularManipulatorLogic(getMainNode(), this);
    }

    boolean acceptPattern(IPatternDetails patternDetails, KeyCounter[] inputs) {
        if (assembling || !(patternDetails instanceof IMolecularAssemblerSupportedPattern pattern)) {
            return false;
        }

        var level = getLevel();
        var grid = getMainNode().getGrid();
        if (level == null || grid == null || !getLogic().getReturnInv().isEmpty()) {
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

            for (var entry : outputAmounts.object2LongEntrySet()) {
                bufferedOutputs.put(entry.getKey(),
                        bufferedOutputs.getLong(entry.getKey()) + entry.getLongValue());
            }
            craftingBatcher.consumeInputs(inputs);
            fireCraftingEventOncePerTick(level, patternDetails, pattern);
            outputReadyTick = Math.max(outputReadyTick, level.getGameTime() + 1);
            markOutputBufferChanged(level.getGameTime());
            return true;
        } finally {
            assembling = false;
        }
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
    }

    public void serverTick() {
        var level = getLevel();
        if (level == null || level.isClientSide() || assembling || bufferedOutputs.isEmpty()
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
