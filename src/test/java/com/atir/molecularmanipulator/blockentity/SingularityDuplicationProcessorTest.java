package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import com.atir.molecularmanipulator.config.ModConfig;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SingularityDuplicationProcessorTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final AEFluidKey MATTER = AEFluidKey.of(Fluids.WATER);
    private static final IActionSource SOURCE = IActionSource.empty();
    private Field cachedValue;
    private Object previousDuplicationBlacklist;
    private Object previousCollectionBlacklist;

    @BeforeEach void configureBlacklists() throws Exception {
        cachedValue = ModConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");
        cachedValue.setAccessible(true);
        previousDuplicationBlacklist = cachedValue.get(ModConfig.SINGULARITY_DUPLICATION_ITEM_BLACKLIST);
        previousCollectionBlacklist = cachedValue.get(ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST);
        setBlacklist(List.of());
        cachedValue.set(ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST, List.of());
    }

    @AfterEach void restoreBlacklists() throws Exception {
        cachedValue.set(ModConfig.SINGULARITY_DUPLICATION_ITEM_BLACKLIST, previousDuplicationBlacklist);
        cachedValue.set(ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST, previousCollectionBlacklist);
    }

    @Test void emptyBlacklistCopiesOneItemAndPreservesTheOriginalAndComponents() {
        assertEquals(List.of(), ModConfig.SINGULARITY_DUPLICATION_ITEM_BLACKLIST.getDefault());
        var rig = new Rig();
        rig.sample.set(DataComponents.CUSTOM_NAME, Component.literal("Named sample"));
        var original = rig.sample.copy();

        rig.tick();

        assertEquals(1, rig.storage.copiedItems);
        assertEquals(AEItemKey.of(original), rig.storage.output);
        assertEquals(SingularityDuplicationProcessor.MATTER_PER_ITEM, rig.storage.extracted);
        assertTrue(ItemStack.matches(original, rig.sample));
        assertFalse(rig.processor.hasContents());
    }

    @Test void blacklistedIdBlocksPlainAndComponentModifiedSamplesBeforeAnyStorageAccess() throws Exception {
        setBlacklist(List.of(" minecraft:diamond "));
        for (boolean renamed : new boolean[]{false, true}) {
            var rig = new Rig();
            if (renamed) rig.sample.set(DataComponents.CUSTOM_NAME, Component.literal("Still a diamond"));
            var original = rig.sample.copy();

            rig.tick();

            assertEquals(0, rig.storage.calls, "Blocked samples must not simulate output or extract matter");
            assertEquals(0, rig.storage.copiedItems);
            assertEquals(10_000, rig.storage.matter);
            assertTrue(ItemStack.matches(original, rig.sample));
            assertFalse(rig.processor.hasContents());
            assertEquals(0, rig.saves);
        }
    }

    @Test void differentIdsAndTheCollectionBlacklistDoNotBlockAnAllowedSample() throws Exception {
        setBlacklist(List.of("minecraft:diamond_block", "optionalmod:diamond"));
        cachedValue.set(ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST, List.of("minecraft:diamond"));
        var rig = new Rig();

        rig.tick();

        assertEquals(1, rig.storage.copiedItems);
        assertEquals(SingularityDuplicationProcessor.MATTER_PER_ITEM, rig.storage.extracted);
    }

    @Test void configurationChangesStopAnExistingSampleAndRemovingTheEntryResumesCopying() throws Exception {
        var rig = new Rig();
        rig.tick();
        int callsBeforeBlock = rig.storage.calls;
        setBlacklist(List.of("minecraft:diamond"));

        rig.tick();
        rig.tick();

        assertEquals(callsBeforeBlock, rig.storage.calls);
        assertEquals(1, rig.storage.copiedItems);
        assertEquals(SingularityDuplicationProcessor.MATTER_PER_ITEM, rig.storage.extracted);
        setBlacklist(List.of());
        rig.tick();
        assertEquals(2, rig.storage.copiedItems, "No sample reinsertion or machine rebuild is required");
        assertEquals(2L * SingularityDuplicationProcessor.MATTER_PER_ITEM, rig.storage.extracted);
        assertEquals(1, rig.sample.getCount());
    }

    @Test void blacklistingStillReturnsAnAlreadyPaidOutputWithoutStartingAnotherCopy() throws Exception {
        var rig = new Rig();
        rig.storage.acceptOutput = false;
        rig.tick();
        assertTrue(rig.processor.hasContents());
        assertEquals(SingularityDuplicationProcessor.MATTER_PER_ITEM, rig.storage.extracted);
        assertEquals(0, rig.storage.copiedItems);

        setBlacklist(List.of("minecraft:diamond"));
        rig.storage.acceptOutput = true;
        rig.tick();
        rig.tick();

        assertEquals(1, rig.storage.copiedItems, "The completed copy remains owned even after a blacklist change");
        assertEquals(SingularityDuplicationProcessor.MATTER_PER_ITEM, rig.storage.extracted);
        assertFalse(rig.processor.hasContents());
    }

    @Test void blacklistingStillReturnsAPendingPartialExtractionRefund() throws Exception {
        var rig = new Rig();
        rig.storage.extractionLimit = 400;
        rig.storage.acceptRefund = false;
        rig.tick();
        assertEquals(9_600, rig.storage.matter);
        assertTrue(rig.processor.hasContents());

        setBlacklist(List.of("minecraft:diamond"));
        rig.storage.acceptRefund = true;
        rig.tick();
        rig.tick();

        assertEquals(10_000, rig.storage.matter, "Blacklisting cannot strand or discard a fluid refund");
        assertEquals(400, rig.storage.extracted, "No additional extraction occurs for the blocked sample");
        assertEquals(0, rig.storage.copiedItems);
        assertFalse(rig.processor.hasContents());
    }

    private void setBlacklist(List<String> entries) throws Exception {
        cachedValue.set(ModConfig.SINGULARITY_DUPLICATION_ITEM_BLACKLIST, entries);
    }

    private static final class Rig {
        final Stock storage = new Stock();
        final ItemStack sample = new ItemStack(Items.DIAMOND);
        int saves;
        final SingularityDuplicationProcessor processor = new SingularityDuplicationProcessor(() -> saves++);

        void tick() {
            processor.tick(storage, MATTER, SOURCE, sample);
        }
    }

    private static final class Stock implements MEStorage {
        long matter = 10_000;
        long copiedItems;
        long extracted;
        long extractionLimit = Long.MAX_VALUE;
        int calls;
        boolean acceptOutput = true;
        boolean acceptRefund = true;
        AEItemKey output;

        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            calls++;
            if (key.equals(MATTER)) {
                if (!acceptRefund) return 0;
                if (mode == Actionable.MODULATE) matter += amount;
            } else {
                assertInstanceOf(AEItemKey.class, key);
                // Simulate space, then optionally reject the completed copy to exercise recovery.
                if (mode == Actionable.MODULATE) {
                    if (!acceptOutput) return 0;
                    copiedItems += amount;
                    output = (AEItemKey) key;
                }
            }
            return amount;
        }

        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            calls++;
            assertEquals(MATTER, key);
            long taken = Math.min(matter, amount);
            if (mode == Actionable.MODULATE) {
                taken = Math.min(taken, extractionLimit);
                matter -= taken;
                extracted += taken;
            }
            return taken;
        }

        @Override public void getAvailableStacks(KeyCounter counter) {
            fail("Sample copying does not enumerate network contents");
        }

        @Override public Component getDescription() {
            return Component.literal("Singularity duplication test storage");
        }
    }
}
