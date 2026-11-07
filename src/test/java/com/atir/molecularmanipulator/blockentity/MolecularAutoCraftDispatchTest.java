package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Hot-path wiring guards complement the real-storage capacity and GameTest regressions. */
class MolecularAutoCraftDispatchTest {
    @Test
    void sharedHostBudgetIsReadBeforeTakingAnyInputs() throws Exception {
        var run = runPattern();
        int budget = callIndex(run, "MolecularAutoCrafterHost", "getAutoCraftingBudget");
        int firstInput = callIndex(run, "CraftingCpuHelper", "extractPatternInputs");
        assertTrue(budget >= 0 && firstInput > budget,
                "Every slot must check the host's remaining allowance before taking its first input");
        assertEquals(Long.MAX_VALUE, MolecularAutoCrafter.MAX_BATCH_CRAFTS,
                "The host budget, not a legacy int-sized per-slot cap, bounds passive batches");
    }

    @Test
    void outputCapacityIsProbedOnlyAfterSelectingTheActualBatch() throws Exception {
        var run = runPattern();
        int expansion = callIndex(run, "MolecularBatchCraftingExtractor", "expandFromFirst");
        int preparation = callIndex(run, "MolecularAutoCrafter", "prepareOutputs");
        int capacity = callIndex(run, "MolecularAutoCraftOutput", "capacity");
        assertTrue(expansion >= 0 && preparation > expansion && capacity > preparation,
                "Probe the material/power-bounded outputs and actual remainders, not a theoretical maximum");
        int capacityCalls = 0;
        for (var instruction : run.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.endsWith("/MolecularAutoCraftOutput") && call.name.equals("capacity")) capacityCalls++;
        }
        assertEquals(1, capacityCalls, "One final probe site, shared by the bounded reduced retry");
    }

    @Test
    void successfulRetryGrowthUsesSaturatingArithmetic() throws Exception {
        assertTrue(callIndex(runPattern(), "MolecularAutoCraftMath", "saturatedMultiply") >= 0,
                "Doubling a Long.MAX_VALUE batch allowance must never wrap negative");
    }

    private static int callIndex(MethodNode method, String owner, String name) {
        int index = 0;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.endsWith("/" + owner) && call.name.equals(name)) return index;
            index++;
        }
        return -1;
    }

    private static MethodNode runPattern() throws Exception {
        var type = new ClassNode();
        try (var input = MolecularAutoCrafter.class.getResourceAsStream("MolecularAutoCrafter.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(type, 0);
        }
        return type.methods.stream().filter(method -> method.name.equals("runPattern")).findFirst().orElseThrow();
    }
}
