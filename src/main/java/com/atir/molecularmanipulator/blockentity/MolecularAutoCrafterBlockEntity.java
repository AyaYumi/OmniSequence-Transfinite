package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.me.helpers.MachineSource;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.api.util.AECableType;
import appeng.api.orientation.BlockOrientation;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.integration.ae2.AEKeyTransferScheduler;
import com.atir.molecularmanipulator.menu.MolecularAutoCrafterMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.block.MolecularAutoCrafterBlock;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Single-block host for the sequence array's passive auto-crafting subsystem. */
public final class MolecularAutoCrafterBlockEntity extends PatternProviderBlockEntity
        implements InternalInventoryHost, MolecularAutoCrafterHost {
    private static final String AUTO_CRAFTER_TAG = "sequence_auto_crafter";
    private static final String OUTPUTS_TAG = "outputs";
    private static final String OUTPUT_READY_TAG = "output_ready_tick";
    private static final String OUTPUT_MODE_TAG = "output_mode";
    private static final String OUTPUT_SIDES_TAG = "output_sides";
    private static final int MAX_BUFFERED_TYPES = 256;

    private final MachineSource actionSource = new MachineSource(this);
    private final MolecularAutoCrafter autoCrafter = new MolecularAutoCrafter(this);
    private final Object2LongOpenHashMap<AEKey> bufferedOutputs = new Object2LongOpenHashMap<>();
    private final AEKeyTransferScheduler outputTransferScheduler = new AEKeyTransferScheduler();
    private boolean assembling;
    private long outputReadyTick = Long.MIN_VALUE;
    private int menuSelectedSlot = -1;
    private OutputMode outputMode = OutputMode.NETWORK;
    private int outputSides;

    public MolecularAutoCrafterBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MOLECULAR_AUTO_CRAFTER_BE.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(128.0);
    }

    @Override
    protected PatternProviderLogic createLogic() {
        // The nine private slots are intentionally not advertised as ordinary AE2
        // pattern-provider slots. They are scheduled by MolecularAutoCrafter.
        return new PatternProviderLogic(getMainNode(), this, 0);
    }

    public MolecularAutoCrafter getAutoCrafter() {
        return autoCrafter;
    }

    public AppEngInternalInventory getAutoCraftPatternInventory() {
        return autoCrafter.getPatternInventory();
    }

    public int getMenuSelectedSlot() {
        return menuSelectedSlot;
    }

    public void setMenuSelectedSlot(int slot) {
        menuSelectedSlot = slot >= 0 && slot < MolecularAutoCrafter.PATTERN_SLOTS ? slot : -1;
    }

    public OutputMode getOutputMode() {
        return outputMode;
    }

    public int getOutputSides() {
        return outputSides;
    }

    public void toggleOutputMode() {
        outputMode = outputMode == OutputMode.NETWORK ? OutputMode.ADJACENT : OutputMode.NETWORK;
        outputReadyTick = 0;
        saveChanges();
    }

    public void toggleOutputSide(int side) {
        if (side < 0 || side >= 6) return;
        outputSides ^= 1 << side;
        outputReadyTick = 0;
        saveChanges();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(ModContent.MOLECULAR_AUTO_CRAFTER_ITEM.get());
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inventory) {
        saveChanges();
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.SMART;
    }

    @Override
    public EnumSet<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    public boolean isOperational() {
        return getLevel() != null && getMainNode().isActive();
    }

    @Override
    public boolean canRunAutoCrafting() {
        return !assembling && isOperational();
    }

    @Override
    public IActionSource autoCraftActionSource() {
        return actionSource;
    }

    @Override
    public boolean canQueueAutoCraftOutputs(Object2LongOpenHashMap<AEKey> primary,
            Object2LongOpenHashMap<AEKey> remainders) {
        int types = bufferedOutputs.size();
        var totals = new Object2LongOpenHashMap<AEKey>();
        totals.putAll(bufferedOutputs);
        try {
            for (var group : List.of(primary, remainders)) {
                for (var entry : group.object2LongEntrySet()) {
                    if (!totals.containsKey(entry.getKey()) && ++types > MAX_BUFFERED_TYPES) {
                        return false;
                    }
                    totals.put(entry.getKey(), Math.addExact(totals.getLong(entry.getKey()), entry.getLongValue()));
                }
            }
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    @Override
    public void queueAutoCraftOutputs(Object2LongOpenHashMap<AEKey> primary,
            Object2LongOpenHashMap<AEKey> remainders, long gameTime, long craftCount) {
        addOutputs(primary);
        addOutputs(remainders);
        outputReadyTick = Math.max(outputReadyTick, gameTime + 1);
        saveChanges();
    }

    @Override
    public void queueAutoCraftRefund(AEKey key, long amount) {
        if (key != null && amount > 0) {
            bufferedOutputs.put(key, MolecularAutoCraftMath.saturatedAdd(
                    bufferedOutputs.getLong(key), amount));
            saveChanges();
        }
    }

    @Override
    public long getBufferedAutoCraftAmount(AEKey key) {
        return key == null ? 0 : bufferedOutputs.getLong(key);
    }

    @Override
    public void flushAutoCraftOutputsAfterControlChange() {
        Level level = getLevel();
        if (level != null && !level.isClientSide() && !assembling) {
            flushOutputs(level);
        }
    }

    /** Snapshot for the output-buffer slots; the machine remains the owner of these resources. */
    public List<GenericStack> getBufferedAutoCraftOutputs() {
        var result = new ArrayList<GenericStack>();
        for (var entry : bufferedOutputs.object2LongEntrySet()) {
            if (entry.getLongValue() > 0) {
                result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }
        return result;
    }

    private void addOutputs(Object2LongOpenHashMap<AEKey> outputs) {
        for (var entry : outputs.object2LongEntrySet()) {
            bufferedOutputs.put(entry.getKey(), Math.addExact(
                    bufferedOutputs.getLong(entry.getKey()), entry.getLongValue()));
        }
    }

    public void serverTick() {
        Level level = getLevel();
        if (level == null || level.isClientSide()) return;
        autoCrafter.tick(level.getGameTime());
        updateWorkingState();
        flushOutputs(level);
    }

    private void updateWorkingState() {
        boolean working = autoCrafter.isWorking();
        if (getBlockState().getValue(MolecularAutoCrafterBlock.WORKING) != working) {
            getLevel().setBlock(worldPosition,
                    getBlockState().setValue(MolecularAutoCrafterBlock.WORKING, working), 3);
        }
    }

    private void flushOutputs(Level level) {
        if (assembling || bufferedOutputs.isEmpty() || level.getGameTime() < outputReadyTick) return;
        var grid = getMainNode().getGrid();
        if (outputMode == OutputMode.NETWORK && grid == null) return;
        assembling = true;
        try {
            long transferred;
            if (outputMode == OutputMode.NETWORK) {
                var result = outputTransferScheduler.flush(bufferedOutputs,
                        AEKeyTransferScheduler.defaultBudget(),
                        (key, amount) -> grid.getStorageService().getInventory().insert(
                                key, amount, Actionable.MODULATE, actionSource));
                transferred = result.transferred();
                if (result.changed()) saveChanges();
            } else {
                transferred = flushToAdjacent(level);
            }
            outputReadyTick = bufferedOutputs.isEmpty()
                    ? Long.MIN_VALUE : level.getGameTime() + (transferred > 0 ? 1 : 5);
        } finally {
            assembling = false;
        }
    }

    private long flushToAdjacent(Level level) {
        if (outputSides == 0) return 0;
        long transferred = 0;
        for (var key : new ArrayList<>(bufferedOutputs.keySet())) {
            long amount = bufferedOutputs.getLong(key);
            if (amount <= 0 || !(key instanceof AEItemKey itemKey)) continue;
            long remaining = amount;
            for (var side : Direction.values()) {
                if ((outputSides & 1 << side.get3DDataValue()) == 0) continue;
                BlockPos neighbor = worldPosition.relative(side);
                if (!level.hasChunkAt(neighbor)) continue;
                var target = level.getCapability(Capabilities.ItemHandler.BLOCK,
                        neighbor, side.getOpposite());
                if (target == null) continue;
                while (remaining > 0) {
                    int batch = (int) Math.min(remaining,
                            Math.min(Integer.MAX_VALUE, itemKey.getMaxStackSize()));
                    var remainder = ItemHandlerHelper.insertItemStacked(
                            target, itemKey.toStack(batch), false);
                    int accepted = batch - remainder.getCount();
                    if (accepted <= 0) break;
                    remaining -= accepted;
                    transferred += accepted;
                    if (accepted < batch) break;
                }
                if (remaining == 0) break;
            }
            if (remaining == 0) bufferedOutputs.removeLong(key);
            else if (remaining != amount) bufferedOutputs.put(key, remaining);
        }
        if (transferred > 0) saveChanges();
        return transferred;
    }

    public boolean hasRemovalRecovery() {
        return !bufferedOutputs.isEmpty() || !autoCrafter.getPatternInventory().isEmpty();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        if (!hasRemovalRecovery()) return;
        var payload = new CompoundTag();
        saveAdditional(payload, level.registryAccess());
        drops.add(RetainedBlockContents.createDrop(this, payload));
    }

    @Override
    public void clearContent() {
        super.clearContent();
        bufferedOutputs.clear();
        autoCrafter.clear();
        outputReadyTick = Long.MIN_VALUE;
        outputMode = OutputMode.NETWORK;
        outputSides = 0;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        autoCrafter.save(tag, registries);
        var outputs = new ListTag();
        for (var entry : bufferedOutputs.object2LongEntrySet()) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                outputs.add(GenericStack.writeTag(registries,
                        new GenericStack(entry.getKey(), entry.getLongValue())));
            }
        }
        tag.put(OUTPUTS_TAG, outputs);
        tag.putLong(OUTPUT_READY_TAG, outputReadyTick);
        tag.putString(OUTPUT_MODE_TAG, outputMode.name());
        tag.putInt(OUTPUT_SIDES_TAG, outputSides);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag = RetainedBlockContents.unpack(tag);
        super.loadTag(tag, registries);
        autoCrafter.load(tag, registries);
        bufferedOutputs.clear();
        for (var element : tag.getList(OUTPUTS_TAG, Tag.TAG_COMPOUND)) {
            var stack = GenericStack.readTag(registries, (CompoundTag) element);
            if (stack != null && stack.amount() > 0) bufferedOutputs.addTo(stack.what(), stack.amount());
        }
        outputReadyTick = tag.contains(OUTPUT_READY_TAG, Tag.TAG_LONG)
                ? tag.getLong(OUTPUT_READY_TAG) : Long.MIN_VALUE;
        outputMode = parseOutputMode(tag.getString(OUTPUT_MODE_TAG));
        outputSides = tag.contains(OUTPUT_SIDES_TAG, Tag.TAG_INT)
                ? tag.getInt(OUTPUT_SIDES_TAG) & 63 : 0;
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(MolecularAutoCrafterMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(MolecularAutoCrafterMenu.TYPE, player, subMenu.getLocator());
    }

    private static OutputMode parseOutputMode(String value) {
        try {
            return value == null || value.isBlank() ? OutputMode.NETWORK : OutputMode.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return OutputMode.NETWORK;
        }
    }

    public enum OutputMode {
        NETWORK,
        ADJACENT
    }
}
