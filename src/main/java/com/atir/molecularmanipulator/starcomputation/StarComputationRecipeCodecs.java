package com.atir.molecularmanipulator.starcomputation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** DFU 1.20 optional fields must reject malformed requirements instead of silently using a default. */
public final class StarComputationRecipeCodecs {
    private StarComputationRecipeCodecs() {}

    public static <A> MapCodec<Optional<A>> optionalField(Codec<A> codec, String name) {
        return optionalField(codec.xmap(Optional::of, Optional::orElseThrow), name, Optional.empty());
    }

    public static <A> MapCodec<A> optionalField(Codec<A> codec, String name, A fallback) {
        return new MapCodec<>() {
            @Override public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }

            @Override public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
                T value = input.get(name);
                return value == null ? DataResult.success(fallback) : codec.parse(ops, value);
            }

            @Override public <T> RecordBuilder<T> encode(A value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                if (!Objects.equals(value, fallback)) prefix.add(name, codec.encodeStart(ops, value));
                return prefix;
            }
        };
    }
}
