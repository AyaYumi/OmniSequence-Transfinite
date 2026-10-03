package com.atir.molecularmanipulator.menu;

import appeng.client.gui.Icon;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.blockentity.SingularityBlockEntity;
import com.atir.molecularmanipulator.blockentity.SingularityStructure;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class SingularityMenu extends AEBaseMenu {
    public static final SlotSemantic QUANTUM_INPUT = SlotSemantics.register(
            "molecularmanipulator:SINGULARITY_QUANTUM_INPUT", false);
    public static final SlotSemantic DUPLICATION_SAMPLE = SlotSemantics.register(
            "molecularmanipulator:SINGULARITY_DUPLICATION_SAMPLE", false);
    public static final SlotSemantic BLACK_HOLE_INPUT = SlotSemantics.register(
            "molecularmanipulator:SINGULARITY_BLACK_HOLE_INPUT", false);
    public static final int QUANTUM_X = 26, QUANTUM_Y = 190;
    public static final int SAMPLE_X = 22, BLACK_HOLE_X = 166, DUPLICATION_Y = 150;
    public static final MenuType<SingularityMenu> TYPE = MenuTypeBuilder.create(SingularityMenu::new, SingularityBlockEntity.class)
            .buildUnregistered(MolecularManipulator.id("event_horizon_singularity_hub"));
    @GuiSync(10) public boolean formed;
    @GuiSync(11) public boolean networkOnline;
    @GuiSync(12) public int correct;
    @GuiSync(13) public int missing;
    @GuiSync(14) public int conflicts;
    @GuiSync(15) public int unloaded;
    @GuiSync(16) public SingularityBlockEntity.Operation operation = SingularityBlockEntity.Operation.IDLE;
    @GuiSync(17) public SingularityBlockEntity.Status status = SingularityBlockEntity.Status.IDLE;
    @GuiSync(18) public boolean paused;
    @GuiSync(19) public int progress;
    @GuiSync(20) public int operationTotal;
    @GuiSync(21) public String needed = "";
    @GuiSync(22) public String problem = "";
    @GuiSync(23) public int neededMaterial = -1;
    @GuiSync(24) public int motionMode;
    @GuiSync(25) public String motionMessage = "off";
    @GuiSync(26) public boolean movingBodies;
    @GuiSync(27) public int structureVersion = SingularityStructure.VERSION;
    @GuiSync(28) public boolean embedRequested;
    @GuiSync(29) public long quantumFrequency;
    @GuiSync(30) public MolecularCenterBlockEntity.QuantumLinkState quantumLinkState =
            MolecularCenterBlockEntity.QuantumLinkState.EMPTY;
    @GuiSync(31) public boolean collectionActive;
    @GuiSync(32) public boolean collectionStarting;
    @GuiSync(33) public String collectionItems = "";
    @GuiSync(34) public int collectionBatchSize = 1000;
    @GuiSync(35) public int blackHoleCount;
    @GuiSync(36) public int duplicationSpeedMultiplier;
    @GuiSync(37) public int duplicationMatterPerSecond;
    @GuiSync(38) public String duplicationEnergyPriority = "FE → AE";
    @GuiSync(39) public int duplicationIntervalTicks = 20;
    @GuiSync(40) public int duplicationMatterPerCycle;
    @GuiSync(41) public long duplicationFePerCycle;
    @GuiSync(42) public long duplicationAePerCycle;
    @GuiSync(43) public long lastDuplicationFeConsumed;
    @GuiSync(44) public double lastDuplicationAeConsumed;
    private final AppEngSlot quantumSlot;
    private final AppEngSlot duplicationSampleSlot;
    private final AppEngSlot blackHoleSlot;
    private final SingularityBlockEntity machine;
    private long dismantleArmedAt = Long.MIN_VALUE;
    private long lastRefresh = Long.MIN_VALUE;
    private SingularityMenu(int id, Inventory inventory, SingularityBlockEntity machine) {
        super(TYPE, id, inventory, machine); this.machine = machine;
        createPlayerInventorySlots(inventory);
        var slots = getSlots(SlotSemantics.PLAYER_INVENTORY);
        for (int i = 0; i < slots.size(); i++) { slots.get(i).x = 85 + i % 9 * 18; slots.get(i).y = 282 + i / 9 * 18; }
        slots = getSlots(SlotSemantics.PLAYER_HOTBAR);
        for (int i = 0; i < slots.size(); i++) { slots.get(i).x = 85 + i * 18; slots.get(i).y = 340; }
        quantumSlot = new AppEngSlot(machine.getQuantumInventory(), 0) {
            @Override public boolean mayPlace(ItemStack stack) {
                return SingularityBlockEntity.isQuantumEntangledSingularity(stack) && super.mayPlace(stack);
            }
        };
        quantumSlot.setIcon(Icon.BACKGROUND_SINGULARITY);
        quantumSlot.x = QUANTUM_X;
        quantumSlot.y = QUANTUM_Y;
        addSlot(quantumSlot, QUANTUM_INPUT);
        duplicationSampleSlot = new AppEngSlot(machine.getDuplicationInventory(), 0);
        duplicationSampleSlot.x = SAMPLE_X;
        duplicationSampleSlot.y = DUPLICATION_Y;
        addSlot(duplicationSampleSlot, DUPLICATION_SAMPLE);
        blackHoleSlot = new AppEngSlot(machine.getDuplicationInventory(), 1) {
            @Override public boolean mayPlace(ItemStack stack) {
                return stack.is(ModContent.BLACK_HOLE.get()) && super.mayPlace(stack);
            }
        };
        blackHoleSlot.x = BLACK_HOLE_X;
        blackHoleSlot.y = DUPLICATION_Y;
        addSlot(blackHoleSlot, BLACK_HOLE_INPUT);
        registerClientAction("build", () -> withPlayer(machine::startBuild));
        registerClientAction("pause", () -> withPlayer(machine::togglePause));
        registerClientAction("cancel", () -> withPlayer(machine::cancel));
        registerClientAction("embed", () -> withPlayer(machine::requestEmbedding));
        registerClientAction("upgrade", () -> withPlayer(machine::requestSuspendedUpgrade));
        registerClientAction("collection", () -> withPlayer(machine::toggleCollection));
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
    public SingularityBlockEntity getMachine() { return machine; }
    public void setQuantumSlotActive(boolean active) {
        // Page visibility belongs to the client; server validation and inventory ownership stay available.
        if (isClientSide()) quantumSlot.setActive(active);
    }
    public void setDuplicationSlotsActive(boolean active) {
        // Slot visibility is a client-side page concern. Keep the server-side
        // slots active so vanilla/AE2 click validation can still insert or
        // extract items while the client is on the duplication page.
        if (!isClientSide()) return;
        duplicationSampleSlot.setActive(active);
        blackHoleSlot.setActive(active);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (isClientSide() || slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot clickedSlot = slots.get(slotIndex);
        ItemStack clickedStack = clickedSlot.getItem();
        if (clickedStack.isEmpty() || !clickedSlot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }

        int playerStart = playerSlotStart();
        int playerEnd = playerSlotEnd();
        if (playerStart < 0 || playerEnd <= playerStart) {
            return ItemStack.EMPTY;
        }

        if (isSingularitySlot(clickedSlot)) {
            ItemStack moved = clickedStack.copy();
            if (!moveItemStackTo(clickedStack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
            if (clickedStack.isEmpty()) {
                clickedSlot.setByPlayer(ItemStack.EMPTY);
            } else {
                clickedSlot.setChanged();
            }
            return moved;
        }

        if (!isPlayerSlot(clickedSlot)) {
            return ItemStack.EMPTY;
        }

        ItemStack moved = clickedStack.copy();
        boolean transferred;
        if (clickedStack.is(ModContent.BLACK_HOLE.get())) {
            transferred = moveItemStackTo(clickedStack, slotIndex(blackHoleSlot), slotIndex(blackHoleSlot) + 1, false);
        } else if (SingularityBlockEntity.isQuantumEntangledSingularity(clickedStack)) {
            transferred = moveItemStackTo(clickedStack, slotIndex(quantumSlot), slotIndex(quantumSlot) + 1, false);
        } else {
            transferred = moveItemStackTo(clickedStack, slotIndex(duplicationSampleSlot), slotIndex(duplicationSampleSlot) + 1, false);
        }
        if (!transferred) {
            return ItemStack.EMPTY;
        }
        if (clickedStack.isEmpty()) {
            clickedSlot.setByPlayer(ItemStack.EMPTY);
        } else {
            clickedSlot.setChanged();
        }
        return moved;
    }

    private boolean isSingularitySlot(Slot slot) {
        return slot == quantumSlot || slot == duplicationSampleSlot || slot == blackHoleSlot;
    }

    private boolean isPlayerSlot(Slot slot) {
        return getSlots(SlotSemantics.PLAYER_INVENTORY).contains(slot)
                || getSlots(SlotSemantics.PLAYER_HOTBAR).contains(slot);
    }

    private int playerSlotStart() {
        int first = Integer.MAX_VALUE;
        for (Slot slot : getSlots(SlotSemantics.PLAYER_INVENTORY)) first = Math.min(first, slot.index);
        for (Slot slot : getSlots(SlotSemantics.PLAYER_HOTBAR)) first = Math.min(first, slot.index);
        return first == Integer.MAX_VALUE ? -1 : first;
    }

    private int playerSlotEnd() {
        int last = -1;
        for (Slot slot : getSlots(SlotSemantics.PLAYER_INVENTORY)) last = Math.max(last, slot.index);
        for (Slot slot : getSlots(SlotSemantics.PLAYER_HOTBAR)) last = Math.max(last, slot.index);
        return last + 1;
    }

    private int slotIndex(Slot target) {
        return target.index;
    }
    @Override public void broadcastChanges() {
        if (isServerSide()) {
            var scan = machine.inspection();
            formed = machine.formed(); networkOnline = machine.networkOnline();
            structureVersion = machine.structureVersion(); embedRequested = machine.embedRequested();
            motionMode = machine.motion().mode(); motionMessage = machine.motion().message(); movingBodies = machine.motion().hasBodies();
            quantumFrequency = machine.getQuantumFrequency(); quantumLinkState = machine.getQuantumLinkState();
            collectionActive = machine.collectionActive(); collectionStarting = machine.collectionStarting();
            collectionItems = machine.collectionItemIds(); collectionBatchSize = machine.collectionBatchSizeForMenu();
            blackHoleCount = machine.blackHoleCount(); duplicationSpeedMultiplier = machine.duplicationSpeedMultiplier();
            duplicationMatterPerSecond = machine.duplicationTargetPerSecond();
            duplicationEnergyPriority = machine.duplicationEnergyPriorityForMenu();
            duplicationIntervalTicks = machine.duplicationIntervalTicksForMenu();
            duplicationMatterPerCycle = machine.duplicationBatchSizeForMenu();
            duplicationFePerCycle = machine.duplicationFePerCycleForMenu();
            duplicationAePerCycle = machine.duplicationAePerCycleForMenu();
            lastDuplicationFeConsumed = machine.lastDuplicationFeConsumed();
            lastDuplicationAeConsumed = machine.lastDuplicationAeConsumed();
            correct = scan.correct(); missing = scan.missing(); conflicts = scan.conflicts(); unloaded = scan.unloaded();
            operation = machine.operation(); status = machine.status(); paused = machine.paused();
            progress = machine.progress(); operationTotal = machine.operationTotal();
            needed = Arrays.stream(SingularityStructure.Type.values()).map(t -> Integer.toString(scan.needed().getOrDefault(t, 0)))
                    .collect(Collectors.joining(","));
            var pos = machine.problem() == null ? scan.problem() : machine.problem();
            problem = pos == null ? "" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            neededMaterial = machine.neededMaterial() == null ? -1 : machine.neededMaterial().ordinal();
        }
        super.broadcastChanges();
    }
}
