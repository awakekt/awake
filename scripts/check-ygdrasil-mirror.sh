#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks that a Maven repository, a directory or an https URL, holds every io.ygdrasil build the
# WebGPU backend pins in build.gradle.kts: the module metadata, the POM and the jar or klib of each
# module, at the timestamped names Gradle asks for. The Maven fallback workflow runs it on the site
# before deploying, so a mirror that lost a file fails there and not in the next release.
#
#   scripts/check-ygdrasil-mirror.sh build/maven-fallback
#   scripts/check-ygdrasil-mirror.sh https://awakekt.github.io/awake
set -euo pipefail

base="${1:?usage: check-ygdrasil-mirror.sh <directory|url>}"
base="${base%/}"
build="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/build.gradle.kts"

# floating-version module...: the modules each pinned family publishes, with the platforms they have.
families="
0.0.10-SNAPSHOT webgpu-ktypes webgpu-ktypes-jvm webgpu-ktypes-wasm-js webgpu-ktypes-descriptors webgpu-ktypes-descriptors-jvm webgpu-ktypes-descriptors-wasm-js webgpu-ktypes-web webgpu-ktypes-web-wasm-js
0.2.0-SNAPSHOT wgpu4k wgpu4k-jvm wgpu4k-wasm-js wgpu4k-toolkit wgpu4k-toolkit-jvm wgpu4k-toolkit-wasm-js
v29.0.0-SNAPSHOT wgpu4k-native wgpu4k-native-jvm kffi kffi-jvm
"

has() {
  case "$base" in
    http*) curl -fsSI --retry 3 --retry-all-errors -o /dev/null "$base/$1" ;;
    *) [[ -f "$base/$1" ]] ;;
  esac
}

missing=0
checked=0
while read -r floating modules; do
  [[ -n "$floating" ]] || continue
  pinned="$(sed -n "s|^ *\"$floating\" to \"\([^\"]*\)\".*|\1|p" "$build" | head -1)"
  [[ -n "$pinned" ]] || { echo "build.gradle.kts pins nothing for $floating" >&2; exit 1; }
  for module in $modules; do
    case "$module" in
      *-wasm-js) binary=klib ;;
      *) binary=jar ;;
    esac
    for ext in module pom "$binary"; do
      file="io/ygdrasil/$module/$floating/$module-$pinned.$ext"
      checked=$((checked + 1))
      has "$file" || { echo "missing: $file" >&2; missing=$((missing + 1)); }
    done
  done
done <<< "$families"
has io/ygdrasil/LICENSE-wgpu4k.txt || { echo "missing: io/ygdrasil/LICENSE-wgpu4k.txt" >&2; missing=$((missing + 1)); }

[[ "$missing" -eq 0 ]] || { echo "$missing of $checked pinned io.ygdrasil files are missing from $base" >&2; exit 1; }
echo "All $checked pinned io.ygdrasil files are in $base"
