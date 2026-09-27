package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.registry.ModContent;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class Forge206GameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 200)
    public static void machineImportsKeepJsonMappingsAcrossReload(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        var apply = RecipeManager.class.getDeclaredMethod("apply", Map.class, ResourceManager.class, ProfilerFiller.class);
        apply.setAccessible(true);
        try {
            var json = new LinkedHashMap<ResourceLocation, JsonElement>();
            json.put(new ResourceLocation("verification", "furnace_import"), JsonParser.parseString("""
                    {"type":"molecularmanipulator:matter_machine_import","machine":"minecraft:furnace",
                     "serializers":["minecraft:smelting"],"inputs":{"items":["/ingredient"]},
                     "outputs":{"items":["/result"]},"research":{"title":"Verification","ingredients":[]}}
                    """));
            var sourceId = new ResourceLocation("verification", "smelting");
            for (int count : new int[] {2, 3}) {
                json.put(sourceId, JsonParser.parseString("""
                        {"type":"minecraft:smelting","ingredient":{"item":"minecraft:iron_ingot"},
                         "result":{"item":"minecraft:diamond","count":%d},"experience":0,"cookingtime":20}
                        """.formatted(count)));
                apply.invoke(manager, json, level.getServer().getResourceManager(), InactiveProfiler.INSTANCE);
                var index = MatterRecipeIndex.get(level);
                var imported = index.fabrication();
                helper.assertTrue(imported.size() == 1, "Explicit Forge JSON mapping must import one recipe");
                helper.assertTrue(imported.get(0).results().get(0).getCount() == count,
                        "Reload must preserve the source serializer's complete output amount");
                helper.assertTrue(index.candidates(Map.of(AEItemKey.of(Items.DIAMOND), (long) count)).size() == 1,
                        "Imported outputs must participate in fabrication matching");
                helper.assertTrue(index.research().size() == 1 && index.research().get(0).unlocks().contains(imported.get(0).id()),
                        "Import declaration must generate research ownership");
            }
            System.out.println("FORGE_MACHINE_IMPORT_PASS: JSON field mappings, generated research, exact outputs and reload");
            helper.succeed();
        } finally { manager.replaceRecipes(original); }
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 200)
    public static void autoCrafterRemovalKeepsOneCopyAndLongOutputs(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var state = ModContent.MOLECULAR_AUTO_CRAFTER.get().defaultBlockState();
        var machine = new MolecularAutoCrafterBlockEntity(pos, state);
        machine.setLevel(level);
        var recipe = (CraftingRecipe) level.getRecipeManager().byKey(new ResourceLocation("oak_planks")).orElseThrow();
        var inputs = new ItemStack[9];
        Arrays.fill(inputs, ItemStack.EMPTY);
        inputs[0] = new ItemStack(Items.OAK_LOG);
        var pattern = PatternDetailsHelper.encodeCraftingPattern(recipe, inputs, new ItemStack(Items.OAK_PLANKS, 4), false, false);
        machine.getAutoCraftPatternInventory().setItemDirect(0, pattern);
        machine.getAutoCrafter().setOutputLimit(0, Long.MAX_VALUE);
        machine.getAutoCrafter().setProtection(0, 0, 3_000_000_000L);
        machine.getAutoCrafter().setEnabled(0, true);
        var saved = machine.saveWithFullMetadata();
        var outputs = new ListTag();
        outputs.add(GenericStack.writeTag(new GenericStack(AEItemKey.of(Items.DIAMOND), Long.MAX_VALUE)));
        saved.put("outputs", outputs);
        machine.loadTag(saved);
        var drops = new ArrayList<>(Block.getDrops(state, level, pos, machine));
        machine.addAdditionalDrops(level, pos, drops);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(ModContent.MOLECULAR_AUTO_CRAFTER_ITEM.get()),
                "Recovery must yield one machine rather than a plain duplicate");
        var restored = new MolecularAutoCrafterBlockEntity(pos, state);
        restored.setLevel(level);
        restored.loadTag(BlockItem.getBlockEntityData(drops.get(0)));
        var view = restored.getAutoCrafter().getView(0);
        helper.assertTrue(view.enabled() && view.outputLimit() == Long.MAX_VALUE
                        && view.protections()[0] == 3_000_000_000L,
                "Retained pattern settings must survive compression and Forge NBT loading");
        var restoredOutputs = restored.saveWithFullMetadata().getList("outputs", Tag.TAG_COMPOUND);
        helper.assertTrue(GenericStack.readTag(restoredOutputs.getCompound(0)).amount() == Long.MAX_VALUE,
                "Buffered outputs must remain exact after recovery");
        machine.clearContent();
        helper.assertTrue(!machine.hasRemovalRecovery(), "Removal must clear the original owner's contents");
        System.out.println("FORGE_AUTO_CRAFTER_RECOVERY_PASS: one drop, pattern settings and long output counts");
        helper.succeed();
    }
}
