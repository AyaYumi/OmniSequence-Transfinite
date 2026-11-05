package com.atir.molecularmanipulator.world;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HighEnergyLabWorldgenTest {
    private static final Path DATA = Path.of("src/main/resources/data/molecularmanipulator/worldgen");

    @BeforeAll
    static void bootstrap() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(),
                    java.util.List.of(), java.util.List.of(), java.util.Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void generationIsEndOnlyWithoutTerrainDeformation() throws Exception {
        for (String id : new String[] {"twin_singularity_observation_ring", "end_warp_ruin"}) {
            var structure = JsonParser.parseString(Files.readString(DATA.resolve("structure/" + id + ".json"))).getAsJsonObject();
            assertEquals("molecularmanipulator:" + id, structure.get("type").getAsString());
            assertEquals("#minecraft:is_end", structure.get("biomes").getAsString());
            assertEquals("surface_structures", structure.get("step").getAsString());
            assertEquals("none", structure.get("terrain_adaptation").getAsString());
        }
        assertEquals(new BlockPos(8109, 128, 8105), HighEnergyLabStructure.ORIGIN);
        assertTrue(HighEnergyLabStructure.ORIGIN.getY() > 80);
        assertTrue(HighEnergyLabStructure.ORIGIN.getY() + HighEnergyLabStructure.SIZE.getY() <= 256);
    }

    @Test
    void structureSetsHaveOneFixedCandidateAndNoRandomSpread() throws Exception {
        var sets = new String[] {"twin_singularity_observation_rings", "end_warp_ruins"};
        var ids = new String[] {"twin_singularity_observation_ring", "end_warp_ruin"};
        var chunks = new ChunkPos[] {HighEnergyLabStructure.START_CHUNK, EndWarpRuinStructure.START_CHUNK};
        for (int i = 0; i < sets.length; i++) {
            var set = JsonParser.parseString(Files.readString(DATA.resolve("structure_set/" + sets[i] + ".json"))).getAsJsonObject();
            assertEquals(1, set.getAsJsonArray("structures").size());
            assertEquals("molecularmanipulator:" + ids[i], set.getAsJsonArray("structures").get(0)
                    .getAsJsonObject().get("structure").getAsString());
            var json = set.getAsJsonObject("placement");
            assertEquals("molecularmanipulator:fixed", json.get("type").getAsString());
            assertEquals(chunks[i].x, json.get("chunk_x").getAsInt());
            assertEquals(chunks[i].z, json.get("chunk_z").getAsInt());
            assertFalse(json.has("spacing"));
            assertFalse(json.has("separation"));
            var placement = FixedStructurePlacement.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
            assertEquals(chunks[i], placement.getPotentialStructureChunk(42L, -99999, 99999));
            var encoded = FixedStructurePlacement.CODEC.codec().encodeStart(JsonOps.INSTANCE, placement).getOrThrow().getAsJsonObject();
            assertEquals(json.get("chunk_x"), encoded.get("chunk_x"));
            assertEquals(json.get("chunk_z"), encoded.get("chunk_z"));
            assertEquals(json.get("salt"), encoded.get("salt"));
        }
    }

    @Test
    void onlyTheFixedChunkGeneratesForEverySeedAndLocateSearchRegion() {
        for (var chunk : new ChunkPos[] {new ChunkPos(512, 512), new ChunkPos(0, 14)}) {
            var placement = new FixedStructurePlacement(chunk.x, chunk.z);
            assertInstanceOf(RandomSpreadStructurePlacement.class, placement,
                    "Vanilla locate only searches random-spread and concentric-ring placements");
            for (long seed : new long[] {0L, 42L, -1L, Long.MIN_VALUE, Long.MAX_VALUE}) {
                var state = ChunkGeneratorStructureState.createForFlat(null, seed, null, Stream.empty());
                assertTrue(placement.isStructureChunk(state, chunk.x, chunk.z));
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    if (dx != 0 || dz != 0) assertFalse(placement.isStructureChunk(state, chunk.x + dx, chunk.z + dz));
                }
                for (int x : new int[] {-1_000_000, -512, 0, 512, 1_000_000}) {
                    for (int z : new int[] {-1_000_000, -14, 0, 14, 1_000_000}) {
                        assertEquals(chunk, placement.getPotentialStructureChunk(seed, x, z));
                    }
                }
            }
            assertEquals(new BlockPos(chunk.getMinBlockX(), 0, chunk.getMinBlockZ()), placement.getLocatePos(chunk));
        }
    }

    @Test
    void generationStubsUseExactCoordinatesAndRejectEveryOtherChunk() {
        var settings = new Structure.StructureSettings(HolderSet.direct());
        var lab = new HighEnergyLabStructure(settings);
        var ruin = new EndWarpRuinStructure(settings);
        for (long seed : new long[] {0L, 42L, Long.MAX_VALUE}) {
            assertEquals(new BlockPos(8192, 162, 8192), lab.findGenerationPoint(context(HighEnergyLabStructure.START_CHUNK, seed))
                    .orElseThrow().position());
            assertEquals(EndWarpTravel.sourcePad(), ruin.findGenerationPoint(context(EndWarpRuinStructure.START_CHUNK, seed))
                    .orElseThrow().position());
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                assertTrue(lab.findGenerationPoint(context(new ChunkPos(512 + dx, 512 + dz), seed)).isEmpty());
                assertTrue(ruin.findGenerationPoint(context(new ChunkPos(dx, 14 + dz), seed)).isEmpty());
            }
            assertTrue(lab.findGenerationPoint(context(new ChunkPos(0, 0), seed)).isEmpty());
            assertTrue(ruin.findGenerationPoint(context(new ChunkPos(512, 512), seed)).isEmpty());
        }
    }

    @Test
    void landingAndCoreAnchorsStayWithinTheFixedTemplateFootprints() {
        assertEquals(new BlockPos(-20, 68, 204), EndWarpRuinStructure.ORIGIN);
        assertEquals(new BlockPos(0, 90, 224), EndWarpTravel.sourcePad());
        assertEquals(88, EndWarpRuinStructure.ORIGIN.getY() + 20);
        assertEquals(new BlockPos(8189, 163, 8245), HighEnergyLabStructure.LANDING_ORIGIN);
        assertEquals(new BlockPos(8192, 165, 8252), EndWarpTravel.labPad());
        for (int x : new int[] {0, HighEnergyLabStructure.LANDING_SIZE.getX() - 1}) {
            for (int z : new int[] {0, HighEnergyLabStructure.LANDING_SIZE.getZ() - 1}) {
                var local = HighEnergyLabStructure.LANDING_ORIGIN.offset(x, 0, z).subtract(HighEnergyLabStructure.ORIGIN);
                assertTrue(local.getX() >= 0 && local.getX() < HighEnergyLabStructure.SIZE.getX());
                assertTrue(local.getZ() >= 0 && local.getZ() < HighEnergyLabStructure.SIZE.getZ());
            }
        }
        assertFalse(EndWarpTemplatePiece.settings().isIgnoreEntities());
        assertEquals(Rotation.NONE, EndWarpTemplatePiece.settings().getRotation());
    }

    @Test
    void allOldLabRotationsStayWithinStructureReferenceRangeAndPreserveEntityPlacement() {
        var center = new BlockPos(83, 34, 87);
        for (var rotation : Rotation.values()) {
            var settings = HighEnergyLabPiece.settings(rotation);
            assertFalse(settings.isIgnoreEntities(), "Natural generation must retain the visual anchor");
            assertEquals(center, StructureTemplate.transform(center, Mirror.NONE, rotation, settings.getRotationPivot()));
            for (int x : new int[] {0, 162}) for (int z : new int[] {0, 168}) {
                var corner = StructureTemplate.transform(new BlockPos(x, 0, z), Mirror.NONE, rotation, settings.getRotationPivot());
                assertTrue(Math.abs(corner.getX() - 83) < 128);
                assertTrue(Math.abs(corner.getZ() - 87) < 128);
            }
        }
    }

    private static Structure.GenerationContext context(ChunkPos chunk, long seed) {
        return new Structure.GenerationContext(null, null, null, null, null, null, seed, chunk, null, biome -> true);
    }
}
