package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fml.ModList;

/** Probe actual optional contracts reflectively; no NeoForge APIs or test stubs enter the runtime. */
final class MatterNativeCpuChecks {
    static void run(GameTestHelper helper, MatterFabricationPatternAssemblyBlockEntity assembly,
            IPatternDetails pattern, Map<AEKey, Long> unit) {
        try {
            var logic = assembly.getLogic();
            if (ModList.get().isLoaded("neoecoae")) {
                var api = optional("cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider");
                if (api == null) {
                    helper.assertTrue(!hasInterface(logic, "ECOFastPathDispatchProvider"), "Older Forge ECO must retain ordinary AE dispatch");
                    System.out.println("MATTER_ECO_FALLBACK_PASS: installed Forge ECO has no fast-path API");
                } else {
                    helper.assertTrue(api.isInstance(logic), "Available ECO contract must be injected");
                    var contextType = Class.forName("cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext");
                    var context = construct(contextType, pattern, MatterCpuBatchGameTests.stacks(unit),
                            Arrays.asList(pattern.getOutputs()), List.of(), helper.getLevel(), UUID.randomUUID());
                    var prepared = api.getMethod("eco$prepareFastPath", contextType).invoke(logic, context);
                    helper.assertTrue(prepared != null, "Actual ECO contract must prepare the native provider");
                    var batchType = Class.forName("cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider$Batch");
                    var batch = construct(batchType, 1026L, MatterCpuBatchGameTests.stacks(MatterPatternBuffer.scaled(unit, 1026)),
                            MatterCpuBatchGameTests.stacks(MatterPatternBuffer.scaled(MatterFabricationBatch.patternOutputs(pattern), 1026)),
                            List.of(), Map.of());
                    @SuppressWarnings("unchecked")
                    var dispatch = (java.util.function.Predicate<Object>) prepared.getClass().getMethod("dispatch").invoke(prepared);
                    helper.assertTrue(dispatch.test(batch), "Real ECO API must accept a complete batch");
                    MatterCpuBatchGameTests.assertQueue(helper, assembly, MatterPatternBuffer.scaled(unit, 1026), "ECO API");
                    System.out.println("MATTER_ECO_API_PASS: real contract admitted 1026 crafts");
                }
            }
            if (ModList.get().isLoaded("thunderbolt")) {
                var api = optional("com.moakiee.thunderbolt.api.crafting.batch.IBatchCraftingProvider");
                if (api == null) {
                    helper.assertTrue(!hasInterface(logic, "IBatchCraftingProvider"), "Older Forge Thunderbolt must retain ordinary AE dispatch");
                    System.out.println("MATTER_THUNDERBOLT_FALLBACK_PASS: installed Forge CPU has no batch API");
                } else {
                    helper.assertTrue(api.isInstance(logic), "Available Thunderbolt contract must be injected");
                    var prototype = MatterCpuBatchGameTests.counters(unit);
                    long left = (long) api.getMethod("pushBatch", IPatternDetails.class, KeyCounter[].class, long.class)
                            .invoke(logic, pattern, prototype, 1026L);
                    helper.assertTrue(left == 0 && MatterCpuBatchGameTests.amounts(prototype).equals(unit),
                            "Actual Thunderbolt contract must admit the full batch without consuming its prototype");
                    MatterCpuBatchGameTests.assertQueue(helper, assembly, MatterPatternBuffer.scaled(unit, 1026), "Thunderbolt API");
                    System.out.println("MATTER_THUNDERBOLT_API_PASS: real contract admitted 1026 crafts");
                }
            }
            if (ModList.get().isLoaded("data_energistics")) {
                var registry = optional("com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProviderAdapters");
                if (registry == null) {
                    System.out.println("MATTER_TRINITY_FALLBACK_PASS: installed Forge CPU has no counted registry");
                } else {
                    var supports = Arrays.stream(registry.getMethods()).filter(method -> method.getName().equals("supportsCountedDispatch")
                            && method.getParameterCount() == 1 && method.getParameterTypes()[0].isInstance(logic)).findFirst().orElseThrow();
                    helper.assertTrue((boolean) supports.invoke(null, logic), "Actual Trinity registry must discover the registered provider");
                    // Registration/lifecycle uses the real registry; durable counted ownership is tested below the adapter.
                    var batch = logic.prepareCountedBatch(pattern, unit, 1026);
                    helper.assertTrue(batch != null && batch.commitPrototype(MatterCpuBatchGameTests.counters(unit), 1026),
                            "Registered counted provider must transfer its complete batch");
                    MatterCpuBatchGameTests.assertQueue(helper, assembly, MatterPatternBuffer.scaled(unit, 1026), "Trinity registered provider");
                    System.out.println("MATTER_TRINITY_API_PASS: real registry and durable provider admitted 1026 crafts");
                }
            }
        } catch (ReflectiveOperationException error) { throw new AssertionError("Installed Forge CPU API does not match its declared contract", error); }
    }

    private static Class<?> optional(String name) throws ReflectiveOperationException {
        try { return Class.forName(name); } catch (ClassNotFoundException absent) { return null; }
    }

    private static boolean hasInterface(Object object, String name) {
        return Arrays.stream(object.getClass().getInterfaces()).anyMatch(type -> type.getSimpleName().equals(name));
    }

    private static Object construct(Class<?> type, Object... arguments) throws ReflectiveOperationException {
        for (var constructor : type.getConstructors()) {
            if (constructor.getParameterCount() == arguments.length) return constructor.newInstance(arguments);
        }
        throw new NoSuchMethodException(type.getName() + " constructor with " + arguments.length + " parameters");
    }
}
