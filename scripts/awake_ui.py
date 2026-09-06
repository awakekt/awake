#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Awake UI reference, preview, debug-overlay, and parity command line.

The CLI deliberately orchestrates the existing capture/render/compare tools instead of owning a
second renderer. A component must have a committed reference case and Awake preview mapping
before this command will claim to generate or validate it.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


REPO_ROOT = Path(__file__).resolve().parent.parent
REFERENCE_CASES = REPO_ROOT / "tools" / "shadcn" / "shadcn_reference_cases.json"
PARITY_CASES = REPO_ROOT / "tools" / "shadcn" / "shadcn_parity_manifest.json"
PARITY_TEST = ":samples:ui-showcase:desktopTest"
PARITY_TEST_FILTER = "*ShadcnComposeParityPreviewTest*"


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
        {
            "rest": ("button-variants",),
            "icons": ("button-icons",),
            "disabled": ("button-disabled",),
            "sizes": ("button-sizes",),
            "all": ("button-variants", "button-icons", "button-disabled", "button-sizes"),
        },
        ("button-variants", "button-icons", "button-sizes", "button-disabled"),
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
    "select": ComponentSpec("select", {"rest": ("select-closed",), "open": ("select-open",), "all": ("select-closed", "select-open")}, ("select-closed", "select-open")),
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
        if any(case["comparisonName"].startswith(prefix + "-") for prefix in spec.parity_prefixes)
        and (theme == "both" or case["comparisonName"].endswith("-" + theme))
    ]
    if not matches:
        fail(
            f"{spec.canonical_name} has no Awake parity fixture for theme {theme!r}. "
            "Add one to tools/shadcn/shadcn_parity_manifest.json before using this command."
        )
    return matches


def matching_parity_cases_for_states(spec: ComponentSpec, theme: str, states: Iterable[str]) -> list[dict]:
    """Return only the registered Awake cases paired with the requested reference cases."""
    reference_case_ids = set(resolve_reference_cases(spec, states))
    cases = [case for case in matching_parity_cases(spec, theme) if case["referenceCase"] in reference_case_ids]
    if not cases:
        fail(
            f"{spec.canonical_name} has no registered Awake parity case for state(s) "
            f"{', '.join(states)} and theme {theme!r}."
        )
    return cases


def manifest_component_specs(theme: str) -> dict[str, tuple[ComponentSpec, tuple[str, ...], list[dict]]]:
    """Build the aggregate audit matrix from the manifest, not from a second component list."""
    cases = [case for case in load_json(PARITY_CASES)["cases"] if theme == "both" or case["theme"] == theme]
    grouped: dict[str, list[dict]] = {}
    for case in cases:
        grouped.setdefault(case["component"], []).append(case)

    result = {}
    for name, component_cases in grouped.items():
        reference_by_state: dict[str, list[str]] = {}
        for case in component_cases:
            reference_by_state.setdefault(case["state"], []).append(case["referenceCase"])
        result[name] = (
            ComponentSpec(
                canonical_name=name,
                reference_by_state={state: tuple(dict.fromkeys(ids)) for state, ids in reference_by_state.items()},
                parity_prefixes=(),
            ),
            tuple(dict.fromkeys(case["state"] for case in component_cases)),
            component_cases,
        )
    return result


def run(command: list[str], *, check: bool = True) -> subprocess.CompletedProcess[str]:
    print("+", " ".join(command))
    return subprocess.run(command, cwd=REPO_ROOT, text=True, check=check)


def capture_reference(args: argparse.Namespace) -> int:
    spec = component_spec(args.component)
    case_ids = resolve_reference_cases(spec, split_states(args.state))
    ensure_reference_cases_exist(case_ids)
    command = [sys.executable, "tools/shadcn/capture_shadcn_local.py", "--only", ",".join(case_ids), "--theme", args.theme]
    if args.skip_build:
        command.append("--skip-build")
    run(command)
    return 0


