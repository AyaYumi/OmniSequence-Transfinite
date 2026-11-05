package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.registry.WarpContent;
import com.mojang.logging.LogUtils;
import java.util.HashSet;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Prepares the two fixed End destinations once, including in worlds with existing End chunks. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class EndWarpTravel extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "molecularmanipulator_end_warp";
    private static final Factory<EndWarpTravel> FACTORY = new Factory<>(EndWarpTravel::new, EndWarpTravel::load);
    private static final Set<ServerLevel> ISLAND_QUEUE = new HashSet<>();
    private static final BlockPos SOURCE_PAD = EndWarpRuinStructure.ORIGIN.offset(20, 22, 20);
    private static final BlockPos LAB_PAD = HighEnergyLabStructure.LANDING_ORIGIN.offset(3, 2, 7);

    private boolean islandGenerated;
    private boolean labGenerated;

    EndWarpTravel() {}

    public static BlockPos sourcePad() {
        return SOURCE_PAD;
    }

    public static BlockPos labPad() {
        return LAB_PAD;
    }

    /** Call on the server thread. The caller validates the actual core and performs the teleport. */
    public static BlockPos destination(ServerLevel end, boolean returning) {
        ensure(end, returning);
        var pad = returning ? sourcePad() : labPad();
        // Once prepared, loading the pad's footprint is enough; never restore destroyed blocks.
        loadRegion(end, pad.offset(-1, -1, -1), new Vec3i(3, 3, 3));
        return pad;
    }

    /** Call on the server thread, outside world generation. */
    public static void ensureIsland(ServerLevel end) {
        ensure(end, true);
    }

    private static void ensure(ServerLevel end, boolean returning) {
        if (!Level.END.equals(end.dimension())) throw new IllegalArgumentException("Warp destinations belong in the End");
        var data = end.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        if (!data.prepareOnce(returning, () -> generate(end, returning))) {
            throw new IllegalStateException("Could not prepare End warp destination at " + (returning ? sourcePad() : labPad()));
        }
    }

    // Keep the decision independent of pad survival: a successful site must never be rebuilt.
    boolean prepareOnce(boolean returning, BooleanSupplier generate) {
        if (returning ? islandGenerated : labGenerated) return true;
        if (!generate.getAsBoolean()) return false;
        if (returning) islandGenerated = true;
        else labGenerated = true;
        setDirty();
        return true;
    }

    private static boolean generate(ServerLevel end, boolean returning) {
        var origin = returning ? EndWarpRuinStructure.ORIGIN : HighEnergyLabStructure.ORIGIN;
        var size = returning ? EndWarpRuinStructure.SIZE : HighEnergyLabStructure.SIZE;
        var startChunk = returning ? EndWarpRuinStructure.START_CHUNK : HighEnergyLabStructure.START_CHUNK;
        var structureId = returning ? MolecularManipulator.id("end_warp_ruin")
                : com.atir.molecularmanipulator.registry.HighEnergyLabContent.ID;
        // FULL chunks complete natural block/entity placement before deciding whether a fallback is needed.
        loadRegion(end, origin, size);
        if (hasNaturalStart(end, startChunk, structureId)) return true;

        var padTemplate = requireTemplate(end,
                returning ? EndWarpRuinStructure.TEMPLATE : HighEnergyLabStructure.LANDING_TEMPLATE,
                returning ? EndWarpRuinStructure.SIZE : HighEnergyLabStructure.LANDING_SIZE);
        var padOrigin = returning ? EndWarpRuinStructure.ORIGIN : HighEnergyLabStructure.LANDING_ORIGIN;
        var pad = returning ? sourcePad() : labPad();
        if (padTemplate.filterBlocks(padOrigin, EndWarpTemplatePiece.settings(), WarpContent.WARP_PAD.get()).stream()
                .noneMatch(block -> block.pos().equals(pad))) {
            throw new IllegalStateException("End warp template has no pad at " + pad);
        }
        if (!returning) {
            var lab = requireTemplate(end, HighEnergyLabPiece.TEMPLATE, HighEnergyLabStructure.SIZE);
            // Put the landing down first. It has no entities, so a later retry
            // after an interrupted lab placement cannot duplicate the visual anchor.
            if (!padTemplate.placeInWorld(end, padOrigin, padOrigin, EndWarpTemplatePiece.settings(), end.getRandom(), 2)) {
                return false;
            }
            if (!lab.placeInWorld(end, HighEnergyLabStructure.ORIGIN, HighEnergyLabStructure.ORIGIN,
                    HighEnergyLabPiece.settings(Rotation.NONE), end.getRandom(), 2)) return false;
        } else if (!padTemplate.placeInWorld(end, padOrigin, padOrigin, EndWarpTemplatePiece.settings(), end.getRandom(), 2)) {
            return false;
        }
        return end.getBlockState(pad).is(WarpContent.WARP_PAD.get());
    }

    private static boolean hasNaturalStart(ServerLevel end, ChunkPos chunk, ResourceLocation structureId) {
        var structure = end.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structureId);
        if (structure == null) return false;
        var start = end.getChunk(chunk.x, chunk.z).getStartForStructure(structure);
        // A valid start remains authoritative even if a player has removed its pad.
        return start != null && start.isValid();
    }

    private static StructureTemplate requireTemplate(ServerLevel end, ResourceLocation id, Vec3i expectedSize) {
        var template = end.getStructureManager().get(id)
                .orElseThrow(() -> new IllegalStateException("Missing End warp template " + id));
        if (!expectedSize.equals(template.getSize())) {
            throw new IllegalStateException("Unexpected size for End warp template " + id + ": " + template.getSize());
        }
        return template;
    }

    private static void loadRegion(ServerLevel end, BlockPos origin, Vec3i size) {
        int maxX = (origin.getX() + size.getX() - 1) >> 4;
        int maxZ = (origin.getZ() + size.getZ() - 1) >> 4;
        for (int x = origin.getX() >> 4; x <= maxX; x++) {
            for (int z = origin.getZ() >> 4; z <= maxZ; z++) end.getChunk(x, z);
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
        if (end != null && ISLAND_QUEUE.remove(end)) {
            // A server tick queue, rather than execute(), which can run inline during chunk loading.
            try {
                ensureIsland(end);
            } catch (RuntimeException error) {
                LOGGER.error("Could not prepare the End warp ruin; it can retry on the next End entry", error);
            }
        }
    }

    static EndWarpTravel load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new EndWarpTravel();
        data.islandGenerated = tag.getBoolean("IslandGenerated");
        data.labGenerated = tag.getBoolean("LabGenerated");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean("IslandGenerated", islandGenerated);
        tag.putBoolean("LabGenerated", labGenerated);
        return tag;
    }
}
