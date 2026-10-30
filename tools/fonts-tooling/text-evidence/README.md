# Text-clipping evidence

Before/after sheets, with lost-ink counts, for any change that can cut a glyph off: a clip, a layer, a
text box, a font. They began as the proof for #1053 (glyphs cut off by clips and layers sized to the
text box, fixed in #1069) and now come with every PR that touches text.

A **lost-ink pixel** is one whose ink in the unclipped reference is at least 8/255 higher than in the
render. A case is drawn black on white by the CPU raster the UI snapshot tests use, once as the engine
draws it and once with its clips and layers taken away, and the two are compared. The distance-field
cases have no reference; they count ink on the outer ring of the glyph quad, where a fringe that fades
out inside the quad has none.

## Run it

```bash
python3 -m pip install pillow
tools/fonts-tooling/text-evidence/run_local.sh origin/main        # base = origin/main, head = this tree
open build/text-evidence/sheets/overview.png                      # then sheet-*.png and table.md
```

`run_local.sh <base-ref> [out-dir]` captures the base in a throwaway worktree, captures this tree,
and composes `out-dir/sheets`. A base that predates the capture gets the capture laid over it, so
the #1053 sheets are:

```bash
tools/fonts-tooling/text-evidence/run_local.sh 2ce8755e5          # the commit before the fix
```

By hand, which is what the workflow does:

```bash
./gradlew :awake:engine:render:parity:captureTextClipEvidence -Pevidence.dir=/tmp/text-before   # on the base
./gradlew :awake:engine:render:parity:captureTextClipEvidence -Pevidence.dir=/tmp/text-after    # on the head
python3 -I tools/fonts-tooling/text-evidence/compose_sheets.py sheets /tmp/text-before /tmp/text-after /tmp/text-sheets
```

The capture needs no GPU and no display, only a JDK 25 toolchain and the repository's usual build
(it compiles the render parity module, which builds naga). It takes about 13 s on a warm Gradle
cache and about 70 s the first time a checkout compiles the parity tests.

## What it writes

| Output | What |
| --- | --- |
| `<dir>/cases.tsv` | One row per case: id, group, titles, metric (`lost` or `edge`), count, extra, outline. The numbers a PR comment quotes |
| `<dir>/<id>@render.png`, `@ref.png`, `<id>.json` | What the case draws, what it would draw unclipped, and the clip, layer and glyph-quad rects |
| `sheets/sheet-<group>.png` | Every case of a group: BEFORE, AFTER and the reference at a nearest-neighbour zoom of the crop around the lost ink; lost ink red, the rect the render used green |
| `sheets/overview.png` | The most telling case of each group, which the PR comment shows |
| `sheets/table.md`, `summary.json` | The count table, and the same as data |

The composer recounts every case from its PNGs and stops if `cases.tsv` disagrees, so a sheet and its
number cannot drift apart. Each side is counted against its own checkout's reference.

## In CI

`.github/workflows/render-evidence.yml` runs the capture on the head and the base when a changed
path matches `paths.py` (font data, `awake/core/text/`, text layout and `BasicTextField`,
`awake/compose/ui/`, the CPU rasterizer, the glyph shader, the layer compositor, the 2D scissor, the
canvas, shadcn inputs and the sidebar, any TTF or OTF). It publishes the sheets next to the other
evidence images and adds a "Text clipping" section to the one Render Evidence comment, with the cases
whose count changed in bold. `test_text_evidence.py` pins which paths match; when a text-related module
moves or appears, add its path to `paths.py` and a sample to the test.

## Adding a case

Add a `TextClipCase` to `TextClipCases.kt` under
`awake/engine/render/parity/src/desktopTest/kotlin/com/awakekt/awake/render/parity/textclip/`. Its id
is the key a base and a head are compared on, so keep it stable. Give it a reference (the same frame
with the clip or layer taken away) when it can have one, and an outline (`Clips`, `Layers` or `Quads`)
for the green rectangles. Every case should be one a clip or layer could cut: a case that cannot lose
ink is not evidence of anything.

## Negative control

Recorded for #1079: making `BasicTextField`'s text clip tight on every side again (`clipFieldText` in
`BasicTextField.kt`, margins 0f) takes the `field` group from 0 to 112 lost pixels across 7 of its 8
cases, e.g. `"jump" 96 sp, density 1.5` from 0 to 58; restoring it puts them back to 0.
`InkTest` covers the count itself.
