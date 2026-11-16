#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks the order of .github/actions/ios-native's cached native builds: for MoltenVK and JoltC,
# restore, build only on a miss, verify what is there either way, then save only a verified miss.
# A save before the verification, or a build that ignores the restore, is the mistake this catches.
set -euo pipefail

action="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.github/actions/ios-native/action.yml"
failures=0

line_of() { # step-name -> its line number, empty when absent
  { grep -nF -- "- name: $1" "$action" || true; } | head -1 | cut -d: -f1
}

check() { # library restore build verify save cache-id key-part...
  local name=$1 restore build verify save
  restore="$(line_of "$2")" build="$(line_of "$3")" verify="$(line_of "$4")" save="$(line_of "$5")"
  local id=$6
  shift 6
  if [[ -z "$restore" || -z "$build" || -z "$verify" || -z "$save" ]]; then
    echo "FAIL $name: a step is missing (restore '${restore}', build '${build}', verify '${verify}', save '${save}')"
    failures=$((failures + 1))
    return
  fi
  if (( restore < build && build < verify && verify < save )); then echo "ok   $name: restore, build, verify, save"; else
    echo "FAIL $name: steps out of order"; failures=$((failures + 1)); fi
  for step in "$build" "$save"; do
    if sed -n "$((step + 1)),$((step + 2))p" "$action" | grep -qF "if: steps.$id.outputs.cache-hit != 'true'"; then :; else
      echo "FAIL $name: the step at line $step runs on a cache hit"; failures=$((failures + 1)); fi
  done
  local key
  key="$(sed -n "$restore,$build p" "$action" | grep -m1 'key:')"
  for part in "$@"; do
    if [[ "$key" == *"$part"* ]]; then :; else echo "FAIL $name: the key leaves out $part"; failures=$((failures + 1)); fi
  done
}

check MoltenVK "Restore the MoltenVK build" "Build MoltenVK for iOS" "Verify the xcframework carries both slices" \
  "Save the verified MoltenVK build" moltenvk-cache 'runner.arch' 'steps.pins.outputs.moltenvk'
check JoltC "Restore the JoltC build" "Build JoltC for iOS" "Verify the JoltC libraries for both slices" \
  "Save the verified JoltC build" joltc-cache 'runner.arch' 'steps.pins.outputs.joltc' \
  'awake/backend/jolt/build.gradle.kts' 'NativeCMake.kt'

[[ $failures -eq 0 ]] || { echo "$failures case(s) failed"; exit 1; }
