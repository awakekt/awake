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
out="$(mktemp)"
err="$(mktemp)"
trap 'rm -f "$log" "$out" "$err"' EXIT

MISS='Could not find [^ ]|was not found in any of the following sources'
FILE_MISS='Could not find [^ ]+ \([^)]+\)'
ERROR="Could not resolve|status code 429|Received status code 5[0-9][0-9]"

# Runs "$@" with stdout and stderr written to files, not pipes, and streamed from them to this
# script's own stdout and stderr while it runs, then exits with its status. On Windows the Gradle and
# Kotlin daemons inherit the command's handles and outlive it: through a pipe (tee), the reader then
# never saw the end of the output, and every Windows step hung until its timeout (#1831). A file
# can be held open by a daemon without holding anything up.
run() {
  : > "$out"
  : > "$err"
  "$@" > "$out" 2> "$err" &
  local pid=$! status
  trap 'kill "$pid" 2>/dev/null' TERM INT
  sent_out=0
  sent_err=0
  while kill -0 "$pid" 2>/dev/null; do
    drain "$out" sent_out 1
    drain "$err" sent_err 2
    # The system sleep, in a subshell so bash doesn't remember its path for the pauses below, whose
    # sleep the tests replace on PATH to record them.
    (command -p sleep 0.5)
  done
  wait "$pid"
  status=$?
  trap - TERM INT
  drain "$out" sent_out 1
  drain "$err" sent_err 2
  cat "$out" "$err" > "$log"
  return "$status"
}

# Writes what [file] $1 gained since the offset held in variable $2 to descriptor $3, and moves it on.
drain() {
  local size
  size=$(wc -c < "$1")
  if (( size > ${!2} )); then
    tail -c +"$(( ${!2} + 1 ))" "$1" | head -c "$(( size - ${!2} ))" >&"$3"
    printf -v "$2" '%s' "$size"
  fi
}

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
