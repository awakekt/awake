#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks scripts/stop-build-daemons.sh against stand-in daemons: sleeps named as a Gradle and a Kotlin
# daemon, and a fake wrapper whose --stop ends the Gradle one.
set -euo pipefail

root="$(mktemp -d)"
pids=()
cleanup() { kill "${pids[@]}" 2> /dev/null || true; rm -rf "$root"; }
trap cleanup EXIT
mkdir -p "$root/scripts"
cp "$(dirname "${BASH_SOURCE[0]}")/stop-build-daemons.sh" "$root/scripts/"
# Stand-ins only, so a real daemon on this machine is neither stopped nor counted.
export GRADLE_DAEMON="org.gradle.launcher.daemon.bootstrap.GradleDaemon test-stand-in"
export KOTLIN_DAEMON="org.jetbrains.kotlin.daemon.KotlinCompileDaemon test-stand-in"

failures=0
check() { # name expected-status [STOP_DAEMONS_WAIT]
  local status=0 out
  out="$(STOP_DAEMONS_WAIT="${3:-10}" "$root/scripts/stop-build-daemons.sh" 2>&1)" || status=$?
  if [[ $status -eq $2 ]]; then echo "ok   $1"; else echo "FAIL $1: exit $status, expected $2: $out"; failures=$((failures + 1)); fi
}
spawn() { # pattern -> a stand-in process whose command line matches it
  (exec -a "$1" sleep 300) &
  pids+=($!)
}
running() { pgrep -f "test-stand-in" > /dev/null; }

printf '#!/usr/bin/env bash\npkill -f "%s" || true\n' "$GRADLE_DAEMON" > "$root/gradlew"
chmod +x "$root/gradlew"

check "no daemon running" 0

spawn "$GRADLE_DAEMON"
spawn "$KOTLIN_DAEMON"
check "a Gradle and a Kotlin daemon are both stopped" 0
if running; then echo "FAIL a stand-in daemon survived"; failures=$((failures + 1)); else echo "ok   none survives"; fi

# A wrapper that stops nothing leaves the Gradle daemon running: the script fails, naming it.
printf '#!/usr/bin/env bash\nexit 0\n' > "$root/gradlew"
spawn "$GRADLE_DAEMON"
check "a daemon still running after the wait fails the script" 1 2

[[ $failures -eq 0 ]] || { echo "$failures case(s) failed"; exit 1; }
