package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.util.MathCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

/** Persistent controller-local override. INHERIT is absence, not a fourth effect tier. */
public final class MachineEffectSettings {
    public static final int INHERIT = -1;
    public static final int OFF = 0;
    public static final int REDUCED = 1;
    public static final int FULL = 2;
    public static final String NBT_KEY = "omni_dynamic_effect_level";

    private int override = INHERIT;

    public int getOverride() {
        return override;
    }

    public static boolean isValidOverride(int value) {
        return value >= INHERIT && value <= FULL;
    }

    /** Reject malformed requests rather than turning them into a different valid setting. */
    public boolean setOverride(int value) {
        if (!isValidOverride(value) || override == value) return false;
        override = value;
        return true;
    }

    public static int resolve(int override, int clientDefault) {
        return override >= OFF && override <= FULL ? override : MathCompat.clamp(clientDefault, OFF, FULL);
    }

    public static int nextLevel(int override, int clientDefault) {
        return (resolve(override, clientDefault) + 1) % (FULL + 1);
    }

    public void save(CompoundTag tag) {
        if (override == INHERIT) tag.remove(NBT_KEY);
        else tag.putInt(NBT_KEY, override);
    }

    /** Old, missing, wrongly typed or corrupt data must retain the client's global default. */
    public void load(CompoundTag tag) {
        int stored = tag.contains(NBT_KEY, Tag.TAG_INT) ? tag.getInt(NBT_KEY) : INHERIT;
        override = isValidOverride(stored) ? stored : INHERIT;
    }

    public void write(FriendlyByteBuf data) {
        data.writeByte(override);
    }

    public boolean read(FriendlyByteBuf data) {
        int stored = data.readByte();
        return setOverride(isValidOverride(stored) ? stored : INHERIT);
    }
}
