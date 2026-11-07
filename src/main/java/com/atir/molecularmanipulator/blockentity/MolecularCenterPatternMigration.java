package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.IChestOrDrive;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.cells.StorageCell;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.me.helpers.PlayerSource;
import com.atir.molecularmanipulator.diagnostics.RateLimitedLog;
import com.atir.molecularmanipulator.integration.ae2.SegmentedPatternContainers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Moves existing physical patterns into the matrix's configured main library, synchronously.
 *
 * <p>Sources use AE2's public inventory contracts, not machine or cell class allowlists:
 * active {@link PatternContainer}s expose physical terminal slots, and active
 * {@link IChestOrDrive}s expose their mounted {@link StorageCell}s. Read-only terminal
 * rows cannot be extracted. Private machine slots, crafting-detail definitions and
 * aggregate storage-bus proxies are not inspected. Every commit must remove one actual
 * item from its selected source; no recipes or empty patterns are synthesized.</p>
 */
public final class MolecularCenterPatternMigration {
    private MolecularCenterPatternMigration() {}

    /**
     * Call on the server thread AFTER validating the current menu, distance and build/network
     * permissions. This service additionally validates target formation, main-library ownership
     * and grid membership before every write. There is no queue; partial progress is returned.
     * No empty patterns are spent, no recipes are re-encoded, and duplicate physical patterns
     * remain separate physical items (one per destination slot).
     */
    public static Result migrate(MolecularCenterBlockEntity destination, ServerPlayer player) {
        if (destination == null || player == null || destination.getLevel() != player.serverLevel()
                || !player.serverLevel().getServer().isSameThread() || !destination.isFormed()
                || !destination.canMutateExternalPatterns()) return new Result(0, 0, false, true);
        var grid = destination.getMainNode().getGrid();
        if (grid == null) return new Result(0, 0, false, true);
        var session = new Session(destination, grid, new PlayerSource(player));
        session.run();
        return new Result(session.providers, session.cells, session.full, session.interrupted);
    }

    /**
     * Counts are committed individual items, not unique recipes. destinationFull means an eligible
     * item was left behind for lack of space. interrupted signals an ownership/source failure;
     * successfully moved items remain committed and are included in the counts.
     * fromProviders includes all public pattern containers, not just ordinary providers.
     */
    public record Result(int fromProviders, int fromStorageCells, boolean destinationFull, boolean interrupted) {
        public int moved() { return fromProviders + fromStorageCells; }
    }

    /** Normalize terminal-only segments BEFORE checking source policy or inspecting any inventory. */
    static List<PatternContainer> physicalContainers(Iterable<?> candidates) {
        Set<PatternContainer> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        var result = new ArrayList<PatternContainer>();
        for (var candidate : candidates) {
            if (candidate instanceof PatternContainer container) {
                var physical = SegmentedPatternContainers.unwrap(container);
                if (seen.add(physical)) result.add(physical);
            }
        }
        return result;
    }

    static boolean isPatternContainerType(Class<?> type) {
        return PatternContainer.class.isAssignableFrom(type);
    }

    static boolean isCellHost(Class<?> type) {
        return IChestOrDrive.class.isAssignableFrom(type);
    }

    private static final class Session {
        private final MolecularCenterBlockEntity destination;
        private final IGrid grid;
        private final IActionSource actionSource;
        private final InternalInventory inventory;
        private final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<InternalInventory> visitedInventories = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<StorageCell> visitedCells = Collections.newSetFromMap(new IdentityHashMap<>());
        private final java.util.Map<AEItemKey, Boolean> supportCache = new java.util.HashMap<>();
        private int cursor, providers, cells;
        private boolean full, interrupted;

        Session(MolecularCenterBlockEntity destination, IGrid grid, IActionSource actionSource) {
            this.destination = destination;
            this.grid = grid;
            this.actionSource = actionSource;
            this.inventory = destination.getTerminalPatternInventory();
        }

        private boolean writable() {
            return !destination.isRemoved() && destination.isFormed()
                    && destination.getMainNode().getGrid() == grid && destination.canMutateExternalPatterns()
                    && inventory.size() <= destination.getTerminalPatternInventory().size();
        }

        private boolean supported(ItemStack stack) {
            if (stack.isEmpty()) return false;
            // One synchronous click observes one recipe generation. Decode each distinct
            // physical key once, not once per duplicate slot/item across all source cells.
            return supportCache.computeIfAbsent(AEItemKey.of(stack), key -> {
                try {
                    return destination.isSupportedExternalPattern(stack);
                } catch (RuntimeException invalidPattern) {
                    return false;
                }
            });
        }

