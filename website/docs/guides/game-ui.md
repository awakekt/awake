# Game UI

<p class="awake-lede">A HUD that belongs to the scene: text, panels, bars, buttons and touch joysticks pinned to the screen. Each element is a component on a scene entity, so it saves and loads with the scene, and the scene runtime draws it over the game.</p>

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
| `kind` | `Text` · `Panel` · `Bar` · `Button` · `Joystick` | `Text` | What the element draws. |
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

A running `CanvasElement` also has values that are not saved:

| Member | Type | What it gives |
| --- | --- | --- |
| `consumePress()` | `Boolean` | `true` once for each tap on a `Button` since the last call. |
| `isHeld` | `Boolean` | Whether a `Button` is pressed right now. |
| `stickX`, `stickY` | `Float`, -1 to 1 | A `Joystick`'s deflection. Up is negative `stickY`, as on screen. Releasing re-centres it. |

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
    `fontSize` that is not positive, and a `value` outside 0 to 1. At draw time, a bad `color` falls
    back to white and a bad `background` falls back to transparent.

## Debugging

Each drawn element carries the test tag `canvas-element-<entity id>` in the UI's semantics tree.
`SceneAppLifecycleRuntime.uiSemantics` holds the last frame's tree, so a test can find an element
and check where it was placed.

## See also

- [UI with AwakeKt Compose](ui.md) for menus and tools written in Kotlin.
- [Scene authoring](../guides/scene-dsl.md) for the scene DSL.
- [Scene engine overview](../guides/scene-documents.md) for loading scene documents.
