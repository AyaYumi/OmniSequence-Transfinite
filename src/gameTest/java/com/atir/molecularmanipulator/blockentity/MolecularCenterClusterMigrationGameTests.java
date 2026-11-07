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
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.me.cells.BasicCellInventory;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.integration.ae2.SegmentedPatternContainers;
import com.atir.molecularmanipulator.registry.ModContent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Opt-in real ECOcraft L4 cluster, its public bus inventory, a segmented host and a finite disk.
 * ECO's public Forge 20.5.0 build/cluster APIs are loaded reflectively: no optional compile dependency,
 * substituted bus, forced formation, private inventory access or manual source publication refresh.
 */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MolecularCenterClusterMigrationGameTests {
    private static final List<Integer> MANIPULATOR_SLOTS = List.of(0, 128, 359);
    private static final AEItemKey RETAINED_ITEM = AEItemKey.of(Items.DIAMOND);
    private static final String ECO = "cn.dancingsnow.neoecoae.";

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
                    "The disk owns one real encoded pattern of each supported category");
        }
        helper.assertTrue(cell.insert(RETAINED_ITEM, 7, Actionable.MODULATE, IActionSource.empty()) == 7,
                "Keep unrelated disk contents as a persistence/conservation sentinel");
        cell.persist();
        drive.getInternalInventory().setItemDirect(0, disk);
        var publishedBeforeMigration = new ArrayList<IPatternDetails>();

        helper.startSequence().thenWaitUntil(() -> {
                    nodesReady(helper, target);
                    for (var member : clusterSource.members()) {
                        helper.assertTrue(member.getMainNode().getNode() != null, "Every planned cluster member has a real grid node");
                    }
                }).thenExecute(() -> call(clusterSource.controller(), "rebuildMultiblock"))
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
                                "Use genuinely decodable crafting, stonecutting and smithing patterns with their real NBT");
                    }
                    var bus = clusterSource.buses().get(0);
                    int physicalSlots = (Integer) call(bus, "getPatternSlotCount");
                    var inventory = bus.getTerminalPatternInventory();
                    helper.assertTrue(physicalSlots >= 3 && inventory.size() >= physicalSlots,
                            "Use the actual public terminal view, not a guessed GUI page or private backing inventory");
                    seed(helper, inventory, List.of(0, 1, physicalSlots - 1), supported);
                    seed(helper, manipulator.getTerminalPatternInventory(), MANIPULATOR_SLOTS, supported);
                }).thenWaitUntil(() -> sourcesPublished(helper, network, supported))
                .thenExecute(() -> {
                    // ECO mutates its advertised list in place; preserve the real definitions before removal.
                    publishedBeforeMigration.addAll(provider(clusterSource.buses().get(0)).getAvailablePatterns());
                    helper.assertTrue(publishedBeforeMigration.size() == 3, "All three bus recipes were actually published first");
                    var result = MolecularCenterPatternMigration.migrate(target.center(),
                            FakePlayerFactory.getMinecraft(helper.getLevel()));
                    helper.assertTrue(result.fromProviders() == 6 && result.fromStorageCells() == 3 && result.moved() == 9
                                    && !result.interrupted() && !result.destinationFull(),
                            "The bus and 360-slot host supply six items, the real disk supplies three, without segment duplicates: " + result);
                    physicalState(helper, network, supported);
                }).thenWaitUntil(() -> {
                    networkOnline(helper, network);
                    var bus = clusterSource.buses().get(0);
                    helper.assertTrue(provider(bus).getAvailablePatterns().isEmpty() && manipulator.getLogic().getAvailablePatterns().isEmpty(),
                            "Normal extraction notifications revoke the sources' previously published recipes");
                    var center = target.center();
                    var service = (CraftingService) center.getMainNode().getGrid().getCraftingService();
                    for (var previous : publishedBeforeMigration) {
                        var key = previous.getDefinition();
                        helper.assertTrue(find(center.getLogic().getAvailablePatterns(), key) != null,
                                "The destination advertises every transferred recipe");
                        helper.assertTrue(!hasProvider(service, previous, provider(bus))
                                        && !hasProvider(service, previous, manipulator.getLogic())
                                        && hasProvider(service, previous, center.getLogic()),
                                "Grid provider routing replaces both source providers with the real target provider");
                        helper.assertTrue(service.getCraftingFor(previous.getPrimaryOutput().what()).stream()
                                        .anyMatch(details -> details.getDefinition().equals(key)),
                                "The migrated recipe remains craftable through the actual grid crafting service");
                    }
                }).thenExecute(() -> {
                    var again = MolecularCenterPatternMigration.migrate(target.center(),
                            FakePlayerFactory.getMinecraft(helper.getLevel()));
                    helper.assertTrue(again.fromProviders() == 0 && again.fromStorageCells() == 0 && again.moved() == 0
                                    && !again.interrupted() && !again.destinationFull(),
                            "A repeated request cannot copy the destination, revisit segments or invent disk items: " + again);
                    physicalState(helper, network, supported);
                    org.slf4j.LoggerFactory.getLogger(MolecularCenterClusterMigrationGameTests.class).info(
                            "PATTERN_MIGRATION_CLUSTER_NETWORK_PASS: formed L4 cluster, online public bus slots, "
                                    + "manipulator 0/128/359, finite disk, nine exact NBT items, owning crystals, ME cache, source revocation and idempotence");
                }).thenSucceed();
    }

    private record ClusterFixture(AENetworkBlockEntity controller, List<AENetworkBlockEntity> members,
            List<PatternContainer> buses) {}

    private record NetworkFixture(MolecularCenterPatternMigrationGameTests.Fixture target,
            MolecularManipulatorBlockEntity manipulator, DriveBlockEntity drive, ClusterFixture clusterSource) {}

    private static ClusterFixture buildCluster(GameTestHelper helper, BlockPos pos) {
        var controllerType = loadAddon("blocks.entity.crafting.ECOCraftingSystemBlockEntity");
        var memberType = loadAddon("blocks.entity.NEBlockEntity");
        var busType = loadAddon("blocks.entity.crafting.ECOCraftingPatternBusBlockEntity");
        var definitionType = loadAddon("multiblock.definition.MultiBlockDefinition");
        var planType = loadAddon("multiblock.placement.MultiBlockPlacementPlan");
        var placement = loadAddon("multiblock.placement.MultiBlockPlacementService");
        var previewMethod = method(placement, "preview", Level.class, BlockPos.class, BlockState.class,
                definitionType, int.class, boolean.class);
        var buildMethod = method(placement, "buildInstant", ServerLevel.class, planType, ServerPlayer.class);
        var id = new ResourceLocation("neoecoae", "crafting_system_l4");
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "The opted-in ECOcraft addon registers " + id);
        var state = BuiltInRegistries.BLOCK.get(id).defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        var controller = (AENetworkBlockEntity) place(helper, pos, state, controllerType);
        var definition = call(controller, "getBuildDefinition");
        helper.assertTrue(definitionType.isInstance(definition), "Use the controller's real runtime L4 crafting build definition");
        int expand = (Integer) call(controller, "getMinBuildLength");
        var level = helper.getLevel();
        // preview omits the already placed controller anchor. Load every planned chunk before building.
        var preview = invoke(previewMethod, null, level, pos, controller.getBlockState(), definition, expand, false);
        helper.assertTrue(!list(preview, "getAllBlocks").isEmpty(), "The real crafting subsystem plan cannot be empty");
        for (var block : list(preview, "getAllBlocks")) {
            var at = (BlockPos) call(block, "worldPos");
            level.setChunkForced(at.getX() >> 4, at.getZ() >> 4, true);
            level.getChunkAt(at);
        }
        var plan = invoke(previewMethod, null, level, pos, controller.getBlockState(), definition, expand, false);
        helper.assertTrue(list(plan, "getConflictPositions").isEmpty(),
                "The cluster placement plan has no conflicts: " + list(plan, "getConflictPositions"));
        var builder = FakePlayerFactory.getMinecraft(level);
        var previousMode = builder.gameMode.getGameModeForPlayer();
        try {
            builder.setGameMode(GameType.CREATIVE);
            helper.assertTrue(Boolean.TRUE.equals(invoke(buildMethod, null, level, plan, builder)),
                    "ECO's public, permission-checked builder places the complete real subsystem");
        } finally {
            builder.setGameMode(previousMode);
        }
        var members = new ArrayList<AENetworkBlockEntity>();
        var buses = new ArrayList<PatternContainer>();
        members.add(controller);
        for (var block : list(plan, "getAllBlocks")) {
            var entity = level.getBlockEntity((BlockPos) call(block, "worldPos"));
            if (memberType.isInstance(entity)) members.add((AENetworkBlockEntity) entity);
            if (busType.isInstance(entity)) {
                helper.assertTrue(entity instanceof PatternContainer && entity instanceof ICraftingProvider,
                        "The placed real ECO bus implements the public inventory and provider contracts");
                buses.add((PatternContainer) entity);
            }
        }
        helper.assertTrue(!buses.isEmpty(), "Find real pattern buses in the built plan, not hard-coded relative coordinates");
        return new ClusterFixture(controller, List.copyOf(members), List.copyOf(buses));
    }

    private static void formed(GameTestHelper helper, ClusterFixture source) {
        var cluster = call(source.controller(), "getCluster");
        helper.assertTrue(Boolean.TRUE.equals(call(source.controller(), "isFormed")) && cluster != null
                        && call(cluster, "getController") == source.controller(),
                "The real calculator forms a crafting cluster with the placed controller");
        helper.assertTrue(!list(cluster, "getWorkers").isEmpty() && !list(cluster, "getParallelCores").isEmpty(),
                "A real crafting subsystem includes workers and tiered parallel cores");
        var buses = list(cluster, "getPatternBuses");
        helper.assertTrue(buses.size() == source.buses().size() && buses.containsAll(source.buses()),
                "Every bus in the placement plan belongs to the calculated cluster");
        for (var member : source.members()) {
            helper.assertTrue(Boolean.TRUE.equals(call(member, "isFormed")) && call(member, "getCluster") == cluster,
                    "All real subsystem members share the formed cluster: " + member.getBlockPos());
        }
        for (var bus : source.buses()) {
            helper.assertTrue(call(bus, "getCraftingCluster") == cluster && call(bus, "getCraftingController") == source.controller(),
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
            helper.assertTrue(((AENetworkBlockEntity) bus).getMainNode().isActive() && bus.getGrid() == grid
                            && grid.getActiveMachines(bus.getClass()).contains(bus),
                    "A real online bus is visible to active-machine discovery on this exact grid");
        }
        var manipulator = network.manipulator();
        helper.assertTrue(manipulator.getMainNode().isActive() && manipulator.getGrid() == grid,
                "The real single-block manipulator has power and a channel on the same grid");
        int segments = 0;
        int slots = 0;
        // This concrete-class query returns terminal segments, not block-entity instances.
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
                    "Public insertion owns an exact, normally extractable pattern in physical slot " + slot);
        }
    }

    private static void sourcesPublished(GameTestHelper helper, NetworkFixture network, List<ItemStack> supported) {
        networkOnline(helper, network);
        var bus = network.clusterSource().buses().get(0);
        var manipulator = network.manipulator();
        var grid = network.target().center().getMainNode().getGrid();
        var service = (CraftingService) grid.getCraftingService();
        helper.assertTrue(inventoryCounts(bus.getTerminalPatternInventory()).equals(expectedCounts(supported, 1))
                        && inventoryCounts(manipulator.getTerminalPatternInventory()).equals(expectedCounts(supported, 1)),
                "Each physical host owns exactly the same three NBT-bearing pattern items before migration");
        for (var stack : supported) {
            var key = AEItemKey.of(stack);
            var busDetails = find(provider(bus).getAvailablePatterns(), key);
            var manipulatorDetails = find(manipulator.getLogic().getAvailablePatterns(), key);
            helper.assertTrue(busDetails != null && manipulatorDetails != null,
                    "Wait for natural source decoding/publication, including the bus's scheduled insertion");
            helper.assertTrue(hasProvider(service, busDetails, provider(bus)) && hasProvider(service, manipulatorDetails, manipulator.getLogic()),
                    "Both real source providers are registered by the grid before anything is removed");
            helper.assertTrue(network.drive().getOriginalCellInventory(0).getAvailableStacks().get(key) == 1,
                    "The mounted finite disk owns the third physical copy");
            helper.assertTrue(grid.getStorageService().getCachedInventory().get(key) == 1,
                    "Prime the actual ME cache with the disk's physical copy before direct-cell extraction");
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
                "The mounted disk loses only its three patterns and neither generates nor spends blank patterns");
        var disk = network.drive().getInternalInventory().getStackInSlot(0);
        var persisted = BasicCellInventory.createInventory(ItemStack.of(disk.save(new CompoundTag())), null);
        helper.assertTrue(persisted != null && storageCounts(persisted).equals(retained),
                "Source removal reaches the actual saved cell ItemStack NBT, not merely an in-memory handler");
        var center = network.target().center();
        for (var stack : supported) {
            helper.assertTrue(center.getGrid().getStorageService().getCachedInventory().get(AEItemKey.of(stack)) == 0,
                    "ME cache no longer advertises a migrated physical disk pattern");
        }
        helper.assertTrue(center.getGrid().getStorageService().getCachedInventory().get(RETAINED_ITEM) == 7,
                "ME cache retains the unrelated disk contents");
        var inventory = center.getLogic().getFullPatternInventory();
        helper.assertTrue(inventoryCounts(inventory).equals(expectedCounts(supported, 3)),
                "Nine items survive with exact NBT: three physical copies of each recipe, not deduplicated or re-encoded");
        int occupied = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                helper.assertTrue(stack.getCount() == 1, "Each committed physical pattern occupies its own destination slot");
                occupied++;
            }
        }
        helper.assertTrue(occupied == 9, "The target contains exactly the nine committed physical items");
        var shards = MolecularCenterPatternShards.split(MolecularCenterPatternShards.inventoryList(inventory));
        int crystalItems = 0;
        for (int index = 0; index < MolecularCenterPatternShards.crystals().size(); index++) {
            var part = MolecularCenterPatternShards.crystals().get(index);
            var pos = MolecularCenterStructure.worldPos(center.getBlockPos(), Direction.NORTH, part);
            var crystal = (MolecularCenterCrystalBlockEntity) helper.getLevel().getBlockEntity(pos);
            var expected = shards.get(index);
            helper.assertTrue(crystal != null && crystal.patterns().equals(expected),
                    "Every owning crystal stores the exact slot-local stacks and NBT for shard " + index);
            helper.assertTrue(crystal.saveWithFullMetadata()
                            .getList(MolecularCenterPatternShards.PATTERNS_TAG, Tag.TAG_COMPOUND).equals(expected),
                    "The real owning crystal serializes exactly its committed shard");
            var drop = ItemStack.of(crystal.createDrop().save(new CompoundTag()));
            helper.assertTrue(!drop.isEmpty() && MolecularCenterCrystalBlockEntity.patternsFromItem(drop).equals(expected),
                    "The crystal's real drop/ItemStack NBT round-trip preserves exact NBT and local slot ownership");
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

    private static ICraftingProvider provider(PatternContainer bus) {
        return (ICraftingProvider) bus;
    }

    private static boolean hasProvider(CraftingService service, IPatternDetails details, ICraftingProvider expected) {
        for (var provider : service.getProviders(details)) if (provider == expected) return true;
        return false;
    }

    private static Class<?> loadAddon(String path) {
        try {
            return Class.forName(ECO + path);
        } catch (ClassNotFoundException | LinkageError failure) {
            throw new AssertionError("Requires -PgameTestPatternCluster and the real Forge ECOcraft/LDLib runtime: " + ECO + path, failure);
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Missing public Forge ECOcraft API: " + type.getName() + "." + name, failure);
        }
    }

    private static Object invoke(Method method, Object target, Object... arguments) {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            throw new AssertionError("Real ECOcraft API failed: " + method, failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot invoke public ECOcraft API: " + method, failure);
        }
    }

    private static Object call(Object target, String name) {
        return invoke(method(target.getClass(), name), target);
    }

    private static List<?> list(Object target, String name) {
        return (List<?>) call(target, name);
    }
}
