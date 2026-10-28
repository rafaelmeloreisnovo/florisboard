# S2 — Bounded C11 core + disabled diagnostic JNI

State: IMPLEMENTED_UNTESTED_ON_DEVICE. FlorisBoard upstream Apache-2.0/license and notices remain unchanged; own authorship and commercial restrictions require human/legal review.

This revision creates separate app-native `librmr_ime_probe.so` via NDK/CMake and an internal Kotlin JNI diagnostic. `ENABLED_FOR_IME=false`; no real IME dispatch, lifecycle, Rust library or InputConnection changes. No typed text is passed to JNI, stored or logged. Missing library returns a negative diagnostic and the original IME remains unaffected. No GUI or event interception.

Synchronous core now limits the ASCII inspection buffer to `RMR_PROBE_MAX_BYTES=4096` before pointer access. SIMD is compile-gated and available only when the caller has proved the CPU capability; this JNI diagnostic never passes actual user input or enables NEON. Event and fault counters are two bounded guards, NOT independent timed watchdogs.

Run `sh rmr_lowlevel/tests/run.sh` and `sh rmr_lowlevel/tests/run_extended.sh` (with JAVA_HOME set for the host JNI header test). Android test source `RmrNativeBridgeInstrumentedTest` executes JNI smoke on device/emulator. `assembleDebugAndroidTest` compiles but does not run it. Release/runtime claims remain HOLD until exact-head Android APK/ABI/signer evidence, physical execution, Unicode/IME accessibility/password tests and independent review.

Rollback: revert S2 paths or close PR#64. No merge, no mainline runtime change. GPU, NUMA and latency/battery superiority are TOKEN_VAZIO.
