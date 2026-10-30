# UI with AwakeKt Compose

<p class="awake-lede">AwakeKt Compose is AwakeKt's own retained UI engine with a Compose-shaped Kotlin API. Use it for menus, overlays and tools that share the frame with your game.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:compose:foundation</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge awake-badge--ok">WebGPU</span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

Add `com.awakekt.awake.compose:foundation` (catalog alias `libs.awake.compose.foundation`) to
`commonMain`. For headless UI tests, add `com.awakekt.awake.compose:ui-testing` to your test source
set. For a HUD saved inside a scene, see [Game UI](game-ui.md).

## It is not Jetpack Compose

The function and modifier names follow Compose, but the engine is different. Do not copy Jetpack
Compose code without checking it against these rules.

- A UI function takes a `context(_: Composer)` parameter. There is no `@Composable` annotation and
  no compiler plugin.
- The whole tree is rebuilt every frame in one synchronous pass. Nothing is skipped.
- `remember { … }` keeps a value from one frame to the next. There is no `rememberSaveable`,
  `LaunchedEffect`, `DisposableEffect` or `ViewModel`.
- Input is shared with the game. Each frame reports what the UI claimed, so gameplay can ignore a
  click that landed on a button.

## Lay out a component

`Row`, `Column` and `Box` place their children. Modifiers set size, padding, background and input.

```kotlin title="Kotlin"
--8<-- "awake/compose/ui-testing/src/desktopTest/kotlin/com/awakekt/awake/compose/testing/ComposeDocsSampleTest.kt:layout"
```

!!! warning "Row spacing has its own function"
    `Arrangement.spacedBy(…)` returns a vertical arrangement for a `Column`. For a `Row`, use
    `Arrangement.spacedByHorizontal(…)`.

## Keep state across frames

Hold a value with `remember { mutableStateOf(…) }` in the lowest node that owns it. Pass the value
down and take events back up, so a component such as `Counter` has no hidden state.

```kotlin title="Kotlin"
--8<-- "awake/compose/ui-testing/src/desktopTest/kotlin/com/awakekt/awake/compose/testing/ComposeDocsSampleTest.kt:state"
```

!!! warning "Remembered values are matched by call order"
    A `remember` belongs to its enclosing node and is found by call order. Never call `remember`
    after a list whose length changes in the same node, and never inside an `if` that flips. Give
    each repeated item its own node and a `key(…)`.

## Show an image

`Image` draws an `ImageBitmap`: RGBA8 pixels, rows top to bottom. `contentScale` is `Fit` (the
default), `Crop`, `FillBounds` or `Inside`. A `contentDescription` is what assistive technology
reads; pass `null` for a decorative image.

```kotlin title="Kotlin"
--8<-- "awake/compose/ui-testing/src/desktopTest/kotlin/com/awakekt/awake/compose/testing/ComposeDocsSampleTest.kt:image"
```

`tint` multiplies every pixel's colour; white, the default, leaves it as it is. `filterQuality`
`FilterQuality.None` samples the nearest pixel, so pixel art stays crisp when it's scaled;
`FilterQuality.Low`, the default, blends neighbouring pixels. To draw part of a bitmap, such as one
icon of a sheet, call `drawImage(bitmap, …, srcX, srcY, srcWidth, srcHeight)` from a draw modifier.

### Frames and gauges

`Modifier.background(ImageFill(…))` fills a node of any size from one image cut into nine. The four
`slice` insets, in the image's pixels, mark the corners, which keep their size at one dp per pixel,
or per `pixelsPerDp` pixels for art drawn at 2x or 3x. The edges stretch along their length, or
repeat at that size with `repeatEdges`; the centre stretches, repeats both ways with
`repeatCenter`, or is left out with `hollow`. Slice only the left and right for a three-part strip
such as a gauge, and pass `srcX`, `srcY`, `srcWidth` and `srcHeight` to cut the frame from a sheet.
A node too small for its corners shrinks them together.

```kotlin title="Kotlin"
--8<-- "awake/compose/ui-testing/src/desktopTest/kotlin/com/awakekt/awake/compose/testing/ComposeDocsSampleTest.kt:image-fill"
```

