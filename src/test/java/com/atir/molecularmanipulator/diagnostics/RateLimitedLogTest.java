package com.atir.molecularmanipulator.diagnostics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitedLogTest {
    @Test
    void persistentFailuresLogImmediatelyAndAtMostOncePerMinute() {
        var clock = new AtomicLong(-123);
        long interval = TimeUnit.MINUTES.toNanos(1);
        var gate = new RateLimitedLog.Gate(interval, clock::get);
        assertTrue(gate.allow("auto-crafting event"));
        for (int attempt = 0; attempt < 10_000; attempt++) {
            clock.incrementAndGet();
            assertFalse(gate.allow("auto-crafting event"));
        }
        clock.set(-123 + interval - 1);
        assertFalse(gate.allow("auto-crafting event"));
        clock.incrementAndGet();
        assertTrue(gate.allow("auto-crafting event"));
        assertFalse(gate.allow("auto-crafting event"));
    }

    @Test
    void unrelatedFailureCategoriesKeepTheirFirstReport() {
        var gate = new RateLimitedLog.Gate(TimeUnit.MINUTES.toNanos(1), () -> 0);
        assertTrue(gate.allow("batch admission"));
        assertTrue(gate.allow("batch cleanup"));
        assertFalse(gate.allow("batch admission"));
        assertFalse(gate.allow("batch cleanup"));
    }

    @Test
    void concurrentMachinesShareOneWindow() {
        var gate = new RateLimitedLog.Gate(TimeUnit.MINUTES.toNanos(1), () -> 0);
        var emitted = new AtomicInteger();
        IntStream.range(0, 1_000).parallel().forEach(machine -> {
            if (gate.allow("output return")) emitted.incrementAndGet();
        });
        assertEquals(1, emitted.get());
    }

    @Test
    void nativeFailureReasonsRemainArgumentsInsteadOfRateLimitKeys() throws Exception {
        var type = new ClassNode();
        try (var input = getClass().getResourceAsStream(
                "/com/atir/molecularmanipulator/mixin/CraftingCpuLogicMixin.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(type, 0);
        }
        var failure = type.methods.stream().filter(method -> method.name.equals("omniNative$fail"))
                .findFirst().orElseThrow();
        boolean constantTemplate = false;
        boolean limitedWarning = false;
        boolean pausesCpu = false;
        for (var instruction : failure.instructions) {
            assertFalse(instruction instanceof InvokeDynamicInsnNode,
                    "Failure reasons must not create a separate rate-limit key for each CPU");
            if (instruction instanceof LdcInsnNode constant) {
                constantTemplate |= "Native batch ownership/accounting is uncertain; CPU suspended: {}"
                        .equals(constant.cst);
            }
            if (instruction instanceof MethodInsnNode call) {
                limitedWarning |= call.owner.equals("com/atir/molecularmanipulator/diagnostics/RateLimitedLog")
                        && call.name.equals("warn");
                pausesCpu |= call.name.equals("omniDispatch$pause");
            }
        }
        assertTrue(constantTemplate);
        assertTrue(limitedWarning);
        assertTrue(pausesCpu, "Logging cleanup must retain uncertain-ownership suspension");
    }

    @Test
    void monotonicClockWrapDoesNotBreakTheWindow() {
        var clock = new AtomicLong(Long.MAX_VALUE - 5);
        var gate = new RateLimitedLog.Gate(10, clock::get);
        assertTrue(gate.allow("failure"));
        clock.set(Long.MIN_VALUE + 3);
        assertFalse(gate.allow("failure"));
        clock.incrementAndGet();
        assertTrue(gate.allow("failure"));
    }
}
