#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: exit 1 means a detekt baseline suppresses findings in files that no longer exist.
# Kinds are defined in docs/tasks/2026-08-23-ui-tooling-formalization-plan.md. Only a GATE can fail
# a build; `scripts/awake verify` runs every one.
"""Finds detekt baseline entries whose file is gone.

A baseline is a list of accepted findings, and detekt does not prune it when a file moves or is
deleted. The entries simply stop matching anything, which is invisible: the module still passes, and
nobody can tell a live suppression from a dead one by reading the file.

Measured 2026-08-23, before this existed: 34 of 525 entries repo-wide were dead -- and 32 of those
were one module, `awake/ui/graphics`, whose baseline was **80% stale** after `UiPath` and
`UiGradient` moved to `core:graphics2d`. That module had been running with almost no real suppression
and passing, which is the failure this catches.

Subtractive by design. It reports what to delete and never regenerates: `./gradlew detektBaseline`
absorbs *new* findings too, which is how real debt gets laundered into looking accepted.
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]

# `Rule:FileName.kt$Symbol$...` -- the file name is the part that can go stale.
ENTRY = re.compile(r"<ID>([^<]+)</ID>")
FILE_IN_ENTRY = re.compile(r"[^:]+:([^$]+\.kts?)\$")

SKIP = ("/build/", "/.claude/", "/third_party/")


def skipped(baseline: Path) -> bool:
    """Whether [baseline] is under a directory this gate ignores.

    Matched against the repo-RELATIVE path, not the absolute one: a checkout whose own path
    contains a skip token -- a git worktree under `.claude/worktrees/`, or any clone below a
    directory called `build` -- otherwise skips every baseline and reports a clean repo having
    read nothing.
    """
    relative = f"/{baseline.relative_to(REPO_ROOT)}"
    return any(token in relative for token in SKIP)


def stale_entries() -> list[tuple[Path, list[str]]]:
    found = []
    for baseline in sorted(REPO_ROOT.rglob("detekt-baseline.xml")):
        if skipped(baseline):
            continue
        src = baseline.parent / "src"
        if not src.exists():
            continue
        present = {p.name for p in src.rglob("*.kt")}
        dead = []
        for entry in ENTRY.findall(baseline.read_text(encoding="utf-8")):
            match = FILE_IN_ENTRY.match(entry)
            if match and match.group(1) not in present:
                dead.append(entry)
        if dead:
            found.append((baseline.relative_to(REPO_ROOT), dead))
    return found


def main() -> int:
    found = stale_entries()
    if not found:
        print("   OK: every detekt baseline entry names a file that exists")
        return 0

    for baseline, dead in found:
        print(f"   STALE {baseline}: {len(dead)} entr{'y' if len(dead) == 1 else 'ies'}")
        for entry in dead[:5]:
            print(f"      {entry}")
        if len(dead) > 5:
            print(f"      ... and {len(dead) - 5} more")
    print("\nDelete these entries. Do not run `./gradlew detektBaseline` to fix it -- that absorbs")
    print("new findings as well, which turns real debt into accepted debt.")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
