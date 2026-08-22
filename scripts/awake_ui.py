#!/usr/bin/env python3
# Copyright (c) Ron June Valdoz
# SPDX-License-Identifier: Apache-2.0
"""Awake UI reference, preview, debug-overlay, and parity command line.

The CLI deliberately orchestrates the existing capture/render/compare tools instead of owning a
second renderer. A component must have a committed reference case and Awake preview mapping
before this command will claim to generate or validate it.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


REPO_ROOT = Path(__file__).resolve().parent.parent
REFERENCE_CASES = REPO_ROOT / "tools" / "shadcn_reference_cases.json"
PARITY_CASES = REPO_ROOT / "tools" / "ui_component_parity_cases.json"
PARITY_MANIFEST = REPO_ROOT / "tools" / "shadcn_parity_manifest.json"
PARITY_TEST = ":samples:ui-showcase:desktopTest"
PARITY_TEST_FILTER = "*ShadcnParityScreenshotTest*"


@dataclass(frozen=True)
class ComponentSpec:
    canonical_name: str
    reference_by_state: dict[str, tuple[str, ...]]
    parity_prefixes: tuple[str, ...]


# This is intentionally a small explicit mapping, not fuzzy filename discovery. It prevents a
# new component from accidentally being compared with a merely similarly-named fixture.
COMPONENTS: dict[str, ComponentSpec] = {
    "button": ComponentSpec(
        "button",
        {"rest": ("button-variants",), "disabled": ("button-disabled",), "sizes": ("button-sizes",), "all": ("button-variants", "button-disabled", "button-sizes")},
        ("button-variants",),
    ),
    "button-group": ComponentSpec("button-group", {"rest": ("button-group-basic", "button-group-vertical"), "horizontal": ("button-group-basic",), "vertical": ("button-group-vertical",), "all": ("button-group-basic", "button-group-vertical")}, ("button-group-basic", "button-group-vertical")),
    "badge": ComponentSpec("badge", {"rest": ("badge-variants",), "all": ("badge-variants",)}, ("badge-variants",)),
    "checkbox": ComponentSpec("checkbox", {"rest": ("checkbox-states",), "all": ("checkbox-states",)}, ("checkbox-states",)),
    "radio": ComponentSpec("radio", {"rest": ("radio-group-states",), "all": ("radio-group-states",)}, ("radio-group-states",)),
    "progress": ComponentSpec("progress", {"rest": ("progress-states",), "all": ("progress-states",)}, ("progress-states",)),
    "switch": ComponentSpec("switch", {"rest": ("switch-states",), "all": ("switch-states",)}, ("switch-states",)),
    "input": ComponentSpec("input", {"rest": ("input-states",), "all": ("input-states",)}, ("input-states",)),
    "tabs": ComponentSpec("tabs", {"rest": ("tabs-states",), "all": ("tabs-states",)}, ("tabs-states",)),
    "slider": ComponentSpec("slider", {"rest": ("slider-states",), "all": ("slider-states",)}, ("slider-states",)),
    "select": ComponentSpec("select", {"rest": ("select-closed",), "all": ("select-closed",)}, ("select-closed",)),
    "card": ComponentSpec("card", {"rest": ("card-login",), "all": ("card-login",)}, ("card-login",)),
    "dropdown-menu": ComponentSpec("dropdown-menu", {"open": ("dropdown-menu-states",), "all": ("dropdown-menu-states",)}, ("dropdown-menu-states",)),
    "popover": ComponentSpec("popover", {"open": ("popover-states",), "all": ("popover-states",)}, ("popover-states",)),
    "tooltip": ComponentSpec("tooltip", {"open": ("tooltip-open",), "all": ("tooltip-open",)}, ()),
    "dialog": ComponentSpec("dialog", {"open": ("dialog-open",), "all": ("dialog-open",)}, ()),
}

ALIASES = {
    "radio-group": "radio",
    "text-field": "input",
    "textfield": "input",
    "dropdown": "dropdown-menu",
}

DEBUG_COLORS = {
    "bounds": (51, 153, 255, 230),
    "contentBounds": (77, 217, 89, 230),
    "clippedBounds": (255, 89, 77, 230),
}


def fail(message: str) -> "None":
    raise SystemExit(f"error: {message}")


def component_spec(component: str) -> ComponentSpec:
    key = ALIASES.get(component.lower(), component.lower())
    spec = COMPONENTS.get(key)
    if spec is None:
        known = ", ".join(sorted(COMPONENTS))
        fail(f"unknown component {component!r}; known fixtures: {known}")
    return spec


def split_states(value: str) -> tuple[str, ...]:
    states = tuple(part.strip().lower() for part in value.split(",") if part.strip())
    if not states:
        fail("--state must contain at least one state")
    return states


def resolve_reference_cases(spec: ComponentSpec, states: Iterable[str]) -> tuple[str, ...]:
    resolved: list[str] = []
    for state in states:
        case_ids = spec.reference_by_state.get(state)
        if case_ids is None:
            supported = ", ".join(sorted(spec.reference_by_state))
            fail(f"{spec.canonical_name} has no official fixture for state {state!r}; supported: {supported}")
        resolved.extend(case_ids)
    return tuple(dict.fromkeys(resolved))


def load_json(path: Path) -> dict:
    try:
        return json.loads(path.read_text())
    except FileNotFoundError:
        fail(f"required manifest is missing: {path.relative_to(REPO_ROOT)}")


def ensure_reference_cases_exist(case_ids: Iterable[str]) -> None:
    available = {case["id"] for case in load_json(REFERENCE_CASES)["cases"]}
    missing = set(case_ids) - available
    if missing:
        fail("fixture mapping points to missing official reference case(s): " + ", ".join(sorted(missing)))


def matching_parity_cases(spec: ComponentSpec, theme: str) -> list[dict]:
    cases = load_json(PARITY_CASES)["cases"]
    matches = [
        case
        for case in cases
        if any(case["name"].startswith(prefix + "-") for prefix in spec.parity_prefixes)
        and (theme == "both" or case["name"].endswith("-" + theme))
    ]
    if not matches:
        fail(
            f"{spec.canonical_name} has no Awake parity fixture for theme {theme!r}. "
            "Add one to tools/ui_component_parity_cases.json before using this command."
        )
    return matches


def run(command: list[str], *, check: bool = True) -> subprocess.CompletedProcess[str]:
    print("+", " ".join(command))
    return subprocess.run(command, cwd=REPO_ROOT, text=True, check=check)


def capture_reference(args: argparse.Namespace) -> int:
    spec = component_spec(args.component)
    case_ids = resolve_reference_cases(spec, split_states(args.state))
    ensure_reference_cases_exist(case_ids)
    command = [sys.executable, "tools/capture_shadcn_local.py", "--only", ",".join(case_ids), "--theme", args.theme]
    if args.skip_build:
        command.append("--skip-build")
    run(command)
    return 0


def run_awake_preview(cases: Iterable[dict]) -> None:
    # Run both fixture classes in ONE Gradle test invocation. Separate invocations replace the
    # generated preview directory, which used to leave Card evidence but erase Dropdown/Button.
    command = ["./gradlew", PARITY_TEST, "--tests", PARITY_TEST_FILTER]
    if any(Path(case["awakePng"]).name == "awake-card-light.png" for case in cases):
        command.extend(("--tests", "*ShadcnReferenceComparisonTest*"))
    command.extend(("--rerun-tasks", "--no-daemon", "--quiet"))
    # It may return non-zero while old Awake-to-Awake goldens are intentionally stale, but it
    # still writes the requested preview PNG/semantic JSON; artefact existence below is the
    # generation contract.
    result = run(command, check=False)
    if result.returncode:
        print(
            "note: parity golden test returned non-zero; generated files are checked next. "
            "Do not re-record goldens merely to make this command green.",
            file=sys.stderr,
        )


def verify_awake_files(cases: Iterable[dict]) -> None:
    missing: list[str] = []
    for case in cases:
        for key in ("awakePng", "semanticJson"):
            path = REPO_ROOT / case[key]
            if not path.exists():
                missing.append(case[key])
    if missing:
        fail("Awake preview generation did not create: " + ", ".join(missing))


def make_debug_overlay(case: dict, output: Path | None) -> Path:
    try:
        from PIL import Image, ImageDraw
    except ImportError:
        fail("debug overlay requires Pillow; install it with: pip3 install pillow")

    png_path = REPO_ROOT / case["awakePng"]
    json_path = REPO_ROOT / case["semanticJson"]
    image = Image.open(png_path).convert("RGBA")
    document = json.loads(json_path.read_text())
    logical_width = float(document["width"])
    logical_height = float(document["height"])
    scale_x = image.width / logical_width
    scale_y = image.height / logical_height
    overlay = Image.new("RGBA", image.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)

    for node in document.get("semantics", []):
        for key, color in DEBUG_COLORS.items():
            bounds = node.get(key)
            if not bounds:
                continue
            x = float(bounds["x"]) * scale_x
            y = float(bounds["y"]) * scale_y
            right = (float(bounds["x"]) + float(bounds["w"])) * scale_x
            bottom = (float(bounds["y"]) + float(bounds["h"])) * scale_y
            draw.rectangle((x, y, right, bottom), outline=color, width=max(1, round(scale_x)))

    result = Image.alpha_composite(image, overlay)
    destination = output or REPO_ROOT / "build" / "reports" / "ui-debug" / f"{Path(case['awakePng']).stem}-layout.png"
    if not destination.is_absolute():
        destination = REPO_ROOT / destination
    destination.parent.mkdir(parents=True, exist_ok=True)
    result.save(destination)
    return destination


def preview_awake(args: argparse.Namespace) -> int:
    spec = component_spec(args.component)
    states = split_states(args.state)
    # A preview can only claim a state that its committed fixture actually encodes. This guard
    # prevents `--state hover` from silently producing the static rest image.
    resolve_reference_cases(spec, states)
    if args.variant not in (None, "all"):
        fail("--variant is not yet parameterized by the Kotlin preview registry; use --variant all or add a fixture first")
    if any(value is not None for value in (args.style, args.base, args.accent)):
        fail("--style, --base, and --accent are not yet parameterized by the Kotlin preview registry; add a named fixture first")
    if args.output is not None and not args.debug_layout:
        fail("--output is only valid together with --debug-layout")
    cases = matching_parity_cases(spec, args.theme)
    run_awake_preview(cases)
    verify_awake_files(cases)
    for case in cases:
        print("generated", case["awakePng"])
        if args.debug_layout:
            print("generated debug overlay", make_debug_overlay(case, args.output))
    return 0


def validate_component(args: argparse.Namespace) -> int:
    spec = component_spec(args.component)
    cases = matching_parity_cases(spec, args.theme)
    verify_awake_files(cases)
    for case in cases:
        reference = REPO_ROOT / case["referencePng"]
        if not reference.exists():
            fail(
                f"official reference is missing: {case['referencePng']}. "
                f"Generate it first with `awake ui reference --component {spec.canonical_name} --state rest --theme {args.theme}`."
            )
        command = [
            sys.executable,
            "tools/compare_component_crops.py",
            "--awake-png", case["awakePng"],
            "--semantic-json", case["semanticJson"],
            "--reference-png", case["referencePng"],
            "--name", case["name"],
            "--padding", str(case.get("padding", 0)),
        ]
        for node_id in case.get("nodeIds", [case.get("nodeId")]):
            if node_id is not None:
                command.extend(("--node-id", node_id))
        if args.strict:
            if "maxMismatchPct" not in case:
                fail(f"{case['name']} has no reviewed mismatch threshold; strict validation is not allowed")
            command.extend(("--max-mismatch-pct", str(case["maxMismatchPct"]), "--fail-on-mismatch"))
        run(command)
    return 0


def report_parity(args: argparse.Namespace) -> int:
    command = [sys.executable, "tools/generate_ui_parity_report.py"]
    if args.output:
        command.extend(("--out", str(args.output)))
    run(command)
    return 0


def performance_report(args: argparse.Namespace) -> int:
    """Measure the parity-tool slice, never pretend this is UI frame performance."""
    spec = component_spec(args.component)
    cases = matching_parity_cases(spec, args.theme)
    started = time.perf_counter()
    validate_component(argparse.Namespace(component=args.component, theme=args.theme, strict=False))
    elapsed_ms = round((time.perf_counter() - started) * 1000, 2)
    out = REPO_ROOT / "build/reports/ui-parity/performance.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({
        "kind": "parity-tool-performance",
        "component": spec.canonical_name,
        "theme": args.theme,
        "cases": [case["name"] for case in cases],
        "comparisonElapsedMs": elapsed_ms,
        "note": "Measures crop/diff/report tooling only; use the UI benchmark suite for frame performance.",
    }, indent=2) + "\n")
    print("wrote", out.relative_to(REPO_ROOT))
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="awake", description=__doc__)
    commands = parser.add_subparsers(dest="area", required=True)
    ui = commands.add_parser("ui", help="Generate and validate UI reference fixtures")
    ui_commands = ui.add_subparsers(dest="command", required=True)

    reference = ui_commands.add_parser("reference", help="Capture a pinned official shadcn reference")
    reference.add_argument("--component", required=True)
    reference.add_argument("--state", default="rest", help="fixture state, comma-separated")
    reference.add_argument("--theme", default="light", choices=("light", "dark", "both"))
    reference.add_argument("--skip-build", action="store_true")
    reference.set_defaults(handler=capture_reference)

    preview = ui_commands.add_parser("preview", help="Generate an existing Awake component preview")
    preview.add_argument("--component", required=True)
    preview.add_argument("--state", default="rest", help="fixture state, comma-separated")
    preview.add_argument("--theme", default="light", choices=("light", "dark", "both"))
    preview.add_argument("--variant")
    preview.add_argument("--style")
    preview.add_argument("--base")
    preview.add_argument("--accent")
    preview.add_argument("--debug-layout", action="store_true", help="write blue/green/red semantic bounds overlay")
    preview.add_argument("--output", type=Path, help="debug overlay output path; valid only with --debug-layout")
    preview.set_defaults(handler=preview_awake)

    validate = ui_commands.add_parser("validate", help="Compare an Awake preview with its official component crop")
    validate.add_argument("--component", required=True)
    validate.add_argument("--theme", default="light", choices=("light", "dark", "both"))
    validate.add_argument("--strict", action="store_true", help="fail on a reviewed per-case threshold")
    validate.set_defaults(handler=validate_component)

    report = ui_commands.add_parser("report", help="Generate the manifest-backed parity report")
    report.add_argument("--output", type=Path, help="JSON report output, relative to repository root")
    report.set_defaults(handler=report_parity)

    performance = ui_commands.add_parser("performance", help="Measure the parity-tool comparison path")
    performance.add_argument("--component", required=True)
    performance.add_argument("--theme", default="light", choices=("light", "dark", "both"))
    performance.set_defaults(handler=performance_report)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.area != "ui":
        fail(f"unsupported area {args.area!r}")
    return int(args.handler(args))


if __name__ == "__main__":
    raise SystemExit(main())
