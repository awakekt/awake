#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks scripts/vulkan-publication-impact.sh against a throwaway repository, one change per case.
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/vulkan-publication-impact.sh"
repo="$(mktemp -d)"
trap 'rm -rf "$repo"' EXIT
cd "$repo"
git init -q
git config user.email test@example.com
git config user.name test

src=awake/backend/vulkan/src/commonMain/kotlin/Renderer.kt
cpp=awake/backend/vulkan/bindings/src/main/cpp/Utils.cpp
mkdir -p "$(dirname "$src")" "$(dirname "$cpp")" awake/backend/vulkan/api awake/scene
printf '/** Draws. */\nfun draw() = 1\n' > "$src"
printf '// Utils\nint add(int a, int b) { return a + b; }\n' > "$cpp"
printf 'cmake_minimum_required(VERSION 3.22)\n' > awake/backend/vulkan/bindings/CMakeLists.txt
printf 'public final fun draw ()I\n' > awake/backend/vulkan/api/vulkan.api
printf '# Vulkan\n' > awake/backend/vulkan/README.md
printf 'fun scene() = 1\n' > awake/scene/Scene.kt
git add -A && git commit -qm base
base="$(git rev-parse HEAD)"

failures=0
expect() { # name expected
  local actual
  actual="$("$script" "$base" HEAD)"
  if [[ "$actual" == "$2" ]]; then echo "ok   $1"; else echo "FAIL $1: expected $2, got $actual"; failures=$((failures + 1)); fi
  git reset -q --hard "$base"
}
commit() { git add -A && git commit -qm change; }

printf '# Vulkan backend\n\nMore words.\n' > awake/backend/vulkan/README.md && commit
expect "markdown only" false

printf 'public final fun draw ()I\npublic final fun drawTwice ()I\n' > awake/backend/vulkan/api/vulkan.api && commit
expect "api dump only" false

printf '/** Draws one frame. */\n// A note.\nfun draw() = 1\n' > "$src" && commit
expect "kotlin comment only" false

printf '/**\n * Draws one frame.\n *\n */\nfun draw() = 1\n' > "$src" && commit
expect "kotlin kdoc block" false

printf '// Adds two ints.\nint add(int a, int b) { return a + b; }\n' > "$cpp" && commit
expect "c++ comment only" false

printf '# Native build\ncmake_minimum_required(VERSION 3.22)\n' > awake/backend/vulkan/bindings/CMakeLists.txt && commit
expect "cmake comment only" false

printf 'fun scene() = 2\n' > awake/scene/Scene.kt && commit
expect "outside the family" false

printf '/** Draws. */\nfun draw() = 2\n' > "$src" && commit
expect "kotlin code" true

printf '/** Draws one frame. */\nfun draw() = 2 // and a comment\n' > "$src" && commit
expect "code beside a comment" true

printf 'cmake_minimum_required(VERSION 3.25)\n' > awake/backend/vulkan/bindings/CMakeLists.txt && commit
expect "cmake code" true

git rm -q "$cpp" && commit
expect "deleted source" true

mkdir -p awake/backend/vulkan/src/main/resources && printf 'shader\n' > awake/backend/vulkan/src/main/resources/lit.wgsl && commit
expect "other family file" true

exit "$failures"
