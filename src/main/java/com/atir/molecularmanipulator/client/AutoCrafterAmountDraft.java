package com.atir.molecularmanipulator.client;

import java.util.OptionalLong;

/** Retains unsubmitted edits across menu syncs, focus changes and ingredient pages. */
final class AutoCrafterAmountDraft {
    private String text = "0";
    private long serverValue;
    private boolean initialized;

    String text() {
        return text;
    }

    void edit(String text) {
        this.text = text;
    }

    void sync(long value) {
        if (!initialized || !isDirty()) {
            text = Long.toString(value);
        }
        serverValue = value;
        initialized = true;
    }

    boolean isDirty() {
        var value = value();
        return value.isEmpty() || value.getAsLong() != serverValue;
    }

    OptionalLong value() {
        if (text.isEmpty() || !accepts(text)) return OptionalLong.empty();
        try {
            return OptionalLong.of(Long.parseLong(text));
        } catch (NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }

    static boolean accepts(String text) {
        return text.chars().allMatch(character -> character >= '0' && character <= '9');
    }
}
