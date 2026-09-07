package com.atir.molecularmanipulator.menu;

import appeng.api.stacks.GenericStack;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.MenuTypeBuilder;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPortBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/** Menu shared by the four material service ports. */
public final class MatterFabricationPortMenu extends AEBaseMenu {
    private static final String ACTION_TRANSFER_FLUID = "transfer_fluid";
    private static final String ACTION_INTERACT_FLUID = "interact_fluid";
    private static final String ACTION_RETURN = "return_inputs";
    private static final String ACTION_AUTO = "toggle_auto_output";
    private static final String ACTION_SIDE = "toggle_output_side";

    public static final MenuType<MatterFabricationPortMenu> TYPE = MenuTypeBuilder
            .create(MatterFabricationPortMenu::new, MatterFabricationPortBlockEntity.class)
            .buildUnregistered(MolecularManipulator.id("matter_fabrication_port"));

    @GuiSync(40)
    public GenericStack tank0;
    @GuiSync(41)
    public GenericStack tank1;
    @GuiSync(42)
    public GenericStack tank2;
    @GuiSync(43)
    public GenericStack tank3;
    @GuiSync(44) public boolean networkOnline;
    @GuiSync(45) public boolean autoOutput;
    @GuiSync(46) public int outputSides;
    @GuiSync(47) public int occupied;
    @GuiSync(48) public long totalAmount;
    @GuiSync(49) public long lastReturned = -1;

    private final MatterFabricationPortBlockEntity port;

