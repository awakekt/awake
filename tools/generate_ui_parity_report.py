#!/usr/bin/env python3
# Copyright (c) Ron June Valdoz
# SPDX-License-Identifier: Apache-2.0
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

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_MANIFEST = ROOT / "tools/shadcn_parity_manifest.json"
DEFAULT_REPORT = ROOT / "build/reports/ui-parity/report.json"


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
        expected_values = {
            "left": float(expected.get("paddingLeft", 0)),
            "top": float(expected.get("paddingTop", 0)),
            "right": float(expected.get("paddingRight", 0)),
            "bottom": float(expected.get("paddingBottom", 0)),
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
                "measurementSource": "resolved-style",
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
    ids = sorted(case["nodeIds"], key=lambda item: float(reference[item]["x"]))
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


def style(case: dict[str, Any]) -> dict[str, Any]:
    source = artifacts(case)
    if source is None:
        return {"status": "missing-artifact", "reason": "generate reference and Awake previews first"}
    awake, reference = source
    rows = []
    for node_id in case["nodeIds"]:
        actual, expected = awake[node_id], reference[node_id]
        properties = {}
        for key in ("borderWidth", "borderRadius"):
            if key not in actual:
                properties[key] = {"status": "unmeasured", "expected": expected.get(key), "reason": "Awake preview did not export this property"}
                continue
            delta = round(float(actual[key]) - float(expected.get(key, 0)), 3)
            properties[key] = {"status": "pass" if abs(delta) <= 1 else "drift", "expected": expected.get(key), "actual": actual[key], "delta": delta}
        properties["borderColor"] = {"status": "captured-token" if actual.get("borderToken") else "unmeasured", "expected": expected.get("borderColor"), "actualToken": actual.get("borderToken")}
        rows.append({"id": node_id, "properties": properties})
    states = [v["status"] for row in rows for v in row["properties"].values()]
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


def report(manifest: dict[str, Any]) -> dict[str, Any]:
    cases = []
    for case in manifest["cases"]:
        artifacts = {key: resolve(case[key]).exists() for key in ("awakePng", "semanticJson", "referencePng", "referenceJson")}
        cases.append({
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
        })
    return {"reference": manifest["reference"], "cases": cases}


def markdown(data: dict[str, Any]) -> str:
    rows = ["# UI parity report", "", f"Reference: `{data['reference']['id']}`.", "",
            "| case | geometry | padding | spacing | border/radius | paint | sizing |", "|---|---|---|---|---|---|---|"]
    for case in data["cases"]:
        g, pad, gap, style_data, p = case["geometry"], case["padding"], case["spacing"], case["style"], case["paint"]
        geometry_cell = g["status"] + (f" (max Δ {g['maxDeltaPx']}px)" if "maxDeltaPx" in g else "")
        paint_cell = p["status"] + (f" ({p['mismatchPct']}%)" if p.get("mismatchPct") is not None else "")
        rows.append(f"| `{case['id']}` | {geometry_cell} | {pad['status']} | {gap['status']} | {style_data['status']} | {paint_cell} | {case['sizing']} |")
    rows += ["", "Unmeasured is intentional evidence of a missing oracle, not a pass.", ""]
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
