# `awake:ui:font-atlas-generator`

Packs the UI font's glyphs into a multi-channel signed distance field atlas and writes it as Kotlin
source, one object per weight, into `awake:core:text` (`Roboto*UiFontData.kt`). The output is
committed: nothing runs this at build time, so a change to the atlas is a change to those files.

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

`FontVerticalMetricsTest` pins the parsing against hand-built fonts and checks that each committed
object's `lineHeightEm` is what its font file's `hhea` table gives. `EmLiteralTest` pins the number
format against the default locale. Run `./gradlew :awake:ui:font-atlas-generator:test`.

The **Font atlas** workflow (`.github/workflows/font-atlas.yml`) runs when this module, the font
files or the generated files change. It builds the pinned `msdfgen`, regenerates, formats, and fails on
any difference from what is committed, so a hand edit, a font change that was not regenerated, or an
atlas made with another `msdfgen` cannot merge.
