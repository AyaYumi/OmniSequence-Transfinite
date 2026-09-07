package com.atir.molecularmanipulator.block;

public enum MatterFabricationPortType {
    ITEM_INPUT(true, false),
    ITEM_OUTPUT(true, true),
    FLUID_INPUT(false, false),
    FLUID_OUTPUT(false, true);

    private final boolean item;
    private final boolean output;

    MatterFabricationPortType(boolean item, boolean output) {
        this.item = item;
        this.output = output;
    }

    public boolean isItem() {
        return item;
    }

    public boolean isFluid() {
        return !item;
    }

    public boolean isInput() {
        return !output;
    }

    public boolean isOutput() {
        return output;
    }
}
