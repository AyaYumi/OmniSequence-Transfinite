package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.me.helpers.PlayerSource;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocators;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.registry.TaixuContent;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.*;

/** Construction terminal. Production modules are intentionally not represented as working machines. */
public final class TaixuBlockEntity extends AENetworkedInvBlockEntity {
    public enum Operation { IDLE, BUILD, DISMANTLE }
    public enum Status { IDLE, BUILDING, DISMANTLING, PAUSED, MATERIAL, CONFLICT, UNLOADED,
        OWNER_OFFLINE, PROTECTED, STORAGE_FULL, OUT_OF_BOUNDS, COMPLETE, VERSION_CHANGED }
    private final AppEngInternalInventory recovery = new AppEngInternalInventory(this, 1);
    private final TaixuMotionState motion = new TaixuMotionState(this);
    private Operation operation = Operation.IDLE;
    private Status status = Status.IDLE;
    private UUID owner;
    private Direction operationFacing = Direction.NORTH;
    private boolean paused, formed, scanRequested;
    private int cursor;
    private int structureVersion = TaixuStructure.VERSION;
    private boolean embedRequested;
    private long nextInspection;
    private DismantlePlan dismantle;
    private BlockPos problem;
    private TaixuStructure.Type neededMaterial;
    private TaixuStructure.Inspection inspection = new TaixuStructure.Inspection(
            0, 0, 0, TaixuStructure.parts().size(), Map.of(), null);

