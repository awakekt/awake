#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Writes the render-evidence PR comment from a base and a head capture to stdout.
#
# Usage: scripts/render-evidence-comment.sh <before-dir> <after-dir> <image-url-prefix> <head-sha>
#
# Both captures come from the same PNG encoder, so identical pixels give identical bytes and a byte
# comparison is a pixel comparison. <image-url-prefix> is where before/ and after/ were published.
set -euo pipefail

before="$1" after="$2" prefix="$3" sha="$4"
width=256

img() { # side name present
  if [[ "$3" == 1 ]]; then echo "<img src=\"$prefix/$1/$2.png\" width=\"$width\">"; else echo "_none_"; fi
}

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
echo "Headless Vulkan (lavapipe) renders of the \`:awake:engine:render:parity\` scenarios at \`${sha:0:9}\`. **${#changed[@]} of $total changed.**"
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
echo "Regenerate locally with \`./gradlew :awake:engine:render:parity:captureRenderEvidence\`."
