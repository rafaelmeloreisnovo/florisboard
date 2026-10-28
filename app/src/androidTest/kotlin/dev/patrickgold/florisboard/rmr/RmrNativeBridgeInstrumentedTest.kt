package dev.patrickgold.florisboard.rmr

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RmrNativeBridgeInstrumentedTest {
    @Test fun imeRouteIsNotEnabled() {
        assertFalse(RmrNativeBridge.ENABLED_FOR_IME)
    }

    @Test fun nativeC11AbiAndFailsafeSmokeTest() {
        assertEquals(0, RmrNativeBridge.diagnosticSelfTest())
    }
}
