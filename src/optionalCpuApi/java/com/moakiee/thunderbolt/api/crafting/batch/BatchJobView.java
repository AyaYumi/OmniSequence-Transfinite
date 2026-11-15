package com.moakiee.thunderbolt.api.crafting.batch;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.crafting.inv.ListCraftingInventory;
import java.util.Iterator;
import java.util.UUID;
import net.minecraft.world.level.Level;

/** Compile-only 2.0.3 ABI; never packaged. The mixin plugin requires a matching installed executor API. */
public interface BatchJobView {
    Level level();
    UUID craftingId();
    Iterator<BatchTaskHandle> taskIterator();
    ListCraftingInventory waitingFor();
    default void insertWaitingFor(AEKey key, long amount) { throw new UnsupportedOperationException(); }
    void addContainerMaxItems(long count, AEKeyType type);
    default void failDispatch(String reason, Throwable cause) { throw new UnsupportedOperationException(); }
}
