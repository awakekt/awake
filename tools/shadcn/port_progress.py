#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# INVESTIGATION: reports Stage 3 port progress. Never fails a build.
"""How much of `ui-shadcn` still depends on the layers Stage 3 deletes.

Progress is *derived*, never tracked by hand: a file is ported when it no longer imports `ui-core` or
`ui-headless`. That cannot drift out of date the way a checklist does, and it cannot claim a file is
done while the import is still there.

It is deliberately not a gate. Mid-port the number is supposed to be red, and a gate that is red on
purpose for weeks is one people learn to ignore -- then it is still ignored on the day it means
something. `docs/tasks/README.md` says what is open; this says how far along one item is.

    python3 tools/shadcn/port_progress.py
    python3 tools/shadcn/port_progress.py --json
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SHADCN = REPO_ROOT / (
    "awake/ui/shadcn/src/commonMain/kotlin/io/github/awakelab/awake/ui/shadcn"
)

# The layers Stage 3 deletes. An import of one of these packages is what "not ported yet" means.
#
# `theme` and `font` are deliberately absent, and the reason is a real wart rather than an oversight:
# **`awake.ui.theme` is a split package.** `TextStyle` lives in `:awake:ui:text`, which survives and
# which `:compose:ui` itself depends on, while the rest of the package is `ui-core`'s. Counting it
# marked ported files as legacy; not counting it can only under-report, and a genuine `ui-core`
# consumer imports `context`/`scope`/`modifier`/`style` too, so nothing real hides behind it.
LEGACY_IMPORT = re.compile(
    r"^import io\.github\.awakelab\.awake\.ui\."
    r"(headless|context|scope|modifier|style|layouts|graphics|api)\b",
    re.M,
)
TARGET_IMPORT = re.compile(r"^import io\.github\.awakelab\.awake\.compose\.", re.M)

# A top-level `shadcn*` function, with or without a receiver. `*Style` helpers are counted apart
# because the adoption plan folds them into their variants rather than porting them one for one.
DECLARATION = re.compile(r"^(?:internal |public )?fun (?:[\w.<>]+\.)?(shadcn\w+)\s*\(", re.M)

# `Ui`-prefixed types are the names 2026-08-22-ui-prefix-rename-plan.md retires, and its rule is
# opportunistic: rename what a file owns as it is touched, never introduce a new one. A ported file
# still naming them has taken the legacy alias where a canonical name exists, so it is worth seeing
# next to the port status rather than found in a sweep later.
UI_PREFIXED = re.compile(r"\bUi[A-Z]\w*")

# Comments are stripped before matching. A ported file's doc comment naming the legacy type it
# replaced is prose doing its job, not a usage -- flagging it would train people to stop writing the
# explanation, which is the opposite of what this is for.
COMMENTS = re.compile(r"/\*.*?\*/|//[^\n]*", re.S)


@dataclass
class FileReport:
    path: str
    recipes: list[str] = field(default_factory=list)
    style_helpers: list[str] = field(default_factory=list)
    legacy_imports: int = 0
    target_imports: int = 0
    ui_names: list[str] = field(default_factory=list)

    @property
    def status(self) -> str:
        if self.legacy_imports and self.target_imports:
            return "MIXED"
        if self.legacy_imports:
            return "legacy"
        if self.target_imports:
            return "PORTED"
        return "neutral"


def scan() -> list[FileReport]:
    reports = []
    for path in sorted(SHADCN.rglob("*.kt")):
        text = path.read_text(encoding="utf-8")
        names = DECLARATION.findall(text)
        reports.append(
            FileReport(
                path=str(path.relative_to(SHADCN)),
                recipes=[n for n in names if not n.endswith("Style")],
                style_helpers=[n for n in names if n.endswith("Style")],
                legacy_imports=len(LEGACY_IMPORT.findall(text)),
                target_imports=len(TARGET_IMPORT.findall(text)),
                ui_names=sorted(set(UI_PREFIXED.findall(COMMENTS.sub("", text)))),
            )
        )
    return reports


def render(reports: list[FileReport]) -> str:
    rows = [r for r in reports if r.recipes or r.style_helpers]
    width = max((len(r.path) for r in rows), default=10)
    lines = [f"{'file'.ljust(width)}  status   recipes  styles  legacy  Ui*"]
    lines.append("-" * (width + 38))
    for r in sorted(rows, key=lambda r: (r.status != "legacy", r.path)):
        lines.append(
            f"{r.path.ljust(width)}  {r.status:<7}  {len(r.recipes):>7}  "
            f"{len(r.style_helpers):>6}  {r.legacy_imports:>6}  {len(r.ui_names):>3}"
        )

    helpers = sum(len(r.style_helpers) for r in reports)
    ported = sum(len(r.recipes) for r in reports if r.status == "PORTED")
    # A file with neither import needs no port -- pure math or pure data. Counting it as unported
    # would make the denominator grow every time something correct is added, which reads as
    # going backwards.
    blocked = sum(len(r.recipes) for r in reports if r.status in ("legacy", "MIXED"))
    free = sum(len(r.recipes) for r in reports if r.status == "neutral")
    total = ported + blocked
    files_left = sum(1 for r in reports if r.status in ("legacy", "MIXED"))
    pct = (ported / total * 100) if total else 100.0

    lines += [
        "",
        f"recipes        {ported}/{total} ported ({pct:.0f}%)",
        f"style helpers  {helpers} -- folded into their variants as each recipe moves, not ported one for one",
        f"files left     {files_left}",
    ]
    if free:
        lines.append(f"needs no port  {free} already free of both layers")
    stragglers = {n for r in reports if r.status in ("PORTED", "neutral") for n in r.ui_names}
    if stragglers:
        lines.append("")
        lines.append(
            f"Ui* names still used by ported files: {', '.join(sorted(stragglers))}"
        )
        lines.append("Ported code takes the canonical name -- see 2026-08-22-ui-prefix-rename-plan.md.")
    if files_left:
        lines.append("")
        lines.append("A file counts as ported when it no longer imports ui-core or ui-headless.")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", action="store_true", help="machine-readable, for a report")
    args = parser.parse_args(argv)

    if not SHADCN.exists():
        print(f"not found: {SHADCN}", file=sys.stderr)
        return 2

    reports = scan()
    if args.json:
        print(json.dumps([r.__dict__ | {"status": r.status} for r in reports], indent=2))
    else:
        print(render(reports))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