def run_awake_preview(cases: Iterable[dict]) -> None:
    command = ["./gradlew", PARITY_TEST, "--tests", PARITY_TEST_FILTER, "--rerun-tasks", "--no-daemon", "--quiet"]
    result = run(command, check=False)
    if result.returncode:
        print(
            "note: parity preview generation returned non-zero; generated files are checked next.",
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


def validate_cases(cases: Iterable[dict], spec: ComponentSpec, theme: str, strict: bool) -> None:
    verify_awake_files(cases)
    for case in cases:
        reference = REPO_ROOT / case["referencePng"]
        if not reference.exists():
            fail(
                f"official reference is missing: {case['referencePng']}. Generate it first with "
                f"`awake ui reference --component {spec.canonical_name} --state rest --theme {theme}`."
            )
        command = [
            sys.executable,
            ".agents/skills/awake-ui-verification/scripts/compare_component_crops.py",
            "--awake-png", case["awakePng"],
            "--semantic-json", case["semanticJson"],
            "--reference-png", case["referencePng"],
            "--name", case["comparisonName"],
            "--padding", str(case.get("padding", 0)),
        ]
        # Distinct from nodeIds: geometry/relationship checks need every semantic node (e.g. a
        # popover's trigger AND content, to measure the gap between them), but the pixel crop must
        # match whatever region the reference PNG itself was captured at -- often narrower.
        crop_node_ids = case.get("paintNodeIds", case["nodeIds"])
        for node_id in crop_node_ids:
            if node_id is not None:
                command.extend(("--node-id", node_id))
        if strict:
            if "maxMismatchPct" not in case:
                fail(f"{case['comparisonName']} has no reviewed mismatch threshold; strict validation is not allowed")
            command.extend(("--max-mismatch-pct", str(case["maxMismatchPct"]), "--fail-on-mismatch"))
        run(command)


def validate_component(args: argparse.Namespace) -> int:
    spec = component_spec(args.component)
    validate_cases(matching_parity_cases(spec, args.theme), spec, args.theme, args.strict)
    return 0


def report_parity(args: argparse.Namespace) -> int:
    command = [
        sys.executable,
        ".agents/skills/awake-ui-verification/scripts/generate_ui_parity_report.py",
        "--manifest",
        str(PARITY_CASES),
    ]
    if args.output:
        command.extend(("--out", str(args.output)))
    run(command)
    return 0


def file_digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_inspect_provenance(spec: ComponentSpec, cases: Iterable[dict], states: Iterable[str], theme: str) -> Path:
    """Record exactly the newly generated evidence an inspect review refers to."""
    files = [REFERENCE_CASES, PARITY_CASES, REPO_ROOT / "samples/ui-showcase/src/desktopTest/kotlin/com/awakekt/awake/sample/uishowcase/ui/ShadcnComposeParityPreviewTest.kt"]
    files.extend(REPO_ROOT / case[key] for case in cases for key in ("referencePng", "referenceJson", "awakePng", "semanticJson"))
    evidence = []
    for path in files:
        if not path.exists():
            fail(f"inspect did not generate required evidence: {path.relative_to(REPO_ROOT)}")
        evidence.append({"path": str(path.relative_to(REPO_ROOT)), "sha256": file_digest(path)})
    out = REPO_ROOT / "build/reports/ui-parity" / f"{spec.canonical_name}-inspect.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({
        "kind": "awake-compose-parity-inspect",
        "component": spec.canonical_name,
        "states": list(states),
        "theme": theme,
        "generatedAtEpochMs": round(time.time() * 1000),
        "previewHarness": "ShadcnComposeParityPreviewTest using composeFrame/captureSemantics",
        "evidence": evidence,
    }, indent=2) + "\n")
    return out


def make_contact_sheet(cases: Iterable[dict], spec: ComponentSpec) -> Path:
    """Make the three images needed for visual parity review available in one file."""
    try:
        from PIL import Image, ImageDraw, ImageFont
    except ImportError:
        fail("contact sheet requires Pillow; install it with: pip3 install pillow")

    rows = []
    for case in cases:
        paths = [
            REPO_ROOT / case["referencePng"],
            REPO_ROOT / "build/reports/ui-component-parity" / f"{case['comparisonName']}_awake.png",
            REPO_ROOT / "build/reports/ui-component-parity" / f"{case['comparisonName']}_diff.png",
        ]
        if any(not path.exists() for path in paths):
            fail(f"contact sheet is missing comparison output for {case['comparisonName']}; run validation first")
        rows.append((case["comparisonName"], [Image.open(path).convert("RGBA") for path in paths]))

    font = ImageFont.load_default()
    label_height, gutter, row_gap = 18, 12, 16
    columns = ("Reference", "Awake Compose", "Diff")
    column_widths = [max(images[index].width for _, images in rows) for index in range(3)]
    width = sum(column_widths) + gutter * 4
    row_heights = [label_height + max(image.height for image in images) for _, images in rows]
    height = label_height + sum(row_heights) + row_gap * (len(rows) - 1) + gutter * 2
    sheet = Image.new("RGBA", (width, height), (18, 18, 20, 255))
    draw = ImageDraw.Draw(sheet)
    x = gutter
    for label, column_width in zip(columns, column_widths):
        draw.text((x, 4), label, fill=(235, 235, 235, 255), font=font)
        x += column_width + gutter
    y = label_height + gutter
    for name, images in rows:
        draw.text((gutter, y), name, fill=(180, 220, 255, 255), font=font)
        y += label_height
        x = gutter
        for image, column_width in zip(images, column_widths):
            sheet.alpha_composite(image, (x, y))
            x += column_width + gutter
        y += max(image.height for image in images) + row_gap
    out = REPO_ROOT / "build/reports/ui-parity" / f"{spec.canonical_name}-inspect.png"
    out.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(out)
    return out


