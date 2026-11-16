#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks the order of .github/actions/ios-native's cached native builds: restore, build only on a
# miss, verify what is there either way, then save only a verified miss. A save before the
# verification, or a build that ignores the restore, is the mistake this catches. JoltC's device and
# simulator slices are cached apart, each under a key and a path naming only its own slice, so a
# restore cannot put one slice's library where the other's link reads it.
set -euo pipefail

action="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.github/actions/ios-native/action.yml"
failures=0
fail() { echo "FAIL $1"; failures=$((failures + 1)); }

line_of() { # step-name -> its line number, empty when absent
  { grep -nF -- "- name: $1" "$action" || true; } | head -1 | cut -d: -f1
}

step() { # line -> the step's text, up to the next step
  awk -v s="$1" 'NR == s { print; next } NR > s && /^    - name:/ { exit } NR > s { print }' "$action"
}

check() { # name restore build verify save cache-id must-contain... [-- must-not-contain...]
  local name=$1 restore build verify save id=$6
  restore="$(line_of "$2")" build="$(line_of "$3")" verify="$(line_of "$4")" save="$(line_of "$5")"
  shift 6
  if [[ -z "$restore" || -z "$build" || -z "$verify" || -z "$save" ]]; then
    fail "$name: a step is missing (restore '${restore}', build '${build}', verify '${verify}', save '${save}')"
    return
  fi
  if (( restore < build && build < verify && verify < save )); then echo "ok   $name: restore, build, verify, save"
  else fail "$name: steps out of order"; fi
  step "$build" | grep -m1 'if:' | grep -qF "steps.$id.outputs.cache-hit != 'true'" || fail "$name: the build ignores the restore"
  step "$save" | grep -m1 'if:' | grep -qF "steps.$id.outputs.cache-hit != 'true'" || fail "$name: the save runs on a cache hit"
  local restored saved forbidden=0 part
  restored="$(step "$restore")" saved="$(step "$save")"
  for part in "$@"; do
    if [[ "$part" == -- ]]; then forbidden=1; continue; fi
    if (( forbidden )); then
      [[ "$restored$saved" != *"$part"* ]] || fail "$name: the cache names $part"
    else
      [[ "$(grep -m1 'key:' <<< "$restored")" == *"$part"* ]] || fail "$name: the key leaves out $part"
    fi
  done
}

check MoltenVK "Restore the MoltenVK build" "Build MoltenVK for iOS" "Verify the xcframework carries both slices" \
  "Save the verified MoltenVK build" moltenvk-cache 'runner.arch' 'steps.pins.outputs.moltenvk'
recipe=('steps.pins.outputs.joltc' 'awake/backend/jolt/build.gradle.kts' 'NativeCMake.kt' 'runner.arch')
check "JoltC device" "Restore the JoltC device build" "Build JoltC for iOS" "Verify the JoltC libraries for both slices" \
  "Save the verified JoltC device build" joltc-device "${recipe[@]}" iosArm64 -- iosSimulatorArm64 'joltc-native/*'
check "JoltC simulator" "Restore the JoltC simulator build" "Build JoltC for iOS" "Verify the JoltC libraries for both slices" \
  "Save the verified JoltC simulator build" joltc-simulator "${recipe[@]}" iosSimulatorArm64 -- iosArm64 'joltc-native/*'

[[ $failures -eq 0 ]] || { echo "$failures case(s) failed"; exit 1; }