        private int nextSlot() {
            if (!writable()) { interrupted = true; return -1; }
            while (cursor < inventory.size() && !inventory.getStackInSlot(cursor).isEmpty()) cursor++;
            if (cursor >= inventory.size()) { full = true; return -1; }
            return cursor;
        }

        void run() {
            // Snapshot machine classes/objects before inventory callbacks request crafting updates.
            var containers = new ArrayList<Object>();
            var cellHosts = new ArrayList<IChestOrDrive>();
            for (var type : grid.getMachineClasses()) {
                if (isPatternContainerType(type)) containers.addAll(grid.getActiveMachines(type));
                if (isCellHost(type)) {
                    for (var host : grid.getActiveMachines(type)) cellHosts.add((IChestOrDrive) host);
                }
            }
            try {
                for (var container : physicalContainers(containers)) {
                    if (container != destination && visited.add(container)) {
                        moveContainer(container);
                    }
                    if (full || !writable()) return;
                }
                for (var host : cellHosts) {
                    if (visited.add(host)) moveCellHost(host);
                    if (full || !writable()) return;
                }
            } finally {
                if (!writable()) interrupted = true;
                try {
                    if (providers + cells > 0) destination.getLogic().updatePatterns();
                } catch (RuntimeException failure) {
                    interrupted = true;
                    RateLimitedLog.warn("Pattern migration could not refresh the destination provider", failure);
                } finally {
                    // Direct, specifically selected cell extraction bypasses the aggregate monitor.
                    if (cells > 0) grid.getStorageService().invalidateCache();
                }
            }
        }

        private void moveContainer(PatternContainer source) {
            try {
                if (source.getGrid() != grid) return;
                var patterns = source.getTerminalPatternInventory();
                if (patterns == inventory || !visitedInventories.add(patterns)) return;
                for (int slot = 0; slot < patterns.size(); slot++) {
                    while (supported(patterns.getStackInSlot(slot))) {
                        // Public terminal inventories can also contain read-only recipe rows.
                        // A displayed icon is not a physical item or an interrupted migration.
                        var single = patterns.getStackInSlot(slot).copyWithCount(1);
                        if (!ItemStack.matches(single, patterns.extractItem(slot, 1, true))) break;
                        int targetSlot = nextSlot();
                        if (targetSlot < 0) return;
                        if (!MolecularCenterPatternTransfer.fromInventory(patterns, slot, inventory, targetSlot,
                                () -> writable() && source.getGrid() == grid)) {
                            // Repair the owning shard too if a target callback failed after writing.
                            destination.onPatternInventoryChanged(targetSlot);
                            interrupted = true;
                            break;
                        }
                        providers++;
                        cursor++;
                    }
                }
            } catch (RuntimeException failure) {
                interrupted = true;
                RateLimitedLog.warn("Pattern migration stopped reading a public pattern container", failure);
            }
            // Use normal inventory notifications for persistence and recipe publication.
            // PatternContainer intentionally does not require a particular provider logic.
        }

        private void moveCellHost(IChestOrDrive source) {
            try {
                var node = source.getActionableNode();
                if (node == null || node.getGrid() != grid || !node.isActive()) return;
                for (int slot = 0; slot < source.getCellCount(); slot++) {
                    var cell = source.getOriginalCellInventory(slot);
                    var mounted = source.getCellInventory(slot);
                    if (cell == null || mounted == null || !visitedCells.add(cell)) continue;
                    int cellSlot = slot;
                    try {
                        // The cell's own snapshot gives provenance; the network's aggregate listing does not.
                        for (var entry : cell.getAvailableStacks()) {
                            if (!(entry.getKey() instanceof AEItemKey key) || entry.getLongValue() <= 0
                                    || !supported(key.toStack(1))
                                    || mounted.extract(key, 1, Actionable.SIMULATE, actionSource) != 1) continue;
                            for (long count = entry.getLongValue(); count > 0; count--) {
                                int targetSlot = nextSlot();
                                if (targetSlot < 0) return;
                                if (!MolecularCenterPatternTransfer.fromCell(cell, mounted, key, actionSource,
                                        inventory, targetSlot, () -> writable() && node.isActive() && node.getGrid() == grid
                                                && source.getOriginalCellInventory(cellSlot) == cell
                                                && source.getCellInventory(cellSlot) == mounted)) {
                                    destination.onPatternInventoryChanged(targetSlot);
                                    interrupted = true;
                                    break;
                                }
                                cells++;
                                cursor++;
                            }
                        }
                    } finally {
                        cell.persist();
                    }
                }
            } catch (RuntimeException failure) {
                interrupted = true;
                RateLimitedLog.warn("Pattern migration stopped reading a finite storage cell", failure);
            }
        }
    }
}
