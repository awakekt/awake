#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks scripts/maven-fallback-site.sh against two throwaway release bundles.
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/maven-fallback-site.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cd "$work"

bundle() { # zip version artifact-dir...
  local zip="$1" version="$2"
  shift 2
  rm -rf content && mkdir content
  for dir in "$@"; do
    local name
    name="$(basename "$dir")"
    mkdir -p "content/$dir/$version"
    for file in "$name-$version.pom" "$name-$version.jar"; do
      printf '%s\n' "$file" > "content/$dir/$version/$file"
      for suffix in asc md5 sha1; do printf 'x\n' > "content/$dir/$version/$file.$suffix"; done
    done
  done
  (cd content && zip -qr "../$zip" .)
}
math=com/awakekt/awake/core/math
marker=com/awakekt/awake/plugin/library/com.awakekt.awake.plugin.library.gradle.plugin
bundle old.zip 0.9.0 "$math"
bundle new.zip 0.10.0 "$math" "$marker"

failures=0
pass() { echo "ok   $1"; }
fail() { echo "FAIL $1"; failures=$((failures + 1)); }

if "$script" site new.zip old.zip > out.txt; then pass "builds from two bundles"; else fail "builds from two bundles"; fi
if [[ -f "site/$math/0.9.0/math-0.9.0.jar.asc" && -f "site/$math/0.10.0/math-0.10.0.pom" ]]; then
  pass "keeps both releases' files, signatures included"; else fail "keeps both releases' files"; fi

metadata="site/$math/maven-metadata.xml"
expected_versions="$(printf '      <version>0.9.0</version>\n      <version>0.10.0</version>')"
if [[ "$(grep '<version>' "$metadata")" == "$expected_versions" ]]; then pass "lists versions in version order"; else
  fail "lists versions in version order: $(grep '<version>' "$metadata")"; fi
if grep -q '<release>0.10.0</release>' "$metadata" && grep -q '<latest>0.10.0</latest>' "$metadata"; then
  pass "names the newest as latest and release"; else fail "names the newest: $(cat "$metadata")"; fi
if grep -q '<groupId>com.awakekt.awake.core</groupId>' "$metadata" && grep -q '<artifactId>math</artifactId>' "$metadata"; then
  pass "reads coordinates from the path"; else fail "reads coordinates: $(cat "$metadata")"; fi
if [[ "$(cat "$metadata.sha1")" == "$(sha1sum "$metadata" | cut -d ' ' -f 1)" ]]; then pass "checksums its metadata"; else fail "checksums its metadata"; fi

marker_metadata="site/$marker/maven-metadata.xml"
if grep -q '<groupId>com.awakekt.awake.plugin.library</groupId>' "$marker_metadata" && grep -q '<version>0.10.0</version>' "$marker_metadata"; then
  pass "serves plugin markers too"; else fail "serves plugin markers: $(cat "$marker_metadata")"; fi

if grep -q 'maven("https://awakekt.github.io/awake/")' site/index.html &&
  [[ "$(grep -o '<li><code>[^<]*' site/index.html | sed 's|<li><code>||' | tr '\n' ' ')" == "0.10.0 0.9.0 " ]]; then
  pass "index says how to use it and lists releases newest first"; else fail "index: $(cat site/index.html)"; fi

if "$script" site 2> /dev/null; then fail "refuses no bundles"; else pass "refuses no bundles"; fi

exit "$failures"
