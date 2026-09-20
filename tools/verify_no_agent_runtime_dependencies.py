#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: product tooling must run from a clean checkout without deployed agent bundles.
"""Reject product-runtime references to the generated `.agents` deployment surface."""

from __future__ import annotations

import sys
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[1]
SCANNED_ROOTS = (".github", "build-logic", "scripts", "tools", "hooks")
# This hook identifies deployed mirrors rather than executing them. The checker itself naturally
# carries the forbidden token as well.
EXEMPT = {
    "hooks/block-edit-vendored-skills.sh",
    "tools/verify_no_agent_runtime_dependencies.py",
    "tools/verify_agent_skills_lock.py",
    "tools/verify_doc_coordinates.py",
}


def main() -> int:
    failures: list[str] = []
    for root in SCANNED_ROOTS:
        directory = REPO_ROOT / root
        if not directory.exists():
            continue
        for path in directory.rglob("*"):
            if not path.is_file() or path.suffix not in {".py", ".sh", ".kts", ".yml", ".yaml"}:
                continue
            relative = path.relative_to(REPO_ROOT).as_posix()
            if relative in EXEMPT:
                continue
            for line_number, line in enumerate(path.read_text(encoding="utf-8", errors="ignore").splitlines(), 1):
                if ".agents/" in line:
                    failures.append(f"{relative}:{line_number}: product tooling must not depend on .agents")
    if failures:
        print("Agent-runtime dependency check failed:", *failures, sep="\n", file=sys.stderr)
        return 1
    print("Agent-runtime dependency check passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
