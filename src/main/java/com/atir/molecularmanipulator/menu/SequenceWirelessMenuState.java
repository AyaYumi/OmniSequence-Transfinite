package com.atir.molecularmanipulator.menu;

import appeng.menu.guisync.PacketWritable;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity.QuantumLinkState;
import com.atir.molecularmanipulator.integration.ae2.SequenceWirelessBinding;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/** One authorized page, not the server's global hub directory. */
public record SequenceWirelessMenuState(int page, int pages, boolean canConfigure, boolean bound,
        Entry current, QuantumLinkState linkState, List<Entry> entries, String result) implements PacketWritable {
    public static final int PAGE_SIZE = 3;
    public static final SequenceWirelessMenuState EMPTY = new SequenceWirelessMenuState(
            0, 1, false, false, null, QuantumLinkState.EMPTY, List.of(), "");
    public record Entry(SequenceWirelessBinding binding, Component name, boolean online) {}

    public SequenceWirelessMenuState { entries = List.copyOf(entries); }

    public SequenceWirelessMenuState(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean() ? readEntry(buffer) : null, buffer.readEnum(QuantumLinkState.class),
                readEntries(buffer), buffer.readUtf(64));
    }

    private static Entry readEntry(RegistryFriendlyByteBuf buffer) {
        var binding = new SequenceWirelessBinding(buffer.readGlobalPos(), buffer.readUUID());
        return new Entry(binding, ComponentSerialization.STREAM_CODEC.decode(buffer), buffer.readBoolean());
    }

    private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > PAGE_SIZE) throw new IllegalArgumentException("Invalid wireless hub page size");
        var entries = new ArrayList<Entry>(count);
        for (int i = 0; i < count; i++) entries.add(readEntry(buffer));
        return entries;
    }

    private static void writeEntry(RegistryFriendlyByteBuf buffer, Entry entry) {
        buffer.writeGlobalPos(entry.binding().target());
        buffer.writeUUID(entry.binding().hubId());
        ComponentSerialization.STREAM_CODEC.encode(buffer, entry.name());
        buffer.writeBoolean(entry.online());
    }

    @Override public void writeToPacket(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(page);
        buffer.writeVarInt(pages);
        buffer.writeBoolean(canConfigure);
        buffer.writeBoolean(bound);
        buffer.writeBoolean(current != null);
        if (current != null) writeEntry(buffer, current);
        buffer.writeEnum(linkState);
        buffer.writeVarInt(entries.size());
        for (var entry : entries) writeEntry(buffer, entry);
        buffer.writeUtf(result, 64);
    }
}
