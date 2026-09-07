package com.atir.molecularmanipulator.research;

import static org.junit.jupiter.api.Assertions.*;
import io.netty.buffer.Unpooled;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ResearchVisualStateTest {
    @Test void customStagesAreStableButUseAllStylesAcrossRoundsAndMachines() {
        var id = ResourceLocation.parse("kubejs:custom_research");
        var variants = new HashSet<Integer>();
        for (int round = 1; round <= 32; round++) {
            long identity = ResearchVisualState.identity(id, round, 9134);
            assertEquals(identity, ResearchVisualState.identity(id, round, 9134));
            assertNotEquals(identity, ResearchVisualState.identity(id, round, 9135));
            variants.add(ResearchVisualState.style(id, identity));
        }
        assertEquals(4, variants.size());
        var builtin = ResourceLocation.parse("molecularmanipulator:research/omni_computation");
        assertEquals(2, ResearchVisualState.style(builtin, 0));
        assertEquals(2, ResearchVisualState.style(builtin, Long.MAX_VALUE));
    }

    @Test void pausedAndStalePacketsDoNotExtrapolateUnlimitedProgress() {
        var paused = new ResearchVisualState.Task(0, 0, 1, 40, 100, false);
        var running = new ResearchVisualState.Task(0, 0, 1, 40, 100, true);
        assertEquals(0.4F, paused.fraction(1000));
        assertEquals(0.45F, running.fraction(1000));
        assertEquals(1F, new ResearchVisualState.Task(0, 0, 1, 99, 100, true).fraction(5));
    }

    @Test void manyCustomTasksHaveABoundedLosslessVisualPacket() {
        var tasks = IntStream.range(0, 200).mapToObj(i -> new ResearchVisualState.Task(
                Long.MAX_VALUE - i, i % 4, Integer.MAX_VALUE, 10, Integer.MAX_VALUE, i % 2 == 0)).toList();
        var state = new ResearchVisualState(tasks, 24, 3);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            state.write(buffer);
            assertTrue(buffer.readableBytes() < 160);
            assertEquals(4, state.tasks().size());
            assertEquals(state, ResearchVisualState.read(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
