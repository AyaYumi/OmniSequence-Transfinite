package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;
import com.atir.molecularmanipulator.client.render.MatterStellarEffects;
import com.atir.molecularmanipulator.client.render.RecordingColorConsumer;
import com.atir.molecularmanipulator.research.ResearchVisualState;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class MatterStellarEffectsTest {
    @Test void rapidCraftsKeepContinuousMotionAndThenFadeOut() {
        var motion = new MatterStellarEffects.Animation();
        float previous = -1;
        for (int tick = 0; tick < 200; tick++) {
            var frame = motion.sample(tick, tick % 2 == 0, 0.6F, ResearchVisualState.EMPTY, 0, 0);
            assertTrue(frame.productionTime() > previous);
            previous = frame.productionTime();
            if (tick > 40) assertTrue(frame.activity() > 0.99F);
        }
        MatterStellarEffects.Frame last = null;
        for (int tick = 200; tick < 300; tick++) last = motion.sample(tick, false, 0, ResearchVisualState.EMPTY, 0, 0);
        assertNotNull(last);
        assertTrue(last.activity() < 0.001F);
    }

    @Test void pausedResearchGeometryFreezesAndEachStyleIsDistinct() {
        var distinct = new java.util.HashSet<List<RecordingColorConsumer.Vertex>>();
        for (int style = 0; style < 4; style++) {
            var state = new ResearchVisualState(List.of(new ResearchVisualState.Task(15, style, 9, 40, 100, false)), 0, 0);
            var a = vertices(new MatterStellarEffects.Frame(0, 0, state, 0, 0), true);
            var b = vertices(new MatterStellarEffects.Frame(0, 900, state, 5, 0), true);
            assertEquals(a, b);
            assertFalse(a.isEmpty());
            distinct.add(a);
        }
        assertEquals(4, distinct.size());
    }

    @Test void simultaneousMaximumDepthEffectsStayInsideTheChamberAndBounded() {
        var tasks = IntStream.range(0, 4).mapToObj(i -> new ResearchVisualState.Task(i, i, Integer.MAX_VALUE,
                50, 100, true)).toList();
        var frame = new MatterStellarEffects.Frame(1, 1234, new ResearchVisualState(tasks, 3, 2), 2.5, 0.5F);
        var full = vertices(frame, true);
        assertTrue(full.size() < 60000, "Bounded work even at maximum research level: " + full.size());
        assertTrue(vertices(frame, false).size() < full.size());
        for (var vertex : full) {
            assertTrue(Math.abs(vertex.x()) < 5 && Math.abs(vertex.z()) < 5);
            assertTrue(vertex.y() > -5 && vertex.y() < 8);
        }
        var absent = new RecordingColorConsumer();
        MatterFabricationRenderer.renderFabricationPass(new PoseStack(), absent, 100, true, false, Direction.NORTH,
                MatterFabricationRenderer.FoundryPass.FRAME_DEPTH,
                new com.atir.molecularmanipulator.client.render.MatterRasterEffects.Frame(0, 1, 0.5F, true, true, 0, 0), frame);
        assertTrue(absent.vertices().isEmpty(), "Unformed structures must not show research or production effects");
    }

    private static List<RecordingColorConsumer.Vertex> vertices(MatterStellarEffects.Frame frame, boolean detailed) {
        var consumer = new RecordingColorConsumer();
        MatterStellarEffects.render(new PoseStack(), consumer, frame, detailed, false);
        MatterStellarEffects.render(new PoseStack(), consumer, frame, detailed, true);
        return consumer.vertices();
    }
}
