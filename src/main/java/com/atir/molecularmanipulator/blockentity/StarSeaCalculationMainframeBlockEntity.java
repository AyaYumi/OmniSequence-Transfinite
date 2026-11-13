package com.atir.molecularmanipulator.blockentity;

import appeng.api.networking.GridFlags;
import appeng.api.orientation.BlockOrientation;
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.me.helpers.MachineSource;
import com.atir.molecularmanipulator.block.StarSeaCalculationGeometry;
import com.atir.molecularmanipulator.block.StarSeaCalculationMainframeBlock;
import com.atir.molecularmanipulator.registry.StarSeaCalculationContent;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.world.DetachedSignalRealm;
import java.util.EnumSet;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Server owner of the 5 x 9 x 5 listener. Only bottom-front PART 2 owns an AE
 * node, accepting cables only at the white front-facing socket; the other reserved cells
 * are passive. Production and power are configurable, with an output bonus in the Detached Signal Realm.
 */
public final class StarSeaCalculationMainframeBlockEntity extends AENetworkBlockEntity {
    /** Full structure validation interval; time, light and loaded chunks are checked every tick. */
    public static final int STRUCTURE_CHECK_INTERVAL = 20;
    private static final int ROOF_COLUMN_COUNT = 25;
    private static final String PAID_ENERGY_TAG = "paid_energy";

    public enum Status {
        UNFORMED, DAYTIME, TOO_BRIGHT, OFFLINE, OUTPUT_FULL, NO_POWER, RUNNING;

        public String translationKey() {
            return "status.molecularmanipulator.star_sea_calculation_mainframe." + name().toLowerCase(Locale.ROOT);
        }
    }

    private final StarSeaCalculationProduction production = new StarSeaCalculationProduction();
    private final MachineSource actionSource = new MachineSource(this);
    private Status status = Status.UNFORMED;
    private boolean running;
    private boolean formed;
    private long lastStructureCheck = Long.MIN_VALUE;
    @Nullable private Direction checkedFacing;

    public StarSeaCalculationMainframeBlockEntity(BlockPos pos, BlockState state) {
        super(StarSeaCalculationContent.BLOCK_ENTITY.get(), pos, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(0);
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return getBlockState().getBlock().asItem();
    }

    @Override
    public EnumSet<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return isCore(getBlockState())
                ? EnumSet.of(getBlockState().getValue(HorizontalDirectionalBlock.FACING))
                : EnumSet.noneOf(Direction.class);
    }

    public Status getStatus() {
        return status;
    }

    /** Synchronized actual production, not the readiness simulated by an empty-hand status probe. */
    public boolean isRunning() {
        return running;
    }

    private void setRunning(boolean next) {
        if (running == next) return;
        running = next;
        markForUpdate();
    }

    public long outputBitsPerTick() {
        long base = ModConfig.STAR_SEA_OUTPUT_BITS_PER_TICK.get();
        return DetachedSignalRealm.isRealm(level) ? base * ModConfig.STAR_SEA_DETACHED_SIGNAL_OUTPUT_MULTIPLIER.get() : base;
    }

    public double energyPerTick() {
        return ModConfig.STAR_SEA_ENERGY_PER_TICK.get();
    }

    /** Private, already paid AE units; unused payment survives changes to the configured price. */
    public double energyCredit() {
        return production.energyCredit();
    }

    /** Called every server tick by the dedicated block's ticker. */
    public void serverTick() {
        if (level == null || level.isClientSide() || isRemoved()) return;
        Status blocked = blockingStatus(false);
        if (blocked != null) {
            status = blocked;
            setRunning(false);
            return;
        }
        var grid = getMainNode().getGrid();
        if (grid == null || !getMainNode().isActive()) {
            status = Status.OFFLINE;
            setRunning(false);
            return;
        }

        double previousCredit = production.energyCredit();
        status = productionStatus(production.tick(grid.getStorageService().getInventory(),
                grid.getEnergyService(), actionSource, outputBitsPerTick(), energyPerTick()));
        setRunning(status == Status.RUNNING);
        if (previousCredit != production.energyCredit()) saveChanges();
    }

