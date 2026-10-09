# Input

<p class="awake-lede">Read keys, pointer buttons and scroll from a per-frame snapshot, in a scene's <code>update</code> block or in your own systems.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>com.awakekt.awake.core:input</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

Every app has one `Input`. The platform host writes key and pointer events into it as they arrive,
and once per frame takes an `InputSnapshot`: a fixed picture of that frame that every system reads.
A scene names its actions, and the keys behind them, in its `input_actions` component; see
[Bind actions in the scene](#bind-actions-in-the-scene). For the player's movement, see
[Cameras and controls](cameras-and-controls.md).

## Read a snapshot

`isDown` is true while a key or button is held. `wasPressed` is true only on the frame it went down.

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/InputDocsSampleTest.kt:snapshot"
```

A test drives input this way; in an app the host calls `updateSnapshot()` for you, once per frame.

## Read input in a scene

The `update` block receives the frame's snapshot. A system looks up the `Input` service and reads
`currentSnapshot` when it runs:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/InputDocsSampleTest.kt:scene-input"
```

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/InputDocsSampleTest.kt:jump-system"
```

The system takes a function rather than a snapshot, so it reads the current frame each time instead
of the one it was created with.

## Leave the UI its input

When a text field has focus or a dialog is open, gameplay should ignore the keys. `GameplayInput`
wraps a snapshot and what the UI claimed this frame (`uiOwnership` on the scene runtime), and
answers `false` for anything the UI owns:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/InputDocsSampleTest.kt:gameplay-input"
```

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/InputDocsSampleTest.kt:walk-system"
```

`GameplayInput` is in `com.awakekt.awake.scene:controls`. The built-in camera and player systems
already read input through it.

## Bind actions in the scene

A scene's `input_actions` component names its actions and what triggers each: keys, pointer buttons
and on-screen controls. Systems then ask for an action and never for a key, so a game rebinds its
controls in data:

```json title="controls.scene.json"
{ "name": "Controls", "components": [
  { "component": "input_actions", "actions": [
    { "type": "button", "name": "interact", "keys": ["E"], "trigger": "Press" },
    { "type": "button", "name": "run", "keys": ["X"], "trigger": "Toggle", "startsOn": true }
  ] }
] }
```

`PlayerInputSystem` reads the keys into the actions every frame, and a played project's
`canvas_element` controls add to them. A system of your own reads them from the world with
`world.inputActions()`:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/InputDocsSampleTest.kt:interact-system"
```

`isActive` says whether a button is active as its `trigger` says, `wasPressed` whether one was
pressed this frame, and `axisX` and `axisY` where an axis points. The player's `move`, `jump` and
`run` are there whether or not the scene binds them: a scene that does not gets W A S D or the
arrows, Space and Shift held. Every field is in the
[reference](../reference/scene-document-components.md#input_actions).

## Bind actions to keys

Without a scene, a `KeybindingProfile` maps your own actions to a primary and an optional secondary
key, and can be rebound at runtime:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/InputDocsSampleTest.kt:actions"
```

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/InputDocsSampleTest.kt:keybindings"
```

`getAxis2D` returns a unit vector with forward as -Z, or zero when no direction key is held.

## Properties

`InputSnapshot`:

| Property | Type | What it holds |
| --- | --- | --- |
| `keysDown` | set of `Key` | Keys held this frame. `isDown(key)` reads it. |
| `keysPressed`, `keysReleased` | set of `Key` | Keys that went down or up this frame. `wasPressed(key)` reads the first. |
| `pointerX`, `pointerY` | number | Last pointer position the host reported. |
| `pointerDown` | boolean | Primary button held. |
| `pointerPressed`, `pointerReleased` | boolean | A press or release happened this frame, even if both did. |
| `buttonsDown`, `buttonsPressed`, `buttonsReleased` | set of `PointerButton` | `Primary`, `Secondary`, `Middle`, `Back`, `Forward`. `isDown(button)`, `wasPressed(button)`, `wasReleased(button)` read them. |
| `scrollDeltaX`, `scrollDeltaY` | number | Scroll this frame. |
| `scrollSource` | `Wheel` · `Trackpad` · `Unknown` | What scrolled, where the platform can tell. |
| `typedText`, `editActions` | string, list | Text typed this frame, and editing keys such as backspace. |

`Key` covers letters, digits, arrows, F1 to F5, Space, Escape, Tab, Enter, the editing keys, and
`Ctrl`, `Shift`, `Alt` and `Meta` (left and right are the same key). A key the host does not map
arrives as `Key.Unknown`.

## How it works

The host writes events into `Input` between frames with `setKeyDown`, `setPointer`, `setButton` and
the scroll fields. At the start of a frame it calls `updateSnapshot()`, which works out the pressed
and released edges against the previous snapshot and clears the per-frame values: scroll, typed text
and edit actions. The snapshot is then passed to the app as `frame.input` and stays unchanged for the
whole frame.

!!! warning "`wasPressed` is per frame, not per fixed step"
    `update` runs once per fixed step, and every step in a frame sees the same snapshot. On a frame
    that runs two steps, `wasPressed` is true in both; on a frame that runs none, `update` does not
    see the press. Handle one-shot presses in a frame system.

## See also

- [Cameras and controls](cameras-and-controls.md) for the built-in player and camera controls.
- [App lifecycle](app-lifecycle.md) for fixed steps and frame systems.
