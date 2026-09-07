package com.atir.molecularmanipulator.research;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/** Decimal strings preserve 64-bit amounts when definitions are authored in JavaScript. */
public final class ResearchCodecs {
    /** Zero is the Java representation of the data-pack string "max". */
    public static final Codec<Integer> PREREQUISITE_LEVEL = Codec.either(Codec.intRange(1, Integer.MAX_VALUE),
            Codec.STRING.validate(value -> value.equals("max") ? DataResult.success(value)
                    : DataResult.error(() -> "Prerequisite level must be a positive integer or max")))
            .xmap(value -> value.map(level -> level, maximum -> 0),
                    level -> level == 0 ? Either.right("max") : Either.left(level));

    public static final Codec<Long> POSITIVE_LONG = Codec.either(Codec.STRING, Codec.LONG).comapFlatMap(value -> {
        try {
            long parsed = value.map(Long::parseLong, number -> number);
            return parsed > 0 ? DataResult.success(parsed) : DataResult.error(() -> "Value must be a positive 64-bit integer");
        } catch (NumberFormatException error) { return DataResult.error(() -> "Invalid 64-bit integer"); }
    }, value -> Either.left(Long.toString(value)));
    private ResearchCodecs() {}
}
