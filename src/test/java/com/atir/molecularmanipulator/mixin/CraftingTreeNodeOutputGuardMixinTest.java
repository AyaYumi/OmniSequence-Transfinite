package com.atir.molecularmanipulator.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.atir.molecularmanipulator.runtime.CraftingTreeOutputCountGuard;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CraftingTreeNodeOutputGuardMixinTest {
    @Test
    void preservesPositiveOutputCounts() {
        var rejections = new AtomicInteger();

        assertEquals(4, CraftingTreeOutputCountGuard.sanitize(
                4, rejections::incrementAndGet));
        assertEquals(0, rejections.get());
    }

    @Test
    void rejectsNonPositiveOutputCountsAndReturnsSafeSentinel() {
        var rejections = new AtomicInteger();

        assertEquals(1, CraftingTreeOutputCountGuard.sanitize(
                0, rejections::incrementAndGet));
        assertEquals(1, CraftingTreeOutputCountGuard.sanitize(
                -1, rejections::incrementAndGet));
        assertEquals(2, rejections.get());
    }
}
