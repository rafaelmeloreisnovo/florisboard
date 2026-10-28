#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="${TMPDIR:-/tmp}/rmr-ext-$$"
trap 'rm -f "$OUT" "$OUT.jni"' EXIT HUP INT TERM
for COMPILER in cc clang; do
  "$COMPILER" -std=c11 -O2 -ffreestanding -fno-builtin -Wall -Wextra -Werror \
    -I"$ROOT/include" "$ROOT/src/rmr_ime.c" "$ROOT/tests/negative_probe_test.c" -o "$OUT"
  "$OUT"
done
if [ -n "${JAVA_HOME:-}" ] && [ -f "${JAVA_HOME}/include/jni.h" ]; then
  cc -std=c11 -Wall -Wextra -Werror -I"$ROOT/include" \
    -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
    "$ROOT/src/rmr_ime.c" "$ROOT/../app/src/main/cpp/rmr_ime/rmr_ime_jni.c" \
    "$ROOT/tests/jni_host_test.c" -o "$OUT.jni"
  "$OUT.jni"
  printf 'PASS: host JNI C ABI smoke\n'
fi
printf 'PASS: negative bounded probe tests\n'
