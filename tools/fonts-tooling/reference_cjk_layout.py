#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
# SPDX-License-Identifier: Apache-2.0
# GENERATOR: pinned Chromium CJK text-layout measurements; see layout/README.md.
"""Generate deliberate Chromium CJK line-break goldens, or verify their inputs without a browser."""
from __future__ import annotations

import argparse
import base64
import hashlib
import html
import importlib.metadata
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
SPEC = ROOT / "tools/fonts-tooling/layout"
OUTPUT = ROOT / "awake/compose/foundation/src/desktopTest/resources/text-layout/cjk-chromium.json"

MEASURE = r"""async ({spec, family}) => {
    await document.fonts.ready;
    const ratio = spec.density;
    const box = document.createElement('div');
    box.lang = spec.lang;
    Object.assign(box.style, {
        position:'absolute', left:'0', top:'0', margin:'0', padding:'0', border:'0',
        fontFamily:family, fontSize:`${spec.sizePx / ratio}px`, fontWeight:'400',
        fontKerning:'none', fontVariantLigatures:'none',
        letterSpacing:'0', whiteSpace:'pre-wrap', overflowWrap:'break-word',
        wordBreak:spec.wordBreak, lineBreak:spec.lineBreak,
        width:`${spec.wrapWidthPx / ratio}px`,
    });
    const text = document.createTextNode(spec.text);
    box.append(text); document.body.append(box);
    if (!document.fonts.check(`${spec.sizePx / ratio}px ${family}`, spec.text)) {
        throw Error('Font unavailable for ' + spec.id);
    }
    const origin = box.getBoundingClientRect();
    const ranges = [];
    for (let i = 0; i < spec.text.length; i++) {
        if (spec.text[i] === '\n') continue;
        const range = document.createRange(); range.setStart(text, i); range.setEnd(text, i + 1);
        const rect = range.getBoundingClientRect();
        ranges.push({index:i, left:rect.left - origin.left, right:rect.right - origin.left, top:rect.top - origin.top});
    }
    const groups = [];
    for (const r of ranges) {
        let group = groups.find(g => Math.abs(g.top - r.top) < 0.01);
        if (!group) { group = {top:r.top, glyphs:[]}; groups.push(group); }
        group.glyphs.push(r);
    }
    groups.sort((a, b) => a.top - b.top);
    for (const g of groups) {
        while (g.glyphs.length && spec.text[g.glyphs.at(-1).index] === ' ') g.glyphs.pop();
    }
    if (groups.some(g => !g.glyphs.length)) throw Error('Empty reference line in ' + spec.id);
    const round = v => Math.round(v * 1000000) / 1000000 || 0;
    const lines = groups.map(g => ({
        start: g.glyphs[0].index,
        end: g.glyphs.at(-1).index + 1,
        text: spec.text.slice(g.glyphs[0].index, g.glyphs.at(-1).index + 1),
        advance: round((g.glyphs.at(-1).right - g.glyphs[0].left) * ratio),
    }));
    box.remove();
    return {
        advance: round(Math.max(...lines.map(l => l.advance))),
        lineCount: lines.length,
        lines,
    };
}"""


def sha(path: Path) -> str:
    raw = path.read_bytes()
    return hashlib.sha256(raw.replace(b"\r\n", b"\n") if path.suffix in {".json", ".py"} else raw).hexdigest()


def inputs() -> tuple[dict, dict, dict]:
    corpus = json.loads((SPEC / "cjk-corpus.json").read_text(encoding="utf-8"))
    pin = json.loads((SPEC / "toolchain.json").read_text(encoding="utf-8"))
    for font in corpus["fonts"]:
        if sha(ROOT / font["path"]) != font["sha256"]:
            raise ValueError(f"Font input changed: {font['path']}")
    provenance = {
        "corpusSha256": sha(SPEC / "cjk-corpus.json"),
        "toolchainSha256": sha(SPEC / "toolchain.json"),
        "generatorSha256": sha(Path(__file__)),
        "fonts": corpus["fonts"],
    }
    return corpus, pin, provenance


def verify_pinned_playwright(pin: dict) -> None:
    if importlib.metadata.version("playwright") != pin["playwright"]:
        raise ValueError("Install requirements-layout.txt: Playwright version differs")
    if sys.platform != pin["canonicalPlatform"]:
        raise ValueError(f"Canonical regeneration platform is {pin['canonicalPlatform']}, not {sys.platform}")
    import playwright
    browsers = json.loads((Path(playwright.__file__).parent / "driver/package/browsers.json").read_text(encoding="utf-8"))
    bundled = next(b for b in browsers["browsers"] if b["name"] == pin["variant"])
    if bundled["revision"] != pin["revision"] or bundled["browserVersion"] != pin["chromium"]:
        raise ValueError("Bundled Chromium revision differs from the pin")


