#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Verifies that the published WebGPU release pin (`com.awakekt.awake.backend:webgpu:<core>-SNAPSHOT`)
# on Central's snapshot repository carries a `wasmJsRuntimeElements-published` variant whose Awake
# Core dependencies and constraints all hold Core strictly at `<core>`.
#
# Supports both KOTLIN_ARCHIVE publications (Kotlin 2.5+, where `wasmJs*` variants live inline
# inside the root `webgpu` module metadata) and legacy split publications (where
# `wasmJsRuntimeElements-published` redirects via `available-at` to `webgpu-wasm-js`).
#
# Usage: scripts/verify-webgpu-pin-snapshot.sh <core-version> [snapshots-backend-base-url]
set -euo pipefail

core="${1:?usage: verify-webgpu-pin-snapshot.sh <core-version> [snapshots-backend-base-url]}"
base="${2:-https://central.sonatype.com/repository/maven-snapshots/com/awakekt/awake/backend}"
base="${base%/}"
version="$core-SNAPSHOT"

fetch_latest_module() {
  local artifact="$1"
  local out_file="$2"
  local artifact_url="$base/$artifact/$version"
  local metadata timestamp build_number build
  metadata="$(curl -fsS --retry 5 --retry-all-errors "$artifact_url/maven-metadata.xml")"
  timestamp="$(sed -n 's:.*<timestamp>\(.*\)</timestamp>.*:\1:p' <<< "$metadata" | tr -d '\r' | head -n 1)"
  build_number="$(sed -n 's:.*<buildNumber>\(.*\)</buildNumber>.*:\1:p' <<< "$metadata" | tr -d '\r' | head -n 1)"
  if [[ -z "$timestamp" || -z "$build_number" ]]; then
    echo "The snapshot repository lists no build of $artifact $version." >&2
    return 1
  fi
  build="$core-$timestamp-$build_number"
  curl -fsS --retry 5 --retry-all-errors "$artifact_url/$artifact-$build.module" > "$out_file"
  printf '%s\n' "$build"
}

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

root_module="$work/webgpu.module"
root_build="$(fetch_latest_module webgpu "$root_module")"

if ! jq -e '.variants | any(.name == "wasmJsRuntimeElements-published")' "$root_module" > /dev/null; then
  echo "webgpu $root_build on the snapshot repository has no wasmJsRuntimeElements-published variant." >&2
  exit 1
fi

wasm_redirect="$(jq -r '
  [.variants[] | select(.name == "wasmJsRuntimeElements-published") | ."available-at".module // ""] | first // ""
' "$root_module" | tr -d '\r')"

target_module="$root_module"
target_artifact="webgpu"
target_build="$root_build"
if [[ -n "$wasm_redirect" ]]; then
  target_artifact="$wasm_redirect"
  target_module="$work/$target_artifact.module"
  target_build="$(fetch_latest_module "$target_artifact" "$target_module")"
fi

if ! jq -e --arg core "$core" '
  def awake: .group | test("^com\\.awakekt\\.awake(\\.|$)");
  ([.variants[] | select(.name == "wasmJsRuntimeElements-published") | .dependencies[]? | select(awake and (.module | startswith("webgpu") | not))] | length) > 0
  and ([.variants[] |
    ([.dependencies[]? | select(awake and (.module | startswith("webgpu") | not))] as $deps |
     [.dependencyConstraints[]? | select(awake)] as $constraints |
     ($deps | all(.version.requires == $core))
     and ($constraints | all(.version.strictly == $core))
     and (([$deps[] | "\(.group):\(.module)"] - [$constraints[] | "\(.group):\(.module)"]) | length) == 0)
  ] | all)
' "$target_module" > /dev/null; then
  echo "$target_artifact $target_build on the snapshot repository does not hold every Core module strictly at $core." >&2
  exit 1
fi

echo "::notice::WebGPU $version (build $target_build via $target_artifact) pins Core $core"
