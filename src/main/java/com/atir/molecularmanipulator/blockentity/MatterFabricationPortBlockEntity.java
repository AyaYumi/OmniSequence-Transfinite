package com.atir.molecularmanipulator.blockentity;

import appeng.blockentity.AEBaseBlockEntity;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.me.helpers.MachineSource;
import appeng.util.SettingsFrom;
import com.atir.molecularmanipulator.block.MatterFabricationPortBlock;
import com.atir.molecularmanipulator.block.MatterFabricationPortType;
import com.atir.molecularmanipulator.registry.ModContent;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import java.util.List;

public final class MatterFabricationPortBlockEntity extends AEBaseBlockEntity {
    public static final int ITEM_SLOTS = 16;
    public static final int FLUID_TANKS = 4;
    public static final int FLUID_CAPACITY = Integer.MAX_VALUE;
    private static final String INVENTORY_TAG = "port_inventory";
    private static final String FLUIDS_TAG = "port_fluids";
    private static final String FLUID_TAG = "port_fluid";
    private static final String CONTROLLER_TAG = "fabrication_controller";
    private static final String AUTO_OUTPUT_TAG = "port_auto_output";
    private static final String OUTPUT_SIDES_TAG = "port_output_sides";

    private final ItemStackHandler inventory = new ItemStackHandler(ITEM_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            saveChanges();
        }
    };
    private final FluidTank[] tanks = createTanks();
    private final IFluidHandler internalFluids = new MultiTankFluidHandler();
    private final IItemHandler externalItems = new ExternalItemHandler();
    private final IFluidHandler externalFluids = new ExternalFluidHandler();
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> externalItems);
    private LazyOptional<IFluidHandler> fluidCapability = LazyOptional.of(() -> externalFluids);

    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
        if (!isRemoved()) {
            if (capability == ForgeCapabilities.ITEM_HANDLER && getPortType().isItem()) return itemCapability.cast();
            if (capability == ForgeCapabilities.FLUID_HANDLER && getPortType().isFluid()) return fluidCapability.cast();
        }
        return super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps();
        itemCapability.invalidate();
        fluidCapability.invalidate();
    }
    @Override public void reviveCaps() {
        super.reviveCaps();
        itemCapability = LazyOptional.of(() -> externalItems);
        fluidCapability = LazyOptional.of(() -> externalFluids);
    }

    private BlockPos controllerPos;
    private boolean autoOutput;
    private int outputSides;
    private long nextOutputTick;

    public MatterFabricationPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MATTER_FABRICATION_PORT_BE.get(), pos, state);
    }

    public MatterFabricationPortType getPortType() {
        return ((MatterFabricationPortBlock) getBlockState().getBlock()).getPortType();
    }

    public IItemHandler getExternalItemHandler() {
        return getPortType().isItem() ? externalItems : null;
    }

    public IFluidHandler getExternalFluidHandler() {
        return getPortType().isFluid() ? externalFluids : null;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public FluidTank getTank(int index) {
        if (index < 0 || index >= tanks.length) {
            throw new IndexOutOfBoundsException("Invalid fabrication tank " + index);
        }
        return tanks[index];
    }

    public IFluidHandler getInternalFluidHandler() {
        return internalFluids;
    }

    public void openMenu(Player player) {
        MenuOpener.open(ModContent.MATTER_FABRICATION_PORT_MENU.get(), player,
                MenuLocators.forBlockEntity(this));
    }

    public void setControllerPos(BlockPos controllerPos) {
        if (controllerPos == null ? this.controllerPos == null : controllerPos.equals(this.controllerPos)) {
            return;
        }
        this.controllerPos = controllerPos == null ? null : controllerPos.immutable();
        saveChanges();
    }

    public boolean isLinkedTo(BlockPos controller) {
        return controllerPos != null && controllerPos.equals(controller);
    }

    public MatterFabricationBlockEntity getController() {
        if (controllerPos == null || level == null || !level.hasChunkAt(controllerPos)) return null;
        if (!(level.getBlockEntity(controllerPos) instanceof MatterFabricationBlockEntity machine) || !machine.isStructureFormed()) return null;
        var facing = machine.getBlockState().getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING);
        // Packed interfaces can carry a previous controller coordinate; require a current physical service bay.
        return MatterFabricationStructure.patternAssemblyBays().stream()
                .anyMatch(bay -> MatterFabricationStructure.worldPos(controllerPos, facing, bay).equals(worldPosition)) ? machine : null;
    }

    public boolean isNetworkOnline() {
        var controller = getController();
        return controller != null && controller.getMainNode().isActive();
    }

    public boolean isAutoOutput() { return autoOutput; }
    public int getOutputSides() { return outputSides; }
    public void setAutoOutput(boolean enabled) {
        if (!getPortType().isOutput()) return;
        autoOutput = enabled; nextOutputTick = 0; saveChanges();
    }
    public void setOutputSides(int sides) {
        if (!getPortType().isOutput()) return;
        outputSides = sides & 63; nextOutputTick = 0; saveChanges();
    }

    public long totalAmount() {
        long amount = 0;
        if (getPortType().isItem()) for (int slot = 0; slot < ITEM_SLOTS; slot++) amount += inventory.getStackInSlot(slot).getCount();
        else for (var tank : tanks) amount += tank.getFluidAmount();
        return amount;
    }

    public int occupiedSlots() {
        int occupied = 0;
        if (getPortType().isItem()) { for (int slot = 0; slot < ITEM_SLOTS; slot++) if (!inventory.getStackInSlot(slot).isEmpty()) occupied++; }
        else for (var tank : tanks) if (!tank.isEmpty()) occupied++;
        return occupied;
    }

    /** Single server-thread transfer; only the amount accepted by AE is removed from this port. */
    public long returnInputsToNetwork() {
        if (level == null || level.isClientSide() || !getPortType().isInput() || !isNetworkOnline()) return 0;
        var controller = getController();
        var storage = controller.getMainNode().getGrid().getStorageService().getInventory();
        var source = new MachineSource(controller);
        long moved = 0;
        if (getPortType().isItem()) {
            for (int slot = 0; slot < ITEM_SLOTS; slot++) {
                var stack = inventory.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                int accepted = (int) storage.insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, source);
                if (accepted > 0) { inventory.extractItem(slot, accepted, false); moved += accepted; }
            }
        } else {
            for (var tank : tanks) {
                if (tank.isEmpty()) continue;
                int accepted = (int) storage.insert(AEFluidKey.of(tank.getFluid()), tank.getFluidAmount(), Actionable.MODULATE, source);
                if (accepted > 0) { tank.drain(accepted, IFluidHandler.FluidAction.EXECUTE); moved += accepted; }
            }
        }
        return moved;
    }

    public void serverTick() {
        if (level == null || level.isClientSide() || !getPortType().isOutput() || !autoOutput || outputSides == 0) return;
        long now = level.getGameTime();
        if (now < nextOutputTick) return;
        nextOutputTick = now + 5;
        for (var side : Direction.values()) {
            if ((outputSides & 1 << side.get3DDataValue()) == 0) continue;
            var neighbor = worldPosition.relative(side);
            if (!level.hasChunkAt(neighbor)) continue;
            if (getPortType().isItem()) {
                var neighborEntity = level.getBlockEntity(neighbor);
                var target = neighborEntity == null ? null : neighborEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side.getOpposite()).orElse(null);
                if (target == null || target == externalItems || target == inventory) continue;
                for (int slot = 0; slot < ITEM_SLOTS; slot++) {
                    var stack = inventory.getStackInSlot(slot);
                    if (stack.isEmpty()) continue;
                    var remainder = ItemHandlerHelper.insertItemStacked(target, stack.copy(), false);
                    int moved = stack.getCount() - remainder.getCount();
                    if (moved > 0) inventory.extractItem(slot, moved, false);
                }
            } else {
                var neighborEntity = level.getBlockEntity(neighbor);
                var target = neighborEntity == null ? null : neighborEntity.getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite()).orElse(null);
                if (target == null || target == externalFluids || target == internalFluids) continue;
                for (var tank : tanks) {
                    if (tank.isEmpty()) continue;
                    int moved = target.fill(tank.getFluid().copy(), IFluidHandler.FluidAction.EXECUTE);
                    if (moved > 0) tank.drain(moved, IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }
    }

    @Override
    public void exportSettings(SettingsFrom mode, CompoundTag builder, Player player) {
        super.exportSettings(mode, builder, player);
        if (mode == SettingsFrom.DISMANTLE_ITEM) {
            var tag = new CompoundTag(); tag.putBoolean(AUTO_OUTPUT_TAG, autoOutput); tag.putInt(OUTPUT_SIDES_TAG, outputSides);
            builder.merge(tag);
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, CompoundTag input, Player player) {
        super.importSettings(mode, input, player);
        if (mode == SettingsFrom.DISMANTLE_ITEM && !input.isEmpty()) {
            var tag = input;
            if (tag.contains(AUTO_OUTPUT_TAG)) autoOutput = tag.getBoolean(AUTO_OUTPUT_TAG);
            if (tag.contains(OUTPUT_SIDES_TAG)) outputSides = tag.getInt(OUTPUT_SIDES_TAG) & 63;
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        if (!hasRemovalRecovery()) return;
        var contents = new CompoundTag();
        contents.put(INVENTORY_TAG, inventory.serializeNBT());
        var fluids = new ListTag();
        for (var tank : tanks) fluids.add(tank.writeToNBT( new CompoundTag()));
        contents.put(FLUIDS_TAG, fluids);
        contents.putBoolean(AUTO_OUTPUT_TAG, autoOutput);
        contents.putInt(OUTPUT_SIDES_TAG, outputSides);
        drops.add(RetainedBlockContents.createDrop(this, contents));
    }

    public boolean hasRemovalRecovery() {
        for (int slot = 0; slot < inventory.getSlots(); slot++) if (!inventory.getStackInSlot(slot).isEmpty()) return true;
        for (var tank : tanks) if (!tank.isEmpty()) return true;
        return false;
    }

    @Override
    public void clearContent() {
        super.clearContent();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        for (var tank : tanks) {
            tank.setFluid(FluidStack.EMPTY);
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(INVENTORY_TAG, inventory.serializeNBT());
        var fluids = new ListTag();
        for (var tank : tanks) {
            fluids.add(tank.writeToNBT(new CompoundTag()));
        }
        tag.put(FLUIDS_TAG, fluids);
        tag.putBoolean(AUTO_OUTPUT_TAG, autoOutput);
        tag.putInt(OUTPUT_SIDES_TAG, outputSides);
        if (controllerPos != null) {
            tag.putLong(CONTROLLER_TAG, controllerPos.asLong());
        }
    }

    @Override
    public void loadTag(CompoundTag tag) {
        tag = RetainedBlockContents.unpack(tag);
        super.loadTag(tag);
        inventory.deserializeNBT(tag.getCompound(INVENTORY_TAG));
        for (var tank : tanks) {
            tank.setFluid(FluidStack.EMPTY);
        }
        if (tag.contains(FLUIDS_TAG, Tag.TAG_LIST)) {
            var fluids = tag.getList(FLUIDS_TAG, Tag.TAG_COMPOUND);
            for (int index = 0; index < Math.min(fluids.size(), tanks.length); index++) {
                tanks[index].readFromNBT(fluids.getCompound(index));
            }
        } else if (tag.contains(FLUID_TAG, Tag.TAG_COMPOUND)) {
            // Backwards compatibility for the original single 256,000 mB tank.
            tanks[0].readFromNBT(tag.getCompound(FLUID_TAG));
        }
        controllerPos = tag.contains(CONTROLLER_TAG) ? BlockPos.of(tag.getLong(CONTROLLER_TAG)) : null;
        autoOutput = tag.getBoolean(AUTO_OUTPUT_TAG);
        outputSides = tag.contains(OUTPUT_SIDES_TAG) ? tag.getInt(OUTPUT_SIDES_TAG) & 63 : 0;
        nextOutputTick = 0;
    }

    private final class ExternalItemHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return inventory.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return getPortType().isInput() ? inventory.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return getPortType().isOutput() ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return getPortType().isInput() && inventory.isItemValid(slot, stack);
        }
    }

    private final class ExternalFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return FLUID_TANKS;
        }

        @Override
        public FluidStack getFluidInTank(int tankIndex) {
            return getTank(tankIndex).getFluid().copy();
        }

        @Override
        public int getTankCapacity(int tankIndex) {
            return getTank(tankIndex).getCapacity();
        }

        @Override
        public boolean isFluidValid(int tankIndex, FluidStack stack) {
            return getPortType().isInput() && getTank(tankIndex).isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return getPortType().isInput() ? internalFluids.fill(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return getPortType().isOutput()
                    ? internalFluids.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return getPortType().isOutput()
                    ? internalFluids.drain(maxDrain, action) : FluidStack.EMPTY;
        }
    }

    private FluidTank[] createTanks() {
        var result = new FluidTank[FLUID_TANKS];
        for (int index = 0; index < result.length; index++) {
            result[index] = new FluidTank(FLUID_CAPACITY) {
                @Override
                protected void onContentsChanged() {
                    saveChanges();
                }
            };
        }
        return result;
    }

    /** Combines four independent tanks while preserving their individual fluids. */
    private final class MultiTankFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return tanks.length;
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            return getTank(index).getFluid().copy();
        }

        @Override
        public int getTankCapacity(int index) {
            return getTank(index).getCapacity();
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack) {
            return getTank(index).isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            int filled = 0;
            // Merge into matching tanks first so empty tanks stay available for
            // other fluids.
            for (int pass = 0; pass < 2 && filled < resource.getAmount(); pass++) {
                for (var tank : tanks) {
                    boolean matching = !tank.isEmpty()
                            && tank.getFluid().isFluidEqual(resource);
                    if ((pass == 0) != matching || pass == 1 && !tank.isEmpty()) {
                        continue;
                    }
                    var remainder = com.atir.molecularmanipulator.util.ForgeFluids.copyWithAmount(resource, resource.getAmount() - filled);
                    filled += tank.fill(remainder, action);
                    if (filled >= resource.getAmount()) {
                        break;
                    }
                }
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            int remaining = resource.getAmount();
            int drainedAmount = 0;
            for (var tank : tanks) {
                if (remaining <= 0 || tank.isEmpty()
                        || !tank.getFluid().isFluidEqual(resource)) {
                    continue;
                }
                var drained = tank.drain(com.atir.molecularmanipulator.util.ForgeFluids.copyWithAmount(resource, remaining), action);
                drainedAmount += drained.getAmount();
                remaining -= drained.getAmount();
            }
            return drainedAmount <= 0 ? FluidStack.EMPTY : com.atir.molecularmanipulator.util.ForgeFluids.copyWithAmount(resource, drainedAmount);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            for (var tank : tanks) {
                if (!tank.isEmpty()) {
                    return drain(com.atir.molecularmanipulator.util.ForgeFluids.copyWithAmount(tank.getFluid(), maxDrain), action);
                }
            }
            return FluidStack.EMPTY;
        }
    }
}
