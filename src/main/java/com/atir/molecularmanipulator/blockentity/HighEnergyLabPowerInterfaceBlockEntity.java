package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.inventories.InternalInventory;
import appeng.blockentity.grid.AENetworkedPoweredBlockEntity;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.registry.HighEnergyLabContent;
import com.atir.molecularmanipulator.block.HighEnergyLabDecorationBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** AE2 owns cable lifecycle, FE conversion and the persistent, private energy buffer. */
public final class HighEnergyLabPowerInterfaceBlockEntity extends AENetworkedPoweredBlockEntity {
    public static final double POWER_PER_TICK = 16;
    public static final double BUFFER_CAPACITY = 320;
    private boolean powered;
    private boolean decorationsSynced;

    public HighEnergyLabPowerInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(HighEnergyLabContent.POWER_INTERFACE_BE.get(), pos, state);
        getMainNode().setFlags().setIdlePowerUsage(0);
        setInternalMaxPower(BUFFER_CAPACITY);
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return getBlockState().getBlock().asItem();
    }

    @Override
    public InternalInventory getInternalInventory() {
        return InternalInventory.empty();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inventory, int slot) {}

    public boolean powered() {
        return powered;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) return;
        double before = getInternalCurrentPower();
        var grid = getMainNode().getGrid();
        if (grid != null) {
            double demand = getInternalMaxPower() - getInternalCurrentPower();
            double received = grid.getEnergyService().extractAEPower(demand, Actionable.MODULATE, PowerMultiplier.ONE);
            injectAEPower(received, Actionable.MODULATE);
        }
        // A partial payment stays in the buffer until a whole operating tick can be paid.
        boolean next = extractAEPower(POWER_PER_TICK, Actionable.SIMULATE, PowerMultiplier.CONFIG)
                >= POWER_PER_TICK;
        if (next) extractAEPower(POWER_PER_TICK, Actionable.MODULATE, PowerMultiplier.CONFIG);
        if (!decorationsSynced || powered != next) {
            HighEnergyLabDecorationBlock.setPowered(level, worldPosition,
                    getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING), next);
            decorationsSynced = true;
        }
        if (powered != next || getBlockState().getValue(BlockStateProperties.LIT) != next) {
            powered = next;
            markForUpdate();
        }
        if (next || before > 0 || getInternalCurrentPower() > 0) saveChanges();
    }
}
