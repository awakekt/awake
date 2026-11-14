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
# `vulkan-v*` tag that is on Maven Central, or when no `vulkan-v*` tag exists yet (the first is
# tagged by hand). A tag on <head> whose Publish run never reached Central is a version that never
# shipped: with VULKAN_REPLACE_UNPUBLISHED=1 the next one is printed in its place, so the Vulkan tag
# workflow can recover it; without it nothing is printed, since a tag is also absent from Central
# while its own Publish run is still uploading. When Central cannot say, nothing is printed. The
# reasons go to stderr. Reads the tags already fetched.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
head="${1:-HEAD}"

# Whether Vulkan release <version> is on Maven Central: 0 yes, 1 no, 2 could not tell.
# VULKAN_RELEASE_CHECK names a command that answers the same way, for the tests.
published() {
  if [[ -n "${VULKAN_RELEASE_CHECK:-}" ]]; then "$VULKAN_RELEASE_CHECK" "$1"; return; fi
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' --retry 3 --retry-all-errors \
    "https://repo1.maven.org/maven2/com/awakekt/awake/backend/vulkan/$1/vulkan-$1.pom" || true)"
  case "$code" in 200) return 0 ;; 404) return 1 ;; *) return 2 ;; esac
}

latest="$(git tag -l 'vulkan-v*' | grep -E '^vulkan-v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1 || true)"

if [[ -n "$(git tag --points-at "$head" -l 'vulkan-v*')" ]]; then
  carried="$(git tag --points-at "$head" -l 'vulkan-v*' | grep -E '^vulkan-v[0-9]+\.[0-9]+\.[0-9]+$' | sort -V | tail -n 1 || true)"
  status=0
  [[ -z "$carried" ]] || published "${carried#vulkan-v}" || status=$?
  if [[ -z "$carried" || "$status" == 0 ]]; then
    echo "$head already carries $(git tag --points-at "$head" -l 'vulkan-v*' | head -n 1)." >&2
    exit 0
  fi
  if [[ "$status" != 1 ]]; then
    echo "$head carries $carried, and Maven Central could not say whether it was published: no tag." >&2
    exit 0
  fi
  # Not on Central is also true while that tag's own Publish run is still uploading, so a person
  # says the run failed (the Vulkan tag workflow's replace_unpublished input), never the release run.
  if [[ "${VULKAN_REPLACE_UNPUBLISHED:-}" != 1 ]]; then
    echo "$head carries $carried, which is not on Maven Central. If its Publish run failed, run the Vulkan tag workflow with replace_unpublished." >&2
    exit 0
  fi
  # Its Publish run failed before anything reached Central, and Central takes no version twice.
  echo "$head carries $carried, which never reached Maven Central: the next version takes its place." >&2
  IFS=. read -r major minor patch <<< "${latest#vulkan-v}"
  echo "vulkan-v$major.$minor.$((patch + 1))"
  exit 0
fi

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
