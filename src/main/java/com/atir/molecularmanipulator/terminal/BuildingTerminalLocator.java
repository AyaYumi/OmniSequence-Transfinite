package com.atir.molecularmanipulator.terminal;

import appeng.menu.locator.MenuLocator;
import appeng.menu.locator.MenuLocators;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Server: the actual controller. Client: an unticked menu host, populated only by normal menu sync. */
public record BuildingTerminalLocator(GlobalPos target, BlockState state, int terminalSlot) implements MenuLocator {
    public static void register() {
        MenuLocators.register(BuildingTerminalLocator.class, BuildingTerminalLocator::write, BuildingTerminalLocator::read);
    }

    private static void write(BuildingTerminalLocator locator, FriendlyByteBuf buffer) {
        buffer.writeGlobalPos(locator.target());
        buffer.writeVarInt(Block.getId(locator.state()));
        buffer.writeVarInt(locator.terminalSlot());
    }

    private static BuildingTerminalLocator read(FriendlyByteBuf buffer) {
        return new BuildingTerminalLocator(buffer.readGlobalPos(), Block.stateById(buffer.readVarInt()), buffer.readVarInt());
    }

    public BuildingTerminal.Binding binding() {
        return new BuildingTerminal.Binding(target, BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public boolean isValid(Player player) {
        if (!player.level().dimension().equals(target.dimension())
                || terminalSlot < 0 || terminalSlot >= player.getInventory().getContainerSize()
                || !BuildingTerminal.isBound(player.getInventory().getItem(terminalSlot), binding())
                || !player.level().mayInteract(player, target.pos())) return false;
        return BuildingTerminal.resolve(player.level(), binding()) != null;
    }

    @Override
    public <T> @Nullable T locate(Player player, Class<T> hostInterface) {
        if (!player.level().dimension().equals(target.dimension())) return null;
        BlockEntity machine;
        if (!player.level().isClientSide()) {
            if (!isValid(player)) return null;
            machine = BuildingTerminal.resolve(player.level(), binding());
        } else {
            // Never install this host into a client chunk or tick it. The server still owns every slot,
            // action and telemetry value; the blockstate supplies only the immutable GUI/port variant.
            // This also works when the actual building is beyond the client's render distance.
            if (!(state.getBlock() instanceof EntityBlock block)) return null;
            machine = block.newBlockEntity(target.pos(), state);
            if (machine == null || BuildingTerminal.menuType(machine) == null) return null;
            machine.setLevel(player.level());
        }
        return hostInterface.isInstance(machine) ? hostInterface.cast(machine) : null;
    }
}
