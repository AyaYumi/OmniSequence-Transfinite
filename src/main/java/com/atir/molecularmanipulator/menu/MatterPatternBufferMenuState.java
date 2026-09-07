package com.atir.molecularmanipulator.menu;

import appeng.api.stacks.GenericStack;
import appeng.menu.guisync.PacketWritable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Only the visible cache page is synchronized, regardless of the number of stored types. */
public record MatterPatternBufferMenuState(int page, int pages, int itemTypes, int fluidTypes,
        int queuedPatterns, boolean processing, boolean unavailable, List<GenericStack> contents) implements PacketWritable {
    public static final int PAGE_SIZE = 4;
    public static final MatterPatternBufferMenuState EMPTY = new MatterPatternBufferMenuState(0, 1, 0, 0, 0, false, false, List.of());
    public MatterPatternBufferMenuState { contents = List.copyOf(contents); }
    public MatterPatternBufferMenuState(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readBoolean(), readContents(buffer));
    }
    private static List<GenericStack> readContents(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > PAGE_SIZE) throw new IllegalArgumentException("Invalid pattern buffer page size");
        var values = new ArrayList<GenericStack>(size);
        for (int i = 0; i < size; i++) values.add(GenericStack.STREAM_CODEC.decode(buffer));
        return values;
    }
    @Override public void writeToPacket(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(page); buffer.writeVarInt(pages); buffer.writeVarInt(itemTypes); buffer.writeVarInt(fluidTypes); buffer.writeVarInt(queuedPatterns);
        buffer.writeBoolean(processing); buffer.writeBoolean(unavailable); buffer.writeVarInt(contents.size());
        for (var stack : contents) GenericStack.STREAM_CODEC.encode(buffer, stack);
    }
}
