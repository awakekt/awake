#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks scripts/next-vulkan-tag.sh against a throwaway repository, one change per case.
set -euo pipefail

scripts="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo="$(mktemp -d)"
trap 'rm -rf "$repo"' EXIT
cd "$repo"
git init -q
git config user.email test@example.com
git config user.name test
# The script finds the impact scripts beside itself.
mkdir scripts
cp "$scripts/next-vulkan-tag.sh" "$scripts/vulkan-publication-impact.sh" "$scripts/vulkan-core-api-impact.sh" scripts/
script="$repo/scripts/next-vulkan-tag.sh"

# vulkan -> math; scene is not reached.
mkdir -p awake/backend/vulkan/src awake/core/math/api awake/scene/api
printf 'implementation(project(":awake:core:math"))\n' > awake/backend/vulkan/build.gradle.kts
printf 'fun draw() = 1\n' > awake/backend/vulkan/src/Draw.kt
printf 'plugins {}\n' > awake/core/math/build.gradle.kts
printf 'public final fun add ()I\n' > awake/core/math/api/math.api
printf 'public final fun scene ()I\n' > awake/scene/api/scene.api
git add -A && git commit -qm base
base="$(git rev-parse HEAD)"
# An older tag with a higher patch than its text sorts by, and one off the version pattern.
git tag -a vulkan-v0.1.9 -m old "$base"
git tag -a vulkan-v0.1.10 -m latest "$base"
git tag -a vulkan-v0.2.0-rc.1 -m preview "$base"

failures=0
expect() { # name expected-output
  local actual
  actual="$("$script" HEAD 2> /dev/null)"
  if [[ "$actual" == "$2" ]]; then echo "ok   $1"; else echo "FAIL $1: expected '$2', got '$actual'"; failures=$((failures + 1)); fi
  git reset -q --hard "$base"
}
commit() { git add -A && git commit -qm change; }

expect "nothing changed since the latest tag" ""

printf 'fun draw() = 2\n' > awake/backend/vulkan/src/Draw.kt && commit
expect "Vulkan code changed: the latest tag's patch, bumped" "vulkan-v0.1.11"

printf '// draws\nfun draw() = 1\n' > awake/backend/vulkan/src/Draw.kt && commit
expect "only a comment changed" ""

: > awake/core/math/api/math.api && commit
expect "a signature Vulkan links against was removed" "vulkan-v0.1.11"

printf 'public final fun add ()I\npublic final fun sub ()I\n' > awake/core/math/api/math.api && commit
expect "a signature was only added" ""

printf 'public final fun scene ()J\n' > awake/scene/api/scene.api && commit
expect "an API outside Vulkan's closure changed" ""

printf 'fun draw() = 2\n' > awake/backend/vulkan/src/Draw.kt && commit && git tag -a vulkan-v0.1.11 -m done HEAD
expect "the commit already carries a Vulkan tag" ""
git tag -d vulkan-v0.1.11 > /dev/null

git tag -d vulkan-v0.1.9 vulkan-v0.1.10 vulkan-v0.2.0-rc.1 > /dev/null
printf 'fun draw() = 2\n' > awake/backend/vulkan/src/Draw.kt && commit
expect "no Vulkan release tag yet" ""

exit "$failures"
