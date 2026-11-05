package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.starcomputation.EntropyArmillaryLayout;
import net.minecraft.core.Direction;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class EntropyArmillaryRendererTest {
    @Test
    void rendererRotationMatchesEveryFunctionalSlot() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var q = new Quaternionf().rotationY((float)Math.toRadians(EntropyArmillaryRenderer.facingAngle(facing)));
            for (int ring = 0; ring < 9; ring++) for (int slot = 0; slot < EntropyArmillaryLayout.slotCount(ring); slot++) {
                var north = EntropyArmillaryLayout.offset(Direction.NORTH,ring,slot);
                var actual = q.transform(new Vector3f(north.getX(),north.getY(),north.getZ()));
                var expected = EntropyArmillaryLayout.offset(facing,ring,slot);
                assertEquals(expected.getX(),actual.x,1e-4);
                assertEquals(expected.getY(),actual.y,1e-4);
                assertEquals(expected.getZ(),actual.z,1e-4);
            }
        }
    }

    @Test
    void anchorCameraUsesTheInverseFixedFacingWithoutAnyRingAnimation() {
        var local = new Vector3f(13.25F, -2.5F, 29.75F);
        for (var facing : Direction.Plane.HORIZONTAL) {
            var world = new Quaternionf().rotationY((float) Math.toRadians(EntropyArmillaryRenderer.facingAngle(facing)))
                    .transform(new Vector3f(local));
            var actual = EntropyArmillaryRenderer.cameraInLayout(facing,
                    new net.minecraft.world.phys.Vec3(world.x, world.y, world.z));
            assertEquals(local.x, actual.x, 1E-5);
            assertEquals(local.y, actual.y, 1E-5);
            assertEquals(local.z, actual.z, 1E-5);
        }
    }

    @Test
    void normalTransformUsesTheSameRingPlanesAsTheStructure() {
        for (int ring = 0; ring < 9; ring++) {
            double yaw = EntropyArmillaryLayout.yaw(ring),pitch = EntropyArmillaryLayout.pitch(ring);
            var normal = EntropyArmillaryRenderer.normalRotation(yaw,pitch).transform(new Vector3f(0,1,0));
            assertEquals(Math.cos(Math.toRadians(pitch))*Math.cos(Math.toRadians(yaw)),normal.x,1e-5);
            assertEquals(Math.sin(Math.toRadians(pitch)),normal.y,1e-5);
            assertEquals(Math.cos(Math.toRadians(pitch))*Math.sin(Math.toRadians(yaw)),normal.z,1e-5);
        }
    }

    @Test
    void floatingPedestalReplacesTheSolidSkinWithoutRestoringRemovedDecorations() {
        var parts = java.util.Arrays.asList(EntropyArmillaryRenderer.PARTS);
        assertFalse(parts.contains("pendant"), "Hanging ornaments are embedded into the belts instead");
        assertTrue(parts.containsAll(java.util.List.of("floating_pedestal", "floating_pedestal_glow",
                "floating_pedestal_dial", "floating_pedestal_dial_glow", "floating_front_gem")));
        assertFalse(parts.contains("floating_pedestal_petals"), "No inverted crystal supports below the well");
        assertFalse(parts.contains("pedestal_skin"), "Do not draw the solid pedestal behind the floating artwork");
        assertFalse(parts.contains("front_gem"), "The old gem was positioned on the removed solid rim");
        assertTrue(parts.contains("energy_well"));
        assertFalse(parts.contains("floor_disk"), "No black decorative floor");
        assertTrue(parts.stream().noneMatch(part -> part.startsWith("crystal_cluster")),
                "No non-colliding perimeter clusters");
        assertTrue(parts.containsAll(java.util.List.of("well_beams", "energy_ring", "float_crystal", "gear")));
        assertTrue(parts.stream().noneMatch(part -> java.util.Set.of("galaxy_disk", "nebula_sphere", "halo",
                "star", "planet", "moon").contains(part)), "The new procedural nebula must not restore the old halo meshes or orbiting ornaments");
        assertEquals(parts.size(), java.util.Set.copyOf(parts).size(), "No duplicate registrations");
    }

    @Test
    void retiredCrystalSupportsAreNeitherRegisteredNorDrawn() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/atir/molecularmanipulator/client/render/EntropyArmillaryRenderer.java"));
        assertFalse(source.contains("\"floating_pedestal_petals\""));
        assertTrue(source.contains("\"floating_front_gem\", FULL"), "Keep the front jewel");
        assertTrue(source.contains("\"float_crystal\", FULL"), "Keep the separate upper and inset ornaments");
    }

    @Test
    void openWellDropsOnlyItsSolidCapAndPreservesTheMetalRimAndOtherParts() {
        for (String form : EntropyArmillaryRenderer.FORMS) {
            var fill = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("molecularmanipulator",
                    "block/entropy_armillary/" + form + "/wellglow");
            var metal = fill.withPath("block/entropy_armillary/" + form + "/metal");
            assertFalse(EntropyArmillaryRenderer.keepWellMaterial("energy_well", fill));
            assertTrue(EntropyArmillaryRenderer.keepWellMaterial("energy_well", metal));
            for (String part : EntropyArmillaryRenderer.PARTS) {
                if (!part.equals("energy_well")) assertTrue(EntropyArmillaryRenderer.keepWellMaterial(part, fill));
            }
        }
    }

    @Test
    void brighterPedestalTexturesRetainSceneLightingInsteadOfFullBrightMetal() throws Exception {
        String source = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/atir/molecularmanipulator/client/render/EntropyArmillaryRenderer.java"));
        for (String part : java.util.List.of("floating_pedestal", "floating_pedestal_dial")) {
            assertTrue(source.contains("\"" + part + "\", packedLight, false, 1)"));
            assertFalse(source.contains("\"" + part + "\", FULL"));
        }
        assertTrue(source.contains("tierForm, \"ring_\" + (ring + 1), packedLight, false, 1)"),
                "Keep the visible belts lit by the scene");
    }

    @Test
    void coloredEffectsUseTerrainFogWithoutChangingOtherMachinesColorLayers() throws Exception {
        var root = java.nio.file.Path.of("src/main/resources/assets/molecularmanipulator/shaders/core");
        var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(root.resolve("armillary_color.json")))
                .getAsJsonObject();
        var uniforms = json.getAsJsonArray("uniforms").asList().stream()
                .map(e -> e.getAsJsonObject().get("name").getAsString()).collect(java.util.stream.Collectors.toSet());
        assertTrue(uniforms.containsAll(java.util.Set.of("FogStart", "FogEnd", "FogShape")));
        String vertex = java.nio.file.Files.readString(root.resolve("armillary_color.vsh"));
        String fragment = java.nio.file.Files.readString(root.resolve("armillary_color.fsh"));
        assertTrue(vertex.contains("fog_distance(Position, FogShape)"), "Fog distance is in unrotated camera-relative space");
        assertTrue(fragment.contains("color.a *= linear_fog_fade(vertexDistance, FogStart, FogEnd)"));
        assertTrue(fragment.contains("if (color.a <= 0.0) discard"), "Fully fogged effects must not occlude the scene");
        String renderer = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/atir/molecularmanipulator/client/render/EntropyArmillaryRenderer.java"));
        assertTrue(renderer.contains("OmniRenderLayers.armillaryCrystal(buffers)"));
        assertTrue(renderer.contains("OmniRenderLayers.armillaryGlow(buffers)"));
    }

    @Test
    void sodiumExtrasExemptionIsRegisteredBeforeItsPerTypeCullingDecisionIsCached() throws Exception {
        String compatibility = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/atir/molecularmanipulator/client/SingularityRenderCompatibility.java"));
        assertTrue(compatibility.contains("allow(config, \"tileEntityWhitelist\", \"molecularmanipulator:star_computation_engine_core\")"),
                "SodiumExtras ignores renderer bounds and would otherwise cancel draws beyond 64 horizontal or 32 vertical blocks");
        String setup = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/atir/molecularmanipulator/client/ClientEvents.java"));
        assertTrue(setup.contains("event.enqueueWork(SingularityRenderCompatibility::initialize)"),
                "Register before entering a world: SodiumExtras caches its decision per block entity type");
    }

    @Test
    void reusedRingPlaneNormalMatchesTheAllocatingVariant() {
        var target = new Quaternionf();
        for (int ring = 0; ring < 9; ring++) {
            double yaw = EntropyArmillaryLayout.yaw(ring), pitch = EntropyArmillaryLayout.pitch(ring);
            EntropyArmillaryRenderer.applyNormal(target, yaw, pitch);
            assertEquals(EntropyArmillaryRenderer.normalRotation(yaw, pitch), new Quaternionf(target));
        }
    }
}