    /** Refresh structure/environment and simulate readiness without paying or producing on a click. */
    public void showStatus(Player player) {
        if (level == null || level.isClientSide()) return;
        Status blocked = blockingStatus(true);
        var grid = getMainNode().getGrid();
        if (blocked != null) status = blocked;
        else if (grid == null || !getMainNode().isActive()) status = Status.OFFLINE;
        else status = productionStatus(production.inspect(grid.getStorageService().getInventory(),
                grid.getEnergyService(), actionSource, outputBitsPerTick(), energyPerTick()));

        // Always report the server's configured rate, including this dimension's output multiplier.
        player.displayClientMessage(Component.translatable(
                "message.molecularmanipulator.star_sea_calculation_mainframe.status",
                Component.translatable(status.translationKey()), outputBitsPerTick(), energyPerTick()), true);
    }

    @Nullable
    private Status blockingStatus(boolean forceStructureCheck) {
        var state = getBlockState();
        if (level == null || !isCore(state)) {
            invalidateStructure();
            return Status.UNFORMED;
        }
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        if (!footprintLoaded(facing)) {
            // A cached formed result cannot permit work across an unloaded chunk.
            // Require a fresh full validation when the footprint becomes available.
            invalidateStructure();
            return Status.UNFORMED;
        }
        long now = level.getGameTime();
        if (forceStructureCheck || checkedFacing != facing || lastStructureCheck == Long.MIN_VALUE
                || now < lastStructureCheck || now - lastStructureCheck >= STRUCTURE_CHECK_INTERVAL) {
            formed = StarSeaCalculationMainframeBlock.structureComplete(level, worldPosition, facing);
            lastStructureCheck = now;
            checkedFacing = facing;
        }
        if (!formed) return Status.UNFORMED;

        var dimension = level.dimensionType();
        if (!DetachedSignalRealm.isRealm(level) && !StarSeaCalculationEnvironment.isNight(dimension.hasSkyLight(),
                dimension.hasFixedTime(), level.getDayTime())) return Status.DAYTIME;

        // y=9 is immediately above the reserved y=0..8 body. Sample all 25
        // columns, rotated around the bottom-front core, without a sky-visibility test.
        int maximumBrightness = 0;
        for (int part = 0; part < ROOF_COLUMN_COUNT; part++) {
            BlockPos roof = worldPosition.offset(StarSeaCalculationGeometry.offset(part, facing))
                    .above(StarSeaCalculationEnvironment.ROOF_SAMPLE_HEIGHT);
            maximumBrightness = Math.max(maximumBrightness, level.getMaxLocalRawBrightness(roof));
        }
        return StarSeaCalculationEnvironment.isDarkEnough(maximumBrightness) ? null : Status.TOO_BRIGHT;
    }

    private boolean footprintLoaded(Direction facing) {
        // Chunks span the full world height, so the bottom 25 cells cover every
        // chunk of the 225-cell reservation. hasChunkAt never loads a chunk.
        for (int part = 0; part < ROOF_COLUMN_COUNT; part++) {
            if (!level.hasChunkAt(worldPosition.offset(StarSeaCalculationGeometry.offset(part, facing)))) return false;
        }
        return true;
    }

    private void invalidateStructure() {
        formed = false;
        lastStructureCheck = Long.MIN_VALUE;
        checkedFacing = null;
    }

    private static boolean isCore(BlockState state) {
        return state.getBlock() instanceof StarSeaCalculationMainframeBlock
                && state.getValue(StarSeaCalculationMainframeBlock.PART) == StarSeaCalculationMainframeBlock.CORE_PART;
    }

    private static Status productionStatus(StarSeaCalculationProduction.Result result) {
        return switch (result) {
            case OUTPUT_FULL -> Status.OUTPUT_FULL;
            case NO_POWER -> Status.NO_POWER;
            case RUNNING -> Status.RUNNING;
        };
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return StarSeaCalculationMainframeBlock.bounds(worldPosition,
                getBlockState().getValue(HorizontalDirectionalBlock.FACING)).inflate(.35D, 0, .35D);
    }

    @Override
    protected void writeToStream(FriendlyByteBuf data) {
        super.writeToStream(data);
        data.writeBoolean(running);
    }

    @Override
    protected boolean readFromStream(FriendlyByteBuf data) {
        boolean changed = super.readFromStream(data);
        boolean next = data.readBoolean();
        changed |= running != next;
        running = next;
        return changed;
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putDouble(PAID_ENERGY_TAG, production.energyCredit());
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        production.restoreEnergyCredit(tag.getDouble(PAID_ENERGY_TAG));
        // Formation and readiness are always revalidated before the first work tick.
        invalidateStructure();
        status = Status.UNFORMED;
        running = false;
    }
}
