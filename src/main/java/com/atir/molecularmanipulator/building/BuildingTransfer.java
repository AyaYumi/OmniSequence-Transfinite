package com.atir.molecularmanipulator.building;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.helpers.IGridConnectedBlockEntity;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.registry.SingularityContent;
import com.atir.molecularmanipulator.diagnostics.RateLimitedLog;
import com.atir.molecularmanipulator.blockentity.*;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Server-owned write-ahead transfer journal. A sealed archive has exactly one deployment reservation. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class BuildingTransfer {
    private static final Map<MinecraftServer, Map<UUID, Operation>> OPERATIONS = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<BlockPos>> LOCKED = new WeakHashMap<>();
    private static final ThreadLocal<BlockPos> MUTATION = new ThreadLocal<>();
    private static final net.minecraft.server.level.TicketType<UUID> TRANSFER_TICKET =
            net.minecraft.server.level.TicketType.create("molecularmanipulator_building_transfer", UUID::compareTo);
    public static boolean mayMutate(BlockPos pos) { return pos.equals(MUTATION.get()); }
    private static void setBlock(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) throws IOException {
        MUTATION.set(pos);
        try { level.setBlock(pos, state, 2 | 16); } finally { MUTATION.remove(); }
        if (!level.getBlockState(pos).equals(state)) throw new IOException("Building transfer could not change block at " + pos);
    }
    private BuildingTransfer() {}
    private static final class Operation {
        final UUID id;
        CompoundTag manifest;
        final List<BuildingArchiveStore.Entry> layout;
        final ListTag descriptors;
        final int foundationEnd;
        final Set<net.minecraft.world.level.ChunkPos> chunks = new HashSet<>();
        int cursor, contentCursor, age, retryTicks;
        int blockedIndex = -1;
        String reportedError;
        Operation(UUID id, CompoundTag manifest, List<BuildingArchiveStore.Entry> layout, ListTag descriptors) {
            this.id = id; this.manifest = manifest; this.layout = layout; this.descriptors = descriptors;
            int minY = layout.stream().filter(entry -> !entry.state().isAir()).mapToInt(entry -> entry.offset().getY()).min().orElse(0);
            int end = 0;
            while (end < layout.size() && !layout.get(end).offset().equals(BlockPos.ZERO) && layout.get(end).offset().getY() <= minY + 1) end++;
            foundationEnd = end;
            if (manifest.getString("phase").equals("BLOCKED")) {
                int blocked = manifest.getInt("blocked_index");
                if (blocked >= 0 && blocked < layout.size()) blockedIndex = blocked;
            }
            // Replay from the beginning after a restart: chunk saves can lag the journal cursor.
        }
    }
    public static boolean locked(Level level, BlockPos pos) {
        return level instanceof ServerLevel server && LOCKED.getOrDefault(server, Set.of()).contains(pos);
    }
    public static boolean restoring(BlockEntity entity) { return entity.getLevel() != null && locked(entity.getLevel(), entity.getBlockPos()); }
    private static void message(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("message.molecularmanipulator.folding." + key, args), false);
    }
    public static void explainPackage(Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer server) || BuildingPackage.type(stack) == null) { message(player, "empty"); return; }
        UUID id = BuildingPackage.reservation(stack);
        if (id == null) id = BuildingPackage.archive(stack);
        if (id == null) {
            if (!BuildingType.orderable().contains(BuildingPackage.type(stack))) message(player, "retired_engine");
            else message(player, "new_package", Component.translatable(BuildingPackage.type(stack).translationKey()));
            return;
        }
        try {
            var store = BuildingArchiveStore.forServer(server.server);
            if (!store.exists(id)) { message(player, "missing_archive"); return; }
            var manifest = store.readManifest(id);
            message(player, "archive_status", manifest.getInt("blocks"), Component.translatable("folding.state." + manifest.getString("state")));
        } catch (IOException error) { fail(player, error); }
    }
    public static List<BuildingArchiveStore.Entry> layout(ServerPlayer player, ItemStack stack) throws IOException {
        var type = BuildingPackage.type(stack);
        if (type == null) throw new IOException("Package has no building template");
        var data = BuildingPackage.data(stack);
        if (data.getInt("template") != BuildingType.TEMPLATE_VERSION) throw new IOException("Unsupported building template version");
        UUID id = BuildingPackage.archive(stack);
        if (id == null) {
            if (!BuildingType.orderable().contains(type)) throw new IOException("Retired engine template: order a star orrery L1-L3 package instead");
            return type.template();
        }
        var store = BuildingArchiveStore.forServer(player.server);
        if (!store.exists(id)) throw new IOException("Archive is absent from this world");
        var manifest = store.readManifest(id);
        requireSealed(manifest, type);
        return store.readLayout(id, player.registryAccess());
    }
    static void requireSealed(CompoundTag manifest, BuildingType type) throws IOException {
        if (!manifest.getString("state").equals("SEALED")) throw new IOException("Archive is already reserved or deployed");
        if (!manifest.getString("type").equals(type.id) || manifest.getInt("template") != BuildingType.TEMPLATE_VERSION)
            throw new IOException("Package type or template differs from its archive");
    }
    public static BlockPos controllerAtBase(List<BuildingArchiveStore.Entry> layout, BlockPos base) {
        int minY = layout.stream().filter(entry -> !entry.state().isAir()).mapToInt(entry -> entry.offset().getY()).min().orElse(0);
        return base.above(-minY);
    }
    public record SiteSurvey(List<BlockPos> conflicts, int obstructions, int outsideHeight, int restricted, int minY, int maxY) {
        public Component summary() {
            return conflicts.isEmpty() ? Component.translatable("star_chart.molecularmanipulator.site_clear")
                    : Component.translatable("star_chart.molecularmanipulator.site_conflicts", obstructions, outsideHeight, restricted);
        }
        public Component detail() {
            var detail = summary().copy().append("\n").append(Component.translatable(
                    "star_chart.molecularmanipulator.height_range", minY, maxY - 1));
            if (!conflicts.isEmpty()) detail.append("\n").append(Component.translatable(
                    "star_chart.molecularmanipulator.first_conflict", conflicts.getFirst().toShortString()));
            return detail;
        }
    }
    public static SiteSurvey survey(ServerPlayer player, List<BuildingArchiveStore.Entry> layout,
            BlockPos controller, Direction facing) {
        var level = player.serverLevel(); var rotation = BuildingType.rotation(facing); var conflicts = new ArrayList<BlockPos>();
        int obstructions = 0, outsideHeight = 0, restricted = 0;
        for (var entry : layout) {
            var pos = controller.offset(entry.offset().rotate(rotation));
            // Check the dimension's real build range BEFORE reading state: out-of-range reads return VOID_AIR.
            if (level.isOutsideBuildHeight(pos)) { conflicts.add(pos); outsideHeight++; continue; }
            if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos) || locked(level, pos)) {
                conflicts.add(pos); restricted++; continue;
            }
            var current = level.getBlockState(pos);
            // All native air variants are empty. Blueprint AIR is a required empty cell, not an ignored wildcard.
            if ((!current.isAir() && !current.canBeReplaced()) || level.getBlockEntity(pos) != null) {
                conflicts.add(pos); obstructions++;
            }
        }
        return new SiteSurvey(List.copyOf(conflicts), obstructions, outsideHeight, restricted, level.getMinBuildHeight(), level.getMaxBuildHeight());
    }
    public static List<BlockPos> conflicts(ServerPlayer player, List<BuildingArchiveStore.Entry> layout, BlockPos controller, Direction facing) {
        return survey(player, layout, controller, facing).conflicts();
    }
    /** The item stays in its inventory slot until the destination and its contents are durably saved. */
    public static boolean deploy(ServerPlayer player, int slot, BlockPos base, Direction facing) {
        if (!player.mayBuild() || player.isSpectator()) return false;
        var stack = player.getInventory().getItem(slot);
        if (BuildingPackage.type(stack) == null || BuildingPackage.reservation(stack) != null) return false;
        if (BuildingPackage.archive(stack) == null && !BuildingType.orderable().contains(BuildingPackage.type(stack))) {
            message(player, "retired_engine"); return false;
        }
        try {
            var layout = layout(player, stack); var controller = controllerAtBase(layout, base);
            var site = survey(player, layout, controller, facing);
            if (!site.conflicts().isEmpty()) { player.displayClientMessage(site.detail(), false); return false; }
            var store = BuildingArchiveStore.forServer(player.server); var id = BuildingPackage.archive(stack);
            boolean fresh = id == null;
            if (fresh) id = UUID.randomUUID();
            var manifest = fresh ? new CompoundTag() : store.readManifest(id);
            if (!fresh) {
                // Verify every native stream before reserving the item or changing the destination.
                var descriptors = store.readIndex(id).getList("entries", Tag.TAG_COMPOUND);
                for (int i = 0; i < descriptors.size(); i++) if (descriptors.getCompound(i).getInt("chunks") > 0)
                    store.readEntry(id, i, descriptors.getCompound(i), player.registryAccess());
            }
            manifest.putString("type", BuildingPackage.type(stack).id);
            manifest.putInt("template", BuildingType.TEMPLATE_VERSION);
            manifest.putBoolean("fresh", fresh);
            manifest.putUUID("owner", player.getUUID());
            if (fresh) {
                manifest.putInt("ae_owner", appeng.api.features.IPlayerRegistry.getPlayerId(player));
                var team = com.atir.molecularmanipulator.integration.FtbTeamOwnership.forPlayer(player);
                if (team != null) manifest.putUUID("placement_team", team);
            }
            manifest.putString("target_dimension", player.level().dimension().location().toString());
            manifest.putLong("target", controller.asLong()); manifest.putString("facing", facing.getName());
            manifest.putInt("blocks", (int) layout.stream().filter(entry -> !entry.state().isAir()).count());
            if (fresh) store.writeSnapshot(id, layout, player.registryAccess());
            // Persist a tagged reservation before the journal authorizes any placement.
            BuildingPackage.reserve(stack, id); BuildingTransferPersistence.savePlayer(player);
            manifest.putString("state", "DEPLOYING"); manifest.putString("phase", "BLOCKS");
            manifest.putInt("cursor", 0); manifest.putInt("content_cursor", 0); manifest.remove("blocked_index");
            store.writeManifest(id, manifest);
            add(player.server, id, manifest, layout, store.readIndex(id).getList("entries", Tag.TAG_COMPOUND));
            message(player, "deploy_started"); return true;
        } catch (IOException | RuntimeException error) { fail(player, error); return false; }
    }
    public static boolean seal(ServerPlayer player, BlockEntity controller) {
        var type = BuildingType.forController(controller);
        if (type == null || controller.getLevel() != player.serverLevel() || !player.mayBuild() || player.isSpectator()
                || controller.isRemoved() || locked(player.level(), controller.getBlockPos())) return false;
        try {
            if (controller instanceof SingularityBlockEntity singularity) {
                if (!singularity.canManage(player)) return false;
                if (singularity.motion().hasBodies()) return singularity.requestPackageReclaim(player);
                if (!singularity.canSealPackage()) { message(player, "busy"); return false; }
            }
            if (controller instanceof MatterFabricationBlockEntity well && (well.isBuilding() || well.isDismantling() || well.isUpdatingStructure()
                    || well.getResearchOrders().activeJobs() > 0)) { message(player, "busy"); return false; }
            if (controller instanceof MolecularCenterBlockEntity array && (array.isBuilding() || array.isDismantling() || array.isStructureUpdating())) { message(player, "busy"); return false; }
            if (controller instanceof OmniComputationCoreBlockEntity omni && (omni.isBuilding() || omni.isDismantling() || omni.isStructureUpdating()
                    || omni.getActiveJobCount() > 0 || omni.getActiveMaterialCalculations() > 0)) { message(player, "busy"); return false; }
            if (controller instanceof IGridConnectedBlockEntity networked && networked.getMainNode().getGrid() != null
                    && networked.getMainNode().getGrid().getCraftingService().getCpus().stream().anyMatch(ICraftingCPU::isBusy)) {
                message(player, "busy"); return false;
            }
            var level = player.serverLevel(); var origin = controller.getBlockPos(); var facing = BuildingType.facing(controller.getBlockState());
            var candidates = new LinkedHashSet<BlockPos>();
            var air = new LinkedHashSet<BlockPos>();
            if (controller instanceof StarComputationEngineCoreBlockEntity engine) {
                if (!engine.canInteract(player)) return false;
                // Resolve the fixed L0-L9 footprint before classifying; an unloaded ring must not
                // silently downgrade the package or disappear from the captured membership.
                for (var chunk : StarOrreryRings.chunksFor(origin, facing, StarOrreryRings.RINGS)) level.getChunk(chunk.x, chunk.z);
                engine.refreshStructureNow();
                type = BuildingType.forController(engine);
                candidates.addAll(engine.foldingMembers());
            } else if (controller instanceof SingularityBlockEntity singularity) {
                for (var part : singularity.structureParts()) candidates.add(singularity.worldPos(part));
                for (var local : SingularityStructure.requiredAir()) {
                    var pos = singularity.worldPos(local); candidates.add(pos); air.add(pos);
                }
                for (var local : SingularityStructure.compatibilityCollectionNodePositions(singularity.structureVersion())) {
                    var pos = singularity.worldPos(local);
                    level.getChunkAt(pos);
                    if (level.getBlockState(pos).is(SingularityContent.COLLECTION_NODE.get())) candidates.add(pos);
                }
            } else if (controller instanceof MolecularCenterBlockEntity array) {
                array.refreshStructure();
                var geometry = array.getConstructionOriginLayout();
                for (var part : MolecularCenterStructure.parts(geometry)) {
                    var pos = MolecularCenterStructure.worldPos(origin, facing, part, geometry);
                    candidates.add(pos); if (part.partType() == MolecularCenterStructure.PartType.AIR) air.add(pos);
                }
            } else if (controller instanceof OmniComputationCoreBlockEntity omni) {
                omni.refreshStructureNow();
                var geometry = omni.getFoldingLayout();
                for (var part : OmniComputationStructure.parts(geometry)) {
                    var pos = OmniComputationStructure.worldPos(origin, facing, part, geometry);
                    candidates.add(pos); if (part.type() == OmniComputationStructure.PartType.AIR) air.add(pos);
                }
            } else for (var entry : type.template()) {
                var pos = origin.offset(entry.offset().rotate(BuildingType.rotation(facing)));
                candidates.add(pos); if (entry.state().isAir()) air.add(pos);
            }
            candidates.add(origin);
            for (var pos : candidates) {
                level.getChunkAt(pos);
                if (!level.mayInteract(player, pos) || locked(level, pos)) { message(player, "conflicts"); return false; }
            }
            player.closeContainer();
            if (controller instanceof OmniComputationCoreBlockEntity omni) omni.prepareRemovalRecovery();
            // Saving this first synchronizes all 14 crystal shards before any crystal is captured.
            var controllerData = controller.saveWithFullMetadata(level.registryAccess());
            var rotation = BuildingType.rotation(facing);
            var inverse = switch (rotation) { case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90; case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90; default -> rotation; };
            var entries = new ArrayList<BuildingArchiveStore.Entry>();
            for (var pos : candidates) {
                var offset = pos.subtract(origin).rotate(inverse);
                if (air.contains(pos)) { entries.add(new BuildingArchiveStore.Entry(offset, Blocks.AIR.defaultBlockState(), null)); continue; }
                var state = level.getBlockState(pos);
                if (state.isAir()) continue;
                var normalized = BuildingType.rotateState(state, inverse);
                if (!pos.equals(origin) && !isMember(type, offset, normalized, controller)) continue;
                entries.add(new BuildingArchiveStore.Entry(offset, normalized, null));
            }
            entries.sort(Comparator.comparingInt((BuildingArchiveStore.Entry entry) -> entry.offset().equals(BlockPos.ZERO) ? 1 : 0)
                    .thenComparingInt(entry -> entry.offset().getY()).thenComparingInt(entry -> entry.offset().getX()).thenComparingInt(entry -> entry.offset().getZ()));
            var id = UUID.randomUUID(); var store = BuildingArchiveStore.forServer(player.server);
            // Freeze each component through its native serializer, but keep only one component's NBT at a time.
            store.writeSnapshot(id, entries.size(), index -> {
                var entry = entries.get(index);
                var pos = origin.offset(entry.offset().rotate(rotation));
                var entity = entry.state().isAir() ? null : level.getBlockEntity(pos);
                var data = pos.equals(origin) ? controllerData : entity == null ? null : entity.saveWithFullMetadata(level.registryAccess());
                return new BuildingArchiveStore.Entry(entry.offset(), entry.state(), data == null ? null : rotatePorts(data, inverse));
            }, level.registryAccess());
            var manifest = new CompoundTag(); manifest.putString("state", "SEALING"); manifest.putString("type", type.id);
            manifest.putInt("template", BuildingType.TEMPLATE_VERSION); manifest.putInt("blocks", (int) entries.stream().filter(entry -> !entry.state().isAir()).count());
            manifest.putUUID("owner", player.getUUID()); manifest.putString("source_dimension", level.dimension().location().toString());
            manifest.putLong("source", origin.asLong()); manifest.putString("source_facing", facing.getName()); manifest.putLong("sealed_tick", level.getGameTime());
            store.writeManifest(id, manifest);
            add(player.server, id, manifest, entries.stream().map(entry -> new BuildingArchiveStore.Entry(entry.offset(), entry.state(), null)).toList(),
                    store.readIndex(id).getList("entries", Tag.TAG_COMPOUND));
            // Source removal is a single server-thread operation; no inventory or machine tick can interleave.
            finishSealing(player.server, OPERATIONS.get(player.server).get(id));
            message(player, "sealed"); return true;
        } catch (IOException | RuntimeException error) { fail(player, error); return false; }
    }
    private static boolean isMember(BuildingType type, BlockPos offset, net.minecraft.world.level.block.state.BlockState state, BlockEntity controller) {
        return switch (type) {
            case FABRICATION_WELL -> {
                var part = new MatterFabricationStructure.Part(offset.getX(), offset.getY() + MatterFabricationStructure.CONTROLLER_Y,
                        offset.getZ() + MatterFabricationStructure.CONTROLLER_Z, MatterFabricationStructure.PartType.CASING);
                yield MatterFabricationStructure.isValidCurrentBlockAt(state, part);
            }
            case SEQUENCE_ARRAY -> MolecularCenterStructure.isStructurePart(state);
            case OMNI_COMPUTATION -> OmniComputationStructure.isStructurePart(state);
            case EVENT_HORIZON_SINGULARITY -> {
                var version = ((SingularityBlockEntity) controller).structureVersion();
                var part = SingularityStructure.partAt(BlockPos.ZERO, Direction.NORTH, offset, version);
                yield part != null && SingularityStructure.matches(state, part, Direction.NORTH)
                        || state.is(SingularityContent.COLLECTION_NODE.get())
                        && SingularityStructure.isCompatibilityCollectionNode(BlockPos.ZERO, Direction.NORTH, offset, version);
            }
            case STAR_COMPUTATION_ENGINE, STAR_ORRERY_L0, STAR_ORRERY_L1, STAR_ORRERY_L2, STAR_ORRERY_L3,
                    STAR_ORRERY_L4, STAR_ORRERY_L5, STAR_ORRERY_L6, STAR_ORRERY_L7, STAR_ORRERY_L8, STAR_ORRERY_L9 -> true;
        };
    }
    private static void add(MinecraftServer server, UUID id, CompoundTag manifest, List<BuildingArchiveStore.Entry> layout, ListTag descriptors) {
        var operation = new Operation(id, manifest, layout, descriptors);
        OPERATIONS.computeIfAbsent(server, ignored -> new LinkedHashMap<>()).put(id, operation);
        var level = operationLevel(server, manifest);
        if (level == null) return;
        var origin = BlockPos.of(manifest.getLong(manifest.getString("state").equals("SEALING") ? "source" : "target"));
        var rotation = BuildingType.rotation(direction(manifest.getString(manifest.getString("state").equals("SEALING") ? "source_facing" : "facing")));
        var locks = LOCKED.computeIfAbsent(level, ignored -> new HashSet<>());
        for (var entry : layout) {
            var pos = origin.offset(entry.offset().rotate(rotation));
            locks.add(pos);
            operation.chunks.add(new net.minecraft.world.level.ChunkPos(pos));
        }
        // Only in-flight transfers keep chunks loaded (at FULL, not force-ticking). A SEALED archive has no tickets.
        for (var chunk : operation.chunks) level.getChunkSource().addRegionTicket(TRANSFER_TICKET, chunk, 0, id);
        for (var entry : layout) {
            var pos = origin.offset(entry.offset().rotate(rotation));
            level.invalidateCapabilities(pos);
            if (manifest.getString("state").equals("SEALING") && level.getBlockEntity(pos) instanceof IGridConnectedBlockEntity networked)
                networked.getMainNode().destroy();
        }
    }
    private static Direction direction(String name) { var direction = Direction.byName(name); return direction == null ? Direction.NORTH : direction; }
    private static ServerLevel operationLevel(MinecraftServer server, CompoundTag manifest) {
        var id = manifest.getString(manifest.getString("state").equals("SEALING") ? "source_dimension" : "target_dimension");
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id)));
    }
    private static void finishSealing(MinecraftServer server, Operation operation) throws IOException {
        var manifest = operation.manifest; var level = operationLevel(server, manifest);
        if (level == null) throw new IOException("Source dimension unavailable");
        var origin = BlockPos.of(manifest.getLong("source")); var rotation = BuildingType.rotation(direction(manifest.getString("source_facing")));
        // Remove the controller first so neighbor callbacks cannot flush saved buffers into the ME network.
        var ordered = new ArrayList<>(operation.layout); ordered.sort(Comparator.comparingInt(entry -> entry.offset().equals(BlockPos.ZERO) ? 0 : 1));
        for (var entry : ordered) {
            if (entry.state().isAir()) continue;
            var pos = origin.offset(entry.offset().rotate(rotation)); level.getChunkAt(pos);
            if (level.getBlockState(pos).isAir()) continue;
            if (!level.getBlockState(pos).is(entry.state().getBlock())) throw new IOException("Source changed during sealing at " + pos);
            level.removeBlockEntity(pos);
            setBlock(level, pos, Blocks.AIR.defaultBlockState());
        }
        // Flush chunks before the archive becomes deployable. An older journal is always safe to replay.
        BuildingTransferPersistence.checkpoint(level, operation.layout, origin, rotation);
        operation.manifest = BuildingArchiveStore.forServer(server).updateManifest(operation.id, manifest, next -> next.putString("state", "SEALED"));
        unlock(server, operation, level, origin, rotation);
        deliver(server, operation.id, operation.manifest);
    }
    private static void deliver(MinecraftServer server, UUID id, CompoundTag manifest) throws IOException {
        if (manifest.getBoolean("delivered")) return;
        var player = server.getPlayerList().getPlayer(manifest.getUUID("owner"));
        if (player == null) return;
        // The delivery handle is also a locked receipt until BOTH inventory save and journal acknowledgement succeed.
        ItemStack handle = ItemStack.EMPTY;
        for (var stack : player.getInventory().items) if (id.equals(BuildingPackage.archive(stack))) { handle = stack; break; }
        if (handle.isEmpty()) {
            int slot = player.getInventory().getFreeSlot();
            if (slot < 0) { message(player, "inventory_full"); return; }
            handle = BuildingPackage.sealed(BuildingType.byId(manifest.getString("type")), id, manifest.getInt("blocks"));
            BuildingPackage.reserve(handle, id);
            player.getInventory().setItem(slot, handle);
        }
        BuildingTransferPersistence.savePlayer(player);
        BuildingArchiveStore.forServer(server).updateManifest(id, manifest, next -> next.putBoolean("delivered", true));
        BuildingPackage.clearReservation(handle);
        BuildingTransferPersistence.savePlayer(player);
    }
    /** Restart-safe recovery of a package that could not be delivered to a full/offline owner. */
    public static void collect(ServerPlayer player) {
        try {
            var store = BuildingArchiveStore.forServer(player.server);
            for (var id : store.listArchives()) {
                var manifest = store.readManifest(id);
                if (manifest.hasUUID("owner") && manifest.getUUID("owner").equals(player.getUUID()) && manifest.getString("state").equals("SEALED")) deliver(player.server, id, manifest);
            }
        } catch (IOException error) { fail(player, error); }
    }
    private static void unlock(MinecraftServer server, Operation operation, ServerLevel level, BlockPos origin, Rotation rotation) {
        var locks = LOCKED.get(level);
        if (locks != null) {
            for (var entry : operation.layout) locks.remove(origin.offset(entry.offset().rotate(rotation)));
            if (locks.isEmpty()) LOCKED.remove(level);
        }
        for (var chunk : operation.chunks) level.getChunkSource().removeRegionTicket(TRANSFER_TICKET, chunk, 0, operation.id);
        OPERATIONS.getOrDefault(server, Map.of()).remove(operation.id);
    }
    private static void deployTick(MinecraftServer server, Operation operation) throws IOException {
        var manifest = operation.manifest; var level = operationLevel(server, manifest);
        if (level == null) return;
        var origin = BlockPos.of(manifest.getLong("target")); var rotation = BuildingType.rotation(direction(manifest.getString("facing")));
        operation.age++;
        if (operation.age <= 40) {
            if (operation.age % 4 == 0) {
                // The footprint resolves as a star chart before the first physical block appears.
                int samples = Math.min(64, operation.layout.size());
                int floor = operation.layout.stream().mapToInt(entry -> entry.offset().getY()).min().orElse(0);
                for (int i = 0; i < samples; i++) {
                    var offset = operation.layout.get(i * operation.layout.size() / samples).offset().rotate(rotation);
                    double t = operation.age / 40D;
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                            origin.getX() + .5 + offset.getX() * t, origin.getY() + floor + .15,
                            origin.getZ() + .5 + offset.getZ() * t, 1, 0, 0, 0, 0);
                }
            }
            return;
        }
        int wallEnd = operation.layout.size() - 1;
        int stageEnd, budget;
        if (operation.age <= 72) { stageEnd = operation.foundationEnd; budget = Math.max(1, Math.ceilDiv(stageEnd, 32)); }
        else if (operation.age <= 132) { stageEnd = wallEnd; budget = Math.max(1, Math.ceilDiv(wallEnd - operation.foundationEnd, 60)); }
        else if (operation.age < 156) {
            double height = (156 - operation.age) / 24D * 8;
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4 + operation.age * .15;
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        origin.getX() + .5 + Math.cos(angle) * .7, origin.getY() + .5 + height,
                        origin.getZ() + .5 + Math.sin(angle) * .7, 1, 0, 0, 0, 0);
            }
            return;
        } else { stageEnd = operation.layout.size(); budget = 128; }
        int limit = Math.min(stageEnd, operation.cursor + Math.min(128, budget));
        for (; operation.cursor < limit; operation.cursor++) {
            var entry = operation.layout.get(operation.cursor); var pos = origin.offset(entry.offset().rotate(rotation));
            level.getChunkAt(pos);
            var state = BuildingType.rotateState(entry.state(), rotation);
            if (level.isOutsideBuildHeight(pos)) throw new IOException("Deployment exceeds dimension build height at " + pos.toShortString());
            var current = level.getBlockState(pos);
            if (!current.isAir() && !current.canBeReplaced() && !current.is(state.getBlock())) {
                operation.blockedIndex = operation.cursor;
                if (!manifest.getString("phase").equals("BLOCKED") || manifest.getInt("blocked_index") != operation.cursor) {
                    manifest.putString("phase", "BLOCKED"); manifest.putInt("blocked_index", operation.cursor);
                    manifest.putInt("cursor", operation.cursor);
                    BuildingArchiveStore.forServer(server).writeManifest(operation.id, manifest);
                }
                throw new IOException("Deployment obstructed at " + pos.toShortString());
            }
            operation.blockedIndex = -1; manifest.remove("blocked_index");
            if (!current.equals(state) && !(current.isAir() && state.isAir())) setBlock(level, pos, state);
            if (!state.isAir() && operation.cursor % 16 == 0) level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 1, .1, .1, .1, 0);
        }
        var store = BuildingArchiveStore.forServer(server);
        manifest.putInt("cursor", operation.cursor);
        if (operation.cursor < operation.layout.size()) {
            manifest.putString("phase", "BLOCKS"); store.writeManifest(operation.id, manifest); return;
        }
        // All blocks exist before native contents are loaded. Bound each tick by component count AND serialized bytes.
        // On restart both cursors replay from zero: region-file saves may lag the journal, and the site remains locked.
        int restored = 0; long bytes = 0;
        while (operation.contentCursor < operation.layout.size() && restored < 128 && (restored == 0 || bytes < 1024 * 1024)) {
            int i = operation.contentCursor;
            var descriptor = operation.descriptors.getCompound(i);
            var entry = store.readEntry(operation.id, i, descriptor, level.registryAccess());
            var pos = origin.offset(entry.offset().rotate(rotation));
            level.getChunkAt(pos);
            var entity = level.getBlockEntity(pos);
            if (entry.data() != null && entity == null) throw new IOException("Missing restored block entity at " + pos);
            if (entry.data() != null || manifest.getBoolean("fresh") && entity instanceof IGridConnectedBlockEntity) {
                var data = entry.data() == null ? entity.saveWithFullMetadata(level.registryAccess())
                        : rotatePorts(relocated(entry.data(), pos, origin, level.getGameTime() - manifest.getLong("sealed_tick")), rotation);
                if (manifest.getBoolean("fresh")) data = freshOwnership(data, manifest, pos.equals(origin));
                entity.loadWithComponents(data, level.registryAccess()); entity.setChanged();
            }
            operation.contentCursor++; restored++; bytes += descriptor.getLong("bytes");
        }
        manifest.putString("phase", "CONTENTS"); manifest.putInt("content_cursor", operation.contentCursor);
        store.writeManifest(operation.id, manifest);
        if (operation.contentCursor < operation.layout.size()) return;
        BuildingTransferPersistence.checkpoint(level, operation.layout, origin, rotation);
        operation.manifest = store.updateManifest(operation.id, manifest, next -> next.putString("state", "DEPLOYED"));
        var player = server.getPlayerList().getPlayer(manifest.getUUID("owner"));
        unlock(server, operation, level, origin, rotation);
        for (var entry : operation.layout) {
            var pos = origin.offset(entry.offset().rotate(rotation));
            var entity = level.getBlockEntity(pos); if (entity != null) entity.clearRemoved();
            level.invalidateCapabilities(pos);
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
        }
        if (player != null) { consumeReservation(player, operation.id); message(player, "deployed"); }
    }
    /** Cancel an unfinished deployment without ever making its archived contents available twice. */
    public static boolean cancelDeployment(ServerPlayer player, UUID id) {
        if (id == null || !player.mayBuild() || player.isSpectator()) return false;
        var operation = OPERATIONS.getOrDefault(player.server, Map.of()).get(id);
        if (operation == null || !operation.manifest.hasUUID("owner")
                || !operation.manifest.getUUID("owner").equals(player.getUUID())) return false;
        var state = operation.manifest.getString("state");
        if (state.equals("ROLLING_BACK")) { message(player, "rollback_started"); return true; }
        if (!state.equals("DEPLOYING")) return false;
        try {
            // Authorize rollback durably before deleting even the first destination block.
            operation.manifest = BuildingArchiveStore.forServer(player.server).updateManifest(id, operation.manifest, next -> {
                next.putString("state", "ROLLING_BACK"); next.putString("phase", "ROLLBACK"); next.putInt("rollback_cursor", 0);
                next.remove("blocked_index");
            });
            operation.cursor = 0; operation.blockedIndex = -1; operation.retryTicks = 0; operation.reportedError = null;
            var level = operationLevel(player.server, operation.manifest);
            if (level != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.atir.molecularmanipulator.network.BuildingPayloads.DeploymentCancelled(
                            level.dimension().location(), BlockPos.of(operation.manifest.getLong("target"))));
            message(player, "rollback_started");
            return true;
        } catch (IOException | RuntimeException error) { fail(player, error); return false; }
    }
    public record TransferState(UUID id, String state) {}
    /** A panel can recover its own receipt after reopening or reconnecting, never another player's operation. */
    public static TransferState transferState(ServerPlayer player, UUID requested, int slot, BlockPos target) throws IOException {
        UUID id = requested;
        if (id == null && slot >= 0 && slot < player.getInventory().items.size())
            id = BuildingPackage.reservation(player.getInventory().getItem(slot));
        if (id == null && locked(player.level(), target)) {
            for (var operation : OPERATIONS.getOrDefault(player.server, Map.of()).values()) {
                if (operationLevel(player.server, operation.manifest) != player.level() || !operation.manifest.getUUID("owner").equals(player.getUUID())
                        || operation.manifest.getString("state").equals("SEALING")) continue;
                var origin = BlockPos.of(operation.manifest.getLong("target"));
                var rotation = BuildingType.rotation(direction(operation.manifest.getString("facing")));
                if (operation.layout.stream().anyMatch(entry -> origin.offset(entry.offset().rotate(rotation)).equals(target))) { id = operation.id; break; }
            }
        }
        if (id == null) return new TransferState(null, "");
        var operation = OPERATIONS.getOrDefault(player.server, Map.of()).get(id);
        CompoundTag manifest;
        if (operation != null) manifest = operation.manifest;
        else {
            var store = BuildingArchiveStore.forServer(player.server);
            if (!store.exists(id)) return new TransferState(null, "");
            manifest = store.readManifest(id);
        }
        if (!manifest.hasUUID("owner") || !manifest.getUUID("owner").equals(player.getUUID())) return new TransferState(null, "");
        var state = manifest.getString("state");
        if (state.equals("DEPLOYING")) {
            if (manifest.getString("phase").equals("BLOCKED")) state = "BLOCKED";
            else if (operation != null && operation.reportedError != null) state = "PAUSED";
        }
        return new TransferState(id, state);
    }
    private static void rollbackTick(MinecraftServer server, Operation operation) throws IOException {
        var manifest = operation.manifest; var level = operationLevel(server, manifest);
        if (level == null) throw new IOException("Destination dimension unavailable for rollback");
        var origin = BlockPos.of(manifest.getLong("target"));
        var rotation = BuildingType.rotation(direction(manifest.getString("facing")));
        int limit = Math.min(operation.layout.size(), operation.cursor + 128);
        for (; operation.cursor < limit; operation.cursor++) {
            // Layouts put the controller last; remove it first, before any other restored inventories.
            int index = operation.cursor == 0 ? operation.layout.size() - 1 : operation.cursor - 1;
            var entry = operation.layout.get(index);
            if (entry.state().isAir()) continue;
            var pos = origin.offset(entry.offset().rotate(rotation)); level.getChunkAt(pos);
            // The site was checked empty and exclusively locked before placement. Never delete a foreign obstruction.
            if (!level.getBlockState(pos).is(entry.state().getBlock())) continue;
            if (level.getBlockEntity(pos) instanceof IGridConnectedBlockEntity networked) networked.getMainNode().destroy();
            level.removeBlockEntity(pos);
            setBlock(level, pos, Blocks.AIR.defaultBlockState());
        }
        manifest.putInt("rollback_cursor", operation.cursor);
        BuildingArchiveStore.forServer(server).writeManifest(operation.id, manifest);
        if (operation.cursor < operation.layout.size()) return;
        BuildingTransferPersistence.checkpoint(level, operation.layout, origin, rotation);
        operation.manifest = BuildingArchiveStore.forServer(server).updateManifest(operation.id, manifest, next -> {
            // A fresh template already has its original item; publishing SEALED would mint an extra archive handle.
            next.putString("state", next.getBoolean("fresh") ? "CANCELLED" : "SEALED");
            next.remove("blocked_index");
        });
        unlock(server, operation, level, origin, rotation);
        var player = server.getPlayerList().getPlayer(manifest.getUUID("owner"));
        if (player != null) { releaseReservation(player, operation.id); message(player, "rollback_finished"); }
    }
    private static void releaseReservation(ServerPlayer player, UUID id) throws IOException {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (id.equals(BuildingPackage.reservation(stack))) BuildingPackage.clearReservation(stack);
        }
        var carried = player.containerMenu.getCarried();
        if (id.equals(BuildingPackage.reservation(carried))) BuildingPackage.clearReservation(carried);
        BuildingTransferPersistence.savePlayer(player);
    }
    /** Only the foreign block currently preventing progress may be broken through the site lock. */
    public static boolean mayClearObstruction(Level level, BlockPos pos) {
        return obstruction(level, pos) != null;
    }
    private static Operation obstruction(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel) || !locked(level, pos)) return null;
        for (var operation : OPERATIONS.getOrDefault(serverLevel.getServer(), Map.of()).values()) {
            if (operation.blockedIndex < 0 || !operation.manifest.getString("state").equals("DEPLOYING")
                    || operationLevel(serverLevel.getServer(), operation.manifest) != level) continue;
            var entry = operation.layout.get(operation.blockedIndex);
            var at = BlockPos.of(operation.manifest.getLong("target")).offset(entry.offset().rotate(
                    BuildingType.rotation(direction(operation.manifest.getString("facing")))));
            if (at.equals(pos) && !level.getBlockState(pos).is(entry.state().getBlock())) return operation;
        }
        return null;
    }
    /** New templates have no previous owner. Every AE component, not just the controller, belongs to the placing player. */
    static CompoundTag freshOwnership(CompoundTag original, CompoundTag manifest, boolean controller) {
        var data = original.copy();
        var proxy = new CompoundTag(); proxy.putInt("p", manifest.getInt("ae_owner")); data.put("proxy", proxy);
        if (controller) {
            var type = BuildingType.byId(manifest.getString("type"));
            if (type == BuildingType.FABRICATION_WELL) {
                data.putBoolean("fabrication_team_bound", true);
                if (manifest.hasUUID("placement_team")) data.putUUID("fabrication_bound_team", manifest.getUUID("placement_team"));
            } else if (type.isStarOrrery()) data.putUUID("entropy_team", manifest.hasUUID("placement_team")
                    ? manifest.getUUID("placement_team") : manifest.getUUID("owner"));
        }
        return data;
    }
    /** Only known infrastructure pointers change. Foreign item/fluid component compounds remain byte-for-byte native. */
    public static CompoundTag relocated(CompoundTag original, BlockPos position, BlockPos controller, long pausedTicks) {
        var data = original.copy();
        data.putInt("x", position.getX()); data.putInt("y", position.getY()); data.putInt("z", position.getZ());
        if (data.contains("proxy", Tag.TAG_COMPOUND)) {
            // Keep AE's native per-node data (owner, buffered energy, channel mode and service extensions).
            // Only an old grid-storage identity must not bind the relocated node back to its former grid.
            data.getCompound("proxy").remove("g");
        }
        for (var key : List.of("fabrication_controller", "molecular_center_controller_pos"))
            if (data.contains(key, Tag.TAG_LONG)) data.putLong(key, controller.asLong());
        for (var key : List.of("molecular_center_output_ready_tick")) {
            if (data.contains(key, Tag.TAG_LONG) && data.getLong(key) != Long.MIN_VALUE) {
                try { data.putLong(key, Math.addExact(data.getLong(key), pausedTicks)); }
                catch (ArithmeticException overflow) { data.putLong(key, Long.MAX_VALUE); }
            }
        }
        return data;
    }
    public static CompoundTag rotatePorts(CompoundTag original, Rotation rotation) {
        var data = original.copy();
        if (data.contains("port_output_sides", Tag.TAG_INT)) {
            int sides = data.getInt("port_output_sides"), rotated = 0;
            for (var side : Direction.values()) if ((sides & (1 << side.ordinal())) != 0)
                rotated |= 1 << rotation.rotate(side).ordinal();
            data.putInt("port_output_sides", rotated);
        }
        return data;
    }
    public static void settleReservation(ServerPlayer player, ItemStack stack) {
        var id = BuildingPackage.reservation(stack); if (id == null) return;
        try {
            var store = BuildingArchiveStore.forServer(player.server);
            if (!store.exists(id)) BuildingPackage.clearReservation(stack);
            else {
                var manifest = store.readManifest(id);
                if (manifest.getString("state").equals("SEALED")) {
                    if (manifest.getBoolean("delivered")) BuildingPackage.clearReservation(stack);
                    else if (manifest.hasUUID("owner") && manifest.getUUID("owner").equals(player.getUUID())) deliver(player.server, id, manifest);
                } else if (manifest.getString("state").equals("CANCELLED")) BuildingPackage.clearReservation(stack);
                else if (manifest.getString("state").equals("DEPLOYED")) { consumeReservation(player, id); return; }
            }
            if (BuildingPackage.reservation(stack) == null) BuildingTransferPersistence.savePlayer(player);
        } catch (IOException error) { RateLimitedLog.error("Could not settle building receipt {}", id, error); }
    }
    private static void consumeReservation(ServerPlayer player, UUID id) throws IOException {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (id.equals(BuildingPackage.reservation(stack))) stack.shrink(stack.getCount());
        }
        var carried = player.containerMenu.getCarried();
        if (id.equals(BuildingPackage.reservation(carried))) carried.shrink(carried.getCount());
        BuildingTransferPersistence.savePlayer(player);
    }
    private static void fail(Player player, Exception error) {
        RateLimitedLog.error("Building folding operation paused", error);
        message(player, "error", error.getMessage());
    }
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        var server = event.getServer(); var store = BuildingArchiveStore.forServer(server);
        try {
            for (var id : store.listArchives()) {
                var manifest = store.readManifest(id); var state = manifest.getString("state");
                if (state.equals("SEALING") || state.equals("DEPLOYING") || state.equals("ROLLING_BACK")) add(server, id, manifest,
                        store.readLayout(id, server.registryAccess()), store.readIndex(id).getList("entries", Tag.TAG_COMPOUND));
            }
        } catch (IOException error) { throw new IllegalStateException("Could not recover building folding journal", error); }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Pre event) {
        var server = event.getServer();
        for (var operation : List.copyOf(OPERATIONS.getOrDefault(server, Map.of()).values())) try {
            if (operation.retryTicks > 0) { operation.retryTicks--; continue; }
            switch (operation.manifest.getString("state")) {
                case "SEALING" -> finishSealing(server, operation);
                case "ROLLING_BACK" -> rollbackTick(server, operation);
                default -> deployTick(server, operation);
            }
            operation.reportedError = null;
        } catch (IOException | RuntimeException error) {
            operation.retryTicks = 20;
            if (!Objects.equals(operation.reportedError, error.getMessage())) {
                operation.reportedError = error.getMessage();
                var player = server.getPlayerList().getPlayer(operation.manifest.getUUID("owner"));
                if (player != null) {
                    if (operation.blockedIndex >= 0) {
                        var entry = operation.layout.get(operation.blockedIndex);
                        var pos = BlockPos.of(operation.manifest.getLong("target")).offset(entry.offset().rotate(
                                BuildingType.rotation(direction(operation.manifest.getString("facing")))));
                        message(player, "blocked", pos.getX(), pos.getY(), pos.getZ());
                    } else message(player, "paused", error.getMessage());
                }
                RateLimitedLog.error("Building {} remains reserved for recovery", operation.id, error);
            }
        }
    }
    @SubscribeEvent public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            collect(player);
            for (var stack : player.getInventory().items) settleReservation(player, stack);
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) {
        OPERATIONS.remove(event.getServer()); LOCKED.keySet().removeIf(level -> level.getServer() == event.getServer());
    }
    @SubscribeEvent public static void explosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> locked(event.getLevel(), pos));
    }
    @SubscribeEvent public static void piston(net.neoforged.neoforge.event.level.PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level) || !LOCKED.containsKey(level)) return;
        var resolver = event.getStructureHelper();
        if (resolver == null) return;
        resolver.resolve();
        var movement = event.getPistonMoveType().isExtend ? event.getDirection() : event.getDirection().getOpposite();
        if (locked(level, event.getFaceOffsetPos()) || resolver.getToPush().stream().anyMatch(pos -> locked(level, pos) || locked(level, pos.relative(movement)))
                || resolver.getToDestroy().stream().anyMatch(pos -> locked(level, pos))) event.setCanceled(true);
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level) || !locked(level, event.getPos())) return;
        var operation = obstruction(level, event.getPos());
        if (operation == null || !operation.manifest.getUUID("owner").equals(event.getPlayer().getUUID())) event.setCanceled(true);
    }
    @SubscribeEvent public static void useBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!locked(event.getLevel(), event.getPos())) return;
        // The calibrator must reach its panel even when aimed at a locked, unfinished member.
        if (event.getItemStack().is(com.atir.molecularmanipulator.registry.BuildingContent.CALIBRATOR.get())) {
            event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
            event.setUseItem(net.neoforged.neoforge.common.util.TriState.TRUE);
        } else event.setCanceled(true);
    }
}