def build_faces_css(corpus: dict) -> str:
    return "\n".join(
        f"@font-face{{font-family:Oracle{f['id']};src:url(data:font/ttf;base64,{base64.b64encode((ROOT / f['path']).read_bytes()).decode()});font-weight:400;}}"
        for f in corpus["fonts"]
    )


def render_capture_html(out_html: Path) -> None:
    corpus, pin, _ = inputs()
    verify_pinned_playwright(pin)
    if any(spec["density"] != 1 for spec in corpus["cases"]):
        raise ValueError("Direct headless-shell HTML capture supports density=1 cases only")
    faces = build_faces_css(corpus)
    page = f"""<!doctype html><html><head><meta charset="utf-8"><style>{faces}</style></head><body style="margin:0"><pre id="out"></pre><script>
const MEASURE = {MEASURE};
const CASES = {json.dumps(corpus['cases'])};
(async () => {{
  await Promise.all([...document.fonts].map(f => f.load()));
  await document.fonts.ready;
  const cases = [];
  for (const spec of CASES) {{
    const reference = await MEASURE({{spec, family: 'Oracle' + spec.font}});
    cases.push({{id: spec.id, reference}});
  }}
  document.getElementById('out').textContent = JSON.stringify({{userAgent: navigator.userAgent, cases}});
}})();
</script></body></html>"""
    out_html.parent.mkdir(parents=True, exist_ok=True)
    out_html.write_text(page, encoding="utf-8")


def capture_from_dom(dom_path: Path) -> dict:
    _, pin, provenance = inputs()
    verify_pinned_playwright(pin)
    dom = dom_path.read_text(encoding="utf-8")
    match = re.search(r'<pre id="out">(.*?)</pre>', dom, re.S)
    if not match or not match.group(1):
        raise ValueError("Captured DOM is missing #out payload")
    payload = json.loads(html.unescape(match.group(1)))
    if f"HeadlessChrome/{pin['chromium']}" not in payload["userAgent"]:
        raise ValueError(f"Browser user agent {payload['userAgent']!r} differs from pinned {pin['chromium']}")
    return {"schema": 1, "toolchain": pin, "provenance": provenance, "cases": payload["cases"]}


def capture() -> dict:
    corpus, pin, provenance = inputs()
    verify_pinned_playwright(pin)
    from playwright.sync_api import sync_playwright
    faces = build_faces_css(corpus)
    result = {"schema": 1, "toolchain": pin, "provenance": provenance, "cases": []}
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, args=["--disable-lcd-text", "--font-render-hinting=none"])
        if browser.version != pin["chromium"]:
            raise ValueError("Running browser differs from the pinned Chromium")
        for spec in corpus["cases"]:
            context = browser.new_context(viewport={"width": 2048, "height": 2048}, device_scale_factor=spec["density"], locale="en-US", timezone_id="UTC")
            page = context.new_page()
            page.route("**/*", lambda route: route.abort())
            page.set_content(f"<html><head><style>{faces}</style></head><body style='margin:0'></body></html>")
            page.evaluate("async () => { await Promise.all([...document.fonts].map(f=>f.load())); await document.fonts.ready; }")
            measured = page.evaluate(MEASURE, {"spec": spec, "family": "Oracle" + spec["font"]})
            result["cases"].append({"id": spec["id"], "reference": measured})
            context.close()
        browser.close()
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=OUTPUT)
    parser.add_argument("--check", action="store_true", help="recapture and require byte-identical committed measurements")
    parser.add_argument("--verify-inputs", action="store_true", help="verify committed provenance without installing a browser")
    parser.add_argument("--render-html", type=Path, help="render self-contained capture HTML for headless-shell --dump-dom")
    parser.add_argument("--from-dom", type=Path, help="assemble golden from headless-shell --dump-dom output")
    args = parser.parse_args()
    if args.verify_inputs:
        _, pin, provenance = inputs()
        golden = json.loads(args.out.read_text(encoding="utf-8"))
        if golden["toolchain"] != pin or golden["provenance"] != provenance:
            raise ValueError("CJK layout-reference inputs changed: deliberate regeneration is required")
        print("Pinned CJK layout-reference inputs and font hashes match")
        return
    if args.render_html:
        render_capture_html(args.render_html)
        return
    captured = capture_from_dom(args.from_dom) if args.from_dom else capture()
    data = (json.dumps(captured, sort_keys=True, indent=2, ensure_ascii=True) + "\n").encode()
    if args.check:
        if args.out.read_bytes().replace(b"\r\n", b"\n") != data:
            raise ValueError("Chromium CJK measurements differ: investigate before deliberate regeneration")
        print("Chromium CJK layout recapture is byte-identical")
    else:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_bytes(data)
        print(f"Captured {len(json.loads(data)['cases'])} CJK layout cases to {args.out.relative_to(ROOT) if args.out.is_relative_to(ROOT) else args.out}")


if __name__ == "__main__":
    main()
