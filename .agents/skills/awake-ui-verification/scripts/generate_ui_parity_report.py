#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
#
# INVESTIGATION: summarises manifest-backed evidence.
# Kinds are defined in docs/tasks/2026-08-23-ui-tooling-formalization-plan.md. Only a GATE can fail
# a build; `scripts/awake verify` runs every one.
"""Generate a manifest-backed shadcn parity report.

This is the canonical report for the new parity tool. It reports independent geometry, padding,
spacing, border/radius, and image-comparison evidence, and deliberately calls missing facts
unmeasured instead of folding them into a misleading parity percentage.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[4]
DEFAULT_MANIFEST = ROOT / "tools/shadcn/shadcn_parity_manifest.json"
DEFAULT_REPORT = ROOT / "build/reports/ui-parity/report.json"

# A score is only meaningful inside a clearly declared measurement scope. Raster output cannot be
# a structural score because browser and Awake differ in glyph rasterization, shadow blur, and
# colour-management details. Those facts remain visible in their own diagnostics instead of being
# smuggled into a blended "parity percentage".
SCOPES = {
    "structural": ("geometry", "padding", "spacing", "relationships"),
    "surface": ("style",),
}


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text())


def resolve(value: str) -> Path:
    return ROOT / value


def semantic_nodes(path: Path) -> dict[str, dict[str, Any]]:
    return {node["id"]: node for node in load_json(path).get("semantics", []) if node.get("id")}


def rect(node: dict[str, Any], key: str = "bounds") -> dict[str, float] | None:
    value = node.get(key)
    if not isinstance(value, dict):
        return None
    width, height = value.get("width", value.get("w")), value.get("height", value.get("h"))
    if any(v is None for v in (value.get("x"), value.get("y"), width, height)):
        return None
    return {"x": float(value["x"]), "y": float(value["y"]), "width": float(width), "height": float(height)}


def artifacts(case: dict[str, Any]) -> tuple[dict[str, dict[str, Any]], dict[str, dict[str, Any]]] | None:
    awake_path, reference_path = resolve(case["semanticJson"]), resolve(case["referenceJson"])
    if not awake_path.exists() or not reference_path.exists():
        return None
    return semantic_nodes(awake_path), load_json(reference_path).get("nodes", {})


def geometry(case: dict[str, Any]) -> dict[str, Any]:
    required = ("semanticJson", "referenceJson", "nodeIds")
    if any(not case.get(key) for key in required):
        return {"status": "unmeasured", "reason": "case lacks semantic/geometry mapping"}
    source = artifacts(case)
    if source is None:
        return {"status": "missing-artifact", "reason": "generate reference and Awake previews first"}
    awake, reference = source
    origin_id = case.get("coordinateOrigin")
    actual_origin = rect(awake.get(origin_id, {})) if origin_id else None
    expected_origin = rect({"bounds": reference.get(origin_id, {})}) if origin_id else None
    rows = []
    for node_id in case["nodeIds"]:
        actual, expected = awake.get(node_id), reference.get(node_id)
        if actual is None or expected is None:
            return {"status": "failed", "reason": f"node mapping missing for {node_id}"}
        actual_bounds = rect(actual)
        if actual_bounds is None:
            return {"status": "failed", "reason": f"invalid Awake bounds for {node_id}"}
        expected_values = dict(expected)
        actual_values = dict(actual_bounds)
        if actual_origin and expected_origin:
            actual_values["x"] -= actual_origin["x"]
            actual_values["y"] -= actual_origin["y"]
            expected_values["x"] -= expected_origin["x"]
            expected_values["y"] -= expected_origin["y"]
        measurements = {
            key: {
                "expected": round(float(expected_values[key]), 3),
                "actual": round(float(actual_values[key]), 3),
                "delta": round(float(actual_values[key]) - float(expected_values[key]), 3),
            }
            for key in ("x", "y", "width", "height")
            if actual_values.get(key) is not None and key in expected_values
        }
        rows.append({"id": node_id, "measurements": measurements})
    max_delta = max(
        (abs(measurement["delta"]) for row in rows for measurement in row["measurements"].values()),
        default=0.0,
    )
    return {"status": "pass" if max_delta <= 1.0 else "drift", "maxDeltaPx": max_delta, "nodes": rows}


def padding(case: dict[str, Any]) -> dict[str, Any]:
    source = artifacts(case)
    if source is None:
        return {"status": "missing-artifact", "reason": "generate reference and Awake previews first"}
    awake, reference = source
    rows = []
    for node_id in case["nodeIds"]:
        parent, expected = awake.get(node_id), reference.get(node_id)
        # Awake's Style content padding is the distance from the border box to its content. CSS
        # reports padding separately from its border, so normalize the reference to the same
        # effective inset before comparing. This makes a `border p-1` surface 5px on both sides.
        border = float(expected.get("borderWidth", 0))
        expected_values = {
            "left": float(expected.get("paddingLeft", 0)) + border,
            "top": float(expected.get("paddingTop", 0)) + border,
            "right": float(expected.get("paddingRight", 0)) + border,
            "bottom": float(expected.get("paddingBottom", 0)) + border,
        }
        direct = parent.get("contentPadding") if parent else None
        if isinstance(direct, dict) and all(key in direct for key in ("start", "top", "end", "bottom")):
            actual_values = {
                "left": float(direct["start"]),
                "top": float(direct["top"]),
                "right": float(direct["end"]),
                "bottom": float(direct["bottom"]),
            }
            properties = {
                key: {
                    "expected": expected_values[key],
                    "actual": actual_values[key],
                    "delta": round(actual_values[key] - expected_values[key], 3),
                }
                for key in ("left", "top", "right", "bottom")
            }
            max_delta = max(abs(value["delta"]) for value in properties.values())
            rows.append({
                "id": node_id,
                "status": "pass" if max_delta <= 1 else "drift",
                "measurementSource": "effective-content-inset",
                "referenceCssBorderWidth": border,
                **properties,
            })
            continue
        # Design-system text labels conventionally receive the parent id plus `.label`.
        child = awake.get(f"{node_id}.label")
        parent_bounds, child_bounds = (rect(parent) if parent else None), (rect(child) if child else None)
        if parent_bounds is None or child_bounds is None:
            rows.append({"id": node_id, "status": "unmeasured", "reason": "no semantic content-slot child"})
            continue
        actual_left = round(child_bounds["x"] - parent_bounds["x"], 3)
        actual_right = round(parent_bounds["x"] + parent_bounds["width"] - child_bounds["x"] - child_bounds["width"], 3)
        expected_left, expected_right = expected_values["left"], expected_values["right"]
        rows.append({
            "id": node_id, "status": "pass" if max(abs(actual_left - expected_left), abs(actual_right - expected_right)) <= 1 else "drift",
            "measurementSource": "content-bounds-inference",
            "left": {"expected": expected_left, "actual": actual_left, "delta": round(actual_left - expected_left, 3)},
            "right": {"expected": expected_right, "actual": actual_right, "delta": round(actual_right - expected_right, 3)},
            "vertical": {"status": "unmeasured", "reason": "text semantic bounds fill the vertical content slot"},
        })
    measured = [r for r in rows if r["status"] != "unmeasured"]
    return {"status": "unmeasured" if not measured else ("drift" if any(r["status"] == "drift" for r in measured) else "pass"), "nodes": rows}


def spacing(case: dict[str, Any]) -> dict[str, Any]:
    source = artifacts(case)
    if source is None:
        return {"status": "missing-artifact", "reason": "generate reference and Awake previews first"}
    awake, reference = source
    valid_ids = [node_id for node_id in case["nodeIds"] if node_id in reference and node_id in awake]
    if len(valid_ids) < 2:
        return {"status": "unmeasured", "reason": "not enough mapped nodes present"}
    ids = sorted(valid_ids, key=lambda item: float(reference[item]["x"]))
    rows = []
    for left_id, right_id in zip(ids, ids[1:]):
        actual_left, actual_right = rect(awake[left_id]), rect(awake[right_id])
        expected_left, expected_right = reference[left_id], reference[right_id]
        if actual_left is None or actual_right is None:
            return {"status": "failed", "reason": "invalid semantic bounds"}
        actual_gap = actual_right["x"] - (actual_left["x"] + actual_left["width"])
        expected_gap = float(expected_right["x"]) - (float(expected_left["x"]) + float(expected_left["width"]))
        rows.append({"between": [left_id, right_id], "expected": round(expected_gap, 3), "actual": round(actual_gap, 3), "delta": round(actual_gap - expected_gap, 3)})
    return {"status": "pass" if all(abs(row["delta"]) <= 1 for row in rows) else "drift", "gaps": rows}


def relationships(case: dict[str, Any]) -> dict[str, Any]:
    """Compare manifest-declared nested offsets and sibling gaps without naming a widget type."""
    source = artifacts(case)
    specs = case.get("relationships", [])
    if not specs:
        return {"status": "not-required"}
    if source is None:
        return {"status": "missing-artifact", "reason": "generate reference and Awake previews first"}
    awake, reference = source
    rows = []
    for spec in specs:
        kind, first_id, second_id = spec["kind"], spec["first"], spec["second"]
        actual_first, actual_second = rect(awake.get(first_id, {})), rect(awake.get(second_id, {}))
        expected_first, expected_second = reference.get(first_id), reference.get(second_id)
        if not actual_first or not actual_second or not expected_first or not expected_second:
            rows.append({"name": spec.get("name", f"{first_id}:{second_id}"), "status": "unmeasured", "reason": "mapped nodes missing"})
            continue
        if kind == "vertical-gap":
            actual = actual_second["y"] - (actual_first["y"] + actual_first["height"])
            expected = float(expected_second["y"]) - (float(expected_first["y"]) + float(expected_first["height"]))
        elif kind == "horizontal-gap":
            actual = actual_second["x"] - (actual_first["x"] + actual_first["width"])
            expected = float(expected_second["x"]) - (float(expected_first["x"]) + float(expected_first["width"]))
        elif kind == "vertical-offset":
            actual = actual_second["y"] - actual_first["y"]
            expected = float(expected_second["y"]) - float(expected_first["y"])
        elif kind == "horizontal-offset":
            actual = actual_second["x"] - actual_first["x"]
            expected = float(expected_second["x"]) - float(expected_first["x"])
        else:
            rows.append({"name": spec.get("name", f"{first_id}:{second_id}"), "status": "unmeasured", "reason": f"unsupported relationship kind {kind}"})
            continue
        delta = round(actual - expected, 3)
        rows.append({"name": spec.get("name", f"{first_id}:{second_id}"), "kind": kind, "status": "pass" if abs(delta) <= 1 else "drift", "expected": round(expected, 3), "actual": round(actual, 3), "delta": delta})
    states = [row["status"] for row in rows]
    return {"status": "drift" if "drift" in states else ("partial" if "unmeasured" in states else "pass"), "relationships": rows}


def layout_intent(case: dict[str, Any]) -> dict[str, Any]:
    """Expose source layout declarations; Awake intent remains unmeasured until semantic export exists."""
    source = artifacts(case)
    if source is None:
        return {"status": "missing-artifact", "reason": "generate reference and Awake previews first"}
    awake, reference = source
    rows = []
    for node_id in case["nodeIds"]:
        node = reference.get(node_id, {})
        classes = set(str(node.get("className", "")).split())
        def axis(name: str, fill: str) -> str:
            if fill in classes:
                return "fill-parent"
            if any(value.startswith(f"{name}-[") or value.removeprefix(f"{name}-").replace(".5", "").isdigit() for value in classes):
                return "fixed"
            return "intrinsic" if node.get("display") in {"inline-flex", "inline-block"} else "unmeasured"
        source_intent = {
            "width": axis("w", "w-full"), "height": axis("h", "h-full"),
            "horizontalContentAlignment": node.get("justifyContent"),
            "verticalContentAlignment": node.get("alignItems"),
            "minWidth": node.get("minWidth"), "maxWidth": node.get("maxWidth"),
        }
        actual = awake.get(node_id, {})
        awake_width, awake_height = actual.get("widthStrategy"), actual.get("heightStrategy")
        width_status = "unmeasured" if source_intent["width"] == "unmeasured" else ("pass" if source_intent["width"] == awake_width else "drift")
        height_status = "unmeasured" if source_intent["height"] == "unmeasured" else ("pass" if source_intent["height"] == awake_height else "drift")
        rows.append({
            "id": node_id,
            "source": source_intent,
            "awake": {
                "width": awake_width,
                "height": awake_height,
                "widthStatus": width_status if awake_width is not None else "unmeasured",
                "heightStatus": height_status if awake_height is not None else "unmeasured",
            },
        })
    states = [status for row in rows for status in (row["awake"]["widthStatus"], row["awake"]["heightStatus"])]
    return {"status": "drift" if "drift" in states else ("partial" if "unmeasured" in states else "pass"), "nodes": rows}


def radius_value(value: Any, bounds: dict[str, Any]) -> float | str:
    """Normalize CSS `rounded-full` and Awake's full-radius sentinel to one semantic value."""
    radius = float(value)
    smallest_side = min(float(bounds.get("width", bounds.get("w", 0))), float(bounds.get("height", bounds.get("h", 0))))
    return "full" if smallest_side > 0 and radius >= smallest_side / 2 else radius


