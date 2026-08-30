#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GENERATOR: writes font binaries into awake/ui/text resources.
# Kinds are defined in docs/tasks/2026-08-23-ui-tooling-formalization-plan.md. Only a GATE can fail
# a build; `scripts/awake verify` runs every one.
"""Instantiates the static Roboto faces Awake ships from the variable source.

google/fonts ships Roboto as a single variable font (ofl/roboto/Roboto[wdth,wght].ttf), so the
static faces the atlas generator consumes are derived here rather than downloaded separately. One
source version means a weight change is only a weight change -- mixing releases would make every
metric difference unattributable, the same reason capture_font_reference.py pins its own file.

The variable source lives in tools/ because it is build input, not a shipped resource.

Usage:
    python3 tools/fonts-tooling/instantiate_roboto.py
"""

from __future__ import annotations

import copy
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

REPO = Path(__file__).resolve().parent.parent.parent
SOURCE = REPO / "tools/fonts-tooling/samples/Roboto[wdth,wght].ttf"
OUT_DIR = REPO / "awake/core/text/src/commonMain/resources/fonts"

# These stable faces back the Compose-shaped typography API. Requests between faces resolve to the
# closest face, matching Compose's FontFamily resolver instead of silently painting every weight
# with Regular.
FACES = {
    "Roboto-Thin.ttf": (100, OUT_DIR),
    "Roboto-Light.ttf": (300, OUT_DIR),
    "Roboto-Regular.ttf": (400, OUT_DIR),
    "Roboto-Medium.ttf": (500, OUT_DIR),
    "Roboto-SemiBold.ttf": (600, OUT_DIR),
    "Roboto-Bold.ttf": (700, OUT_DIR),
    "Roboto-Black.ttf": (900, OUT_DIR),
}


def main() -> int:
    source = TTFont(SOURCE)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for filename, (weight, destination) in FACES.items():
        destination.mkdir(parents=True, exist_ok=True)
        instance = instantiateVariableFont(
            copy.deepcopy(source), {"wght": weight, "wdth": 100}, inplace=False
        )
        out = destination / filename
        instance.save(out)
        print(f"{filename:22} wght={weight}  {out.stat().st_size:>8,} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
