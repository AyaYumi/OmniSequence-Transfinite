package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.*;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import com.atir.molecularmanipulator.api.crafting.*;
import com.atir.molecularmanipulator.crafting.MolecularScaledPattern;
import com.atir.molecularmanipulator.crafting.MolecularOmniBatchDelivery;
import com.atir.molecularmanipulator.crafting.OmniSmartDoublingPlanner;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.verification.VerificationAEKey;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MatterCpuBatchGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 400)
    public static void nativeBatchOwnershipAndFallback(GameTestHelper helper) {
        var level = helper.getLevel();
        var origin = new BlockPos(1200, 80, 160);
        for (int x = 72; x <= 78; x++) for (int z = 6; z <= 16; z++) {
            level.setChunkForced(x, z, true); level.getChunk(x, z);
        }
        for (var part : MatterFabricationStructure.parts()) {
            var pos = MatterFabricationStructure.worldPos(origin, Direction.NORTH, part);
            if (level.getBlockEntity(pos) instanceof AEBaseBlockEntity old) old.clearContent();
            if (!MatterFabricationStructure.isController(part)) level.setBlock(pos, MatterFabricationStructure.partState(part.type()), 3);
        }
        level.setBlock(origin, ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH), 3);
        var controller = (MatterFabricationBlockEntity) level.getBlockEntity(origin);
        var bay = MatterFabricationStructure.worldPos(origin, Direction.NORTH, MatterFabricationStructure.patternAssemblyBays().getFirst());
        level.setBlock(bay, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState(), 3);
        var assembly = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(bay);
        var powerPos = origin.offset(30, 0, 0);
        level.setBlock(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var power = (CreativeEnergyCellBlockEntity) level.getBlockEntity(powerPos);
        helper.runAfterDelay(5, () -> {
            controller.refreshStructure(); assembly.setControllerPos(origin);
            GridHelper.createConnection(controller.getMainNode().getNode(), power.getMainNode().getNode());
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(assembly.isOperational(), "Real fabrication well must be online");
            var unit = new LinkedHashMap<AEKey, Long>();
            unit.put(AEItemKey.of(Items.DIAMOND), 2L);
            unit.put(new VerificationAEKey("gas", "hot"), 3_000_000_000L);
            unit.put(new VerificationAEKey("gas", "cold"), 7L);
            unit.put(AEFluidKey.of(Fluids.LAVA), 100L);
            unit.put(AEFluidKey.of(Fluids.WATER), 250L);
            var encoded = PatternDetailsHelper.encodeProcessingPattern(stacks(unit),
                    List.of(new GenericStack(AEItemKey.of(Items.EMERALD), 1)));
            assembly.getLogic().getPatternInv().setItemDirect(0, encoded);
            assembly.getLogic().updatePatterns();
        });
        helper.runAfterDelay(50, () -> {
            var unit = unitInputs();
            var logic = assembly.getLogic();
            var pattern = logic.getAvailablePatterns().getFirst();
            var ordinary = counters(unit);
            helper.assertTrue(logic.pushPattern(pattern, ordinary) && ordinary[0].isEmpty(), "Ordinary CPU delivery must transfer once");
            assembly.getBuffer().process(controller); assembly.getBuffer().process(controller);
            helper.assertTrue(amounts(assembly.getBuffer().contents(true)).equals(Map.of(AEItemKey.of(Items.EMERALD), 1L)), "Native queue must produce exactly one output");
            assembly.getBuffer().clear();
            var plan = new CraftingPlan(new GenericStack(AEItemKey.of(Items.EMERALD), 1026), 100,
                    false, false, new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, 1026L));
            helper.assertTrue(OmniSmartDoublingPlanner.rewriteForSubmission(plan, ignored -> List.of(logic)) == plan,
                    "Batch provider must bypass the built-in smart doubling rewrite");
            verifyOmniScaled(helper, assembly, pattern, unit);
            var limit = logic.prepareCountedBatch(pattern, unit, Long.MAX_VALUE);
            helper.assertTrue(limit != null && limit.maxCrafts() == Long.MAX_VALUE / 3_000_000_000L, "Long materials must bound native capacity");
            var malformed = new LinkedHashMap<>(unit); malformed.put(AEItemKey.of(Items.DIAMOND), 1L);
            helper.assertTrue(logic.prepareCountedBatch(pattern, malformed, 1026) == null, "Partial input vectors must never be admitted");
            var stale = logic.prepareCountedBatch(pattern, unit, 8);
            logic.getPatternInv().setItemDirect(0, net.minecraft.world.item.ItemStack.EMPTY); logic.updatePatterns();
            var retained = counters(unit);
            helper.assertTrue(!stale.commitPrototype(retained, 8) && amounts(retained).equals(unit), "Removed patterns must reject without consuming the prototype");
            logic.getPatternInv().setItemDirect(0, pattern.getDefinition().toStack()); logic.updatePatterns();
            if (ModList.get().isLoaded("neoecoae") || ModList.get().isLoaded("thunderbolt") || ModList.get().isLoaded("data_energistics"))
                MatterNativeCpuChecks.run(helper, assembly, pattern, unit);
            helper.runAfterDelay(6, () -> {
                System.out.println("MATTER_CPU_BATCH_PASS: real provider APIs, ownership, scaling, persistence and optional loading");
                helper.succeed();
            });
        });
    }

    private static void verifyOmniScaled(GameTestHelper helper, MatterFabricationPatternAssemblyBlockEntity assembly,
            IPatternDetails pattern, Map<AEKey, Long> unit) {
        var scaled = new MolecularScaledPattern(pattern, 8);
        var one = MatterPatternBuffer.scaled(unit, 8);
        var probe = new OmniBatchProbe(scaled, one.entrySet().stream()
                .map(e -> new OmniBatchProbe.Input(0, e.getKey(), e.getValue())).toList(), 3);
        var batch = assembly.getLogic().prepareOmniBatch(probe);
        helper.assertTrue(batch != null && batch.maxCrafts() == 3, "Scaled pattern must retain native operation count");
        var total = MatterPatternBuffer.scaled(unit, 24);
        var delivery = new MolecularOmniBatchDelivery(new OmniBatchRequest(UUID.randomUUID(), null, scaled, 3,
                total.entrySet().stream().map(e -> new OmniBatchRequest.Input(0, e.getKey(), e.getValue())).toList(),
                List.of(new GenericStack(AEItemKey.of(Items.EMERALD), 24))));
        batch.commit(delivery); delivery.seal();
        helper.assertTrue(delivery.accepted(), "Scaled batches must preserve all 24 crafts' materials");
        assertQueue(helper, assembly, total, "Omni scaled batch");
    }

    static Map<AEKey, Long> unitInputs() {
        var unit = new LinkedHashMap<AEKey, Long>();
        unit.put(AEItemKey.of(Items.DIAMOND), 2L);
        unit.put(new VerificationAEKey("gas", "hot"), 3_000_000_000L);
        unit.put(new VerificationAEKey("gas", "cold"), 7L);
        unit.put(AEFluidKey.of(Fluids.LAVA), 100L); unit.put(AEFluidKey.of(Fluids.WATER), 250L);
        return unit;
    }

    static void assertQueue(GameTestHelper helper, MatterFabricationPatternAssemblyBlockEntity assembly,
            Map<AEKey, Long> expected, String context) {
        helper.assertTrue(amounts(assembly.getBuffer().contents(false)).equals(expected), context + " must preserve full material vector");
        var tag = new CompoundTag();
        assembly.getBuffer().save(tag, helper.getLevel().registryAccess());
        assembly.getBuffer().load(tag, helper.getLevel().registryAccess());
        helper.assertTrue(amounts(assembly.getBuffer().contents(false)).equals(expected), context + " must survive NBT reload");
        assembly.getBuffer().refundQueuedInputs();
        helper.assertTrue(amounts(assembly.getBuffer().contents(false)).equals(expected) && assembly.getBuffer().queuedPatterns() == 0,
                context + " must refund every input exactly once");
        assembly.getBuffer().clear();
    }

    static KeyCounter[] counters(Map<AEKey, Long> values) { var c = new KeyCounter(); values.forEach(c::add); return new KeyCounter[]{c}; }
    static List<GenericStack> stacks(Map<AEKey, Long> values) { return values.entrySet().stream().map(e -> new GenericStack(e.getKey(), e.getValue())).toList(); }
    static Map<AEKey, Long> amounts(List<GenericStack> values) { return MatterFabricationPatternLogic.batchInputs(values); }
    static Map<AEKey, Long> amounts(KeyCounter[] values) { return MatterFabricationPatternLogic.batchInputs(values); }
}
