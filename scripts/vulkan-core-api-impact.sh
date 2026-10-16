#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Lists the Core modules the Vulkan family depends on whose public API lost a signature between two
# commits, one module directory per line. Empty means none.
#
# Usage: scripts/vulkan-core-api-impact.sh <last-vulkan-tag> <head>
#
# The Core alignment BOM lifts an older Vulkan release to a newer Core, so a Core release needs a
# Vulkan release only when Vulkan's own code changed (vulkan-publication-impact.sh) or when the old
# Vulkan binaries can no longer link against the new Core. Removing or changing a signature breaks
# linking; adding one does not, so only lines removed from an api/*.api dump count.
#
# "Depends on" is the transitive closure of `project(":awake:...")` in the family's build scripts at
# <head>, test dependencies included. That over-reports and never under-reports a break.
set -euo pipefail

base="$1" head="$2"
family='awake/backend/vulkan'

module_dir() { echo "${1#:}" | tr : /; } # :awake:core:math -> awake/core/math

deps() { # build script path -> the project paths it names
  git show "$head:$1" 2> /dev/null | grep -oE 'project\(":awake[A-Za-z0-9:_-]*"\)' | sed -E 's/project\("(.*)"\)/\1/' || true
}

# Newline-separated project paths already queued; no associative arrays, macOS bash is 3.2.
seen=$'\n'
queue=()
while IFS= read -r script; do
  queue+=("$script")
done < <(git ls-tree -r --name-only "$head" -- "$family" | grep '/build.gradle.kts$')

while ((${#queue[@]})); do
  script="${queue[0]}"
  queue=("${queue[@]:1}")
  while IFS= read -r project; do
    [[ -n "$project" && "$seen" != *$'\n'"$project"$'\n'* ]] || continue
    seen+="$project"$'\n'
    dir="$(module_dir "$project")"
    [[ "$dir/" == "$family/"* ]] && continue
    queue+=("$dir/build.gradle.kts")
  done < <(deps "$script")
done

while IFS= read -r project; do
  [[ -n "$project" ]] || continue
  dir="$(module_dir "$project")"
  [[ "$dir/" == "$family/"* ]] && continue
  if [[ -n "$(git diff -U0 --no-renames "$base" "$head" -- "$dir/api" | grep -E '^-([^-]|$)' || true)" ]]; then
    echo "$dir"
  fi
done <<< "$seen" | sort
