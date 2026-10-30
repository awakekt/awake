#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Builds awake-plugins against this working tree's Core, published to Maven Local, so a Core change
# that breaks the plugins or makes them allocate more fails on the Core pull request that causes it,
# not after a release. Core 0.28.0 shipped a canvas read that allocated every frame (#1169); only
# awake-plugins' HUD allocation scorecard caught it, after the release, and it took 0.28.1 to fix.
#
# The plugins name the Core modules they use in their version catalog (gradle/libs.versions.toml,
# `version.ref = "awake"`). This publishes those modules and every Core project they depend on, and
# nothing else: no GPU backend, window or native library, and no Android or Apple publication.
#
# Usage:
#   tools/check_plugins_consumer.sh modules   <plugins-dir>
#       The Core projects the plugins resolve, with their project dependencies, one per line.
#   tools/check_plugins_consumer.sh affected  <plugins-dir> <base-sha> <head-sha>
#       Prints true when a change between the two commits can reach those projects or the build that
#       publishes them, false otherwise. A base that is missing or unreadable prints true.
#   tools/check_plugins_consumer.sh publish   <plugins-dir> <version>
#       Publishes those projects to Maven Local at <version>, a version no release uses.
#   tools/check_plugins_consumer.sh point     <plugins-dir> <version>
#       Points the plugins' catalog (`awake = "..."`) at <version>. It edits that checkout in place.
#   tools/check_plugins_consumer.sh test      <plugins-dir>
#       Runs every plugin's JVM tests, plain and obfuscated.
#   tools/check_plugins_consumer.sh scorecard <plugins-dir>
#       Runs the HUD scorecard, whose allocation gate holds rpg-hud-studio's install path among others.
set -euo pipefail

ENGINE_ROOT=$(cd "$(dirname "$0")/.." && pwd)

usage() {
  sed -n '/^# Usage:/,/^set -euo/p' "$0" | sed '$d; s/^# \{0,1\}//' >&2
  exit 2
}

module_dir() { echo "${1#:}" | tr : /; } # :awake:core:math -> awake/core/math

