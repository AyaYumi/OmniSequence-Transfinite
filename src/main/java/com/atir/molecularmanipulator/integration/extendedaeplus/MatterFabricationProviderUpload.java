package com.atir.molecularmanipulator.integration.extendedaeplus;

import appeng.helpers.patternprovider.PatternContainer;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternAssemblyBlockEntity;
import com.atir.molecularmanipulator.integration.FtbTeamOwnership;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** EAEP's fabrication-category auto target is a machine identity, never a user-supplied display name. */
public final class MatterFabricationProviderUpload {
    private MatterFabricationProviderUpload() {}

    // EAEP 1.6.4 transports component JSON as a String and decodes it for display.
    public static String automaticTarget() {
        return Component.Serializer.toJson(Component.translatable("gui.molecularmanipulator.fabrication.jei_title"));
    }

    public static boolean isFabricationCategory(String key) {
        return key != null && (key.equals("molecularmanipulator:matter_fabrication")
                || key.equals("molecularmanipulator:matter_fabrication_processing")
                || key.equals("matter_fabrication") || key.equals("matter_fabrication_processing")
                || key.equals(Component.translatable("gui.molecularmanipulator.fabrication.jei_title").getString()));
    }

    public static boolean isAutomaticTarget(String name) {
        return automaticTarget().equals(name);
    }

    public static boolean accepts(PatternContainer container, ServerPlayer player) {
        if (!(container instanceof MatterFabricationPatternAssemblyBlockEntity assembly)
                || player == null || player.isSpectator() || assembly.isRemoved()
                || !(assembly.getLevel() instanceof ServerLevel level) || level.getServer() != player.server
                || !level.hasChunkAt(assembly.getBlockPos())) return false;
        var controller = assembly.getController();
        var team = FtbTeamOwnership.forPlayer(player);
        return controller != null && team != null && team.equals(controller.getBoundTeam());
    }
}
