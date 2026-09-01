#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Checks what `publishToMavenLocal` actually produced, against what Central requires.

Maven Central rejects an artifact missing a licence, an SCM entry, a developer or a description,
and a KMP consumer that resolves an artifact with no platform variants gets a metadata-only module
that compiles against nothing. Both failures are invisible from inside this repository: the build
succeeds, the tests pass, and the artifact is wrong in someone else's build.

Run after:

    ./gradlew publishToMavenLocal -PisMainHost=true

Usage:
    python3 tools/verify_published_artifacts.py             # check every published module
    python3 tools/verify_published_artifacts.py --list      # print what was found
"""

from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
# Only this namespace: a developer's `~/.m2` holds artifacts from other projects, and older runs
# of this one under its previous group, which would be reported as today's publication.
LOCAL_REPO = Path.home() / ".m2" / "repository" / "io" / "github" / "awake-lab"
POM_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}

# What Central refuses an upload without, plus the metadata a consumer reads to find the source.
REQUIRED_POM = ("name", "description", "url", "licenses", "developers", "scm")

# A KMP root module lists its variants here; anything else is a single-platform artifact.
VARIANT_MARKER = "kotlin-tooling-metadata"


def published_modules() -> list[Path]:
    """Every directory under the local repo holding a `.pom`, one per artifact version."""
    if not LOCAL_REPO.exists():
        return []
    return sorted({pom.parent for pom in LOCAL_REPO.rglob("*.pom")})


def pom_of(directory: Path) -> Path | None:
    poms = sorted(directory.glob("*.pom"))
    return poms[0] if poms else None


def missing_metadata(pom: Path) -> list[str]:
    root = ElementTree.parse(pom).getroot()
    return [field for field in REQUIRED_POM if root.find(f"m:{field}", POM_NAMESPACE) is None]


def coordinate(pom: Path) -> str:
    root = ElementTree.parse(pom).getroot()
    group = root.findtext("m:groupId", default="?", namespaces=POM_NAMESPACE)
    artifact = root.findtext("m:artifactId", default="?", namespaces=POM_NAMESPACE)
    return f"{group}:{artifact}"


def unresolvable_dependencies(pom: Path, known: set[str]) -> list[str]:
    """Dependencies on `io.github.awake-lab` coordinates that were never published."""
    root = ElementTree.parse(pom).getroot()
    missing = []
    for dependency in root.iterfind(".//m:dependency", POM_NAMESPACE):
        group = dependency.findtext("m:groupId", default="", namespaces=POM_NAMESPACE)
        artifact = dependency.findtext("m:artifactId", default="", namespaces=POM_NAMESPACE)
        if group.startswith("io.github.awake-lab") and f"{group}:{artifact}" not in known:
            missing.append(f"{group}:{artifact}")
    return missing


def companions(directory: Path, stem: str) -> dict[str, bool]:
    """Whether the sidecar artifacts Central expects were produced."""
    names = {path.name for path in directory.iterdir()}
    return {
        "sources": any(re.match(rf"{re.escape(stem)}.*-sources\.jar$", name) for name in names),
        "javadoc": any(re.match(rf"{re.escape(stem)}.*-javadoc\.jar$", name) for name in names),
        "module": any(name.endswith(".module") for name in names),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--list", action="store_true", help="Print every artifact found and exit")
    args = parser.parse_args()

    directories = published_modules()
    if not directories:
        print(
            "Nothing published locally. Run:\n"
            "  ./gradlew publishToMavenLocal -PisMainHost=true",
            file=sys.stderr,
        )
        return 1

    known = {coordinate(pom) for pom in (pom_of(d) for d in directories) if pom}

    if args.list:
        for name in sorted(known):
            print(name)
        print(f"\n{len(known)} artifacts")
        return 0

    failures: list[str] = []
    for directory in directories:
        pom = pom_of(directory)
        if pom is None:
            continue
        name = coordinate(pom)
        stem = pom.name[: -len(".pom")]

        missing = missing_metadata(pom)
        if missing:
            failures.append(f"  {name}: POM is missing {', '.join(missing)}")

        unresolvable = unresolvable_dependencies(pom, known)
        if unresolvable:
            failures.append(f"  {name}: depends on unpublished {', '.join(sorted(set(unresolvable)))}")

        produced = companions(directory, stem)
        absent = [kind for kind, present in produced.items() if not present]
        # A KMP platform artifact (`-jvm`, `-iosarm64`, ...) has no module metadata of its own; only
        # the root does. Judging those by the root's rule would report a dozen false failures.
        if absent and not re.search(r"-(jvm|android|desktop|ios\w*|wasm\w*|js|linux\w*|macos\w*|mingw\w*)$", name):
            failures.append(f"  {name}: no {', '.join(absent)} artifact")

    if failures:
        print(f"{len(failures)} publication problem(s):\n")
        print("\n".join(sorted(failures)))
        return 1

    print(f"Published artifacts OK ({len(known)} artifacts, POM metadata and sidecars present)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