From a draw modifier, `drawImageFill(fill, x, y, width, height)` draws the same fill into part of the
node. `drawImageFillBar(fill, value, direction)` draws it as a bar's fill, from the edge
`direction` names: both end caps stay whole at every value above 0, and only the middle grows.

### Gradients and styles

`Modifier.background(brush, shape)` fills a node with a `Brush`: `Brush.vertical(top, bottom)`,
`Brush.horizontal(start, end)`, or one colour per corner with `Brush.linearGradient`. Inside a
`Style`, `background(brush)` does the same in place of a background colour, `backgroundImage(fill)`
draws an `ImageFill` over it, and `dropShadow(shadow)` casts a shadow in the style's shape. Scene UI's
`canvas_element` style is the saved form of the same properties.

`TextStyle(shadow = TextShadow(color, offsetX, offsetY, blur), outline = TextOutline(color, width))` draws
a shadow and an outline under a `Text` or a `BasicTextField`'s text, in dp; both merge over an inherited
style like its colour and size do. The shadow is hard unless `blur` softens its edge across that many dp
either side; with no offset it is a glow. With the built-in fonts and any font from `UiFonts.load`, each
glyph draws its fill, outline and shadow in one quad from the font's effect field, a coarse distance field
that reaches half an em past the ink: a thick outline stays round with no gaps, and the text costs one draw
a glyph. Decorated text draws from the distance field at every size, so its weight does not jump at the
24-pixel limit. A font without an effect field, such as `UiFonts.bitmap()`, stamps the text in the shadow's
colour, then eight times around it in the outline's, then once more. A scene's `canvas_element` text ignores
the host's text style and looks only as its own data says.

To load a PNG or JPEG, call `decodeImageBitmap(bytes)`. It suspends, because the browser decodes
asynchronously, so decode outside the UI and show the result on a later frame. The engine uploads
each bitmap to the GPU the first frame it appears and keeps it, so reuse a decoded bitmap instead of
decoding the same file again.

## Draw crisp text

`Text` sizes its glyphs in device pixels: `sp` times the display density and any canvas scale, rounded
to a whole pixel. So 12 sp on a 150% display is 18 physical pixels, not a stretched 12. Which path draws
the text depends on that size.

| Text | Drawn from |
|---|---|
| 24 device pixels or smaller, on the pixel grid | Glyphs rasterized from the font's outlines at exactly that size, drawn 1:1 |
| Larger than 24 pixels | The font's distance-field atlas |
| Under `Modifier.scale` or another scaling transform | The distance-field atlas, so it scales smoothly |
| In `UiFonts.bitmap()` | Its own pixel-art atlas, at every size |

The exact-size glyphs put a one-pixel stem on one solid pixel column, the x-height and cap height
on whole pixels, and the crossbar of an `e` or the bar of an `H`, `A` or `F` on one solid pixel row.
Each glyph comes in four subpixel variants, and a glyph is drawn as the variant nearest its pen's
fraction of a pixel, with its stem still on the grid, so the gaps in a run follow layout instead of
jumping by up to a pixel between letters. The default Roboto family and any font from `UiFonts.load`
have them. Each size is rasterized the first time it is drawn, in about ten milliseconds, and kept.

### How coverage becomes colour

Both paths give each pixel a coverage: the share of the pixel the glyph's outline covers. Exact-size
glyphs store it; the distance field gives it as a one-pixel ramp across the outline. One rule, the
**text coverage rule**, turns that coverage into the alpha the pixel is blended with. It is the same on
both paths, on Vulkan, on WebGPU and in the CPU raster the snapshot tests use, so text does not change
weight when it crosses the 24-pixel limit or starts to scale.

The rule is Skia's mask gamma with the constants Chromium uses for grayscale text on Linux:

1. Take the text colour's luma: its channels raised to the power 1.2, weighted 0.2126, 0.7152 and
   0.0722, and the sum raised back by 1 / 1.2.
2. Assume the ground is the opposite of the text: ground luma = 1 − text luma. The shader cannot see
   what is behind the text.
3. Boost the coverage `c` with a contrast of 0.2 times the ground's linear luma: `c + (1 − c) × k × c`.
   Dark text on a light ground gains a little, and light text on a dark ground gains nothing.
4. Mix text and ground by that coverage in light linearised with gamma 1.2. Then take the alpha that
   gives the same colour when the GPU blends in the target's own encoding.

