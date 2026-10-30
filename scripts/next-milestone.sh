#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Prints the title of the milestone the next Core release closes: the lowest open `vX.Y.Z`
# milestone above the latest stable `vX.Y.Z` tag. Older milestones left open hold carried-over
# issues, not the next release, so "the earliest open milestone" would pick one of them.
#
# Usage: scripts/next-milestone.sh <owner/repo>
#   Reads the tags and open milestones with `gh api`. For tests, NEXT_MILESTONE_TAGS and
#   NEXT_MILESTONE_TITLES replace them, one name per line.
# Exits 1, printing nothing, when no open milestone is above the latest release.
set -euo pipefail

repo="${1:?usage: next-milestone.sh <owner/repo>}"
semver='^v[0-9]+\.[0-9]+\.[0-9]+$'

if [[ -n "${NEXT_MILESTONE_TAGS+x}" ]]; then
  tags="$NEXT_MILESTONE_TAGS"
else
  tags="$(gh api --paginate "repos/$repo/git/matching-refs/tags/v" --jq '.[].ref | ltrimstr("refs/tags/")')"
fi
if [[ -n "${NEXT_MILESTONE_TITLES+x}" ]]; then
  titles="$NEXT_MILESTONE_TITLES"
else
  titles="$(gh api --paginate "repos/$repo/milestones?state=open&per_page=100" --jq '.[].title')"
fi

above() { # version floor -> true when version sorts after floor
  [[ "$1" != "$2" && "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -n 1)" == "$1" ]]
}

latest="$(grep -E "$semver" <<< "$tags" | sort -V | tail -n 1 || true)"
next=""
while read -r title; do
  if [[ -z "$latest" ]] || above "$title" "$latest"; then
    next="$title"
    break
  fi
done < <(grep -E "$semver" <<< "$titles" | sort -V || true)

[[ -n "$next" ]] || exit 1
echo "$next"
