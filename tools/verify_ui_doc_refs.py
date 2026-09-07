#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: active canonical docs must not advertise deleted UI modules.
"""Reject retired UI module names in Awake's active canonical documentation."""

from __future__ import annotations

import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]

ACTIVE_DOCS = (
    REPO_ROOT / "docs" / "README.md",
    REPO_ROOT / "docs" / "architecture.md",
    REPO_ROOT / "docs" / "mvp-plan.md",
    *sorted((REPO_ROOT / "docs" / "reference").glob("*.md")),
)

FORBIDDEN = (
    "awake:ui:ui-core",
    "awake:ui:headless",
    "awake:ui:testing",
    "awake:engine:ui-dsl",
    "awake:engine:ui:ui-core",
    "awake:engine:ui:ui-headless",
    "awake:engine:ui:ui-shadcn",
    "awake:engine:ui:ui-testing",
)


def main() -> int:
    failures: list[str] = []
    for path in ACTIVE_DOCS:
        if not path.exists():
            continue
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            for token in FORBIDDEN:
                if token in line:
                    display_path = path.relative_to(REPO_ROOT) if path.is_relative_to(REPO_ROOT) else path
                    failures.append(f"{display_path}:{line_number}: retired module {token}")
    if failures:
        print("Active documentation contains retired UI module references:", file=sys.stderr)
        print("\n".join(failures), file=sys.stderr)
        return 1
    print(f"UI documentation module-reference check passed ({len(ACTIVE_DOCS)} files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