# The Core project paths a catalog names. A coordinate is com.awakekt.awake[.<parent path>]:<name>,
# the group the root build derives from a project's parent path (build.gradle.kts), so
# com.awakekt.awake.ui:shadcn is :awake:ui:shadcn and com.awakekt.awake:ecs is :awake:ecs.
catalog_projects() {
  local catalog="$1/gradle/libs.versions.toml" line coordinate group name path
  [[ -f "$catalog" ]] || { echo "No version catalog at $catalog" >&2; exit 1; }
  while IFS= read -r line; do
    coordinate=$(sed -nE 's/.*module *= *"([^"]+)".*/\1/p' <<< "$line")
    [[ -n "$coordinate" ]] || continue
    group="${coordinate%%:*}"
    name="${coordinate#*:}"
    [[ "$group" == com.awakekt.awake || "$group" == com.awakekt.awake.* ]] || continue
    [[ "$group" == com.awakekt.awake.plugins* ]] && continue
    path=":awake$(tr . : <<< "${group#com.awakekt.awake}"):$name"
    if [[ ! -f "$ENGINE_ROOT/$(module_dir "$path")/build.gradle.kts" ]]; then
      echo "The plugins' catalog names $coordinate, which is no Core project ($path)." >&2
      exit 1
    fi
    echo "$path"
  done < <(grep -E 'version\.ref *= *"awake"' "$catalog")
}

# Those projects and the transitive closure of the `project(":awake:...")` each build script names.
# Test dependencies count too: that over-reports and never misses a module a consumer resolves. The
# root build adds the alignment BOM to every published Core module, where no build script names it,
# and a POM that imports a BOM Maven Local lacks does not parse, so the BOM is always one of them.
closure() {
  local seen=$'\n' queue=() project dependency roots
  roots=$(catalog_projects "$1")
  if [[ -z "$roots" ]]; then
    echo "The plugins' catalog names no Core module (version.ref = \"awake\")." >&2
    exit 1
  fi
  roots+=$'\n:awake:bom'
  while IFS= read -r project; do
    [[ "$seen" == *$'\n'"$project"$'\n'* ]] && continue
    seen+="$project"$'\n'
    queue+=("$project")
  done <<< "$roots"
  while ((${#queue[@]})); do
    project="${queue[0]}"
    queue=("${queue[@]:1}")
    while IFS= read -r dependency; do
      [[ -n "$dependency" && "$seen" != *$'\n'"$dependency"$'\n'* ]] || continue
      seen+="$dependency"$'\n'
      queue+=("$dependency")
    done < <(grep -oE 'project\(":awake[A-Za-z0-9:_-]*"\)' "$ENGINE_ROOT/$(module_dir "$project")/build.gradle.kts" \
      | sed -E 's/project\("(.*)"\)/\1/' || true)
  done
  printf '%s' "$seen" | sed '/^$/d' | sort
}

affected() {
  local plugins="$1" base="$2" head="$3" changed project path
  if [[ -z "$base" || "$base" =~ ^0+$ ]] || ! git -C "$ENGINE_ROOT" cat-file -e "$base^{commit}" 2> /dev/null; then
    echo true
    return
  fi
  changed=$(git -C "$ENGINE_ROOT" diff --name-only "$base" "$head")
  local modules prefixes=()
  modules=$(closure "$plugins")
  while IFS= read -r project; do prefixes+=("$(module_dir "$project")/"); done <<< "$modules"
  while IFS= read -r path; do
    [[ -z "$path" || "$path" == *.md ]] && continue
    # The build that publishes them, and this gate itself.
    if [[ "$path" =~ ^(gradle/|build-logic/|build\.gradle\.kts$|settings\.gradle\.kts$|gradle\.properties$|\.github/workflows/ci\.yml$|tools/check_plugins_consumer\.sh$) ]]; then
      echo true
      return
    fi
    for prefix in "${prefixes[@]}"; do
      if [[ "$path" == "$prefix"* ]]; then
        echo true
        return
      fi
    done
  done <<< "$changed"
  echo false
}

publish() {
  local plugins="$1" version="$2" modules tasks=() project
  modules=$(closure "$plugins")
  while IFS= read -r project; do tasks+=("$project:publishToMavenLocal"); done <<< "$modules"
  echo "==> Publishing ${#tasks[@]} Core projects at $version"
  # The plugins build JVM and wasm (their ABI dumps take the wasm klib); Apple publications are off
  # on Linux already, and no plugin targets Android.
  (cd "$ENGINE_ROOT" && ./gradlew "${tasks[@]}" -x publishAndroidPublicationToMavenLocal \
    -Pawake.version="$version" --no-configuration-cache --stacktrace)
}

point() {
  local plugins="$1" version="$2" catalog
  catalog="$plugins/gradle/libs.versions.toml"
  grep -qE '^awake *= *"' "$catalog" || { echo "No 'awake = \"...\"' version in $catalog" >&2; exit 1; }
  sed -i.bak -E "s/^awake *= *\"[^\"]*\"/awake = \"$version\"/" "$catalog" && rm -f "$catalog.bak"
  echo "==> awake-plugins now builds against Core $version"
  grep -E '^awake *=' "$catalog"
}

# Linux cross-compiles Apple klibs by default, and those need Core's, which only a Mac publishes. Off,
# the iOS targets are skipped here as they are on the plugins' own Windows CI.
NO_APPLE_KLIBS=-Pkotlin.native.enableKlibsCrossCompilation=false

# Every plugin's JVM suite, on its own jar and on the R8-renamed one a game ships, which also proves
# R8 still resolves everything the plugins reach in Core.
run_tests() {
  (cd "$1" && ./gradlew jvmTest jvmObfuscatedTest "$NO_APPLE_KLIBS" --continue --max-workers=2 --stacktrace)
}

# The HUD modules' ABI, coverage and source gates, the zero-allocation read ratchet, and the JMH
# allocation gate: B/op within 10% of config/scorecards/hud-baselines.json, which is where
# rpg-hud-studio's install path is held. Nothing changes on the plugins' side, so HEAD is the base
# their detekt baseline is compared with. Throughput stays informational on a shared runner.
scorecard() {
  (cd "$1" && ./gradlew pluginScorecard "$NO_APPLE_KLIBS" --continue -Pscorecard.baseRef=HEAD --max-workers=2 --stacktrace)
}

command="${1:-}"
case "$command" in
  modules) [[ $# -eq 2 ]] || usage; closure "$2" ;;
  affected) [[ $# -eq 4 ]] || usage; affected "$2" "$3" "$4" ;;
  publish) [[ $# -eq 3 ]] || usage; publish "$2" "$3" ;;
  point) [[ $# -eq 3 ]] || usage; point "$2" "$3" ;;
  test) [[ $# -eq 2 ]] || usage; run_tests "$2" ;;
  scorecard) [[ $# -eq 2 ]] || usage; scorecard "$2" ;;
  *) usage ;;
esac
