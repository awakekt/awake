#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: the tracked agent source declaration is reproducible and names only known ownership kinds.
"""Validate the declarative agent-skill lockfile without loading deployed bundles."""

from __future__ import annotations

import re
import sys
import tomllib
from pathlib import Path


LOCK = Path(__file__).resolve().parents[1] / ".agents" / "skills.lock.toml"
COMMIT = re.compile(r"^[0-9a-f]{40}$")
DIGEST = re.compile(r"^[0-9a-f]{64}$")


def main() -> int:
    data = tomllib.loads(LOCK.read_text(encoding="utf-8"))
    errors: list[str] = []
    if data.get("version") != 1:
        errors.append("version must be 1")
    for source in data.get("source", []):
        required = ("id", "kind", "source", "tag", "commit", "archive_sha256", "license", "skill_root", "skills", "skills_target")
        if any(not source.get(field) for field in required):
            errors.append(f"{source.get('id', '<unknown>')}: missing required field")
        elif source["kind"] not in {"vendor", "maintained-core", "maintained-studio"}:
            errors.append(f"{source['id']}: invalid kind")
        elif not COMMIT.fullmatch(source["commit"]) or not DIGEST.fullmatch(source["archive_sha256"]):
            errors.append(f"{source['id']}: invalid immutable revision or digest")
        elif source["kind"] == "maintained-studio":
            errors.append("public Awake lockfile must not install private Studio sources")
        elif source["skills_target"] != ".agents/skills":
            errors.append(f"{source['id']}: invalid skill deployment target")
        elif source.get("commands") and source.get("commands_target") != ".agents/commands":
            errors.append(f"{source['id']}: invalid command deployment target")
    if errors:
        print("Agent-skill lock validation failed:", *errors, sep="\n", file=sys.stderr)
        return 1
    print(f"Agent-skill lock validation passed ({len(data['source'])} sources)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
