#!/usr/bin/env bash
# FlorisBoard ARM64 release builder and fail-closed verifier.
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

APK_OUTPUT_DIR="app/build/outputs/apk/release"
BUILD_LOG="build_process.log"
VERIFY_LOG="apk_verification.log"
REPORT="build_verification_report.md"

: > "$BUILD_LOG"
: > "$VERIFY_LOG"

log() {
  printf '[%s] %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$*" | tee -a "$BUILD_LOG"
}

fail() {
  log "FAIL: $*"
  printf 'FAIL: %s\n' "$*" >> "$VERIFY_LOG"
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

read_property() {
  local key="$1"
  sed -n "s/^${key}=//p" gradle.properties | head -n1
}

read_toml_version() {
  local key="$1"
  sed -nE "s/^${key}[[:space:]]*=[[:space:]]*\"([^\"]+)\"/\1/p" gradle/tools.versions.toml | head -n1
}

EXPECTED_ABIS=(arm64-v8a armeabi-v7a)

VERSION_CODE="$(read_property projectVersionCode)"
VERSION_NAME="$(read_property projectVersionName)"
MIN_SDK="$(read_property projectMinSdk)"
TARGET_SDK="$(read_property projectTargetSdk)"
COMPILE_SDK="$(read_property projectCompileSdk)"
BUILD_TOOLS_VERSION="$(read_toml_version buildTools)"
NDK_VERSION="$(read_toml_version ndk)"
CMAKE_VERSION="$(read_toml_version cmake)"

for value in VERSION_CODE VERSION_NAME MIN_SDK TARGET_SDK COMPILE_SDK BUILD_TOOLS_VERSION NDK_VERSION CMAKE_VERSION; do
  [ -n "${!value}" ] || fail "missing canonical configuration: $value"
done

if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
  export PATH="$ANDROID_SDK_ROOT/build-tools/$BUILD_TOOLS_VERSION:$ANDROID_SDK_ROOT/platform-tools:$PATH"
fi

for cmd in java unzip sha256sum zipalign aapt apksigner; do
  require_cmd "$cmd"
done

[ -x ./gradlew ] || fail "Gradle wrapper is not executable"
[ -f app/src/main/AndroidManifest.xml ] || fail "AndroidManifest.xml is missing"
for expected_abi in "${EXPECTED_ABIS[@]}"; do
  grep -Fq "$expected_abi" app/build.gradle.kts || fail "app build config is missing $expected_abi"
done

log "Canonical config: version=$VERSION_NAME($VERSION_CODE) minSdk=$MIN_SDK targetSdk=$TARGET_SDK compileSdk=$COMPILE_SDK buildTools=$BUILD_TOOLS_VERSION ndk=$NDK_VERSION cmake=$CMAKE_VERSION"
log "Cleaning and building unsigned Android ARM release APK"
./gradlew --no-daemon clean :app:assembleRelease -PuserlandUnsignedApk=true 2>&1 | tee -a "$BUILD_LOG"

[ -d "$APK_OUTPUT_DIR" ] || fail "APK output directory not found: $APK_OUTPUT_DIR"
shopt -s nullglob
apks=("$APK_OUTPUT_DIR"/*.apk)
[ "${#apks[@]}" -gt 0 ] || fail "no release APK generated"

cat > "$REPORT" <<EOF
# FlorisBoard Android ARM Build Verification Report

- **Result:** PENDING
- **Version:** $VERSION_NAME ($VERSION_CODE)
- **Min SDK:** $MIN_SDK
- **Target SDK:** $TARGET_SDK
- **Compile SDK:** $COMPILE_SDK
- **Architecture contract:** ${EXPECTED_ABIS[*]}
- **NDK:** $NDK_VERSION
- **CMake:** $CMAKE_VERSION
- **Build Tools:** $BUILD_TOOLS_VERSION

## Artifacts
EOF

entry_exists() {
  local wanted="$1"
  local entry
  for entry in "${entries[@]}"; do
    [ "$entry" = "$wanted" ] && return 0
  done
  return 1
}

for apk in "${apks[@]}"; do
  apk_name="$(basename "$apk")"
  log "Verifying $apk_name"

  unzip -t "$apk" >/dev/null || fail "$apk_name has invalid ZIP structure"

  mapfile -t entries < <(unzip -Z1 "$apk")
  entry_exists 'AndroidManifest.xml' || fail "$apk_name has no AndroidManifest.xml"
  entry_exists 'resources.arsc' || fail "$apk_name has no resources.arsc"

  dex_count="$(printf '%s\n' "${entries[@]}" | grep -Ec '^classes([0-9]+)?\.dex$' || true)"
  [ "$dex_count" -gt 0 ] || fail "$apk_name contains no classes*.dex"

  mapfile -t abis < <(printf '%s\n' "${entries[@]}" | awk -F/ '$1 == "lib" && NF >= 3 {print $2}' | sort -u)
  [ "${#abis[@]}" -eq "${#EXPECTED_ABIS[@]}" ] || fail "$apk_name ABI count mismatch; found: ${abis[*]:-none}"
  for expected_abi in "${EXPECTED_ABIS[@]}"; do
    entry_exists "lib/${expected_abi}/libfl_native.so" || fail "$apk_name is missing lib/${expected_abi}/libfl_native.so"
    found_expected_abi=false
    for abi in "${abis[@]}"; do
      if [ "$abi" = "$expected_abi" ]; then
        found_expected_abi=true
        break
      fi
    done
    [ "$found_expected_abi" = true ] || fail "$apk_name is missing expected ABI: $expected_abi"
  done

  zipalign -c -p 4 "$apk" >/dev/null || fail "$apk_name failed zipalign verification"

  badging="$(aapt dump badging "$apk")" || fail "aapt could not inspect $apk_name"
  grep -q "sdkVersion:'${MIN_SDK}'" <<< "$badging" || fail "$apk_name minSdk does not match $MIN_SDK"
  grep -q "targetSdkVersion:'${TARGET_SDK}'" <<< "$badging" || fail "$apk_name targetSdk does not match $TARGET_SDK"

  if apksigner verify "$apk" >/dev/null 2>&1; then
    fail "$apk_name is signed although unsigned artifact was requested"
  fi

  sha256="$(sha256sum "$apk" | awk '{print $1}')"
  size_bytes="$(stat -c '%s' "$apk")"

  {
    printf 'PASS: %s\n' "$apk_name"
    printf '  sha256=%s\n' "$sha256"
    printf '  bytes=%s\n' "$size_bytes"
    printf '  dex_count=%s\n' "$dex_count"
    printf '  abis=%s\n' "${abis[*]}"
  } >> "$VERIFY_LOG"

  cat >> "$REPORT" <<EOF

### $apk_name
- SHA-256: \`$sha256\`
- Bytes: $size_bytes
- DEX files: $dex_count
- ABIs: \`${abis[*]}\`
- Native library: \`libfl_native.so\` present for each ABI
- ZIP structure: PASS
- zipalign: PASS
- SDK metadata: PASS
- Signature contract: unsigned PASS
EOF
done

sed -i 's/\*\*Result:\*\* PENDING/**Result:** PASS/' "$REPORT"
log "All Android ARM APK verification gates passed"
cat "$REPORT"
