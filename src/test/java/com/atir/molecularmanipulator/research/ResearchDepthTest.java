package com.atir.molecularmanipulator.research;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResearchDepthTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void decodesLegacySpeedFieldsWithoutApplyingThem() {
        for (var legacy : List.of("\"speed_multiplier\":\"256\"", "\"processing_ticks\":1",
                "\"speed_multiplier\":\"9223372036854775807\",\"processing_ticks\":1")) {
            var depth = ResearchDepth.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString("{\"parallel\":\"1024\"," + legacy + "}")).getOrThrow();
            assertEquals(1024, depth.parallel());
            assertEquals(400, depth.ticks(400));
            assertEquals(73, depth.ticks(73));
            assertEquals(1, depth.ticks(0));
        }
    }

    @Test
    void allNineDefaultRoundsOnlyGrantParallelism() {
        assertEquals(9, ResearchDepth.DEFAULTS.size());
        for (int round = 0; round < 9; round++) {
            var depth = ResearchDepth.DEFAULTS.get(round);
            assertEquals(1L << round, depth.materialMultiplier());
            assertEquals(round == 8 ? Long.MAX_VALUE : 1L << (round * 8), depth.parallel());
            assertEquals(1, depth.speedMultiplier());
            assertEquals(0, depth.processingTicks());
            assertEquals(400, depth.ticks(400));
        }
    }
}
