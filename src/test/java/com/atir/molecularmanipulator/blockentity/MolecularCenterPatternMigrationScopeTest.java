package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.parts.crafting.PatternProviderPart;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.ForgeTestBootstrap;
import com.atir.molecularmanipulator.integration.ae2.SegmentedPatternContainers;
import com.glodblock.github.extendedae.common.parts.PartExPatternProvider;
import com.glodblock.github.extendedae.common.tileentities.TileExDrive;
import com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MolecularCenterPatternMigrationScopeTest {
    static {
        try { ForgeTestBootstrap.bootstrapMinecraft(); }
        catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }

    @Test void everyPublicPatternContainerIsEligibleWithoutMachineAdapters() {
        for (var type : List.of(PatternProviderBlockEntity.class, PatternProviderPart.class,
                TileExPatternProvider.class, PartExPatternProvider.class, MolecularCenterBlockEntity.class,
                MolecularManipulatorBlockEntity.class, MolecularAutoCrafterBlockEntity.class,
                TileAssemblerMatrixPattern.class, MatterFabricationPatternAssemblyBlockEntity.class, Container.class)) {
            assertTrue(MolecularCenterPatternMigration.isPatternContainerType(type), type.getName());
        }
        for (var type : List.of(MolecularAssemblerBlockEntity.class, AppEngInternalInventory.class, Object.class)) {
            assertFalse(MolecularCenterPatternMigration.isPatternContainerType(type), type.getName());
        }
        // Eligibility concerns the interface. Private crafter slots are never inspected.
    }

    @Test void everyCellHostIsEligibleButArbitraryInventoriesAndStorageBusesAreNot() {
        for (var type : List.of(DriveBlockEntity.class, ChestBlockEntity.class, TileExDrive.class,
                AddonDrive.class, IChestOrDrive.class)) {
            assertTrue(MolecularCenterPatternMigration.isCellHost(type), type.getName());
        }
        for (var type : List.of(MolecularAutoCrafterBlockEntity.class, TileAssemblerMatrixPattern.class,
                appeng.parts.storagebus.StorageBusPart.class, AppEngInternalInventory.class, Object.class)) {
            assertFalse(MolecularCenterPatternMigration.isCellHost(type), type.getName());
        }
    }

    @Test void allSegmentsAndRepeatedGridViewsMapToOnePhysicalHostWithoutReadingSlots() {
        var host = new Container();
        var segments = new SegmentedPatternContainers(host).getContainers();
        assertEquals(3, segments.size());
        var candidates = new ArrayList<Object>(segments);
        candidates.addAll(segments);
        candidates.add(host);
        candidates.add(new Object());
        var physical = MolecularCenterPatternMigration.physicalContainers(candidates);
        assertEquals(1, physical.size());
        assertSame(host, physical.get(0));
        assertTrue(MolecularCenterPatternMigration.isPatternContainerType(physical.get(0).getClass()));
    }

    @Test void identityDeduplicationDoesNotCollapseEqualButDistinctHosts() {
        var first = new Container();
        var second = new Container();
        var physical = MolecularCenterPatternMigration.physicalContainers(List.of(first, second, first));
        assertEquals(2, physical.size());
        assertSame(first, physical.get(0));
        assertSame(second, physical.get(1));
    }

    @Test void resultCountsPhysicalItemsAndReportsPartialProgress() {
        var result = new MolecularCenterPatternMigration.Result(3, 2, true, false);
        assertEquals(5, result.moved());
        assertTrue(result.destinationFull());
        assertFalse(result.interrupted());
        assertEquals(0, MolecularCenterPatternMigration.migrate(null, null).moved());
        assertTrue(MolecularCenterPatternMigration.migrate(null, null).interrupted());
    }

    private abstract static class AddonDrive implements IChestOrDrive {}

    private static final class Container implements PatternContainer {
        private final InternalInventory inventory = new AppEngInternalInventory(260) {
            @Override public net.minecraft.world.item.ItemStack getStackInSlot(int slot) {
                throw new AssertionError("Normalization/eligibility must not inspect slots");
            }
        };
        @Override public IGrid getGrid() { return null; }
        @Override public InternalInventory getTerminalPatternInventory() { return inventory; }
        @Override public PatternContainerGroup getTerminalGroup() { throw new AssertionError("Not a source inventory"); }
        @Override public boolean equals(Object other) { return other instanceof Container; }
        @Override public int hashCode() { return 1; }
    }
}
