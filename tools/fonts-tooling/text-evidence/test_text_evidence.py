#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Checks paths.py and compose_sheets.py: python3 -I tools/fonts-tooling/text-evidence/test_text_evidence.py"""
import json
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
import compose_sheets  # noqa: E402
import paths  # noqa: E402

# Paths a text, font, clip or layer change touches. A new text-related module adds one here.
MATCHING = [
    "awake/core/text/src/commonMain/kotlin/com/awakekt/awake/core/text/font/UiFonts.kt",
    "awake/core/text/src/commonMain/kotlin/com/awakekt/awake/core/text/font/OpenTypeUiFont.kt",
    "awake/core/text/src/commonTest/kotlin/com/awakekt/awake/core/text/KerningTest.kt",
    "awake/ui/font-atlas-generator/fonts/Roboto-Regular.ttf",
    "awake/ui/font-atlas-generator/src/desktopMain/kotlin/Generator.kt",
    "tools/fonts-tooling/instantiate_roboto.py",
    "awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/text/Text.kt",
    "awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/text/BasicTextField.kt",
    "awake/compose/ui/src/commonMain/kotlin/com/awakekt/awake/compose/ui/draw/DrawModifier.kt",
    "awake/compose/ui/src/commonMain/kotlin/com/awakekt/awake/compose/ui/graphics/GraphicsLayerScope.kt",
    "awake/compose/ui-testing/src/commonMain/kotlin/com/awakekt/awake/compose/testing/UiRasterizer.kt",
    "awake/compose/ui-testing/src/commonMain/kotlin/com/awakekt/awake/compose/testing/UiRasterizerGlyphs.kt",
    "awake/asset/shaders/src/commonMain/kotlin/com/awakekt/awake/asset/shaders/GlyphShader.kt",
    "awake/asset/shader-dsl/src/commonMain/kotlin/com/awakekt/awake/asset/shaderdsl/Dsl.kt",
    "awake/engine/compose/src/commonMain/kotlin/com/awakekt/awake/engine/compose/GraphicsLayerCompositor.kt",
    "awake/engine/render/passes2d/src/commonMain/kotlin/com/awakekt/awake/render/passes2d/Scissor.kt",
    "awake/ui/canvas/src/commonMain/kotlin/com/awakekt/awake/ui/canvas/CanvasOverlay.kt",
    "awake/scene/canvas/src/commonMain/kotlin/com/awakekt/awake/scene/canvas/CanvasBinding.kt",
    "awake/ui/shadcn/src/commonMain/kotlin/com/awakekt/awake/ui/shadcn/components/ShadcnSidebar.kt",
    "awake/ui/shadcn/src/commonMain/kotlin/com/awakekt/awake/ui/shadcn/components/ShadcnInput.kt",
    "awake/ui/shadcn/src/commonMain/kotlin/com/awakekt/awake/ui/shadcn/components/ShadcnTextarea.kt",
    "awake/core/graphics2d/src/commonMain/kotlin/com/awakekt/awake/core/graphics2d/UiDrawPrimitive.kt",
    "docs/fonts/Specimen.otf",
    "samples/engine-showcase/src/commonMain/composeResources/font/Display.TTF",
    "awake/engine/render/parity/src/desktopTest/kotlin/com/awakekt/awake/render/parity/textclip/TextClipCases.kt",
]
UNRELATED = [
    "awake/physics/api/src/commonMain/kotlin/com/awakekt/awake/physics/PhysicsWorld.kt",
    "awake/ecs/src/commonMain/kotlin/com/awakekt/awake/ecs/World.kt",
    "awake/compose/ui-testing/src/commonMain/kotlin/com/awakekt/awake/compose/testing/UiTestHost.kt",
    "awake/compose/foundation/src/commonMain/kotlin/com/awakekt/awake/compose/foundation/Scroll.kt",
    "awake/ui/shadcn/src/commonMain/kotlin/com/awakekt/awake/ui/shadcn/components/ShadcnSlider.kt",
    "awake/engine/render/passes/src/commonMain/kotlin/com/awakekt/awake/render/passes/Shadow.kt",
    "docs/release-process.md",
    "changelog/unreleased/fixed/fix-something.md",
    ".github/workflows/ci.yml",
]


class PathFilterTest(unittest.TestCase):
    def test_every_text_path_matches(self):
        missed = [p for p in MATCHING if not paths.matches(p)]
        self.assertEqual([], missed)

    def test_unrelated_paths_do_not(self):
        hit = [p for p in UNRELATED if paths.matches(p)]
        self.assertEqual([], hit)

    def test_a_font_file_matches_anywhere(self):
        self.assertTrue(paths.matches("a/b/c/font.ttf"))
        self.assertTrue(paths.matches("a/b/c/FONT.OTF"))
        self.assertFalse(paths.matches("a/b/c/font.ttf.md"))


