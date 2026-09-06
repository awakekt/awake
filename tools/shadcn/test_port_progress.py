#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: exit 1 means the tool itself is broken; runs under `awake verify`'s tool-tests gate.
"""A progress report that miscounts is worse than none -- it reads as authoritative.

The cases here are the ones that would silently misreport: a receiver on the declaration, a file
that imports both layers mid-port, and a file that needs no port at all.
"""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

MODULE = Path(__file__).resolve().parent / "port_progress.py"
_spec = importlib.util.spec_from_file_location("port_progress", MODULE)
port_progress = importlib.util.module_from_spec(_spec)
# Registered before exec: `@dataclass` under `from __future__ import annotations` resolves its
# field types through `sys.modules[cls.__module__]`, which is None for a module loaded by path.
sys.modules["port_progress"] = port_progress
_spec.loader.exec_module(port_progress)

LEGACY = "import com.awakekt.awake.ui.headless.button"
TARGET = "import com.awakekt.awake.compose.foundation.Text"


def report(text: str) -> port_progress.FileReport:
    names = port_progress.DECLARATION.findall(text)
    return port_progress.FileReport(
        path="x.kt",
        recipes=[n for n in names if not n.endswith("Style")],
        style_helpers=[n for n in names if n.endswith("Style")],
        legacy_imports=len(port_progress.LEGACY_IMPORT.findall(text)),
        target_imports=len(port_progress.TARGET_IMPORT.findall(text)),
    )


def test_a_legacy_import_means_not_ported() -> None:
    assert report(f"{LEGACY}\nfun shadcnButton(x: Int) {{}}").status == "legacy"


def test_a_compose_import_alone_means_ported() -> None:
    assert report(f"{TARGET}\nfun shadcnButton(x: Int) {{}}").status == "PORTED"


def test_both_imports_is_flagged_rather_than_counted_as_done() -> None:
    # Half-ported is the state that most wants to be visible, and the one a naive "does it import
    # compose?" check would report as finished.
    assert report(f"{LEGACY}\n{TARGET}\nfun shadcnButton(x: Int) {{}}").status == "MIXED"


def test_a_file_importing_neither_needs_no_port() -> None:
    assert report("fun shadcnSliderTrack(x: Float) {}").status == "neutral"


def test_a_receiver_on_the_declaration_is_still_counted() -> None:
    # Most recipes are `fun UiScope.shadcnButton(...)`. Missing the receiver form would under-report
    # the denominator by almost everything.
    found = report("fun UiScope.shadcnButton(x: Int) {}")
    assert found.recipes == ["shadcnButton"], found.recipes


def test_style_helpers_are_counted_apart_from_recipes() -> None:
    found = report("fun shadcnButtonStyle(x: Int) {}\nfun shadcnButton(x: Int) {}")
    assert found.recipes == ["shadcnButton"]
    assert found.style_helpers == ["shadcnButtonStyle"]


def test_a_non_shadcn_function_is_not_counted() -> None:
    assert report("fun helper(x: Int) {}").recipes == []


def test_files_needing_no_port_stay_out_of_the_denominator() -> None:
    # Otherwise adding a correct file makes the percentage fall, which reads as going backwards.
    reports = [
        report(f"{LEGACY}\nfun shadcnA(x: Int) {{}}"),
        report("fun shadcnB(x: Int) {}"),
    ]
    rendered = port_progress.render(reports)

    assert "0/1 ported" in rendered, rendered
    assert "needs no port  1" in rendered, rendered


def test_the_real_tree_reports_without_crashing() -> None:
    assert port_progress.main([]) == 0
