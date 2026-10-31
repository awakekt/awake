#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Prints the Vulkan tag a Core release at <head> needs, or nothing when it needs none.
#
# Usage: scripts/next-vulkan-tag.sh [head]   (head defaults to HEAD)
#
# The Vulkan family releases on its own train of `vulkan-vA.B.C` tags, and the Core alignment BOM
# lifts an older Vulkan release onto a newer Core. So a Core release needs a Vulkan release only
# when, since the latest `vulkan-v*` tag, Vulkan's own code changed (vulkan-publication-impact.sh)
# or a Core module it depends on lost or changed a signature (vulkan-core-api-impact.sh). The API
# check can name a break Vulkan never calls; a release then is merely unneeded, while a missing one
# leaves consumers unable to link, so any report counts.
#
# The next tag bumps the latest one's patch. The latest is the highest version, not the nearest
# tag: a tag can sit off main (vulkan-v0.1.21). Prints nothing when <head> already carries a
# `vulkan-v*` tag, or when no `vulkan-v*` tag exists yet (the first is tagged by hand). The reasons
# go to stderr. Reads the tags already fetched.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
head="${1:-HEAD}"

if [[ -n "$(git tag --points-at "$head" -l 'vulkan-v*')" ]]; then
  echo "$head already carries $(git tag --points-at "$head" -l 'vulkan-v*' | head -n 1)." >&2
  exit 0
fi

latest="$(git tag -l 'vulkan-v*' | grep -E '^vulkan-v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1 || true)"
if [[ -z "$latest" ]]; then
  echo "No vulkan-vA.B.C tag yet: tag the first Vulkan release by hand." >&2
  exit 0
fi

code="$("$here/vulkan-publication-impact.sh" "$latest" "$head")"
api="$("$here/vulkan-core-api-impact.sh" "$latest" "$head")"
[[ "$code" == true ]] && echo "Vulkan's own code changed since $latest." >&2
[[ -n "$api" ]] && echo "Core API Vulkan links against lost or changed a signature since $latest: ${api//$'\n'/, }." >&2
if [[ "$code" != true && -z "$api" ]]; then
  echo "Nothing Vulkan ships or links against changed since $latest." >&2
  exit 0
fi

IFS=. read -r major minor patch <<< "${latest#vulkan-v}"
echo "vulkan-v$major.$minor.$((patch + 1))"