    private MatterFabricationPortMenu(int id, Inventory playerInventory,
            MatterFabricationPortBlockEntity port) {
        super(TYPE, id, playerInventory, port);
        this.port = port;
        createPlayerInventorySlots(playerInventory);
        layoutPlayerInventory();

        if (port.getPortType().isItem()) {
            var semantic = port.getPortType().isInput()
                    ? SlotSemantics.MACHINE_INPUT : SlotSemantics.MACHINE_OUTPUT;
            for (int slot = 0; slot < MatterFabricationPortBlockEntity.ITEM_SLOTS; slot++) {
                int x = 27 + slot % 4 * 18;
                int y = 70 + slot / 4 * 18;
                addSlot(new SlotItemHandler(port.getInventory(), slot, x, y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return MatterFabricationPortMenu.this.port.getPortType().isInput()
                                && super.mayPlace(stack);
                    }
                }, semantic);
            }
        }
        registerClientAction(ACTION_TRANSFER_FLUID, Integer.class, this::transferFluid);
        registerClientAction(ACTION_INTERACT_FLUID, Integer.class, this::interactFluid);
        registerClientAction(ACTION_RETURN, this::returnInputs);
        registerClientAction(ACTION_AUTO, this::toggleAutoOutput);
        registerClientAction(ACTION_SIDE, Integer.class, this::toggleOutputSide);
    }

    public void returnInputs() {
        if (isClientSide()) sendClientAction(ACTION_RETURN);
        else if (getPlayer().mayBuild() && port.getPortType().isInput()) {
            lastReturned = port.returnInputsToNetwork(); broadcastChanges();
        }
    }

    public void toggleAutoOutput() {
        if (isClientSide()) sendClientAction(ACTION_AUTO);
        else if (getPlayer().mayBuild()) { port.setAutoOutput(!port.isAutoOutput()); broadcastChanges(); }
    }

    public void toggleOutputSide(Integer side) {
        if (side == null || side < 0 || side >= 6) return;
        if (isClientSide()) sendClientAction(ACTION_SIDE, side);
        else if (getPlayer().mayBuild()) { port.setOutputSides(port.getOutputSides() ^ 1 << side); broadcastChanges(); }
    }

    public MatterFabricationPortBlockEntity getPort() {
        return port;
    }

    public GenericStack getTankStack(int index) {
        return switch (index) {
            case 0 -> tank0;
            case 1 -> tank1;
            case 2 -> tank2;
            case 3 -> tank3;
            default -> null;
        };
    }

    public void requestFluidTransfer(int tank) {
        if (isClientSide() && tank >= 0 && tank < MatterFabricationPortBlockEntity.FLUID_TANKS) {
            sendClientAction(ACTION_TRANSFER_FLUID, tank);
        }
    }

    private void transferFluid(Integer requestedTank) {
        transferFluid(requestedTank, false);
    }

    public void interactFluid(Integer tank) {
        if (tank == null || tank < 0 || tank >= MatterFabricationPortBlockEntity.FLUID_TANKS) return;
        if (isClientSide()) sendClientAction(ACTION_INTERACT_FLUID, tank);
        else transferFluid(tank, true);
    }

    private void transferFluid(Integer requestedTank, boolean bidirectional) {
        if (isClientSide() || requestedTank == null || requestedTank < 0
                || requestedTank >= MatterFabricationPortBlockEntity.FLUID_TANKS
                || !port.getPortType().isFluid() || !(getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        var carried = getCarried();
        if (carried.isEmpty()) {
            return;
        }
        var playerInventory = player.getCapability(Capabilities.ItemHandler.ENTITY);
        if (playerInventory == null) {
            return;
        }
        var singleTank = new SingleTankHandler(requestedTank);
        boolean emptyContainer = port.getPortType().isInput();
        if (bidirectional) {
            var container = FluidUtil.getFluidHandler(carried.copyWithCount(1)).orElse(null);
            if (container == null) return;
            emptyContainer = false;
            for (int tank = 0; tank < container.getTanks(); tank++) {
                if (!container.getFluidInTank(tank).isEmpty()) { emptyContainer = true; break; }
            }
        }
        // Right-click performs a real container exchange, including in creative mode. With no player fallback,
        // a stacked result must fit in the inventory before fluid is moved; nothing is dropped on the ground.
        var transferPlayer = bidirectional ? null : player;
        FluidActionResult result = emptyContainer
                ? FluidUtil.tryEmptyContainerAndStow(carried, singleTank, playerInventory, Integer.MAX_VALUE, transferPlayer, true)
                : FluidUtil.tryFillContainerAndStow(carried, singleTank, playerInventory, Integer.MAX_VALUE, transferPlayer, true);
        if (result.isSuccess()) {
            setCarried(result.getResult());
            broadcastChanges();
        }
    }

    private void layoutPlayerInventory() {
        var main = getSlots(SlotSemantics.PLAYER_INVENTORY);
        for (int index = 0; index < main.size(); index++) {
            main.get(index).x = 44 + index % 9 * 18;
            main.get(index).y = 198 + index / 9 * 18;
        }
        var hotbar = getSlots(SlotSemantics.PLAYER_HOTBAR);
        for (int index = 0; index < hotbar.size(); index++) {
            hotbar.get(index).x = 44 + index * 18;
            hotbar.get(index).y = 256;
        }
    }

    @Override
    public void broadcastChanges() {
        if (isServerSide()) {
            networkOnline = port.isNetworkOnline(); autoOutput = port.isAutoOutput(); outputSides = port.getOutputSides();
            occupied = port.occupiedSlots(); totalAmount = port.totalAmount();
        }
        if (isServerSide() && port.getPortType().isFluid()) {
            tank0 = GenericStack.fromFluidStack(port.getTank(0).getFluid());
            tank1 = GenericStack.fromFluidStack(port.getTank(1).getFluid());
            tank2 = GenericStack.fromFluidStack(port.getTank(2).getFluid());
            tank3 = GenericStack.fromFluidStack(port.getTank(3).getFluid());
        }
        super.broadcastChanges();
    }

    private final class SingleTankHandler implements IFluidHandler {
        private final int tankIndex;

        private SingleTankHandler(int tankIndex) {
            this.tankIndex = tankIndex;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int ignored) {
            return port.getTank(tankIndex).getFluid().copy();
        }

        @Override
        public int getTankCapacity(int ignored) {
            return MatterFabricationPortBlockEntity.FLUID_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int ignored, FluidStack stack) {
            return port.getTank(tankIndex).isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return port.getTank(tankIndex).fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return port.getTank(tankIndex).drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return port.getTank(tankIndex).drain(maxDrain, action);
        }
    }
}
