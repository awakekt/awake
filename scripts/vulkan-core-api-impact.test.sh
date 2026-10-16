#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks scripts/vulkan-core-api-impact.sh against a throwaway repository, one change per case.
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/vulkan-core-api-impact.sh"
repo="$(mktemp -d)"
trap 'rm -rf "$repo"' EXIT
cd "$repo"
git init -q
git config user.email test@example.com
git config user.name test

# vulkan -> render -> math (transitive); scene is not reached.
mkdir -p awake/backend/vulkan awake/engine/render/api awake/core/math/api awake/scene/api
printf 'implementation(project(":awake:engine:render"))\n' > awake/backend/vulkan/build.gradle.kts
printf 'api(project(":awake:core:math"))\n' > awake/engine/render/build.gradle.kts
printf 'plugins {}\n' > awake/core/math/build.gradle.kts
printf 'public final fun draw ()I\n' > awake/engine/render/api/render.api
printf 'public final fun add ()I\n' > awake/core/math/api/math.api
printf 'public final fun scene ()I\n' > awake/scene/api/scene.api
git add -A && git commit -qm base
base="$(git rev-parse HEAD)"

failures=0
expect() { # name expected-output
  local actual
  actual="$("$script" "$base" HEAD)"
  if [[ "$actual" == "$2" ]]; then echo "ok   $1"; else echo "FAIL $1: expected '$2', got '$actual'"; failures=$((failures + 1)); fi
  git reset -q --hard "$base"
}
commit() { git add -A && git commit -qm change; }

printf 'public final fun draw ()I\npublic final fun drawTwo ()I\n' > awake/engine/render/api/render.api && commit
expect "signature added" ""

printf 'public final fun draw ()J\n' > awake/engine/render/api/render.api && commit
expect "signature changed in a direct dependency" "awake/engine/render"

: > awake/core/math/api/math.api && commit
expect "signature removed in a transitive dependency" "awake/core/math"

printf 'public final fun scene ()J\n' > awake/scene/api/scene.api && commit
expect "api changed outside the closure" ""

exit "$failures"