def inspect_component(args: argparse.Namespace) -> int:
    """Regenerate current Compose evidence and package it for one visual review."""
    spec = component_spec(args.component)
    states = split_states(args.state)
    reference_case_ids = resolve_reference_cases(spec, states)
    ensure_reference_cases_exist(reference_case_ids)
    cases = matching_parity_cases_for_states(spec, args.theme, states)

    command = [sys.executable, "tools/shadcn/capture_shadcn_local.py", "--only", ",".join(reference_case_ids), "--theme", args.theme]
    if args.skip_reference_build:
        command.append("--skip-build")
    run(command)
    run_awake_preview(cases)
    verify_awake_files(cases)
    validate_cases(cases, spec, args.theme, strict=False)
    report_parity(argparse.Namespace(output=None))

    report_path = REPO_ROOT / "build/reports/ui-parity/report.json"
    report_data = json.loads(report_path.read_text())
    requested = {case["id"] for case in cases}
    drift_cases = [case for case in report_data["cases"] if case["id"] in requested and case["geometry"]["status"] == "drift"]
    overlays = [make_debug_overlay(next(source for source in cases if source["id"] == case["id"]), None) for case in drift_cases]
    contact_sheet = make_contact_sheet(cases, spec)
    provenance = write_inspect_provenance(spec, cases, states, args.theme)
    print("review contact sheet", contact_sheet.relative_to(REPO_ROOT))
    print("fresh Compose evidence", provenance.relative_to(REPO_ROOT))
    for overlay in overlays:
        print("geometry debug overlay", overlay.relative_to(REPO_ROOT))
    return 0


def audit_all_components(args: argparse.Namespace) -> int:
    """Run one fresh reference/Compose/diff chain for every manifest-registered component."""
    matrix = manifest_component_specs(args.theme)
    if not matrix:
        fail(f"parity manifest has no cases for theme {args.theme!r}")

    all_cases = [case for _, _, cases in matrix.values() for case in cases]
    reference_case_ids = tuple(dict.fromkeys(case["referenceCase"] for case in all_cases))
    ensure_reference_cases_exist(reference_case_ids)
    command = [
        sys.executable,
        "tools/shadcn/capture_shadcn_local.py",
        "--only",
        ",".join(reference_case_ids),
        "--theme",
        args.theme,
    ]
    if args.skip_reference_build:
        command.append("--skip-build")
    run(command)

    # The Compose parity preview task renders the complete registered preview registry in one
    # deterministic run; filtering happens below when validating and packaging evidence.
    run_awake_preview(all_cases)
    verify_awake_files(all_cases)

    report_parity(argparse.Namespace(output=None))
    report_path = REPO_ROOT / "build/reports/ui-parity/report.json"
    report_data = json.loads(report_path.read_text())
    report_by_id = {case["id"]: case for case in report_data["cases"]}
    components: list[dict] = []
    for name, (spec, states, cases) in matrix.items():
        validate_cases(cases, spec, args.theme, strict=False)
        contact_sheet = make_contact_sheet(cases, spec)
        components.append({
            "component": name,
            "states": list(states),
            "cases": [
                {
                    "id": case["id"],
                    "comparisonName": case["comparisonName"],
                    "status": report_by_id.get(case["id"], {}).get("geometry", {}).get("status", "missing"),
                }
                for case in cases
            ],
            "contactSheet": str(contact_sheet.relative_to(REPO_ROOT)),
        })

    out = REPO_ROOT / "build/reports/ui-parity/all-components-inspect.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps({
        "kind": "awake-compose-parity-audit",
        "theme": args.theme,
        "generatedAtEpochMs": round(time.time() * 1000),
        "componentCount": len(components),
        "caseCount": len(all_cases),
        "components": components,
        "report": "build/reports/ui-parity/report.json",
    }, indent=2) + "\n")
    print("wrote", out.relative_to(REPO_ROOT))
    print(f"audited {len(components)} components and {len(all_cases)} registered cases")
    for component in components:
        print("review contact sheet", component["contactSheet"])
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
        "cases": [case["comparisonName"] for case in cases],
        "comparisonElapsedMs": elapsed_ms,
        "note": "Measures crop/diff/report tooling only; use the UI benchmark suite for frame performance.",
    }, indent=2) + "\n")
    print("wrote", out.relative_to(REPO_ROOT))
    return 0