    public TaixuBlockEntity(BlockPos pos, BlockState state) {
        super(TaixuContent.CONTROLLER_BE.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(4);
    }
    @Override protected Item getItemFromBlockEntity() { return TaixuContent.CONTROLLER.get().asItem(); }
    @Override public EnumSet<Direction> getGridConnectableSides(BlockOrientation orientation) { return EnumSet.allOf(Direction.class); }
    @Override public InternalInventory getInternalInventory() { return recovery; }
    @Override protected InternalInventory getExposedInventoryForSide(Direction side) { return InternalInventory.empty(); }
    @Override public void onChangeInventory(AppEngInternalInventory inv, int slot) { saveChanges(); }
    @Override public void onReady() { super.onReady(); scanRequested |= formed; nextInspection = 0; }
    public void openMenu(Player player) {
        if (player instanceof ServerPlayer server && operation == Operation.IDLE && motion.hasPortable()) setRecoveryOwner(server);
        MenuOpener.open(TaixuContent.MENU.get(), player, MenuLocators.forBlockEntity(this));
    }
    public TaixuMotionState motion() { return motion; }
    public int structureVersion() { return structureVersion; }
    public boolean embedRequested() { return embedRequested; }
    public List<TaixuStructure.Part> structureParts() { return TaixuStructure.parts(structureVersion); }
    public BlockPos worldPos(BlockPos local) { return TaixuStructure.worldPos(worldPosition, facing(), local, structureVersion); }
    public BlockPos worldPos(TaixuStructure.Part part) { return worldPos(part.pos()); }
    public void setRecoveryOwner(ServerPlayer player) { owner = player.getUUID(); saveChanges(); }
    public void scheduleInspection() { nextInspection = 0; }
    public Direction facing() { return getBlockState().getValue(HorizontalDirectionalBlock.FACING); }
    public boolean formed() { return formed; }
    public boolean networkOnline() { return getMainNode().isActive(); }
    public Operation operation() { return operation; }
    public Status status() { return status; }
    public boolean paused() { return paused; }
    public BlockPos problem() { return problem; }
    public TaixuStructure.Type neededMaterial() { return neededMaterial; }
    public TaixuStructure.Inspection inspection() { return inspection; }
    public int progress() { return operation == Operation.DISMANTLE && dismantle != null ? dismantle.completed() : cursor; }
    public int operationTotal() { return operation == Operation.DISMANTLE && dismantle != null ? dismantle.total() : structureParts().size(); }
    public Set<ChunkPos> getChunkLoadingChunks() {
        if (motion.hasBodies()) return MultiblockChunkLoading.rectangle(
                worldPos(new BlockPos(-50, 0, -50)), worldPos(new BlockPos(50, 128, 50)));
        if (operation == Operation.DISMANTLE && dismantle != null) return dismantle.remainingChunks();
        return operation != Operation.IDLE || formed || scanRequested ? TaixuStructure.chunks(worldPosition, facing(), structureVersion) : Set.of();
    }
    public boolean canManage(ServerPlayer player) {
        return !isRemoved() && player.level() == level && player.mayBuild()
                && player.distanceToSqr(worldPosition.getCenter()) <= 64
                && level.mayInteract(player, worldPosition);
    }
    public void requestInspection(ServerPlayer player) {
        if (!canManage(player)) return;
        scanRequested = true; nextInspection = 0;
    }
    public void startBuild(ServerPlayer player) {
        if (!canManage(player) || operation != Operation.IDLE || motion.hasBodies() || motion.hasPortable() || embedRequested) return;
        if (!TaixuStructure.fits(level, worldPosition, facing(), structureVersion)) { status = Status.OUT_OF_BOUNDS; return; }
        operation = Operation.BUILD; owner = player.getUUID(); operationFacing = facing();
        cursor = 0; paused = false; problem = null; status = Status.BUILDING; saveChanges();
    }
    public void togglePause(ServerPlayer player) {
        if (!canManage(player) || operation == Operation.IDLE
                || !player.getUUID().equals(owner) && !player.hasPermissions(2)) return;
        paused = !paused;
        if (!paused) owner = player.getUUID();
        status = paused ? Status.PAUSED : operation == Operation.BUILD ? Status.BUILDING : Status.DISMANTLING;
        saveChanges();
    }
    public void cancel(ServerPlayer player) {
        if (!canManage(player) || !player.getUUID().equals(owner) && !player.hasPermissions(2)) return;
        operation = Operation.IDLE; paused = false; dismantle = null; embedRequested = false; status = Status.IDLE;
        nextInspection = 0; saveChanges();
    }
    public void startDismantle(ServerPlayer player) {
        if (!canManage(player) || operation != Operation.IDLE || embedRequested) return;
        if (motion.hasBodies()) { motion.dock(player, true); return; }
        beginDismantle(player);
    }
    void beginDismantle(ServerPlayer player) {
        if (player.level() != level || !player.mayBuild()) return;
        refreshInspection();
        if (inspection.unloaded() > 0) { scanRequested = true; status = Status.UNLOADED; return; }
        var entries = new ArrayList<DismantlePlan.Entry>();
        for (var part : structureParts()) {
            if (TaixuStructure.isController(part)) continue;
            var pos = worldPos(part);
            if (TaixuStructure.matches(level.getBlockState(pos), part, facing()))
                entries.add(new DismantlePlan.Entry(pos, TaixuStructure.block(part.type())));
        }
        dismantle = DismantlePlan.create(entries);
        operation = Operation.DISMANTLE; owner = player.getUUID(); operationFacing = facing();
        paused = false; status = Status.DISMANTLING; problem = null; saveChanges();
    }
    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        if (level.getGameTime() % 20 == 0 || nextInspection == 0) MultiblockChunkLoading.maintain(this);
        if (level.getGameTime() >= nextInspection) refreshInspection();
        motion.serverTick();
        if (embedRequested && !motion.hasBodies()) {
            var relocatingPlayer = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
            if (relocatingPlayer != null && canManage(relocatingPlayer)) {
                embedRequested = false;
                if (structureVersion == TaixuStructure.LEGACY_VERSION) TaixuControllerEmbedding.move(this, relocatingPlayer);
                else TaixuSuspendedUpgrade.begin(this, relocatingPlayer);
                if (isRemoved()) return;
                saveChanges(); markForClientUpdate();
            }
        }
        if (operation == Operation.IDLE && recovery.isEmpty() && !motion.hasPortable()) return;
        var player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        if (player == null || player.level() != level || !player.mayBuild()) {
            if (!paused && operation != Operation.IDLE) status = Status.OWNER_OFFLINE;
            return;
        }
        flushRecovery(player);
        if (operation != Operation.BUILD && recovery.isEmpty() && motion.hasPortable()) { recovery.setItemDirect(0, motion.nextPortableStack()); flushRecovery(player); }
        if (operation == Operation.IDLE || paused) return;
        if (operationFacing != facing()) { paused = true; status = Status.CONFLICT; saveChanges(); return; }
        // Retry environmental/material waits once per second, not every tick.
        if (status != Status.BUILDING && status != Status.DISMANTLING && level.getGameTime() % 20 != 0) return;
        if (!recovery.isEmpty()) { status = Status.STORAGE_FULL; return; }
        if (operation == Operation.BUILD) build(player); else dismantle(player);
    }
    private void refreshInspection() {
        inspection = TaixuStructure.inspect(level, worldPosition, facing(), motion::owns, structureVersion);
        boolean value = inspection.formed();
        if (formed != value) { formed = value; markForClientUpdate(); saveChanges(); }
        if (inspection.unloaded() == 0) scanRequested = false;
        nextInspection = level.getGameTime() + 40;
    }
    boolean allowed(ServerPlayer player, BlockPos pos) {
        return !level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.mayInteract(player, pos) && player.mayUseItemAt(pos, Direction.UP, ItemStack.EMPTY);
    }
    private void waitAt(Status reason, BlockPos pos) { status = reason; problem = pos; saveChanges(); }
    private void build(ServerPlayer player) {
        if (!TaixuStructure.fits(level, worldPosition, operationFacing, structureVersion)) { waitAt(Status.OUT_OF_BOUNDS, worldPosition); return; }
        status = Status.BUILDING; neededMaterial = null; problem = null;
        int placed = 0, visited = 0;
        while (cursor < structureParts().size() && placed < 64 && visited++ < 512) {
            var part = structureParts().get(cursor);
            if (TaixuStructure.isController(part)) { cursor++; continue; }
            var pos = TaixuStructure.worldPos(worldPosition, operationFacing, part, structureVersion);
            if (!level.hasChunkAt(pos)) { waitAt(Status.UNLOADED, pos); return; }
            var current = level.getBlockState(pos);
            if (TaixuStructure.matches(current, part, operationFacing)) { cursor++; continue; }
            if (!current.canBeReplaced() || current.hasBlockEntity()) { waitAt(Status.CONFLICT, pos); return; }
            if (!allowed(player, pos)) { waitAt(Status.PROTECTED, pos); return; }
            var material = new ItemStack(TaixuStructure.block(part.type()));
            if (!takeMaterial(player, material, part.type())) { neededMaterial = part.type(); waitAt(Status.MATERIAL, pos); return; }
            var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
            var expected = TaixuStructure.state(part, operationFacing);
            boolean changed = level.setBlock(pos, expected, Block.UPDATE_CLIENTS);
            boolean rejected = changed && NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                    snapshot, current, player)).isCanceled();
            if (!changed || rejected) {
                if (changed) snapshot.restore();
                if (!player.getAbilities().instabuild) recovery.setItemDirect(0, material);
                waitAt(rejected ? Status.PROTECTED : Status.CONFLICT, pos); return;
            }
            level.updateNeighborsAt(pos, expected.getBlock());
            cursor++; placed++;
        }
        if (cursor >= structureParts().size()) {
            refreshInspection();
            if (inspection.unloaded() > 0) { waitAt(Status.UNLOADED, inspection.problem()); return; }
            if (!inspection.formed()) {
                if (inspection.missing() > 0) cursor = 0;
                waitAt(Status.CONFLICT, inspection.problem()); return;
            }
            finish();
        }
        saveChanges();
    }
    private void dismantle(ServerPlayer player) {
        status = Status.DISMANTLING; problem = null;
        for (int budget = 0; budget < 64 && dismantle != null && !dismantle.isComplete(); budget++) {
            var entry = dismantle.current(); var pos = entry.pos();
            if (!level.hasChunkAt(pos)) { waitAt(Status.UNLOADED, pos); return; }
            var current = level.getBlockState(pos);
            if (!entry.matches(current)) { dismantle.advance(); continue; }
            // Revalidate the authored state too: a modified double slab must never be erased for one item.
            // Membership is bound to a saved exact position and block; states are checked against the blueprint below.
            var part = partAt(pos);
            if (part == null || !TaixuStructure.matches(current, part, operationFacing)) { dismantle.advance(); continue; }
            if (!allowed(player, pos) || NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, current, player)).isCanceled()) {
                waitAt(Status.PROTECTED, pos); return;
            }
            if (!recovery.isEmpty()) { waitAt(Status.STORAGE_FULL, pos); return; }
            if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) { waitAt(Status.CONFLICT, pos); return; }
            if (!player.getAbilities().instabuild) recovery.setItemDirect(0, new ItemStack(entry.block()));
            dismantle.advance(); saveChanges(); flushRecovery(player);
            if (!recovery.isEmpty()) { waitAt(Status.STORAGE_FULL, pos); return; }
        }
        if (dismantle == null || dismantle.isComplete()) finish();
        saveChanges();
    }
    private TaixuStructure.Part partAt(BlockPos pos) {
        return TaixuStructure.partAt(worldPosition, operationFacing, pos, structureVersion);
    }
    private boolean takeMaterial(ServerPlayer player, ItemStack template, TaixuStructure.Type type) {
        if (player.getAbilities().instabuild) return true;
        if (motion.takePortable(type)) return true;
        for (var stack : player.getInventory().items) if (ItemStack.isSameItemSameComponents(stack, template)) {
            stack.shrink(1); player.getInventory().setChanged(); return true;
        }
        var grid = getMainNode().getGrid();
        return networkOnline() && grid != null && grid.getStorageService().getInventory().extract(
                AEItemKey.of(template), 1, Actionable.MODULATE, new PlayerSource(player)) == 1;
    }
    private void flushRecovery(ServerPlayer player) {
        var stack = recovery.getStackInSlot(0).copy();
        if (stack.isEmpty()) return;
        var grid = getMainNode().getGrid();
        if (networkOnline() && grid != null) stack.shrink((int) grid.getStorageService().getInventory().insert(
                AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, new PlayerSource(player)));
        if (!stack.isEmpty()) player.getInventory().add(stack);
        recovery.setItemDirect(0, stack); saveChanges();
    }
    private void finish() {
        operation = Operation.IDLE; paused = false; status = Status.COMPLETE; problem = null;
        nextInspection = 0; saveChanges();
    }
    @Override public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("taixuMotion", motion.save());
        tag.putInt("taixuVersion", structureVersion); tag.putInt("taixuLayout", structureVersion);
        tag.putBoolean("taixuEmbedRequested", embedRequested); tag.putString("operation", operation.name());
        tag.putInt("cursor", cursor); tag.putBoolean("paused", paused); tag.putBoolean("formed", formed);
        tag.putString("operationFacing", operationFacing.getName());
        if (owner != null) tag.putUUID("owner", owner);
        if (dismantle != null && operation == Operation.DISMANTLE) tag.put("dismantle", dismantle.save());
    }
    @Override public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        tag = RetainedBlockContents.unpack(tag);
        super.loadTag(tag, registries);
        int storedLayout = tag.contains("taixuLayout") ? tag.getInt("taixuLayout") : tag.getInt("taixuVersion");
        structureVersion = storedLayout == TaixuStructure.LEGACY_VERSION || storedLayout == TaixuStructure.EMBEDDED_VERSION
                ? storedLayout : TaixuStructure.VERSION;
        embedRequested = structureVersion < TaixuStructure.VERSION && tag.getBoolean("taixuEmbedRequested");
        if (tag.contains("taixuPortableMaterials")) motion.loadPortable(tag.getIntArray("taixuPortableMaterials"));
        else if (tag.contains("taixuMotion")) motion.load(tag.getCompound("taixuMotion"));
        try { operation = Operation.valueOf(tag.getString("operation")); } catch (IllegalArgumentException ignored) { operation = Operation.IDLE; }
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        operationFacing = Direction.byName(tag.getString("operationFacing"));
        if (operationFacing == null || operationFacing.getAxis().isVertical()) operationFacing = facing();
        cursor = Math.clamp(tag.getInt("cursor"), 0, structureParts().size());
        paused = tag.getBoolean("paused"); formed = tag.getBoolean("formed");
        dismantle = tag.contains("dismantle") ? DismantlePlan.load(tag.getCompound("dismantle")) : null;
        if (operation == Operation.DISMANTLE && dismantle == null) operation = Operation.IDLE;
        if (operation != Operation.IDLE && tag.getInt("taixuVersion") != structureVersion) {
            operation = Operation.IDLE; status = Status.VERSION_CHANGED;
        } else status = paused ? Status.PAUSED : Status.IDLE;
        nextInspection = 0;
    }
    public boolean hasRemovalRecovery() { return !recovery.isEmpty() || motion.hasBodies() || motion.hasPortable(); }
    @Override public void addAdditionalDrops(net.minecraft.world.level.Level level, BlockPos pos, List<ItemStack> drops) {
        if (!hasRemovalRecovery()) return;
        var data = new CompoundTag(); super.saveAdditional(data, level.registryAccess());
        var portable = new CompoundTag(); portable.put("inv", data.getCompound("inv"));
        portable.putIntArray("taixuPortableMaterials", motion.portableCounts());
        drops.add(RetainedBlockContents.createDrop(this, portable));
    }
    @Override public void clearContent() { super.clearContent(); motion.clear(); }
    @Override protected void writeToStream(RegistryFriendlyByteBuf data) {
        super.writeToStream(data); data.writeBoolean(formed); data.writeVarInt(motion.mask());
        data.writeVarInt(structureVersion); data.writeBoolean(embedRequested);
    }
    @Override protected boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data); boolean previous = formed; formed = data.readBoolean();
        int previousMask = motion.mask(); motion.setClientMask(data.readVarInt());
        int previousVersion = structureVersion; structureVersion = data.readVarInt();
        boolean previousRequest = embedRequested; embedRequested = data.readBoolean();
        return changed || previous != formed || previousMask != motion.mask() || previousVersion != structureVersion || previousRequest != embedRequested;
    }

    public void requestEmbedding(ServerPlayer player) {
        if (!canManage(player) || operation != Operation.IDLE || structureVersion != TaixuStructure.LEGACY_VERSION
                || embedRequested || motion.mode() == TaixuMotionState.DOCKING || motion.hasPortable()) return;
        refreshInspection();
        if (!formed) { status = Status.CONFLICT; problem = inspection.problem(); return; }
        owner = player.getUUID(); embedRequested = true;
        if (motion.hasBodies()) motion.dock(player, false);
        saveChanges(); markForClientUpdate();
    }

    public void requestSuspendedUpgrade(ServerPlayer player) {
        if (!canManage(player) || operation != Operation.IDLE || structureVersion != TaixuStructure.EMBEDDED_VERSION
                || embedRequested || motion.mode() == TaixuMotionState.DOCKING || motion.hasPortable()) return;
        refreshInspection();
        if (!formed) { status = Status.CONFLICT; problem = inspection.problem(); return; }
        owner = player.getUUID(); embedRequested = true;
        if (motion.hasBodies()) motion.dock(player, false);
        saveChanges(); markForClientUpdate();
    }
    void beginSuspendedConstruction(ServerPlayer player, int[] refunds) {
        structureVersion = TaixuStructure.VERSION; embedRequested = false;
        motion.loadPortable(player.getAbilities().instabuild ? new int[refunds.length] : refunds);
        owner = player.getUUID(); operationFacing = facing(); cursor = 0; paused = false;
        operation = Operation.BUILD; status = Status.BUILDING; problem = null; neededMaterial = null;
        refreshInspection(); saveChanges(); markForClientUpdate();
    }

    void embeddingFailed(Status reason, BlockPos pos) { waitAt(reason, pos); markForClientUpdate(); }
    void embeddingComplete() { refreshInspection(); status = Status.COMPLETE; saveChanges(); markForClientUpdate(); }
}
