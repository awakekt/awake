#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Runs a Gradle command, and runs it again after a pause when it failed resolving a dependency, up to
# three more times with growing pauses (#1592). A green command runs exactly once and pays nothing.
#
#   scripts/gradle-retry.sh ./gradlew :awake:compose:ui:desktopTest
#   scripts/gradle-retry.sh xvfb-run -a ./gradlew :awake:backend:webgpu:desktopTest
#
# Two kinds of failure are retried, told apart by what Gradle printed:
# - A miss ("Could not find", "was not found in any of the following sources").
# - An error ("Could not resolve", "status code 429", a 5xx).
# A retry is the same command, except after a missing file: "Could not find x-1.0.klib (g:x:1.0)", a
# file 404 behind metadata a repository served. Gradle caches that miss and asks only the repository
# that served the metadata, so the same command can never recover it; the retry adds
# --refresh-dependencies, kept on every later retry. A missing module or plugin ("Could not find
# g:x:1.0.") is asked again by the same command, so it is not refreshed: a refresh re-requests every
# module the last run had cached, and on 20 Nov that sent each retry to a Central edge answering 404
# for files Central serves, failing it sooner (#1592). Measured locally: see "Dependency resolution
# retries" in docs/release-process.md.
# Every retry runs with --info, so a failure that outlasts them leaves the host and HTTP status
# Gradle met in the log; a command that passes first time is never verbose.
#
# The last retry also passes scripts/central-mirror-first.init.gradle.kts, which lists Google's mirror of
# Maven Central ahead of every repository: a file a Central edge answers 404 for, behind a POM it
# served, is asked only of Central, so a mirror listed after Central can't serve it; first, it serves
# the POM and the file both. Earlier tries keep the builds' own order, Central first.
#
# Anything else fails as it did, on whichever run it happens. stdout and stderr stay separate
# streams, so a command substitution of `-q` output still captures only stdout.
# GRADLE_RETRY_PAUSES lists the pauses in seconds, one per retry (default "60 180 300": Central has
# throttled these runners for longer than Gradle's own retries span, and for longer than a minute).
set -uo pipefail

[[ $# -gt 0 ]] || { echo "usage: $0 <command> [args...]" >&2; exit 64; }

mirror_first="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/central-mirror-first.init.gradle.kts"
read -r -a pauses <<< "${GRADLE_RETRY_PAUSES:-60 180 300}"
log="$(mktemp)"
trap 'rm -f "$log"' EXIT

MISS='Could not find [^ ]|was not found in any of the following sources'
FILE_MISS='Could not find [^ ]+ \([^)]+\)'
ERROR="Could not resolve|status code 429|Received status code 5[0-9][0-9]"

# Runs "$@" with each stream teed into $log and kept on its own descriptor, and exits with its status.
run() (
  : > "$log"
  { { "$@" 2>&1 1>&4 | tee -a "$log" >&3; exit "${PIPESTATUS[0]}"; } 4>&1 | tee -a "$log"; exit "${PIPESTATUS[0]}"; } 3>&2
)

refreshed=false
verbose=false
attempt=0
while true; do
  run "$@"
  status=$?
  [[ $status -eq 0 ]] && exit 0
  [[ $attempt -lt ${#pauses[@]} ]] || exit "$status"

  if grep -qE "$MISS" "$log"; then
    kind="a missing dependency"
    if ! $refreshed && grep -qE "$FILE_MISS" "$log"; then set -- "$@" --refresh-dependencies; refreshed=true; fi
  elif grep -qE "$ERROR" "$log"; then
    kind="a repository error"
  else
    exit "$status"
  fi
  if ! $verbose; then set -- "$@" --info; verbose=true; fi

  pause="${pauses[$attempt]}"
  attempt=$((attempt + 1))
  [[ $attempt -eq ${#pauses[@]} ]] && set -- "$@" --init-script "$mirror_first"
  echo "::warning title=Dependency resolution retried::The command failed on $kind; retry $attempt of ${#pauses[@]} in ${pause}s: $*" >&2
  sleep "$pause"
done
