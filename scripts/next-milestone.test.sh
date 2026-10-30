#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Checks scripts/next-milestone.sh against fixed tag and milestone lists, one case per line.
set -euo pipefail

script="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/next-milestone.sh"
failures=0

expect() { # name tags titles expected
  local actual status=0
  actual="$(NEXT_MILESTONE_TAGS="$2" NEXT_MILESTONE_TITLES="$3" "$script" owner/repo)" || status=$?
  [[ $status -eq 0 ]] || actual="exit $status"
  if [[ "$actual" == "$4" ]]; then echo "ok   $1"; else echo "FAIL $1: expected $4, got $actual"; failures=$((failures + 1)); fi
}

old_open=$'v0.19.1\nv0.22.0\nv0.28.0\nv0.29.0\nv0.27.1'
expect "skips stale and released milestones" $'v0.27.1\nv0.28.0\nvulkan-v0.1.38' "$old_open" v0.29.0
expect "compares versions, not text" $'v0.9.0' $'v0.10.0\nv0.9.1' v0.9.1
expect "ignores previews and vulkan tags" $'v0.28.0\nv0.29.0-rc.1\nvulkan-v0.2.0' $'v0.29.0\nv0.30.0' v0.29.0
expect "no stable tag yet" $'v0.1.0-rc.13' $'v0.2.0\nv0.1.0' v0.1.0
expect "ignores non-version titles" $'v0.28.0' $'Backlog\nv0.29.0' v0.29.0
expect "nothing above the latest release" $'v0.29.0' $'v0.28.0\nv0.29.0' "exit 1"
expect "no milestones" $'v0.29.0' '' "exit 1"

[[ $failures -eq 0 ]] || { echo "$failures case(s) failed"; exit 1; }
