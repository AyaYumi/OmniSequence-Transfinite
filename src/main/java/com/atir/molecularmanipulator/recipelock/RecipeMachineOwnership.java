package com.atir.molecularmanipulator.recipelock;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.blockentity.StarComputationEngineCoreBlockEntity;
import com.atir.molecularmanipulator.integration.FtbTeamOwnership;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

/** Persistent placement ownership for executors without a native team field. It stores no research grants. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class RecipeMachineOwnership {
    private static final String TEAM = "molecularmanipulator_recipe_team";

    private RecipeMachineOwnership() {}

    @Nullable public static UUID team(BlockEntity machine) {
        if (machine instanceof MatterFabricationBlockEntity matter) return matter.getBoundTeam();
        if (machine instanceof StarComputationEngineCoreBlockEntity engine) return engine.getBoundTeam();
        var data = machine.getPersistentData();
        return data.hasUUID(TEAM) ? data.getUUID(TEAM) : null;
    }

    /** Placement or an explicit first bind only. Never changes an already-bound machine to the last user. */
    public static boolean bind(BlockEntity machine, ServerPlayer player) {
        if (team(machine) != null || machine.getLevel() != player.level() || machine.isRemoved()
                || !player.mayBuild() || player.isSpectator()
                || !player.level().mayInteract(player, machine.getBlockPos())) return false;
        var team = FtbTeamOwnership.forPlayer(player);
        if (team == null) return false;
        // Native machines must keep their original ownership and migration policy.
        if (machine instanceof MatterFabricationBlockEntity || machine instanceof StarComputationEngineCoreBlockEntity) return false;
        machine.getPersistentData().putUUID(TEAM, team);
        machine.setChanged();
        return true;
    }

    @SubscribeEvent
    public static void placed(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiple) {
            for (var snapshot : multiple.getReplacedBlockSnapshots()) {
                var machine = level.getBlockEntity(snapshot.getPos());
                if (machine != null) bind(machine, player);
            }
        } else {
            var machine = level.getBlockEntity(event.getPos());
            if (machine != null) bind(machine, player);
        }
    }

    /** Existing saves and dispenser-placed machines can be deliberately claimed, without a global first-opener grant. */
    @SubscribeEvent
    public static void firstBind(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND
                || !player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
                || player.distanceToSqr(event.getPos().getCenter()) > 64) return;
        var machine = player.serverLevel().getBlockEntity(event.getPos());
        if (machine != null && bind(machine, player)) {
            player.displayClientMessage(Component.literal("已将机器制作权限绑定至你的当前团队；不会复制 ME 共享研究进度。"), true);
        }
    }
}
