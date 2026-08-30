#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: exit 1 means the committed skill-evaluation fixtures or their required policy surface drifted.
"""Fixture-backed contract tests for Awake's web-to-Compose skill family.

These tests intentionally verify decisions and required routing surfaces, not prose style. Actual
model forward evaluation remains a separate, human-reviewed exercise described in
tools/skill-evals/README.md.
"""
from __future__ import annotations

import json
from pathlib import Path

import pytest


REPO_ROOT = Path(__file__).resolve().parents[1]
FIXTURES = REPO_ROOT / "tools" / "skill-evals" / "web_to_compose_cases.json"


def read(relative: str) -> str:
    return (REPO_ROOT / relative).read_text(encoding="utf-8")


def load_cases() -> list[dict[str, object]]:
    loaded = json.loads(FIXTURES.read_text(encoding="utf-8"))
    assert isinstance(loaded, list) and loaded, "evaluation fixture list must be non-empty"
    return loaded


def test_web_to_compose_evaluation_cases_are_complete_and_distinct() -> None:
    cases = load_cases()
    identifiers = set()
    valid_sources = {"raw-html-css", "tailwind", "shadcn"}
    valid_ownership = {"shared", "product-local", "one-off"}

    for case in cases:
        assert set(case) == {
            "id", "request", "source", "expected_route", "expected_ownership",
            "expected_warning", "forbidden",
        }
        identifier = case["id"]
        assert isinstance(identifier, str) and identifier not in identifiers
        identifiers.add(identifier)
        assert isinstance(case["request"], str) and case["request"].strip()
        assert case["source"] in valid_sources
        assert case["expected_ownership"] in valid_ownership
        assert isinstance(case["expected_route"], list) and case["expected_route"]
        assert all(isinstance(route, str) and route.startswith("awake-") for route in case["expected_route"])
        assert isinstance(case["expected_warning"], str)
        assert isinstance(case["forbidden"], list) and case["forbidden"]

    assert {case["source"] for case in cases} == valid_sources
    assert {case["expected_ownership"] for case in cases} == valid_ownership


@pytest.mark.parametrize(
    ("path", "requirements"),
    [
        (
            ".agents/skills/awake-web-to-compose/SKILL.md",
            [
                "awake-tailwind-to-compose",
                "awake-shadcn-to-compose",
                "existing shared recipe",
                "product-local recipe",
                "deliberate one-off",
                "no HTML, CSS, Tailwind class strings, React, or browser runtime dependency",
            ],
        ),
        (
            ".agents/skills/awake-ui-design-audit/references/component-extraction.md",
            [
                "Existing shared recipe",
                "Product-local recipe",
                "Deliberate one-off composition",
                "Many references were assembled page by page",
                "Never force Shadcn visual identity",
            ],
        ),
        (
            ".agents/skills/awake-ui-design-audit/SKILL.md",
            [
                "## Modes and trigger keywords",
                "Use **remediate mode**",
                "Remediate mode begins with the same evidence and classification pass",
                "Do not silently redesign a reference",
            ],
        ),
    ],
)
def test_skill_policy_surface_supports_the_evaluation_cases(path: str, requirements: list[str]) -> None:
    content = read(path)
    missing = [requirement for requirement in requirements if requirement not in content]
    assert not missing, f"{path} is missing policy required by evaluation fixtures: {missing}"
