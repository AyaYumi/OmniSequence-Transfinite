package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;
import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

public final class MatterFabricationPatternAssemblyBlockEntity extends PatternProviderBlockEntity {
    public static final int PATTERN_SLOTS = 36;
    private static final String CONTROLLER_TAG = "fabrication_controller";
    private BlockPos controllerPos;
    private IGridConnection controllerConnection;
    private final MatterPatternBuffer buffer = new MatterPatternBuffer(this);

    public MatterFabricationPatternAssemblyBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY_BE.get(), pos, state);
    }

    @Override
    protected MatterFabricationPatternLogic createLogic() {
        return new MatterFabricationPatternLogic(this);
    }

    @Override
    public MatterFabricationPatternLogic getLogic() {
        return (MatterFabricationPatternLogic) super.getLogic();
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY_ITEM.get();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY_ITEM.get());
    }

    public void openAssemblyMenu(Player player, MenuLocator locator) {
        MenuOpener.open(ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY_MENU.get(), player, locator);
    }

    public void rename(String requestedName) {
        String safeName = requestedName == null ? "" : requestedName
                .replaceAll("[\\p{Cntrl}]", "").strip();
        if (safeName.length() > 64) {
            safeName = safeName.substring(0, 64);
        }
        if (safeName.isBlank()) {
            safeName = ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY_ITEM.get()
                    .getDescription().getString();
        }
        setName(safeName);
        saveChanges();
        getLogic().updatePatterns();
    }

    public boolean hasRemovalRecovery() {
        return buffer.hasContents() || RetainedBlockContents.hasPatternContents(this);
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        if (!hasRemovalRecovery()) return;
        var contents = new CompoundTag();
        getLogic().writeToNBT(contents);
        buffer.save(contents);
        drops.add(RetainedBlockContents.createDrop(this, contents));
    }

    public MatterPatternBuffer getBuffer() { return buffer; }
    void patternsChanged() { if (buffer != null) buffer.patternsChanged(); }
    public void serverTick() { buffer.serverTick(); }

    @Override
    public void clearContent() {
        super.clearContent();
        buffer.clear();
        saveChanges();
    }

    public void setControllerPos(BlockPos controllerPos) {
        if (controllerPos == null ? this.controllerPos == null : controllerPos.equals(this.controllerPos)) {
            ensureControllerConnection();
            return;
        }
        destroyControllerConnection();
        this.controllerPos = controllerPos == null ? null : controllerPos.immutable();
        saveChanges();
        getLogic().updatePatterns();
        ensureControllerConnection();
    }

    public boolean isOperational() {
        return getController() != null && getMainNode().isActive();
    }

    public boolean acceptPattern(IPatternDetails details, KeyCounter[] inputs) {
        var supplied = new java.util.LinkedHashMap<appeng.api.stacks.AEKey, Long>();
        try {
            for (var counter : inputs) for (var entry : counter) {
                if (entry.getLongValue() <= 0) return false;
                supplied.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            }
            if (!buffer.enqueue(details, supplied, 1)) return false;
        } catch (ArithmeticException error) { return false; }
        for (var counter : inputs) counter.clear();
        return true;
    }

    MatterFabricationBlockEntity getController() {
        if (controllerPos == null || level == null) {
            return null;
        }
        var blockEntity = level.getBlockEntity(controllerPos);
        if (blockEntity instanceof MatterFabricationBlockEntity controller
                && controller.isStructureFormed()) {
            return controller;
        }
        return null;
    }

    @Override
    public void onReady() {
        super.onReady();
        ensureControllerConnection();
    }

    @Override
    public void setRemoved() {
        destroyControllerConnection();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        destroyControllerConnection();
        super.onChunkUnloaded();
    }

    private void ensureControllerConnection() {
        if (level == null || level.isClientSide() || controllerPos == null) {
            return;
        }
        if (!level.hasChunkAt(controllerPos)) {
            destroyControllerConnection();
            return;
        }
        var controllerEntity = level.getBlockEntity(controllerPos);
        if (!(controllerEntity instanceof MatterFabricationBlockEntity controller) || !controller.isStructureFormed()) {
            destroyControllerConnection();
            return;
        }
        var ownNode = getMainNode().getNode();
        var controllerNode = controller.getMainNode().getNode();
        if (ownNode == null || controllerNode == null) {
            destroyControllerConnection();
            return;
        }
        if (controllerConnection != null) {
            if (ownNode.getConnections().contains(controllerConnection)
                    && controllerNode.getConnections().contains(controllerConnection)) {
                return;
            }
            destroyControllerConnection();
        }
        for (var connection : ownNode.getConnections()) {
            if (connection.getOtherSide(ownNode) == controllerNode) {
                controllerConnection = connection;
                return;
            }
        }
        controllerConnection = GridHelper.createConnection(ownNode, controllerNode);
    }

    private void destroyControllerConnection() {
        if (controllerConnection != null) {
            var currentNode = getMainNode().getNode();
            if (currentNode != null && currentNode.getConnections().contains(controllerConnection)) {
                controllerConnection.destroy();
            }
            controllerConnection = null;
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        buffer.save(tag);
        if (controllerPos != null) {
            tag.putLong(CONTROLLER_TAG, controllerPos.asLong());
        }
    }

    @Override
    public void loadTag(CompoundTag tag) {
        tag = RetainedBlockContents.unpack(tag);
        super.loadTag(tag);
        buffer.load(tag);
        controllerPos = tag.contains(CONTROLLER_TAG) ? BlockPos.of(tag.getLong(CONTROLLER_TAG)) : null;
    }
}
