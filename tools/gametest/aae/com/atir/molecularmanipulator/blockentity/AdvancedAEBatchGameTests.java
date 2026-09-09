package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.crafting.CraftingLink;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.helpers.MachineSource;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.api.crafting.*;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.research.MatterResearchApi;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class AdvancedAEBatchGameTests {
    private static final BlockPos ORIGIN = new BlockPos(1200, 80, 160);

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 400)
    public static void aaeExecutesPublicBatches(GameTestHelper helper) {
        var level = helper.getLevel();
        for (int x = 73; x <= 77; x++) for (int z = 8; z <= 14; z++) {
            level.setChunkForced(x, z, true); level.getChunk(x, z);
        }
        for (var part : MatterFabricationStructure.parts()) if (!MatterFabricationStructure.isController(part))
            level.setBlock(MatterFabricationStructure.worldPos(ORIGIN, Direction.NORTH, part),
                    MatterFabricationStructure.partState(part.type()), 3);
        level.setBlock(ORIGIN, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(ORIGIN, ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
        var controller = (MatterFabricationBlockEntity) level.getBlockEntity(ORIGIN);
        var bay = MatterFabricationStructure.worldPos(ORIGIN, Direction.NORTH,
                MatterFabricationStructure.patternAssemblyBays().get(0));
        level.setBlock(bay, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(bay, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState(), 3);
        var assembly = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(bay);
        var powerPos = ORIGIN.offset(30, 0, 0);
        level.setBlock(powerPos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var power = (CreativeEnergyCellBlockEntity) level.getBlockEntity(powerPos);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                controller.getMainNode().getNode() != null && assembly.getMainNode().getNode() != null
                        && power.getMainNode().getNode() != null, "Wait for native AE2 node initialization"))
                .thenExecute(() -> {
            controller.refreshStructure(); assembly.setControllerPos(ORIGIN);
            GridHelper.createConnection(controller.getMainNode().getNode(), power.getMainNode().getNode());
        }).thenWaitUntil(() -> helper.assertTrue(controller.isStructureFormed()
                && controller.getMainNode().isActive() && assembly.isOperational(), "Wait for the real AE network"))
                .thenExecute(() -> {
            try { verify(helper, controller, assembly); }
            catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
    }

    private static void verify(GameTestHelper helper, MatterFabricationBlockEntity controller,
            MatterFabricationPatternAssemblyBlockEntity assembly) throws ReflectiveOperationException {
        helper.assertTrue(assembly.isOperational(), "Real AE grid and matter assembly active");
        var level = helper.getLevel();
        var key = AEItemKey.of(AEItems.CERTUS_QUARTZ_CRYSTAL.asItem());
        var output = AEItemKey.of(AEItems.CERTUS_QUARTZ_CRYSTAL_CHARGED.asItem());
        var encoded = PatternDetailsHelper.encodeProcessingPattern(new GenericStack[] {new GenericStack(key, 16)}, new GenericStack[] {new GenericStack(output, 16)});
        assembly.getLogic().getPatternInv().setItemDirect(0, encoded);
        assembly.getLogic().updatePatterns();
        var pattern = assembly.getLogic().getAvailablePatterns().get(0);
        var candidate = MatterRecipeIndex.get(level).candidates(MatterFabricationBatch.patternOutputs(pattern)).get(0);
        for (var research : MatterRecipeIndex.get(level).researchFor(candidate.id()))
            MatterResearchApi.setCompletionCount(controller, research.id().toString(), 1);
        var grid = controller.getMainNode().getGrid();
        var service = (CraftingService) grid.getCraftingService();
        service.refreshNodeCraftingProvider(assembly.getMainNode().getNode());
        var cpu = createCpu(controller, pattern, 65_536, key, output);
        long start = System.nanoTime();
        int operations = cpu.craftingLogic.executeCrafting(Integer.MAX_VALUE, service, grid.getEnergyService(), level);
        double elapsed = (System.nanoTime() - start) / 1_000_000.0;
        check(helper, operations == 1 && remaining(cpu, pattern) == 0, "65,536 crafts use one actual AAE operation");
        check(helper, cpu.craftingLogic.getWaitingFor(output) == 1_048_576 && cpu.getInventory().list.isEmpty(), "Exact AAE waiting and input counts");
        check(helper, assembly.getBuffer().contents(false).get(0).amount() == 1_048_576, "Matter queue owns all inputs");
        var saved = new CompoundTag();
        assembly.getBuffer().save(saved); assembly.getBuffer().clear(); assembly.getBuffer().load(saved);
        check(helper, assembly.getBuffer().contents(false).get(0).amount() == 1_048_576, "Accepted queue survives reload");
        var link = cpu.craftingLogic.getLastLink(); cpu.craftingLogic.cancel();
        check(helper, link.isCanceled() && !cpu.craftingLogic.hasJob(), "AAE cancellation clears the task");
        check(helper, assembly.getBuffer().contents(false).get(0).amount() == 1_048_576, "Cancellation does not duplicate machine-owned inputs");
        assembly.getBuffer().clear();
        System.out.println("AAE_BATCH_REAL_PASS: crafts=65536 operations=" + operations + " executeMs=" + elapsed);

        for (Mode mode : Mode.values()) {
            var provider = new ProbeProvider(pattern, mode);
            // AE2 15 has no global provider registration API; supply only this fault-injection endpoint.
            var isolated = new CraftingService(grid, grid.getStorageService(), grid.getEnergyService()) {
                @Override public Iterable<ICraftingProvider> getProviders(IPatternDetails requested) {
                    return List.of(provider);
                }
            };
            var testCpu = createCpu(controller, pattern, 128, key, output);
            testCpu.craftingLogic.executeCrafting(Integer.MAX_VALUE, isolated, grid.getEnergyService(), level);
            if (mode == Mode.ACCEPT_THEN_THROW || mode == Mode.RESERVE_BUSY) {
                check(helper, provider.commits == 1 && provider.owned == 2048 && remaining(testCpu, pattern) == 0,
                        "Accepted ownership survives " + mode);
                check(helper, testCpu.getInventory().list.isEmpty() && testCpu.craftingLogic.getWaitingFor(output) == 2048, "Accepted counts " + mode);
            } else if (mode == Mode.REJECT || mode == Mode.THROW_BEFORE_ACCEPT) {
                check(helper, provider.owned == 0 && remaining(testCpu, pattern) == 128
                        && testCpu.getInventory().list.get(key) == 2048 && testCpu.craftingLogic.getWaitingFor(output) == 0,
                        "Rejected aggregate restores task and all materials " + mode);
            } else if (mode == Mode.LIMIT_AND_BACKPRESSURE) {
                check(helper, provider.owned == 128 && remaining(testCpu, pattern) == 120
                        && testCpu.getInventory().list.get(key) == 1920, "Partial admission preserves unallocated crafts");
                testCpu.craftingLogic.executeCrafting(Integer.MAX_VALUE, isolated, grid.getEnergyService(), level);
                check(helper, provider.commits == 1, "Capacity backpressure persists across same-tick execute calls");
                helper.runAfterDelay(1, () -> {
                    testCpu.craftingLogic.executeCrafting(Integer.MAX_VALUE, isolated, grid.getEnergyService(), level);
                    check(helper, provider.commits == 2, "Backpressure resets next tick");
                });
            } else if (mode == Mode.SLOW_SINGLE) {
                check(helper, provider.singles > 0 && provider.singles < 128, "Slow fallback is bounded");
                int previous = provider.singles;
                testCpu.craftingLogic.executeCrafting(Integer.MAX_VALUE, isolated, grid.getEnergyService(), level);
                check(helper, previous == provider.singles, "Repeated execute calls cannot renew the time slice");
                check(helper, testCpu.getInventory().list.get(key) + provider.owned == 2048, "Time slicing does not lose input");
            }
            check(helper, provider.closes == provider.prepares, "Every admission is closed exactly once " + mode);
            System.out.println("AAE_BATCH_CASE_PASS: " + mode);
        }
        var nearFull = createCpu(controller, pattern, 128, key, output);
        var job = field(AdvCraftingCPULogic.class, "job").get(nearFull.craftingLogic);
        var waiting = (ListCraftingInventory) field(job.getClass(), "waitingFor").get(job);
        waiting.insert(output, Long.MAX_VALUE - 31, Actionable.MODULATE);
        nearFull.craftingLogic.executeCrafting(Integer.MAX_VALUE, service, grid.getEnergyService(), level);
        check(helper, nearFull.craftingLogic.getWaitingFor(output) == Long.MAX_VALUE - 15
                && remaining(nearFull, pattern) == 127, "Waiting-for headroom prevents long overflow even on one-craft fallback");
        assembly.getBuffer().clear();
        helper.runAfterDelay(2, () -> { com.atir.molecularmanipulator.MolecularManipulator.LOGGER.info("AAE_BATCH_ALL_PASS"); helper.succeed(); });
    }

    private static void check(GameTestHelper helper, boolean valid, String message) { helper.assertTrue(valid, message); }

    private static Field field(Class<?> type, String name) throws ReflectiveOperationException {
        var field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }

    private static long remaining(TestCpu cpu, IPatternDetails pattern) throws ReflectiveOperationException {
        var job = field(AdvCraftingCPULogic.class, "job").get(cpu.craftingLogic);
        var tasks = (Map<?, ?>) field(job.getClass(), "tasks").get(job);
        var task = tasks.get(pattern);
        return task == null ? 0 : field(task.getClass(), "value").getLong(task);
    }

    private static TestCpu createCpu(MatterFabricationBlockEntity controller, IPatternDetails pattern,
            long count, AEKey key, AEKey output) throws ReflectiveOperationException {
        var cpu = new TestCpu(controller);
        var plan = new ICraftingPlan() {
            public GenericStack finalOutput() { return new GenericStack(output, count * 16); }
            public long bytes() { return 1; }
            public boolean simulation() { return false; }
            public boolean multiplePaths() { return false; }
            public KeyCounter usedItems() { return new KeyCounter(); }
            public KeyCounter emittedItems() { return new KeyCounter(); }
            public KeyCounter missingItems() { return new KeyCounter(); }
            public Map<IPatternDetails, Long> patternTimes() { return Map.of(pattern, count); }
        };
        var tag = new CompoundTag(); tag.putUUID("craftId", UUID.randomUUID()); tag.putBoolean("req", false); tag.putBoolean("standalone", true);
        var link = new CraftingLink(tag, cpu);
        var type = Class.forName("net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob");
        var listenerType = Class.forName(type.getName() + "$CraftingDifferenceListener");
        var listener = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {listenerType}, (instance, method, arguments) -> null);
        var constructor = type.getDeclaredConstructor(ICraftingPlan.class, listenerType, CraftingLink.class, Integer.class);
        constructor.setAccessible(true);
        field(AdvCraftingCPULogic.class, "job").set(cpu.craftingLogic, constructor.newInstance(plan, listener, link, null));
        cpu.getInventory().insert(key, count * 16, Actionable.MODULATE);
        return cpu;
    }

    /** The real AAE logic runs unchanged; only the physical CPU shell is supplied by the test grid. */
    private static final class TestCpu extends AdvCraftingCPU {
        private final MatterFabricationBlockEntity controller;
        TestCpu(MatterFabricationBlockEntity controller) { super(null, UUID.randomUUID(), Long.MAX_VALUE); this.controller = controller; }
        @Override public boolean isActive() { return true; }
        @Override public Level getLevel() { return controller.getLevel(); }
        @Override public IGrid getGrid() { return controller.getMainNode().getGrid(); }
        @Override public IActionSource getSrc() { return new MachineSource(controller); }
        @Override public void markDirty() {}
        @Override public int getCoProcessors() { return 65536; }
        @Override public void updateOutput(GenericStack output) { finalOutput = output; }
    }

    private enum Mode { RESERVE_BUSY, ACCEPT_THEN_THROW, REJECT, THROW_BEFORE_ACCEPT, LIMIT_AND_BACKPRESSURE, SLOW_SINGLE }

    private static final class ProbeProvider implements OmniBatchCraftingProvider {
        final IPatternDetails pattern;
        final Mode mode;
        int prepares, commits, closes, singles;
        long owned;
        boolean reserved;
        ProbeProvider(IPatternDetails pattern, Mode mode) { this.pattern = pattern; this.mode = mode; }
        public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
        public boolean isBusy() { return mode == Mode.RESERVE_BUSY && reserved; }
        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
            if (mode != Mode.SLOW_SINGLE) return false;
            long finish = System.nanoTime() + 3_000_000;
            while (System.nanoTime() - finish < 0) Thread.onSpinWait();
            for (var counter : inputs) for (var entry : counter) owned += entry.getLongValue();
            singles++; return true;
        }
        public OmniBatchAdmission prepareOmniBatch(OmniBatchProbe probe) {
            if (mode == Mode.SLOW_SINGLE) return null;
            prepares++; reserved = true;
            return new OmniBatchAdmission() {
                public long maxCrafts() { return mode == Mode.LIMIT_AND_BACKPRESSURE ? 8 : probe.requestedMaxCrafts(); }
                public void commit(OmniBatchDelivery delivery) {
                    commits++;
                    if (mode == Mode.THROW_BEFORE_ACCEPT) throw new IllegalStateException("Expected pre-accept failure");
                    if (mode == Mode.REJECT) { delivery.reject(OmniBatchDelivery.Rejection.reject(OmniBatchDelivery.RejectReason.CAPACITY_CHANGED)); return; }
                    for (var input : delivery.request().inputs()) owned += input.amount();
                    delivery.accept(new OmniBatchDelivery.Receipt(OmniBatchDelivery.Ownership.PERSISTED_PROVIDER_QUEUE,
                            OmniBatchDelivery.Backpressure.RECHECK_NEXT_TICK));
                    if (mode == Mode.ACCEPT_THEN_THROW) throw new IllegalStateException("Expected post-accept failure");
                }
                public void close() { closes++; reserved = false; }
            };
        }
    }
}
