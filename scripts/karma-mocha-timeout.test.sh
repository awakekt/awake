#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks that Mocha's timeout for the wasmJs browser tests is set in one place, the browser-tests
# convention (build-logic), and reaches every project of the Core build that runs tests in a browser.
# Seven modules had grown their own karma.config.d copy, each after a finished test went red with
# "Timeout of 2000ms exceeded" (#1592); a module without one was still exposed.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
failures=0
fail() { echo "FAIL $1"; failures=$((failures + 1)); }

own="$(cd "$root" && { git grep -ln 'client\.mocha' -- '*karma.config.d/*.js' || true; })"
if [[ -n "$own" ]]; then fail "a module sets Mocha's options in its own karma.config.d:"; echo "$own"
else echo "ok   no module's karma.config.d sets Mocha's options"; fi

conventions=(library application)
for convention in "${conventions[@]}"; do
  file="$root/build-logic/src/main/kotlin/com.awakekt.awake.plugin.$convention.gradle.kts"
  grep -qF 'id("com.awakekt.awake.plugin.browser-tests")' "$file" || fail "the $convention convention does not apply browser-tests"
done

# Every project the Core build includes that declares a browser target gets the convention, directly
# or through the library or application convention.
missing=0
while IFS= read -r path; do
  build="$root/${path//://}/build.gradle.kts"
  [[ -f "$build" ]] || continue
  grep -qE 'browser *(\(|\{)' "$build" || continue
  if ! grep -qE 'id\("com\.awakekt\.awake\.plugin\.(library|application|browser-tests)"\)' "$build"; then
    fail "${build#"$root/"} has a browser target without the browser-tests convention"; missing=1
  fi
done < <(sed -n 's/^include("\(:[^"]*\)").*/\1/p' "$root/settings.gradle.kts" | sed 's/^://')
(( missing )) || echo "ok   every browser target in the Core build gets the shared Mocha timeout"

[[ $failures -eq 0 ]] || { echo "$failures case(s) failed"; exit 1; }
