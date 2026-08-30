#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: exit 1 means a SKILL.md violates the Agent Skills specification.
# Kinds are defined in docs/tasks/2026-08-23-ui-tooling-formalization-plan.md. Only a GATE can fail
# a build; `scripts/awake verify` runs every one.
"""Validates every `skills/*/SKILL.md` against https://agentskills.io/specification.

`verify_agent_skills_sync.py` is next door and checks something different: that skills the docs
*cite* exist and that the four entrypoints agree. It accepts any frontmatter that parses. This
checks the format itself -- the rules an external consumer of these skills would enforce.

Two tiers, because the spec has two:

* **Hard rules** fail. `name` charset/length/directory match and `description` length are stated as
  requirements, and a skill breaking one is invalid rather than merely large.
* **Size recommendations** warn. "Under 500 lines" and "under 5000 tokens" are the spec's own
  wording, and splitting a skill is editorial work -- a judgement about what a reader needs at
  activation time versus on demand. Failing a build over it would get the number raised, not the
  skill split.

Deliberately no dependency on `skills-ref`: the hard rules are a dozen lines of regex, and this
repo's gates run with no network and no install step.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[1]
# ".agents/skills", not "skills": skills moved there and this constant kept the old root, so the
# walk found nothing and this gate reported success while validating zero SKILL.md files.
SKILLS_DIR = REPO_ROOT / ".agents" / "skills"

NAME_PATTERN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
NAME_MAX = 64
DESCRIPTION_MAX = 1024
COMPATIBILITY_MAX = 500

# The spec's own recommendations, quoted: "Keep your main SKILL.md under 500 lines" and
# "Instructions (< 5000 tokens recommended)".
BODY_LINE_LIMIT = 500
BODY_TOKEN_LIMIT = 5000

KNOWN_FIELDS = {"name", "description", "license", "compatibility", "metadata", "allowed-tools"}


def parse(path: Path) -> tuple[dict, str] | None:
    """Returns (frontmatter, body), or None when there is no parseable frontmatter block."""
    match = re.match(r"^---\n(.*?)\n---\n(.*)$", path.read_text(encoding="utf-8"), re.S)
    if not match:
        return None
    loaded = yaml.safe_load(match.group(1))
    return (loaded if isinstance(loaded, dict) else {}), match.group(2)


def check(path: Path) -> tuple[list[str], list[str]]:
    """Returns (errors, warnings) for one SKILL.md."""
    errors: list[str] = []
    warnings: list[str] = []
    parsed = parse(path)
    if parsed is None:
        return [f"{path}: no YAML frontmatter block"], []
    front, body = parsed

    name = front.get("name")
    if not isinstance(name, str) or not name:
        errors.append(f"{path}: 'name' is required")
    else:
        if len(name) > NAME_MAX:
            errors.append(f"{path}: name is {len(name)} characters, max {NAME_MAX}")
        if not NAME_PATTERN.fullmatch(name):
            errors.append(
                f"{path}: name {name!r} must be lowercase a-z0-9 and single hyphens, "
                "not leading, trailing or doubled"
            )
        if name != path.parent.name:
            errors.append(f"{path}: name {name!r} must match its directory {path.parent.name!r}")

    description = front.get("description")
    if not isinstance(description, str) or not description.strip():
        errors.append(f"{path}: 'description' is required and must be non-empty")
    elif len(description) > DESCRIPTION_MAX:
        errors.append(f"{path}: description is {len(description)} characters, max {DESCRIPTION_MAX}")

    compatibility = front.get("compatibility")
    if compatibility is not None and len(str(compatibility)) > COMPATIBILITY_MAX:
        errors.append(
            f"{path}: compatibility is {len(str(compatibility))} characters, max {COMPATIBILITY_MAX}"
        )

    metadata = front.get("metadata")
    if metadata is not None:
        if not isinstance(metadata, dict):
            errors.append(f"{path}: metadata must be a mapping")
        else:
            for key, value in metadata.items():
                if not isinstance(value, str):
                    errors.append(
                        f"{path}: metadata.{key} is {type(value).__name__}; the spec allows string "
                        "values only -- join a list into one string"
                    )

    unknown = set(front) - KNOWN_FIELDS
    if unknown:
        # Not an error: the spec does not forbid extra keys. But a typo'd `licence:` is invisible
        # otherwise, and every one seen so far has been a typo rather than an intent.
        warnings.append(f"{path}: unrecognised frontmatter field(s) {sorted(unknown)}")

    lines = len(body.splitlines())
    tokens = len(body) // 4
    if lines > BODY_LINE_LIMIT:
        warnings.append(
            f"{path}: body is {lines} lines, over the spec's recommended {BODY_LINE_LIMIT}; "
            "move detail into references/ so it loads on demand"
        )
    if tokens > BODY_TOKEN_LIMIT:
        warnings.append(
            f"{path}: body is ~{tokens} tokens, over the spec's recommended {BODY_TOKEN_LIMIT}; "
            "this is loaded in full the moment the skill activates"
        )
    return errors, warnings


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--strict", action="store_true", help="treat size recommendations as failures too"
    )
    args = parser.parse_args(argv)

    paths = sorted(SKILLS_DIR.glob("*/SKILL.md"))
    if not paths:
        print(f"no SKILL.md found under {SKILLS_DIR}", file=sys.stderr)
        return 2

    all_errors: list[str] = []
    all_warnings: list[str] = []
    for path in paths:
        errors, warnings = check(path)
        all_errors += errors
        all_warnings += warnings

    for warning in all_warnings:
        print(f"   WARN {warning}")
    for error in all_errors:
        print(f"   FAIL {error}")

    if all_errors or (args.strict and all_warnings):
        print(f"\n{len(all_errors)} error(s), {len(all_warnings)} warning(s) across {len(paths)} skills")
        return 1
    print(f"   OK: {len(paths)} skills valid ({len(all_warnings)} size warning(s))")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
