#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Runs a Gradle command, and runs it once more after a pause when it failed resolving a dependency
# (#1592). A green command runs exactly once and pays nothing.
#
#   scripts/gradle-retry.sh ./gradlew :awake:compose:ui:desktopTest
#   scripts/gradle-retry.sh xvfb-run -a ./gradlew :awake:backend:webgpu:desktopTest
#
# Two kinds of failure are retried, told apart by what Gradle printed:
# - A miss ("Could not find", "was not found in any of the following sources"). Gradle records a 404
#   as missing and trusts that for 24 hours, a missing module indefinitely, so the retry adds
#   --refresh-dependencies, which drops those records. It re-checks every module's metadata.
# - An error ("Could not resolve", "status code 429"). Gradle records no miss for it, so the retry
#   is the same command. It does not refresh, which would send a throttling host more requests.
#
# Anything else fails as it did. stdout and stderr stay separate streams, so a command substitution
# of `-q` output still captures only stdout. GRADLE_RETRY_PAUSE_SECONDS sets the pause (default 60:
# Central has throttled these runners for longer than Gradle's own retries span).
set -uo pipefail

[[ $# -gt 0 ]] || { echo "usage: $0 <command> [args...]" >&2; exit 64; }

pause="${GRADLE_RETRY_PAUSE_SECONDS:-60}"
log="$(mktemp)"
trap 'rm -f "$log"' EXIT

MISS='Could not find [^ ]|was not found in any of the following sources'
ERROR="Could not resolve|status code 429|Received status code 5[0-9][0-9]"

# Runs "$@" with each stream teed into $log and kept on its own descriptor, and exits with its status.
run() (
  : > "$log"
  { { "$@" 2>&1 1>&4 | tee -a "$log" >&3; exit "${PIPESTATUS[0]}"; } 4>&1 | tee -a "$log"; exit "${PIPESTATUS[0]}"; } 3>&2
)

run "$@"
status=$?
[[ $status -eq 0 ]] && exit 0

if grep -qE "$MISS" "$log"; then
  kind="a missing dependency"
  set -- "$@" --refresh-dependencies
elif grep -qE "$ERROR" "$log"; then
  kind="a repository error"
else
  exit "$status"
fi

echo "::warning title=Dependency resolution retried::The command failed on $kind; running it once more in ${pause}s: $*" >&2
sleep "$pause"
run "$@"
