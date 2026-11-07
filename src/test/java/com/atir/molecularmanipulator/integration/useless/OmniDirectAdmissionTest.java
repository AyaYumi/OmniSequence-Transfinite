package com.atir.molecularmanipulator.integration.useless;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class OmniDirectAdmissionTest {
    @Test
    void nativeLongProjectionDoesNotOverflowForUnboundedWindows() {
        var max = BigInteger.valueOf(Long.MAX_VALUE);
        assertEquals(1, OmniDirectAdmission.projectSegments(max));
        assertEquals(2, OmniDirectAdmission.projectSegments(max.add(BigInteger.ONE)));
        assertEquals(Long.MAX_VALUE, OmniDirectAdmission.projectSegments(max.pow(4)));
    }
}
