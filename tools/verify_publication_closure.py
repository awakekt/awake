#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Inventory Maven publications and check their internal dependency closure.

The inventory is intentionally derived from each module's build script, including both the
published plugin convention and explicit vanniktech publications (the Android JNI library).
The JSON form is suitable for tooling; the normal form is the inexpensive CI gate.

Usage:
    python3 tools/verify_publication_closure.py
    python3 tools/verify_publication_closure.py --list
    python3 tools/verify_publication_closure.py --json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent

PUBLISH_PLUGIN = re.compile(
    r"""(?:id\s*\(\s*["']com\.awakekt\.awake\.plugin\.publish["']\s*\)|"
    r"id\s*\(\s*["']awake\.publish-convention["']\s*\))"""
)
EXPLICIT_PUBLICATION = re.compile(
    r'''id\s*\(\s*["']com\.vanniktech\.maven\.publish["']\s*\)'''
)
COORDINATES_OVERRIDE = re.compile(
    r'''coordinates\s*\(\s*["'](?P<group>[^"']+)["']\s*,\s*["'](?P<artifact>[^"']+)["']'''
)
SOURCE_SET = re.compile(r"\b(\w+(?:Main|Test))\s*(?:\.dependencies)?\s*\{")
PROJECT_DEPENDENCY = re.compile(
    r'''\b(?P<scope>api|implementation)\s*\(\s*project\s*\(\s*["'](?P<target>:[^"']+)["']\s*\)'''
)

VULKAN_FAMILY = {
    ":awake:backend:vulkan",
    ":awake:backend:vulkan:bindings",
    ":awake:backend:vulkan:bindings:android-native",
}


def modules() -> list[str]:
    """Every Awake project with a Gradle build script."""
    return sorted(
        ":" + str(path.parent.relative_to(REPO_ROOT)).replace("/", ":")
        for path in REPO_ROOT.glob("awake/**/build.gradle.kts")
        if "/build/" not in path.as_posix()
    )


def build_file(module: str) -> Path:
    return REPO_ROOT / module.strip(":").replace(":", "/") / "build.gradle.kts"


def is_published(text: str) -> bool:
    """Recognize the shared publish convention and explicit Maven publications."""
    return bool(PUBLISH_PLUGIN.search(text) or (EXPLICIT_PUBLICATION.search(text) and "mavenPublishing" in text))


def coordinate(module: str, text: str) -> dict[str, str]:
    override = COORDINATES_OVERRIDE.search(text)
    if override:
        return {"group": override.group("group"), "artifact": override.group("artifact")}

    parts = module.strip(":").split(":")
    parent = parts[:-1]
    if parent and parent[0] == "awake":
        parent = parent[1:]
    group = "com.awakekt.awake" + ("." + ".".join(parent) if parent else "")
    return {"group": group, "artifact": parts[-1]}


def main_dependencies(text: str) -> list[dict[str, str]]:
    """Read direct API/runtime project edges from non-test Kotlin source-set blocks."""
    dependencies: list[dict[str, str]] = []
    source_set: str | None = None
    for line in text.splitlines():
        block = SOURCE_SET.search(line)
        if block:
            source_set = block.group(1)
        if source_set is None or source_set.endswith("Test"):
            continue
        for edge in PROJECT_DEPENDENCY.finditer(line):
            dependencies.append(
                {
                    "sourceSet": source_set,
                    "scope": edge.group("scope"),
                    "target": edge.group("target"),
                }
            )
    return dependencies


def inventory() -> list[dict[str, object]]:
    result: list[dict[str, object]] = []
    for module in modules():
        path = build_file(module)
        text = path.read_text()
        if not is_published(text):
            continue
        parts = module.strip(":").split(":")
        component_family = parts[2] if len(parts) > 2 else parts[-1]
        result.append(
            {
                "module": module,
                "coordinate": coordinate(module, text),
                "releaseFamily": "vulkan" if module in VULKAN_FAMILY else "core",
                "componentFamily": component_family,
                "dependencies": main_dependencies(text),
            }
        )
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    output = parser.add_mutually_exclusive_group()
    output.add_argument("--list", action="store_true", help="Print publications and direct edges as TSV")
    output.add_argument("--markdown", action="store_true", help="Print a Markdown inventory table")
    output.add_argument("--json", action="store_true", help="Print the complete machine-readable inventory")
    args = parser.parse_args()

    publications = inventory()
    published = {entry["module"] for entry in publications}
    failures: list[str] = []
    api_edges = 0
    implementation_edges = 0
    for entry in publications:
        for dependency in entry["dependencies"]:
            scope = dependency["scope"]
            target = dependency["target"]
            if scope == "api":
                api_edges += 1
            else:
                implementation_edges += 1
            if target not in published:
                failures.append(f"  {entry['module']}\n    -> {target} ({scope}, not published)")

    if args.json:
        print(json.dumps(publications, indent=2, sort_keys=True))
        return 1 if failures else 0

    if args.markdown:
        print("| Coordinate | Gradle module | Release family | Direct main-source project dependencies |")
        print("|---|---|---|---|")
        for entry in publications:
            coordinate_value = entry["coordinate"]
            coordinate_text = f"{coordinate_value['group']}:{coordinate_value['artifact']}"
            dependencies = "; ".join(
                f"`{dependency['scope']}` → `{dependency['target']}`"
                for dependency in entry["dependencies"]
            ) or "—"
            print(
                f"| `{coordinate_text}` | `{entry['module']}` | `{entry['releaseFamily']}` | "
                f"{dependencies} |"
            )
        return 1 if failures else 0

    if args.list:
        for entry in publications:
            coordinate_value = entry["coordinate"]
            deps = ", ".join(
                f"{dependency['scope']}:{dependency['target']}"
                for dependency in entry["dependencies"]
            ) or "—"
            print(
                f"{entry['module']}\t{coordinate_value['group']}:{coordinate_value['artifact']}"
                f"\t{entry['releaseFamily']}\t{deps}"
            )
        print(f"\n{len(publications)} published modules")
        return 1 if failures else 0

    if failures:
        print(f"Published modules depend on {len(failures)} unpublished module(s):\n")
        print("\n".join(failures))
        print(
            "\nEither publish the dependency or move the edge to a test source set. "
            "A consumer cannot resolve an unpublished project."
        )
        return 1

    core_count = sum(entry["releaseFamily"] == "core" for entry in publications)
    vulkan_count = sum(entry["releaseFamily"] == "vulkan" for entry in publications)
    print(
        f"Publication closure OK ({len(publications)} published modules; "
        f"{api_edges} api / {implementation_edges} implementation project edges; "
        f"release families: core={core_count}, vulkan={vulkan_count})"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
