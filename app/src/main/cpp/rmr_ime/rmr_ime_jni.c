/* JNI boundary is Android-hosted; it never receives text or keystrokes. */
#include <jni.h>
#include "rmr_ime.h"
JNIEXPORT jint JNICALL
Java_dev_patrickgold_florisboard_rmr_RmrNativeBridge_nativeSelfTest(
        JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    rmr_ime_state state = {0};
    rmr_ime_decision decision = {0};
    rmr_ime_event event = {3u, 1u, RMR_OP_TEXT_META, 2u};
    rmr_ime_reset(&state, 3u, 0u);
    if (state.abi != RMR_IME_ABI) return 1;
    if (rmr_ime_route(&state, &event, &decision) != RMR_OK ||
        decision.backend != RMR_BACKEND_SCALAR) return 2;
    if (rmr_ime_route(&state, &event, &decision) != RMR_REJECT) return 3;
    rmr_ime_reset(&state, 4u, RMR_FLAG_SENSITIVE_FIELD);
    event.epoch = 4u;
    if (rmr_ime_route(&state, &event, &decision) != RMR_FALLBACK ||
        decision.backend != RMR_BACKEND_PLATFORM) return 4;
    {
        static const uint8_t ascii[] = {0x41u, 0x7Fu, 0u};
        uint32_t is_ascii = 0u, backend = 0u;
        if (rmr_ime_ascii_probe(ascii, 3u, 0u, &is_ascii, &backend) != RMR_OK ||
            is_ascii != 1u || backend != RMR_BACKEND_SCALAR) return 5;
    }
    return 0;
}
