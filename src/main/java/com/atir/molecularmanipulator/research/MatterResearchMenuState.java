package com.atir.molecularmanipulator.research;

import appeng.menu.guisync.PacketWritable;
import net.minecraft.network.FriendlyByteBuf;

/** Larger than AE2's default 32K string field for data packs with many research tasks. */
public record MatterResearchMenuState(String json) implements PacketWritable {
    private static final int MAX_LENGTH = 262_144;
    public MatterResearchMenuState(FriendlyByteBuf buffer) { this(buffer.readUtf(MAX_LENGTH)); }
    @Override public void writeToPacket(FriendlyByteBuf buffer) { buffer.writeUtf(json, MAX_LENGTH); }
}
