package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.stacks.AEItemKey;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.me.helpers.PlayerSource;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipeInput;
import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

public final class MatterFabricationBlockEntity extends AENetworkedInvBlockEntity {
    public static final int INPUT_SLOTS = 4;
    public static final int OUTPUT_SLOTS = 2;
    public static final double IDLE_POWER = 16.0;
    private static final int STRUCTURE_CHECK_INTERVAL = 20;
    private static final String PROGRESS_TAG = "fabrication_progress";
    private static final String ACTIVE_RECIPE_TAG = "fabrication_recipe";
    private static final String BUILDING_TAG = "fabrication_building";
    private static final String BUILD_CURSOR_TAG = "fabrication_build_cursor";
    private static final String BUILD_OWNER_TAG = "fabrication_build_owner";
    private static final int BUILD_BLOCKS_PER_TICK = 64;

    private final AppEngInternalInventory inventory = new AppEngInternalInventory(this,
            INPUT_SLOTS + OUTPUT_SLOTS);
    private MatterFabricationStructure.Inspection inspection =
            new MatterFabricationStructure.Inspection(MatterFabricationStructure.parts().size(), 0,
                    MatterFabricationStructure.parts().size(), 0);
    private boolean structureFormed;
    private boolean clientStructureFormed;
    private boolean clientRunning;
    private int progress;
    private ResourceLocation activeRecipeId;
    private ProcessingState processingState = ProcessingState.STRUCTURE_INCOMPLETE;
    private int currentProcessingTime;
    private double currentAePerTick;
    private long nextStructureCheck;
    private boolean building;
    private int buildCursor;
    private UUID buildOwner;