# Every gate in the repo, in one place. A gate is a tool that FAILS -- exit non-zero means the tree
# is wrong. That is the distinction `docs/tasks/2026-08-23-ui-tooling-formalization-plan.md` draws
# between a gate, a generator (writes a committed file) and an investigation tool (produces evidence
# for a human and proves nothing on its own).
#
# Adding a gate is one row. Anything not listed here cannot fail a build, by definition.
GATES: list[tuple[str, list[str], str]] = [
    (
        "shadcn-reference",
        ["tools/shadcn/verify_shadcn_reference.sh"],
        "generated token table is current; reports upstream drift without failing",
    ),
    (
        "generated-files",
        ["python3", "tools/verify_generated.py"],
        "committed generated files still match their generators",
    ),
    (
        "ui-doc-refs",
        ["python3", "tools/verify_ui_doc_refs.py"],
        "active UI documentation does not advertise deleted modules",
    ),
    (
        "tool-tests",
        [
            sys.executable, "-m", "pytest", "-q",
            # ".agents/skills", not "skills": there is no skills/ at the repo root, and pytest
            # exits on the bad argument before collecting anything -- so this gate reported
            # FAILED on every run while never executing a single test.
            "tools", ".agents/skills",
            # The vendored MoltenVK/SPIRV trees carry their own test_*.py that are not ours.
            "--ignore=awake",
        ],
        "unit tests for the tools themselves",
    ),
    (
        "agent-skills-sync",
        ["python3", "tools/verify_agent_skills_sync.py"],
        "vendored skills match their source",
    ),
    (
        "detekt-baselines",
        ["python3", "tools/verify_detekt_baselines.py"],
        "no baseline suppresses a file that no longer exists",
    ),
    (
        "skill-spec",
        ["python3", "tools/verify_skill_spec.py"],
        "every SKILL.md is valid per the Agent Skills specification",
    ),
]


def verify(args: argparse.Namespace) -> int:
    """Runs every gate and reports each one, rather than stopping at the first failure.

    Stopping early hides how much is wrong, and the whole point of one command is to answer "is
    anything wrong" in a single run.
    """
    only = getattr(args, "only", None)
    selected = [g for g in GATES if only is None or g[0] == only]
    if not selected:
        fail(f"no gate named {only!r}. Known: {', '.join(name for name, _, _ in GATES)}")

    failures: list[str] = []
    for name, command, what in selected:
        # flush: the subprocess writes straight to the terminal, so an unflushed header
        # lands after the output it is meant to introduce.
        print(f"\n=== {name}: {what} ===", flush=True)
        result = subprocess.run(command, cwd=REPO_ROOT, check=False)
        if result.returncode != 0:
            failures.append(name)

    print()
    if failures:
        print(f"FAILED: {', '.join(failures)}")
        return 1
    print(f"OK: {len(selected)} gate(s) passed")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="awake", description=__doc__)
    commands = parser.add_subparsers(dest="area", required=True)
    verify_cmd = commands.add_parser("verify", help="Run every gate -- the answer to \"is anything wrong\"")
    verify_cmd.add_argument("--only", help="run a single gate by name")
    verify_cmd.set_defaults(handler=verify)

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

    inspect = ui_commands.add_parser("inspect", help="Regenerate current Compose parity evidence and a review contact sheet")
    inspect.add_argument("--component", required=True)
    inspect.add_argument("--state", default="rest", help="fixture state, comma-separated")
    inspect.add_argument("--theme", default="light", choices=("light", "dark", "both"))
    inspect.add_argument("--skip-reference-build", action="store_true")
    inspect.set_defaults(handler=inspect_component)

    audit = ui_commands.add_parser(
        "audit",
        help="Regenerate and validate every component/state/theme registered in the parity manifest",
    )
    audit.add_argument("--theme", default="both", choices=("light", "dark", "both"))
    audit.add_argument("--skip-reference-build", action="store_true")
    audit.set_defaults(handler=audit_all_components)

    performance = ui_commands.add_parser("performance", help="Measure the parity-tool comparison path")
    performance.add_argument("--component", required=True)
    performance.add_argument("--theme", default="light", choices=("light", "dark", "both"))
    performance.set_defaults(handler=performance_report)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    return int(args.handler(args))


if __name__ == "__main__":
    raise SystemExit(main())
