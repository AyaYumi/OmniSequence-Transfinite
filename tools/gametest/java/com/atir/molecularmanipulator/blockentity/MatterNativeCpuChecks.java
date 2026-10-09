package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.provider.*;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapters;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.*;
import com.moakiee.thunderbolt.api.crafting.batch.*;
import com.moakiee.thunderbolt.core.crafting.batch.*;
import java.util.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;

/** Executes installed CPU dispatch engines and their actual API classes, without API stubs. */
final class MatterNativeCpuChecks {
    static void run(GameTestHelper helper, MatterFabricationPatternAssemblyBlockEntity assembly,
            IPatternDetails pattern, Map<AEKey, Long> unit) {
        if (ModList.get().isLoaded("neoecoae")) Eco.run(helper, assembly, pattern, unit);
        if (ModList.get().isLoaded("thunderbolt")) Thunderbolt.run(helper, assembly, pattern, unit);
        if (ModList.get().isLoaded("data_energistics")) Trinity.run(helper, assembly, pattern, unit);
    }

    private static final class Eco {
    private static void run(GameTestHelper helper, MatterFabricationPatternAssemblyBlockEntity assembly,
            IPatternDetails pattern, Map<AEKey, Long> unit) {
        helper.assertTrue(ECOFastPathFacade.supports(assembly.getLogic()), "ECO CPU must recognize the assembly's public fast-path interface");
        var target = ECOFastPathFacade.resolveProvider(assembly.getLogic());
        var context = new ECOBatchDispatchContext(pattern, List.of(MatterCpuBatchGameTests.stacks(unit)),
                pattern.getOutputs(), List.of(), helper.getLevel(), UUID.randomUUID());
        var bad = target.eco$prepareFastPath(context);
        var wrong = MatterPatternBuffer.scaled(unit, 8); wrong.put(wrong.keySet().iterator().next(), 15L);
        helper.assertTrue(!bad.push(new ECOFastPathDispatchProvider.Batch(8, MatterCpuBatchGameTests.stacks(wrong),
                MatterCpuBatchGameTests.stacks(MatterPatternBuffer.scaled(MatterFabricationBatch.patternOutputs(pattern), 8)), List.of()))
                && !assembly.getBuffer().hasContents(), "ECO must reject incomplete full-batch inputs");
        var inventory = new ListCraftingInventory(ignored -> {});
        MatterPatternBuffer.scaled(unit, 1026).forEach(inventory.list::add);
        var outputs = new KeyCounter(); pattern.getOutputs().forEach(stack -> outputs.add(stack.what(), stack.amount()));
        var prepared = ECOFastPathFacade.prepare(assembly.getLogic(), pattern, prototype(pattern), outputs,
                new KeyCounter(), inventory, 1026, 0, null, helper.getLevel(), UUID.randomUUID());
        helper.assertTrue(prepared != null && prepared.craftCount() == 1026, "Actual ECO dispatcher must admit 1026 crafts");
        var committed = new boolean[1];
        helper.assertTrue(prepared.submit(power -> new ECOFastPathFacade.Reservation() {
            public void commit() { committed[0] = true; }
            public void refund() { throw new AssertionError("Successful ECO batch must not refund energy"); }
        }) && committed[0] && inventory.list.isEmpty(), "ECO dispatch transaction must transfer queue ownership and debit materials exactly once");
        MatterCpuBatchGameTests.assertQueue(helper, assembly, MatterPatternBuffer.scaled(unit, 1026), "ECO CPU");
        System.out.println("MATTER_ECO_API_PASS: real fast-path planner dispatched 1026 crafts");
    }
    }

    private static final class Thunderbolt {
    private static void run(GameTestHelper helper, MatterFabricationPatternAssemblyBlockEntity assembly,
            IPatternDetails pattern, Map<AEKey, Long> unit) {
        var provider = (IBatchCraftingProvider) (Object) assembly.getLogic();
        var prototype = MatterCpuBatchGameTests.counters(unit);
        helper.assertTrue(provider.pushBatch(pattern, prototype, 1026) == 0 && MatterCpuBatchGameTests.amounts(prototype).equals(unit),
                "Thunderbolt must accept the full batch and retain its reusable single-copy template");
        MatterCpuBatchGameTests.assertQueue(helper, assembly, MatterPatternBuffer.scaled(unit, 1026), "Thunderbolt API");
        var inventory = new ListCraftingInventory(ignored -> {});
        MatterPatternBuffer.scaled(unit, 1026).forEach(inventory.list::add);
        var waiting = new ListCraftingInventory(ignored -> {});
        var value = new long[]{1026};
        BatchTaskHandle task = new BatchTaskHandle() {
            public IPatternDetails details() { return pattern; }
            public long getValue() { return value[0]; }
            public void setValue(long amount) { value[0] = amount; }
        };
        var tasks = new ArrayList<BatchTaskHandle>(List.of(task));
        var job = new BatchJobView() {
            public net.minecraft.world.level.Level level() { return helper.getLevel(); }
            public Iterator<BatchTaskHandle> taskIterator() { return tasks.iterator(); }
            public ListCraftingInventory waitingFor() { return waiting; }
            public UUID craftingId() { return UUID.randomUUID(); }
            public void addContainerMaxItems(long amount, AEKeyType type) { }
        };
        var grid = assembly.getMainNode().getGrid();
        var result = BatchExecutor.runBatchOnly(1, BatchCpuAccounting.Mode.SUCCESSFUL_DISPATCH,
                (CraftingService) grid.getCraftingService(), grid.getEnergyService(), job, inventory,
                new HashMap<>(), () -> {});
        helper.assertTrue(result.dispatchedCopies() == 1026 && value[0] == 0 && inventory.list.isEmpty(),
                "Actual Thunderbolt CPU engine must debit materials and task count exactly once");
        helper.assertTrue(waiting.list.getFirstEntry().getLongValue() == 1026, "Thunderbolt must account for exactly 1026 outputs");
        MatterCpuBatchGameTests.assertQueue(helper, assembly, MatterPatternBuffer.scaled(unit, 1026), "Thunderbolt CPU engine");
        System.out.println("MATTER_THUNDERBOLT_API_PASS: real batch executor dispatched 1026 crafts");
    }
    }

