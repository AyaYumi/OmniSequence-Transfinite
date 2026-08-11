package com.atir.molecularmanipulator.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MixinRefmapConfigurationTest {
    @Test
    void productionMixinConfigurationLoadsGeneratedRefmap() {
        String resource = "/molecularmanipulator.mixins.json";
        try (var stream = MixinRefmapConfigurationTest.class
                .getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing Mixin configuration: " + resource);
            try (var reader = new InputStreamReader(stream,
                    StandardCharsets.UTF_8)) {
                var config = JsonParser.parseReader(reader).getAsJsonObject();
                var refmap = config.get("refmap");
                assertNotNull(refmap,
                        "Mixin configuration must declare the generated refmap");
                assertEquals("molecularmanipulator.refmap.json",
                        refmap.getAsString(),
                        "Production Mixin targets require the generated refmap");
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not read " + resource, exception);
        }
    }
}
