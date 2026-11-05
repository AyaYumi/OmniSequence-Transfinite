package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class HighEnergyLabEffectsTest {
    @Test
    void powerOffSubmitsNoFilamentsOrSupplyLines() {
        for (boolean detailed : new boolean[] {false,true}) {
            var solid = new RecordingColorConsumer();
            var glow = new RecordingColorConsumer();
            HighEnergyLabEffects.render(new PoseStack(),solid,false,3,detailed,false);
            HighEnergyLabEffects.render(new PoseStack(),glow,false,3,detailed,true);
            assertTrue(solid.vertices().isEmpty());
            assertTrue(glow.vertices().isEmpty());
            HighEnergyLabEffects.render(new PoseStack(),solid,true,3,detailed,false);
            HighEnergyLabEffects.render(new PoseStack(),glow,true,3,detailed,true);
            assertFalse(solid.vertices().isEmpty());
            assertFalse(glow.vertices().isEmpty());
        }
    }

    @Test
    void solidPassOnlyContainsTheExperimentSupplyWithoutOpaqueHoleBodiesOrRims() {
        var out = new RecordingColorConsumer();
        HighEnergyLabEffects.render(new PoseStack(),out,true,3,true,false);
        assertFalse(out.vertices().isEmpty());
        assertTrue(out.vertices().stream().allMatch(v -> Math.abs(v.x()) < .3));
        assertTrue(out.vertices().stream().allMatch(v -> v.y() >= 7.8 && v.y() <= 11.2));
        assertTrue(out.vertices().stream().allMatch(v -> v.z() >= 1.8 && v.z() <= 39.2));
        assertEquals(0,out.vertices().size()%4);
    }

    @Test
    void modernSupplyStartsAtThePlatformContactAndStillStopsWithoutPower() {
        var out = new RecordingColorConsumer();
        HighEnergyLabEffects.render(new PoseStack(),out,false,3,true,false,true);
        assertTrue(out.vertices().isEmpty());
        HighEnergyLabEffects.render(new PoseStack(),out,true,3,true,false,true);
        assertTrue(out.vertices().stream().anyMatch(v -> v.z() > 45));
        assertTrue(out.vertices().stream().anyMatch(v -> v.y() < 2.5));
        assertTrue(out.vertices().stream().allMatch(v -> Math.abs(v.x()) < .3 && v.z() <= 45.5));
        assertEquals(0,out.vertices().size()%4);
    }

    @Test
    void surroundingFilamentsStayNearTheHolesWithoutVerticalRingsOrLongAxialJets() {
        for (float time : new float[] {0,3,12}) {
            var out = new RecordingColorConsumer();
            HighEnergyLabEffects.holeLight(new PoseStack(),out,time,true);
            assertTrue(out.vertices().stream().allMatch(v -> v.y() >= 8 && v.y() <= 16));
            assertTrue(out.vertices().stream().allMatch(v -> Math.abs(v.x()) < 29 && Math.abs(v.z()) < 15));
        }
    }

    @Test
    void rendererRestoresTheCallerPoseAndReducedDetailStillShowsTheFilaments() {
        var poses = new PoseStack();
        poses.translate(2,4,-5); poses.mulPose(Axis.YP.rotationDegrees(90));
        var before = new Matrix4f(poses.last().pose());
        var detailed = new RecordingColorConsumer();
        var reduced = new RecordingColorConsumer();
        HighEnergyLabEffects.render(poses,detailed,true,3,true,true);
        HighEnergyLabEffects.render(poses,reduced,true,3,false,true);
        assertEquals(before,poses.last().pose());
        assertFalse(reduced.vertices().isEmpty());
        assertTrue(detailed.vertices().size() > reduced.vertices().size());
    }
}