    private static final class Trinity {
    private static void run(GameTestHelper helper, MatterFabricationPatternAssemblyBlockEntity assembly,
            IPatternDetails pattern, Map<AEKey, Long> unit) {
        var logic = assembly.getLogic();
        helper.assertTrue(CountedCraftingProviderAdapters.supportsCountedDispatch(logic), "Trinity must discover the registered counted API adapter");
        var prototype = MatterCpuBatchGameTests.counters(unit);
        var snapshots = CountedCraftingProviderAdapters.captureCapacity(logic, new CraftingProviderId(1, 1),
                pattern, prototype, 1026, "fabrication", 1, 1, helper.getLevel().getGameTime());
        helper.assertTrue(snapshots.size() == 1, "Trinity must publish one aggregate provider route");
        var prepared = CountedCraftingProviderAdapters.prepare(logic, pattern, prototype, 1026,
                snapshots.getFirst(), CraftingDispatchTargetAvailability.all());
        helper.assertTrue(prepared.accepted() && prepared.admission().count() == 1026, "Actual Trinity registry must prepare 1026 crafts");
        helper.assertTrue(prepared.admission().commit(prototype) && prepared.admission().hasTransferredInputOwnership()
                && MatterCpuBatchGameTests.amounts(prototype).equals(unit), "Trinity must transfer ownership without clearing its template");
        helper.assertTrue(!prepared.admission().commit(prototype), "Trinity admission must reject duplicate commits");
        MatterCpuBatchGameTests.assertQueue(helper, assembly, MatterPatternBuffer.scaled(unit, 1026), "Trinity CPU");
        var savedPattern = logic.getPatternInv().getStackInSlot(0).copy();
        var controllerPos = assembly.getController().getBlockPos();
        var saved = assembly.saveWithoutMetadata(helper.getLevel().registryAccess());
        long revision = CountedCraftingProviderAdapters.mutationRevision();
        assembly.onChunkUnloaded();
        // Data Energistics also injects a legacy interface into all AE pattern providers;
        // supportsCountedDispatch remains true even after this native adapter is removed.
        helper.assertTrue(CountedCraftingProviderAdapters.mutationRevision() == revision + 1,
                "Chunk unload must unregister exactly one native Trinity adapter");
        var pos = assembly.getBlockPos(); var state = assembly.getBlockState();
        helper.getLevel().setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(pos, state, 3);
        var replacement = (MatterFabricationPatternAssemblyBlockEntity) helper.getLevel().getBlockEntity(pos);
        replacement.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.runAfterDelay(4, () -> {
            replacement.setControllerPos(controllerPos);
            replacement.getLogic().getPatternInv().setItemDirect(0, savedPattern); replacement.getLogic().updatePatterns();
            helper.assertTrue(CountedCraftingProviderAdapters.mutationRevision() == revision + 2,
                    "Reload must register exactly one fresh native adapter");
            helper.assertTrue(replacement.isOperational(), "Recreated assembly must reconnect to the formed well");
            var fresh = replacement.getLogic(); var restored = fresh.getAvailablePatterns().getFirst();
            var copy = MatterCpuBatchGameTests.counters(unit);
            var route = CountedCraftingProviderAdapters.captureCapacity(fresh, new CraftingProviderId(1, 2), restored,
                    copy, 8, "reloaded", 2, 2, helper.getLevel().getGameTime()).getFirst();
            helper.assertTrue(CountedCraftingProviderAdapters.prepare(fresh, restored, copy, 8,
                    route, CraftingDispatchTargetAvailability.all()).admission().count() == 8,
                    "Reloaded Trinity adapter must retain native batching");
            System.out.println("MATTER_TRINITY_API_PASS: real counted registry dispatched 1026 crafts and reloaded");
        });
    }
    }

    private static KeyCounter[] prototype(IPatternDetails pattern) {
        var result = new KeyCounter[pattern.getInputs().length];
        for (int slot = 0; slot < result.length; slot++) {
            var input = pattern.getInputs()[slot]; var stack = input.getPossibleInputs()[0];
            result[slot] = new KeyCounter(); result[slot].add(stack.what(), Math.multiplyExact(stack.amount(), input.getMultiplier()));
        }
        return result;
    }
}
