#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Builds the render-evidence PR comment from a base and a head capture.

    render_evidence.py prepare <before> <after> <out> <base-sha> <head-sha>
    render_evidence.py comment <out> <image-url-prefix> <head-sha> <owner/repo> <icons|all>

`prepare` compares every capture and every committed baseline PNG the PR touches, writes an
amplified diff image for each change under <out>/diff/, and records the numbers in
<out>/summary.json. `comment` turns that into markdown on stdout, or prints nothing when nothing
changed. Publish <out>/diff next to before/ and after/ so the comment's diff images resolve.
"""
from __future__ import annotations

import io
import json
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageChops

# A browser-rendered reference captured on the head only; it has no before to compare.
HEAD_ONLY = {"ui-original-svg-browser"}
# Captured by the shadcn tests rather than RenderEvidence.kt, so captions.tsv does not name them.
ICON_CAPTIONS = {
    "ui-lucide-icons-cpu": "Lucide icon sheet, CPU raster",
    "ui-shadcn-icons-cpu": "shadcn icon defaults, CPU raster",
    "ui-button-icons-cpu": "Icon buttons, CPU raster",
}
# At or below this, a change is the rasterizer rounding, not a visible difference.
ROUNDING = 2
WIDTH = 256


def compare(before: Image.Image, after: Image.Image, diff_path: Path) -> dict:
    """Changed-pixel share and largest channel change; writes the diff image when anything moved."""
    a, b = before.convert("RGB"), after.convert("RGB")
    r, g, bl = ImageChops.difference(a, b).split()
    delta = ImageChops.lighter(ImageChops.lighter(r, g), bl)
    per_pixel = delta.tobytes()
    changed = sum(1 for v in per_pixel if v)
    largest = max(per_pixel, default=0)
    if changed:
        # The head dimmed to grey for context, changed pixels in red scaled to the largest change,
        # so a one-level shift is visible and a large one still shows where it is strongest.
        context = b.convert("L").point(lambda v: v // 3).convert("RGB")
        red = Image.new("RGB", b.size, (255, 0, 0))
        diff_path.parent.mkdir(parents=True, exist_ok=True)
        Image.composite(red, context, delta.point(lambda v: v * 255 // largest)).save(diff_path)
    return {"changed": changed, "total": len(per_pixel), "max": largest}


def show(revision: str, path: str) -> Image.Image:
    return Image.open(io.BytesIO(subprocess.run(["git", "show", f"{revision}:{path}"], check=True, capture_output=True).stdout))


def prepare(before_dir: Path, after_dir: Path, out: Path, base_sha: str, head_sha: str) -> None:
    captions = dict(ICON_CAPTIONS)
    captions_file = after_dir / "captions.tsv"
    if captions_file.is_file():
        for line in captions_file.read_text().splitlines():
            name, _, caption = line.partition("\t")
            captions[name] = caption

    names = sorted({p.stem for d in (before_dir, after_dir) if d.is_dir() for p in d.glob("*.png")} - HEAD_ONLY)
    scenes = []
    for name in names:
        b, a = before_dir / f"{name}.png", after_dir / f"{name}.png"
        entry = {"name": name, "caption": captions.get(name, ""), "before": b.is_file(), "after": a.is_file()}
        if entry["before"] and entry["after"]:
            bi, ai = Image.open(b), Image.open(a)
            if bi.size != ai.size:
                entry["status"] = "resized"
            else:
                entry.update(compare(bi, ai, out / "diff" / f"{name}.png"))
                entry["status"] = "changed" if entry["changed"] else "same"
        else:
            entry["status"] = "added" if entry["after"] else "removed"
        scenes.append(entry)

    merge_base = git("merge-base", base_sha, head_sha)
    baselines = []
    listing = git("diff", "--name-status", "--no-renames", merge_base, head_sha, "--", "*/baselines/*.png", "*/baselines/**/*.png")
    for index, line in enumerate(filter(None, listing.splitlines())):
        status, path = line.split("\t", 1)
        entry = {"path": path, "status": {"A": "added", "D": "removed"}.get(status, "changed"), "id": index}
        if entry["status"] == "changed":
            bi, ai = show(merge_base, path), show(head_sha, path)
            if bi.size == ai.size:
                entry.update(compare(bi, ai, out / "diff" / "baselines" / f"{index}.png"))
            else:
                entry["status"] = "resized"
        baselines.append(entry)

    out.mkdir(parents=True, exist_ok=True)
    summary = {"merge_base": merge_base, "scenes": scenes, "baselines": baselines, "base_captured": any(before_dir.glob("*.png"))}
    (out / "summary.json").write_text(json.dumps(summary, indent=1))


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout.strip()


def img(url: str, width: int = WIDTH) -> str:
    return f'<img src="{url}" width="{width}">'


def stats(entry: dict) -> str:
    share = 100 * entry["changed"] / entry["total"]
    note = ", rounding-level" if entry["max"] <= ROUNDING else ""
    return f"{share:.1f}% of pixels, up to {entry['max']}/255{note}"


def comment(out: Path, prefix: str, sha: str, repo: str, focus: str) -> str:
    summary = json.loads((out / "summary.json").read_text())
    raw = f"https://raw.githubusercontent.com/{repo}"
    lines = ["<!-- render-evidence -->", "## Render evidence", ""]

    # A committed baseline is the PR's own claim about what it changes, so it leads.
    baselines = summary["baselines"]
    if baselines:
        lines += ["### Baselines this PR re-recorded", "", "| Baseline | Before | After | Diff |", "| --- | --- | --- | --- |"]
        for b in baselines:
            path, where = b["path"], f"`{b['path'].split('/src/')[0]}`<br>`{b['path'].split('/baselines/')[-1]}`"
            before = img(f"{raw}/{summary['merge_base']}/{path}") if b["status"] != "added" else "_new_"
            after = img(f"{raw}/{sha}/{path}") if b["status"] != "removed" else "_removed_"
            diff = img(f"{prefix}/diff/baselines/{b['id']}.png") + f"<br>{stats(b)}" if b.get("changed") else ""
            lines.append(f"| {where} | {before} | {after} | {diff} |")
        lines.append("")

    if focus == "icons":
        lines += icon_section(prefix, sha)
    else:
        lines += scene_section(summary, prefix, sha)

    if len(lines) <= 3:
        return ""
    return "\n".join(lines) + "\n"


def scene_section(summary: dict, prefix: str, sha: str) -> list[str]:
    scenes = summary["scenes"]
    moved = [s for s in scenes if s["status"] in ("changed", "added", "removed")]
    resized = [s for s in scenes if s["status"] == "resized"]
    same = [s for s in scenes if s["status"] == "same"]
    if not moved and not resized:
        return []
    out = [
        f"### Render scenes ({len(moved)} of {len(scenes)} changed)",
        "",
        f"Headless Vulkan (lavapipe) at `{sha[:9]}`. The diff shows the head in grey with changed pixels in red, "
        "scaled to the largest change, so even a one-level shift is visible.",
        "",
    ]
    if not summary["base_captured"]:
        out += ["_The base has no evidence capture, so every scene is new._", ""]
    for s in moved:
        out.append(f"**`{s['name']}`**: {s['caption'] or 'no caption'}" + (f" ({stats(s)})" if s.get("changed") else f" ({s['status']})"))
        out += ["", "| Before | After | Diff |", "| --- | --- | --- |"]
        before = img(f"{prefix}/before/{s['name']}.png") if s["before"] else "_none_"
        after = img(f"{prefix}/after/{s['name']}.png") if s["after"] else "_none_"
        diff = img(f"{prefix}/diff/{s['name']}.png") if s.get("changed") else ""
        out += [f"| {before} | {after} | {diff} |", ""]
    if resized:
        names = ", ".join(f"`{s['name']}`" for s in resized)
        out += [f"Not comparable, rendered at a different size on the base: {names}.", ""]
    if same:
        out += [f"<details><summary>{len(same)} unchanged</summary>", "", ", ".join(f"`{s['name']}`" for s in same), "", "</details>", ""]
    out.append("Regenerate with `./gradlew :awake:engine:render:parity:captureRenderEvidence -Pevidence.dir=<dir>`.")
    return out


def icon_section(prefix: str, sha: str) -> list[str]:
    return [
        "### Icons",
        "",
        f"The pinned SVGs rendered by Chromium, and Awake's CPU raster of the same icons at 16 and 32 px, on the base and at `{sha[:9]}`.",
        "",
        "| Original SVG in Chromium | Lucide before | Lucide after |",
        "| --- | --- | --- |",
        f"| {img(f'{prefix}/after/ui-original-svg-browser.png', 320)} | {img(f'{prefix}/before/ui-lucide-icons-cpu.png', 320)} | {img(f'{prefix}/after/ui-lucide-icons-cpu.png', 320)} |",
        "",
        "| shadcn before | shadcn after | Icon buttons before | Icon buttons after |",
        "| --- | --- | --- | --- |",
        f"| {img(f'{prefix}/before/ui-shadcn-icons-cpu.png', 220)} | {img(f'{prefix}/after/ui-shadcn-icons-cpu.png', 220)} "
        f"| {img(f'{prefix}/before/ui-button-icons-cpu.png', 220)} | {img(f'{prefix}/after/ui-button-icons-cpu.png', 220)} |",
        "",
        "These are CPU raster previews; native Vulkan and WebGPU icon pixels are not captured. Regenerate with "
        "`./gradlew :awake:ui:shadcn:desktopTest --tests '*ShadcnComposeRenderPreview.buttonIconsRenderThePinnedLucideGlyphs' "
        "--tests '*LucideShadcnSpritesheetPreviewTest*'`.",
    ]


def main(argv: list[str]) -> int:
    if len(argv) == 6 and argv[0] == "prepare":
        prepare(Path(argv[1]), Path(argv[2]), Path(argv[3]), argv[4], argv[5])
        return 0
    if len(argv) == 6 and argv[0] == "comment":
        sys.stdout.write(comment(Path(argv[1]), argv[2], argv[3], argv[4], argv[5]))
        return 0
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
