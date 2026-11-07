package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.crafting.PatternRecipeReloads;
import net.minecraftforge.client.event.RecipesUpdatedEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.*;

class MolecularAutoCraftRecipeCacheTest {
    @Test
    void decodingDoesNotEnumerateRecipesOrExpireEveryTick() throws Exception {
        var type = bytecode(MolecularAutoCrafter.class);
        var decode = type.methods.stream().filter(method -> method.name.equals("decode")).findFirst().orElseThrow();
        boolean checksReload = false;
        boolean checksRecipeIdentity = false;
        for (var instruction : decode.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                assertNotEquals("getRecipes", call.name, "Cache hits must never enumerate recipes");
                assertNotEquals("getGameTime", call.name, "Decoded patterns must survive tick changes");
                checksReload |= call.owner.endsWith("/PatternRecipeReloads") && call.name.equals("generation");
                checksRecipeIdentity |= call.owner.endsWith("/RecipeManagerAccessor")
                        && call.name.equals("molecularmanipulator$getRecipesByName");
            }
        }
        assertTrue(checksReload, "Recipe/tag reloads must invalidate both successful and failed decodes");
        assertTrue(checksRecipeIdentity, "Recipe map replacement must be detected without scanning its contents");
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

    @Test
    void clientEventTypesAreIsolatedFromTheDedicatedServerSubscriber() throws Exception {
        // Reading the annotation reflectively initializes Forge's Bus enum and
        // FMLLoader, which plain JUnit deliberately does not bootstrap.
        var subscriber = bytecode(PatternRecipeReloads.Client.class).visibleAnnotations.stream()
                .filter(annotation -> annotation.desc.equals("Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;"))
                .findFirst().orElseThrow();
        int valueIndex = subscriber.values.indexOf("value");
        assertTrue(valueIndex >= 0, "The client subscriber must declare its physical distribution");
        var distributions = (java.util.List<?>) subscriber.values.get(valueIndex + 1);
        assertEquals(1, distributions.size());
        assertArrayEquals(new String[]{"Lnet/minecraftforge/api/distmarker/Dist;", "CLIENT"},
                (String[]) distributions.get(0));
        for (var method : bytecode(PatternRecipeReloads.class).methods) {
            assertFalse(method.desc.contains("net/minecraftforge/client/"),
                    "The common subscriber must not resolve client-only event types");
        }
    }

    private static ClassNode bytecode(Class<?> type) throws Exception {
        var node = new ClassNode();
        try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }
}
