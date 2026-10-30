#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0
"""Before/after text-clipping sheets from two `captureTextClipEvidence` runs.

    compose_sheets.py sheets <before-dir> <after-dir> <out-dir>
    compose_sheets.py comment <out-dir> <image-url-prefix>

`sheets` writes, under <out-dir>: one `sheet-<group>.png` per group of cases, `overview.png` (the most
telling case of each group), `summary.json` and `table.md`. Every row is BEFORE | AFTER | reference at
a nearest-neighbour zoom of the crop that matters, with the ink the render lost in red (the reference
had at least 8/255 more ink there), the clip, layer or glyph-quad rect in green, and a caption with the
count. `comment` turns summary.json into the "Text clipping" section of the Render Evidence comment;
<image-url-prefix> is where <out-dir> is published, as `<prefix>/text/`.

Each side is measured against its own checkout's reference, so a base that clips the same text a
different way is still compared like for like. The counts come from `cases.tsv`, which the capture
wrote; the composer recounts from the PNGs and stops on a mismatch, so the pictures and the numbers
cannot drift apart.
"""
from __future__ import annotations

import json
import math
import sys
from dataclasses import dataclass
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

THRESHOLD = 8  # ink in the reference but not in the render, out of 255; Ink.THRESHOLD in the capture
PANEL_W, PANEL_H = 435, 380
LABEL_W, GAP = 250, 10
RED, GREEN, GREY = (214, 28, 28), (0, 150, 40), (150, 150, 150)
ZOOMS = (8, 6, 4, 3, 2, 1)
TEXT_CROP, EDGE_CROP = (52, 30), (54, 40)  # the smallest crop, in pixels, around lost ink or an edge pixel
OVERVIEW_WIDTH = 900
FOLD_ABOVE = 12  # a changed-case table longer than this goes in a <details>
MIN_ROW = 120  # room for a wrapped label
COLUMNS = ("BEFORE (base)", "AFTER (head)", "REFERENCE (no clip, no layer)")
KEYS = ("id", "group", "group_title", "title", "note", "metric", "count", "extra", "outline")

# Labels in the repo's own Roboto: Pillow's default face has no Å and draws case names as tofu.
LABEL_FONT = Path(__file__).resolve().parents[3] / "awake/ui/font-atlas-generator/fonts/Roboto-Regular.ttf"


def label_font(size: int) -> ImageFont.ImageFont:
    try:
        return ImageFont.truetype(str(LABEL_FONT), size)
    except OSError:
        return ImageFont.load_default(size=size)


F12, F14, F22 = (label_font(n) for n in (12, 14, 22))


class Case:
    """One case on one side: its row of cases.tsv plus the PNGs and geometry the capture wrote."""

    def __init__(self, directory: Path, row: dict):
        self.directory, self.row = directory, row
        self.id, self.metric = row["id"], row["metric"]
        self.count, self.extra = int(row["count"]), int(row["extra"])

    def image(self, kind: str) -> Image.Image | None:
        path = self.directory / f"{self.id}@{kind}.png"
        return Image.open(path).convert("RGB") if path.is_file() else None

    def geometry(self, kind: str) -> list[tuple]:
        document = json.loads((self.directory / f"{self.id}.json").read_text())
        return [tuple(r) for r in document.get(kind, [])]


def load(directory: Path) -> dict[str, Case]:
    table = directory / "cases.tsv"
    if not table.is_file():
        return {}
    rows = (dict(zip(KEYS, line.split("\t"))) for line in table.read_text(encoding="utf-8").splitlines() if line)
    return {row["id"]: Case(directory, row) for row in rows}


def ink(image: Image.Image) -> list[list[int]]:
    pixels = image.load()
    return [[255 - pixels[x, y][0] for x in range(image.width)] for y in range(image.height)]


def masks(reference: Image.Image, render: Image.Image) -> tuple[dict, dict]:
    """Pixels where the render has lost, or gained, at least THRESHOLD of ink against the reference."""
    lost, extra = {}, {}
    for y, (row_ref, row_render) in enumerate(zip(ink(reference), ink(render))):
        for x, (a, b) in enumerate(zip(row_ref, row_render)):
            if a - b >= THRESHOLD:
                lost[(x, y)] = a - b
            elif b - a >= THRESHOLD:
                extra[(x, y)] = b - a
    return lost, extra


