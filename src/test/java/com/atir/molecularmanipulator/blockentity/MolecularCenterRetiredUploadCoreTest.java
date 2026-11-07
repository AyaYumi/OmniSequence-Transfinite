package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.util.inv.AppEngInternalInventory;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;

/** A retired functional slot must neither swallow unknown addon NBT nor replay a refunded item. */
class MolecularCenterRetiredUploadCoreTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static final HolderLookup.Provider REGISTRIES = HolderLookup.Provider.create(
            BuiltInRegistries.REGISTRY.stream().map(registry -> registry.asLookup()));

    @Test void knownCoreReturnsItsExactComponentsAndMergesWithExistingEscrow() {
        var stack = core();
        var key = AEItemKey.of(stack);
        var saved = legacy(stack);
        var original = saved.copy();
        var outputs = new Object2LongOpenHashMap<AEKey>();
        outputs.put(key, 2);
        assertNull(MolecularCenterBlockEntity.migrateRetiredUploadCore(saved, REGISTRIES, outputs));
        assertEquals(3, outputs.getLong(key));
        assertEquals(original, saved, "The caller-owned legacy inventory must remain immutable");
        assertTrue(ItemStack.matches(stack, ((AEItemKey) outputs.keySet().iterator().next()).toStack(1)));
        assertNull(MolecularCenterBlockEntity.migrateRetiredUploadCore(null, REGISTRIES, outputs));
        assertEquals(3, outputs.getLong(key), "A post-migration save has no replayable retired slot");
    }

    @Test void absentAddonRegistrationKeepsRawItemAndEveryComponentForLaterRecovery() {
        var unknown = new CompoundTag();
        unknown.putString("id", "extendedae_plus:temporarily_unregistered_upload_core");
        unknown.putInt("count", 1);
        unknown.putInt("Slot", 0);
        var components = new CompoundTag();
        var payload = new CompoundTag();
        payload.putString("legacy_owner", "Do not erase me");
        components.put("minecraft:custom_data", payload);
        unknown.put("components", components);
        var saved = new ListTag();
        saved.add(unknown);
        var outputs = new Object2LongOpenHashMap<AEKey>();
        var retained = MolecularCenterBlockEntity.migrateRetiredUploadCore(saved, REGISTRIES, outputs);
        assertEquals(saved, retained);
        assertNotSame(saved, retained);
        assertNotSame(saved.get(0), ((ListTag) retained).get(0));
        assertTrue(outputs.isEmpty());
        assertEquals(retained, MolecularCenterBlockEntity.migrateRetiredUploadCore(retained, REGISTRIES, outputs));
        assertTrue(outputs.isEmpty(), "Repeated saves without the addon cannot synthesize a replacement item");
    }

    @Test void overflowingEscrowRetainsTheOriginalCoreInsteadOfWrappingOrSaturatingItAway() {
        var stack = core();
        var key = AEItemKey.of(stack);
        var saved = legacy(stack);
        var outputs = new Object2LongOpenHashMap<AEKey>();
        outputs.put(key, Long.MAX_VALUE);
        var retained = MolecularCenterBlockEntity.migrateRetiredUploadCore(saved, REGISTRIES, outputs);
        assertEquals(saved, retained);
        assertEquals(Long.MAX_VALUE, outputs.getLong(key));
        outputs.put(key, Long.MAX_VALUE - 1);
        assertNull(MolecularCenterBlockEntity.migrateRetiredUploadCore(retained, REGISTRIES, outputs));
        assertEquals(Long.MAX_VALUE, outputs.getLong(key), "The preserved item becomes claimable once capacity returns");
    }

    @Test void malformedPayloadsAndUnexpectedSlotsArePreservedButEmptyLegacySlotsAreRetired() {
        var outputs = new Object2LongOpenHashMap<AEKey>();
        var malformed = StringTag.valueOf("legacy data of an unknown format");
        assertEquals(malformed, MolecularCenterBlockEntity.migrateRetiredUploadCore(malformed, REGISTRIES, outputs));
        var unexpected = legacy(core());
        unexpected.getCompound(0).putInt("Slot", 1);
        assertEquals(unexpected, MolecularCenterBlockEntity.migrateRetiredUploadCore(unexpected, REGISTRIES, outputs));
        assertNull(MolecularCenterBlockEntity.migrateRetiredUploadCore(new ListTag(), REGISTRIES, outputs));
        assertTrue(outputs.isEmpty());
    }

    private static ItemStack core() {
        var stack = new ItemStack(Items.DIAMOND);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Retired physical upload core"));
        var custom = new CompoundTag();
        custom.putLong("legacy_payload", 4_294_967_297L);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        return stack;
    }

    private static ListTag legacy(ItemStack core) {
        var inventory = new AppEngInternalInventory(1);
        inventory.setItemDirect(0, core);
        var tag = new CompoundTag();
        inventory.writeToNBT(tag, "matrix_upload_core_inventory", REGISTRIES);
        return tag.getList("matrix_upload_core_inventory", net.minecraft.nbt.Tag.TAG_COMPOUND);
    }
}
