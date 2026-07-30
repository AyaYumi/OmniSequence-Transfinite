package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.menu.MolecularCenterMenu;
import com.atir.molecularmanipulator.network.PatternSearchIndexPayload;
import net.minecraft.client.Minecraft;

public final class PatternSearchIndexClientHandler {
    private PatternSearchIndexClientHandler() {
    }

    public static void handle(PatternSearchIndexPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player == null
                || !(player.containerMenu instanceof MolecularCenterMenu menu)
                || menu.containerId != payload.containerId()) {
            return;
        }
        menu.acceptClientPatternSearchChunk(
                payload.revision(),
                payload.reset(),
                payload.complete(),
                payload.entries());
    }
}
