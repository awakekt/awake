#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import importlib.util
from pathlib import Path


SPEC = Path(__file__).resolve().parent / "verify_ui_doc_refs.py"
spec = importlib.util.spec_from_file_location("verify_ui_doc_refs", SPEC)
assert spec and spec.loader
verify_ui_doc_refs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify_ui_doc_refs)


def test_active_docs_pass_when_no_retired_module_names_exist(tmp_path: Path) -> None:
    doc = tmp_path / "current.md"
    doc.write_text("Use :awake:compose:foundation for controls.\n", encoding="utf-8")
    original = verify_ui_doc_refs.ACTIVE_DOCS
    verify_ui_doc_refs.ACTIVE_DOCS = (doc,)
    try:
        assert verify_ui_doc_refs.main() == 0
    finally:
        verify_ui_doc_refs.ACTIVE_DOCS = original


def test_active_docs_reject_retired_module_names(tmp_path: Path) -> None:
    doc = tmp_path / "stale.md"
    doc.write_text("Use :awake:ui:ui-core for layout.\n", encoding="utf-8")
    original = verify_ui_doc_refs.ACTIVE_DOCS
    verify_ui_doc_refs.ACTIVE_DOCS = (doc,)
    try:
        assert verify_ui_doc_refs.main() == 1
    finally:
        verify_ui_doc_refs.ACTIVE_DOCS = original
