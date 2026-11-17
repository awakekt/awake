#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Decides whether a release tag's run reuses the Consumer result of its release-cut rehearsal
# instead of running Consumer again (#1592, B1). It reuses only when every check below holds, and
# runs Consumer in full on anything else, a missing or unreadable record included:
#   - nothing forces a full run (FORCE is not true);
#   - a rehearsal record exists (RECORD names a readable file);
#   - the record's tree is the tag's tree (TAG_TREE), the exact content the tag publishes;
#   - the record names the same Vulkan publication choice (VULKAN_PUBLISH);
#   - when the templates take Vulkan from Central, Central's latest Vulkan release (CURRENT_VULKAN)
#     is the one the rehearsal built them against.
#
# The record is `key=value` lines: tree, vulkan_publish, template_vulkan and run (the rehearsal's
# run id), written by the rehearsal's Consumer job and found by scripts/consumer-reuse-find.sh.
#
# Prints `reuse=true|false` and `reason=<why>`, for $GITHUB_OUTPUT. Always exits 0.
set -uo pipefail

decide() { # reuse reason
  printf 'reuse=%s\nreason=%s\n' "$1" "$2"
  exit 0
}

[[ "${FORCE:-false}" == true ]] && decide false "a full Consumer run was forced (AWAKE_FULL_CONSUMER)"
[[ -n "${TAG_TREE:-}" ]] || decide false "the tag's tree is unknown"
record=${RECORD:-}
[[ -n "$record" && -r "$record" ]] || decide false "no green release-cut rehearsal was found for this tree"

field() { sed -n "s/^$1=//p" "$record" | head -n 1; }
tree=$(field tree)
run=$(field run)
recorded_publish=$(field vulkan_publish)
recorded_vulkan=$(field template_vulkan)

[[ "$tree" =~ ^[0-9a-f]{40}$ ]] || decide false "the rehearsal record has no tree"
[[ "$tree" == "$TAG_TREE" ]] || decide false "the rehearsal verified tree $tree, not the tag's $TAG_TREE"
publish=${VULKAN_PUBLISH:-false}
[[ "$recorded_publish" == "$publish" ]] ||
  decide false "the rehearsal's Vulkan publication choice ($recorded_publish) differs from the tag's ($publish)"
if [[ "$publish" != true ]]; then
  current=${CURRENT_VULKAN:-}
  [[ -n "$current" ]] || decide false "Central's latest Vulkan release could not be read"
  [[ -n "$recorded_vulkan" ]] || decide false "the rehearsal record has no Vulkan version"
  [[ "$current" == "$recorded_vulkan" ]] ||
    decide false "Central's latest Vulkan release is $current; the rehearsal built the templates against $recorded_vulkan"
fi
decide true "release-cut rehearsal run $run verified this exact tree ($tree) against the same Central versions"
