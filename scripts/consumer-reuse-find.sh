#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Finds the record of a green release-cut rehearsal of [tag] that verified [tree], for
# scripts/consumer-reuse.sh (#1592, B1), and prints the path of that record, or nothing.
#
# It trusts the Actions API, not the tree: the rehearsal is a Publish run (build-and-publish.yml) of
# the `release-cut/<tag>` pull request whose Consumer job concluded success, newest first; its record
# is that run's `consumer-rehearsal` artifact. A record whose tree is not [tree] is passed over. The
# run that wrote it ran this same tree's workflow, so it is what the tag would run. Anything that
# fails here prints nothing, which runs Consumer in full. Always exits 0.
#
# Usage: scripts/consumer-reuse-find.sh <owner/repo> <tag> <tree> <dir>   (needs GH_TOKEN)
set -uo pipefail

repo=$1 tag=$2 tree=$3 dir=$4
runs=$(gh api "repos/$repo/actions/workflows/build-and-publish.yml/runs?event=pull_request&branch=release-cut/$tag&per_page=20" \
  --jq '.workflow_runs[].id' 2>/dev/null) || exit 0

for run in $runs; do
  [[ "$run" =~ ^[0-9]+$ ]] || continue
  consumer=$(gh api "repos/$repo/actions/runs/$run/jobs?per_page=100" \
    --jq '[.jobs[] | select(.name == "Consumer")][0].conclusion' 2>/dev/null) || continue
  [[ "$consumer" == success ]] || continue
  into="$dir/$run"
  mkdir -p "$into"
  gh run download "$run" --repo "$repo" --name consumer-rehearsal --dir "$into" >/dev/null 2>&1 || continue
  record="$into/consumer-rehearsal.txt"
  [[ -r "$record" ]] || continue
  if [[ "$(sed -n 's/^tree=//p' "$record" | head -n 1)" == "$tree" ]]; then
    echo "$record"
    exit 0
  fi
done
exit 0
