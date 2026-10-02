#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Writes a before/after table of the committed baseline PNGs a pull request adds, changes or
# removes, or nothing when it touches none.
#
# Usage: scripts/baseline-diff-comment.sh <base-sha> <head-sha> <owner/repo>
#
# A baseline change is the review evidence for a visual change: the test fails until the PNG is
# re-recorded, so every pixel that moved shows up here.
set -euo pipefail

base="$1" head="$2" repo="$3"
raw="https://raw.githubusercontent.com/$repo"
width=320

# Before is the merge base, the version this PR actually changed.
before_sha="$(git merge-base "$base" "$head")"
changes="$(git diff --name-status --no-renames "$before_sha" "$head" -- '*/baselines/*.png' '*/baselines/**/*.png')"
[[ -n "$changes" ]] || exit 0

echo "### Baselines changed in this PR"
echo
echo "| Baseline | Before | After |"
echo "| --- | --- | --- |"
while IFS=$'\t' read -r status path; do
  before="<img src=\"$raw/$before_sha/$path\" width=\"$width\">"
  after="<img src=\"$raw/$head/$path\" width=\"$width\">"
  [[ "$status" == A ]] && before="_new_"
  [[ "$status" == D ]] && after="_removed_"
  echo "| \`${path%%/src/*}\` \`${path#*/baselines/}\` | $before | $after |"
done <<<"$changes"
echo
echo "Re-recorded with \`-DAWAKE_RECORD_SNAPSHOTS=true\`. Each one should be a change this PR means to make."
