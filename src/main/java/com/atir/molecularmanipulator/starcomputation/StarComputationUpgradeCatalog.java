package com.atir.molecularmanipulator.starcomputation;

import appeng.menu.guisync.PacketWritable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** Nine server-evaluated levels with resolved names and explanations, synchronized through AE2's custom GuiSync field. */
public record StarComputationUpgradeCatalog(List<Entry> entries) implements PacketWritable {
    public static final int LEVEL_COUNT = 9;
    public static final int MAX_ROWS = StarComputationUpgradeRecipe.MAX_ELEMENTS * 2;
    public static final StarComputationUpgradeCatalog EMPTY = StarComputationUpgrades.catalog(null,
            ignored -> { throw new IllegalStateException("An empty catalog cannot have configured rules"); });

    public record Row(int depth, boolean met, Component text, List<Component> details) {
        public Row(int depth, boolean met, Component text) { this(depth, met, text, List.of()); }

        public Row {
            Objects.requireNonNull(text);
            details = List.copyOf(details);
            if (depth < 0 || depth > StarComputationUpgradeRecipe.MAX_DEPTH + 1) {
                throw new IllegalArgumentException("Invalid upgrade row depth: " + depth);
            }
        }
    }

    public record Entry(int level, boolean configured, double energy, double aePerTick,
                        boolean conditionsMet, List<Row> rows, boolean autoBuild) {
        public Entry(int level, boolean configured, double energy, double aePerTick,
                     boolean conditionsMet, List<Row> rows) {
            this(level, configured, energy, aePerTick, conditionsMet, rows, false);
        }

        public Entry {
            rows = List.copyOf(rows);
            if (level < 1 || level > LEVEL_COUNT || !Double.isFinite(energy) || energy < 0
                    || !Double.isFinite(aePerTick) || aePerTick < 0 || configured && aePerTick == 0
                    || !configured && conditionsMet || rows.size() > MAX_ROWS) {
                throw new IllegalArgumentException("Invalid upgrade catalog entry: " + level);
            }
        }
    }

    public StarComputationUpgradeCatalog {
        entries = List.copyOf(entries);
        if (entries.size() != LEVEL_COUNT) throw new IllegalArgumentException("Expected nine upgrade levels");
        for (int index = 0; index < LEVEL_COUNT; index++) {
            var entry = entries.get(index);
            if (entry.level() != index + 1) throw new IllegalArgumentException("Upgrade levels must be ordered 1..9");
            if (entry.level() <= 3 && (!entry.configured() || entry.energy() != 0 || !entry.conditionsMet() && !entry.autoBuild())) {
                throw new IllegalArgumentException("Levels 1..3 are automatic");
            }
        }
    }

    /** AE2 CustomField uses reflection and requires this exact public buffer constructor. */
    public StarComputationUpgradeCatalog(FriendlyByteBuf buffer) {
        this(readEntries(buffer));
    }

    public Entry entry(int level) {
        if (level < 1 || level > LEVEL_COUNT) throw new IllegalArgumentException("Invalid upgrade level: " + level);
        return entries.get(level - 1);
    }

    @Override public void writeToPacket(FriendlyByteBuf buffer) {
        buffer.writeVarInt(LEVEL_COUNT);
        for (var entry : entries) {
            buffer.writeVarInt(entry.level());
            buffer.writeBoolean(entry.configured());
            buffer.writeDouble(entry.energy());
            buffer.writeDouble(entry.aePerTick());
            buffer.writeBoolean(entry.conditionsMet());
            buffer.writeBoolean(entry.autoBuild());
            buffer.writeVarInt(entry.rows().size());
            for (var row : entry.rows()) {
                buffer.writeVarInt(row.depth());
                buffer.writeBoolean(row.met());
                buffer.writeComponent(row.text());
                buffer.writeVarInt(row.details().size());
                for (var detail : row.details()) buffer.writeComponent(detail);
            }
        }
    }

    private static List<Entry> readEntries(FriendlyByteBuf buffer) {
        if (buffer.readVarInt() != LEVEL_COUNT) throw new IllegalArgumentException("Expected nine upgrade levels");
        var entries = new ArrayList<Entry>(LEVEL_COUNT);
        for (int index = 0; index < LEVEL_COUNT; index++) {
            int level = buffer.readVarInt();
            if (level != index + 1) throw new IllegalArgumentException("Upgrade levels must be ordered 1..9");
            boolean configured = buffer.readBoolean();
            double energy = buffer.readDouble();
            double rate = buffer.readDouble();
            boolean met = buffer.readBoolean();
            boolean autoBuild = buffer.readBoolean();
            int size = buffer.readVarInt();
            if (size < 0 || size > MAX_ROWS) throw new IllegalArgumentException("Invalid upgrade row count: " + size);
            var rows = new ArrayList<Row>(size);
            for (int row = 0; row < size; row++) {
                int depth = buffer.readVarInt();
                if (depth < 0 || depth > StarComputationUpgradeRecipe.MAX_DEPTH + 1) {
                    throw new IllegalArgumentException("Invalid upgrade row depth: " + depth);
                }
                boolean rowMet = buffer.readBoolean();
                Component title = buffer.readComponent();
                int detailCount = buffer.readVarInt();
                if (detailCount < 0 || detailCount > MAX_ROWS) throw new IllegalArgumentException("Invalid upgrade detail count: " + detailCount);
                var details = new ArrayList<Component>(detailCount);
                for (int detail = 0; detail < detailCount; detail++) details.add(buffer.readComponent());
                rows.add(new Row(depth, rowMet, title, details));
            }
            entries.add(new Entry(level, configured, energy, rate, met, rows, autoBuild));
        }
        return entries;
    }
}
