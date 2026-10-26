#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# INVESTIGATION. Captures the text-clipping cases on a base and on this working tree, then composes the
# before/after sheets, as the Render Evidence workflow does for a pull request.
#
#   tools/fonts-tooling/text-evidence/run_local.sh <base-ref> [out-dir]
#
# <base-ref> is any commit-ish; out-dir defaults to build/text-evidence. A base that predates the
# capture gets it laid over a throwaway worktree: the textclip sources and the marked block of
# awake/engine/render/parity/build.gradle.kts. Needs Pillow (python3 -m pip install pillow) and a JDK;
# set GRADLE to use another launcher than ./gradlew.
set -euo pipefail

base_ref="${1:?usage: run_local.sh <base-ref> [out-dir]}"
root="$(git rev-parse --show-toplevel)"
out="$(realpath -m "${2:-$root/build/text-evidence}")"
gradle="${GRADLE:-./gradlew}"
parity=awake/engine/render/parity
harness="$parity/src/desktopTest/kotlin/com/awakekt/awake/render/parity/textclip"
task=:awake:engine:render:parity:captureTextClipEvidence

work="$(mktemp -d)"
base="$work/base"
trap 'git -C "$root" worktree remove --force "$base" 2>/dev/null || true; rm -rf "$work"' EXIT
git -C "$root" worktree add --detach "$base" "$base_ref"

if [[ ! -d "$base/$harness" ]]; then
  echo "The base predates the text-clip capture; laying it over a worktree."
  mkdir -p "$base/$harness"
  cp "$root/$harness"/*.kt "$base/$harness/"
  sed -n '/^\/\/ >>> text-clip-evidence/,/^\/\/ <<< text-clip-evidence/p' "$root/$parity/build.gradle.kts" >> "$base/$parity/build.gradle.kts"
fi

# The naga build is the slow part of a cold checkout; the head's is the one the base needs too.
if [[ -d "$root/awake/asset/shader-compiler/rust-native/target" && ! -d "$base/awake/asset/shader-compiler/rust-native/target" ]]; then
  cp -R "$root/awake/asset/shader-compiler/rust-native/target" "$base/awake/asset/shader-compiler/rust-native/" || true
fi

rm -rf "$out/before" "$out/after" "$out/sheets"
(cd "$base" && $gradle "$task" -Pevidence.dir="$out/before")
(cd "$root" && $gradle "$task" -Pevidence.dir="$out/after")
python3 -I "$root/tools/fonts-tooling/text-evidence/compose_sheets.py" sheets "$out/before" "$out/after" "$out/sheets"
echo "Sheets: $out/sheets (overview.png, sheet-*.png, table.md)"
