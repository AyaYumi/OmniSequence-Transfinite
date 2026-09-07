package com.atir.molecularmanipulator.menu;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.RestrictedInputSlot;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternAssemblyBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/** A single, renameable 9 x 4 processing-pattern bank. */
public final class MatterFabricationPatternAssemblyMenu extends AEBaseMenu {
    private static final String ACTION_RENAME = "rename_assembly";
    private static final String ACTION_VIEW = "buffer_view", ACTION_PAGE = "buffer_page", ACTION_REFUND = "buffer_refund";

    public static final MenuType<MatterFabricationPatternAssemblyMenu> TYPE = MenuTypeBuilder
            .create(MatterFabricationPatternAssemblyMenu::new,
                    MatterFabricationPatternAssemblyBlockEntity.class)
            .buildUnregistered(MolecularManipulator.id("matter_fabrication_pattern_assembly"));

    @GuiSync(50)
    public String assemblyName = "";
    @GuiSync(51) public boolean networkOnline;
    @GuiSync(52) public int occupied;
    @GuiSync(53) public int view;
    @GuiSync(54) public MatterPatternBufferMenuState bufferState = MatterPatternBufferMenuState.EMPTY;
    @GuiSync(55) public boolean canRefund;
    @GuiSync(56) public boolean controllerReady;
    private int bufferPage;
    private long nextBufferSync;

    private final MatterFabricationPatternAssemblyBlockEntity assembly;

    private MatterFabricationPatternAssemblyMenu(int id, Inventory playerInventory,
            MatterFabricationPatternAssemblyBlockEntity assembly) {
        super(TYPE, id, playerInventory, assembly);
        this.assembly = assembly;
        createPlayerInventorySlots(playerInventory);
        layoutPlayerInventory();

        var patterns = assembly.getLogic().getPatternInv();
        for (int slot = 0; slot < MatterFabricationPatternAssemblyBlockEntity.PATTERN_SLOTS; slot++) {
            var patternSlot = new RestrictedInputSlot(
                    RestrictedInputSlot.PlacableItemType.PROVIDER_PATTERN, patterns, slot);
            patternSlot.x = 44 + slot % 9 * 18;
            patternSlot.y = 111 + slot / 9 * 18;
            addSlot(patternSlot, SlotSemantics.ENCODED_PATTERN);
        }
        registerClientAction(ACTION_RENAME, String.class, this::rename);
        registerClientAction(ACTION_VIEW, Integer.class, this::setView);
        registerClientAction(ACTION_PAGE, Integer.class, this::setPage);
        registerClientAction(ACTION_REFUND, this::refund);
    }

    public void requestView(int selected) { if (isClientSide()) sendClientAction(ACTION_VIEW, selected); }
    public void requestPage(int page) { if (isClientSide()) sendClientAction(ACTION_PAGE, page); }
    public void requestRefund() { if (isClientSide()) sendClientAction(ACTION_REFUND); }
    private void setView(int selected) { view = Math.clamp(selected, 0, 2); bufferPage = 0; nextBufferSync = 0; updatePatternSlots(); }
    private void setPage(int page) { bufferPage = Math.max(0, page); nextBufferSync = 0; }
    private void refund() { if (isServerSide() && getPlayer().mayBuild()) { assembly.getBuffer().refundQueuedInputs(); nextBufferSync = 0; } }
    public void updatePatternSlots() { for (var slot : getSlots(SlotSemantics.ENCODED_PATTERN)) ((appeng.menu.slot.AppEngSlot) slot).setActive(view == 0); }

    public void requestRename(String name) {
        if (isClientSide()) {
            sendClientAction(ACTION_RENAME, name == null ? "" : name);
        }
    }

    private void rename(String name) {
        if (isServerSide() && getPlayer().mayBuild()) {
            assembly.rename(name);
        }
    }

    private void layoutPlayerInventory() {
        var main = getSlots(SlotSemantics.PLAYER_INVENTORY);
        for (int index = 0; index < main.size(); index++) {
            main.get(index).x = 44 + index % 9 * 18;
            main.get(index).y = 238 + index / 9 * 18;
        }
        var hotbar = getSlots(SlotSemantics.PLAYER_HOTBAR);
        for (int index = 0; index < hotbar.size(); index++) {
            hotbar.get(index).x = 44 + index * 18;
            hotbar.get(index).y = 296;
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            assemblyName = assembly.getName().getString();
            networkOnline = assembly.getMainNode().isActive();
            controllerReady = assembly.isOperational();
            occupied = 0;
            for (var stack : assembly.getLogic().getPatternInv()) if (!stack.isEmpty()) occupied++;
            var buffer = assembly.getBuffer();
            canRefund = buffer.hasQueuedInputs() && !buffer.isUnavailable();
            long now = getPlayer().level().getGameTime();
            if (now >= nextBufferSync) {
                nextBufferSync = now + 5;
                var contents = view == 0 ? java.util.List.<appeng.api.stacks.GenericStack>of() : buffer.contents(view == 2);
                int pages = Math.max(1, Math.ceilDiv(contents.size(), MatterPatternBufferMenuState.PAGE_SIZE));
                bufferPage = Math.min(bufferPage, pages - 1);
                int start = bufferPage * MatterPatternBufferMenuState.PAGE_SIZE;
                int itemTypes = (int) contents.stream().filter(stack -> stack.what() instanceof appeng.api.stacks.AEItemKey).count();
                bufferState = new MatterPatternBufferMenuState(bufferPage, pages, itemTypes, contents.size() - itemTypes,
                        buffer.queuedPatterns(), buffer.isProcessing(), buffer.isUnavailable(),
                        contents.subList(start, Math.min(contents.size(), start + MatterPatternBufferMenuState.PAGE_SIZE)));
            }
            updatePatternSlots();
        }
        super.broadcastChanges();
    }
}
