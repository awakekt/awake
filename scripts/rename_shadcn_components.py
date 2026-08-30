#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""rename_shadcn_components.py — lowercase `shadcn*` entry points to PascalCase.

The rule this serves, from `.agents/skills/awake-ui-authoring/SKILL.md`:

    A public `context(Composer)` `Unit` visual component is a PascalCase noun
    (`ShadcnButton`), not a lowercase helper. Keep an old lowercase `shadcn*`
    entry only as a deprecated source-compatibility delegate.

Only *visual components* are eligible. A lowercase `shadcn*` that returns a value
is a helper, not a component, and PascalCase would be wrong for it -- so the
classifier reads each declaration's return type rather than matching on the name.

Regex, not a Kotlin parser. Everything it cannot classify with confidence is
reported as SKIPPED with a reason instead of being renamed on a guess: a wrong
rename here is silent until something fails to compile, and the point of the
dry run is to see the whole set before any of it moves.

THIS SCRIPT WILL NOT GROW AN `--apply` THAT REWRITES KOTLIN.

`kmp-refactor` classifies a Kotlin function rename as IDE `Refactor > Rename`
work, and names a repo-wide sed over `.kt` as the anti-pattern, because a textual
sweep cannot tell a call from an identical word inside a string. That is not
hypothetical here: 38 string literals in this repo contain a `shadcn*` name --
assertion messages, KDoc samples, parity notes -- and a sweep would rewrite all
of them indiscriminately.

So the division of labour is: the IDE performs the rename, and this script is the
scope and the residue check around it. `--apply` stays unimplemented on purpose.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
SHADCN_ROOT = REPO_ROOT / "awake/ui/shadcn/src/commonMain/kotlin/io/github/awakelab/awake/ui/shadcn"

# `fun shadcnFoo(` at column 0. Nested/member functions are not public entry points.
DECL = re.compile(r"^fun (shadcn[A-Za-z0-9]*)\s*\(", re.MULTILINE)


@dataclass
class Decl:
    name: str
    pascal: str
    file: Path
    line: int
    returns_value: bool
    has_composer_context: bool
    deprecated: bool

    @property
    def eligible(self) -> bool:
        return not self.returns_value and not self.deprecated

    @property
    def reason(self) -> str:
        if self.deprecated:
            return "already a deprecated delegate"
        if self.returns_value:
            return "returns a value, so it is a helper and not a visual component"
        return ""


def signature_end(text: str, open_paren: int) -> int:
    """Index just past the declaration's closing paren, tracking nesting."""
    depth = 0
    for i in range(open_paren, len(text)):
        if text[i] == "(":
            depth += 1
        elif text[i] == ")":
            depth -= 1
            if depth == 0:
                return i + 1
    return -1


def classify(path: Path) -> list[Decl]:
    text = path.read_text()
    out: list[Decl] = []
    for match in DECL.finditer(text):
        name = match.group(1)
        end = signature_end(text, match.end() - 1)
        if end < 0:
            continue
        # What follows the parameter list decides component vs helper: `: Foo` is a
        # return type, `{` or `=` with no annotation is Unit.
        tail = text[end:end + 120].lstrip()
        returns_value = tail.startswith(":") and not tail.startswith(": Unit")
        # The two lines above the declaration carry `context(...)` and any annotation.
        head_start = text.rfind("\n", 0, max(match.start() - 200, 0))
        head = text[max(head_start, 0):match.start()]
        out.append(
            Decl(
                name=name,
                pascal=name[0].upper() + name[1:],
                file=path,
                line=text[:match.start()].count("\n") + 1,
                returns_value=returns_value,
                has_composer_context="context(" in head,
                deprecated="@Deprecated" in head,
            )
        )
    return out


def existing_pascal_names() -> set[str]:
    names: set[str] = set()
    for path in SHADCN_ROOT.rglob("*.kt"):
        names.update(re.findall(r"^fun (Shadcn[A-Za-z0-9]*)\s*\(", path.read_text(), re.MULTILINE))
    return names