def style(case: dict[str, Any]) -> dict[str, Any]:
    source = artifacts(case)
    if source is None:
        return {"status": "missing-artifact", "reason": "generate reference and Awake previews first"}
    awake, reference = source
    rows = []
    for node_id in case["nodeIds"]:
        actual, expected = awake.get(node_id), reference.get(node_id)
        if actual is None or expected is None:
            rows.append({"id": node_id, "properties": {key: {"status": "unmeasured", "reason": "node mapping missing"} for key in ("borderWidth", "borderRadius", "borderColor")}})
            continue
        properties = {}
        for key in ("borderWidth", "borderRadius"):
            if key not in actual:
                properties[key] = {"status": "unmeasured", "expected": expected.get(key), "reason": "Awake preview did not export this property"}
                continue
            if key == "borderRadius":
                expected_value = radius_value(expected.get(key, 0), expected)
                actual_value = radius_value(actual[key], actual.get("bounds", {}))
                if expected_value == actual_value == "full":
                    properties[key] = {"status": "pass", "expected": "full", "actual": "full", "delta": 0}
                    continue
            else:
                expected_value, actual_value = float(expected.get(key, 0)), float(actual[key])
            delta = round(float(actual_value) - float(expected_value), 3)
            properties[key] = {"status": "pass" if abs(delta) <= 1 else "drift", "expected": expected_value, "actual": actual_value, "delta": delta}
        properties["borderColor"] = {"status": "captured-token" if actual.get("borderToken") else "unmeasured", "expected": expected.get("borderColor"), "actualToken": actual.get("borderToken")}
        rows.append({"id": node_id, "properties": properties})
    # Border geometry is a deterministic surface concern. Colour tokens are recorded separately:
    # a missing normalized colour export must not conceal a verified border width/radius result.
    states = [
        row["properties"][key]["status"]
        for row in rows
        for key in ("borderWidth", "borderRadius")
    ]
    return {"status": "drift" if "drift" in states else ("partial" if "unmeasured" in states else "pass"), "nodes": rows}