def lost_mask(case: Case) -> dict:
    """The lost-ink pixels of a case with a reference, after checking they agree with its recorded count."""
    lost, extra = masks(case.image("ref"), case.image("render"))
    if (len(lost), len(extra)) != (case.count, case.extra):
        raise SystemExit(f"{case.directory}/{case.id}: cases.tsv says lost {case.count} and extra {case.extra}, "
                         f"the PNGs say {len(lost)} and {len(extra)}")
    return lost


def ring(quad: tuple) -> set[tuple[int, int]]:
    """The outermost pixels of a glyph quad, those whose centres are inside it."""
    x, y, w, h = quad
    x0, x1 = math.ceil(x - 0.5), math.ceil(x + w - 0.5) - 1
    y0, y1 = math.ceil(y - 0.5), math.ceil(y + h - 0.5) - 1
    return ({(xx, yy) for xx in range(x0, x1 + 1) for yy in (y0, y1)}
            | {(xx, yy) for yy in range(y0, y1 + 1) for xx in (x0, x1)})


def strongest_edge_pixel(case: Case) -> tuple[int, int] | None:
    """Where the ink is strongest on a quad's outer ring: the hard edge, if the fringe was cut there."""
    render = case.image("render")
    values = ink(render)
    cells = [(values[y][x], (x, y)) for q in case.geometry("quads") for x, y in ring(q)
             if 0 <= x < render.width and 0 <= y < render.height]
    return max(cells)[1] if cells else None


