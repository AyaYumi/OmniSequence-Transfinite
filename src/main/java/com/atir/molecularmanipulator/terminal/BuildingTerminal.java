package com.atir.molecularmanipulator.terminal;

import com.atir.molecularmanipulator.blockentity.*;
import com.atir.molecularmanipulator.item.BuildingTerminalItem;
import com.atir.molecularmanipulator.menu.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** The terminal remembers explicit bindings, never discovers or loads other players' machines. */
public final class BuildingTerminal {
    public static final int MAX_BINDINGS = 64;
    private static final String BINDINGS = "BuildingTerminalBindings";

    private BuildingTerminal() {}

    public record Binding(GlobalPos target, ResourceLocation block) {
        public Binding {
            target = GlobalPos.of(target.dimension(), target.pos().immutable());
        }
    }

    public static List<Binding> bindings(ItemStack stack) {
        if (!(stack.getItem() instanceof BuildingTerminalItem)) return List.of();
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        var list = tag.getList(BINDINGS, Tag.TAG_COMPOUND);
        var result = new ArrayList<Binding>();
        for (int i = 0; i < list.size() && result.size() < MAX_BINDINGS; i++) {
            var entry = list.getCompound(i);
            var dimension = ResourceLocation.tryParse(entry.getString("dimension"));
            var block = ResourceLocation.tryParse(entry.getString("block"));
            if (dimension == null || block == null || !entry.contains("pos", Tag.TAG_LONG)) continue;
            var target = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension),
                    BlockPos.of(entry.getLong("pos")));
            if (result.stream().noneMatch(binding -> binding.target().equals(target))) {
                result.add(new Binding(target, block));
            }
        }
        return List.copyOf(result);
    }

    public static void saveBindings(ItemStack stack, List<Binding> bindings) {
        if (!(stack.getItem() instanceof BuildingTerminalItem) || bindings.size() > MAX_BINDINGS) {
            throw new IllegalArgumentException("Invalid building terminal bindings");
        }
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            var list = new ListTag();
            for (var binding : bindings) {
                var entry = new CompoundTag();
                entry.putString("dimension", binding.target().dimension().location().toString());
                entry.putLong("pos", binding.target().pos().asLong());
                entry.putString("block", binding.block().toString());
                list.add(entry);
            }
            if (list.isEmpty()) tag.remove(BINDINGS);
            else tag.put(BINDINGS, list);
        });
    }

    public static Binding binding(BlockEntity machine) {
        return new Binding(GlobalPos.of(machine.getLevel().dimension(), machine.getBlockPos()),
                BuiltInRegistries.BLOCK.getKey(machine.getBlockState().getBlock()));
    }

    public static boolean isBound(ItemStack stack, Binding binding) {
        return bindings(stack).contains(binding);
    }

    /** Retains nearby interaction while allowing explicitly bound controllers at any same-dimension distance. */
    public static boolean canAccess(Player player, BlockEntity machine) {
        var level = machine.getLevel();
        if (level == null || player.level() != level || machine.isRemoved()
                || !level.hasChunkAt(machine.getBlockPos()) || !level.mayInteract(player, machine.getBlockPos())) return false;
        var binding = binding(machine);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isBound(player.getInventory().getItem(slot), binding)) return true;
        }
        return false;
    }

    @Nullable
    public static BlockEntity resolve(Level level, Binding binding) {
        if (!level.dimension().equals(binding.target().dimension()) || !level.hasChunkAt(binding.target().pos())) return null;
        var machine = level.getBlockEntity(binding.target().pos());
        return machine != null && !machine.isRemoved() && menuType(machine) != null
                && BuiltInRegistries.BLOCK.getKey(machine.getBlockState().getBlock()).equals(binding.block()) ? machine : null;
    }

    /** Only real controllers or service ports with their own GUI are terminal targets. */
    @Nullable
    public static BlockEntity findTarget(Level level, BlockPos clicked) {
        if (!level.hasChunkAt(clicked)) return null;
        var machine = level.getBlockEntity(clicked);
        if (machine != null && menuType(machine) != null) return machine;
        // Verified orrery anchors are legitimate controller binding points too.
        return StarComputationEngineCoreBlockEntity.findCore(level, clicked);
    }

    @Nullable
    public static MenuType<?> menuType(BlockEntity machine) {
        if (machine instanceof MolecularManipulatorBlockEntity) return MolecularManipulatorMenu.TYPE;
        if (machine instanceof MolecularCenterBlockEntity) return MolecularCenterMenu.TYPE;
        if (machine instanceof MolecularAutoCrafterBlockEntity) return MolecularAutoCrafterMenu.TYPE;
        if (machine instanceof MatterFabricationBlockEntity) return MatterFabricationMenu.TYPE;
        if (machine instanceof MatterFabricationPortBlockEntity) return MatterFabricationPortMenu.TYPE;
        if (machine instanceof MatterFabricationPatternAssemblyBlockEntity) return MatterFabricationPatternAssemblyMenu.TYPE;
        if (machine instanceof StarComputationEngineCoreBlockEntity) return StarComputationEngineMenu.TYPE;
        if (machine instanceof OmniComputationCoreBlockEntity core && !core.isSingleBlock()) return OmniComputationMenu.TYPE;
        if (machine instanceof SingularityBlockEntity) return SingularityMenu.TYPE;
        return null;
    }
}
