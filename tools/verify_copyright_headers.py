#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Gate first-party Kotlin, Python, and shell source headers.

The pre-commit hook runs --all. --staged was the original choice, so an unrelated historical
file could not block a change -- but it meant an unheadered file sat indefinitely and then failed
whichever change happened to touch it next, which is how 32 of them accumulated. Scanning every
tracked file costs well under a second and fails at the point a gap is introduced instead.

--staged remains for a caller that genuinely wants the narrow check.
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
COPYRIGHT = "SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz"
SPDX = "SPDX-License-Identifier" + ": Apache-2.0"
HEADERS = {
    ".kt": ("/*", f" * {COPYRIGHT}", " *", f" * {SPDX}", " */"),
    ".kts": ("/*", f" * {COPYRIGHT}", " *", f" * {SPDX}", " */"),
    ".py": (f"# {COPYRIGHT}", "#", f"# {SPDX}"),
    ".sh": (f"# {COPYRIGHT}", "#", f"# {SPDX}"),
}
EXCLUDED_PARTS = {"build", ".gradle", "vendor", "third_party"}


def git_paths(*args: str) -> list[Path]:
    result = subprocess.run(
        ["git", *args],
        cwd=ROOT,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    )
    return [ROOT / line for line in result.stdout.splitlines() if line]


def paths(staged: bool) -> list[Path]:
    args = ("diff", "--cached", "--name-only", "--diff-filter=ACMR") if staged else ("ls-files",)
    return [
        path
        for path in git_paths(*args)
        if path.is_file() and path.suffix in HEADERS and not (set(path.parts) & EXCLUDED_PARTS)
    ]


def header_start(path: Path) -> int:
    lines = path.read_text(encoding="utf-8").splitlines()
    return 1 if lines and lines[0].startswith("#!") else 0


def error(path: Path) -> str | None:
    expected = HEADERS[path.suffix]
    lines = path.read_text(encoding="utf-8").splitlines()
    start = header_start(path)
    actual = tuple(lines[start : start + len(expected)])
    if actual != expected:
        return (
            f"{path.relative_to(ROOT)}: expected {' / '.join(expected)!r} "
            f"at line {start + 1}, found {' / '.join(actual) or '<missing>'!r}"
        )
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--staged", action="store_true", help="check added, copied, modified, and renamed staged files")
    mode.add_argument("--all", action="store_true", help="check every tracked first-party source file")
    args = parser.parse_args()

    errors = [message for path in paths(args.staged) if (message := error(path))]
    if errors:
        print("Copyright header verification failed:", file=sys.stderr)
        print(*errors, sep="\n", file=sys.stderr)
        return 1
    print(f"Copyright headers OK ({'staged' if args.staged else 'all tracked'} files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