| Constant | Value | Where |
|---|---|---|
| Gamma | 1.2 | `TEXT_GAMMA` in `AslUiShaders.UiGlyphShader`; `UiRasterizer.textCoverageAlpha` copies it |
| Contrast | 0.2 | `TEXT_CONTRAST`, likewise |
| Luma weights | 0.2126, 0.7152, 0.0722 | Rec. 709 |

At half coverage, near-black text on white draws with alpha 0.50 and near-white text on near-black with
0.55. The exact-size glyphs used to blend with gamma 1.8 and a contrast of 1 in both polarities, which
gave 0.49 and 0.80, so light text looked bolder than dark. The distance field used `pow(c, 1 / 1.45)`,
which gave 0.62 for both.

`TextPolarityWeightTest` holds the rule to Chromium. Chromium draws the same label in the same Roboto
face, dark on light and light on dark, at every size from 9 to 48 px. The references are written by
`tools/fonts-tooling/chromium_polarity_reference.cjs`. Awake's ink is within 6% of Chromium's at every
size in both polarities, 0.95 to 1.05 of it. The light-on-dark to dark-on-light ink ratio is within 6%
of Chromium's own. Ink means the summed darkness of the text's pixels, measured toward the text colour.
From 24 px pixel glyphs to 25 px distance field, the ink per em changes by under 2% and stem darkness
by under 3.5%. Before the rule, light-on-dark small text held 1.16 to 1.29 times Chromium's ink, and
crossing the limit moved ink by 9 to 10% in each polarity.

The rule does no stem darkening. Chromium does none, and the sizes it was measured at are within the
tolerance without it.

#### Text colour and custom shaders

- **The colour picks the curve.** The rule reads only the text colour, never the ground. Dark text gets
  a slightly heavier edge and light text a slightly lighter one than plain coverage would give. Text on
  a ground close to its own luma, such as grey on grey, is drawn as if the ground were the opposite of
  the text, as Chromium draws it. A saturated colour counts by its luma: pure red's is about 0.28, so red
  text is treated as dark text, wherever it is drawn.
- **Mid-grey text** has a luma of 0.5 and its assumed ground is itself, so it draws with the boosted
  coverage as it is.
- **The colour's alpha** multiplies the rule's alpha afterwards. Faded text keeps the shape of its
  edges and only gets lighter.
- **Layers and opacity.** The rule applies when a glyph is drawn, including into a `graphicsLayer`. A
  layer composited later gives the same pixels as drawing straight onto the ground.
- **A shader document** (`shader_effect`) draws as a pass of its own. It never changes how text is
  drawn: text it covers, or that covers it, has already been drawn with the rule. An image pass that
  post-processes the frame sees text after the rule as well.
- **A glyph shader of your own.** A renderer built with its own `UiShaderSet.glyph` must apply the
  rule itself, or its text will not match the CPU raster or the other backend. Start from
  `UiGlyphShader` in `awake:asset:shaders`. Its `fontInfo` uniform says which atlases take the rule:
  `x = 1` for a distance field, and `x = 0` with `z = 1` for exact-size coverage. A bitmap font such
  as `UiFonts.bitmap()` (`z = 0`) and colour glyphs (`x = 2`) draw their alpha unchanged.

### The reference small text is held to

The weight of small text is checked against references we control, not against Awake's own earlier
output. Both polarities are held to Chromium, as above. The shapes of small dark-on-light text are
also held to FreeType. `tools/fonts-tooling/reference_render.py` renders the same labels with
FreeType, pinned to 2.13.2 through `freetype-py` 2.5.1 in `requirements-reference.txt`, with these
settings:

| Setting | Value | Why |
|---|---|---|
| Hinting | Light (`FT_LOAD_TARGET_LIGHT`) | Snaps horizontal edges and the x-height to the grid, leaves shapes and advances alone |
| Stem darkening | On | Keeps thin strokes from fading once blending stops thickening them |
| Anti-aliasing | 8-bit grayscale coverage | The quantity Awake's exact-size glyphs hold too |
| Blending | Gamma 1.8 | The gamma FreeType documents for light hinting with stem darkening, and the classic text gamma |

