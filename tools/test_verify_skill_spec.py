#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: exit 1 means the tool itself is broken; runs under `awake verify`'s tool-tests gate.
"""A validator nobody tests is a validator that quietly stops validating.

Each case here is a rule the spec states, checked by construction rather than by trusting that the
repo happens to contain a violation.
"""
from __future__ import annotations

import importlib.util
from pathlib import Path

import pytest

SPEC = Path(__file__).resolve().parent / "verify_skill_spec.py"
_spec = importlib.util.spec_from_file_location("verify_skill_spec", SPEC)
verify_skill_spec = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(verify_skill_spec)


def write(tmp_path: Path, name: str, front: str, body: str = "# Body\n") -> Path:
    skill = tmp_path / name
    skill.mkdir()
    path = skill / "SKILL.md"
    path.write_text(f"---\n{front}\n---\n{body}")
    return path


def errors_for(path: Path) -> list[str]:
    return verify_skill_spec.check(path)[0]


def warnings_for(path: Path) -> list[str]:
    return verify_skill_spec.check(path)[1]


def test_a_minimal_valid_skill_passes(tmp_path: Path) -> None:
    path = write(tmp_path, "good-skill", "name: good-skill\ndescription: Does a thing. Use when.")
    assert errors_for(path) == []


@pytest.mark.parametrize(
    "name",
    [
        "Bad-Skill",  # uppercase
        "-leading",
        "trailing-",
        "double--hyphen",
        "under_score",
    ],
)
def test_invalid_names_are_rejected(tmp_path: Path, name: str) -> None:
    path = write(tmp_path, name, f"name: {name}\ndescription: Does a thing.")
    assert any("must be lowercase" in e for e in errors_for(path)), name


def test_name_must_match_its_directory(tmp_path: Path) -> None:
    path = write(tmp_path, "actual-dir", "name: different-name\ndescription: Does a thing.")
    assert any("must match its directory" in e for e in errors_for(path))


def test_description_is_required(tmp_path: Path) -> None:
    path = write(tmp_path, "no-desc", "name: no-desc\ndescription: '   '")
    assert any("'description' is required" in e for e in errors_for(path))


def test_over_long_description_is_rejected(tmp_path: Path) -> None:
    path = write(tmp_path, "long-desc", f"name: long-desc\ndescription: {'x' * 1025}")
    assert any("max 1024" in e for e in errors_for(path))


def test_non_string_metadata_value_is_rejected(tmp_path: Path) -> None:
    # The real violation this gate was written for: `keywords` as a YAML list. It also broke the
    # skill loader's own parse, so both descriptions vanished from the skill listing.
    path = write(
        tmp_path,
        "list-meta",
        "name: list-meta\ndescription: Does a thing.\nmetadata:\n  keywords:\n    - a\n    - b",
    )
    assert any("string values only" in e for e in errors_for(path))


def test_an_oversized_body_warns_but_does_not_fail(tmp_path: Path) -> None:
    path = write(tmp_path, "big-skill", "name: big-skill\ndescription: Does a thing.", "line\n" * 600)
    assert errors_for(path) == []
    assert any("over the spec's recommended 500" in w for w in warnings_for(path))


def test_the_repo_itself_is_clean() -> None:
    assert verify_skill_spec.main([]) == 0
