package com.atir.molecularmanipulator.integration.ae2;

/**
 * Runtime bridge implemented on AE2's client-side number entry widget.
 *
 * <p>The transformed AE2 screen must only reference a normal application
 * interface. Mixin implementation classes are intentionally not loadable as
 * ordinary game classes in every production launcher.</p>
 */
public interface LongNumberEntryWidgetBridge {
    void molecularmanipulator$setInputMaxLength(int maxLength);
}
