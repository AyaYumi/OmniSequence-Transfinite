package appeng.crafting.execution;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import com.atir.molecularmanipulator.mixin.ExactExecutingJobAccessor;
import java.util.Map;

/** Gives isolated tests the accessor that Mixin adds to the real AE2 job in game. */
public final class EcoCpuDispatchJob extends ExecutingCraftingJob implements ExactExecutingJobAccessor {
    public Map<IPatternDetails, Object> tasks;
    public ListCraftingInventory waiting;
    public GenericStack output;
    public long remaining;
    private EcoCpuDispatchJob() { super((appeng.api.networking.crafting.ICraftingPlan) null, null, null, null); } // allocated without construction
    public long molecularmanipulator$getRemaining() { return remaining; }
    public void molecularmanipulator$setRemaining(long value) { remaining = value; }
    public ListCraftingInventory molecularmanipulator$getWaitingFor() { return waiting; }
    public GenericStack molecularmanipulator$getFinalOutput() { return output; }
}
