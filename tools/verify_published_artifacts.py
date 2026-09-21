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
import urllib.error
import urllib.request
import xml.etree.ElementTree as ElementTree
from pathlib import Path

try:
    from .verify_publication_closure import inventory
except ImportError:  # Support direct execution as `python3 tools/verify_published_artifacts.py`.
    from verify_publication_closure import inventory

REPO_ROOT = Path(__file__).resolve().parent.parent
# Only this namespace: a developer's `~/.m2` holds artifacts from other projects, and older runs
# of this one under its previous group, which would be reported as today's publication.
LOCAL_REPO = Path.home() / ".m2" / "repository" / "com" / "awakekt" / "awake"
POM_NAMESPACE = {"m": "http://maven.apache.org/POM/4.0.0"}

# What Central refuses an upload without, plus the metadata a consumer reads to find the source.
REQUIRED_POM = ("name", "description", "url", "licenses", "developers", "scm")

# A KMP root module lists its variants here; anything else is a single-platform artifact.
VARIANT_MARKER = "kotlin-tooling-metadata"
PUBLICATIONS = inventory()
PUBLICATION_BY_COORDINATE = {
    f"{entry['coordinate']['group']}:{entry['coordinate']['artifact']}": entry
    for entry in PUBLICATIONS
}


def published_modules(version: str | None = None, family: str | None = None) -> list[Path]:
    """Every directory under the local repo holding a `.pom`, one per artifact version."""
    if not LOCAL_REPO.exists():
        return []
    dirs = {pom.parent for pom in LOCAL_REPO.rglob("*.pom")}
    if version:
        dirs = {d for d in dirs if d.name == version}
    if family:
        dirs = {
            directory
            for directory in dirs
            if (pom := pom_of(directory)) is not None
            and PUBLICATION_BY_COORDINATE.get(coordinate(pom), {}).get("releaseFamily") == family
        }
    return sorted(dirs)


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


def unresolvable_dependencies(pom: Path) -> list[str]:
    """Dependencies on `com.awakekt.awake` coordinates that are not configured publications."""
    root = ElementTree.parse(pom).getroot()
    missing = []
    for dependency in root.iterfind(".//m:dependency", POM_NAMESPACE):
        group = dependency.findtext("m:groupId", default="", namespaces=POM_NAMESPACE)
        artifact = dependency.findtext("m:artifactId", default="", namespaces=POM_NAMESPACE)
        if group.startswith("com.awakekt.awake") and f"{group}:{artifact}" not in PUBLICATION_BY_COORDINATE:
            missing.append(f"{group}:{artifact}")
    return missing


def snapshot_dependencies(pom: Path) -> list[str]:
    """Dependencies that declare a -SNAPSHOT version, forbidden by Maven Central in releases."""
    root = ElementTree.parse(pom).getroot()
    snapshots = []
    for dependency in root.iterfind(".//m:dependency", POM_NAMESPACE):
        group = dependency.findtext("m:groupId", default="", namespaces=POM_NAMESPACE)
        artifact = dependency.findtext("m:artifactId", default="", namespaces=POM_NAMESPACE)
        version = dependency.findtext("m:version", default="", namespaces=POM_NAMESPACE)
        if version.endswith("-SNAPSHOT"):
            snapshots.append(f"{group}:{artifact}:{version}")
    return snapshots


def internal_dependency_versions(pom: Path, family: str, artifact_version: str, core_version: str) -> list[str]:
    """Check that family POMs refer to exact versions from their own release train."""
    root = ElementTree.parse(pom).getroot()
    mismatches = []
    for dependency in root.iterfind(".//m:dependency", POM_NAMESPACE):
        group = dependency.findtext("m:groupId", default="", namespaces=POM_NAMESPACE)
        artifact = dependency.findtext("m:artifactId", default="", namespaces=POM_NAMESPACE)
        version = dependency.findtext("m:version", default="", namespaces=POM_NAMESPACE)
        entry = PUBLICATION_BY_COORDINATE.get(f"{group}:{artifact}")
        if entry is None:
            continue
        dependency_family = entry["releaseFamily"]
        expected = artifact_version if dependency_family == family else core_version
        if version != expected:
            mismatches.append(f"{group}:{artifact}:{version} (expected {expected})")
    return mismatches


