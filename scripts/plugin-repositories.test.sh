#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks the Gradle repository lists that resolve plugins and dependencies for the Core build and
# build-logic. A repository that fails to answer, rather than answering 404, stops Gradle's search
# where it is, so on 16 Nov 2026 an unreachable Compose dev repository listed before google() made
# AGP's plugin unresolvable on every CI job that started with an empty Gradle home. So:
#   - in a pluginManagement block, google() (or google { ... } with a content filter) comes before every
#     maven(...) repository;
#   - no build lists the retired Space repository (maven.pkg.jetbrains.space): nothing resolves from it.
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
failures=0
fail() { echo "FAIL $1"; failures=$((failures + 1)); }

for file in settings.gradle.kts build-logic/settings.gradle.kts; do
  block="$(awk '/^pluginManagement *\{/ { on = 1 } on { print } on && /^\}/ { exit }' "$root/$file")"
  google="$( { grep -nE '^[[:space:]]*google[[:space:]]*(\(\)|\{)' <<< "$block" || true; } | head -1 | cut -d: -f1)"
  maven="$( { grep -n 'maven("' <<< "$block" || true; } | head -1 | cut -d: -f1)"
  if [[ -z "$google" ]]; then fail "$file: pluginManagement has no google()"
  elif [[ -n "$maven" && "$maven" -lt "$google" ]]; then fail "$file: pluginManagement lists a maven() repository before google()"
  else echo "ok   $file: google() first among pluginManagement's repositories"; fi
done

space="$(cd "$root" && { git grep -nF 'maven.pkg.jetbrains.space' -- '*.gradle.kts' || true; })"
if [[ -n "$space" ]]; then fail "a build still lists maven.pkg.jetbrains.space:"; echo "$space"
else echo "ok   no build lists maven.pkg.jetbrains.space"; fi

[[ $failures -eq 0 ]] || { echo "$failures case(s) failed"; exit 1; }
