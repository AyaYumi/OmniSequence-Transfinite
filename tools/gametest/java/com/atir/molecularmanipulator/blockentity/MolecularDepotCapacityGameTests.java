package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.core.definitions.AEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MolecularDepotCapacityGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 300)
    public static void actualDepotSimulationIsBoundedExactAndNonMutating(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = new BlockPos(9920, 120, 9920);
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        level.getChunkAt(pos);
        var block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(
                "data_energistics", "digital_storage_depot"));
        helper.assertTrue(block != Blocks.AIR, "Real optional digital depot is registered");
        level.setBlock(pos, block.defaultBlockState(), 3);
        var depot = level.getBlockEntity(pos);
        try {
            var field = depot.getClass().getDeclaredField("networkStorage");
            field.setAccessible(true);
            var storage = (MEStorage) field.get(depot);
            var inventory = (InternalInventory) depot.getClass().getMethod("getStorageInventory").invoke(depot);
            var source = IActionSource.empty();
            var key = AEItemKey.of(Items.OAK_PLANKS);
            // The unpatched implementation would loop for Long.MAX_VALUE / 64
            // iterations while repeatedly simulating the same empty slot.
            long room = storage.insert(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
            helper.assertTrue(room == inventory.size() * 64L, "Empty physical depot capacity is exact: " + room);
            for (int slot = 0; slot < inventory.size(); slot++) {
                helper.assertTrue(inventory.getStackInSlot(slot).isEmpty(), "Simulation cannot create stored items");
            }
            inventory.setItemDirect(0, new ItemStack(Items.OAK_PLANKS, 60));
            inventory.setItemDirect(1, new ItemStack(Items.STONE, 64));
            room = storage.insert(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
            helper.assertTrue(room == (inventory.size() - 2) * 64L + 4, "Count each matching/empty slot once");
            helper.assertTrue(storage.insert(key, room, Actionable.MODULATE, source) == room,
                    "Real unchanged insertion accepts exactly the simulated amount");
            helper.assertTrue(storage.insert(key, 1, Actionable.SIMULATE, source) == 0, "Full depot rejects further output");
            for (int slot = 0; slot < inventory.size(); slot++) inventory.setItemDirect(slot, ItemStack.EMPTY);
            var upgrades = ((IUpgradeableObject) depot).getUpgrades();
            upgrades.setItemDirect(0, AEItems.CAPACITY_CARD.stack());
            int perSlot = (int) depot.getClass().getMethod("getItemCapacity", ItemStack.class)
                    .invoke(depot, new ItemStack(Items.OAK_PLANKS));
            room = storage.insert(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
            helper.assertTrue(perSlot > 64 && room == (long) perSlot * inventory.size(),
                    "Real capacity-card slot limits remain supported: " + perSlot + " / " + room);
            helper.assertTrue(storage.insert(key, room, Actionable.MODULATE, source) == room,
                    "Upgraded physical insertion agrees with bounded simulation");
            com.mojang.logging.LogUtils.getLogger().info("MOLECULAR_DEPOT_CAPACITY_PASS slots={} upgradedSlot={} room={}",
                    inventory.size(), perSlot, room);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Real depot verification API", exception);
        } finally {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        helper.succeed();
    }
}