    public MatterFabricationBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MATTER_FABRICATION_CONTROLLER_BE.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(IDLE_POWER);
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return ModContent.MATTER_FABRICATION_CONTROLLER_ITEM.get();
    }

    @Override
    public EnumSet<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public InternalInventory getInternalInventory() {
        return inventory;
    }

    public InternalInventory getInputInventory() {
        return inventory.getSubInventory(0, INPUT_SLOTS);
    }

    public InternalInventory getOutputInventory() {
        return inventory.getSubInventory(INPUT_SLOTS, INPUT_SLOTS + OUTPUT_SLOTS);
    }

    @Override
    protected InternalInventory getExposedInventoryForSide(Direction side) {
        return side == Direction.DOWN ? getOutputInventory() : getInputInventory();
    }

    @Override
    public void onReady() {
        super.onReady();
        scheduleStructureCheck();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory changedInventory, int slot) {
        if (slot < INPUT_SLOTS && progress > 0) {
            resetProgress();
        }
    }

    public void openMenu(Player player) {
        MenuOpener.open(ModContent.MATTER_FABRICATION_MENU.get(), player,
                MenuLocators.forBlockEntity(this));
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        long gameTime = level.getGameTime();
        if (building) {
            processBuild();
            setState(ProcessingState.BUILDING);
            updatePoweredState(false);
            return;
        }
        if (gameTime >= nextStructureCheck) {
            refreshStructure();
            nextStructureCheck = gameTime + STRUCTURE_CHECK_INTERVAL;
        }
        processRecipe();
    }

    public void scheduleStructureCheck() {
        nextStructureCheck = level == null ? 0 : Math.min(nextStructureCheck, level.getGameTime() + 1);
    }

    public void refreshStructure() {
        if (level == null || level.isClientSide()) {
            return;
        }
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var nextInspection = MatterFabricationStructure.inspect(level, worldPosition, facing);
        boolean changed = structureFormed != nextInspection.formed();
        inspection = nextInspection;
        structureFormed = nextInspection.formed();
        if (!structureFormed) {
            resetProgress();
        }
        if (changed) {
            onGridConnectableSidesChanged();
            markForClientUpdate();
        }
        saveChanges();
    }

    public void startBuild(ServerPlayer player) {
        if (level == null || level.isClientSide() || building || !player.mayBuild()) {
            return;
        }
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var currentInspection = MatterFabricationStructure.inspect(level, worldPosition, facing);
        if (currentInspection.formed()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.molecularmanipulator.fabrication.already_formed"), false);
            return;
        }
        if (currentInspection.conflicts() > 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.molecularmanipulator.fabrication.conflicts", currentInspection.conflicts()), false);
            return;
        }
        building = true;
        buildCursor = 0;
        buildOwner = player.getUUID();
        setState(ProcessingState.BUILDING);
        saveChanges();
    }

    private void processBuild() {
        if (level == null || buildOwner == null || level.getServer() == null) {
            return;
        }
        var player = level.getServer().getPlayerList().getPlayer(buildOwner);
        if (player == null || !player.mayBuild()) {
            return;
        }
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var parts = MatterFabricationStructure.parts();
        int budget = BUILD_BLOCKS_PER_TICK;
        while (budget-- > 0 && buildCursor < parts.size()) {
            var part = parts.get(buildCursor++);
            if (MatterFabricationStructure.isController(part)) {
                continue;
            }
            var pos = MatterFabricationStructure.worldPos(worldPosition, facing, part);
            var expected = MatterFabricationStructure.partState(part.type());
            var current = level.getBlockState(pos);
            if (current.is(expected.getBlock())) {
                continue;
            }
            if (!current.canBeReplaced() || !player.mayUseItemAt(pos, Direction.UP, ItemStack.EMPTY)) {
                building = false;
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.molecularmanipulator.fabrication.build_conflict",
                        pos.getX(), pos.getY(), pos.getZ()), false);
                saveChanges();
                return;
            }
            var material = new ItemStack(expected.getBlock().asItem());
            if (!takeBuildMaterial(player, material)) {
                building = false;
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.molecularmanipulator.fabrication.missing_material",
                        material.getHoverName()), false);
                saveChanges();
                return;
            }
            if (!level.setBlock(pos, expected, 3)) {
                refundBuildMaterial(player, material);
                building = false;
                saveChanges();
                return;
            }
        }
        if (buildCursor >= parts.size()) {
            building = false;
            buildCursor = parts.size();
            refreshStructure();
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.molecularmanipulator.fabrication.build_complete"), false);
        }
        saveChanges();
    }

    private boolean takeBuildMaterial(ServerPlayer player, ItemStack template) {
        if (player.getAbilities().instabuild) {
            return true;
        }
        for (var stack : player.getInventory().items) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, template)) {
                stack.shrink(1);
                return true;
            }
        }
        var grid = getMainNode().getGrid();
        var key = AEItemKey.of(template);
        return grid != null && key != null
                && grid.getStorageService().getInventory().extract(
                        key, 1, Actionable.MODULATE, new PlayerSource(player)) == 1;
    }

    private void refundBuildMaterial(ServerPlayer player, ItemStack template) {
        if (player.getAbilities().instabuild || player.getInventory().add(template.copy())) {
            return;
        }
        var grid = getMainNode().getGrid();
        var key = AEItemKey.of(template);
        if (grid == null || key == null || grid.getStorageService().getInventory().insert(
                key, 1, Actionable.MODULATE, new PlayerSource(player)) != 1) {
            player.drop(template.copy(), false);
        }
    }

    private void processRecipe() {
        if (!structureFormed) {
            setState(ProcessingState.STRUCTURE_INCOMPLETE);
            updatePoweredState(false);
            return;
        }
        if (!getMainNode().isActive()) {
            setState(ProcessingState.NETWORK_OFFLINE);
            updatePoweredState(false);
            return;
        }

        var input = snapshotInput();
        var match = level.getRecipeManager().getRecipeFor(
                ModContent.MATTER_FABRICATION_RECIPE_TYPE.get(), input, level);
        if (match.isEmpty()) {
            resetProgress();
            setState(ProcessingState.NO_RECIPE);
            updatePoweredState(false);
            return;
        }

        var holder = match.get();
        var recipe = holder.value();
        if (!holder.id().equals(activeRecipeId)) {
            progress = 0;
            activeRecipeId = holder.id();
        }
        currentProcessingTime = recipe.processingTime();
        currentAePerTick = recipe.aePerTick();

        if (!outputsFit(recipe.resultCopies())) {
            setState(ProcessingState.OUTPUT_BLOCKED);
            updatePoweredState(false);
            return;
        }
        if (!consumePower(recipe.aePerTick())) {
            setState(ProcessingState.WAITING_POWER);
            updatePoweredState(false);
            return;
        }

        progress++;
        setState(ProcessingState.RUNNING);
        updatePoweredState(true);
        if (progress >= recipe.processingTime()) {
            completeRecipe(recipe, input);
        }
        saveChanges();
    }

    private MatterFabricationRecipeInput snapshotInput() {
        var stacks = new ArrayList<ItemStack>(INPUT_SLOTS);
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            stacks.add(inventory.getStackInSlot(slot).copy());
        }
        return new MatterFabricationRecipeInput(stacks);
    }

    private boolean consumePower(double amount) {
        if (amount <= 0.0) {
            return true;
        }
        var grid = getMainNode().getGrid();
        if (grid == null) {
            return false;
        }
        var energy = grid.getEnergyService();
        double simulated = energy.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        if (simulated < amount - 0.01) {
            return false;
        }
        return energy.extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.CONFIG) >= amount - 0.01;
    }

    private boolean outputsFit(List<ItemStack> results) {
        var slots = new ArrayList<ItemStack>(OUTPUT_SLOTS);
        for (int index = 0; index < OUTPUT_SLOTS; index++) {
            slots.add(inventory.getStackInSlot(INPUT_SLOTS + index).copy());
        }
        for (var result : results) {
            int remaining = result.getCount();
            for (int slot = 0; slot < slots.size() && remaining > 0; slot++) {
                var existing = slots.get(slot);
                if (existing.isEmpty()) {
                    int inserted = Math.min(remaining, result.getMaxStackSize());
                    var copy = result.copyWithCount(inserted);
                    slots.set(slot, copy);
                    remaining -= inserted;
                } else if (ItemStack.isSameItemSameComponents(existing, result)) {
                    int inserted = Math.min(remaining, existing.getMaxStackSize() - existing.getCount());
                    existing.grow(inserted);
                    remaining -= inserted;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    private void completeRecipe(MatterFabricationRecipe recipe, MatterFabricationRecipeInput input) {
        int[] consumption = recipe.consumptionPlan(input);
        if (consumption == null || !outputsFit(recipe.resultCopies())) {
            resetProgress();
            return;
        }
        progress = 0;
        activeRecipeId = null;
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            if (consumption[slot] > 0) {
                inventory.extractItem(slot, consumption[slot], false);
            }
        }
        for (var result : recipe.resultCopies()) {
            var remainder = getOutputInventory().addItems(result);
            if (!remainder.isEmpty()) {
                // Output space was simulated immediately before insertion, so this is
                // only a defensive fallback for concurrent capability access.
                net.minecraft.world.Containers.dropItemStack(level,
                        worldPosition.getX() + 0.5, worldPosition.getY() + 1.0,
                        worldPosition.getZ() + 0.5, remainder);
            }
        }
        markForClientUpdate();
    }

    private void resetProgress() {
        progress = 0;
        activeRecipeId = null;
        currentProcessingTime = 0;
        currentAePerTick = 0.0;
    }

    private void setState(ProcessingState next) {
        if (processingState != next) {
            processingState = next;
            markForClientUpdate();
        }
    }

    private void updatePoweredState(boolean powered) {
        var state = getBlockState();
        if (state.hasProperty(BlockStateProperties.POWERED)
                && state.getValue(BlockStateProperties.POWERED) != powered) {
            level.setBlock(worldPosition, state.setValue(BlockStateProperties.POWERED, powered), 3);
            markForClientUpdate();
        }
    }

    public boolean isStructureFormed() {
        return structureFormed;
    }

    public boolean isNetworkOnline() {
        return structureFormed && getMainNode().isActive();
    }

    public boolean isClientStructureFormed() {
        return clientStructureFormed;
    }

    public boolean isClientRunning() {
        return clientRunning;
    }

    public MatterFabricationStructure.Inspection getInspection() {
        return inspection;
    }

    public int getProgress() {
        return progress;
    }

    public int getCurrentProcessingTime() {
        return currentProcessingTime;
    }

    public double getCurrentAePerTick() {
        return currentAePerTick;
    }

    public ProcessingState getProcessingState() {
        return processingState;
    }

    public boolean isBuilding() {
        return building;
    }

    public int getBuildCursor() {
        return buildCursor;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(PROGRESS_TAG, progress);
        if (activeRecipeId != null) {
            tag.putString(ACTIVE_RECIPE_TAG, activeRecipeId.toString());
        }
        tag.putBoolean(BUILDING_TAG, building);
        tag.putInt(BUILD_CURSOR_TAG, buildCursor);
        if (buildOwner != null) {
            tag.putUUID(BUILD_OWNER_TAG, buildOwner);
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        progress = Math.max(0, tag.getInt(PROGRESS_TAG));
        activeRecipeId = ResourceLocation.tryParse(tag.getString(ACTIVE_RECIPE_TAG));
        building = tag.getBoolean(BUILDING_TAG);
        buildCursor = Math.max(0, Math.min(MatterFabricationStructure.parts().size(),
                tag.getInt(BUILD_CURSOR_TAG)));
        buildOwner = tag.hasUUID(BUILD_OWNER_TAG) ? tag.getUUID(BUILD_OWNER_TAG) : null;
    }

    @Override
    protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(structureFormed);
        data.writeBoolean(processingState == ProcessingState.RUNNING);
    }

    @Override
    protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean nextFormed = data.readBoolean();
        boolean nextRunning = data.readBoolean();
        changed |= clientStructureFormed != nextFormed || clientRunning != nextRunning;
        clientStructureFormed = nextFormed;
        clientRunning = nextRunning;
        return changed;
    }

    public enum ProcessingState {
        IDLE,
        STRUCTURE_INCOMPLETE,
        NETWORK_OFFLINE,
        NO_RECIPE,
        OUTPUT_BLOCKED,
        WAITING_POWER,
        BUILDING,
        RUNNING
    }
}
