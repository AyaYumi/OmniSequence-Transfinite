package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.block.HighEnergyLabCradleBlock;
import com.atir.molecularmanipulator.block.LabPlatformGeometry;
import com.atir.molecularmanipulator.entity.HighEnergyLabEffectsEntity;
import com.atir.molecularmanipulator.registry.HighEnergyLabContent;
import com.atir.molecularmanipulator.world.LabCrystallization;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** One stack per cradle; the input owns one durable, paid-power batch experiment. */
public final class HighEnergyLabCradleBlockEntity extends BlockEntity {
    private ItemStack stored = ItemStack.EMPTY;
    private ItemStack working = ItemStack.EMPTY;
    private int progress;
    private boolean advancing;
    private long receivedAt;

    public HighEnergyLabCradleBlockEntity(BlockPos pos, BlockState state) {
        super(HighEnergyLabContent.CRADLE_BE.get(), pos, state);
    }

    public boolean input() { return getBlockState().getBlock() instanceof HighEnergyLabCradleBlock block && block.input(); }
    public ItemStack stored() { return stored; }
    public ItemStack working() { return working; }
    public int progress() { return progress; }
    public boolean running() { return !working.isEmpty(); }
    public boolean advancing() { return advancing; }
    public int count() { return stored.getCount() + working.getCount(); }
    public float renderProgress(float partialTick) {
        if (!advancing || level == null) return progress;
        return Math.min(LabCrystallization.DURATION, progress + Math.max(0, level.getGameTime() - receivedAt) + partialTick);
    }

    /** Insert as much of the held stack as fits; component variants never merge. */
    public int insert(ItemStack held, boolean creative) {
        if (!input() || LabCrystallization.resultFor(held).isEmpty()
                || !stored.isEmpty() && !ItemStack.isSameItemSameComponents(stored, held)
                || !working.isEmpty() && !ItemStack.isSameItemSameComponents(working, held)) return 0;
        int moved = Math.min(held.getCount(), Math.min(64, held.getMaxStackSize()) - count());
        if (moved <= 0) return 0;
        if (stored.isEmpty()) stored = held.copyWithCount(moved); else stored.grow(moved);
        if (!creative) held.shrink(moved);
        sync();
        return moved;
    }

    public void take(Player player) {
        if (stored.isEmpty()) return;
        var result = stored;
        stored = ItemStack.EMPTY;
        if (!player.addItem(result)) player.drop(result, false);
        sync();
    }

    public boolean start() {
        if (!input() || stored.isEmpty() || LabCrystallization.resultFor(stored).isEmpty()
                || !working.isEmpty() || !ready()) return false;
        working = stored;
        stored = ItemStack.EMPTY;
        progress = 0;
        sync();
        return true;
    }

    public BlockPos inletPosition() {
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        return worldPosition.offset(LabPlatformGeometry.rotateOffset(
                (input() ? LabPlatformGeometry.INPUT : LabPlatformGeometry.OUTPUT).multiply(-1), facing));
    }

    public HighEnergyLabEffectsEntity anchor() {
        if (level == null) return null;
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var inlet = inletPosition();
        var position = inlet.offset(LabPlatformGeometry.rotateOffset(LabPlatformGeometry.ANCHOR.subtract(LabPlatformGeometry.INLET), facing));
        var anchors = level.getEntitiesOfClass(HighEnergyLabEffectsEntity.class, new AABB(position).inflate(1),
                entity -> entity.platform() && entity.powerPosition().equals(inlet));
        return anchors.isEmpty() ? null : anchors.getFirst();
    }

    private HighEnergyLabCradleBlockEntity output() {
        if (level == null) return null;
        var facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var pos = inletPosition().offset(LabPlatformGeometry.rotateOffset(LabPlatformGeometry.OUTPUT, facing));
        return level.getBlockEntity(pos) instanceof HighEnergyLabCradleBlockEntity cradle
                && !cradle.input() && cradle.getBlockState().getValue(HorizontalDirectionalBlock.FACING) == facing ? cradle : null;
    }

    private boolean canReceive(ItemStack result) {
        int limit = Math.min(64, result.getMaxStackSize());
        return !input() && !result.isEmpty() && result.getCount() <= limit
                && (stored.isEmpty() || ItemStack.isSameItemSameComponents(stored, result)
                && stored.getCount() + result.getCount() <= limit);
    }

    private boolean ready() {
        if (level == null || !(level.getBlockEntity(inletPosition()) instanceof HighEnergyLabPowerInterfaceBlockEntity inlet)
                || !inlet.powered()) return false;
        var output = output();
        return output != null && output.canReceive(LabCrystallization.resultFor(working.isEmpty() ? stored : working))
                && anchor() != null;
    }

    public void serverTick() {
        if (level == null || level.isClientSide || !input()) return;
        // Old crystal-to-board batches return their original input, never create a new board.
        // Drop beside the cradle instead if a different stored stack prevents merging.
        if (!working.isEmpty() && LabCrystallization.resultFor(working).isEmpty()) {
            if (stored.isEmpty()) stored = working;
            else if (ItemStack.isSameItemSameComponents(stored, working)
                    && stored.getCount() + working.getCount() <= stored.getMaxStackSize()) stored.grow(working.getCount());
            else Block.popResource(level, worldPosition, working);
            working = ItemStack.EMPTY;
            progress = 0;
            advancing = false;
            sync();
        }
        boolean next = !working.isEmpty() && ready();
        if (advancing != next) { advancing = next; sync(); }
        if (!next) return;
        progress++;
        setChanged();
        if (progress >= LabCrystallization.DURATION) {
            var output = output();
            var result = LabCrystallization.resultFor(working);
            if (output.stored.isEmpty()) output.stored = result;
            else output.stored.grow(result.getCount());
            output.sync();
            working = ItemStack.EMPTY;
            progress = 0;
            advancing = false;
            sync();
        } else if (progress % 4 == 0) sync();
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        var remaining = stored; var reserved = working;
        stored = ItemStack.EMPTY; working = ItemStack.EMPTY; advancing = false;
        Block.popResource(level, worldPosition, remaining);
        Block.popResource(level, worldPosition, reserved);
    }

    private void sync() {
        setChanged();
        receivedAt = level == null ? 0 : level.getGameTime();
        if (level instanceof ServerLevel server) server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Stored", stored.saveOptional(registries));
        tag.put("Working", working.saveOptional(registries));
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = ItemStack.parseOptional(registries, tag.getCompound("Stored"));
        working = ItemStack.parseOptional(registries, tag.getCompound("Working"));
        progress = Math.clamp(tag.getInt("Progress"), 0, LabCrystallization.DURATION - 1);
        advancing = level != null && level.isClientSide && tag.getBoolean("Advancing");
        receivedAt = level == null ? 0 : level.getGameTime();
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = saveWithoutMetadata(registries);
        tag.putBoolean("Advancing", advancing);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
