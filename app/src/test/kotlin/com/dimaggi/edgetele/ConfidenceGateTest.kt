package com.dimaggi.edgetele

import com.dimaggi.edgetele.ai.ConfidenceGate
import com.dimaggi.edgetele.data.model.enums.ConfidenceLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidenceGateTest {

    // ---- evaluate() boundary values ----

    @Test fun `0_59 is RED`() =
        assertEquals(ConfidenceLevel.RED, ConfidenceGate.evaluate(0.59f))

    @Test fun `0_60 is YELLOW`() =
        assertEquals(ConfidenceLevel.YELLOW, ConfidenceGate.evaluate(0.60f))

    @Test fun `0_79 is YELLOW`() =
        assertEquals(ConfidenceLevel.YELLOW, ConfidenceGate.evaluate(0.79f))

    @Test fun `0_80 is GREEN`() =
        assertEquals(ConfidenceLevel.GREEN, ConfidenceGate.evaluate(0.80f))

    @Test fun `1_00 is GREEN`() =
        assertEquals(ConfidenceLevel.GREEN, ConfidenceGate.evaluate(1.00f))

    @Test fun `0_00 is RED`() =
        assertEquals(ConfidenceLevel.RED, ConfidenceGate.evaluate(0.00f))

    // ---- requiresExpertConfirmation() ----

    @Test fun `GREEN does not require expert`() =
        assertFalse(ConfidenceGate.requiresExpertConfirmation(ConfidenceLevel.GREEN))

    @Test fun `YELLOW requires expert`() =
        assertTrue(ConfidenceGate.requiresExpertConfirmation(ConfidenceLevel.YELLOW))

    @Test fun `RED requires expert`() =
        assertTrue(ConfidenceGate.requiresExpertConfirmation(ConfidenceLevel.RED))

    // ---- bannerMessage() ----

    @Test fun `GREEN banner is null`() =
        assertNull(ConfidenceGate.bannerMessage(ConfidenceLevel.GREEN))

    @Test fun `YELLOW banner is non-null`() =
        assertTrue(ConfidenceGate.bannerMessage(ConfidenceLevel.YELLOW)!!.isNotBlank())

    @Test fun `RED banner is non-null`() =
        assertTrue(ConfidenceGate.bannerMessage(ConfidenceLevel.RED)!!.isNotBlank())
}
