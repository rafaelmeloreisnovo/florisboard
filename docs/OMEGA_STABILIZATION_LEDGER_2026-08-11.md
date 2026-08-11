# FlorisBoard Ω Stabilization Ledger — 2026-08-11

Status: `IN_PROGRESS / claim_allowed=false`

Branch: `stabilize/omega-crash-coherence-20260811`
PR: `#58` (draft)
Base: `main@c15130326dc1e5840adbd7f8b912eaf9d5434b1b`

## Contract

This ledger separates implementation, execution evidence and remaining unknowns.

- A code change is not proof that the crash is gone.
- A successful CI build is not proof of behavior on a physical Android device.
- `TOKEN_VAZIO` is preserved when evidence is not yet available.
- The PR remains draft until build/test/artifact gates are green and physical crash evidence is reconciled.

## Proven failures discovered

1. ARM64 workflow referenced missing `scripts/setup_rust_env.sh`.
2. ARM64 workflow wrote raw PATH entries into `$GITHUB_ENV` instead of `$GITHUB_PATH`.
3. Two independent Gradle paths failed to resolve orphan JetPref snapshot `20251119T222500Z-SNAPSHOT`.
4. APK workflows referenced nonexistent Gradle task `:app:fetchAssets`.
5. `lib:zipraf-omega` JVM module referenced `android.util.Log`.
6. `RiskMitigationModule.kt` contained two interleaved process-registration algorithms, a malformed `mapOf`, duplicate `runTaskAsProcess()` methods and broken braces.
7. Zombie cleanup removed logical processes without returning their semaphore permits.
8. `tryOrNull()` declared an `EXACTLY_ONCE` contract rejected by Kotlin 2.2.20 and swallowed all `Throwable` values.
9. Preference store initialization reported `preferenceStoreLoaded=true` even after initialization failure.
10. `ImeStateSnapshotStore.restore()` could throw `ClassCastException` for malformed/legacy `SharedPreferences` primitive types.
11. `CrashUtility` staged exception handoff was non-atomic and called `uncaughtException(null, e)`.
12. Release crash decoding artifacts (`mapping.txt` / native symbols) were not preserved by the ARM64 evidence pipeline.

## Applied remediations

- Restored Kotlin runtime null-safety assertions.
- Enabled full native symbols in debug and symbol table retention in release.
- Reworked Devtools logcat collection so timeout covers both child process and blocking pipe reader.
- Canonicalized JDK/SDK/Build Tools/NDK/CMake/Rust from repository version files.
- Added checksum verification and fail-closed SDK installation.
- Replaced orphan JetPref snapshot with upstream-aligned stable `0.3.0`.
- Repaired modern CMake Rust target setup.
- Rebuilt ARM64 verifier as a fail-closed executable contract: valid ZIP, manifest, resources, DEX, one ABI (`arm64-v8a`), `libfl_native.so`, zipalign, SDK metadata, unsigned contract, SHA-256.
- Rebuilt ZIPRAF process tracking around one lock/permit invariant and added regression tests for duplicate IDs, slot recycling and reset capacity.
- Made interoperability path search pure JVM and bounded against cycles/path explosion.
- Made optional IME runtime snapshots fail safe on malformed/storage state.
- Added bounded persistent `HandledFaultRecorder` evidence for caught initialization faults.
- Made staged crash handoff atomic/non-null.
- Reduced CI to three responsibilities: Gradle Verify, unsigned APK, ARM64 verified APK.
- Added `concurrency.cancel-in-progress` so superseded PR runs do not become CI zombies.
- Removed legacy/redundant Android and unsigned APK workflows.
- Added R8 mapping and native symbol preservation to ARM64 artifacts.

## Evidence already obtained

- Legacy debug CI reached `SUCCESS` after JetPref/CMake/Kotlin repairs, proving the debug compile path was restored at that checkpoint.
- Earlier broad `clean build` progressed far enough to isolate the remaining compile blocker specifically in `lib:zipraf-omega`; those source errors have since been repaired.
- Existing subtype restoration verifies that a persisted subtype ID still exists before applying it.
- `ImeUiMode.fromInt()` and `KeyboardMode.fromInt()` already degrade unknown integer states to safe defaults.

## Current gates

The latest HEAD must independently satisfy:

1. `Gradle Verify`: clean build + lint + unit tests.
2. `Build APK (unsigned)`: debug and release APK assembly.
3. `Build and Verify ARM64 APK`: canonical toolchain + release build + fail-closed APK verification + R8/native crash-decoding artifacts.

No earlier run is allowed to promote the current HEAD.

## TOKEN_VAZIO — evidence still required

- Exact stacktrace/tombstone for every currently observed physical-device crash.
- Reproduction frequency and trigger sequence on the target Android 15 ARM64 device.
- Installation/launch/IME-switch typing stability for the APK produced by the current stabilization HEAD.
- Native crash symbolization against a real tombstone, if a native crash occurs.
- Long-run lifecycle/Doze/OEM-kill behavior under the target device firmware.
- Safe reconciliation strategy for the fork's custom changes versus newer upstream commits; no blind upstream merge.

## Promotion rule

`claim_allowed=true` is forbidden until CI gates are green **and** physical device evidence demonstrates that the relevant crash reproductions are closed or explicitly catalogued as remaining `TOKEN_VAZIO`.
