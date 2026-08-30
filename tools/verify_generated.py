#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: exit 1 means a committed generated file is not what its generator produces.
# Kinds are defined in docs/tasks/2026-08-23-ui-tooling-formalization-plan.md. Only a GATE can fail
# a build; `scripts/awake verify` runs every one.
"""Re-runs each generator and diffs its committed output.

A generator is the dangerous middle of the three tool kinds: it looks like a gate because it runs
and succeeds, but it *writes*, so a committed output that stopped matching is invisible. That is not
hypothetical -- `extract_shadcn_tokens.py`'s `OUT_FILE` pointed at a module path that no longer
existed, and `mkdir(parents=True)` meant a run silently created the dead tree and wrote there while
the real file went untouched. Nobody noticed, because git does not track empty directories.

One table-driven check rather than one script per generator: every one of these is "run a command,
diff some paths", and four bespoke wrappers would drift apart the way three copies of a clipping
algorithm once did.

`instantiate_roboto.py` is deliberately absent: its output is a TTF, and re-running changes nine
bytes across three clusters -- `head.modified` and the checksums -- with the size and every glyph
identical. It is not byte-deterministic, and a gate that always fails is worse than no gate. Gating
it would need a normalised comparison that parses both fonts and ignores the volatile tables.

`extract_shadcn_tokens.py` is deliberately absent -- `verify_shadcn_reference.sh` already gates it
*and* reports upstream drift, which needs the pinned checkout this script does not want to require.
"""
from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent


@dataclass(frozen=True)
class Generator:
    name: str
    command: list[str]
    outputs: list[str]
    """Why this generator exists, for the failure message."""
    what: str
    """A shell command whose failure means "skip, not fail" -- a missing optional toolchain."""
    requires: list[str] = field(default_factory=list)
    """True when the command reports drift itself and writes nothing, so no snapshot is needed."""
    checks_itself: bool = False


GENERATORS: list[Generator] = [
    Generator(
        name="tailwind-scale",
        command=["./gradlew", ":awake:ui:tailwind-generator:generateTailwindScale", "-q"],
        outputs=["awake/ui/tailwind/src/commonMain/kotlin/io/github/awakelab/awake/ui/tailwind/Tw.kt"],
        what="Tailwind spacing/radius scale",
    ),
    Generator(
        name="reference-components",
        # --check reports without writing, so this needs no snapshot/restore dance.
        command=[sys.executable, "tools/shadcn/vendor_reference_components.py", "--check"],
        outputs=["tools/shadcn/reference-app/src/ui"],
        what="shadcn component sources the parity screenshots are taken from",
        checks_itself=True,
    ),
    Generator(
        name="font-atlas",
        command=["./gradlew", ":awake:ui:font-atlas-generator:generateFontAtlas", "-q"],
        outputs=[
            "awake/ui/text/src/commonMain/kotlin/io/github/awakelab/awake/ui/font/RobotoRegularUiFontData.kt",
        ],
        what="packed glyph atlas and metrics",
    ),
]


def snapshot(paths: list[Path], into: Path) -> None:
    for i, p in enumerate(paths):
        target = into / str(i)
        if p.is_dir():
            shutil.copytree(p, target)
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(p, target)


def differs(paths: list[Path], against: Path) -> list[str]:
    changed = []
    for i, p in enumerate(paths):
        saved = against / str(i)
        # -w: generated Kotlin is formatted after generation, so indentation drifts without the
        # content changing. Whitespace-insensitive keeps this a content gate rather than a
        # formatter gate -- spotless is not a gate in this repo either.
        flags = ["-rqw"] if p.is_dir() else ["-qw"]
        result = subprocess.run(["diff", *flags, str(saved), str(p)], capture_output=True, text=True)
        if result.returncode != 0:
            changed.append(f"{p.relative_to(REPO_ROOT)}: {result.stdout.strip() or 'differs'}")
    return changed


def restore(paths: list[Path], frm: Path) -> None:
    for i, p in enumerate(paths):
        saved = frm / str(i)
        if p.is_dir():
            shutil.rmtree(p)
            shutil.copytree(saved, p)
        else:
            shutil.copy2(saved, p)


def check(gen: Generator) -> str:
    """Returns "" on pass, a reason on skip-or-fail. Always leaves the tree as it found it."""
    paths = [REPO_ROOT / o for o in gen.outputs]
    missing = [p for p in paths if not p.exists()]
    if missing:
        return f"SKIP: output missing ({missing[0].relative_to(REPO_ROOT)}) -- never generated here"

    if gen.requires:
        probe = subprocess.run(gen.requires, capture_output=True, cwd=REPO_ROOT)
        if probe.returncode != 0:
            return "SKIP: optional toolchain absent"

    if gen.checks_itself:
        run = subprocess.run(gen.command, cwd=REPO_ROOT, capture_output=True, text=True)
        if run.returncode == 2:
            return f"SKIP: {run.stderr.strip().splitlines()[0] if run.stderr.strip() else 'prerequisite missing'}"
        if run.returncode != 0:
            return "STALE: " + run.stdout.strip()
        return ""

    with tempfile.TemporaryDirectory() as tmp:
        saved = Path(tmp)
        snapshot(paths, saved)
        run = subprocess.run(gen.command, cwd=REPO_ROOT, capture_output=True, text=True)
        if run.returncode != 0:
            restore(paths, saved)
            return f"FAIL: the generator itself errored\n{run.stderr.strip()[:600]}"
        changed = differs(paths, saved)
        restore(paths, saved)
        if changed:
            return "STALE: committed output is not what the generator produces\n  " + "\n  ".join(changed)
    return ""


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", help="check a single generator by name")
    args = parser.parse_args(argv)

    selected = [g for g in GENERATORS if args.only is None or g.name == args.only]
    if not selected:
        known = ", ".join(g.name for g in GENERATORS)
        print(f"no generator named {args.only!r}. Known: {known}", file=sys.stderr)
        return 2

    failed = []
    for gen in selected:
        print(f"-- {gen.name}: {gen.what}", flush=True)
        reason = check(gen)
        if not reason:
            print("   OK: committed output matches the generator")
        elif reason.startswith("SKIP"):
            print(f"   {reason}")
        else:
            print(f"   {reason}")
            failed.append(gen.name)

    if failed:
        print(f"\nSTALE: {', '.join(failed)} -- re-run the generator and commit the diff")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
