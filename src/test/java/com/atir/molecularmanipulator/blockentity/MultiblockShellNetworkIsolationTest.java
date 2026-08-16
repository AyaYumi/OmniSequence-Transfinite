package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.assertFalse;

import appeng.api.networking.IInWorldGridNodeHost;
import com.atir.molecularmanipulator.block.MolecularCenterPartBlock;
import com.atir.molecularmanipulator.block.OmniComputationCasingBlock;
import com.atir.molecularmanipulator.block.OmniComputationPartBlock;
import net.minecraft.world.level.block.EntityBlock;
import org.junit.jupiter.api.Test;

class MultiblockShellNetworkIsolationTest {
    @Test
    void molecularCenterCasingsCannotExposeGridNodes() {
        assertFalse(EntityBlock.class.isAssignableFrom(MolecularCenterPartBlock.class));
        assertFalse(IInWorldGridNodeHost.class.isAssignableFrom(MolecularCenterShellBlockEntity.class));
    }

    @Test
    void omniComputationShellBlocksCannotExposeGridNodes() {
        assertFalse(EntityBlock.class.isAssignableFrom(OmniComputationCasingBlock.class));
        assertFalse(EntityBlock.class.isAssignableFrom(OmniComputationPartBlock.class));
        assertFalse(IInWorldGridNodeHost.class.isAssignableFrom(OmniComputationCasingBlock.class));
        assertFalse(IInWorldGridNodeHost.class.isAssignableFrom(OmniComputationPartBlock.class));
    }
}