def paint(case: dict[str, Any]) -> dict[str, Any]:
    path = ROOT / "build/reports/ui-component-parity" / f"{case.get('comparisonName', case['id'])}.json"
    if not path.exists():
        return {"status": "unmeasured", "reason": "run awake ui validate first"}
    data = load_json(path)
    return {
        "status": data.get("status", "unmeasured").lower(),
        "mismatchPct": data.get("mismatch_pct"),
        "coverage": data.get("compared_area_ratio"),
        "diffImage": data.get("diff_image"),
    }


def scoped_score(case: dict[str, Any], dimensions: tuple[str, ...]) -> dict[str, Any]:
    """Score only fully declared, deterministic facts for one parity scope.

    `coveragePct` answers whether every required fact has an oracle. `scorePct` is the share of
    checked facts that pass. A scope can claim 100% only when both are 100: passing one available
    check while another is unmeasured is incomplete evidence, not parity.
    """
    required = [name for name in dimensions if case[name]["status"] != "not-required"]
    checked = [name for name in required if case[name]["status"] in {"pass", "drift", "failed"}]
    passing = [name for name in checked if case[name]["status"] == "pass"]
    missing = [name for name in required if name not in checked]
    score = round(100.0 * len(passing) / len(checked), 2) if checked else None
    coverage = round(100.0 * len(checked) / len(required), 2) if required else 100.0
    return {
        "status": "pass" if coverage == 100.0 and score == 100.0 else ("drift" if len(passing) != len(checked) else "partial"),
        "scorePct": score,
        "coveragePct": coverage,
        "passing": passing,
        "drifted": [name for name in checked if name not in passing],
        "unmeasured": missing,
    }


