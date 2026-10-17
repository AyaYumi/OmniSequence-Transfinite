package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import appeng.me.helpers.MachineSource;
import com.atir.molecularmanipulator.crafting.*;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.research.MatterResearchApi;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.*;

/** Real AE job submission and server ticks, using the installed addon's recipe and resource key. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MatterLightningCatalystCpuGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 500, batch = "lightningCpu")
    public static void cpuOrderWaitsForRetainedCatalyst(GameTestHelper helper) {
        if (!ModList.get().isLoaded("ae2lt")) { helper.succeed(); return; }
        var level = helper.getLevel();
        var holder = MatterRecipeBridge.importRecipes(level, level.getRecipeManager().getRecipes()).stream()
                .filter(value -> value.machineKey().equals("lightning_crystal_catalyzer")
                        && BuiltInRegistries.ITEM.getKey(value.output().getItem()).toString().equals("ae2lt:overload_crystal"))
                .findFirst().orElseThrow().holder();
        var origin = new BlockPos(1800, 80, 160);
        var chunks = new HashSet<ChunkPos>();
        for (var part : MatterFabricationStructure.parts()) {
            var pos = MatterFabricationStructure.worldPos(origin, Direction.NORTH, part);
            force(helper, chunks, pos);
            level.setBlock(pos, MatterFabricationStructure.isController(part)
                    ? ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                    : MatterFabricationStructure.partState(part.type()), 3);
        }
        var controller = (MatterFabricationBlockEntity) level.getBlockEntity(origin);
        var bay = MatterFabricationStructure.worldPos(origin, Direction.NORTH, MatterFabricationStructure.patternAssemblyBays().get(0));
        level.setBlock(bay, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState(), 3);
        var assembly = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(bay);
        var powerPos = origin.offset(30, 0, 0);
        var cpuPos = powerPos.east();
        force(helper, chunks, powerPos); force(helper, chunks, cpuPos);
        level.setBlock(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        level.setBlock(cpuPos, AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState(), 3);
        var power = (CreativeEnergyCellBlockEntity) level.getBlockEntity(powerPos);
        var cpu = (CraftingBlockEntity) level.getBlockEntity(cpuPos);
        var returned = new LinkedHashMap<AEKey, Long>();
        MEStorage storage = new MEStorage() {
            @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                if (mode == Actionable.MODULATE) returned.merge(key, amount, Math::addExact);
                return amount;
            }
            @Override public Component getDescription() { return Component.literal("Catalyst CPU verification storage"); }
        };
        IStorageProvider provider = mounts -> mounts.mount(storage, 0);
        var unit = new LinkedHashMap<AEKey, Long>();
        if (!holder.value().fluidInput().isEmpty()) unit.put(AEFluidKey.of(holder.value().fluidInput()), (long) holder.value().fluidInput().getAmount());
        holder.value().aeInputs().forEach(value -> unit.merge(value.what(), value.amount(), Math::addExact));
        var pausedProgress = new int[1];
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(controller.getMainNode().getNode() != null
                        && power.getMainNode().getNode() != null, "Controller and power nodes must initialize"))
                .thenExecute(() -> {
                    controller.refreshStructure(); assembly.setControllerPos(origin);
                    GridHelper.createConnection(controller.getMainNode().getNode(), power.getMainNode().getNode());
                });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(assembly.isOperational() && cpu.getCluster() != null && cpu.getCluster().isActive(), "Real well and CPU must be online");
            for (var research : MatterRecipeIndex.get(level).researchFor(holder.id())) {
                MatterResearchApi.setCompletionCount(controller, research.id().toString(), 1);
            }
            assembly.getLogic().getPatternInv().setItemDirect(0, MatterFabricationPatternEncoding.encode(holder.value(), List.of()));
            assembly.getLogic().updatePatterns();
            controller.getMainNode().getGrid().getStorageService().addGlobalStorageProvider(provider);
        });
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(controller.getSharedCatalysts().isEmpty(), "The well must begin with no catalysts in any assembly");
            var pattern = assembly.getLogic().getAvailablePatterns().get(0);
            var output = pattern.getOutputs()[0];
            var plan = new CraftingPlan(output, 100, false, false, new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, 1L));
            helper.assertTrue(controller.getMainNode().getGrid().getCraftingService()
                    .submitJob(plan, null, cpu.getCluster(), false, new MachineSource(controller)).successful(), "Real AE order must submit");
            // Supply the materials already reserved by the submitted job; the live CPU owns dispatch.
            var inventory = cpu.getCluster().craftingLogic.getInventory();
            unit.forEach((key, amount) -> inventory.insert(key, amount, Actionable.MODULATE));
        });
        helper.runAfterDelay(90, () -> {
            var inventory = cpu.getCluster().craftingLogic.getInventory();
            helper.assertTrue(cpu.getCluster().isBusy() && MatterFabricationPatternLogic.batchInputs(new KeyCounter[]{inventory.list}).equals(unit)
                            && !assembly.getBuffer().hasContents() && returned.isEmpty(),
                    "A live CPU must keep every material and produce nothing when the catalyst was never installed");
            var catalyst = holder.value().catalysts().get(0);
            assembly.getCatalystInventory().setItemDirect(0, catalyst.ingredient().getItems()[0].copyWithCount(catalyst.count()));
        });
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(controller.getProcessingState() == MatterFabricationBlockEntity.ProcessingState.RUNNING
                            && controller.getProgress() > 0 && returned.isEmpty(), "Installing the correct catalyst must let the live CPU dispatch and start work");
            pausedProgress[0] = controller.getProgress();
            assembly.getCatalystInventory().clear();
        });
        helper.runAfterDelay(160, () -> {
            helper.assertTrue(controller.getProcessingState() == MatterFabricationBlockEntity.ProcessingState.WAITING_CATALYST
                            && controller.getProgress() == pausedProgress[0]
                            && MatterFabricationPatternLogic.batchInputs(assembly.getBuffer().contents(false)).equals(unit) && returned.isEmpty(),
                    "Real server ticks must pause admitted work after catalyst removal, preserving progress and all materials");
            var catalyst = holder.value().catalysts().get(0);
            assembly.getCatalystInventory().setItemDirect(0, catalyst.ingredient().getItems()[0].copyWithCount(catalyst.count()));
        });
        helper.runAfterDelay(400, () -> {
            helper.assertTrue(returned.equals(MatterRecipeIndex.outputAmounts(holder.value())) && !cpu.getCluster().isBusy()
                            && !assembly.getBuffer().hasContents() && assembly.getCatalystInventory().getStackInSlot(0).getCount() == 1,
                    "Restocking must finish exactly one AE order while retaining its original catalyst: outputs=" + returned);
            controller.getMainNode().getGrid().getStorageService().removeGlobalStorageProvider(provider);
            assembly.clearContent();
            for (var chunk : chunks) level.setChunkForced(chunk.x, chunk.z, false);
            System.out.println("MATTER_LIGHTNING_CATALYST_CPU_PASS: never-installed catalyst blocks real AE order; removal pauses; restock completes exactly once");
            helper.succeed();
        });
    }

    private static void force(GameTestHelper helper, Set<ChunkPos> chunks, BlockPos pos) {
        var chunk = new ChunkPos(pos);
        if (chunks.add(chunk)) helper.getLevel().setChunkForced(chunk.x, chunk.z, true);
        helper.getLevel().getChunkAt(pos);
    }
}
