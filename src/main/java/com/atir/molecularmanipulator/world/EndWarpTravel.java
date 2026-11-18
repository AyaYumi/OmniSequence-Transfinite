package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.diagnostics.RateLimitedLog;
import com.atir.molecularmanipulator.entity.HighEnergyLabEffectsEntity;
import com.atir.molecularmanipulator.registry.WarpContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class EndWarpTravel extends SavedData {
    private static final String DATA_NAME = "molecularmanipulator_end_warp";
    private static final Factory<EndWarpTravel> FACTORY = new Factory<>(EndWarpTravel::new, EndWarpTravel::load);
    private static final Set<ServerLevel> ISLAND_QUEUE = new HashSet<>();
    private static final Map<ServerLevel, Map<Boolean, Preparation>> PREPARATIONS = new HashMap<>();
    private static final TicketType<ChunkPos> PREPARATION_TICKET = TicketType.create(
            "molecularmanipulator_end_warp", java.util.Comparator.comparingLong(ChunkPos::toLong), 200);
    private static final BlockPos SOURCE_PAD = EndWarpRuinStructure.ORIGIN.offset(20, 22, 20);
    private static final BlockPos LAB_PAD = HighEnergyLabStructure.LANDING_ORIGIN.offset(3, 2, 7);
    private static final BlockPos LEGACY_LAB_PAD = new BlockPos(8192, 165, 8252);

    private boolean islandGenerated;
    private boolean labGenerated;
    private BlockPos savedLabPad = LAB_PAD;

    EndWarpTravel() {}

    public static BlockPos sourcePad() { return SOURCE_PAD; }
    public static BlockPos labPad() { return LAB_PAD; }

    public static BlockPos destination(ServerLevel end, boolean returning) {
        return prepare(end, returning) ? data(end).pad(returning) : null;
    }

    public static boolean prepare(ServerLevel end, boolean returning) {
        if (!Level.END.equals(end.dimension())) throw new IllegalArgumentException("Warp destinations belong in the End");
        var data = data(end);
        if (data.generated(returning) && loaded(end, data.pad(returning))) return true;
        var jobs = PREPARATIONS.computeIfAbsent(end, ignored -> new HashMap<>());
        var job = jobs.computeIfAbsent(returning, ignored -> new Preparation(data, returning));
        if (job.failure != null) {
            jobs.remove(returning);
            throw job.failure;
        }
        return false;
    }

    public static void ensureIsland(ServerLevel end) { prepare(end, true); }

    private static EndWarpTravel data(ServerLevel end) {
        return end.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private boolean generated(boolean returning) { return returning ? islandGenerated : labGenerated; }
    private BlockPos pad(boolean returning) { return returning ? SOURCE_PAD : savedLabPad; }

    boolean prepareOnce(boolean returning, BooleanSupplier generate) {
        if (generated(returning)) return true;
        if (!generate.getAsBoolean()) return false;
        if (returning) islandGenerated = true;
        else labGenerated = true;
        setDirty();
        return true;
    }

    private static boolean loaded(ServerLevel end, BlockPos pad) {
        for (var chunk : chunks(pad.offset(-1, -1, -1), new Vec3i(3, 3, 3))) {
            if (end.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) return false;
        }
        return true;
    }

    private static List<ChunkPos> chunks(BlockPos origin, Vec3i size) {
        var chunks = new ArrayList<ChunkPos>();
        int maxX = (origin.getX() + size.getX() - 1) >> 4;
        int maxZ = (origin.getZ() + size.getZ() - 1) >> 4;
        for (int x = origin.getX() >> 4; x <= maxX; x++) {
            for (int z = origin.getZ() >> 4; z <= maxZ; z++) chunks.add(new ChunkPos(x, z));
        }
        return chunks;
    }

    private static StructureStart naturalStart(ServerLevel end, boolean returning) {
        var structureId = returning ? MolecularManipulator.id("end_warp_ruin")
                : com.atir.molecularmanipulator.registry.HighEnergyLabContent.ID;
        var structure = end.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structureId);
        if (structure == null) return null;
        var chunk = returning ? EndWarpRuinStructure.START_CHUNK : HighEnergyLabStructure.START_CHUNK;
        var start = end.getChunkSource().getChunkNow(chunk.x, chunk.z).getStartForStructure(structure);
        return start != null && start.isValid() ? start : null;
    }

    private static StructureTemplate requireTemplate(ServerLevel end, ResourceLocation id, Vec3i expectedSize) {
        var template = end.getStructureManager().get(id)
                .orElseThrow(() -> new IllegalStateException("Missing End warp template " + id));
        if (!expectedSize.equals(template.getSize())) {
            throw new IllegalStateException("Unexpected size for End warp template " + id + ": " + template.getSize());
        }
        return template;
    }

    private static final class Preparation {
        private final EndWarpTravel data;
        private final boolean returning;
        private final List<ChunkPos> chunks;
        private int requested;
        private int loaded;
        private int placed;
        private boolean landingPlaced;
        private StructureTemplate template;
        private StructureTemplate landing;
        private RuntimeException failure;

        private Preparation(EndWarpTravel data, boolean returning) {
            this.data = data;
            this.returning = returning;
            var origin = data.generated(returning) ? data.pad(returning).offset(-1, -1, -1)
                    : returning ? EndWarpRuinStructure.ORIGIN : HighEnergyLabStructure.ORIGIN;
            var size = data.generated(returning) ? new Vec3i(3, 3, 3)
                    : returning ? EndWarpRuinStructure.SIZE : HighEnergyLabStructure.SIZE;
            chunks = chunks(origin, size);
        }

        private boolean tick(ServerLevel end) {
            var source = end.getChunkSource();
            for (int index = 0; index < requested; index++) {
                var chunk = chunks.get(index);
                source.addRegionTicket(PREPARATION_TICKET, chunk, 0, chunk);
            }
            while (loaded < requested) {
                var chunk = chunks.get(loaded);
                if (source.getChunkNow(chunk.x, chunk.z) == null) break;
                loaded++;
            }
            if (loaded < chunks.size()) {
                while (requested < chunks.size() && requested - loaded < 2) {
                    var chunk = chunks.get(requested++);
                    source.addRegionTicket(PREPARATION_TICKET, chunk, 0, chunk);
                }
                return false;
            }
            if (data.generated(returning)) return true;
            if (template == null) {
                var start = naturalStart(end, returning);
                if (start != null) {
                    if (!returning) {
                        for (var piece : start.getPieces()) {
                            if (piece instanceof EndWarpTemplatePiece) {
                                var box = piece.getBoundingBox();
                                data.savedLabPad = new BlockPos(box.minX() + 3, box.minY() + 2, box.minZ() + 7);
                                break;
                            }
                        }
                    }
                    return data.prepareOnce(returning, () -> true);
                }
                template = requireTemplate(end, returning ? EndWarpRuinStructure.TEMPLATE : HighEnergyLabPiece.TEMPLATE,
                        returning ? EndWarpRuinStructure.SIZE : HighEnergyLabStructure.SIZE);
                landing = returning ? template : requireTemplate(end, HighEnergyLabStructure.LANDING_TEMPLATE,
                        HighEnergyLabStructure.LANDING_SIZE);
                var origin = returning ? EndWarpRuinStructure.ORIGIN : HighEnergyLabStructure.LANDING_ORIGIN;
                if (landing.filterBlocks(origin, EndWarpTemplatePiece.settings(), WarpContent.WARP_PAD.get()).stream()
                        .noneMatch(block -> block.pos().equals(returning ? SOURCE_PAD : LAB_PAD))) {
                    throw new IllegalStateException("End warp template has no destination pad");
                }
            }
            if (!returning && !landingPlaced) {
                var origin = HighEnergyLabStructure.LANDING_ORIGIN;
                if (!landing.placeInWorld(end, origin, origin, EndWarpTemplatePiece.settings(), end.getRandom(), 2)) {
                    throw new IllegalStateException("Could not place End warp landing");
                }
                landingPlaced = true;
                return false;
            }
            if (placed < chunks.size()) {
                var chunk = chunks.get(placed++);
                var bounds = new BoundingBox(chunk.getMinBlockX(), end.getMinBuildHeight(), chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(), end.getMaxBuildHeight() - 1, chunk.getMaxBlockZ());
                var origin = returning ? EndWarpRuinStructure.ORIGIN : HighEnergyLabStructure.ORIGIN;
                var settings = returning ? EndWarpTemplatePiece.settings() : HighEnergyLabPiece.settings(Rotation.NONE);
                settings.setBoundingBox(bounds);
                if (!returning) {
                    var anchor = HighEnergyLabStructure.ORIGIN.offset(83, 38, 137);
                    if (bounds.isInside(anchor) && !end.getEntitiesOfClass(HighEnergyLabEffectsEntity.class,
                            new AABB(anchor).inflate(1)).isEmpty()) settings.setIgnoreEntities(true);
                }
                if (!template.placeInWorld(end, origin, origin, settings, end.getRandom(), 2)) {
                    throw new IllegalStateException("Could not place End warp structure chunk " + chunk);
                }
                return false;
            }
            if (!end.getBlockState(data.pad(returning)).is(WarpContent.WARP_PAD.get())) {
                throw new IllegalStateException("End warp destination pad was not placed");
            }
            return data.prepareOnce(returning, () -> true);
        }

        private void release(ServerLevel end, boolean arriving) {
            for (int index = 0; index < requested; index++) {
                var chunk = chunks.get(index);
                end.getChunkSource().removeRegionTicket(PREPARATION_TICKET, chunk, 0, chunk);
            }
            if (arriving) {
                for (var chunk : chunks(data.pad(returning).offset(-1, -1, -1), new Vec3i(3, 3, 3))) {
                    end.getChunkSource().addRegionTicket(PREPARATION_TICKET, chunk, 0, chunk);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) queueIsland(player);
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (Level.END.equals(event.getTo()) && event.getEntity() instanceof ServerPlayer player) queueIsland(player);
    }

    private static void queueIsland(ServerPlayer player) {
        var end = player.serverLevel();
        if (Level.END.equals(end.dimension())) ISLAND_QUEUE.add(end);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var end = event.getServer().getLevel(Level.END);
        if (end == null) return;
        if (ISLAND_QUEUE.remove(end)) {
            try {
                ensureIsland(end);
            } catch (RuntimeException error) {
                RateLimitedLog.error("Could not prepare the End warp ruin", error);
            }
        }
        var jobs = PREPARATIONS.get(end);
        if (jobs == null) return;
        var iterator = jobs.values().iterator();
        while (iterator.hasNext()) {
            var job = iterator.next();
            if (job.failure != null) continue;
            try {
                if (job.tick(end)) {
                    if (job.returning) WarpCoordinates.applyInscriptions(end, end.getSeed(), null);
                    job.release(end, true);
                    iterator.remove();
                }
            } catch (RuntimeException error) {
                job.failure = error;
                job.release(end, false);
                RateLimitedLog.error("Could not prepare End warp destination", error);
            }
        }
        if (jobs.isEmpty()) PREPARATIONS.remove(end);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ISLAND_QUEUE.remove(level);
            var jobs = PREPARATIONS.remove(level);
            if (jobs != null) for (var job : jobs.values()) job.release(level, false);
        }
    }

    static EndWarpTravel load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new EndWarpTravel();
        data.islandGenerated = tag.getBoolean("IslandGenerated");
        data.labGenerated = tag.getBoolean("LabGenerated");
        data.savedLabPad = tag.contains("LabPad") ? BlockPos.of(tag.getLong("LabPad"))
                : data.labGenerated ? LEGACY_LAB_PAD : LAB_PAD;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("IslandGenerated", islandGenerated);
        tag.putBoolean("LabGenerated", labGenerated);
        tag.putLong("LabPad", savedLabPad.asLong());
        return tag;
    }
}
