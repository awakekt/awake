#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Chooses what the fallback Maven repository serves and builds it (#1694):
#
# - the newest <core-count> Core releases (v*) and the newest <vulkan-count> Vulkan releases
#   (vulkan-v*) that have a bundle, whole;
# - then, from any other release, only the artifacts a served POM or Gradle module names, until none
#   is missing. A Vulkan release imports the Core BOM, and depends on Core artifacts, of the Core
#   release it was built against: serving it needs that BOM and those artifacts, not the other modules
#   of a 130 MB Core bundle.
#
#   scripts/maven-fallback-assemble.sh build/maven-fallback 2 3 900
#
# It prints what each release adds, and fails, keeping the live site, when the site is over
# <limit-mb> (when given) or a named artifact has no bundle to come from.
#
# Bundles come from the GitHub Releases (`gh release`). With MAVEN_FALLBACK_SOURCE set to a directory
# they come from there instead, for the test: its `tags` file lists tags newest first, and
# `<tag>/<bundle>.zip` holds each one's bundle.
#
# A deploy publishes the site whole, replacing the one before, so what this chooses is exactly what is
# served afterwards.
set -euo pipefail

usage="usage: maven-fallback-assemble.sh <site-dir> <core-count> <vulkan-count> [limit-mb]"
site="${1:?$usage}"
core_count="${2:?$usage}"
vulkan_count="${3:?$usage}"
limit_mb="${4:-}"
scripts="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
whole="$work/whole"     # <tag>/<bundle>.zip, served whole
cache="$work/cache"     # <tag>/<bundle>.zip, served only in part
named="$work/named"     # <tag>/<artifact paths>, the part of a cached bundle a served POM names
mkdir -p "$whole" "$cache" "$named"
source_dir="${MAVEN_FALLBACK_SOURCE:-}"

list_tags() {
  if [[ -n "$source_dir" ]]; then
    cat "$source_dir/tags"
  else
    # Every release, through REST and every page, newest first: a `--limit` drops the oldest once
    # the repository has more releases, as `--limit 100` dropped the wgpu4k mirror on 21 Nov.
    gh api "repos/${GITHUB_REPOSITORY:-awakekt/awake}/releases?per_page=100" --paginate \
      --jq '.[] | select(.draft | not) | .tag_name'
  fi
}

# Downloads [tag]'s bundle into [into]/[tag], once; fails when the release has none.
fetch() { # tag into
  local tag="$1" dir="$2/$1"
  [[ -d "$dir" ]] && return 0
  mkdir -p "$dir"
  if [[ -n "$source_dir" ]]; then
    cp "$source_dir/$tag"/*-maven.zip "$dir/" 2> /dev/null || { rmdir "$dir"; return 1; }
  else
    gh release download "$tag" --pattern 'awake-*-maven.zip' --dir "$dir" 2> /dev/null || { rm -rf "${dir:?}"; return 1; }
  fi
}

newest() { # tag-regex count
  local served=0
  for tag in $(grep -E "$1" <<< "$tags" || true); do
    [[ "$served" -lt "$2" ]] || break
    if fetch "$tag" "$whole"; then served=$((served + 1)); fi
  done
}

# Copies one artifact version, group:artifact:version, out of the release that holds it; fails when
# no release does. Its own version names the release: a Core one, or a Vulkan one when only that exists.
take() { # coordinate
  local group artifact version path tag
  IFS=: read -r group artifact version <<< "$1"
  path="${group//.//}/$artifact/$version"
  for tag in "v$version" "vulkan-v$version"; do
    [[ -d "$whole/$tag" ]] && continue
    fetch "$tag" "$cache" || continue
    unzip -q -o "$cache/$tag"/*-maven.zip "$path/*" -d "$named/$tag" 2> /dev/null && return 0
  done
  return 1
}

build() {
  # shellcheck disable=SC2046 # one argument per bundle path or directory, none with spaces
  "$scripts/maven-fallback-site.sh" "$site" $(find "$whole" -name '*-maven.zip' | sort) $(find "$named" -mindepth 1 -maxdepth 1 -type d | sort) > /dev/null
}

tags="$(list_tags)"
newest '^v[0-9]' "$core_count"
newest '^vulkan-v[0-9]' "$vulkan_count"
[[ -n "$(find "$whole" -name '*-maven.zip')" ]] || { echo "No release has a bundle to serve." >&2; exit 1; }
build

while true; do
  missing="$("$scripts/maven-fallback-closure.sh" "$site")"
  [[ -n "$missing" ]] || break
  added=0
  while IFS= read -r coordinate; do
    if take "$coordinate"; then added=1; fi
  done <<< "$missing"
  [[ "$added" -eq 1 ]] || break
  build
done

missing="$("$scripts/maven-fallback-closure.sh" "$site")"
if [[ -n "$missing" ]]; then
  echo "The site would serve POMs naming what it cannot hold:" >&2
  echo "$missing" >&2
  exit 1
fi

# What each release adds, as unpacked: whole bundles by their listed sizes, named parts on disk.
unpacked_mb() { unzip -l "$1" | tail -n 1 | awk '{ printf "%d", $1 / 1048576 }'; }
for dir in $(find "$whole" -mindepth 1 -maxdepth 1 -type d | sort -V); do
  echo "Serving $(basename "$dir") whole: $(unpacked_mb "$(find "$dir" -name '*-maven.zip' | head -n 1)") MB"
done
for dir in $(find "$named" -mindepth 1 -maxdepth 1 -type d | sort -V); do
  count="$(find "$dir" -name '*.pom' | wc -l)"
  echo "Serving $count artifacts of $(basename "$dir") (named by a served POM): $(du -sm "$dir" | cut -f 1) MB"
done
size="$(du -sm "$site" | cut -f 1)"
echo "Fallback repository: $size MB in $site"
if [[ -n "$limit_mb" && "$size" -gt "$limit_mb" ]]; then
  echo "The site is $size MB, over $limit_mb MB: serve fewer releases whole." >&2
  exit 1
fi