def call_sites(name: str) -> int:
    """Repo-wide references, excluding build output and this script."""
    result = subprocess.run(
        ["git", "grep", "-cw", name, "--", "*.kt"],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
    )
    total = 0
    for line in result.stdout.splitlines():
        _, _, count = line.rpartition(":")
        total += int(count) if count.isdigit() else 0
    return total


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--apply",
        action="store_true",
        help="Refused on purpose -- the IDE renames Kotlin symbols, see this module's docstring",
    )
    parser.add_argument("--filter", help="Only consider names containing this substring")
    args = parser.parse_args()

    decls: list[Decl] = []
    for path in sorted(SHADCN_ROOT.rglob("*.kt")):
        decls.extend(classify(path))
    if args.filter:
        decls = [d for d in decls if args.filter in d.name]

    pascal = existing_pascal_names()

    # Grouped by name, because a rename applies to a name and Kotlin resolves overloads by
    # signature. A name whose overloads disagree -- one returning a value, one a component --
    # cannot be half-renamed without splitting the overload set across two names.
    by_name: dict[str, list[Decl]] = defaultdict(list)
    for d in decls:
        by_name[d.name].append(d)

    mixed = {n: ds for n, ds in by_name.items() if any(d.eligible for d in ds) and any(not d.eligible for d in ds)}
    all_helpers = {n: ds for n, ds in by_name.items() if all(not d.eligible for d in ds)}
    all_components = {n: ds for n, ds in by_name.items() if all(d.eligible for d in ds)}
    collisions = {n: ds for n, ds in all_components.items() if ds[0].pascal in pascal}
    renamable_names = {n: ds for n, ds in all_components.items() if ds[0].pascal not in pascal}

    print(f"Declarations found:        {len(decls)}  across {len(by_name)} distinct names\n")
    print(f"  components  : {len(all_components)} names")
    print(f"  helpers     : {len(all_helpers)} names")
    print(f"  MIXED       : {len(mixed)} names  <- overloads disagree; needs a decision")
    print(f"  collisions  : {len(collisions)} names")
    print(f"  renamable   : {len(renamable_names)} names\n")

    if mixed:
        print("MIXED -- the same name is both a component and a value-returning overload.")
        print("Renaming only the component half would split one overload set across two names.\n")
        for name, ds in sorted(mixed.items()):
            for d in ds:
                kind = "component" if d.eligible else "returns a value"
                print(f"  {name:<28} {kind:<16} {d.file.relative_to(REPO_ROOT).name}:{d.line}")
        print()

    skipped = [ds[0] for ds in all_helpers.values()]
    renamable = [ds[0] for ds in renamable_names.values()]
    collision_list = [ds[0] for ds in collisions.values()]
    collisions = collision_list

    if collisions:
        print("COLLISIONS -- a PascalCase function of this name already exists.")
        print("These need a human decision: is the existing one the same component?\n")
        for d in sorted(collisions, key=lambda d: d.name):
            print(f"  {d.name} -> {d.pascal}  ({d.file.relative_to(REPO_ROOT)}:{d.line})")
        print()

    if skipped:
        print("SKIPPED -- not visual components, so PascalCase would be wrong:\n")
        for d in sorted(skipped, key=lambda d: d.name):
            print(f"  {d.name:<34} {d.reason}")
        print()

    print("RENAMABLE, with repo-wide reference counts:\n")
    total_refs = 0
    for d in sorted(renamable, key=lambda d: d.name):
        refs = call_sites(d.name)
        total_refs += refs
        flag = "" if d.has_composer_context else "   [no context(...) found above the decl]"
        print(f"  {d.name:<34} -> {d.pascal:<34} {refs:>4} refs{flag}")
    print(f"\n  {total_refs} references across {len(renamable)} names.")

    files = defaultdict(int)
    for d in renamable:
        files[d.file.relative_to(REPO_ROOT)] += 1
    print(f"  Declarations live in {len(files)} files.\n")

    if not args.apply:
        print("Dry run. Nothing was written. Re-run with --apply to perform the rename.")
        return 0

    print(
        "--apply is refused, not unfinished. kmp-refactor routes a Kotlin function rename to the\n"
        "IDE's Refactor > Rename, which resolves symbols; a textual sweep would also rewrite the\n"
        "38 string literals in this repo that merely contain a shadcn* name. Rename in the IDE,\n"
        "then re-run this script: a clean report is the residue check."
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
