package com.atir.molecularmanipulator.entity;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingularityAssemblyFluidTest {
    @Test void assemblyOverridesTheFullBoundsFluidScan() throws Exception {
        var method = SingularityAssemblyEntity.class.getDeclaredMethod("updateFluidHeightAndDoFluidPushing");
        assertSame(SingularityAssemblyEntity.class, method.getDeclaringClass(),
                "Assembly bodies must own the fluid-scan override instead of inheriting Entity's full-AABB scan");
        assertTrue(Modifier.isPublic(method.getModifiers()), "Entity's fluid-scan override is public");
        assertEquals(void.class, method.getReturnType());
        assertEquals(0, method.getParameterCount());
    }
}
