package com.atir.molecularmanipulator.blockentity;

import static com.atir.molecularmanipulator.blockentity.MolecularCenterPatternMigrationGameTests.connect;
import static com.atir.molecularmanipulator.blockentity.MolecularCenterPatternMigrationGameTests.fixture;
import static com.atir.molecularmanipulator.blockentity.MolecularCenterPatternMigrationGameTests.nodesReady;
import static com.atir.molecularmanipulator.blockentity.MolecularCenterPatternMigrationGameTests.online;
import static com.atir.molecularmanipulator.blockentity.MolecularCenterPatternMigrationGameTests.patterns;
import static com.atir.molecularmanipulator.blockentity.MolecularCenterPatternMigrationGameTests.place;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.me.cells.BasicCellInventory;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import com.atir.molecularmanipulator.integration.ae2.SegmentedPatternContainers;
import com.atir.molecularmanipulator.registry.ModContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real formed crafting cluster and public bus inventories; no forced formation or source refreshes. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MolecularCenterClusterMigrationGameTests {
    private static final List<Integer> MANIPULATOR_SLOTS = List.of(0, 128, 359);
    private static final AEItemKey RETAINED_ITEM = AEItemKey.of(Items.DIAMOND);

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 1200)
    public static void formedBusSegmentedManipulatorAndDiskMigratePhysicalPatterns(GameTestHelper helper) {
        var supported = patterns(helper);
        var target = fixture(helper, false, 40);
        var origin = target.center().getBlockPos();
        var manipulator = place(helper, origin.east(88), ModContent.MOLECULAR_MANIPULATOR.get().defaultBlockState(),
                MolecularManipulatorBlockEntity.class);
        var drive = place(helper, origin.east(84), AEBlocks.DRIVE.block().defaultBlockState(), DriveBlockEntity.class);
        var clusterSource = buildCluster(helper, origin.east(128));
        var network = new NetworkFixture(target, manipulator, drive, clusterSource);
        target.nodes().add(manipulator.getMainNode());
        target.nodes().add(drive.getMainNode());
        target.nodes().add(clusterSource.controller().getMainNode());
        target.cellHosts().add(drive);

        var disk = AEItems.ITEM_CELL_1K.stack();
        var cell = BasicCellInventory.createInventory(disk, null);
        helper.assertTrue(cell != null, "Seed a real finite item storage cell");
        for (var stack : supported) {
            helper.assertTrue(cell.insert(AEItemKey.of(stack), 1, Actionable.MODULATE, IActionSource.empty()) == 1,
                    "The disk must own one real encoded pattern of each supported category");
        }
        helper.assertTrue(cell.insert(RETAINED_ITEM, 7, Actionable.MODULATE, IActionSource.empty()) == 7,
                "Keep unrelated disk contents as a persistence/conservation sentinel");
        drive.getInternalInventory().setItemDirect(0, disk);
        var publishedBeforeMigration = new ArrayList<IPatternDetails>();

        helper.startSequence().thenWaitUntil(() -> {
                    nodesReady(helper, target);
                    for (var member : clusterSource.members()) {
                        helper.assertTrue(member.getMainNode().getNode() != null, "Every planned cluster member has a real grid node");
                    }
                }).thenExecute(() -> clusterSource.controller().rebuildMultiblock())
                .thenWaitUntil(() -> formed(helper, clusterSource))
                .thenExecute(() -> connect(target))
                .thenWaitUntil(() -> networkOnline(helper, network))
                .thenExecute(() -> {
                    var center = target.center();
                    helper.assertTrue(center.getLogic().getFullPatternInventory().isEmpty(), "Target starts empty");
                    helper.assertTrue(center.getTerminalPatternInventory().size() >= 9, "Target has room for nine physical items");
                    for (var stack : supported) {
                        helper.assertTrue(PatternDetailsHelper.decodePattern(stack, helper.getLevel())
                                        instanceof IMolecularAssemblerSupportedPattern && center.isSupportedExternalPattern(stack),
                                "Use genuinely decodable crafting, stonecutting and smithing patterns");
                    }
                    var bus = clusterSource.buses().getFirst();
                    int physicalSlots = bus.getPatternSlotCount();
                    var inventory = bus.getTerminalPatternInventory();
                    helper.assertTrue(physicalSlots >= 3 && inventory.size() >= physicalSlots,
                            "Use the actual public terminal view, not a guessed GUI page or private backing inventory");
                    // All three are ordinary physical slots; the last slot also exercises the complete public view.
                    seed(helper, inventory, List.of(0, 1, physicalSlots - 1), supported);
                    seed(helper, manipulator.getTerminalPatternInventory(), MANIPULATOR_SLOTS, supported);
                }).thenWaitUntil(() -> sourcesPublished(helper, network, supported))
                .thenExecute(() -> {
                    // Copy the real published definitions before removal: the bus mutates its advertised list in place.
                    publishedBeforeMigration.addAll(clusterSource.buses().getFirst().getAvailablePatterns());
                    helper.assertTrue(publishedBeforeMigration.size() == 3, "All three bus recipes were actually published first");
                    var result = MolecularCenterPatternMigration.migrate(target.center(),
                            FakePlayerFactory.getMinecraft(helper.getLevel()));
                    helper.assertTrue(result.fromProviders() == 6 && result.fromStorageCells() == 3 && result.moved() == 9
                                    && !result.interrupted() && !result.destinationFull(),
                            "The bus and 360-slot host supply six items, the real disk supplies three, with no segment duplicates: " + result);
                    physicalState(helper, network, supported);
                }).thenWaitUntil(() -> {
                    networkOnline(helper, network);
                    var bus = clusterSource.buses().getFirst();
                    helper.assertTrue(bus.getAvailablePatterns().isEmpty() && manipulator.getLogic().getAvailablePatterns().isEmpty(),
                            "Normal extraction notifications must revoke the sources' previously published recipes");
                    var center = target.center();
                    var service = (CraftingService) center.getMainNode().getGrid().getCraftingService();
                    for (var previous : publishedBeforeMigration) {
                        var key = previous.getDefinition();
                        helper.assertTrue(find(center.getLogic().getAvailablePatterns(), key) != null,
                                "The destination must advertise every transferred recipe");
                        helper.assertTrue(!hasProvider(service, previous, bus)
                                        && !hasProvider(service, previous, manipulator.getLogic())
                                        && hasProvider(service, previous, center.getLogic()),
                                "Grid provider routing must replace both source providers with the real target provider");
                        helper.assertTrue(service.getCraftingFor(previous.getOutputs().getFirst().what()).stream()
                                        .anyMatch(details -> details.getDefinition().equals(key)),
                                "The migrated recipe must remain craftable through the actual grid crafting service");
                    }
                }).thenExecute(() -> {
                    var again = MolecularCenterPatternMigration.migrate(target.center(),
                            FakePlayerFactory.getMinecraft(helper.getLevel()));
                    helper.assertTrue(again.fromProviders() == 0 && again.fromStorageCells() == 0 && again.moved() == 0
                                    && !again.interrupted() && !again.destinationFull(),
                            "A repeated request must not copy the destination, revisit segments or invent disk items: " + again);
                    physicalState(helper, network, supported);
                    org.slf4j.LoggerFactory.getLogger(MolecularCenterClusterMigrationGameTests.class).info("PATTERN_MIGRATION_CLUSTER_NETWORK_PASS: formed L4 cluster, online public bus slots, "
                            + "manipulator 0/128/359, finite disk, nine exact items, owning crystals, source revocation and idempotence");
                }).thenSucceed();
    }

    private record ClusterFixture(ECOCraftingSystemBlockEntity controller, List<NEBlockEntity<?, ?>> members,
            List<ECOCraftingPatternBusBlockEntity> buses) {}

    private record NetworkFixture(MolecularCenterPatternMigrationGameTests.Fixture target,
            MolecularManipulatorBlockEntity manipulator, DriveBlockEntity drive, ClusterFixture clusterSource) {}

    private static ClusterFixture buildCluster(GameTestHelper helper, BlockPos pos) {
        var level = helper.getLevel();
        var controller = place(helper, pos, NEBlocks.CRAFTING_SYSTEM_L4.getDefaultState()
                        .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), ECOCraftingSystemBlockEntity.class);
        var definition = controller.getBuildDefinition();
        helper.assertTrue(definition != null, "The cluster controller must expose its real runtime L4 crafting build definition");
        int expand = controller.getMinBuildLength();
        // preview deliberately omits the anchor, which was placed above. Load every planned chunk before building.
        var preview = MultiBlockPlacementService.preview(level, pos, controller.getBlockState(), definition, expand, false);
        helper.assertTrue(!preview.getAllBlocks().isEmpty(), "The real crafting subsystem plan cannot be empty");
        for (var block : preview.getAllBlocks()) {
            var at = block.worldPos();
            level.setChunkForced(at.getX() >> 4, at.getZ() >> 4, true);
            level.getChunkAt(at);
        }
        var plan = MultiBlockPlacementService.preview(level, pos, controller.getBlockState(), definition, expand, false);
        helper.assertTrue(plan.getConflictPositions().isEmpty(), "The cluster placement plan has no conflicting blocks: " + plan.getConflictPositions());
        var builder = FakePlayerFactory.getMinecraft(level);
        var previousMode = builder.gameMode.getGameModeForPlayer();
        try {
            builder.setGameMode(GameType.CREATIVE);
            helper.assertTrue(MultiBlockPlacementService.buildInstant(level, plan, builder),
                    "The cluster's public, permission-checked builder must place the whole real subsystem");
        } finally {
            builder.setGameMode(previousMode);
        }
        var members = new ArrayList<NEBlockEntity<?, ?>>();
        var buses = new ArrayList<ECOCraftingPatternBusBlockEntity>();
        members.add(controller);
        for (var block : plan.getAllBlocks()) {
            var entity = level.getBlockEntity(block.worldPos());
            if (entity instanceof NEBlockEntity<?, ?> member) members.add(member);
            if (entity instanceof ECOCraftingPatternBusBlockEntity bus) buses.add(bus);
        }
        helper.assertTrue(!buses.isEmpty(), "Locate real pattern buses from the built plan, not hard-coded relative coordinates");
        return new ClusterFixture(controller, List.copyOf(members), List.copyOf(buses));
    }

    private static void formed(GameTestHelper helper, ClusterFixture clusterSource) {
        NECraftingCluster cluster = clusterSource.controller().getCluster();
        helper.assertTrue(clusterSource.controller().isFormed() && cluster != null && cluster.getController() == clusterSource.controller(),
                "The real calculator must form a crafting cluster with the placed controller");
        helper.assertTrue(!cluster.getWorkers().isEmpty() && !cluster.getParallelCores().isEmpty(),
                "A real crafting subsystem includes workers and tiered parallel cores");
        helper.assertTrue(cluster.getPatternBuses().size() == clusterSource.buses().size()
                        && cluster.getPatternBuses().containsAll(clusterSource.buses()),
                "Every bus in the placement plan is an actual member of the calculated cluster");
        for (var member : clusterSource.members()) {
            helper.assertTrue(member.isFormed() && member.getCluster() == cluster,
                    "All real subsystem members share the formed cluster: " + member.getBlockPos());
        }
        for (var bus : clusterSource.buses()) {
            helper.assertTrue(bus.getCraftingCluster() == cluster && bus.getCraftingController() == clusterSource.controller(),
                    "Bus execution resolves the real cluster and controller");
        }
    }

    private static void networkOnline(GameTestHelper helper, NetworkFixture network) {
        online(helper, network.target());
        formed(helper, network.clusterSource());
        var grid = network.target().center().getMainNode().getGrid();
        helper.assertTrue(network.clusterSource().controller().getMainNode().isActive()
                        && network.clusterSource().controller().getMainNode().getGrid() == grid,
                "The formed cluster controller has power and a channel on the destination grid");
        for (var bus : network.clusterSource().buses()) {
            helper.assertTrue(bus.getMainNode().isActive() && bus.getGrid() == grid
                            && grid.getActiveMachines(ECOCraftingPatternBusBlockEntity.class).contains(bus),
                    "A real online bus must be visible to active-machine discovery on this exact grid");
        }
        var manipulator = network.manipulator();
        helper.assertTrue(manipulator.getMainNode().isActive() && manipulator.getGrid() == grid,
                "The real single-block manipulator also has power and a channel on the same grid");
        int segments = 0;
        int slots = 0;
        // The concrete-class query returns terminal segments, not MolecularManipulatorBlockEntity instances.
        for (Object candidate : grid.getActiveMachines(MolecularManipulatorBlockEntity.class)) {
            helper.assertTrue(candidate instanceof PatternContainer, "Active query contains public terminal containers");
            var container = (PatternContainer) candidate;
            helper.assertTrue(container != manipulator && SegmentedPatternContainers.unwrap(container) == manipulator,
                    "Every discovered segment unwraps by identity to the one physical host");
            segments++;
            slots += container.getTerminalPatternInventory().size();
        }
        helper.assertTrue(segments == 3 && slots == 360, "Exercise all real 128/128/104 terminal segments, including slot 359");
    }

    private static void seed(GameTestHelper helper, InternalInventory inventory, List<Integer> slots, List<ItemStack> supported) {
        helper.assertTrue(inventory.isEmpty(), "A physical source inventory starts empty");
        for (int index = 0; index < supported.size(); index++) {
            int slot = slots.get(index);
            var stack = supported.get(index);
            helper.assertTrue(inventory.insertItem(slot, stack.copy(), false).isEmpty()
                            && ItemStack.matches(stack, inventory.getStackInSlot(slot))
                            && ItemStack.matches(stack, inventory.extractItem(slot, 1, true)),
                    "Public inventory insertion owns an exact, normally extractable pattern in physical slot " + slot);
        }
    }

    private static void sourcesPublished(GameTestHelper helper, NetworkFixture network, List<ItemStack> supported) {
        networkOnline(helper, network);
        var bus = network.clusterSource().buses().getFirst();
        var manipulator = network.manipulator();
        var service = (CraftingService) network.target().center().getMainNode().getGrid().getCraftingService();
        helper.assertTrue(inventoryCounts(bus.getTerminalPatternInventory()).equals(expectedCounts(supported, 1))
                        && inventoryCounts(manipulator.getTerminalPatternInventory()).equals(expectedCounts(supported, 1)),
                "Each physical host owns exactly the same three component-bearing pattern items before migration");
        for (var stack : supported) {
            var key = AEItemKey.of(stack);
            var busDetails = find(bus.getAvailablePatterns(), key);
            var manipulatorDetails = find(manipulator.getLogic().getAvailablePatterns(), key);
            helper.assertTrue(busDetails != null && manipulatorDetails != null,
                    "Wait for natural source decoding/publication, including the bus's scheduled initial insertion");
            helper.assertTrue(hasProvider(service, busDetails, bus) && hasProvider(service, manipulatorDetails, manipulator.getLogic()),
                    "Both real source providers must be registered by the grid before anything is removed");
            helper.assertTrue(network.drive().getOriginalCellInventory(0).getAvailableStacks().get(key) == 1,
                    "The mounted finite disk owns the third physical copy");
        }
        helper.assertTrue(network.target().center().getLogic().getAvailablePatterns().isEmpty(), "No target recipe predates migration");
    }

    private static void physicalState(GameTestHelper helper, NetworkFixture network, List<ItemStack> supported) {
        for (var bus : network.clusterSource().buses()) {
            helper.assertTrue(bus.getTerminalPatternInventory().isEmpty(), "Migration itself removes all bus source items");
        }
        helper.assertTrue(network.manipulator().getTerminalPatternInventory().isEmpty(),
                "Migration itself removes single-block source slots 0, 128 and 359");
        var retained = Map.of(RETAINED_ITEM, 7L);
        helper.assertTrue(storageCounts(network.drive().getOriginalCellInventory(0)).equals(retained),
                "The real mounted disk loses only its three patterns and neither generates nor spends any blank patterns");
        var disk = network.drive().getInternalInventory().getStackInSlot(0);
        var persisted = BasicCellInventory.createInventory(disk.copy(), null);
        helper.assertTrue(persisted != null && storageCounts(persisted).equals(retained),
                "Source removal must reach the actual cell ItemStack components, not merely an in-memory handler");
        var center = network.target().center();
        var inventory = center.getLogic().getFullPatternInventory();
        helper.assertTrue(inventoryCounts(inventory).equals(expectedCounts(supported, 3)),
                "Nine items survive with exact components: three physical copies of each recipe, not deduplicated or re-encoded");
        int occupied = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                helper.assertTrue(stack.getCount() == 1, "Each committed physical pattern occupies its own destination slot");
                occupied++;
            }
        }
        helper.assertTrue(occupied == 9, "The target contains exactly the nine committed physical items");
        var registries = helper.getLevel().registryAccess();
        var shards = MolecularCenterPatternShards.split(MolecularCenterPatternShards.inventoryList(inventory, registries));
        int crystalItems = 0;
        for (int index = 0; index < MolecularCenterPatternShards.crystals().size(); index++) {
            var part = MolecularCenterPatternShards.crystals().get(index);
            var pos = MolecularCenterStructure.worldPos(center.getBlockPos(), Direction.NORTH, part);
            var crystal = (MolecularCenterCrystalBlockEntity) helper.getLevel().getBlockEntity(pos);
            var expected = shards.get(index);
            helper.assertTrue(crystal != null && crystal.patterns().equals(expected),
                    "Every owning crystal stores the exact slot-local stacks and components for shard " + index);
            helper.assertTrue(crystal.saveWithFullMetadata(registries)
                            .getList(MolecularCenterPatternShards.PATTERNS_TAG, Tag.TAG_COMPOUND).equals(expected),
                    "The real owning crystal serializes exactly its committed shard");
            var drop = ItemStack.parseOptional(registries, (net.minecraft.nbt.CompoundTag) crystal.createDrop().save(registries));
            helper.assertTrue(!drop.isEmpty() && MolecularCenterCrystalBlockEntity.patternsFromItem(drop).equals(expected),
                    "The crystal's real drop/ItemStack codec round-trip preserves exact components and local slot ownership");
            crystalItems += crystal.patterns().size();
        }
        helper.assertTrue(crystalItems == 9, "All nine committed patterns reach their owning crystals immediately");
    }

    private static Map<AEItemKey, Long> inventoryCounts(InternalInventory inventory) {
        var result = new HashMap<AEItemKey, Long>();
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) result.merge(AEItemKey.of(stack), (long) stack.getCount(), Long::sum);
        }
        return result;
    }

    private static Map<AEItemKey, Long> expectedCounts(List<ItemStack> supported, long copies) {
        var result = new HashMap<AEItemKey, Long>();
        for (var stack : supported) result.merge(AEItemKey.of(stack), copies, Long::sum);
        return result;
    }

    private static Map<AEKey, Long> storageCounts(MEStorage storage) {
        var result = new HashMap<AEKey, Long>();
        for (var entry : storage.getAvailableStacks()) {
            if (entry.getLongValue() > 0) result.put(entry.getKey(), entry.getLongValue());
        }
        return result;
    }

    private static IPatternDetails find(Iterable<IPatternDetails> patterns, AEItemKey key) {
        for (var details : patterns) if (details.getDefinition().equals(key)) return details;
        return null;
    }

    private static boolean hasProvider(CraftingService service, IPatternDetails details, ICraftingProvider expected) {
        for (var provider : service.getProviders(details)) if (provider == expected) return true;
        return false;
    }
}
