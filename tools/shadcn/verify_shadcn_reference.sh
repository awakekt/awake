#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: exit 1 means the committed token table is stale.
# Kinds are defined in docs/tasks/2026-08-23-ui-tooling-formalization-plan.md. Only a GATE can fail
# a build; `scripts/awake verify` runs every one.
#
# Two checks the shadcn reference pipeline needs and did not have. See
# docs/reference/shadcn-reference-pipeline.md.
#
#   1. STALE  -- is the committed ShadcnReferenceTokens.kt still what the extractor produces?
#                Fails. A hand-edit of a generated file breaks the chain back to upstream while
#                every test stays green, which is the exact failure the pipeline exists to remove.
#                It has already happened once: OUT_FILE pointed at a module path that no longer
#                existed, and `mkdir(parents=True)` meant a run silently created the dead tree and
#                wrote there.
#
#   2. DRIFT  -- has upstream moved since PINNED_SHA?
#                REPORTS ONLY, never fails and never bumps. Moving the pin changes what the theme
#                has to match, which is a visual decision with its own review.
#
# Trap worth knowing: apps/v4/app/globals.css is the docs site's own theme and HAS diverged from
# the registry. apps/v4/registry/themes.ts is what ships and what the extractor reads.
set -euo pipefail

cd "$(dirname "$0")/../.."
GENERATED="awake/ui/shadcn/src/commonMain/kotlin/com/awakekt/awake/ui/shadcn/ShadcnReferenceTokens.kt"
PINNED_SHA="$(grep -oE '[0-9a-f]{40}' tools/shadcn/fetch_shadcn_reference.sh | head -1)"
status=0

echo "== 1. is the generated table stale? =="
if [ ! -d third_party/shadcn-ui-ref ]; then
  echo "   SKIPPED: run tools/shadcn/fetch_shadcn_reference.sh first (checkout is gitignored)"
else
  before="$(mktemp)"; cp "$GENERATED" "$before"
  python3 tools/shadcn/extract_shadcn_tokens.py >/dev/null
  if diff -q "$before" "$GENERATED" >/dev/null; then
    echo "   OK: committed file matches the extractor"
  else
    echo "   STALE: the committed file is not what the extractor produces."
    echo "   Either it was hand-edited, or the pin moved without a regeneration."
    diff "$before" "$GENERATED" | head -20
    cp "$before" "$GENERATED"   # leave the tree as we found it
    status=1
  fi
  rm -f "$before"
fi

echo "== 2. has upstream moved since the pin? =="
url="https://raw.githubusercontent.com/shadcn-ui/ui/%s/apps/v4/registry/themes.ts"
pinned="$(mktemp)"; head="$(mktemp)"
# shellcheck disable=SC2059
curl -fsS "$(printf "$url" "$PINNED_SHA")" -o "$pinned"
# shellcheck disable=SC2059
curl -fsS "$(printf "$url" main)" -o "$head"
if diff -q "$pinned" "$head" >/dev/null; then
  echo "   OK: registry at $PINNED_SHA is identical to main"
else
  echo "   UPSTREAM MOVED since $PINNED_SHA. Reporting only -- bumping is a visual decision."
  diff "$pinned" "$head" | head -30
fi
rm -f "$pinned" "$head"

exit "$status"
