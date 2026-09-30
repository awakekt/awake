#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Writes the render-evidence PR comment from a base and a head capture to stdout.
#
# Usage: scripts/render-evidence-comment.sh <before-dir> <after-dir> <image-url-prefix> <head-sha> [icons|all]
#
# Both captures come from the same PNG encoder, so identical pixels give identical bytes and a byte
# comparison is a pixel comparison. <image-url-prefix> is where before/ and after/ were published.
set -euo pipefail

before="$1" after="$2" prefix="$3" sha="$4" focus="${5:-all}"
width=256

img() { # side name present
  if [[ "$3" == 1 ]]; then echo "<img src=\"$prefix/$1/$2.png\" width=\"$width\">"; else echo "_none_"; fi
}

if [[ "$focus" == icons ]]; then
  for name in ui-button-icons-cpu ui-lucide-icons-cpu ui-shadcn-icons-cpu; do
    [[ -f "$before/$name.png" && -f "$after/$name.png" ]] || {
      echo "Missing icon capture: $name" >&2
      exit 1
    }
  done
  [[ -f "$after/ui-original-svg-browser.png" ]] || {
    echo "Missing original SVG browser capture" >&2
    exit 1
  }
  width=320
  echo "<!-- render-evidence -->"
  echo "## Icon render evidence"
  echo
  echo "All eight pinned SVGs and their Awake Lucide/shadcn defaults, rendered at 16 px and 32 px. Base and head use the same Awake capture test at \`${sha:0:9}\`."
  echo
  echo "| Original SVG in Chromium | Lucide before | Lucide after |"
  echo "| --- | --- | --- |"
  echo "| $(img after ui-original-svg-browser 1) | $(img before ui-lucide-icons-cpu 1) | $(img after ui-lucide-icons-cpu 1) |"
  echo
  echo "| shadcn before | shadcn after |"
  echo "| --- | --- |"
  echo "| $(img before ui-shadcn-icons-cpu 1) | $(img after ui-shadcn-icons-cpu 1) |"
  echo
  width=220
  echo "| Icon buttons before | Icon buttons after |"
  echo "| --- | --- |"
  echo "| $(img before ui-button-icons-cpu 1) | $(img after ui-button-icons-cpu 1) |"
  echo
  echo "Awake sheets are CPU raster previews. The Chromium sheet renders the original SVGs independently. Native Vulkan and WebGPU icon pixels still need separate capture."
  exit 0
fi

[[ "$focus" == all ]] || { echo "Unknown evidence focus: $focus" >&2; exit 1; }

names="$( (ls "$before" "$after" 2>/dev/null || true) | grep '\.png$' | sed 's/\.png$//' | sort -u)"
changed=() unchanged=()
for name in $names; do
  if [[ -f "$before/$name.png" && -f "$after/$name.png" ]] && cmp -s "$before/$name.png" "$after/$name.png"; then
    unchanged+=("$name")
  else
    changed+=("$name")
  fi
done
total=$(( ${#changed[@]} + ${#unchanged[@]} ))

echo "<!-- render-evidence -->"
echo "## Render evidence"
echo
echo "Headless Vulkan (lavapipe) render scenarios, shadcn/Lucide CPU icon previews, and a Chromium render of the pinned original SVGs at \`${sha:0:9}\`. **${#changed[@]} of $total changed.**"
echo "The \`ui-*-cpu\` images are CPU raster previews, not GPU pixel-fidelity results."
if ! ls "$before"/*.png >/dev/null 2>&1; then
  echo
  echo "_The base commit has no evidence capture, so every image is shown as new._"
fi
if (( ${#changed[@]} )); then
  echo
  echo "| Scenario | Before | After |"
  echo "| --- | --- | --- |"
  for name in "${changed[@]}"; do
    has_before=0; [[ -f "$before/$name.png" ]] && has_before=1
    has_after=0; [[ -f "$after/$name.png" ]] && has_after=1
    echo "| \`$name\` | $(img before "$name" $has_before) | $(img after "$name" $has_after) |"
  done
fi
if (( ${#unchanged[@]} )); then
  echo
  echo "<details><summary>${#unchanged[@]} unchanged</summary>"
  echo
  echo "| Scenario | Render |"
  echo "| --- | --- |"
  for name in "${unchanged[@]}"; do
    echo "| \`$name\` | $(img after "$name" 1) |"
  done
  echo
  echo "</details>"
fi
echo
echo "Regenerate the render scenarios with \`./gradlew :awake:engine:render:parity:captureRenderEvidence\`, the icon previews with \`./gradlew :awake:ui:shadcn:desktopTest --tests '*ShadcnComposeRenderPreview.buttonIconsRenderThePinnedLucideGlyphs' --tests '*LucideShadcnSpritesheetPreviewTest*'\`, and the browser reference with \`python3 tools/icons/capture_lucide_spritesheet_reference.py --out original-svg-browser.png\`."
