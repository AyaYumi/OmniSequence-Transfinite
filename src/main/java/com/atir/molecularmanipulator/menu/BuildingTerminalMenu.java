package com.atir.molecularmanipulator.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.guisync.GuiSync;
import appeng.menu.guisync.PacketWritable;
import appeng.menu.implementations.MenuTypeBuilder;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.StarComputationEngineCoreBlockEntity;
import com.atir.molecularmanipulator.blockentity.SingularityBlockEntity;
import com.atir.molecularmanipulator.item.BuildingTerminalItem;
import com.atir.molecularmanipulator.terminal.BuildingTerminal;
import com.atir.molecularmanipulator.terminal.BuildingTerminalLocator;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/** Server-synchronized directory; row indices are checked against the current terminal data on every action. */
public final class BuildingTerminalMenu extends AEBaseMenu {
    public static final MenuType<BuildingTerminalMenu> TYPE = MenuTypeBuilder
            .create(BuildingTerminalMenu::new, BuildingTerminalItem.Host.class)
            .withMenuTitle(host -> Component.translatable("gui.molecularmanipulator.building_terminal.title"))
            .buildUnregistered(MolecularManipulator.id("building_access_terminal"));
    private static final String OPEN = "building_terminal_open";
    private static final String REMOVE = "building_terminal_remove";

    public enum Status { READY, UNLOADED, WRONG_DIMENSION, MISSING, DENIED, INCOMPLETE }
    public record Entry(GlobalPos target, ResourceLocation block, Status status) {}
    public record Directory(List<Entry> entries) implements PacketWritable {
        public static final Directory EMPTY = new Directory(List.of());
        public Directory { entries = List.copyOf(entries); }
        public Directory(RegistryFriendlyByteBuf buffer) { this(readEntries(buffer)); }
        private static List<Entry> readEntries(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > BuildingTerminal.MAX_BINDINGS) throw new IllegalArgumentException("Invalid terminal directory size");
            var entries = new ArrayList<Entry>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new Entry(buffer.readGlobalPos(), buffer.readResourceLocation(), buffer.readEnum(Status.class)));
            }
            return entries;
        }
        @Override public void writeToPacket(RegistryFriendlyByteBuf buffer) {
            buffer.writeVarInt(entries.size());
            for (var entry : entries) {
                buffer.writeGlobalPos(entry.target());
                buffer.writeResourceLocation(entry.block());
                buffer.writeEnum(entry.status());
            }
        }
    }

    @GuiSync(0) public Directory directory = Directory.EMPTY;
    private final BuildingTerminalItem.Host host;

    private BuildingTerminalMenu(int id, Inventory inventory, BuildingTerminalItem.Host host) {
        super(TYPE, id, inventory, host);
        this.host = host;
        registerClientAction(OPEN, Integer.class, this::open);
        registerClientAction(REMOVE, Integer.class, this::remove);
        if (isServerSide()) refreshDirectory();
    }

    public void requestOpen(int index) { if (isClientSide()) sendClientAction(OPEN, index); }
    public void requestRemove(int index) { if (isClientSide()) sendClientAction(REMOVE, index); }

    private BuildingTerminal.Binding currentBinding(Integer index) {
        if (!isServerSide() || !host.isValid() || getPlayer().containerMenu != this
                || index == null || index < 0 || index >= directory.entries().size()) return null;
        var displayed = directory.entries().get(index);
        var binding = new BuildingTerminal.Binding(displayed.target(), displayed.block());
        return BuildingTerminal.isBound(host.getItemStack(), binding) ? binding : null;
    }

    private Status status(BuildingTerminal.Binding binding) {
        var player = getPlayer();
        var level = player.level();
        if (!level.dimension().equals(binding.target().dimension())) return Status.WRONG_DIMENSION;
        if (!level.hasChunkAt(binding.target().pos())) return Status.UNLOADED;
        var machine = BuildingTerminal.resolve(level, binding);
        if (machine == null) return Status.MISSING;
        if (!level.mayInteract(player, machine.getBlockPos())) return Status.DENIED;
        if (machine instanceof StarComputationEngineCoreBlockEntity core && !core.isFormed()) return Status.INCOMPLETE;
        return Status.READY;
    }

    private void open(Integer index) {
        var binding = currentBinding(index);
        if (binding == null || !(getPlayer() instanceof ServerPlayer player)) return;
        if (status(binding) != Status.READY) {
            player.displayClientMessage(Component.translatable("message.molecularmanipulator.building_terminal.unavailable"), true);
            refreshDirectory();
            broadcastChanges();
            return;
        }
        var machine = BuildingTerminal.resolve(player.level(), binding);
        if (machine == null || host.getPlayerInventorySlot() == null) return;
        // Retain the original GUI entry's ownership/recovery behavior, not just its menu type.
        if (machine instanceof StarComputationEngineCoreBlockEntity core) core.bindOwner(player);
        if (machine instanceof SingularityBlockEntity singularity && singularity.operation() == SingularityBlockEntity.Operation.IDLE
                && singularity.motion().hasPortable()) singularity.setRecoveryOwner(player);
        MenuOpener.open(BuildingTerminal.menuType(machine), player,
                new BuildingTerminalLocator(binding.target(), machine.getBlockState(), host.getPlayerInventorySlot()));
    }

    private void remove(Integer index) {
        var binding = currentBinding(index);
        if (binding == null) return;
        var bindings = new ArrayList<>(BuildingTerminal.bindings(host.getItemStack()));
        bindings.remove(binding);
        BuildingTerminal.saveBindings(host.getItemStack(), bindings);
        getPlayer().getInventory().setChanged();
        broadcastChanges();
    }

    private void refreshDirectory() {
        directory = new Directory(BuildingTerminal.bindings(host.getItemStack()).stream()
                .map(binding -> new Entry(binding.target(), binding.block(), status(binding))).toList());
    }

    @Override public void broadcastChanges() {
        if (isServerSide() && host.isValid()) refreshDirectory();
        super.broadcastChanges();
    }
}
