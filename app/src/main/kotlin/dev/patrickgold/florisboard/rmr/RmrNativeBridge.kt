package dev.patrickgold.florisboard.rmr

import androidx.annotation.Keep

/** Experimental JNI diagnostic only: no live IME dispatch or input access. */
@Keep
internal object RmrNativeBridge {
    const val ENABLED_FOR_IME: Boolean = false
    const val DIAGNOSTIC_UNAVAILABLE: Int = -1

    @Keep
    @JvmStatic
    private external fun nativeSelfTest(): Int

    fun diagnosticSelfTest(): Int = try {
        System.loadLibrary("rmr_ime_probe")
        nativeSelfTest()
    } catch (_: UnsatisfiedLinkError) {
        DIAGNOSTIC_UNAVAILABLE
    } catch (_: SecurityException) {
        DIAGNOSTIC_UNAVAILABLE
    }
}
