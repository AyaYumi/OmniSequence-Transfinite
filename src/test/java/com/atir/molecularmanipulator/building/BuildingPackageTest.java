package com.atir.molecularmanipulator.building;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.registry.ModContent;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BuildingPackageTest {
    private static Field holder;
    private static Object previousHolder;

    @BeforeAll static void bindPackageForStandaloneNativeSerialization() throws Exception {
        if (net.neoforged.fml.loading.LoadingModList.get() == null)
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        // JUnit does not run deferred mod registration. Substitute only the item identity;
        // production package metadata and vanilla component/inventory codecs are unchanged.
        holder = DeferredHolder.class.getDeclaredField("holder");
        holder.setAccessible(true);
        previousHolder = holder.get(ModContent.FOLDED_BUILDING_PACKAGE);
        holder.set(ModContent.FOLDED_BUILDING_PACKAGE, Holder.direct(Items.BUNDLE));
    }

    @AfterAll static void restorePackageIdentity() throws Exception {
        if (holder != null) holder.set(ModContent.FOLDED_BUILDING_PACKAGE, previousHolder);
    }

    @Test void sealedPackageContainsOnlyIdentityVersionAndSummaryEvenForHugeBuildings() throws Exception {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for (var type : BuildingType.values()) {
            var id = UUID.randomUUID();
            var stack = BuildingPackage.sealed(type, id, Integer.MAX_VALUE);
            assertEquals(type, BuildingPackage.type(stack));
            assertEquals(id, BuildingPackage.archive(stack));
            assertNull(BuildingPackage.reservation(stack));
            assertEquals(Set.of("type", "template", "archive", "blocks"), BuildingPackage.data(stack).getAllKeys());
            assertEquals(Set.of("folded_building"), stack.get(DataComponents.CUSTOM_DATA).copyTag().getAllKeys());
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) { NbtIo.write((net.minecraft.nbt.CompoundTag) stack.save(registries), output); }
            assertTrue(bytes.size() < 512, "Package size must not depend on archived inventories or building size");
            var restored = ItemStack.parse(registries, stack.save(registries)).orElseThrow();
            assertEquals(id, BuildingPackage.archive(restored));
            assertEquals(Integer.MAX_VALUE, BuildingPackage.data(restored).getInt("blocks"));
        }
    }

    @Test void reservationAddsOnlyAReceiptAndClearingItPreservesArchiveIdentity() {
        var id = UUID.randomUUID();
        var stack = BuildingPackage.sealed(BuildingType.SEQUENCE_ARRAY, id, 42);
        var summary = BuildingPackage.data(stack);
        BuildingPackage.reserve(stack, id);
        assertEquals(id, BuildingPackage.reservation(stack));
        assertEquals(id, BuildingPackage.archive(stack));
        assertEquals(5, BuildingPackage.data(stack).size());
        BuildingPackage.clearReservation(stack);
        assertNull(BuildingPackage.reservation(stack));
        assertEquals(summary, BuildingPackage.data(stack));
    }

    @Test void orderablePackagesReplaceTheLegacyCubeWithoutChangingItsArchiveIdentity() {
        assertEquals(List.of(BuildingType.FABRICATION_WELL, BuildingType.SEQUENCE_ARRAY,
                BuildingType.OMNI_COMPUTATION, BuildingType.EVENT_HORIZON_SINGULARITY, BuildingType.STAR_ORRERY_L1,
                BuildingType.STAR_ORRERY_L2, BuildingType.STAR_ORRERY_L3), BuildingType.orderable());
        assertSame(BuildingType.STAR_COMPUTATION_ENGINE, BuildingType.byId("star_computation_engine"));
        assertThrows(IllegalStateException.class, BuildingType.STAR_COMPUTATION_ENGINE::template);
        for (var type : BuildingType.orderable()) {
            var stack = BuildingPackage.fresh(type);
            assertSame(type, BuildingPackage.type(stack));
            assertSame(type, BuildingType.byId(BuildingPackage.data(stack).getString("type")));
            assertNull(BuildingPackage.archive(stack));
            assertNull(BuildingPackage.reservation(stack));
        }
    }

    @Test void eachActualOrreryGradeHasAStableArchiveIdentityWithoutAddingOrders() {
        for (int level = 0; level <= 9; level++) {
            var type = BuildingType.starOrrery(level);
            assertEquals("star_orrery_l" + level, type.id);
            assertSame(type, BuildingType.byId(type.id));
            assertTrue(type.isStarOrrery());
            assertEquals(level, type.orreryLevel());
            assertEquals(level >= 1 && level <= 3, BuildingType.orderable().contains(type));
            var stack = BuildingPackage.sealed(type, UUID.randomUUID(), 955);
            assertSame(type, BuildingPackage.type(stack));
            if (level == 0 || level > 3) assertThrows(IllegalStateException.class, type::template);
        }
        assertThrows(IllegalArgumentException.class, () -> BuildingType.starOrrery(-1));
        assertThrows(IllegalArgumentException.class, () -> BuildingType.starOrrery(10));
    }

    @Test void freshTemplatesHaveNoArchiveUntilTheirOwnDeploymentReservation() {
        var stack = BuildingPackage.fresh(BuildingType.FABRICATION_WELL);
        assertNull(BuildingPackage.archive(stack));
        assertEquals(Set.of("type", "template"), BuildingPackage.data(stack).getAllKeys());
        var id = UUID.randomUUID();
        BuildingPackage.reserve(stack, id);
        assertEquals(id, BuildingPackage.reservation(stack));
        assertNull(BuildingPackage.archive(stack), "A fresh template is not a handle to a sealed machine");
    }
}
