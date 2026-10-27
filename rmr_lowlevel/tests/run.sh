#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="${TMPDIR:-/tmp}/rmr-ime-test-$$"
trap 'rm -f "$OUT"' EXIT HUP INT TERM
"${CC:-cc}" -std=c11 -O2 -ffreestanding -fno-builtin -Wall -Wextra -Werror \
  -I"$ROOT/include" "$ROOT/src/rmr_ime.c" "$ROOT/tests/host_test.c" -o "$OUT"
"$OUT"
printf 'PASS: RMR scalar ABI, sequence, budgets, sensitive-field fallback, ASCII\n'
