package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.parts.crafting.PatternProviderPart;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.integration.ae2.SegmentedPatternContainers;
import com.glodblock.github.extendedae.common.parts.PartExPatternProvider;
import com.glodblock.github.extendedae.common.tileentities.TileExDrive;
import com.glodblock.github.extendedae.common.tileentities.TileExPatternProvider;
import com.glodblock.github.extendedae.common.tileentities.matrix.TileAssemblerMatrixPattern;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MolecularCenterPatternMigrationScopeTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
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
        // MolecularAutoCrafter exposes zero terminal slots, not its private nine-slot inventory.
        // Eligibility concerns the interface; extraction is always through its public inventory.
    }

    @Test void everyCellHostImplementationIsEligibleButArbitraryInventoriesAreNot() {
        for (var type : List.of(DriveBlockEntity.class, MEChestBlockEntity.class, TileExDrive.class,
                AddonDrive.class, appeng.api.implementations.blockentities.IChestOrDrive.class)) {
            assertTrue(MolecularCenterPatternMigration.isCellHost(type));
        }
        for (var type : List.of(MolecularAutoCrafterBlockEntity.class, TileAssemblerMatrixPattern.class,
                AppEngInternalInventory.class, Object.class)) {
            assertFalse(MolecularCenterPatternMigration.isCellHost(type));
        }
    }

    @Test void everySegmentAndRepeatedGridViewMapsToOnePhysicalHostWithoutReadingItsSlots() {
        var host = new Container();
        var segments = new SegmentedPatternContainers(host).getContainers();
        assertEquals(3, segments.size());
        var candidates = new ArrayList<Object>(segments);
        candidates.addAll(segments); candidates.add(host); candidates.add(new Object());
        var physical = MolecularCenterPatternMigration.physicalContainers(candidates);
        assertEquals(1, physical.size());
        assertSame(host, physical.getFirst());
        assertTrue(MolecularCenterPatternMigration.isPatternContainerType(physical.getFirst().getClass()));
    }

    @Test void identityDeduplicationDoesNotCollapseTwoEqualButDistinctHosts() {
        var first = new Container();
        var second = new Container();
        var physical = MolecularCenterPatternMigration.physicalContainers(List.of(first, second, first));
        assertEquals(2, physical.size());
        assertSame(first, physical.get(0)); assertSame(second, physical.get(1));
    }

    @Test void resultCountsArePhysicalItemCountsAndPartialProgressRemainsReportable() {
        var result = new MolecularCenterPatternMigration.Result(3, 2, true, false);
        assertEquals(5, result.moved());
        assertTrue(result.destinationFull());
        assertFalse(result.interrupted());
        assertEquals(0, MolecularCenterPatternMigration.migrate(null, null).moved());
    }

    private static abstract class AddonDrive extends DriveBlockEntity {
        AddonDrive(net.minecraft.world.level.block.entity.BlockEntityType<?> type, net.minecraft.core.BlockPos pos,
                net.minecraft.world.level.block.state.BlockState state) { super(type, pos, state); }
    }

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
