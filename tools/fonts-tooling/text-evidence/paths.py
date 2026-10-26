#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Which changed files can move a glyph, a clip or a layer: the Render Evidence workflow's text filter.

    git diff --name-only BASE...HEAD | python3 -I paths.py

Prints the matching paths and exits 0 when at least one matches, 1 when none does. A pull request
that matches gets the text-clipping sheets (`compose_sheets.py`) in its Render Evidence comment.
`test_text_evidence.py` checks the patterns against sample paths; when a text-related module moves
or a new one appears, add it here and a sample path there.
"""
from __future__ import annotations

import re
import sys

# Paths are repo-relative with forward slashes. A pattern is searched, not full-matched.
PATTERNS = [
    # Font data, the OpenType reader, kerning, atlases and the text layout all of it feeds.
    r"^awake/core/text/",
    r"^awake/ui/font-atlas-generator/",
    r"^tools/fonts-tooling/",
    # Layout and painting of text: Text, BasicTextField, the line box, and the clip, DrawScope and
    # graphicsLayer machinery a glyph passes through.
    r"^awake/compose/foundation/src/[^/]+/kotlin/.*/text/",
    r"(^|/)BasicTextField[^/]*\.kt$",
    r"^awake/compose/ui/",
    r"(^|/)GraphicsLayer[^/]*\.kt$",
    # The CPU rasterizer the sheets (and the UI snapshot tests) draw with.
    r"^awake/compose/ui-testing/src/[^/]+/kotlin/.*/UiRasterizer",
    # The glyph shader, its definition and the DSL it is written in.
    r"^awake/asset/shaders/",
    r"^awake/asset/shader-dsl/",
    # The layer compositor, the 2D pass that sets the scissor, and the draw primitives.
    r"^awake/engine/compose/",
    r"^awake/engine/render/passes2d/",
    r"^awake/core/graphics2d/",
    # Game canvas text, fields and followers.
    r"^awake/ui/canvas/",
    r"^awake/scene/canvas/",
    # shadcn text inputs and the sidebar.
    r"^awake/ui/shadcn/src/.*/Shadcn(Input|Textarea|Field|Sidebar|Text|Combobox|Command|Select)[^/]*\.kt$",
    # A font file anywhere.
    r"\.(ttf|otf)$",
    # The capture itself and this tool: a change to how the sheets are made should be seen in them.
    r"^awake/engine/render/parity/src/desktopTest/kotlin/.*/textclip/",
    r"^awake/engine/render/parity/text-clip-evidence\.gradle\.kts$",
]

_COMPILED = [re.compile(pattern, re.IGNORECASE) for pattern in PATTERNS]


def matches(path: str) -> bool:
    return any(pattern.search(path) for pattern in _COMPILED)


def matching(paths: list[str]) -> list[str]:
    return [path for path in paths if matches(path)]


def main() -> int:
    found = matching([line.strip() for line in sys.stdin if line.strip()])
    print("\n".join(found))
    return 0 if found else 1


if __name__ == "__main__":
    sys.exit(main())
