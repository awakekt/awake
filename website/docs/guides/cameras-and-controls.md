# Cameras and controls

<p class="awake-lede">A camera decides where the scene is seen from. A camera rig moves it with the mouse and follows a target; movement control turns keys into player motion.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">components: <code>camera</code> · <code>camera_rig</code> · <code>movement_control</code> · <code>spin_control</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

A scene document and the scene DSL are two ways to write the same ECS components. Both tabs below
build a player and a third-person camera that follows it. `camera` is in `awake:scene:scene3d`;
`camera_rig` and `movement_control`, and the systems that drive them, are in
`com.awakekt.awake.scene:controls`, which `com.awakekt.awake.scene:authoring` brings with it.

## Add a follow camera

Give the camera entity a `camera` for the lens and a `camera_rig` for how it moves. The rig's
`target` names the node it follows; the player's `movement_control` marks it as the entity the keys
move.

=== "Scene document"

    ```json title="follow-camera.scene.json"
    --8<-- "website/docs/snippets/scene/follow-camera.scene.json"
    ```

=== "Scene DSL"

    ```kotlin title="Kotlin"
    --8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/CamerasDocsSampleTest.kt:follow-camera-dsl"
    ```

    `camera()` adds both `Camera` and `CameraRig`. `MovementControl` has no helper, so `with` adds
    it.

=== "Studio"

    1. Select the camera entity in the **Hierarchy**.
    2. In the **Inspector**, the **Camera** section sets **Field of View**, **Near** and **Far**.
       **Viewport Preview** turns on a preview from this camera.

    Studio has no **Add component** entry for `camera_rig` or `movement_control`. Add them in the
    scene document.

## Load it