The PNGs live in `awake/compose/ui-testing/src/desktopTest/resources/text-reference/`, and
`TextReferenceMetricsTest` compares Awake's rendering with them per case: stem width, ink mass, the
darkness of curves and diagonals against stems, the share of faint ink pixels, and the mean pixel
difference. It asserts the dark-on-light cases and only reports the light-on-dark ones. The reference
composites light text with gamma 1.8, which gives it about 1.4 times the ink of dark text, where
Chromium gives about 1.1 times. The PNGs change only when someone deliberately reruns the generator:

```
python3 -m pip install -r tools/fonts-tooling/requirements-reference.txt
python3 tools/fonts-tooling/reference_render.py
```

To see any component baseline beside FreeType's glyphs, write glyph packs with
`reference_render.py --glyph-pack <dir>` and run the snapshot tests with
`AWAKE_TEXT_REFERENCE_GLYPHS=<dir>`; each baseline is also written, in FreeType's glyphs, to
`build/reports/text-reference/`.

To move the limit, or to draw a font from its distance field at every size, wrap it once and keep it:

```kotlin title="Kotlin"
val headings = UiFonts.default().withPixelGlyphMaxSize(16) // exact-size glyphs up to 16 px
val uniform = UiFonts.default().withPixelGlyphMaxSize(0)   // the distance field at every size
Text("Inventory", style = TextStyle(font = uniform))
```

## Put UI on the screen

An app has one Compose host. Pick the form that matches your app.

A UI-only app, with no scene:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/UiHostingDocsSampleTest.kt:ui-only-app"
```

UI that belongs to one scene, drawn over it:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/UiHostingDocsSampleTest.kt:scene-ui"
```

UI that belongs to the whole app, drawn over whichever scene runs. Install
`sceneComposeAppModule` before the scene:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/UiHostingDocsSampleTest.kt:app-ui-over-scene"
```

`composeAppModule` comes from `awake:engine:compose`. `sceneComposeAppModule` and the scene's
`ui { }` come with the scene modules.

!!! warning "Only one host"
    Installing a second `composeAppModule` throws. Installing `sceneComposeAppModule` after a scene
    that already declares `ui { }` also throws.

## Test UI without a window

`composeFrame` builds, measures, places and paints one frame, then lets you find nodes by test tag
and check their bounds. No GPU or window is needed.

```kotlin title="Kotlin"
--8<-- "awake/compose/ui-testing/src/desktopTest/kotlin/com/awakekt/awake/compose/testing/ComposeDocsSampleTest.kt:compose-frame"
```

A frame is a snapshot. To test input, use `composeTestSession`: it keeps the UI between frames, and
`click(tag)` presses and releases on a node, then returns the next frame.

```kotlin title="Kotlin"
--8<-- "awake/compose/ui-testing/src/desktopTest/kotlin/com/awakekt/awake/compose/testing/ComposeDocsSampleTest.kt:session"
```

A session also has `hover(tag)`, `clickAt(x, y)` and `pressKey(key)`. `frame.printToString()` prints
the semantics tree and a count of what was painted, which is the quickest way to see why a check
failed.

## How it works

| Module | What it holds |
| --- | --- |
| `awake:compose:runtime` | `Composer`, `remember`, `CompositionLocal`. |
| `awake:compose:ui` | Layout nodes, `Modifier`, drawing, input, focus, semantics, `ImageBitmap`. |
| `awake:compose:foundation` | `Row`, `Column`, `Box`, `Text`, `Image`, `BasicTextField`, lazy lists, `clickable`, scrolling and gestures. |
| `awake:compose:ui-testing` | `composeFrame`, `composeTestSession`, semantics matchers, a CPU rasterizer. |
| `awake:engine:compose` | `composeAppModule`, which connects one Compose host to an app. |

Each frame, the host turns the app's input into a UI frame, lays out and paints the tree, and hands
the draw primitives to the renderer. It also reports what the UI claimed from the input: whether a
pointer is captured, a text field has focus, or a scroll was consumed. In a scene,
`SceneAppLifecycleRuntime.uiOwnership` holds that report, and the camera and player systems read it.

## See also

- [Game UI](game-ui.md) for a HUD stored in a scene.
- [shadcn components](shadcn.md) for themed buttons, fields and menus.
- [Button](../reference/components/button.md) reference.