def scope_summary(cases: list[dict[str, Any]], scope: str) -> dict[str, Any]:
    scores = [case["scores"][scope] for case in cases]
    checked = sum(len(score["passing"]) + len(score["drifted"]) for score in scores)
    passing = sum(len(score["passing"]) for score in scores)
    required = checked + sum(len(score["unmeasured"]) for score in scores)
    return {
        "scorePct": round(100.0 * passing / checked, 2) if checked else None,
        "coveragePct": round(100.0 * checked / required, 2) if required else 100.0,
        "passingChecks": passing,
        "checkedChecks": checked,
        "requiredChecks": required,
    }


def report(manifest: dict[str, Any]) -> dict[str, Any]:
    cases = []
    for case in manifest["cases"]:
        artifacts = {key: resolve(case[key]).exists() for key in ("awakePng", "semanticJson", "referencePng", "referenceJson")}
        case_data = {
            "id": case["id"], "component": case["component"], "theme": case["theme"],
            "state": case["state"], "required": case["status"] == "required", "artifacts": artifacts,
            "geometry": geometry(case) if "geometry" in case["oracles"] else {"status": "not-required"},
            "padding": padding(case) if "padding" in case["oracles"] else {"status": "not-required"},
            "spacing": spacing(case) if "spacing" in case["oracles"] else {"status": "not-required"},
            "relationships": relationships(case),
            "layoutIntent": layout_intent(case),
            "paint": paint(case) if "paint" in case["oracles"] else {"status": "not-required"},
            "style": style(case) if "style" in case["oracles"] else {"status": "not-required"},
            "behavior": {"status": "unmeasured", "reason": "normalized interaction traces are not implemented"},
            "motion": {"status": "unmeasured", "reason": "multi-frame reference capture is not implemented"},
            "sizing": case["sizing"],
        }
        case_data["scores"] = {name: scoped_score(case_data, dimensions) for name, dimensions in SCOPES.items()}
        case_data["scopesOutsideScore"] = {
            "raster": case_data["paint"],
            "typography": {"status": "unmeasured", "reason": "cross-renderer text metrics and glyph rasterization are not normalized"},
            "shadows": {"status": "unmeasured", "reason": "cross-renderer shadow kernel and colour-space comparison are not normalized"},
            "colors": {"status": "unmeasured", "reason": "computed source colours are captured, but Awake token export is not normalized"},
        }
        cases.append(case_data)
    return {
        "reference": manifest["reference"],
        "summary": {name: scope_summary(cases, name) for name in SCOPES},
        "cases": cases,
    }