def central_missing_dependencies(pom: Path, core_version: str) -> list[str]:
    """HEAD-check the full transitive Awake Core closure pinned by a Vulkan publication."""
    root_entry = PUBLICATION_BY_COORDINATE.get(coordinate(pom))
    if root_entry is None:
        return [f"{coordinate(pom)} is not in the publication inventory"]
    missing = []
    visited: set[str] = set()
    pending = [dependency["target"] for dependency in root_entry["dependencies"]]
    while pending:
        module = pending.pop()
        if module in visited:
            continue
        visited.add(module)
        entry = next((item for item in PUBLICATIONS if item["module"] == module), None)
        if entry is None:
            continue
        if entry["releaseFamily"] == "core":
            coordinate_value = entry["coordinate"]
            group, artifact = coordinate_value["group"], coordinate_value["artifact"]
            notation = f"{group}:{artifact}:{core_version}"
            path = f"{group.replace('.', '/')}/{artifact}/{core_version}/{artifact}-{core_version}.pom"
            request = urllib.request.Request(
                f"https://repo.maven.apache.org/maven2/{path}", method="HEAD"
            )
            try:
                with urllib.request.urlopen(request, timeout=15):
                    pass
            except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError):
                missing.append(notation)
        pending.extend(dependency["target"] for dependency in entry["dependencies"])
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
    parser.add_argument("--version", help="Filter verification to a specific artifact version")
    parser.add_argument("--all", action="store_true", help="Verify all artifact versions found in local repository")
    parser.add_argument("--family", choices=("core", "vulkan"), help="Filter by release family")
    parser.add_argument("--core-version", help="Expected exact Core version for a Vulkan publication")
    parser.add_argument("--verify-central", action="store_true", help="Verify pinned Core POMs exist on Maven Central")
    args = parser.parse_args()

    if args.family == "vulkan" and not args.version and not args.all:
        parser.error("--family vulkan requires --version or --all")

    version_filter = args.version
    if not version_filter and not args.all:
        try:
            import subprocess
            raw = subprocess.check_output(
                ["git", "describe", "--tags", "--match", "v[0-9]*", "--always"],
                cwd=REPO_ROOT,
                text=True
            ).strip().lstrip("v")
            m = re.search(r"^(.+?)(?:-(\d+)-g[0-9a-f]+)$", raw)
            if m:
                base = m.group(1)
                bumped = re.sub(r"(\d+)$", lambda match: str(int(match.group(1)) + 1), base)
                version_filter = f"{bumped}-SNAPSHOT"
            else:
                version_filter = raw
        except Exception:
            version_filter = None

    directories = published_modules(version_filter, args.family)
    if not directories:
        target_str = f" for version {version_filter}" if version_filter else ""
        print(
            f"Nothing published locally{target_str}. Run:\n"
            "  ./gradlew publishToMavenLocal -PisMainHost=true",
            file=sys.stderr,
        )
        return 1

    if args.list:
        names = sorted(coordinate(pom) for pom in (pom_of(d) for d in directories) if pom)
        for name in names:
            print(name)
        print(f"\n{len(names)} artifacts")
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

        unresolvable = unresolvable_dependencies(pom)
        if unresolvable:
            failures.append(f"  {name}: depends on unpublished {', '.join(sorted(set(unresolvable)))}")

        if version_filter and not version_filter.endswith("-SNAPSHOT"):
            snapshots = snapshot_dependencies(pom)
            if snapshots:
                failures.append(f"  {name}: contains snapshot dependencies forbidden by Central: {', '.join(sorted(set(snapshots)))}")

        if args.family == "vulkan":
            if not args.core_version:
                failures.append(f"  {name}: --core-version is required for Vulkan-family verification")
            else:
                mismatches = internal_dependency_versions(
                    pom, "vulkan", version_filter or directory.name, args.core_version
                )
                if mismatches:
                    failures.append(f"  {name}: incorrect release-family dependencies: {', '.join(mismatches)}")
                is_release = not (version_filter or directory.name).endswith("-SNAPSHOT")
                if is_release:
                    snapshots = snapshot_dependencies(pom)
                    if snapshots:
                        failures.append(f"  {name}: Vulkan release cannot depend on snapshots: {', '.join(sorted(set(snapshots)))}")
                if args.verify_central and is_release:
                    missing = central_missing_dependencies(pom, args.core_version)
                    if missing:
                        failures.append(f"  {name}: stable Core dependencies are not on Maven Central: {', '.join(missing)}")
                elif args.verify_central:
                    failures.append(f"  {name}: --verify-central is only valid for stable Vulkan releases")

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

    print(f"Published artifacts OK ({len(directories)} artifacts, POM metadata and sidecars present)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
