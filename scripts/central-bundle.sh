#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Turns the folder a release published into (-Pawake.stagingRepository) into one Maven Central
# bundle: it drops what Central does not need, checks that what it does need is there, and zips the
# rest from the repository root.
#
#   scripts/central-bundle.sh build/central-staging build/central-bundle.zip
#
# Gradle writes maven-metadata.xml for every artifact and four checksums for every file, signatures
# included. Central builds its own metadata and needs only an .asc, .md5 and .sha1 next to each file;
# every other file counts against its monthly file limit for nothing.
set -euo pipefail

usage="usage: central-bundle.sh <staging-dir> <bundle.zip>"
staging="${1:?$usage}"
bundle="${2:?$usage}"
[[ -d "$staging" ]] || { echo "No staging repository at $staging" >&2; exit 1; }

find "$staging" -type f \( -name 'maven-metadata.xml*' -o -name '*.sha256' -o -name '*.sha512' \
  -o -name '*.asc.md5' -o -name '*.asc.sha1' \) -delete

problems=0
artifacts=0
while IFS= read -r file; do
  artifacts=$((artifacts + 1))
  for suffix in asc md5 sha1; do
    if [[ ! -f "$file.$suffix" ]]; then
      echo "Missing ${file#"$staging"/}.$suffix" >&2
      problems=$((problems + 1))
    fi
  done
done < <(find "$staging" -type f ! -name '*.asc' ! -name '*.md5' ! -name '*.sha1' | sort)

if [[ "$artifacts" -eq 0 ]]; then
  echo "Nothing was published into $staging." >&2
  exit 1
fi
# A snapshot is never a release, and Central refuses one in a release deployment.
snapshots="$(find "$staging" -type d -name '*-SNAPSHOT')"
if [[ -n "$snapshots" ]]; then
  echo "A snapshot version was staged:" >&2
  echo "$snapshots" >&2
  problems=$((problems + 1))
fi
if [[ "$problems" -gt 0 ]]; then
  echo "The bundle is incomplete ($problems problems); nothing was zipped." >&2
  exit 1
fi

mkdir -p "$(dirname "$bundle")"
bundle="$(cd "$(dirname "$bundle")" && pwd)/$(basename "$bundle")"
rm -f "$bundle"
(cd "$staging" && zip -qrX "$bundle" .)

files="$(find "$staging" -type f | wc -l | tr -d ' ')"
bytes="$(find "$staging" -type f -exec cat {} + | wc -c | tr -d ' ')"
summary="Central bundle: $files files ($artifacts artifacts), $bytes bytes"
echo "$summary"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then echo "$summary" >> "$GITHUB_STEP_SUMMARY"; fi
