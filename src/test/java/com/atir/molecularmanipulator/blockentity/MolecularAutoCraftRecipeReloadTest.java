package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.junit.jupiter.api.Test;

/** Guards the actual 1.21.1 recipe-view identity used by the hot-path cache. */
class MolecularAutoCraftRecipeReloadTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void recipeManagerReusesTheViewUntilSameIdRecipesAreReplaced() {
        var manager = new RecipeManager(RegistryAccess.EMPTY);
        var id = ResourceLocation.fromNamespaceAndPath("molecularmanipulator", "cache_reload_test");
        var oldRecipe = recipe(id, new ItemStack(Items.OAK_PLANKS, 4));
        manager.replaceRecipes(List.of(oldRecipe));
        var source = manager.getRecipes();
        var cache = new MolecularAutoCraftPatternCache<String, RecipeHolder<?>>();
        var level = new Object();
        var decodes = new AtomicInteger();

        for (int read = 0; read < 100; read++) {
            assertSame(source, manager.getRecipes(), "getRecipes must not rebuild a collection on each read");
            assertSame(oldRecipe, cache.get(0, "same_pattern", level, manager.getRecipes(), () -> {
                decodes.incrementAndGet();
                return manager.byKey(id).orElseThrow();
            }));
        }
        assertEquals(1, decodes.get());

        var newRecipe = recipe(id, new ItemStack(Items.OAK_PLANKS, 8));
        manager.replaceRecipes(List.of(newRecipe));
        assertEquals(source.size(), manager.getRecipes().size());
        assertNotSame(source, manager.getRecipes(), "Same IDs/counts must still invalidate on replacement");
        assertSame(newRecipe, cache.get(0, "same_pattern", level, manager.getRecipes(), () -> {
            decodes.incrementAndGet();
            return manager.byKey(id).orElseThrow();
        }));
        assertEquals(2, decodes.get());

        manager.replaceRecipes(List.of());
        assertNull(cache.get(0, "same_pattern", level, manager.getRecipes(),
                () -> manager.byKey(id).orElse(null)), "Removed recipes must not keep crafting from stale details");

        manager.replaceRecipes(List.of(newRecipe));
        assertSame(newRecipe, cache.get(0, "same_pattern", level, manager.getRecipes(),
                () -> manager.byKey(id).orElseThrow()), "Restored recipes must retry a cached invalid pattern");
    }

    private static RecipeHolder<ShapelessRecipe> recipe(ResourceLocation id, ItemStack output) {
        return new RecipeHolder<>(id, new ShapelessRecipe("", CraftingBookCategory.MISC,
                output, NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.OAK_LOG))));
    }
}
