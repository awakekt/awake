# /preview-shader $ARGUMENTS

Render or probe an ASL shader headlessly — CPU evaluation, ANSI half-blocks in the terminal,
no window, no saved file, no GPU.

Arguments (all optional): `[width] [height] [--wgsl] [--probe <u> <v>]`
- No args: render the sample checker at 64×32.
- `--wgsl`: also print the emitted WGSL source.
- `--probe <u> <v>`: instead of an image, print one pixel's full variable view — every
  intermediate `let` plus the final color (a GPU capture tool's pixel history, without the GPU).

Run:

```bash
./gradlew :awake:asset:shader-dsl:previewShader -Pargs="$ARGUMENTS"
```

If `$ARGUMENTS` is empty, omit `-Pargs` entirely.

Then:
1. The ANSI image renders directly in the user's terminal — do not re-print or describe pixel
   escape codes; state what pattern/colors the math produces if asked.
2. For `--probe` output, relay the per-`let` values verbatim and interpret which line of the
   definition each maps to.
3. The definitions live in `awake/asset/shader-dsl/.../AslSampleShaders.kt` (triangle,
   checker) and `awake/asset/shader-pack/.../Asl*Shader*.kt` (the shipped nine). The preview
   `main` currently renders CheckerShader; to preview a different fragment, use
   `AslEvaluator.traceFragment` from a test, or extend `Preview.kt`'s `main`.
4. Texture-sampling and matrix-math fragments throw in the evaluator by design — probe the
   math around them instead (see the README's evaluator limitations).
