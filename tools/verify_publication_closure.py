#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Fails when a published module depends on one that is not published.

A Gradle project dependency becomes a Maven coordinate in the published POM. If that coordinate is
never uploaded, the artifact still builds, still publishes, and still passes every test here --
and then fails in an external consumer's build with an unresolved dependency, which is the worst
place to find out. This walks the main-source project graph of every publishing module and reports
any edge that leaves the published set.

Test-only edges are ignored: a `commonTest` dependency never reaches a consumer.

Usage:
    python3 tools/verify_publication_closure.py            # check, exit 1 on failure
    python3 tools/verify_publication_closure.py --list     # print the published set
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

PROJECT_DEPENDENCY = re.compile(r'\b(?:api|implementation)\(project\("([^"]+)"\)\)')
SOURCE_SET = re.compile(r"\b(\w+(?:Main|Test))\b")


def build_file(module: str) -> Path:
    return REPO_ROOT / module.strip(":").replace(":", "/") / "build.gradle.kts"


def modules() -> list[str]:
    """Every Gradle module with a build file, as a `:path:like:this`."""
    found = []
    for path in REPO_ROOT.glob("awake/**/build.gradle.kts"):
        if "/build/" in path.as_posix():
            continue
        found.append(":" + str(path.parent.relative_to(REPO_ROOT)).replace("/", ":"))
    return sorted(found)


def publishes(module: str) -> bool:
    """Either through the shared convention, or its own `mavenPublishing` block.

    Both count. `:awake:backend:vulkan:bindings:android-native` is a plain Android library, not a
    KMP one, so the convention's `KotlinMultiplatform(...)` configuration cannot apply to it -- it
    declares an `AndroidSingleVariantLibrary` publication itself. Looking only for the convention
    reported it as unpublished, which would have sent someone to "fix" a module that was already
    doing the right thing.
    """
    path = build_file(module)
    if not path.exists():
        return False
    text = path.read_text()
    return "awake.publish-convention" in text or "publishToMavenCentral()" in text


def main_dependencies(module: str) -> list[str]:
    """Project dependencies declared in main source sets, which are the ones that ship."""
    path = build_file(module)
    if not path.exists():
        return []
    dependencies: list[str] = []
    source_set: str | None = None
    for line in path.read_text().splitlines():
        block = SOURCE_SET.search(line)
        if block and "{" in line:
            source_set = block.group(1)
        edge = PROJECT_DEPENDENCY.search(line)
        if edge and source_set and not source_set.endswith("Test"):
            dependencies.append(edge.group(1))
    return dependencies


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--list", action="store_true", help="Print the published set and exit")
    args = parser.parse_args()

    published = {module for module in modules() if publishes(module)}

    if args.list:
        for module in sorted(published):
            print(module)
        return 0

    failures: list[str] = []
    for module in sorted(published):
        for dependency in main_dependencies(module):
            if dependency not in published:
                failures.append(f"  {module}\n    -> {dependency} (not published)")

    if failures:
        print(f"Published modules depend on {len(failures)} unpublished module(s):\n")
        print("\n".join(failures))
        print(
            "\nEither publish the dependency, or move the edge to a test source set if that is "
            "where it belongs. A consumer resolving this artifact cannot see an unpublished "
            "project."
        )
        return 1

    print(f"Publication closure OK ({len(published)} published modules)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
