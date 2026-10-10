# `awake:scene:canvas`

Game UI stored in scenes: text, panels, bars and buttons pinned to the screen and drawn over the
game. It works like Unity's in-scene Canvas or Godot's `Control` nodes: each element is a component
on a scene entity, so it saves with the scene, loads with it, and is edited in Awake Studio's Scene
view like anything else.

Not to be confused with Compose's `Canvas` (a surface you draw pixels on) or
`awake:ui:node-graph-canvas` (the graph editor).

## An element

`CanvasElement` (saved as `canvas_element`) has:

- **`kind`**: `Text`, `Panel`, `Bar`, `Button`, `Joystick` or `Image`.
- **`anchor`**: one of nine screen points (`TopLeft` … `BottomRight`). The element sits against that
  point, and **`offsetX` / `offsetY`** move it inward: a right or bottom anchor moves it left or up.
- **`width` / `height`** in dp.
- **`text`** (Text content, Button label), **`fontSize`**, **`color`** (text, or a Bar's fill) and
  **`background`**, as `#RRGGBB` or `#RRGGBBAA`.
- **`value`**: a Bar's fill from 0 to 1.
- **`order`**: lower draws first. **`visible`** hides it.
- **`image`**: an Image's picture, the frame of a Panel, Button or Text, or a Bar's track; and
  **`fillImage`**, a Bar's fill, cut at `value`. A `CanvasImage` names a project file, the region of
  it to use and the slice insets that keep its corners whole.

## Drawing

`SceneAppLifecycleRuntime` draws every visible element with `SceneCanvas(world)` whenever the scene
has any, under the app's own `ui { }`, with the images in its `canvasImages`. A host with its own
Compose tree calls `SceneCanvas(world, images = …)` itself. `loadCanvasImages(scene, files)` reads and
decodes the images a scene names.

## Reacting to a button

```kotlin
if (world.get<CanvasElement>(jumpButton)?.consumePress() == true) jump()
```

`consumePress()` is true once per tap.

## Touch controls

A `Joystick` is a round pad of `width` across; dragging its knob sets `stickX` and `stickY` from -1 to
1, up being negative, and releasing centres it. A Button reports `isHeld` while pressed, as well as
taps through `consumePress()`. `action` names what an element does for the game, such as `move` or
`jump`, and a `touchOnly` element is drawn only when `SceneCanvas(world, showTouchControls = true)`,
or `SceneAppLifecycleRuntime.showTouchControls`, says a touch screen is in use.
