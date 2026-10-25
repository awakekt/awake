#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
# SPDX-License-Identifier: Apache-2.0
# GENERATOR: pinned Chromium text-layout measurements; see layout/README.md.
"""Generate deliberate browser layout goldens, or verify their inputs without a browser."""
from __future__ import annotations

import argparse
import base64
import hashlib
import importlib.metadata
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
SPEC = ROOT / "tools/fonts-tooling/layout"
OUTPUT = ROOT / "awake/compose/foundation/src/desktopTest/resources/text-layout/chromium.json"

MEASURE = r"""async ({spec, family}) => {
    await document.fonts.ready;
    const ratio = spec.density;
    const box = document.createElement('div');
    Object.assign(box.style, {
        position:'absolute', left:'0', top:'0', margin:'0', padding:'0', border:'0',
        fontFamily:family, fontSize:`${spec.sizePx / ratio}px`, fontWeight:'400',
        fontKerning:spec.kerning, fontVariantLigatures:'none', fontVariantNumeric:spec.numeric,
        letterSpacing:'0', whiteSpace:'pre-wrap', overflowWrap:'normal', wordBreak:'normal',
        lineHeight:spec.lineHeightPx === null ? 'normal' : `${spec.lineHeightPx / ratio}px`,
        width:spec.wrapWidthPx === null ? '2000px' : `${spec.wrapWidthPx / ratio}px`,
    });
    const text = document.createTextNode(spec.text);
    box.append(text); document.body.append(box);
    if (!document.fonts.check(`${spec.sizePx / ratio}px ${family}`, spec.text)) throw Error('Font unavailable');
    const origin = box.getBoundingClientRect();
    const ranges = [];
    for (let i=0;i<spec.text.length;i++) {
        if (spec.text[i] === '\n') continue;
        const range = document.createRange(); range.setStart(text,i); range.setEnd(text,i+1);
        const rect = range.getBoundingClientRect();
        ranges.push({index:i, left:rect.left-origin.left, right:rect.right-origin.left, top:rect.top-origin.top});
    }
    const groups=[];
    for (const r of ranges) {
        let group=groups.find(g=>Math.abs(g.top-r.top)<0.01);
        if (!group) { group={top:r.top, glyphs:[]}; groups.push(group); }
        group.glyphs.push(r);
    }
    groups.sort((a,b)=>a.top-b.top);
    // Trailing soft-wrap spaces hang outside the line's advance and are not painted by Core.
    for (const g of groups) while (g.glyphs.length && spec.text[g.glyphs.at(-1).index] === ' ') g.glyphs.pop();
    if (groups.some(g=>!g.glyphs.length)) throw Error('Empty reference line is outside this corpus');
    const probe=document.createElement('span');
    Object.assign(probe.style,{display:'inline-block',width:'0',height:'0',fontSize:'0',lineHeight:'0',verticalAlign:'baseline'});
    box.append(probe);
    const after=box.getBoundingClientRect();
    if (Math.abs(after.height-origin.height)>0.001) throw Error('Baseline probe changed line boxes');
    for (const r of ranges) {
        const range=document.createRange(); range.setStart(text,r.index);range.setEnd(text,r.index+1);
        const measured=range.getBoundingClientRect();
        if (Math.abs(measured.left-origin.left-r.left)>0.001 || Math.abs(measured.top-origin.top-r.top)>0.001)
            throw Error('Baseline probe changed shaping or wrapping');
    }
    const lastBaseline=(probe.getBoundingClientRect().top-origin.top)*ratio;
    const lineHeight=origin.height*ratio/groups.length;
    const round=v=>Math.round(v*1000000)/1000000 || 0;
    const lines=groups.map((g,line)=>({
        start:g.glyphs[0].index, end:g.glyphs.at(-1).index+1,
        text:spec.text.slice(g.glyphs[0].index,g.glyphs.at(-1).index+1),
        advance:round((g.glyphs.at(-1).right-g.glyphs[0].left)*ratio),
        baseline:round(lastBaseline+(g.top-groups.at(-1).top)*ratio),
        top:round(line*lineHeight), height:round(lineHeight),
        glyphs:g.glyphs.map(r=>({index:r.index,x:round(r.left*ratio)})),
    }));
    box.remove();
    return {advance:round(Math.max(...lines.map(l=>l.advance))),lineCount:lines.length,lineHeight:round(lineHeight),lines};
}"""


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n") if path.suffix in {".json", ".py"} else path.read_bytes()).hexdigest()


