package com.atir.molecularmanipulator.integration.ae2;

import appeng.me.helpers.IGridConnectedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

/** A machine whose existing ME node can connect to a sequence wireless hub. */
public interface SequenceWirelessHost extends IGridConnectedBlockEntity {
    BlockEntity getBlockEntity();

    SequenceWirelessLink getWirelessLink();

    void refreshRemoteConnection();
}
