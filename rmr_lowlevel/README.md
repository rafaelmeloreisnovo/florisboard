# RAFAELIA RMR low-level IME candidate — isolated v0.1

**State:** experimental, host-testable, NOT wired to the live FlorisBoard IME or shipped APK.

## Boundary and authority

FlorisBoard upstream remains Apache-2.0 and third-party. The inherited IME, Android service, app resources, Rust/JNI layer and existing build are unchanged. This is a new bounded C11 candidate written separately from upstream structures; authorship has not been independently reviewed. See `../LICENSE`, `../NOTICE.RAFAELIA.md`, `../THIRD_PARTY_PROVENANCE.md` and `LICENSES/README.md`.

## Interface

- `rmr_ime_reset`: start a new epoch; no allocation, OS call or hidden global state.
- `rmr_ime_route`: synchronous metadata-only policy decision. It NEVER commits actual text. On rejection, watchdog or fallback, the existing Android IME must remain the sole execution path. Caller is responsible for handling the event exactly once, even on fallback.
- `rmr_ime_ascii_probe`: optional vectorized **ASCII classification**, not text prediction or GPU acceleration. Only call with a caller-proven NEON feature bit; compile-time guards still apply. No input is retained.
- Two bounded synchronous counters: 4096 operations per epoch and 3 consecutive fallback decisions. They are watchdog **guards**, not independent real-time watchdog processes.
- The API is single-thread-owned; do not share mutable state concurrently. Network, input persistence, telemetry, GPU, NUMA and CPU affinity are absent.

## Run targeted host tests

`sh rmr_lowlevel/tests/run.sh`

The `-ffreestanding -fno-builtin` flags constrain the C core but do **not** make an Android shared library or the app independent of its loader/ART/Android ABI. Compiler runtime/ELF imports, JNI lifecycle and actual device capability remain unverified until later gates.

## Promotion gates

1. Human review of provenance, license/NOTICE and Android IME `InputConnection` semantics.
2. Repeat scalar tests with GCC/Clang plus UBSan/ASan, and cross-compile armv7 + aarch64 **objects**.
3. On-device NEON-vs-scalar differential tests on actual supported CPUs; test Android 10/14/15 accessibility, password fields, IME switching and locale/Unicode.
4. Integrate behind a disabled-by-default Android flag only after the tests; dual watchdog must never drop a keystroke. Run exact-head CI and APK/device tests before merge.

Rollback: close the unmerged draft PR/discard the isolated branch; production `main` has no runtime changes. No default GPU selection, no hardware NUMA claim and no unverified optimization claims.
