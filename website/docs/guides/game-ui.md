# Game UI

<p class="awake-lede">A HUD that belongs to the scene: text, panels, bars, buttons, images and touch joysticks pinned to the screen. Each element is a component on a scene entity, so it saves and loads with the scene, and the scene runtime draws it over the game.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">component: <code>canvas_element</code></span>
<span class="awake-badge">module: <code>awake:scene:canvas</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge awake-badge--ok">WebGPU</span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

Game UI is scene data. For menus, editors and tools written in Kotlin, use
[AwakeKt Compose](ui.md) instead.

## Add a HUD

Give each element its own entity with a `canvas_element` component. This HUD has a health bar in
the top-left corner, a score in the top-right corner and a jump button in the bottom-right corner.
Both forms below build the same components.

=== "Scene document"

    ```json title="hud.scene.json"
    --8<-- "website/docs/snippets/ui/hud.scene.json"
    ```

=== "Scene DSL"

    ```kotlin title="Kotlin"
    --8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/GameUiDocsSampleTest.kt:hud-dsl"
    ```

    The scene DSL has no dedicated function for canvas elements. `configure(::CanvasElement) { … }`
    attaches one and sets its fields.

There is no Studio tab: AwakeKt Studio has no inspector for `canvas_element` yet.

## React to a button

