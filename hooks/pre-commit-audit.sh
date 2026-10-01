#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
# Pre-commit hook: profile-parity check, commit summary, and a changelog-fragment nudge.
# Wired via .githooks/pre-commit (core.hooksPath), alongside the existing
# commit-msg and pre-push hooks in the same directory.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"


# Cheap per-commit summary: file/area counts only, mechanically derived from the staged
# diff -- no new scan, no judgment calls. "Pointers" is deliberately a short, hand-maintained
# list (below), not auto-derived -- "what's currently open" is exactly the kind of fact that
# goes stale silently if computed instead of maintained (see this repo's own ui-validation.md
# incident, 2026-08-17: two tables disagreeing because nobody had to touch both by hand).
STAGED_ALL="$(git diff --cached --name-only || true)"
if [[ -n "$STAGED_ALL" ]]; then
  FILE_COUNT="$(echo "$STAGED_ALL" | wc -l | tr -d ' ')"
  STAT_TAIL="$(git diff --cached --shortstat | cut -d',' -f2- | sed 's/^ *//')"
  AREAS="$(echo "$STAGED_ALL" | xargs -n1 dirname | cut -d/ -f1-2 | sort | uniq -c \
    | sort -rn | awk '{printf "%s (%s), ", $2, $1}' | sed 's/, $//')"
  echo ""
  echo "Commit summary"
  echo "  $FILE_COUNT files changed${STAT_TAIL:+, }$STAT_TAIL"
  echo "  by area: $AREAS"
  echo ""
  # 2026-09-01: the ui-refactor audit pointer was removed, not ticked off -- its package 6 plans
  # sweeps over `ui-headless` widgets that no longer exist, so it had been directing every reader at
  # a week of work against deleted modules. Closing note is at the top of that file.
  echo "  Open pointers (update this list by hand when something opens/closes):"
  echo "    - github.com/ronjunevaldoz/kmp-agent-skills#6 (docs-hygiene consumer-project gap, open)"
fi

STAGED_KT="$(git diff --cached --name-only | grep -E '\.(kt|kts)$' || true)"

# Nudge (not block) when a commit changes real, non-test Kotlin source but stages no changelog
# fragment. Skips test-source and generated files -- those don't usually warrant an entry, and
# false-positiving on every test-only commit would train people to ignore the warning. CI
# enforces fragments on feat/fix PRs; this just reminds earlier.
NON_TEST_KT="$(echo "$STAGED_KT" | grep -vE '(Test|Tests)\.kts?$|/(commonTest|desktopTest|androidTest|iosTest|jvmTest|wasmJsTest)/|/build/|/generator/' || true)"
if [[ -n "$NON_TEST_KT" ]] && ! echo "$STAGED_ALL" | grep -q '^changelog/unreleased/'; then
  echo ""
  echo "Reminder: this commit touches non-test Kotlin source but adds no changelog fragment."
  echo "  If this is user-visible behavior, add changelog/unreleased/<section>/<branch-name>.md"
  echo "  (not CHANGELOG.md: see changelog/unreleased/README.md)."
fi

# The architecture audit used to run here and was removed 2026-08-22, because it could not
# succeed: the old audit helper only accepted a project root, so it always scanned the whole repo,
# which takes ~2m05s against a 20s alarm. Every commit paid 20 seconds to print "found issues
# or timed out" -- a message that was true either way and so carried no information. A warning
# that always fires trains you to stop reading warnings, which costs more than the check gave.
#
# It is a useful report, just not a per-commit one. Run it by hand when touching module
# boundaries, and read it with the severity mix in mind (661 findings as of removal, mostly
# LOW comment-style):
#
#     Run the optional vendor architecture audit from its installed bundle.
#
# Not moved to CI: 661 pre-existing findings would land it permanently red, which is the same
# always-fires failure in a slower place. Worth wiring up once that backlog is triaged.

exit 0
