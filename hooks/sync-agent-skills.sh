#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# Keeps the deployed agent skills (.agents/ and .claude/) matching .agents/skills.lock.toml.
# Costs one local check when they already match; installs only when the lockfile moved.
#
# Syncs the main checkout even when run from a linked worktree: the app's worktrees live under
# .claude/worktrees/ and read the skills of the checkout that encloses them.
#
# Run by .githooks/post-merge and post-checkout, and by Claude Code at session start
# (.claude/settings.json). Never fails its caller: a skipped sync prints one line to stderr.
set -uo pipefail

# A git hook exports GIT_DIR and friends; the installer's own git commands must not inherit them.
unset $(git rev-parse --local-env-vars 2>/dev/null)

skip() {
  echo "agent skills: $1 -- see 'Agent skill bundles' in CLAUDE.md to install by hand" >&2
  exit 0
}

common="$(git rev-parse --path-format=absolute --git-common-dir 2>/dev/null)" || exit 0
root="$(dirname "$common")"
[[ -f "$root/.agents/skills.lock.toml" ]] || exit 0
python3 -c 'import tomllib' 2>/dev/null || skip "the installer needs Python 3.11 or newer"

bootstrap="$root/.agents/vendor/awake-agent-skills-bootstrap"
installer="$bootstrap/scripts/install_consumer.py"
if [[ ! -f "$installer" ]]; then
  git clone --quiet --depth 1 https://github.com/awakekt/awake-agent-skills "$bootstrap" 2>/dev/null ||
    skip "could not fetch the installer"
fi

python3 "$installer" --project "$root" --check >/dev/null 2>&1 && exit 0
# The lockfile moved; a new pin can need a newer installer, so refresh it before installing.
git -C "$bootstrap" pull --quiet --ff-only 2>/dev/null
python3 "$installer" --project "$root" 2>&1 | tail -n 1 >&2 || skip "the install failed"