def inputs() -> tuple[dict, dict, dict]:
    corpus = json.loads((SPEC / "corpus.json").read_text())
    pin = json.loads((SPEC / "toolchain.json").read_text())
    for font in corpus["fonts"]:
        if sha(ROOT / font["path"]) != font["sha256"]:
            raise ValueError(f"Font input changed: {font['path']}")
    provenance = {"corpusSha256": sha(SPEC / "corpus.json"), "toolchainSha256": sha(SPEC / "toolchain.json"),
                  "generatorSha256": sha(Path(__file__)), "fonts": corpus["fonts"]}
    return corpus, pin, provenance


def capture() -> dict:
    corpus, pin, provenance = inputs()
    if importlib.metadata.version("playwright") != pin["playwright"]:
        raise ValueError("Install requirements-layout.txt: Playwright version differs")
    if sys.platform != pin["canonicalPlatform"]:
        raise ValueError(f"Canonical regeneration platform is {pin['canonicalPlatform']}, not {sys.platform}")
    from playwright.sync_api import sync_playwright
    import playwright
    browsers = json.loads((Path(playwright.__file__).parent / "driver/package/browsers.json").read_text())
    bundled = next(b for b in browsers["browsers"] if b["name"] == pin["variant"])
    if bundled["revision"] != pin["revision"] or bundled["browserVersion"] != pin["chromium"]:
        raise ValueError("Bundled Chromium revision differs from the pin")
    faces = "\n".join(f"@font-face{{font-family:Oracle{f['id']};src:url(data:font/ttf;base64,{base64.b64encode((ROOT/f['path']).read_bytes()).decode()});font-weight:400;}}" for f in corpus["fonts"])
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
            control_spec = dict(spec, kerning="none", numeric="normal")
            control = page.evaluate(MEASURE, {"spec": control_spec, "family": "Oracle" + spec["font"]})
            case = {"id": spec["id"], "reference": measured, "unfeatured": control}
            if spec["wrapWidthPx"] is not None:
                # #1021 tracks Core's legacy half-device-pixel fit allowance separately.
                rounded_spec = dict(control_spec, wrapWidthPx=spec["wrapWidthPx"] + 0.5)
                case["roundedFit"] = page.evaluate(MEASURE, {"spec": rounded_spec, "family": "Oracle" + spec["font"]})
            result["cases"].append(case)
            context.close()
        browser.close()
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=OUTPUT)
    parser.add_argument("--check", action="store_true", help="recapture and require byte-identical committed measurements")
    parser.add_argument("--verify-inputs", action="store_true", help="verify committed provenance without installing a browser")
    args = parser.parse_args()
    if args.verify_inputs:
        _, pin, provenance = inputs()
        golden = json.loads(args.out.read_text())
        if golden["toolchain"] != pin or golden["provenance"] != provenance:
            raise ValueError("Layout-reference inputs changed: deliberate regeneration is required")
        print("Pinned layout-reference inputs and font hashes match")
        return
    data = (json.dumps(capture(), sort_keys=True, indent=2, ensure_ascii=True) + "\n").encode()
    if args.check:
        if args.out.read_bytes().replace(b"\r\n", b"\n") != data:
            raise ValueError("Chromium measurements differ: investigate before deliberate regeneration")
        print("Chromium layout recapture is byte-identical")
    else:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_bytes(data)
        print(f"Captured {len(json.loads(data)['cases'])} layout cases to {args.out.relative_to(ROOT) if args.out.is_relative_to(ROOT) else args.out}")


if __name__ == "__main__":
    main()
