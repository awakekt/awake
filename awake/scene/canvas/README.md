# `awake:scene:canvas`

The `canvas_element` scene component: the schema and validation for `awake:ui:canvas`'s
`CanvasElement`, so a HUD can be authored in a scene document, saved with it, and edited in Awake
Studio's Scene view like anything else. It works like Unity's in-scene Canvas or Godot's `Control`
nodes: each element is a component on a scene entity.

Not to be confused with Compose's `Canvas` (a surface you draw pixels on) or
`awake:ui:node-graph-canvas` (the graph editor). The element model, its styling and the drawing
itself have no scene or ECS dependency and live in `awake:ui:canvas`; see its README for what a
`CanvasElement` holds.

## The mapping

`CanvasElementBinding` loads a scene's `canvas_element` as a live `CanvasElement` component, and
exports it back; `CanvasElementBinding.kt` is the one place a `SceneCanvasElement` field and a
`CanvasElement` option meet, and `CanvasElementExposureTest` fails when a `CanvasElement` option is
neither carried across there nor listed as set from code only.

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

`SceneCanvas(world, …)` walks the world for `CanvasElement`s and their `Transform` parents, and
answers a `CanvasResolver` built from it (`SceneCanvasResolver`) to `awake:ui:canvas`'s
`CanvasOverlay`, which does the actual drawing.

Elements of the same `order` draw in hierarchy order: a node before the nodes under it, and an
earlier sibling, with everything under it, before a later one, so the later one is on top and takes
the pointer. Siblings are in the order the world stores their `Transform`s, which is the order the
scene saves them in and, for a loaded scene, the order its document lists them. An editor whose
hierarchy can move siblings passes its own order as `SceneCanvas(world, siblingOrder = …)`; siblings
it calls equal keep the world's order.

## A 3D view and following a node

A `View` shows what the camera on the node its `camera` names sees; `SceneCanvas(world, views = …)`
takes the `CanvasViews` that renders each picture, and `SceneAppLifecycleRuntime` passes its own,
which renders every View drawn this frame into a target the size of its box before the UI samples
it, and `canvasViews(world)` hands it to a host. An element that follows a node is placed by a
`CanvasProjector`, which reports where the node lands in the frame's pixels; `SceneCanvasResolver`
is how `CanvasOverlay` reaches both without knowing what a `World`, a node or a camera is.

## Reacting to a button

```kotlin
if (world.get<CanvasElement>(jumpButton)?.consumePress() == true) jump()
```

See `awake:ui:canvas`'s README for `consumePress()`, `presses`, touch controls and nesting; they
are the same whether an element comes from a scene or from a host's own list.
