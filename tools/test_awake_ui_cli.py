#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# GATE: exit 1 means the tool itself is broken; runs under `awake verify`'s tool-tests gate.
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image


SCRIPT = Path(__file__).resolve().parent.parent / "scripts" / "awake_ui.py"
SPEC = importlib.util.spec_from_file_location("awake_ui", SCRIPT)
awake_ui = importlib.util.module_from_spec(SPEC)
assert SPEC and SPEC.loader
sys.modules[SPEC.name] = awake_ui
SPEC.loader.exec_module(awake_ui)


class AwakeUiCliTest(unittest.TestCase):
    def test_report_performance_and_inspect_are_public_cli_commands(self) -> None:
        report = awake_ui.build_parser().parse_args(["ui", "report"])
        performance = awake_ui.build_parser().parse_args([
            "ui", "performance", "--component", "button", "--theme", "dark",
        ])
        inspect = awake_ui.build_parser().parse_args([
            "ui", "inspect", "--component", "button-group", "--state", "vertical", "--theme", "both",
        ])
        self.assertIs(report.handler, awake_ui.report_parity)
        self.assertIs(performance.handler, awake_ui.performance_report)
        self.assertIs(inspect.handler, awake_ui.inspect_component)
        self.assertEqual(performance.theme, "dark")
        self.assertEqual(inspect.state, "vertical")

    def test_audit_discovers_every_manifest_component(self) -> None:
        audit = awake_ui.build_parser().parse_args(["ui", "audit", "--theme", "both"])
        matrix = awake_ui.manifest_component_specs("both")
        manifest_components = {
            case["component"] for case in awake_ui.load_json(awake_ui.PARITY_CASES)["cases"]
        }
        self.assertIs(audit.handler, awake_ui.audit_all_components)
        self.assertEqual(set(matrix), manifest_components)
        self.assertIn("alert", matrix)
        self.assertIn("open", matrix["select"][1])

    def test_component_alias_and_reference_state_mapping_are_explicit(self) -> None:
        radio = awake_ui.component_spec("radio-group")
        self.assertEqual(radio.canonical_name, "radio")
        self.assertEqual(awake_ui.resolve_reference_cases(radio, ("rest",)), ("radio-group-states",))

    def test_unknown_state_fails_instead_of_falling_back_to_rest(self) -> None:
        with self.assertRaisesRegex(SystemExit, "no official fixture for state 'hover'"):
            awake_ui.resolve_reference_cases(awake_ui.component_spec("button"), ("hover",))

    def test_parity_mapping_is_not_guessed_from_a_filename(self) -> None:
        progress_cases = awake_ui.matching_parity_cases(awake_ui.component_spec("progress"), "light")
        self.assertEqual([case["comparisonName"] for case in progress_cases], ["progress-states-light"])

    def test_inspect_limits_the_contact_sheet_to_the_requested_fixture_state(self) -> None:
        button_group = awake_ui.component_spec("button-group")
        cases = awake_ui.matching_parity_cases_for_states(button_group, "light", ("vertical",))
        self.assertEqual([case["comparisonName"] for case in cases], ["button-group-vertical-light"])

    def test_debug_overlay_uses_semantic_bounds_lanes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            png = root / "awake.png"
            report = root / "awake.json"
            output = root / "debug.png"
            Image.new("RGBA", (20, 20), (255, 255, 255, 255)).save(png)
            report.write_text(json.dumps({
                "width": 10,
                "height": 10,
                "semantics": [{
                    "id": "button",
                    "bounds": {"x": 1, "y": 1, "w": 5, "h": 5},
                    "contentBounds": {"x": 2, "y": 2, "w": 3, "h": 3},
                    "clippedBounds": {"x": 3, "y": 3, "w": 1, "h": 1},
                }],
            }))
            original_root = awake_ui.REPO_ROOT
            try:
                awake_ui.REPO_ROOT = root
                result = awake_ui.make_debug_overlay(
                    {"awakePng": "awake.png", "semanticJson": "awake.json"}, output,
                )
            finally:
                awake_ui.REPO_ROOT = original_root
            self.assertEqual(result, output)
            self.assertTrue(output.exists())
            self.assertNotEqual(Image.open(output).getpixel((2, 2)), (255, 255, 255, 255))

    def test_contact_sheet_has_reference_awake_and_diff_columns(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reference = root / "reference.png"
            comparison_dir = root / "build/reports/ui-component-parity"
            comparison_dir.mkdir(parents=True)
            awake = comparison_dir / "button-light_awake.png"
            diff = comparison_dir / "button-light_diff.png"
            Image.new("RGBA", (4, 4), (255, 0, 0, 255)).save(reference)
            Image.new("RGBA", (4, 4), (0, 255, 0, 255)).save(awake)
            Image.new("RGBA", (4, 4), (0, 0, 255, 255)).save(diff)
            original_root = awake_ui.REPO_ROOT
            try:
                awake_ui.REPO_ROOT = root
                result = awake_ui.make_contact_sheet(
                    [{
                        "comparisonName": "button-light",
                        "referencePng": "reference.png",
                    }],
                    awake_ui.ComponentSpec("button", {}, ()),
                )
            finally:
                awake_ui.REPO_ROOT = original_root
            self.assertTrue(result.exists())
            self.assertEqual(Image.open(result).mode, "RGBA")


if __name__ == "__main__":
    unittest.main()
