package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.block.SimulationLanternBlock;
import com.atir.molecularmanipulator.registry.SimulationLanternContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class SimulationLanternGameTests {
    private static final ResourceLocation ID = MolecularManipulator.id("simulation_lantern");
    private static final ResourceLocation LEGACY_ID = MolecularManipulator.id("green_fire_lantern");

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 20)
    public static void oldItemStacksKeepTheirFlameAndSaveWithTheNewId(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        helper.assertTrue(ID.equals(BuiltInRegistries.ITEM.getKey(SimulationLanternContent.ITEM.get())), "Only the new item ID is canonical");
        helper.assertTrue(BuiltInRegistries.ITEM.get(LEGACY_ID) == SimulationLanternContent.ITEM.get(), "The old item ID aliases the renamed item");
        for (boolean lit : new boolean[] {false, true}) {
            var oldSave = (CompoundTag) SimulationLanternContent.stack(lit).save(registries);
            oldSave.putString("id", LEGACY_ID.toString());
            var restored = ItemStack.parse(registries, oldSave).orElseThrow();
            helper.assertTrue(SimulationLanternContent.isLantern(restored), "Old saved items remain usable lanterns");
            helper.assertTrue(SimulationLanternContent.isLit(restored) == lit, "The saved flame state is preserved");
            helper.assertTrue(ID.toString().equals(((CompoundTag) restored.save(registries)).getString("id")), "New saves use the new item ID");
        }
        var recipe = helper.getLevel().getRecipeManager().byKey(ID).orElseThrow();
        helper.assertTrue(recipe.value().getResultItem(registries).is(SimulationLanternContent.ITEM.get()), "The renamed recipe produces the renamed item");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(LEGACY_ID).isEmpty(), "There is no duplicate recipe under the retired ID");
        helper.succeed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 20)
    public static void oldBlockStatesAndEntitiesKeepTheirPropertiesAndSaveWithTheNewId(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        helper.assertTrue(ID.equals(BuiltInRegistries.BLOCK.getKey(SimulationLanternContent.LANTERN.get())), "Only the new block ID is canonical");
        helper.assertTrue(BuiltInRegistries.BLOCK.get(LEGACY_ID) == SimulationLanternContent.LANTERN.get(), "The old block ID aliases the renamed block");
        helper.assertTrue(ID.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(SimulationLanternContent.BLOCK_ENTITY.get())), "Only the new entity ID is canonical");
        helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.get(LEGACY_ID) == SimulationLanternContent.BLOCK_ENTITY.get(), "The old entity ID aliases the renamed type");
        for (boolean lit : new boolean[] {false, true}) {
            var state = SimulationLanternContent.LANTERN.get().defaultBlockState()
                    .setValue(SimulationLanternBlock.LIT, lit).setValue(SimulationLanternBlock.WATERLOGGED, !lit);
            var oldState = NbtUtils.writeBlockState(state);
            oldState.putString("Name", LEGACY_ID.toString());
            var restored = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), oldState);
            helper.assertTrue(restored.equals(state), "Old placed lanterns retain lit and waterlogged properties");
            helper.assertTrue(ID.toString().equals(NbtUtils.writeBlockState(restored).getString("Name")), "New block saves use the new ID");
            var oldEntity = new SimulationLanternBlockEntity(BlockPos.ZERO, state).saveWithFullMetadata(registries);
            oldEntity.putString("id", LEGACY_ID.toString());
            var entity = BlockEntity.loadStatic(BlockPos.ZERO, restored, oldEntity, registries);
            helper.assertTrue(entity instanceof SimulationLanternBlockEntity, "Old block entities deserialize as the renamed type");
            helper.assertTrue(ID.toString().equals(entity.saveWithFullMetadata(registries).getString("id")), "New entity saves use the new ID");
        }
        helper.succeed();
    }
}
