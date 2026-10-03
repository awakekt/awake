#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Checks render_evidence.py on synthetic captures: python3 scripts/render_evidence_test.py"""
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
import render_evidence  # noqa: E402


def png(path: Path, colour, size=(8, 8), spot=None):
    image = Image.new("RGB", size, colour)
    if spot:
        image.putpixel((0, 0), spot)
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path)


class RenderEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.root = Path(tempfile.mkdtemp())
        self.repo = self.root / "repo"
        self.repo.mkdir()
        git = lambda *a: subprocess.run(["git", *a], cwd=self.repo, check=True, capture_output=True)
        git("init", "-q")
        git("config", "user.email", "t@t")
        git("config", "user.name", "t")
        baseline = self.repo / "m/src/desktopTest/resources/baselines/components/field.png"
        png(baseline, (10, 10, 10))
        git("add", ".")
        git("commit", "-qm", "base")
        self.base = git("rev-parse", "HEAD").stdout.decode().strip()
        png(baseline, (10, 10, 10), spot=(200, 10, 10))
        git("commit", "-qam", "head")
        self.head = git("rev-parse", "HEAD").stdout.decode().strip()

    def run_prepare(self):
        cwd = Path.cwd()
        try:
            os.chdir(self.repo)
            render_evidence.prepare(self.root / "before", self.root / "after", self.root / "out", self.base, self.head)
        finally:
            os.chdir(cwd)
        return json.loads((self.root / "out/summary.json").read_text())

    def test_counts_changes_and_writes_a_diff_only_for_them(self):
        png(self.root / "before/scene-a.png", (50, 50, 50))
        png(self.root / "after/scene-a.png", (50, 50, 50), spot=(51, 50, 50))
        png(self.root / "before/scene-b.png", (9, 9, 9))
        png(self.root / "after/scene-b.png", (9, 9, 9))
        scenes = {s["name"]: s for s in self.run_prepare()["scenes"]}

        self.assertEqual((scenes["scene-a"]["changed"], scenes["scene-a"]["max"]), (1, 1))
        self.assertEqual(scenes["scene-b"]["status"], "same")
        self.assertTrue((self.root / "out/diff/scene-a.png").is_file())
        self.assertFalse((self.root / "out/diff/scene-b.png").exists())

    def test_baselines_lead_and_rounding_is_labelled(self):
        png(self.root / "before/scene-a.png", (50, 50, 50))
        png(self.root / "after/scene-a.png", (50, 50, 50), spot=(52, 50, 50))
        self.run_prepare()
        text = render_evidence.comment(self.root / "out", "P", self.head, "o/r", "all")

        self.assertLess(text.index("Baselines this PR re-recorded"), text.index("Render scenes"))
        self.assertIn("up to 190/255", text)
        self.assertIn("up to 2/255, rounding-level", text)

    def test_size_changes_are_not_reported_as_render_changes(self):
        png(self.root / "before/scene-a.png", (50, 50, 50), size=(4, 4))
        png(self.root / "after/scene-a.png", (50, 50, 50), size=(8, 8))
        summary = self.run_prepare()
        text = render_evidence.comment(self.root / "out", "P", self.head, "o/r", "all")

        self.assertEqual(summary["scenes"][0]["status"], "resized")
        self.assertIn("(0 of 1 changed)", text)
        self.assertIn("Not comparable", text)

    def test_says_nothing_when_nothing_changed(self):
        subprocess.run(["git", "reset", "-q", "--hard", self.base], cwd=self.repo, check=True)
        self.head = self.base
        png(self.root / "before/scene-a.png", (50, 50, 50))
        png(self.root / "after/scene-a.png", (50, 50, 50))
        self.run_prepare()

        self.assertEqual(render_evidence.comment(self.root / "out", "P", self.head, "o/r", "all"), "")


if __name__ == "__main__":
    unittest.main()
