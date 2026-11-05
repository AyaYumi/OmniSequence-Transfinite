package com.atir.molecularmanipulator.building;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.diagnostics.RateLimitedLog;
import com.atir.molecularmanipulator.blockentity.*;
import com.atir.molecularmanipulator.block.EntropyAnchorBlock;
import com.atir.molecularmanipulator.starcomputation.EntropyArmillaryFoundation;
import com.atir.molecularmanipulator.starcomputation.EntropyArmillaryLayout;
import java.io.IOException;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
    public static boolean mayMutate(BlockPos pos) { return pos.equals(MUTATION.get()); }
    private static void setBlock(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        MUTATION.set(pos);
        try { level.setBlock(pos, state, 2 | 16); } finally { MUTATION.remove(); }
    }
    private BuildingTransfer() {}
    private static final class Operation {
        final UUID id;
        final CompoundTag manifest;
        final List<BuildingArchiveStore.Entry> layout;
        final ListTag descriptors;
        final int foundationEnd;
        int cursor, age;
        Operation(UUID id, CompoundTag manifest, List<BuildingArchiveStore.Entry> layout, ListTag descriptors) {
            this.id = id; this.manifest = manifest; this.layout = layout; this.descriptors = descriptors;
            int minY = layout.stream().filter(entry -> !entry.state().isAir()).mapToInt(entry -> entry.offset().getY()).min().orElse(0);
            int end = 0;
            while (end < layout.size() && !layout.get(end).offset().equals(BlockPos.ZERO) && layout.get(end).offset().getY() <= minY + 1) end++;
            foundationEnd = end;
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
        UUID id = BuildingPackage.archive(stack);
        if (id == null) { message(player, "new_package", Component.translatable(BuildingPackage.type(stack).translationKey())); return; }
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
        if (id == null) return type.template();
        var store = BuildingArchiveStore.forServer(player.server);
        if (!store.exists(id)) throw new IOException("Archive is absent from this world");
        var manifest = store.readManifest(id);
        if (!manifest.getString("state").equals("SEALED")) throw new IOException("Archive is already reserved or deployed");
        if (!manifest.getString("type").equals(type.id) || manifest.getInt("template") != BuildingType.TEMPLATE_VERSION)
            throw new IOException("Package type or template differs from its archive");
        return store.readLayout(id, player.registryAccess());
    }
    public static BlockPos controllerAtBase(List<BuildingArchiveStore.Entry> layout, BlockPos base) {
        int minY = layout.stream().filter(entry -> !entry.state().isAir()).mapToInt(entry -> entry.offset().getY()).min().orElse(0);
        return base.above(-minY);
    }
    public static List<BlockPos> conflicts(ServerPlayer player, List<BuildingArchiveStore.Entry> layout,
            BlockPos controller, Direction facing) {
        var level = player.serverLevel(); var rotation = BuildingType.rotation(facing); var conflicts = new ArrayList<BlockPos>();
        for (var entry : layout) {
            var pos = controller.offset(entry.offset().rotate(rotation));
            if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.mayInteract(player, pos)
                    || locked(level, pos) || !level.getBlockState(pos).canBeReplaced() || level.getBlockEntity(pos) != null) conflicts.add(pos);
        }
        return conflicts;
    }
    /** The item stays in its inventory slot until the destination and its contents are durably saved. */
    public static boolean deploy(ServerPlayer player, int slot, BlockPos base, Direction facing) {
        if (!player.mayBuild() || player.isSpectator()) return false;
        var stack = player.getInventory().getItem(slot);
        if (BuildingPackage.type(stack) == null || BuildingPackage.reservation(stack) != null) return false;
        try {
            var layout = layout(player, stack); var controller = controllerAtBase(layout, base);
            if (!conflicts(player, layout, controller, facing).isEmpty()) { message(player, "conflicts"); return false; }
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
            BuildingPackage.reserve(stack, id); player.getInventory().setChanged(); player.server.getPlayerList().saveAll();
            manifest.putString("state", "DEPLOYING"); store.writeManifest(id, manifest);
            add(player.server, id, manifest, layout, store.readIndex(id).getList("entries", Tag.TAG_COMPOUND));
            message(player, "deploy_started"); return true;
        } catch (IOException | RuntimeException error) { fail(player, error); return false; }
    }
    public static boolean seal(ServerPlayer player, BlockEntity controller) {
        var type = BuildingType.forController(controller);
        if (type == null || !player.mayBuild() || player.isSpectator() || locked(player.level(), controller.getBlockPos())) return false;
        try {
            if (controller instanceof MatterFabricationBlockEntity well && (well.isBuilding() || well.isDismantling()
                    || well.getResearchOrders().activeJobs() > 0)) { message(player, "busy"); return false; }
            if (controller instanceof MolecularCenterBlockEntity array && (array.isBuilding() || array.isDismantling())) { message(player, "busy"); return false; }
            if (controller instanceof OmniComputationCoreBlockEntity omni && (omni.isBuilding() || omni.isDismantling()
                    || omni.getActiveJobCount() > 0 || omni.getActiveMaterialCalculations() > 0)) { message(player, "busy"); return false; }
            if (controller instanceof AENetworkedBlockEntity networked && networked.getMainNode().getGrid() != null
                    && networked.getMainNode().getGrid().getCraftingService().getCpus().stream().anyMatch(ICraftingCPU::isBusy)) {
                message(player, "busy"); return false;
            }
            var level = player.serverLevel(); var origin = controller.getBlockPos(); var facing = BuildingType.facing(controller.getBlockState());
            var candidates = new LinkedHashSet<BlockPos>();
            var air = new LinkedHashSet<BlockPos>();
            if (controller instanceof StarComputationEngineCoreBlockEntity engine) {
                engine.refreshStructureNow(); candidates.addAll(engine.foldingMembers());
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
                if (!pos.equals(origin) && !isMember(type, offset, state, controller)) continue;
                var entity = level.getBlockEntity(pos);
                var data = pos.equals(origin) ? controllerData : entity == null ? null : entity.saveWithFullMetadata(level.registryAccess());
                if (data != null) data = rotatePorts(data, inverse);
                entries.add(new BuildingArchiveStore.Entry(offset, BuildingType.rotateState(state, inverse), data));
            }
            entries.sort(Comparator.comparingInt((BuildingArchiveStore.Entry entry) -> entry.offset().equals(BlockPos.ZERO) ? 1 : 0)
                    .thenComparingInt(entry -> entry.offset().getY()).thenComparingInt(entry -> entry.offset().getX()).thenComparingInt(entry -> entry.offset().getZ()));
            var id = UUID.randomUUID(); var store = BuildingArchiveStore.forServer(player.server);
            store.writeSnapshot(id, entries, level.registryAccess());
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
            case STAR_COMPUTATION_ENGINE -> true;
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
        for (var entry : layout) locks.add(origin.offset(entry.offset().rotate(rotation)));
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
        server.saveEverything(true, true, true);
        manifest.putString("state", "SEALED");
        BuildingArchiveStore.forServer(server).writeManifest(operation.id, manifest);
        unlock(server, operation, level, origin, rotation);
        deliver(server, operation.id, manifest);
    }
    private static void deliver(MinecraftServer server, UUID id, CompoundTag manifest) throws IOException {
        if (manifest.getBoolean("delivered")) return;
        var player = server.getPlayerList().getPlayer(manifest.getUUID("owner"));
        if (player == null) return;
        // After a crash between inventory save and journal acknowledgement, recognize the already delivered handle.
        for (var stack : player.getInventory().items) if (id.equals(BuildingPackage.archive(stack))) {
            manifest.putBoolean("delivered", true); BuildingArchiveStore.forServer(server).writeManifest(id, manifest); return;
        }
        var stack = BuildingPackage.sealed(BuildingType.byId(manifest.getString("type")), id, manifest.getInt("blocks"));
        int slot = player.getInventory().getFreeSlot();
        if (slot < 0) { message(player, "inventory_full"); return; }
        player.getInventory().setItem(slot, stack); player.getInventory().setChanged();
        server.getPlayerList().saveAll();
        manifest.putBoolean("delivered", true); BuildingArchiveStore.forServer(server).writeManifest(id, manifest);
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
        int limit = Math.min(stageEnd, operation.cursor + budget);
        for (; operation.cursor < limit; operation.cursor++) {
            var entry = operation.layout.get(operation.cursor); var pos = origin.offset(entry.offset().rotate(rotation));
            level.getChunkAt(pos);
            var state = BuildingType.rotateState(entry.state(), rotation);
            var current = level.getBlockState(pos);
            if (!current.isAir() && !current.canBeReplaced() && !current.is(state.getBlock())) throw new IOException("Deployment obstructed at " + pos);
            if (!current.equals(state)) setBlock(level, pos, state);
            if (!state.isAir() && operation.cursor % 16 == 0) level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, 1, .1, .1, .1, 0);
        }
        manifest.putInt("cursor", operation.cursor); BuildingArchiveStore.forServer(server).writeManifest(operation.id, manifest);
        if (operation.cursor < operation.layout.size()) return;
        var store = BuildingArchiveStore.forServer(server);
        // Data is loaded one block entity at a time, after every physical block is in place, controller last.
        for (int i = 0; i < operation.layout.size(); i++) {
            var entry = store.readEntry(operation.id, i, operation.descriptors.getCompound(i), level.registryAccess());
            if (entry.data() == null) continue;
            var pos = origin.offset(entry.offset().rotate(rotation));
            var data = rotatePorts(relocated(entry.data(), pos, origin, level.getGameTime() - manifest.getLong("sealed_tick")), rotation);
            var entity = level.getBlockEntity(pos);
            if (entity == null) throw new IOException("Missing restored block entity at " + pos);
            entity.loadWithComponents(data, level.registryAccess()); entity.setChanged();
        }
        var player = server.getPlayerList().getPlayer(manifest.getUUID("owner"));
        if (manifest.getBoolean("fresh")) {
            var entity = level.getBlockEntity(origin);
            if (entity != null) {
                var data = entity.saveWithFullMetadata(level.registryAccess());
                var proxy = new CompoundTag(); proxy.putInt("p", manifest.getInt("ae_owner")); data.put("proxy", proxy);
                var type = BuildingType.byId(manifest.getString("type"));
                if (type == BuildingType.FABRICATION_WELL) {
                    data.putBoolean("fabrication_team_bound", true);
                    if (manifest.hasUUID("placement_team")) data.putUUID("fabrication_bound_team", manifest.getUUID("placement_team"));
                } else if (type == BuildingType.STAR_COMPUTATION_ENGINE) data.putUUID("entropy_team", manifest.hasUUID("placement_team")
                        ? manifest.getUUID("placement_team") : manifest.getUUID("owner"));
                entity.loadWithComponents(data, level.registryAccess()); entity.setChanged();
            }
        }
        server.saveEverything(true, true, true);
        manifest.putString("state", "DEPLOYED"); store.writeManifest(operation.id, manifest);
        unlock(server, operation, level, origin, rotation);
        for (var entry : operation.layout) {
            var pos = origin.offset(entry.offset().rotate(rotation));
            var entity = level.getBlockEntity(pos); if (entity != null) entity.clearRemoved();
            level.invalidateCapabilities(pos);
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
        }
        if (player != null) { consumeReservation(player, operation.id); message(player, "deployed"); }
    }
    /** Only known infrastructure pointers change. Foreign item/fluid component compounds remain byte-for-byte native. */
    public static CompoundTag relocated(CompoundTag original, BlockPos position, BlockPos controller, long pausedTicks) {
        var data = original.copy();
        data.putInt("x", position.getX()); data.putInt("y", position.getY()); data.putInt("z", position.getZ());
        if (data.contains("proxy", Tag.TAG_COMPOUND)) {
            var owner = new CompoundTag();
            if (data.getCompound("proxy").contains("p", Tag.TAG_INT)) owner.putInt("p", data.getCompound("proxy").getInt("p"));
            data.put("proxy", owner);
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
            if (!store.exists(id) || store.readManifest(id).getString("state").equals("SEALED")) BuildingPackage.clearReservation(stack);
            else if (store.readManifest(id).getString("state").equals("DEPLOYED")) stack.shrink(stack.getCount());
            player.getInventory().setChanged();
        } catch (IOException error) { RateLimitedLog.error("Could not settle building receipt {}", id, error); }
    }
    private static void consumeReservation(ServerPlayer player, UUID id) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (id.equals(BuildingPackage.reservation(stack))) stack.shrink(stack.getCount());
        }
        var carried = player.containerMenu.getCarried();
        if (id.equals(BuildingPackage.reservation(carried))) carried.shrink(carried.getCount());
        player.getInventory().setChanged(); player.server.getPlayerList().saveAll();
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
                if (state.equals("SEALING") || state.equals("DEPLOYING")) add(server, id, manifest,
                        store.readLayout(id, server.registryAccess()), store.readIndex(id).getList("entries", Tag.TAG_COMPOUND));
            }
        } catch (IOException error) { throw new IllegalStateException("Could not recover building folding journal", error); }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Pre event) {
        var server = event.getServer();
        for (var operation : List.copyOf(OPERATIONS.getOrDefault(server, Map.of()).values())) try {
            if (operation.manifest.getString("state").equals("SEALING")) finishSealing(server, operation); else deployTick(server, operation);
        } catch (IOException | RuntimeException error) {
            operation.age++;
            RateLimitedLog.error("Building {} remains reserved for recovery", operation.id, error);
        }
        if (server.getTickCount() % 100 == 0) for (var player : server.getPlayerList().getPlayers()) {
            for (var stack : player.getInventory().items) {
                var id = BuildingPackage.reservation(stack); if (id == null) continue;
                try {
                    var store = BuildingArchiveStore.forServer(server);
                    if (!store.exists(id)) { BuildingPackage.clearReservation(stack); continue; }
                    if (store.readManifest(id).getString("state").equals("DEPLOYED")) consumeReservation(player, id);
                } catch (IOException error) { RateLimitedLog.error("Could not settle package reservation", error); }
            }
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
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) { if (locked(event.getLevel() instanceof Level level ? level : null, event.getPos())) event.setCanceled(true); }
    @SubscribeEvent public static void useBlock(PlayerInteractEvent.RightClickBlock event) { if (locked(event.getLevel(), event.getPos())) event.setCanceled(true); }
}
