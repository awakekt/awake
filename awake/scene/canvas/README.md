# `awake:scene:canvas`

Game UI stored in scenes: text, panels, bars and buttons pinned to the screen and drawn over the
game. It works like Unity's in-scene Canvas or Godot's `Control` nodes: each element is a component
on a scene entity, so it saves with the scene, loads with it, and is edited in Awake Studio's Scene
view like anything else.

Not to be confused with Compose's `Canvas` (a surface you draw pixels on) or
`awake:ui:node-graph-canvas` (the graph editor).

## An element

`CanvasElement` (saved as `canvas_element`) has:

- **`kind`**: `Text`, `Panel`, `Bar`, `Button`, `TextField`, `Joystick`, `Image` or `View`.
- **`anchor`**: one of nine screen points (`TopLeft` … `BottomRight`). The element sits against that
  point, and **`offsetX` / `offsetY`** move it inward: a right or bottom anchor moves it left or up,
  and a negative offset at an edge is held there. On a centred axis an offset moves it right or down
  of centre, and a negative one left or up.
- **`width` / `height`** in dp.
- **`text`** (Text content, Button label), **`fontSize`**, **`color`** (text, or a Bar's fill) and
  **`background`**, as `#RRGGBB` or `#RRGGBBAA`.
- **`font`**: a project `.ttf` or `.otf` file, or a built-in family, `default` or `bitmap`; empty
  is the default Roboto.
- **`value`**: a Bar's fill from 0 to 1.
- **`order`**: lower draws first. **`visible`** hides it.
- **`style`**: how it looks beyond its colours, as the saved form of a Compose `Style` drawn
  through `Modifier.styleable`: fill, gradient, corner radius, border, shadow, text colour, alpha,
  `hovered` and `pressed` states for a Button, and images. `style.image` is an Image's picture, the
  frame of a Panel, Button or Text, or a Bar's track; `style.fillImage` is a Bar's fill, cut at
  `value`, or with `style.fillImageMode` `Squeeze` drawn at the filled width with both end caps kept.
  A `CanvasImage` names a project file, the region of it to use and the slice insets that
  keep its corners whole.

## Drawing

`SceneAppLifecycleRuntime` draws every visible element with `SceneCanvas(world)` whenever the scene
has any, under the app's own `ui { }`, with the images in its `canvasImages`. A host with its own
Compose tree calls `SceneCanvas(world, images = …)` itself. A host that shows the game in part of its
window, such as an editor's game view, turns the runtime's `drawsSceneCanvas` off and draws
`SceneCanvas(world, projector = runtime.canvasProjector(world), views = runtime.canvasViews(world))`
there: the runtime's own projector and views, for its world or for another its render system draws,
such as a Play world. The runtime renders and frees the views' pictures; the host only passes them on. `loadCanvasImages(scene, files)` reads and
decodes the images a scene names. A host that draws nothing, such as a game server, calls
`checkCanvasImages(scene, files)` instead: it reads them, logs those it cannot, and decodes none.
`loadCanvasFonts(scene, files)` loads the font files a scene names, which the runtime keeps in its
`canvasFonts` and a host passes as `SceneCanvas(world, fonts = …)`. One that does not load is logged
once and its elements draw in the default font. A host that draws nothing loads none.

## A 3D view

A `View` shows what the camera on the node its `camera` names sees, filling its box under its
children. `SceneCanvas(world, views = …)` takes the `CanvasViews` that renders each picture;
`SceneAppLifecycleRuntime` passes its own, which renders every View drawn this frame into a target
the size of its box before the UI samples it, and `canvasViews(world)` hands it to a host. Without
one a View draws only its style.

An element that follows a node is placed by a `CanvasProjector`, which reports where the node lands in
the frame's pixels. `SceneCanvas` moves that into its own box by where the box sat in the frame when
it was last placed, so a canvas drawn over part of the window lands on what it follows from its
second frame; one filling the window does from its first.

## Nesting and scale

An element below another element in the scene hierarchy is drawn inside it, anchored and offset
within the parent's box, and moves and hides with it. `SceneCanvas(world, scale = 2f)`, or
`SceneAppLifecycleRuntime.canvasScale`, draws everything twice the size, with every edge on a whole
pixel.

## Reacting to a button

```kotlin
if (world.get<CanvasElement>(jumpButton)?.consumePress() == true) jump()
```

`consumePress()` is true once per tap. A tap goes to the topmost element under the pointer that handles
it: a Panel, Text or Image drawn over a Button does not take the Button's taps, and elements anchored
at the same corner with different offsets do not cover one another.

`consumePress()` takes the tap, so of two readers only the first sees it. A reader that shares the
Button with another counts `presses` instead, the number of taps since the element was made: it keeps
the count it saw last, and takes nothing. A blueprint's On Button Pressed reads it this way, so a
Button's `action` and a blueprint both react to one tap.

## Touch controls

A `Joystick` is a round pad of `width` across; dragging its knob sets `stickX` and `stickY` from -1 to
1, up being negative, and releasing centres it. A Button reports `isHeld` while pressed, as well as
taps through `consumePress()`. `action` names what an element does for the game, such as `move` or
`jump`, and a `touchOnly` element is drawn only when `SceneCanvas(world, showTouchControls = true)`,
or `SceneAppLifecycleRuntime.showTouchControls`, says a touch screen is in use.
