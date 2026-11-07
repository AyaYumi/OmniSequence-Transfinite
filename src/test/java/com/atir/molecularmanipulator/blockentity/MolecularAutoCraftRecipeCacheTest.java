package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.crafting.PatternRecipeReloads;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.*;

class MolecularAutoCraftRecipeCacheTest {
    @Test
    void decodingDoesNotEnumerateRecipesOrExpireEveryTick() throws Exception {
        var type = new ClassNode();
        try (var input = MolecularAutoCrafter.class.getResourceAsStream("MolecularAutoCrafter.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(type, 0);
        }
        var decode = type.methods.stream().filter(method -> method.name.equals("decode")).findFirst().orElseThrow();
        boolean checksReload = false;
        for (var instruction : decode.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                assertNotEquals("getRecipes", call.name, "Cache hits must never enumerate recipes");
                assertNotEquals("getGameTime", call.name, "Decoded patterns must survive tick changes");
                checksReload |= call.owner.endsWith("/PatternRecipeReloads") && call.name.equals("generation");
            }
        }
        assertTrue(checksReload, "Recipe reloads must invalidate both successful and failed decodes");
    }

    @Test
    void completedServerDatapackReloadInvalidatesPatterns() {
        long before = PatternRecipeReloads.generation();
        PatternRecipeReloads.onDatapackSync(new OnDatapackSyncEvent(null, null));
        assertEquals(before + 1, PatternRecipeReloads.generation());
    }

    @Test
    void clientRecipeSynchronizationInvalidatesPatterns() {
        long before = PatternRecipeReloads.generation();
        PatternRecipeReloads.Client.onRecipesUpdated(new RecipesUpdatedEvent(null));
        assertEquals(before + 1, PatternRecipeReloads.generation());
    }
}
