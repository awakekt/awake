#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Require declared provenance whenever staged source carries third-party copyright."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
REGISTRY = ROOT / "docs/reference/source-provenance.json"
PROJECT_COPYRIGHT = "2023-2026 Ron June Valdoz"
SOURCE_SUFFIXES = {".kt", ".kts", ".py", ".sh"}
EXCLUDED_PARTS = {"build", ".gradle", "vendor", "third_party"}
COPYRIGHT = re.compile(r"^\s*(?://|#|\*)\s*SPDX-FileCopyrightText:\s*(.+?)\s*$", re.M)
SNIPPET = re.compile(r"^\s*(?://|#|\*)\s*SPDX-SnippetBegin\s*$", re.M)


def git_paths(*args: str) -> list[Path]:
    result = subprocess.run(
        ["git", *args], cwd=ROOT, check=True, text=True, stdout=subprocess.PIPE
    )
    return [ROOT / value for value in result.stdout.splitlines() if value]


def source_paths(staged: bool) -> list[Path]:
    args = ("diff", "--cached", "--name-only", "--diff-filter=ACMR") if staged else ("ls-files",)
    return [
        path
        for path in git_paths(*args)
        if path.suffix in SOURCE_SUFFIXES and not (set(path.parts) & EXCLUDED_PARTS)
    ]


def registry_entries() -> set[tuple[str, str]]:
    if not REGISTRY.exists():
        return set()
    data = json.loads(REGISTRY.read_text(encoding="utf-8"))
    if data.get("version") != 1 or not isinstance(data.get("entries"), list):
        raise ValueError(f"{REGISTRY.relative_to(ROOT)} must contain version 1 and an entries array")
    return {
        (entry["path"], entry["scope"])
        for entry in data["entries"]
        if isinstance(entry, dict) and isinstance(entry.get("path"), str) and entry.get("scope") in {"file", "snippet"}
    }


def error(path: Path, entries: set[tuple[str, str]]) -> str | None:
    text = path.read_text(encoding="utf-8")
    relative = path.relative_to(ROOT).as_posix()
    external_copyright = any(
        value.strip() != PROJECT_COPYRIGHT for value in COPYRIGHT.findall(text)
    )
    needs_file_entry = external_copyright
    needs_snippet_entry = bool(SNIPPET.search(text))
    missing = [
        scope
        for scope, required in (("file", needs_file_entry), ("snippet", needs_snippet_entry))
        if required and (relative, scope) not in entries
    ]
    if missing:
        return (
            f"{relative}: third-party {', '.join(missing)} provenance must be recorded in "
            f"{REGISTRY.relative_to(ROOT)}"
        )
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--staged", action="store_true", help="check added, copied, modified, and renamed staged files")
    mode.add_argument("--all", action="store_true", help="audit every tracked first-party source file")
    args = parser.parse_args()

    try:
        entries = registry_entries()
    except (ValueError, json.JSONDecodeError) as error_value:
        print(f"Source provenance verification failed: {error_value}", file=sys.stderr)
        return 1
    errors = [message for path in source_paths(args.staged) if (message := error(path, entries))]
    if errors:
        print("Source provenance verification failed:", *errors, sep="\n", file=sys.stderr)
        return 1
    print(f"Source provenance OK ({'staged' if args.staged else 'all tracked'} files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
