package com.atir.molecularmanipulator.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.TaixuBlockEntity;
import com.atir.molecularmanipulator.blockentity.TaixuStructure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class TaixuMenu extends AEBaseMenu {
    public static final MenuType<TaixuMenu> TYPE = MenuTypeBuilder.create(TaixuMenu::new, TaixuBlockEntity.class)
            .buildUnregistered(MolecularManipulator.id("taixu_creation_nexus"));
    @GuiSync(10) public boolean formed;
    @GuiSync(11) public boolean networkOnline;
    @GuiSync(12) public int correct;
    @GuiSync(13) public int missing;
    @GuiSync(14) public int conflicts;
    @GuiSync(15) public int unloaded;
    @GuiSync(16) public TaixuBlockEntity.Operation operation = TaixuBlockEntity.Operation.IDLE;
    @GuiSync(17) public TaixuBlockEntity.Status status = TaixuBlockEntity.Status.IDLE;
    @GuiSync(18) public boolean paused;
    @GuiSync(19) public int progress;
    @GuiSync(20) public int operationTotal;
    @GuiSync(21) public String needed = "";
    @GuiSync(22) public String problem = "";
    @GuiSync(23) public int neededMaterial = -1;
    @GuiSync(24) public int motionMode;
    @GuiSync(25) public String motionMessage = "off";
    @GuiSync(26) public boolean movingBodies;
    @GuiSync(27) public int structureVersion = TaixuStructure.VERSION;
    @GuiSync(28) public boolean embedRequested;
    private final TaixuBlockEntity machine;
    private long dismantleArmedAt = Long.MIN_VALUE;
    private long lastRefresh = Long.MIN_VALUE;
    private TaixuMenu(int id, Inventory inventory, TaixuBlockEntity machine) {
        super(TYPE, id, inventory, machine); this.machine = machine;
        createPlayerInventorySlots(inventory);
        var slots = getSlots(SlotSemantics.PLAYER_INVENTORY);
        for (int i = 0; i < slots.size(); i++) { slots.get(i).x = 85 + i % 9 * 18; slots.get(i).y = 282 + i / 9 * 18; }
        slots = getSlots(SlotSemantics.PLAYER_HOTBAR);
        for (int i = 0; i < slots.size(); i++) { slots.get(i).x = 85 + i * 18; slots.get(i).y = 340; }
        addSlot(new AppEngSlot(machine.getInternalInventory(), 0) {
            { x = 22; y = 282; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        }, SlotSemantics.MACHINE_OUTPUT);
        registerClientAction("build", () -> withPlayer(machine::startBuild));
        registerClientAction("pause", () -> withPlayer(machine::togglePause));
        registerClientAction("cancel", () -> withPlayer(machine::cancel));
        registerClientAction("motion", () -> withPlayer(player -> machine.motion().toggle(player)));
        registerClientAction("dock", () -> withPlayer(player -> machine.motion().dock(player, false)));
        registerClientAction("embed", () -> withPlayer(machine::requestEmbedding));
        registerClientAction("upgrade", () -> withPlayer(machine::requestSuspendedUpgrade));
        registerClientAction("refresh", () -> withPlayer(player -> {
            long now = machine.getLevel().getGameTime();
            if (lastRefresh == Long.MIN_VALUE || now - lastRefresh >= 20) { machine.requestInspection(player); lastRefresh = now; }
        }));
        registerClientAction("dismantle", () -> withPlayer(player -> {
            long now = machine.getLevel().getGameTime();
            if (dismantleArmedAt != Long.MIN_VALUE && now - dismantleArmedAt >= 5 && now - dismantleArmedAt <= 60) {
                machine.startDismantle(player); dismantleArmedAt = Long.MIN_VALUE;
            } else dismantleArmedAt = now;
        }));
    }
    private void withPlayer(java.util.function.Consumer<ServerPlayer> action) {
        if (isServerSide() && getPlayer() instanceof ServerPlayer player && machine.canManage(player)) action.accept(player);
    }
    public void request(String action) { if (isClientSide()) sendClientAction(action); }
    public TaixuBlockEntity getMachine() { return machine; }
    @Override public void broadcastChanges() {
        if (isServerSide()) {
            var scan = machine.inspection();
            formed = machine.formed(); networkOnline = machine.networkOnline();
            structureVersion = machine.structureVersion(); embedRequested = machine.embedRequested();
            motionMode = machine.motion().mode(); motionMessage = machine.motion().message(); movingBodies = machine.motion().hasBodies();
            correct = scan.correct(); missing = scan.missing(); conflicts = scan.conflicts(); unloaded = scan.unloaded();
            operation = machine.operation(); status = machine.status(); paused = machine.paused();
            progress = machine.progress(); operationTotal = machine.operationTotal();
            needed = Arrays.stream(TaixuStructure.Type.values()).map(t -> Integer.toString(scan.needed().getOrDefault(t, 0)))
                    .collect(Collectors.joining(","));
            var pos = machine.problem() == null ? scan.problem() : machine.problem();
            problem = pos == null ? "" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            neededMaterial = machine.neededMaterial() == null ? -1 : machine.neededMaterial().ordinal();
        }
        super.broadcastChanges();
    }
}
