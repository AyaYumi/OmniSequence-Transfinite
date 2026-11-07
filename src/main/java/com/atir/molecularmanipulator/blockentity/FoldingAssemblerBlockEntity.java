package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.*;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.me.helpers.MachineSource;
import com.atir.molecularmanipulator.building.*;
import com.atir.molecularmanipulator.registry.BuildingContent;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Bootstrap ME provider: one complete bill of materials becomes one finished package. */
public final class FoldingAssemblerBlockEntity extends AENetworkedBlockEntity implements ICraftingProvider {
    private final EnumMap<BuildingType, Long> pending = new EnumMap<>(BuildingType.class);
    private List<IPatternDetails> patterns;
    public FoldingAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(BuildingContent.ASSEMBLER_BE.get(), pos, state);
        getMainNode().setIdlePowerUsage(2).addService(ICraftingProvider.class, this);
    }
    @Override public void onReady() {
        super.onReady(); ICraftingProvider.requestUpdate(getMainNode());
    }
    @Override public List<IPatternDetails> getAvailablePatterns() {
        if (patterns == null) {
            var result = new ArrayList<IPatternDetails>();
            for (var type : BuildingType.orderable()) {
                var inputs = type.materials().entrySet().stream().map(entry -> new GenericStack(entry.getKey(), entry.getValue())).toList();
                var output = new GenericStack(AEItemKey.of(BuildingPackage.fresh(type)), 1);
                var encoded = PatternDetailsHelper.encodeProcessingPattern(inputs, List.of(output));
                var decoded = PatternDetailsHelper.decodePattern(encoded, level);
                if (decoded != null) result.add(decoded);
            }
            patterns = List.copyOf(result);
        }
        return patterns;
    }
    @Override public boolean isBusy() { return !getMainNode().isActive() || pending.values().stream().mapToLong(Long::longValue).sum() >= 64; }
    @Override public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputHolder) {
        if (isBusy() || !getAvailablePatterns().contains(pattern)) return false;
        var stack = ((AEItemKey) pattern.getPrimaryOutput().what()).toStack();
        var type = BuildingPackage.type(stack);
        if (type == null) return false;
        var supplied = new KeyCounter();
        for (var counter : inputHolder) supplied.addAll(counter);
        var expected = type.materials();
        for (var entry : supplied) if (!(entry.getKey() instanceof AEItemKey key)
                || expected.getOrDefault(key, -1L) != entry.getLongValue()) return false;
        for (var entry : expected.entrySet()) if (supplied.get(entry.getKey()) != entry.getValue()) return false;
        pending.merge(type, 1L, Math::addExact);
        saveChanges();
        return true;
    }
    public void serverTick() {
        var grid = getMainNode().getGrid();
        if (grid == null || !getMainNode().isActive() || pending.isEmpty()) return;
        for (var type : List.copyOf(pending.keySet())) {
            long amount = pending.get(type);
            long inserted = grid.getStorageService().getInventory().insert(AEItemKey.of(BuildingPackage.fresh(type)),
                    amount, Actionable.MODULATE, new MachineSource(this));
            if (inserted > 0) {
                if (inserted == amount) pending.remove(type); else pending.put(type, amount - inserted);
                saveChanges();
            }
        }
    }
    @Override public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        var contents = new CompoundTag(); pending.forEach((type, count) -> contents.putLong(type.id, count));
        tag.put("folding_pending", contents);
    }
    @Override public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries); pending.clear();
        var contents = tag.getCompound("folding_pending");
        for (var type : BuildingType.values()) {
            long count = contents.getLong(type.id); if (count > 0) pending.put(type, count);
        }
    }
    public boolean hasPendingPackages() { return !pending.isEmpty(); }
    @Override public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        if (pending.isEmpty()) return;
        var contents = new CompoundTag(); saveAdditional(contents, level.registryAccess()); contents.remove("proxy");
        var stack = new ItemStack(BuildingContent.ASSEMBLER_ITEM.get());
        net.minecraft.world.item.BlockItem.setBlockEntityData(stack, getType(), contents);
        drops.clear(); drops.add(stack);
    }
}
