#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks that Swappy (games-frame-pacing's libswappy_static.a, #1699) is linked into each built
# libawake-vulkan.so as a C library and stays inside it:
#
#   1. every SwappyVk JNI entry point the Kotlin object declares is exported;
#   2. Swappy itself is linked (SwappyVk_queuePresent is in the library's own symbol table);
#   3. no Swappy symbol is exported, so none can bind to another copy an app loads;
#   4. no std::__ndk1 symbol is imported: no C++ runtime type comes from another library;
#   5. the library does not need libc++_shared.so.
#
# Usage: scripts/swappy-symbols.sh <directory to search for libawake-vulkan.so>...
# The NDK's llvm-nm and llvm-readelf are found under $ANDROID_NDK_HOME, else the newest NDK under
# $ANDROID_HOME/ndk. Exits 1 on the first library that fails, naming the check.
set -euo pipefail

[[ $# -gt 0 ]] || { echo "usage: $0 <dir>..." >&2; exit 2; }

ndk="${ANDROID_NDK_HOME:-}"
if [[ -z "$ndk" ]]; then
  ndk=$(find "${ANDROID_HOME:-/opt/android-sdk}/ndk" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -1)
fi
bin=$(find "$ndk/toolchains/llvm/prebuilt" -mindepth 2 -maxdepth 2 -name bin -type d | head -1)
nm="$bin/llvm-nm" readelf="$bin/llvm-readelf"
[[ -x "$nm" && -x "$readelf" ]] || { echo "no llvm-nm/llvm-readelf under $ndk" >&2; exit 2; }

repo=$(cd "$(dirname "$0")/.." && pwd)
kotlin="$repo/awake/backend/vulkan/bindings/src/androidMain/kotlin/com/awakekt/awake/vulkan/swappy/SwappyVk.kt"
mapfile -t entries < <(grep -o 'external fun [A-Za-z]*' "$kotlin" | awk '{print "Java_com_awakekt_awake_vulkan_swappy_SwappyVk_" $3}')
[[ ${#entries[@]} -gt 0 ]] || { echo "no external functions found in $kotlin" >&2; exit 2; }

mapfile -t libraries < <(find "$@" -name libawake-vulkan.so -type f | sort)
[[ ${#libraries[@]} -gt 0 ]] || { echo "no libawake-vulkan.so under $*" >&2; exit 1; }

fail() { echo "FAIL $1: $2" >&2; exit 1; }

for so in "${libraries[@]}"; do
  exported=$("$nm" -D --defined-only "$so" | awk '{print $3}')
  for entry in "${entries[@]}"; do
    grep -qx "$entry" <<< "$exported" || fail "$so" "JNI entry point $entry is not exported"
  done
  own=$("$nm" "$so")
  grep -q ' SwappyVk_queuePresent$' <<< "$own" || fail "$so" "Swappy is not linked (no SwappyVk_queuePresent)"
  leaked=$(grep -v '^Java_' <<< "$exported" | grep -c -i 'swappy' || true)
  [[ "$leaked" -eq 0 ]] || fail "$so" "$leaked Swappy symbols are exported; link with --exclude-libs"
  imported=$("$nm" -D --undefined-only "$so" | grep -c '__ndk1' || true)
  [[ "$imported" -eq 0 ]] || fail "$so" "$imported std::__ndk1 symbols are imported from another library"
  needed=$("$readelf" -d "$so")
  if grep -q 'libc++_shared.so' <<< "$needed"; then fail "$so" "needs libc++_shared.so"; fi
  echo "ok $so: ${#entries[@]} entry points, Swappy linked and hidden, no shared C++ runtime"
done
