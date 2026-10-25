# Chromium text layout reference

`../reference_layout.py` captures **measurements**, independently of Awake, from the
exact Playwright/Chromium versions in `toolchain.json`. The checked-in reference is
`awake/compose/foundation/src/desktopTest/resources/text-layout/chromium.json`.
This is maintainer tooling: no browser or Python package is a runtime dependency.
The CPU conformance test consumes JSON and the existing licensed font files only.

## Deliberate regeneration

The canonical capture host is **Windows x64**, using the bundled Chromium headless
shell. Font metrics can differ between browser platforms, so the generator rejects
other platforms rather than silently replacing this oracle with another one. The
CPU comparison runs on any JVM host, including Linux CI. Use Python 3.11 or newer.
From the repository root in PowerShell:

```powershell
py -3 -m venv build/text-layout-venv
& build/text-layout-venv/Scripts/python.exe -m pip install -r tools/fonts-tooling/requirements-layout.txt
& build/text-layout-venv/Scripts/python.exe -m playwright install chromium --only-shell
& build/text-layout-venv/Scripts/python.exe tools/fonts-tooling/reference_layout.py
& build/text-layout-venv/Scripts/python.exe tools/fonts-tooling/reference_layout.py --check
```

The second capture must be byte-identical. `--out build/layout-candidate.json`
captures separately for inspection. Inspect every reference change before accepting
it; never regenerate merely to make a failing CPU test pass. `--check` does not
overwrite the reference. Byte comparison normalizes checkout CRLF to repository LF;
JSON inputs are also hashed with LF endings.

The generator verifies the installed package version, bundled browser revision,
running browser version, host platform and font SHA-256 hashes. It loads the exact
font bytes through data URLs, disables ligatures, fixes locale/timezone/DPR, blocks
network requests, and verifies that its zero-size baseline probe did not change
layout. No timestamp or machine-local path enters the output. The output records
the generator, corpus and toolchain hashes and font provenance.

Without installing Playwright or a browser, verify committed input provenance:

```powershell
py -3 tools/fonts-tooling/reference_layout.py --verify-inputs
```

CI performs this verification before the CPU test and uploads `metrics.csv` and
`summary.txt` from the module's `build/reports/text-layout-conformance/` directory.

## Corpus and units

`corpus.json` covers sizes 11, 12, 14 and 18 device pixels, the default bundled
Roboto Regular and a project-loaded Roboto Medium TTF, capital kerning pairs,
default/tabular/proportional digit runs, soft-wrapped paragraphs, hard breaks,
explicit tight/tall line heights and ten-line cases at densities 1.25 and 1.5.
Both fonts are existing repository assets under `Roboto-LICENSE.txt`; no fonts are
downloaded. The corpus records their source paths, hashes and raw TTF hhea metrics.

All output coordinates are **device pixels**, with the em matched between hosts.
For a density case the CSS font size is `sizePx / density`; this deliberately
isolates line placement after Awake's existing em quantization. Each line records
its source range, advance, logical glyph origins, baseline, top and line-box height.
Trailing soft-wrap separator spaces are excluded from the visible line advance.
Glyph origins are logical character positions from DOM Ranges, not ink bounds.

This corpus is ASCII Latin with ligatures disabled: one source character maps to
one glyph position. Empty lines, combining sequences, bidi, emoji, fallback fonts
and complex shaping are outside this oracle's accepted scope; shaping remains
owned by [#852](https://github.com/awakekt/awake/issues/852). Raster pixels are
covered separately by the existing FreeType reference suite (#993).

## CPU comparison and expected failures

```powershell
./gradlew :awake:compose:foundation:desktopTest --tests '*TextLayoutConformanceTest'
```

The test calls production `TextRun` layout and painting, records glyph quads on the
CPU, and recovers their origins/baselines from the face's bearing metrics. It paints
into a scope that is not device-pixel aligned, so every size draws from the distance
field and layout is measured independently of exact-size raster rounding, while the
fonts keep their production pixel-glyph limit, which the line box reads (#1024: a
size drawn from the field gets room for its antialiased fringe). Source ranges/wrap points and line count must match exactly;
advances, glyph origins, baselines and line-box coordinates allow 0.5 device pixels.
Every metric reports the actual value, reference, signed deviation and status.
`NaN` in a reference/deviation means that metric has no browser counterpart, for
example a separator painted inside Core's line but hanging after a browser wrap;
the exact source-range and line-count metrics report that structural difference.

The browser is always the first comparison. A passing metric is `PASS`, including
a previously failing metric fixed by later work. Other deviations can be `XFAIL`
only when they match one of these narrow pre-fix signatures:

- [#1022](https://github.com/awakekt/awake/issues/1022): a kerning-enabled case
  instead matches the independently captured browser control with kerning disabled
  (within 0.1 px, and exact wrap indices/count).
- [#1023](https://github.com/awakekt/awake/issues/1023): a requested numeric feature
  instead matches the independently captured browser control using normal digits
  (within 0.1 px). Already-tabular default digits may pass without that feature API.
- [#1021](https://github.com/awakekt/awake/issues/1021): the half-pixel wrap-fit
  follow-up on that tracker owns Core's existing `width < limit + 0.5` behavior.
  A separate browser control with only the wrap width enlarged by 0.5 device px
  must match the changed horizontal metrics (0.1 px) and exact source ranges/count.

One deviation is deliberate rather than a gap, and reports as `SNAP`. Core lays
every line out with the same whole-device-pixel advance (`TextLineBox`, #1024):
the browser's line height rounded to a whole pixel, with the first baseline on the
pixel grid. Chromium instead advances by a fractional line height, which the
density cases (17.5 px at 1.25) and the fractional tight and tall line heights
(8.8 and 17.6 px for 11 px text) produce, so its gaps alternate once snapped while Core's stay equal.
The difference grows by under half a pixel a line. A later line's top or baseline
is `SNAP` only when it equals the browser's own first baseline rounded to a whole
pixel (half down, as Core rounds a baseline) plus the line index times the browser's line height rounded to a whole
pixel, within 0.02 px; the first line, the line height and the line-box height are
held to the browser's 0.5 px.

These signatures are expected-failure classifiers, **not replacement goldens**.
Unrelated drift fails. The policy test includes negative probes proving that a
tracked issue cannot excuse arbitrary values. Feature implementation work should
wire the relevant style option into this test adapter, without changing Chromium
measurements or weakening tolerances.
