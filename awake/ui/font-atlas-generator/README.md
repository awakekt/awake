# `awake:ui:font-atlas-generator`

Packs the UI font's glyphs into a multi-channel signed distance field atlas and writes it as Kotlin
source, one object per weight, into `awake:core:text` (`Roboto*UiFontData.kt`). The output is
committed: nothing runs this at build time, so a change to the atlas is a change to those files.

The glyph set is `UiFonts.DEFAULT_CHARS`, 339 glyphs (#1058): printable ASCII and `• · … – — × −` first,
in the order they have always had, then Latin-1 Supplement (U+00A0 to U+00FF), Latin Extended-A
(U+0100 to U+017F) and `‘ ’ ‚ “ ” „ † ‡ ‰ ′ ″ ‹ › € ™`. Roboto has every one. A glyph is added by
appending it to the lists in `Main.kt`, to `UiFonts.DEFAULT_CHARS` and to `subset_roboto_outlines.py`, in
that order, and regenerating.

Each glyph gets a tile of its own, its sample rect and two texels around it, and the tiles are packed in
shelves, tallest first (`ShelfPacking`). The atlas holds the ink and little else: the 339 glyphs fill about the
same 0.9 million texels per weight that the 102 of ASCII did when every glyph had a cell as large as the
widest one (`@`, then `‰`). A glyph's texels are the same wherever its tile sits, since its pen origin lands on a
whole texel, so moving a glyph moves no texel of it. `awake:core:text` packs a font loaded at run time with the
same algorithm, and `OpenTypeUiFontTest` holds the two to one another.

It reads the Roboto faces in `fonts/`, which `tools/fonts-tooling/instantiate_roboto.py` instantiates
from the variable source. Nothing loads them at run time, so they live here rather than in
`awake:core:text`, and games and releases do not carry them. Besides this generator only tests read
them: `awake:core:text` and `awake:compose:ui-testing` bake them with `UiFonts.load` and hold the
result against the committed atlases.
`awake:core:text` keeps `fonts/Roboto-LICENSE.txt`, since the atlases it ships are drawn from Roboto.

Small text does not draw from these atlases: up to 24 device pixels it draws glyphs rasterized from the
outlines at the exact size (`UiFont.pixelGlyphs`). Those outlines ship separately, as each weight
subset to the atlas's characters, about 29 KB a face, written by
`tools/fonts-tooling/subset_roboto_outlines.py` into `RobotoUiFontOutlineData.kt`. The subset keeps the
face's `GPOS` `kern` feature as well, about 5 KB of it, which is where every size of the built-in
Roboto takes its pair kerning from (#1022). Run it after changing the faces in `fonts/` or the atlas's
glyph set, beside this generator.

Roboto is licensed under the SIL Open Font License 1.1, not Apache-2.0. An atlas is the font in
another format, which that license counts as a modified version of the font, so the atlases stay
under it: the generator wraps each object in an `OFL-1.1` SPDX snippet, and only the package and
imports above it fall under the file's Apache-2.0 header.

## Regenerating

Run it only to add or change a glyph or a weight, then commit the regenerated files with the change
that needs them. Linux on x86-64 is the supported environment: it is what CI regenerates on, and it
reproduces the committed files exactly.

1. Install the pinned [`msdfgen`](https://github.com/Chlumsky/msdfgen) and put it on `PATH`:

   ```
   awake/ui/font-atlas-generator/install-msdfgen.sh
   export PATH="$HOME/.local/bin:$PATH"
   ```

   It needs git, cmake, ninja, a C++ compiler and the FreeType, libpng and tinyxml2 development
   packages (Ubuntu: `build-essential cmake ninja-build libfreetype-dev libpng-dev libtinyxml2-dev`).
   It fetches the pinned commit and builds it without Skia.
2. Generate, then format. The generator writes KotlinPoet's two-space layout, and the committed files
   are that output after the repository's formatter:

   ```
   ./gradlew :awake:ui:font-atlas-generator:generateFontAtlas
   ./gradlew :awake:core:text:spotlessApply
   ```

3. Commit the files.

On Windows or macOS, use WSL or a Linux machine. Or skip it: push the change, and when the
**Font atlas** check fails it uploads the regenerated files as the `regenerated-font-atlas` artifact,
ready to commit.

## What depends on the machine, and what does not

**The metrics do not.** Ascent, descent, line height, the atlas cell size and every glyph's offsets and
advances come from the font file (its `head` and `hhea` tables and its outlines), so Windows, macOS and
Linux produce identical numbers. They used to come from `java.awt.Font.getLineMetrics`, which asks the
platform's font scaler: for the Roboto files that is `hhea` on Linux (ascender 1900 of 2048 units) but
the `OS/2` table's `winAscent`/`winDescent` (1946 and 512) on Windows, which made the line, the cell
and every glyph offset different on each. See `FontVerticalMetrics`. The numbers in the generated
source are written with `Locale.ROOT`, so a machine whose locale uses a decimal comma writes the same
source too. See `emLiteral`. Nor does the JDK matter: a glyph's box is measured by `tightBounds`, because
`Shape.getBounds2D` boxes a curve's control points on JDK 17 and the curve itself from JDK 19, which
measured `@` and a few other glyphs differently from one JDK to the next.

**The atlas pixels depend on the `msdfgen` build.** `install-msdfgen.sh` builds `msdfgen` 1.13 from its
pinned commit without Skia. On Ubuntu 24.04 (gcc 13.3, FreeType 2.13.2) that reproduces every byte of
the committed atlas, for all seven weights, and after `spotlessApply` the committed files exactly, on
JDK 21 and on the JDK 17 that CI uses. The official Windows release of 1.13, which is built with Skia, differs on about 13% of the
bytes (and 1.12.1 on more), mostly by a wide margin. The UI visual baselines are compared exactly, so
regenerating with a different build moves them. A different compiler or a different CPU architecture
could change the pixels too; that has not been measured, which is why CI is the arbiter.

Moving the pin (a new `msdfgen`, or another runner image) is a decision to make together with a
regenerated atlas, and the visual baselines it moves.

## Checks

`ShelfPackingTest` pins the packing. `FontVerticalMetricsTest` pins the parsing against hand-built fonts and checks that each committed
object's `lineHeightEm` is what its font file's `hhea` table gives. `EmLiteralTest` pins the number
format against the default locale. Run `./gradlew :awake:ui:font-atlas-generator:test`.

The **Font atlas** workflow (`.github/workflows/font-atlas.yml`) runs when this module, the font
files or the generated files change. It builds the pinned `msdfgen`, regenerates, formats, and fails on
any difference from what is committed, so a hand edit, a font change that was not regenerated, or an
atlas made with another `msdfgen` cannot merge.