def crop_around(points: list[tuple[int, int]], size: tuple[int, int], want: tuple[int, int], margin: int = 5) -> tuple:
    """A crop rectangle (x0, y0, x1, y1), at least `want` pixels and inside `size`, centred on the points."""
    xs, ys = [p[0] for p in points], [p[1] for p in points]
    cw = min(max(max(xs) - min(xs) + 1 + 2 * margin, want[0]), size[0])
    ch = min(max(max(ys) - min(ys) + 1 + 2 * margin, want[1]), size[1])
    x0 = max(0, min((min(xs) + max(xs)) // 2 - cw // 2, size[0] - cw))
    y0 = max(0, min((min(ys) + max(ys)) // 2 - ch // 2, size[1] - ch))
    return x0, y0, x0 + cw, y0 + ch


def panel(image: Image.Image, crop: tuple, zoom: int, lost: dict | None = None, rects: list[tuple] = ()) -> Image.Image:
    """The crop of `image` at `zoom` (nearest neighbour), lost ink in red and the rects outlined in green."""
    x0, y0, x1, y1 = crop
    out = image.crop(crop)
    pixels = out.load()
    for (gx, gy), amount in (lost or {}).items():
        if x0 <= gx < x1 and y0 <= gy < y1:
            f = min(1.0, 0.45 + 0.55 * amount / 255)
            pixels[gx - x0, gy - y0] = tuple(int(255 * (1 - f) + c * f) for c in RED)
    big = out.resize(((x1 - x0) * zoom, (y1 - y0) * zoom), Image.NEAREST)
    draw = ImageDraw.Draw(big)
    for rx, ry, rw, rh in rects:
        draw.rectangle([(rx - x0) * zoom, (ry - y0) * zoom, (rx - x0 + rw) * zoom - 1, (ry - y0 + rh) * zoom - 1], outline=GREEN, width=2)
    return big


def placeholder(size: tuple[int, int]) -> Image.Image:
    image = Image.new("RGB", size, (225, 225, 225))
    ImageDraw.Draw(image).text((10, 10), "no capture on this side", font=F14, fill=(90, 90, 90))
    return image


@dataclass
class Drawn:
    panels: list[Image.Image]
    captions: list[str]
    crop: tuple
    zoom: int


class Row:
    """A case across the two sides."""

    def __init__(self, before: Case | None, after: Case | None):
        self.before, self.after = before, after
        self.case = after or before
        self.edge = self.case.metric == "edge"

    @property
    def sides(self) -> tuple[Case | None, Case | None]:
        return self.before, self.after

    @property
    def delta(self) -> int:
        return (self.before.count if self.before else 0) - (self.after.count if self.after else 0)

    @property
    def worst(self) -> int:
        return max(c.count for c in self.sides if c) if any(self.sides) else 0

    def draw(self) -> Drawn:
        existing = [c for c in self.sides if c]
        masks_ = [lost_mask(c) if c and not self.edge else {} for c in self.sides]
        if self.edge:
            points = [p for p in map(strongest_edge_pixel, existing) if p]
            size, want = existing[-1].image("render").size, EDGE_CROP
        else:
            size, want = existing[-1].image("ref").size, TEXT_CROP
            points = [p for mask in masks_ for p in mask]
        if not points:  # nothing lost on either side: frame the start of the text itself
            first = next(((x, y) for y, row in enumerate(ink(existing[-1].image("ref"))) for x, v in enumerate(row) if v >= THRESHOLD), (0, 0))
            points = [(first[0] + want[0] // 2 - 5, first[1] + want[1] // 2 - 5)]
        crop = crop_around(points, size, want)
        cw, ch = crop[2] - crop[0], crop[3] - crop[1]
        zoom = next((z for z in ZOOMS if cw * z <= PANEL_W and ch * z <= PANEL_H), 1)
        outline = self.case.row["outline"]
        panels = [panel(c.image("render"), crop, zoom, mask, c.geometry(outline)) if c else placeholder((cw * zoom, ch * zoom))
                  for c, mask in zip(self.sides, masks_)]
        captions = [f"{'edge ink' if self.edge else 'lost ink'} = {c.count} px" if c else "none" for c in self.sides]
        if not self.edge:
            panels.append(panel(existing[-1].image("ref"), crop, zoom))
            captions.append("reference")
        return Drawn(panels, captions, crop, zoom)


def label(d: ImageDraw.ImageDraw, x: int, y: int, lines: list[tuple[str, ImageFont.ImageFont, tuple]]) -> None:
    """Text lines wrapped into the label column, which is LABEL_W wide."""
    for text, f, colour in lines:
        words, current = text.split(), ""
        for word in words + [None]:
            trial = f"{current} {word}".strip() if word else current
            if word and d.textlength(trial, font=f) > LABEL_W - 16 and current:
                d.text((x, y), current, font=f, fill=colour)
                y, current = y + f.size + 4, word
            else:
                current = trial
        d.text((x, y), current, font=f, fill=colour)
        y += f.size + 6


def paste_row(sheet: Image.Image, y: int, row: Row, drawn: Drawn, headings: bool) -> None:
    """The panels of a row and their captions; red when the side lost ink, green when it kept it all."""
    d = ImageDraw.Draw(sheet)
    for i, (image, caption) in enumerate(zip(drawn.panels, drawn.captions)):
        x = LABEL_W + GAP + i * (PANEL_W + GAP)
        sheet.paste(image, (x, y + 4))
        d.rectangle([x - 1, y + 3, x + image.width, y + 4 + image.height], outline=GREY)
        case = row.sides[i] if i < 2 else None
        colour = (60, 60, 60) if case is None and i >= 2 else (180, 0, 0) if case and case.count else (0, 110, 0)
        text = f"{COLUMNS[i].split(' ')[0]}: {caption}" if headings and i < 2 else caption
        d.text((x, y + 4 + image.height + 6), text, font=F14, fill=colour)


def draw_sheet(title: str, subtitle: str, rows: list[Row], path: Path) -> None:
    drawn = [row.draw() for row in rows]
    columns = max(len(d.panels) for d in drawn)
    width = LABEL_W + columns * (PANEL_W + GAP) + GAP
    heights = [max(max(p.height for p in d.panels) + 44, MIN_ROW) for d in drawn]
    sheet = Image.new("RGB", (width, 124 + sum(heights) + 10), (236, 236, 236))
    d = ImageDraw.Draw(sheet)
    d.text((10, 8), title, font=F22, fill=(20, 20, 20))
    d.text((10, 40), subtitle, font=F12, fill=(60, 60, 60))
    d.rectangle([10, 62, 26, 78], fill=RED)
    d.text((32, 62), f"ink in the reference (>= {THRESHOLD}/255) but missing in the render", font=F12, fill=(40, 40, 40))
    d.rectangle([400, 62, 416, 78], outline=GREEN, width=2)
    d.text((422, 62), "the clip, layer or glyph quad the render used", font=F12, fill=(40, 40, 40))
    for i in range(columns):
        d.text((LABEL_W + GAP + i * (PANEL_W + GAP), 100), COLUMNS[i], font=F14, fill=(30, 30, 30))
    y = 124
    for row, image, height in zip(rows, drawn, heights):
        d.rectangle([0, y, width, y + height - 6], fill=(250, 250, 250))
        label(d, 8, y + 6, [(row.case.row["title"], F14, (0, 0, 0)), (row.case.row["note"], F12, (70, 70, 70)),
                            (f"zoom x{image.zoom}, crop {image.crop[2] - image.crop[0]}x{image.crop[3] - image.crop[1]} px", F12, (90, 90, 90))])
        paste_row(sheet, y, row, image, headings=False)
        y += height
    sheet.crop((0, 0, width, y + 10)).save(path)


def draw_overview(picks: list[tuple[str, Row]], path: Path) -> None:
    """One compact image: the most telling row of every group, stacked."""
    drawn = [row.draw() for _, row in picks]
    columns = max(len(d.panels) for d in drawn)
    width = LABEL_W + columns * (PANEL_W + GAP) + GAP
    heights = [max(max(p.height for p in d.panels) + 44, MIN_ROW) for d in drawn]
    sheet = Image.new("RGB", (width, 46 + sum(heights) + 10), (236, 236, 236))
    d = ImageDraw.Draw(sheet)
    d.text((10, 8), "Text clipping: the most telling case of each group (every case is in the sheets)", font=F22, fill=(20, 20, 20))
    y = 40
    for (title, row), image, height in zip(picks, drawn, heights):
        d.rectangle([0, y, width, y + height - 6], fill=(250, 250, 250))
        label(d, 8, y + 6, [(title, F14, (0, 0, 0)), (row.case.row["title"], F12, (70, 70, 70)), (row.case.row["note"], F12, (90, 90, 90))])
        paste_row(sheet, y, row, image, headings=True)
        y += height
    sheet.crop((0, 0, width, y + 10)).save(path)


def representative(rows: list[Row]) -> Row:
    """The case worth showing for a group: the one whose count moved most, else the one losing most."""
    if any(r.delta for r in rows):
        return max(rows, key=lambda r: abs(r.delta))
    return max(rows, key=lambda r: (r.after.count if r.after else 0, -rows.index(r)))


def one_per_size(rows: list[Row]) -> list[Row]:
    """The distance-field glyphs are 24 frames; the sheet shows the worst glyph of each size, the table all."""
    best: dict[str, Row] = {}
    for r in rows:
        size = r.case.id.rsplit("-", 1)[-1]
        if size not in best or r.worst > best[size].worst:
            best[size] = r
    return list(best.values())


def status(row: Row) -> str:
    if row.before is None:
        return "new"
    if row.after is None:
        return "removed"
    return "changed" if row.before.count != row.after.count else "same"


def sheets(before_dir: Path, after_dir: Path, out: Path) -> None:
    before, after = load(before_dir), load(after_dir)
    if not after:
        raise SystemExit(f"{after_dir}: no cases.tsv; run captureTextClipEvidence on the head first")
    out.mkdir(parents=True, exist_ok=True)
    rows = [Row(before.get(i), after.get(i)) for i in [*after, *(i for i in before if i not in after)]]
    groups: dict[str, list[Row]] = {}
    for r in rows:
        groups.setdefault(r.case.row["group"], []).append(r)
    subtitle = "CPU raster, black on white; each side counted against its own checkout's reference." + (
        "" if before else " The base has no text-clip capture, so BEFORE is empty.")
    written, picks = [], []
    for group, members in groups.items():
        shown = one_per_size(members) if group == "sdf" else members
        title, name = members[0].case.row["group_title"], f"sheet-{group}.png"
        draw_sheet(title, subtitle, shown, out / name)
        written.append({"group": group, "title": title, "file": name})
        picks.append((title, representative(shown)))
    draw_overview(picks, out / "overview.png")
    cases = [{"id": r.case.id, "group": r.case.row["group"], "title": r.case.row["title"], "metric": r.case.metric,
              "before": r.before.count if r.before else None, "after": r.after.count if r.after else None,
              "status": status(r)} for r in rows]
    (out / "summary.json").write_text(json.dumps({"base_captured": bool(before), "cases": cases, "sheets": written}, indent=1), encoding="utf-8")
    (out / "table.md").write_text("\n".join(table(cases)) + "\n", encoding="utf-8")
    print(f"wrote {len(written)} sheets, overview.png, summary.json and table.md to {out}")


def table(cases: list[dict]) -> list[str]:
    lines = ["| Case | Before | After | Change |", "| --- | --- | --- | --- |"]
    for c in cases:
        if c["status"] == "changed":
            delta = c["after"] - c["before"]
            what = "edge ink" if c["metric"] == "edge" else "lost ink"
            change = f"**{delta:+d}**, {'more' if delta > 0 else 'less'} {what}"
        else:
            change = {"same": "", "new": "new", "removed": "removed"}[c["status"]]
        mark = "" if c["status"] == "same" else "**"
        value = lambda v: "-" if v is None else str(v)  # noqa: E731
        title = c["title"].replace("|", "\\|")  # a pipe in a case title would split the table cell
        lines.append(f"| {mark}{title}{mark} (`{c['id']}`) | {value(c['before'])} | {value(c['after'])} | {change} |")
    return lines


def comment(out: Path, prefix: str) -> str:
    summary = json.loads((out / "summary.json").read_text(encoding="utf-8"))
    cases = summary["cases"]
    moved = [c for c in cases if c["status"] != "same"]
    losing = [c for c in cases if c["metric"] == "lost" and (c["after"] or 0) > 0]
    before, after = (sum(c[k] or 0 for c in cases) for k in ("before", "after"))
    url = lambda name: f"{prefix}/text/{name}"  # noqa: E731
    lines = [
        "### Text clipping", "",
        f"{len(cases)} cases drawn with and without their clips and layers on the CPU raster. Lost ink is a pixel whose "
        f"reference ink is at least {THRESHOLD}/255 higher than the render's; the edge-ink cases count ink on the outer ring "
        "of a glyph quad. Red marks lost ink, green the rect the render used.", "",
    ]
    if not summary["base_captured"]:
        lines += ["_The base has no text-clip capture, so every case is new and only the head is counted._", ""]
    elif moved:
        lines += [f"**{len(moved)} of {len(cases)} cases changed.** Counted pixels in total: {before} before, {after} after.", ""]
    else:
        lines += [f"No case changed. Counted pixels in total: {before} before, {after} after; "
                  f"{len(losing)} cases still lose ink on the head.", ""]
    if moved or losing:
        lines += [f'<img src="{url("overview.png")}" width="{OVERVIEW_WIDTH}">', ""]
    if len(moved) > FOLD_ABOVE:
        lines += [f"<details><summary>{len(moved)} cases changed</summary>", "", *table(moved), "", "</details>", ""]
    elif moved:
        lines += table(moved) + [""]
    sheets_ = ", ".join(f"[{s['title']}]({url(s['file'])})" for s in summary["sheets"])
    lines += ["<details><summary>All cases</summary>", "", *table(cases), "", f"Sheets: {sheets_}", "", "</details>", "",
              "Regenerate with `tools/fonts-tooling/text-evidence/run_local.sh <base-ref>`; its README has the steps."]
    return "\n".join(lines) + "\n"


def main(argv: list[str]) -> int:
    if len(argv) == 4 and argv[0] == "sheets":
        sheets(Path(argv[1]), Path(argv[2]), Path(argv[3]))
        return 0
    if len(argv) == 3 and argv[0] == "comment":
        sys.stdout.write(comment(Path(argv[1]), argv[2]))
        return 0
    print(__doc__, file=sys.stderr)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
