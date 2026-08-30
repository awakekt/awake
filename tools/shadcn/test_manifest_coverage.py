#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: exit 1 means a component in scripts/awake_ui.py's COMPONENTS mapping lost its manifest
# case -- the exact silent-drift failure mode that motivated collapsing two duplicate case files
# into tools/shadcn/shadcn_parity_manifest.json.
"""Every component the CLI knows how to preview/validate must resolve at least one manifest case."""
from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
MODULE = REPO_ROOT / "scripts" / "awake_ui.py"
_spec = importlib.util.spec_from_file_location("awake_ui", MODULE)
awake_ui = importlib.util.module_from_spec(_spec)
sys.modules["awake_ui"] = awake_ui
_spec.loader.exec_module(awake_ui)


# Pre-existing gaps: COMPONENTS declares a parity prefix but no case has ever been registered
# for it in either the old duplicate files or the consolidated manifest. Tracked here instead of
# silently passing so a real fix removes one line instead of rediscovering the gap from scratch.
KNOWN_MISSING = {"slider"}


def test_every_component_with_parity_prefixes_has_a_manifest_case() -> None:
    missing = []
    for name, spec in awake_ui.COMPONENTS.items():
        if not spec.parity_prefixes or name in KNOWN_MISSING:
            continue  # not yet wired to parity fixtures (e.g. tooltip, dialog, KNOWN_MISSING)
        try:
            awake_ui.matching_parity_cases(spec, "both")
        except SystemExit:
            missing.append(name)
    assert not missing, f"components with no matching manifest case: {missing}"


if __name__ == "__main__":
    test_every_component_with_parity_prefixes_has_a_manifest_case()
    print("ok")
