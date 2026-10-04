# `awake:ui:font-atlas-generator`

Packs the UI font's glyphs into a multi-channel signed distance field atlas and writes it as Kotlin
source, one object per weight, into `awake:core:text` (`Roboto*UiFontData.kt`). The output is
committed: nothing runs this at build time, so a change to the atlas is a change to those files.

```
./gradlew :awake:ui:font-atlas-generator:generateFontAtlas
```

It shells out to [`msdfgen`](https://github.com/Chlumsky/msdfgen), which must be on `PATH`. Run it only
to add or change a glyph or a weight, then commit the regenerated files with the change that needs them.

## What depends on the machine, and what does not

**The metrics do not.** Ascent, descent, line height, the atlas cell size and every glyph's offsets and
advances come from the font file (its `head` and `hhea` tables and its outlines), so Windows, macOS and
Linux produce identical numbers. They used to come from `java.awt.Font.getLineMetrics`, which asks the
platform's font scaler: for the Roboto files that is `hhea` on Linux (ascender 1900 of 2048 units) but
the `OS/2` table's `winAscent`/`winDescent` (1946 and 512) on Windows, which made the line, the cell
and every glyph offset different on each. See `FontVerticalMetrics`.

**The atlas pixels depend on the `msdfgen` build.** The committed atlas was reproduced to within about
0.02% of its bytes by `msdfgen` 1.13 built from source without Skia:

```
cmake -S . -B build -G Ninja -DCMAKE_BUILD_TYPE=Release -DMSDFGEN_USE_VCPKG=OFF -DMSDFGEN_USE_SKIA=OFF
cmake --build build
```

The official Windows release of 1.13, which is built with Skia, differs on about 13% of the bytes (and
1.12.1 on more), mostly by a wide margin. The UI visual baselines are compared exactly, so regenerating
with a different build moves them. If you add a glyph, build `msdfgen` as above (WSL or any Linux machine
will do) and check that the glyphs you did not touch are unchanged before you re-record anything.

## Checks

`FontVerticalMetricsTest` pins the parsing against hand-built fonts and checks that each committed
object's `lineHeightEm` is what its font file's `hhea` table gives, so the committed atlas and the
generator cannot drift apart unnoticed. Run `./gradlew :awake:ui:font-atlas-generator:test`.

Not checked yet: that regenerating reproduces the committed atlas pixels, which needs a pinned
`msdfgen` on the CI runner.
