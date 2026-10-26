package com.atir.molecularmanipulator.client.render;

import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/** Shared client model-data key for the formed negative-entropy engine. */
public final class NegativeEntropyEngineModelData {
    public static final ModelProperty<Snapshot> PROPERTY = new ModelProperty<>();
    public static final Snapshot NONE = new Snapshot(0, 0);

    private NegativeEntropyEngineModelData() {
    }

    public static ModelData of(int tier, int connections) {
        return ModelData.of(PROPERTY, new Snapshot(tier, connections));
    }

    public record Snapshot(int tier, int connections) {
        public Snapshot {
            tier = Math.clamp(tier, 0, 3);
            connections &= 0xF;
        }
    }
}
