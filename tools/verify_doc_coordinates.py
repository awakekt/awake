#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: a dependency snippet in the docs must name a real publication and a real version.
"""Reject Maven coordinates in Awake's docs that no consumer could resolve.

The docs already have gates for sitemap drift, broken links and retired module
names. None of them reads a code block, so a `implementation("g:a:v")` snippet can
name an artifact that was renamed, a group that was restructured, or a version
that never shipped, and every gate stays green. That is not hypothetical: the
README advertised `vulkan-kmp:0.1.0-dev.1` for months, and the coordinate did not
exist until the day after `v0.1.0-dev.6` was cut.

What it checks, per snippet:

  group:artifact  must match a module that actually publishes. Derived the same
                  way the build derives it -- `com.awakekt.awake` plus the
                  parent path with the leading `awake` dropped -- and overridden
                  where a module calls `coordinates(...)`, which is what makes
                  `vulkan-kmp` a real artifact id and not a typo.

  version         must be one a consumer could ask for: a tag that exists, the
                  version the build currently derives, or either as -SNAPSHOT.

Historical documents are exempt. Release notes and archived plans are records of
what was true when written, and rewriting them to stay green would destroy the
thing that makes them useful.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]

# `implementation("g:a:v")` and friends. Only inside markdown -- Gradle checks its own build files.
SNIPPET = re.compile(r'(?:implementation|api|testImplementation|compileOnly)\(\s*"([^"]+)"\s*\)')

# A version catalog splits the coordinate from the version:
#   vulkan-kmp = { module = "com.awakekt.awake:vulkan-kmp", version.ref = "vulkan-kmp" }
# The first pass missed this entirely, so a catalog block in the bindings README kept advertising a
# version that no build state produces while the gate reported the file clean.
CATALOG_MODULE = re.compile(r'module\s*=\s*"([^"]+)"')

COORDINATE = re.compile(r"^(?P<group>[\w.\-]+):(?P<artifact>[\w.\-]+)(?::(?P<version>.+))?$")

# Written to describe a moment, not to stay current.
EXEMPT = (
    "CHANGELOG.md",
    "docs/release-notes-",
    "docs/tasks/archive/",
    "docs/archive/",
    # Agent skill files contain template/example Maven snippets that consumers are expected
    # to substitute (e.g. `$ktorVersion`). They are not Awake-published coordinates.
    ".agents/skills/",
)

# A version a reader is expected to substitute, not resolve.
PLACEHOLDERS = ("<version>", "${version}", "$version", "VERSION", "x.y.z")


def run(args: list[str]) -> str:
    result = subprocess.run(args, cwd=REPO_ROOT, capture_output=True, text=True, check=False)
    return result.stdout.strip()


def published_coordinates() -> dict[str, set[str]]:
    """`group` -> {artifact}, for every module that actually publishes."""
    coordinates: dict[str, set[str]] = {}
    for build_file in REPO_ROOT.glob("awake/**/build.gradle.kts"):
        text = build_file.read_text()
        if "com.awakekt.awake.plugin.publish" not in text and "awake.publish-convention" not in text and "com.vanniktech.maven.publish" not in text:
            continue
        module = build_file.parent
        override = re.search(r'coordinates\(\s*"([^"]+)"\s*,\s*"([^"]+)"', text)
        if override:
            group, artifact = override.group(1), override.group(2)
        else:
            # Mirrors the group derivation in the root build.gradle.kts: the repo group, plus the
            # parent path with the leading `awake` segment dropped.
            parts = module.relative_to(REPO_ROOT).parts[:-1]  # drop the module's own name
            trimmed = [p for p in parts[1:]] if parts and parts[0] == "awake" else list(parts)
            group = "com.awakekt.awake" + ("." + ".".join(trimmed) if trimmed else "")
            artifact = module.name
        coordinates.setdefault(group, set()).add(artifact)
    return coordinates


def resolvable_versions() -> set[str]:
    versions = {t.removeprefix("v") for t in run(["git", "tag", "--list", "v*"]).splitlines() if t}
    describe = run(["git", "describe", "--tags", "--match", "v*", "--always"])
    exact = re.match(r"^v(.+?)-(\d+)-g[0-9a-f]+$", describe)
    if exact:
        base = exact.group(1)
        versions.add(re.sub(r"(\d+)$", lambda m: str(int(m.group(1)) + 1), base))
    elif describe.startswith("v"):
        versions.add(describe.removeprefix("v"))
    return versions | {f"{v}-SNAPSHOT" for v in versions}


def is_exempt(path: Path) -> bool:
    relative = path.relative_to(REPO_ROOT).as_posix()
    return any(relative.startswith(prefix) or relative == prefix for prefix in EXEMPT)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--list", action="store_true", help="Print every publication and exit")
    args = parser.parse_args()

    coordinates = published_coordinates()
    versions = resolvable_versions()

    if args.list:
        for group in sorted(coordinates):
            for artifact in sorted(coordinates[group]):
                print(f"{group}:{artifact}")
        print(f"\nresolvable versions: {', '.join(sorted(versions))}")
        return 0

    failures: list[str] = []
    checked = 0
    for path in sorted(REPO_ROOT.rglob("*.md")):
        relative = path.relative_to(REPO_ROOT).as_posix()
        # `.claude/worktrees/` holds sibling checkouts of this same repository. Their docs belong
        # to whatever branch is checked out there, and scanning them fails this gate for everyone:
        # a release note archived on another branch is not this working tree's to fix, and its path
        # does not match the EXEMPT prefixes either, since those are relative to a repository root.
        if "/build/" in relative or relative.startswith(("node_modules/", ".git/", ".claude/")):
            continue
        if is_exempt(path):
            continue
        text = path.read_text()
        for raw in SNIPPET.findall(text) + CATALOG_MODULE.findall(text):
            parsed = COORDINATE.match(raw)
            if not parsed:
                continue
            checked += 1
            group, artifact, version = parsed.group("group", "artifact", "version")
            if group not in coordinates:
                failures.append(f"{relative}: group '{group}' publishes nothing ({raw})")
            elif artifact not in coordinates[group]:
                known = ", ".join(sorted(coordinates[group]))
                failures.append(f"{relative}: '{group}' has no artifact '{artifact}' -- has: {known}")
            elif version is not None and version not in versions and version not in PLACEHOLDERS:
                failures.append(f"{relative}: version '{version}' is not a tag or the current build ({raw})")

    if failures:
        print(f"Unresolvable coordinates in the docs ({len(failures)} of {checked} snippets):\n")
        for failure in failures:
            print(f"  {failure}")
        print(
            "\nA reader pastes these verbatim. Fix the snippet, or add the document to EXEMPT if it"
            "\nis a record of a past release rather than instructions for the current one."
        )
        return 1

    print(f"Doc coordinates OK ({checked} snippets checked)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
