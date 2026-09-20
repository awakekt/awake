#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0

from __future__ import annotations

import importlib.util
from pathlib import Path


SCRIPT = Path(__file__).resolve().parent / "verify_no_agent_runtime_dependencies.py"
SPEC = importlib.util.spec_from_file_location("agent_runtime_boundary", SCRIPT)
module = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
SPEC.loader.exec_module(module)


def test_repository_has_no_product_agent_runtime_dependency() -> None:
    assert module.main() == 0
