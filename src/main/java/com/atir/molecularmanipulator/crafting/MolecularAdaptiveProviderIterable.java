package com.atir.molecularmanipulator.crafting;

import appeng.api.networking.crafting.ICraftingProvider;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Repeats the currently selected provider whenever the dispatch state advances.
 *
 * <p>This helper deliberately lives outside the mixin package. Mixin-owned
 * inner classes cannot be loaded directly after their outer mixin has been
 * applied to a target class.</p>
 */
public final class MolecularAdaptiveProviderIterable
        implements Iterable<ICraftingProvider> {
    private final BooleanSupplier activeSupplier;
    private final Supplier<ICraftingProvider> providerSupplier;
    private final LongSupplier serialSupplier;

    public MolecularAdaptiveProviderIterable(
            BooleanSupplier activeSupplier,
            Supplier<ICraftingProvider> providerSupplier,
            LongSupplier serialSupplier) {
        this.activeSupplier = Objects.requireNonNull(activeSupplier);
        this.providerSupplier = Objects.requireNonNull(providerSupplier);
        this.serialSupplier = Objects.requireNonNull(serialSupplier);
    }

    @Override
    public Iterator<ICraftingProvider> iterator() {
        return new ProviderIterator(
                activeSupplier, providerSupplier, serialSupplier);
    }

    private static final class ProviderIterator
            implements Iterator<ICraftingProvider> {
        private final BooleanSupplier activeSupplier;
        private final Supplier<ICraftingProvider> providerSupplier;
        private final LongSupplier serialSupplier;
        private long deliveredSerial = Long.MIN_VALUE;

        private ProviderIterator(
                BooleanSupplier activeSupplier,
                Supplier<ICraftingProvider> providerSupplier,
                LongSupplier serialSupplier) {
            this.activeSupplier = activeSupplier;
            this.providerSupplier = providerSupplier;
            this.serialSupplier = serialSupplier;
        }

        @Override
        public boolean hasNext() {
            var provider = providerSupplier.get();
            return activeSupplier.getAsBoolean()
                    && provider != null
                    && !provider.isBusy()
                    && deliveredSerial != serialSupplier.getAsLong();
        }

        @Override
        public ICraftingProvider next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            deliveredSerial = serialSupplier.getAsLong();
            return providerSupplier.get();
        }
    }
}
