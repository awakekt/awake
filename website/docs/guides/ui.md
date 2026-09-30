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

To load a PNG or JPEG, call `decodeImageBitmap(bytes)`. It suspends, because the browser decodes
asynchronously, so decode outside the UI and show the result on a later frame. The engine uploads
each bitmap to the GPU the first frame it appears and keeps it, so reuse a decoded bitmap instead of
decoding the same file again.

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
