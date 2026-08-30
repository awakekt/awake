# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: exit 1 means a script computes a repo root that is not the repo root.
"""Every tool script derives the repo root by climbing from `__file__`. Moving one breaks that.

That is not hypothetical: `compare_component_crops.py`, `generate_ui_status.py` and
`generate_ui_parity_report.py` moved from `tools/` into `.agents/skills/awake-ui-verification/scripts/`
and kept `parent.parent`, which had been correct one directory up. Two then resolved paths against
`skills/awake-ui-verification/` and one crashed outright -- and nothing noticed, because no test ran
them.

Checking the climb count against the file's real depth catches the whole class without executing
anything: a script at depth N must climb exactly N levels.
"""
from __future__ import annotations

import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]

# The two forms are off by one against each other: `parents[0]` is the containing directory, which
# is what a single `.parent` gives. So `parents[N]` climbs N+1 and a chain of K `.parent`s climbs K.
# `parents[...]` must come first in the alternation: `.parent` also matches the prefix of
# `.parents[3]`, which would silently read a climb of 3 as a climb of 1.
ROOT_EXPR = re.compile(r"Path\(__file__\)\.resolve\(\)(\.parents\[(\d+)\]|(?:\.parent\b)+)")

# A shell script climbs by counting `..` segments off its own directory. Same bug, different
# spelling: `ui_preview_watch.sh` moved three levels down and kept `cd "$(dirname "$0")/.."`,
# landing two directories below the root and finding no `./gradlew`. The Python-only walk below
# could not see it, which is why the gate passed while a moved script was broken.
SHELL_ROOT_EXPR = re.compile(r"""cd\s+"\$\(dirname\s+"\$0"\)((?:/\.\.)+)"?""")

# ".agents/skills", not "skills": skills moved there and this tuple kept the old root, so the
# shell branch below silently dropped from 3 matches to 2 and the walk stopped seeing every
# script the docstring above says it covers.
SEARCH_DIRS = ("tools", ".agents/skills", "scripts")


def scripts_declaring_a_root() -> list[tuple[Path, int, int]]:
    """(path, levels it climbs, levels it must climb) for every script that computes a root."""
    found = []
    for directory in SEARCH_DIRS:
        for path in sorted(
            [*(REPO_ROOT / directory).rglob("*.py"), *(REPO_ROOT / directory).rglob("*.sh")],
        ):
            if any(part in {"node_modules", ".venv", ".pytest_cache"} for part in path.parts):
                continue
            text = path.read_text()
            if path.suffix == ".sh":
                shell = SHELL_ROOT_EXPR.search(text)
                if not shell:
                    continue
                climbs = shell.group(1).count("..")
                found.append((path, climbs, len(path.resolve().relative_to(REPO_ROOT).parts) - 1))
                continue
            match = ROOT_EXPR.search(text)
            if not match:
                continue
            climbs = int(match.group(2)) + 1 if match.group(2) else match.group(1).count(".parent")
            # A bare `.parent` is "my own directory" -- the `sys.path`/sibling-import idiom, not a
            # root. Only a file already at the repo root could mean the root by climbing once.
            if climbs == 1:
                continue
            required = len(path.resolve().relative_to(REPO_ROOT).parts)
            found.append((path, climbs, required))
    return found


def test_every_script_climbs_to_the_actual_repo_root() -> None:
    wrong = [
        f"{p.relative_to(REPO_ROOT)}: climbs {got}, needs {want}"
        for p, got, want in scripts_declaring_a_root()
        if got != want
    ]
    assert not wrong, "scripts resolve paths against the wrong root:\n  " + "\n  ".join(wrong)


def test_the_check_actually_found_scripts() -> None:
    # Without this, a regex that stops matching turns the test above into a vacuous pass -- the
    # exact failure mode `ui-ownership-convention` shipped for months.
    rows = scripts_declaring_a_root()
    assert len(rows) >= 10
    # Counted separately: the shell branch is a second regex against a second file type, and a
    # total-only floor stays green if it stops matching entirely.
    assert sum(1 for path, _, _ in rows if path.suffix == ".sh") >= 3