`camera_rig` and `movement_control` are not among the built-in components, so register them before
decoding. `registerControls()` adds both to a registry you pass to `instantiate`:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/CamerasDocsSampleTest.kt:load-controls"
```

## Make it move

Components hold settings; systems move things. Register the control systems in the scene, and tag
the camera that takes input with `ActiveCamera`:

```kotlin title="Kotlin"
--8<-- "awake/scene/authoring/src/desktopTest/kotlin/com/awakekt/awake/scene/authoring/CamerasDocsSampleTest.kt:app-controls"
```

`scene(document)` inside an app loads with the global registry, which is why this sample uses
`registerGlobal` instead of `registerControls()`.

| Helper in `scene { }` | System | Reads | Writes |
| --- | --- | --- | --- |
| `playerInputSystem()` | `PlayerInputSystem` | W A S D or the arrow keys, Space | `MovementControl.moveX`, `moveZ`, `jump` on every entity that has one |
| `matrixRelativeMovementSystem(speed = 5f)` | `MatrixRelativeMovementSystem` | `MovementControl`, the active camera's view | `Transform.position`, relative to where the camera looks |
| `cameraSystem()` | `CameraSystem` | mouse drag and scroll, the rig's target | the `Camera` lens of the entity tagged `ActiveCamera` |
| `cameraInputSystem()` | `CameraInputSystem` | F1, F2, F4, F5 | the active rig's `mode` |

All four are frame systems. The ones that read input get it with whatever the UI claimed removed, so
typing in a text field does not walk the player. With no `ActiveCamera`, movement treats -Z as
forward.

## Properties

### `camera`

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `eye` | vector | `(0, 0, 5)` | Where the camera is. |
| `center` | vector | `(0, 0, 0)` | The point it looks at. |
| `up` | vector | `(0, 1, 0)` | Which way is up. |
| `fovYDegrees` | number | `60` in a scene document; `45` from `Lens.perspective` | Vertical field of view, between 0 and 180. |
| `near` | number | `0.1` | Near clip distance, greater than 0. |
| `far` | number | `100` | Far clip distance, greater than `near`. |
| `primary` | boolean | `true` | Whether this camera renders the scene. |
| `projection` | `perspective` · `orthographic` | `perspective` | `orthographic` keeps things the same size at any distance and ignores `fovYDegrees`; use it for 2D. |
| `orthoHalfHeight` | number | about `2.07` | Half the vertical world extent an orthographic view covers; the width follows from the aspect ratio. Used only when `projection` is `orthographic`. |

### `camera_rig`

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `mode` | `FirstPerson` · `ThirdPerson` · `FreeFly` · `Cinematic` · `TopDown` | `ThirdPerson` in a scene document; `FirstPerson` from `camera()` | How the camera moves; see below. |
| `target` | node name | none | The node the camera follows. |
| `distance` | number | `5` | How far a third-person or top-down camera sits from its pivot. |
| `minDistance`, `maxDistance` | number | `2`, `20` | The range scrolling zooms within. `distance` must lie inside it. |
| `pitch`, `yaw` | radians | `0` | Starting angles. Negative pitch looks down. |
| `offset` | vector | `(0, 1.8, 0)` | Added to the target's position to get the pivot, or the pivot itself with no target. |
| `flySpeed` | number | `10` | Units per second for `FreeFly`. Greater than 0. |

In the scene DSL these are `CameraRig` fields; `target` is `targetEntity` and `offset` is
`offsetPosition`.

| Mode | Needs a target | What it does |
| --- | --- | --- |
| `FirstPerson` | yes | Eye at the target plus `offset`; dragging turns yaw and pitch. |
| `ThirdPerson` | no | Orbits the pivot at `distance`; dragging orbits, scrolling zooms. The eye eases toward its pose. |
| `FreeFly` | no | W A S D move along the view, Q and E down and up, Shift is faster; dragging looks around. |
| `Cinematic` | yes | Looks at the target from a fixed spot; no input. |
| `TopDown` | no | Looks down at 60 degrees from `distance`; dragging turns yaw, scrolling zooms. |

### `movement_control`

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `speed` | number or none | none | Units per second for this entity. None uses the movement system's speed. |

### `spin_control`

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `radians` | number | `0` | The current angle about Y. |
| `speed` | number | `1` | Turn rate, for the system that advances `radians`. Not negative. |

`SpinSystem` copies `radians` to `Transform.rotation.y`; it does not advance it. Advance it in your
own system, as [Scene DSL](scene-dsl.md#two-entry-points) shows.

## How it works

`CameraSystem` runs every frame. It reads the pointer drag and scroll, updates the rig's yaw, pitch
and distance, then writes the lens `eye` and `center` from the rig, the target's `Transform`, and
the mode. Only the entity tagged `ActiveCamera` takes input.

A rig's `target` is resolved after the whole document is loaded, so the camera may come before the
node it follows. A target name that no node has stops the load with an error.

!!! warning "Tag the active camera"
    Neither a scene document nor `camera()` adds `ActiveCamera`. Without it `CameraSystem` leaves the
    camera where it is. Add it in `onReady`, as above.

!!! warning "A DSL rig that sets `mode` resets its angles"
    Changing `CameraRig.mode` schedules a reset that replaces `pitch`, `yaw` and `distance` with the
    mode's defaults on the next `CameraSystem` update. `camera(mode = …)` changes the mode, so set
    `needsReset = false` in its block to keep angles you set. A loaded `camera_rig` already keeps
    them.

!!! warning "`MatrixRelativeMovementSystem` goes through walls"
    It writes `Transform.position` directly. For an entity that should collide, use a character
    controller instead, and don't run both on the same entity.

!!! tip "F2 does two things"
    With `cameraInputSystem()` registered, F2 switches the rig to `ThirdPerson`, and the scene runtime
    also uses F2 to toggle its frame timing stats. Pass your own `modeKeys` map to `CameraInputSystem`
    to move it.

## See also

- [Input](input.md) for reading keys and the pointer in your own systems.
- [Scene documents](scene-documents.md#components-from-other-modules) for registering components.
- [Character controller](character-controller.md) for movement that collides.
- [Scene document components](../reference/scene-document-components.md) for every component id.
