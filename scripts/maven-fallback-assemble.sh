#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Chooses the release bundles the fallback Maven repository serves and builds it (#1694):
#
# - the newest <core-count> Core releases (v*) and the newest <vulkan-count> Vulkan releases
#   (vulkan-v*) that have a bundle;
# - then every release a served POM names, until none is missing: a Vulkan release imports the Core
#   BOM, and depends on Core artifacts, of the Core release it was built against, so serving it
#   without that release would break every consumer resolving through the site.
#
#   scripts/maven-fallback-assemble.sh build/maven-fallback 2 3
#
# Bundles come from the GitHub Releases (`gh release`). With MAVEN_FALLBACK_SOURCE set to a directory
# they come from there instead, for the test: its `tags` file lists tags newest first, and
# `<tag>/<bundle>.zip` holds each one's bundle.
#
# A deploy publishes the site whole, replacing the one before, so what this chooses is exactly what is
# served afterwards. It fails, and so keeps the live site, if a named release has no bundle to serve.
set -euo pipefail

usage="usage: maven-fallback-assemble.sh <site-dir> <core-count> <vulkan-count>"
site="${1:?$usage}"
core_count="${2:?$usage}"
vulkan_count="${3:?$usage}"
scripts="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bundles="$(mktemp -d)"
trap 'rm -rf "$bundles"' EXIT
source_dir="${MAVEN_FALLBACK_SOURCE:-}"

list_tags() {
  if [[ -n "$source_dir" ]]; then
    cat "$source_dir/tags"
  else
    gh release list --limit 200 --json tagName,isDraft --jq '.[] | select(.isDraft | not) | .tagName'
  fi
}

# Downloads [tag]'s bundle into its own directory; fails when the release has none.
fetch() {
  local tag="$1"
  [[ -d "$bundles/$tag" ]] && return 0
  mkdir -p "$bundles/$tag"
  if [[ -n "$source_dir" ]]; then
    cp "$source_dir/$tag"/*-maven.zip "$bundles/$tag/" 2> /dev/null || { rmdir "$bundles/$tag"; return 1; }
  else
    gh release download "$tag" --pattern 'awake-*-maven.zip' --dir "$bundles/$tag" 2> /dev/null ||
      { rm -rf "${bundles:?}/$tag"; return 1; }
  fi
  echo "Serving $tag"
}

newest() { # tag-regex count
  local served=0
  for tag in $(grep -E "$1" <<< "$tags" || true); do
    [[ "$served" -lt "$2" ]] || break
    if fetch "$tag"; then served=$((served + 1)); fi
  done
}

build() {
  # shellcheck disable=SC2046 # one argument per bundle path, none with spaces
  "$scripts/maven-fallback-site.sh" "$site" $(find "$bundles" -name '*-maven.zip' | sort) > /dev/null
}

tags="$(list_tags)"
newest '^v[0-9]' "$core_count"
newest '^vulkan-v[0-9]' "$vulkan_count"
[[ -n "$(find "$bundles" -name '*-maven.zip')" ]] || { echo "No release has a bundle to serve." >&2; exit 1; }
build

# A named version is a Core release's, or a Vulkan release's when only that tag exists.
while true; do
  added=0
  for version in $("$scripts/maven-fallback-closure.sh" "$site" | cut -d : -f 3 | sort -u); do
    if fetch "v$version" || fetch "vulkan-v$version"; then
      echo "  (named by a served POM)"
      added=1
    fi
  done
  [[ "$added" -eq 1 ]] || break
  build
done

missing="$("$scripts/maven-fallback-closure.sh" "$site")"
if [[ -n "$missing" ]]; then
  echo "The site would serve POMs naming what it cannot hold:" >&2
  echo "$missing" >&2
  exit 1
fi
echo "Fallback repository: $(find "$bundles" -name '*-maven.zip' | wc -l) bundles, $(du -sm "$site" | cut -f 1) MB in $site"
