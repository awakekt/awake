#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
# PreToolUse hook: blocks Edit/Write calls that target a deployed skill mirror
# instead of the real source. Deployed copies under .claude/skills/,
# .agents/skills/, .codex/skills/, or .gemini/skills/ are synced FROM a source
# — either this repo's own skills/<name>/SKILL.md (bundled) or a consumer
# project's root skills/<name>/SKILL.md (project-owned custom skill) — and any
# direct edit there is silently overwritten by the next sync, or worse,
# diverges unnoticed until audit_project.py's agent-setup drift check catches
# it after the fact. This hook blocks the edit before it happens.
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

is_tracked_in_current_repo() {
  local repo_root relative_target
  repo_root="$(git rev-parse --show-toplevel 2>/dev/null || true)"
  [[ -n "$repo_root" ]] || return 1
  case "$TARGET" in
    "$repo_root"/*) relative_target="${TARGET#"$repo_root/"}" ;;
    *) relative_target="$TARGET" ;;
  esac
  git -C "$repo_root" ls-files --error-unmatch -- "$relative_target" >/dev/null 2>&1
}

if [[ -z "$TARGET" ]]; then
  exit 0
fi

if [[ "$TARGET" =~ (^|/)\.claude/skills/ ]] \
  || [[ "$TARGET" =~ (^|/)\.agents/skills/ ]] \
  || [[ "$TARGET" =~ (^|/)\.codex/skills/ ]] \
  || [[ "$TARGET" =~ (^|/)\.gemini/skills/ ]]; then
  # Awake's tracked .agents/skills/awake-* files are source, not deployed mirrors.
  # More generally, never block a file that is tracked by the current repository.
  if is_tracked_in_current_repo; then
    exit 0
  fi
  cat >&2 <<'EOF'
Blocked: this path is a deployed skill mirror, not the source. Edits here get
silently overwritten by the next sync, or drift unnoticed until an audit
catches it.

Edit the real source instead:
  - Bundled kmp-agent-skills skill: edit upstream at
    github.com/ronjunevaldoz/kmp-agent-skills, then re-sync.
  - Project-owned custom skill: edit this project's own root skills/<name>/SKILL.md,
    then re-sync into the deployed copies.
EOF
  exit 2
fi

exit 0
