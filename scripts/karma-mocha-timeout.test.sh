#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks that Mocha's timeout and the logging for the wasmJs browser tests are set in one place, the
# browser-tests convention (build-logic), and reach every project of the Core build that runs tests in
# a browser.
# Seven modules had grown their own karma.config.d copy, each after a finished test went red with
# "Timeout of 2000ms exceeded" (#1592); a module without one was still exposed.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
failures=0
fail() { echo "FAIL $1"; failures=$((failures + 1)); }

own="$(cd "$root" && { git grep -ln 'client\.mocha' -- '*karma.config.d/*.js' || true; })"
if [[ -n "$own" ]]; then fail "a module sets Mocha's options in its own karma.config.d:"; echo "$own"
else echo "ok   no module's karma.config.d sets Mocha's options"; fi

# The convention also sets what a browser test run logs: each test named as it passes, its output and
# its full assertion. Five modules had their own copy; a module without one ran in silence (#1512).
plugin="$root/build-logic/src/main/kotlin/com.awakekt.awake.plugin.browser-tests.gradle.kts"
grep -qF 'showStandardStreams = true' "$plugin" || fail "the browser-tests convention does not show the tests' output"
# A block spans lines, so each build file is read whole.
own_logging="$(cd "$root" && git ls-files -- '*.gradle.kts' ':!build-logic' \
  | xargs perl -0777 -ne 'print "$ARGV\n" if /"wasmJsBrowserTest"\)\s*\{[^}]*testLogging/s')"
if [[ -n "$own_logging" ]]; then fail "a module sets its own wasmJsBrowserTest logging:"; echo "$own_logging"
else echo "ok   no module sets its own browser test logging"; fi

conventions=(kmp-library application)
for convention in "${conventions[@]}"; do
  file="$root/build-logic/src/main/kotlin/com.awakekt.awake.plugin.$convention.gradle.kts"
  grep -qF 'id("com.awakekt.awake.plugin.browser-tests")' "$file" || fail "the $convention convention does not apply browser-tests"
done
grep -qF 'id("com.awakekt.awake.plugin.kmp-library")' "$root/build-logic/src/main/kotlin/com.awakekt.awake.plugin.library.gradle.kts" \
  || fail "the library convention does not apply kmp-library (and therefore browser-tests)"

# Every project the Core build includes that declares a browser target gets the convention, directly
# or through the kmp-library, library, or application convention.
missing=0
while IFS= read -r path; do
  build="$root/${path//://}/build.gradle.kts"
  [[ -f "$build" ]] || continue
  grep -qE 'browser *(\(|\{)' "$build" || continue
  if ! grep -qE 'id\("com\.awakekt\.awake\.plugin\.(kmp-library|library|application|browser-tests)"\)' "$build"; then
    fail "${build#"$root/"} has a browser target without the browser-tests convention"; missing=1
  fi
done < <(sed -n 's/^include("\(:[^"]*\)").*/\1/p' "$root/settings.gradle.kts" | sed 's/^://')
(( missing )) || echo "ok   every browser target in the Core build gets the shared Mocha timeout"

[[ $failures -eq 0 ]] || { echo "$failures case(s) failed"; exit 1; }
