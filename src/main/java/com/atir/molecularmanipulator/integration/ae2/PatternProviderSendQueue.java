package com.atir.molecularmanipulator.integration.ae2;

import net.minecraft.core.Direction;

/** Internal query for materials already owned by an AE2 provider's send queue. */
public interface PatternProviderSendQueue {
    boolean omniDispatch$hasPendingSend(Direction direction);
}
