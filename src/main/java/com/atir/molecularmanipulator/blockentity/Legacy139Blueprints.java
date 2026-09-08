package com.atir.molecularmanipulator.blockentity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Exact active blueprints from the official 1.3.9 release, commit 25dd485. */
final class Legacy139Blueprints {
    private Legacy139Blueprints() {}

    interface Factory<T> { T create(int x, int y, int z, String type); }

    static <T> List<T> load(String name, Factory<T> factory) {
        String path = "/data/molecularmanipulator/blueprints/legacy_1_3_9/" + name + ".nbt";
        try (var stream = Legacy139Blueprints.class.getResourceAsStream(path)) {
            if (stream == null) throw new IOException("Missing legacy blueprint " + path);
            var tag = NbtIo.read(new java.io.DataInputStream(new java.util.zip.GZIPInputStream(stream)),
                    new NbtAccounter(8L * 1024 * 1024));
            if (!tag.getString("version").equals("1.3.9")) throw new IOException("Invalid legacy blueprint version");
            var result = new ArrayList<T>();
            for (var entry : tag.getList("parts", Tag.TAG_STRING)) {
                var part = entry.getAsString().split(",", 4);
                result.add(factory.create(Integer.parseInt(part[0]), Integer.parseInt(part[1]), Integer.parseInt(part[2]), part[3]));
            }
            return List.copyOf(result);
        } catch (IOException error) { throw new UncheckedIOException(error); }
    }
}
