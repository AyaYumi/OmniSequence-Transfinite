package com.atir.molecularmanipulator.recipelock;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** Invocation-local actor identity. Never retains a grant, network, team-progress snapshot or crafting job. */
public final class RecipeExecutionContext {
    private static final ThreadLocal<Object> CURRENT = new ThreadLocal<>();

    private RecipeExecutionContext() {}

    public static Scope open(Player player) { return openActor(player); }
    public static Scope open(BlockEntity machine) { return openActor(machine); }
    public static boolean active() { return CURRENT.get() != null; }
    @Nullable static Object actor() { return CURRENT.get(); }

    private static Scope openActor(Object actor) {
        var scope = new Scope(CURRENT.get());
        CURRENT.set(actor);
        return scope;
    }

    public static final class Scope implements AutoCloseable {
        private final Object previous;

        private Scope(@Nullable Object previous) { this.previous = previous; }

        @Override public void close() {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
