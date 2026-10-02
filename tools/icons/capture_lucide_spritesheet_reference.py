#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
#
# SPDX-License-Identifier: Apache-2.0

"""Render every pinned Lucide SVG in Chromium at the icon sheet's 16/32 px positions."""

from __future__ import annotations

import argparse
import html
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ICONS = ROOT / "awake/ui/shadcn/src/commonMain/svg/lucide/icons"
WIDTH = 320
HEADER_HEIGHT = 48
ROW_HEIGHT = 48
BOTTOM_PADDING = 16


def name_from_path(path: Path) -> str:
    first, *rest = path.stem.split("-")
    return first + "".join(part.capitalize() for part in rest)


def sheet_html(paths: list[Path]) -> str:
    rows = []
    for path in paths:
        svg = path.read_text(encoding="utf-8")
        label = html.escape(name_from_path(path))
        rows.append(
            f'<div class="row"><span>{label}</span>'
            f'<div class="small">{svg}</div><div class="large">{svg}</div></div>'
        )
    return f"""<!doctype html><html><head><style>
        html, body {{ margin: 0; width: {WIDTH}px; background: #0a0a0a; color: white; }}
        body {{ font: 14px Arial, sans-serif; }}
        .header, .row {{ box-sizing: border-box; display: grid;
            grid-template-columns: 192px 48px 32px; align-items: center; padding-left: 16px; }}
        .header {{ height: {HEADER_HEIGHT}px; color: #aaa; }}
        .row {{ height: {ROW_HEIGHT}px; }}
        .small {{ width: 48px; display: flex; justify-content: center; }}
        .small svg {{ width: 16px; height: 16px; }}
        .large svg {{ display: block; width: 32px; height: 32px; }}
        svg {{ color: white; }}
    </style></head><body>
        <div class="header"><span>pinned SVG</span><span>16</span><span>32</span></div>
        {''.join(rows)}
    </body></html>"""


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True, help="PNG output path")
    args = parser.parse_args()
    paths = sorted(ICONS.glob("*.svg"), key=name_from_path)
    if not paths:
        raise RuntimeError(f"No pinned SVGs found in {ICONS}")

    from playwright.sync_api import sync_playwright

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(headless=True)
        page = browser.new_page(
            viewport={"width": WIDTH, "height": HEADER_HEIGHT + ROW_HEIGHT * len(paths) + BOTTOM_PADDING},
            device_scale_factor=1,
        )
        page.set_content(sheet_html(paths))
        args.out.parent.mkdir(parents=True, exist_ok=True)
        page.screenshot(path=str(args.out))
        browser.close()
    print(f"original SVG spritesheet: {args.out}")


if __name__ == "__main__":
    main()
