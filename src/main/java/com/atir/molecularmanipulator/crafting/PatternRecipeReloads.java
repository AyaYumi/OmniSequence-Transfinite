package com.atir.molecularmanipulator.crafting;

import com.atir.molecularmanipulator.MolecularManipulator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

import java.util.concurrent.atomic.AtomicLong;

/** Invalidates passive pattern decodes after server reloads and client recipe synchronization. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class PatternRecipeReloads {
    private static final AtomicLong GENERATION = new AtomicLong();

    private PatternRecipeReloads() { }

    public static long generation() {
        return GENERATION.get();
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        // A joining player does not change server recipes. A full sync follows
        // a completed reload, including tag changes used by crafting ingredients.
        if (event.getPlayer() == null) {
            GENERATION.incrementAndGet();
        }
    }

    // Keep client event types out of the dedicated-server subscriber.
    @EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
    public static final class Client {
        private Client() { }

        @SubscribeEvent
        public static void onRecipesUpdated(net.neoforged.neoforge.client.event.RecipesUpdatedEvent event) {
            GENERATION.incrementAndGet();
        }
    }
}