def score_cell(score: dict[str, Any]) -> str:
    value = "--" if score["scorePct"] is None else f"{score['scorePct']}%"
    return f"{score['status']} ({value}, coverage {score['coveragePct']}%)"


def markdown(data: dict[str, Any]) -> str:
    rows = ["# UI parity report", "", f"Reference: `{data['reference']['id']}`.", "",
            "| case | structural parity | surface parity | raster diagnostic | sizing |", "|---|---|---|---|---|"]
    for case in data["cases"]:
        p = case["paint"]
        paint_cell = p["status"] + (f" ({p['mismatchPct']}%)" if p.get("mismatchPct") is not None else "")
        rows.append(f"| `{case['id']}` | {score_cell(case['scores']['structural'])} | {score_cell(case['scores']['surface'])} | {paint_cell} | {case['sizing']} |")
    rows += ["", "A 100% scoped score means every required deterministic check passed and has full coverage. Raster, typography, shadows, and color quality are deliberately outside these scores until each has a normalized oracle; unmeasured is not a pass.", ""]
    return "\n".join(rows)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--out", type=Path, default=DEFAULT_REPORT)
    args = parser.parse_args()
    manifest = load_json(args.manifest)
    if manifest.get("schemaVersion") != 1 or not manifest.get("cases"):
        raise SystemExit("manifest must use schemaVersion 1 and contain at least one case")
    data = report(manifest)
    out = args.out if args.out.is_absolute() else ROOT / args.out
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(data, indent=2) + "\n")
    out.with_suffix(".md").write_text(markdown(data))
    print(f"wrote {out.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
