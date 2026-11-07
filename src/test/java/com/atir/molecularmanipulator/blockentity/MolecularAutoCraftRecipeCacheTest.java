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
    void decodingUsesTheConstantTimeRecipeViewWithoutEnumeratingOrExpiringEveryTick() throws Exception {
        var type = new ClassNode();
        try (var input = MolecularAutoCrafter.class.getResourceAsStream("MolecularAutoCrafter.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(type, 0);
        }
        var decode = type.methods.stream().filter(method -> method.name.equals("decode")).findFirst().orElseThrow();
        int recipeViewReads = 0;
        boolean usesSlotCache = false;
        for (var instruction : decode.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                // RecipeManager.getRecipes() is its cached immutable values view,
                // not a recipe scan. Its identity also catches replaceRecipes calls
                // that do not publish a datapack/client synchronization event.
                if (call.owner.equals("net/minecraft/world/item/crafting/RecipeManager")
                        && call.name.equals("getRecipes")) recipeViewReads++;
                assertNotEquals("getGameTime", call.name, "Decoded patterns must survive tick changes");
                assertFalse(isEnumeration(call), "Cache hits must not scan or copy recipes: " + call.name);
                usesSlotCache |= call.owner.endsWith("/MolecularAutoCraftPatternCache") && call.name.equals("get");
            }
        }
        assertEquals(1, recipeViewReads, "Use the O(1) recipe-view identity to invalidate successful and failed decodes");
        assertTrue(usesSlotCache, "Retain the per-host slot cache rather than restoring the legacy decoded-pattern map");
    }

    private static boolean isEnumeration(MethodInsnNode call) {
        return switch (call.name) {
            case "iterator", "stream", "parallelStream", "forEach", "toArray",
                    "copyOf", "getAllRecipesFor", "getAllRecipesForType" -> true;
            default -> false;
        };
    }

    @Test
    void completedServerDatapackReloadAdvancesTheSharedGeneration() {
        long before = PatternRecipeReloads.generation();
        PatternRecipeReloads.onDatapackSync(new OnDatapackSyncEvent(null, null));
        assertEquals(before + 1, PatternRecipeReloads.generation());
    }

    @Test
    void clientRecipeSynchronizationAdvancesTheSharedGeneration() {
        long before = PatternRecipeReloads.generation();
        PatternRecipeReloads.Client.onRecipesUpdated(new RecipesUpdatedEvent(null));
        assertEquals(before + 1, PatternRecipeReloads.generation());
    }
}
