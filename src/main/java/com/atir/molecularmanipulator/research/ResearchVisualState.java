package com.atir.molecularmanipulator.research;

import io.netty.handler.codec.DecoderException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** Small world-render snapshot; never includes research costs, recipes or inventory. */
public record ResearchVisualState(List<Task> tasks, long completionSerial, int completionStyle) {
    public static final int MAX_VISIBLE_TASKS = 4;
    public static final int STYLE_COUNT = 4;
    public static final ResearchVisualState EMPTY = new ResearchVisualState(List.of(), 0, 0);

    public ResearchVisualState {
        tasks = List.copyOf(tasks.subList(0, Math.min(tasks.size(), MAX_VISIBLE_TASKS)));
        completionStyle = Math.floorMod(completionStyle, STYLE_COUNT);
    }

    public record Task(long identity, int style, int round, int progress, int duration, boolean running) {
        public Task {
            style = Math.floorMod(style, STYLE_COUNT);
            round = Math.max(1, round);
            duration = Math.max(1, duration);
            progress = Math.clamp(progress, 0, duration);
        }

        public float fraction(double elapsed) {
            return (float) Math.clamp((progress + (running ? Math.clamp(elapsed, 0, 5) : 0)) / duration, 0, 1);
        }
    }

    /** Stable across clients, pause/resume and reload; custom stages are rerolled for each depth. */
    public static long identity(ResourceLocation id, int round, long machineSalt) {
        long value = 0xcbf29ce484222325L ^ machineSalt;
        for (char c : id.toString().toCharArray()) value = (value ^ c) * 0x100000001b3L;
        value ^= (long) round * 0x9e3779b97f4a7c15L;
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    public static int style(ResourceLocation id, long identity) {
        if (id.getNamespace().equals("molecularmanipulator")) {
            switch (id.getPath()) {
                case "research/ae_foundation": return 0;
                case "research/sequence_array": return 1;
                case "research/omni_computation": return 2;
            }
        }
        return Math.floorMod(identity, STYLE_COUNT);
    }

    public void write(FriendlyByteBuf data) {
        data.writeVarInt(tasks.size());
        for (var task : tasks) {
            data.writeLong(task.identity());
            data.writeByte(task.style());
            data.writeVarInt(task.round());
            data.writeVarInt(task.progress());
            data.writeVarInt(task.duration());
            data.writeBoolean(task.running());
        }
        data.writeLong(completionSerial);
        data.writeByte(completionStyle);
    }

    public static ResearchVisualState read(FriendlyByteBuf data) {
        int count = data.readVarInt();
        if (count < 0 || count > MAX_VISIBLE_TASKS) throw new DecoderException("Invalid research visual count: " + count);
        var tasks = new ArrayList<Task>(count);
        for (int i = 0; i < count; i++) tasks.add(new Task(data.readLong(), data.readUnsignedByte(),
                data.readVarInt(), data.readVarInt(), data.readVarInt(), data.readBoolean()));
        return new ResearchVisualState(tasks, data.readLong(), data.readUnsignedByte());
    }
}
