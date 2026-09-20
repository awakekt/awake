#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
# PreToolUse hook: blocks Edit/Write calls that target a deployed skill mirror
# instead of the real source. Deployed copies under .claude/skills/,
# .agents/skills/, .agents/commands/, .agents/vendor/, .codex/skills/, or .gemini/skills/ are immutable deployments
# from their source lockfile. This hook blocks edits before they can drift.
#
# Wire via a PreToolUse matcher on "Edit|Write" in settings.json — the matcher
# filters by tool; this script only decides whether the target path is a
# deployed mirror.
#
# Usage: block-edit-vendored-skills.sh <target-file-path>
#   Exit 2 blocks the tool call (Claude Code shows stderr to the agent).
#   Exit 0 allows it (not a mirror path, or no path given).

set -euo pipefail

TARGET="${1:-}"

if [[ -z "$TARGET" ]]; then
  exit 0
fi

if [[ "$TARGET" =~ (^|/)\.claude/skills/ ]] \
  || [[ "$TARGET" =~ (^|/)\.agents/skills/ ]] \
  || [[ "$TARGET" =~ (^|/)\.agents/commands/ ]] \
  || [[ "$TARGET" =~ (^|/)\.agents/vendor/ ]] \
  || [[ "$TARGET" =~ (^|/)\.codex/skills/ ]] \
  || [[ "$TARGET" =~ (^|/)\.gemini/skills/ ]]; then
  cat >&2 <<'EOF'
Blocked: this path is a deployed skill mirror, not the source. Edits here get
silently overwritten by the next sync, or drift unnoticed until an audit
catches it.

Edit the real source instead:
  - Awake Core skill: github.com/awakekt/awake-agent-skills
  - Studio Pro skill: the private awake-studio-agent-skills repository
  - Vendor kmp-* skill: github.com/ronjunevaldoz/kmp-agent-skills
EOF
  exit 2
fi

exit 0
