package com.atir.molecularmanipulator.recipelock;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RecipeExecutionContextTest {
    @BeforeAll
    static void bootstrap() { RecipeLockRecipeTest.bootstrap(); }

    @Test
    void nestedMachineScopesRestoreThePreviousActorAndRemoveTheOuterScope() {
        var furnace = new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
        var chest = new ChestBlockEntity(BlockPos.ZERO.above(), Blocks.CHEST.defaultBlockState());
        assertFalse(RecipeExecutionContext.active());
        assertNull(RecipeExecutionContext.actor());
        try (var outer = RecipeExecutionContext.open(furnace)) {
            assertTrue(RecipeExecutionContext.active());
            assertSame(furnace, RecipeExecutionContext.actor());
            try (var inner = RecipeExecutionContext.open(chest)) {
                assertSame(chest, RecipeExecutionContext.actor());
            }
            assertSame(furnace, RecipeExecutionContext.actor());
        }
        assertFalse(RecipeExecutionContext.active());
        assertNull(RecipeExecutionContext.actor());
    }

    @Test
    void failedExecutorRestoresItsCallerAndDoesNotLeaveAuthorizationOnTheThread() {
        var furnace = new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
        var other = new FurnaceBlockEntity(BlockPos.ZERO.east(), Blocks.FURNACE.defaultBlockState());
        try (var outer = RecipeExecutionContext.open(furnace)) {
            assertThrows(IllegalStateException.class, () -> {
                try (var inner = RecipeExecutionContext.open(other)) {
                    assertSame(other, RecipeExecutionContext.actor());
                    throw new IllegalStateException("Deliberately failed recipe executor");
                }
            });
            assertSame(furnace, RecipeExecutionContext.actor());
        }
        assertFalse(RecipeExecutionContext.active());
        assertNull(RecipeExecutionContext.actor());
    }

    @Test
    void anInvocationDoesNotGrantAnActorToAnotherThread() throws InterruptedException {
        var furnace = new FurnaceBlockEntity(BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
        var observedActor = new AtomicReference<Object>();
        var failure = new AtomicReference<Throwable>();
        try (var scope = RecipeExecutionContext.open(furnace)) {
            var thread = new Thread(() -> {
                try {
                    assertFalse(RecipeExecutionContext.active());
                    assertNull(RecipeExecutionContext.actor());
                    try (var childScope = RecipeExecutionContext.open(furnace)) {
                        observedActor.set(RecipeExecutionContext.actor());
                    }
                    assertNull(RecipeExecutionContext.actor());
                } catch (Throwable error) {
                    failure.set(error);
                }
            }, "recipe-lock-context-regression");
            thread.start();
            thread.join(5000);
            assertFalse(thread.isAlive(), "The isolated invocation must complete");
            assertNull(failure.get());
            assertSame(furnace, observedActor.get());
            assertSame(furnace, RecipeExecutionContext.actor(), "Closing another thread's scope cannot close this invocation");
        }
        assertFalse(RecipeExecutionContext.active());
    }
}
