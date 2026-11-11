package com.atir.molecularmanipulator.starcomputation;

import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeManager;

/** Only recipe/registry access is initialized; never starts a client, server thread, chunks or event bus. */
public final class StarResearchTestLevel extends ServerLevel {
    private RecipeManager recipes;
    private RegistryAccess registries;

    private StarResearchTestLevel() {
        super(null, null, null, null, null, null, null, false, 0, List.of(), false, null);
        throw new AssertionError("Allocate the headless recipe-only level with create()");
    }

    public static StarResearchTestLevel create(RecipeManager recipes, RegistryAccess registries) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var level = (StarResearchTestLevel) ((sun.misc.Unsafe) field.get(null)).allocateInstance(StarResearchTestLevel.class);
        level.recipes = recipes;
        level.registries = registries;
        return level;
    }

    @Override public RecipeManager getRecipeManager() { return recipes; }
    @Override public RegistryAccess registryAccess() { return registries; }
}