def ink_png(path: Path, size=(40, 30), inked=()):
    image = Image.new("RGB", size, "white")
    for xy in inked:
        image.putpixel(xy, (0, 0, 0))
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


def capture(directory: Path, lost_case: int, edge_case: int, lie: bool = False):
    """A capture of one lost-ink case (`lost_case` pixels cut) and one edge case (`edge_case` ring pixels)."""
    reference = [(x, 10) for x in range(5, 25)]
    render = reference[lost_case:]
    ink_png(directory / "field-a@ref.png", inked=reference)
    ink_png(directory / "field-a@render.png", inked=render)
    (directory / "field-a.json").write_text(json.dumps({"clips": [[5, 5, 20, 10]], "layers": [], "quads": []}))
    ink_png(directory / "sdf-a-28sp@render.png", inked=[(10 + i, 5) for i in range(edge_case)])
    (directory / "sdf-a-28sp.json").write_text(json.dumps({"clips": [], "layers": [], "quads": [[10, 5, 20, 15]]}))
    rows = [
        ["field-a", "field", "A field", "Field | A", "a note", "lost", str(lost_case + (1 if lie else 0)), "0", "clips"],
        ["sdf-a-28sp", "sdf", "Distance-field edge", "A 28 sp", "ring", "edge", str(edge_case), "0", "quads"],
    ]
    (directory / "cases.tsv").write_text("".join("\t".join(r) + "\n" for r in rows), encoding="utf-8")


class ComposeSheetsTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())

    def run_sheets(self, before_lost=6, after_lost=0, with_base=True, lie=False):
        if with_base:
            capture(self.root / "before", before_lost, 3)
        capture(self.root / "after", after_lost, 0, lie=lie)
        compose_sheets.sheets(self.root / "before", self.root / "after", self.root / "out")
        return json.loads((self.root / "out" / "summary.json").read_text())

    def test_it_writes_a_sheet_per_group_and_an_overview(self):
        self.run_sheets()
        names = sorted(p.name for p in (self.root / "out").iterdir())
        self.assertEqual(["overview.png", "sheet-field.png", "sheet-sdf.png", "summary.json", "table.md"], names)

    def test_counts_and_status_per_case(self):
        summary = self.run_sheets(before_lost=6, after_lost=0)
        cases = {c["id"]: c for c in summary["cases"]}
        self.assertEqual((6, 0, "changed"), (cases["field-a"]["before"], cases["field-a"]["after"], cases["field-a"]["status"]))
        self.assertEqual((3, 0, "changed"), (cases["sdf-a-28sp"]["before"], cases["sdf-a-28sp"]["after"], cases["sdf-a-28sp"]["status"]))

    def test_an_unchanged_case_is_same(self):
        summary = self.run_sheets(before_lost=4, after_lost=4)
        self.assertEqual("same", {c["id"]: c for c in summary["cases"]}["field-a"]["status"])

    def test_a_base_with_no_capture_makes_every_case_new(self):
        summary = self.run_sheets(with_base=False)
        self.assertFalse(summary["base_captured"])
        self.assertEqual({"new"}, {c["status"] for c in summary["cases"]})
        self.assertIn("no text-clip capture", compose_sheets.comment(self.root / "out", "https://x/p"))

    def test_a_count_the_pngs_do_not_bear_out_stops_the_run(self):
        with self.assertRaises(SystemExit):
            self.run_sheets(lie=True)

    def test_the_comment_names_what_changed_and_links_the_sheets(self):
        self.run_sheets(before_lost=6, after_lost=0)
        text = compose_sheets.comment(self.root / "out", "https://x/pr-1/abc")
        self.assertIn("### Text clipping", text)
        self.assertIn("**2 of 2 cases changed.**", text)
        self.assertIn("**-6**, less lost ink", text)
        self.assertIn("Field \\| A", text, "a pipe in a title must not split the table row")
        self.assertIn('<img src="https://x/pr-1/abc/text/overview.png"', text)
        self.assertIn("https://x/pr-1/abc/text/sheet-field.png", text)

    def test_a_comment_with_nothing_changed_says_so_and_skips_the_overview_when_all_clean(self):
        capture(self.root / "before", 0, 0)
        capture(self.root / "after", 0, 0)
        compose_sheets.sheets(self.root / "before", self.root / "after", self.root / "out")
        text = compose_sheets.comment(self.root / "out", "https://x/p")
        self.assertIn("No case changed.", text)
        self.assertNotIn("overview.png", text)


if __name__ == "__main__":
    unittest.main()
