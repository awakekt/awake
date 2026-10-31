#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Pushes the Vulkan tag the stable Core release <core-tag> needs (scripts/next-vulkan-tag.sh) on the
# commit that release names, with RELEASE_TOKEN: a tag pushed with a workflow's own token starts no
# workflow, and the tag's Publish run is what releases the Vulkan family. Run it once Core is on
# Maven Central. Writes the reasons to $GITHUB_STEP_SUMMARY when it is set.
#
# Usage: scripts/push-vulkan-tag.sh <core-tag>   (from a checkout with every tag fetched)
# Used by Publish's Vulkan tag job and, for a release published before that job, the Vulkan tag
# workflow (.github/workflows/vulkan-tag.yml).
set -euo pipefail

core="${1:?usage: push-vulkan-tag.sh <core-tag>}"
summary="${GITHUB_STEP_SUMMARY:-/dev/null}"

if [[ ! "$core" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "::notice::$core is a preview: Vulkan releases follow stable Core releases only."
  exit 0
fi
commit="$(git rev-list -n 1 "$core")"
reasons="$(mktemp)"
trap 'rm -f "$reasons"' EXIT
tag="$("$(dirname "${BASH_SOURCE[0]}")/next-vulkan-tag.sh" "$commit" 2> "$reasons")"
cat "$reasons"
{ echo "### Vulkan tag for $core"; echo; sed 's/^/- /' "$reasons"; } >> "$summary"
if [[ -z "$tag" ]]; then
  echo "No Vulkan release needed for $core." >> "$summary"
  exit 0
fi
if git ls-remote --exit-code --tags origin "refs/tags/$tag" > /dev/null; then
  echo "::error::$tag already exists on another commit; tag $core's Vulkan by hand with the next free version."
  exit 1
fi
if [[ -z "${RELEASE_TOKEN:-}" ]]; then
  echo "::notice::RELEASE_TOKEN is not set. Tag by hand: git tag -a $tag -m $tag $commit && git push origin $tag"
  exit 0
fi
git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
basic="$(printf 'x-access-token:%s' "$RELEASE_TOKEN" | base64 -w0)"
echo "::add-mask::$basic"
export GIT_CONFIG_COUNT=1 GIT_CONFIG_KEY_0=http.https://github.com/.extraheader
export GIT_CONFIG_VALUE_0="AUTHORIZATION: basic $basic"
git tag -a "$tag" -m "$tag" "$commit"
git push origin "$tag"
echo "Pushed $tag on $commit: its Publish run releases the Vulkan family." >> "$summary"