A game system polls the button. `consumePress()` returns `true` once for each tap, then `false`
until the next tap.

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/GameUiDocsSampleTest.kt:jump-system"
```

For a button the player holds down, read `isHeld` instead. For a joystick, read `stickX` and
`stickY`.

## Properties

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `kind` | `Text` · `Panel` · `Bar` · `Button` · `Joystick` · `Image` | `Text` | What the element draws. |
| `anchor` | `TopLeft` · `TopCenter` · `TopRight` · `CenterLeft` · `Center` · `CenterRight` · `BottomLeft` · `BottomCenter` · `BottomRight` | `TopLeft` | The screen point the element is pinned to. |
| `offsetX` | number (dp) | `16` | Moves the element inward from its anchor. From a right anchor it moves left. |
| `offsetY` | number (dp) | `16` | Moves the element inward from its anchor. From a bottom anchor it moves up. |
| `width` | number (dp) | `200` | Element width. A joystick is a round pad of this width. |
| `height` | number (dp) | `40` | Element height. |
| `text` | string | `""` | A `Text` element's content, or a `Button`'s label. |
| `fontSize` | number (sp) | `18` | Size of `text`. |
| `color` | `#RRGGBB` or `#RRGGBBAA` | `#FFFFFF` | Text colour, a `Bar`'s fill, or a `Joystick`'s knob. |
| `background` | `#RRGGBB` or `#RRGGBBAA` | `#00000000` | Fill behind the element. A joystick's pad. |
| `value` | number, 0 to 1 | `1` | A `Bar`'s fill fraction. |
| `order` | integer | `0` | Draw order. Lower values draw first, so higher ones sit on top. |
| `visible` | boolean | `true` | Hidden elements are not drawn and take no taps. |
| `action` | string | `""` | Names what the element does for the game, such as `move` or `jump`. The game decides what each name means. |
| `touchOnly` | boolean | `false` | Draw the element only where touch controls are shown. |
| `style` | object | `{}` | How the element looks beyond its colours. See [Draw images](#draw-images). |

A running `CanvasElement` also has values that are not saved:

| Member | Type | What it gives |
| --- | --- | --- |
| `consumePress()` | `Boolean` | `true` once for each tap on a `Button` since the last call. |
| `isHeld` | `Boolean` | Whether a `Button` is pressed right now. |
| `stickX`, `stickY` | `Float`, -1 to 1 | A `Joystick`'s deflection. Up is negative `stickY`, as on screen. Releasing re-centres it. |

## Draw images

An element's `style` holds its images. `style.image` is an `Image`'s picture, the frame of a
`Panel`, `Button` or `Text` over its `background`, or a `Bar`'s track. `style.fillImage` is a
`Bar`'s fill, in place of `color`. Each names a PNG or JPEG in the project and says how it fills the
element. The image is cut into nine by four slice insets, in the image's pixels. The corners keep
their size, one dp per pixel, so a window frame stays crisp at any size. The edges stretch along
their length, and the centre stretches both ways.

```json title="A framed panel and a gauge"
[
  { "component": "canvas_element", "kind": "Panel", "width": 240, "height": 120,
    "style": { "image": { "path": "ui/window.png", "sliceLeft": 16, "sliceTop": 16, "sliceRight": 16,
                          "sliceBottom": 16, "repeatEdges": true, "pixelated": true } } },
  { "component": "canvas_element", "kind": "Bar", "width": 108, "height": 10, "value": 0.6,
    "style": { "fillImage": { "path": "ui/gauges.png", "regionY": 10, "regionHeight": 10,
                              "sliceLeft": 3, "sliceRight": 3 } } }
]
```

| Field | Type | Default | What it does |
| --- | --- | --- | --- |
| `path` | string | required | The image file, from the project's root. |
| `regionX`, `regionY` | integer | `0` | The top-left pixel of the part of the image to use, such as one frame of a sheet. |
| `regionWidth`, `regionHeight` | integer | `0` | The part's size. `0` reaches the image's right or bottom edge. |
| `sliceLeft`, `sliceTop`, `sliceRight`, `sliceBottom` | integer | `0` | The corners' size in pixels. With none the whole image stretches. Slice only the left and right for a three-part strip, such as a gauge. |
| `repeatEdges` | boolean | `false` | Tile the edges at their own size instead of stretching them, so a pattern keeps its spacing. |
| `repeatCenter` | boolean | `false` | Tile the centre both ways instead of stretching it. |
| `tint` | `#RRGGBB` or `#RRGGBBAA` | `#FFFFFF` | Multiplies the image's colour and alpha. |
| `pixelated` | boolean | `false` | Keep the pixels sharp when the image is scaled, for pixel art. |

A `Bar`'s fill image is laid out at the bar's full width and cut at its `value`, not squeezed into
it, so the fill's pattern and its end cap stay where they are as the value changes. Corners too
big for the element shrink together. An element whose image did not load, or whose region does not
fit in it, draws without it.

When a project is played with `awake:project:runtime`, `loadProject` reads every image the scene's
canvas names, and the runtime draws them. If your app runs its own Compose host, pass the decoded
images yourself: `SceneCanvas(world, images = loadCanvasImages(scene, files))`.

## Put elements inside others

An element whose node sits below another element's node in the scene is drawn inside that element.
Its `anchor` and offsets place it against the parent's box instead of the screen, and it draws over
the parent's own content. Move the parent and its children move with it; hide the parent and they
are hidden too. Plain nodes in between, such as a group, are passed through: the nearest element
above is the parent.

```json title="A status window with a bar inside"
{ "name": "Status", "components": [ { "component": "canvas_element", "kind": "Panel",
    "offsetX": 8, "offsetY": 8, "width": 220, "height": 112,
    "style": { "image": { "path": "ui/status.png", "pixelated": true } } } ],
  "children": [
    { "name": "HP", "components": [ { "component": "canvas_element", "kind": "Bar",
        "offsetX": 96, "offsetY": 4, "width": 108, "height": 10, "value": 0.8, "color": "#E5484D" } ] }
  ] }
```

A child is laid out within its parent, so one that would stick out past the parent's edge is
narrowed to fit.

## Scale the UI

`SceneAppLifecycleRuntime.canvasScale`, or `SceneCanvas(world, scale = …)`, multiplies every
element's size, offset, text and image pixels. A HUD drawn from 1x pixel art reads at 2 on a large
screen. Every edge lands on a whole pixel at any scale, so the art stays crisp; at a fractional scale
such as 1.5, some of its pixels are a screen pixel wider than others.

## How it works

When a scene has any `canvas_element`, the scene runtime draws every visible element with
`SceneCanvas(world)` each frame. It does this even when the app declares no `ui { }`. The canvas is
drawn under the app's own `ui { }` content, so a pause menu covers the HUD.

The element's anchor places it against one of nine screen points. The offsets move it inward. On a
centred axis, the offset shifts it right or down. Negative offsets are treated as zero. The element's
hit area is exactly its `width` by `height`.

A `Joystick` knob follows a drag up to the pad's edge and springs back on release. A `touchOnly`
element is drawn only when `SceneCanvas(world, showTouchControls = true)` is called, or when
`SceneAppLifecycleRuntime.showTouchControls` is `true`. When a project is played with
`awake:project:runtime`, an element whose `action` names one of the scene's
[input actions](input.md#bind-actions-in-the-scene) adds to it: a `move` joystick steers the player,
a `jump` button jumps while held and a `run` button runs, as the keys do, and a button for an action of
your own holds and presses that action.

If your app runs its own Compose host, draw the canvas yourself:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/GameUiDocsSampleTest.kt:own-host"
```

!!! warning "One tap, one reader"
    `consumePress()` clears the tap it returns. If two systems poll the same button, only the first
    one sees the tap. Poll it in one place and share the result, or read `isHeld`.

!!! tip "Bad colours do not break the frame"
    `SceneValidator` reports a colour that is not `#RRGGBB` or `#RRGGBBAA`, a negative size, a
    `fontSize` that is not positive, a `value` outside 0 to 1, and an image with no `path`, a negative
    region or slice, or slices wider than their region. At draw time, a bad `color` falls
    back to white and a bad `background` falls back to transparent.

## Debugging

Each drawn element carries the test tag `canvas-element-<entity id>` in the UI's semantics tree.
`SceneAppLifecycleRuntime.uiSemantics` holds the last frame's tree, so a test can find an element
and check where it was placed.

## See also

- [UI with AwakeKt Compose](ui.md) for menus and tools written in Kotlin.
- [Scene authoring](../guides/scene-dsl.md) for the scene DSL.
- [Scene engine overview](../guides/scene-documents.md) for loading scene documents.
