# `awake:scene:canvas`

The canvas element scene components: the schemas and validation for `awake:ui:canvas`'s canvas
components (`canvas_rect`, `canvas_text`, `canvas_appearance`, a widget such as `canvas_button`, and
the rest), so a HUD can be authored in a scene document, saved with it, and edited in Awake
Studio's Scene view like anything else. It works like Unity's in-scene Canvas or Godot's `Control`
nodes: each element is a component on a scene entity.

Not to be confused with Compose's `Canvas` (a surface you draw pixels on) or
`awake:ui:node-graph-canvas` (the graph editor). The element model, its styling and the drawing
itself have no scene or ECS dependency and live in `awake:ui:canvas`; see its README for what a
`CanvasElement` holds.

`canvas_appearance.style.gradient` uses `CanvasGradient` directly: `start` and `end` colours,
`horizontal` (false by default), and an optional `middle` colour at 50%. State styles (`hovered`,
`pressed`, `disabled`) use `CanvasStateStyle`, overriding paint, `shadow`, draw-only centre `scale`
and `alpha`, and interpolate over `style.transitionSeconds` or `canvas_controls.theme.transitionSeconds`
(snapping when `0` or unset). The older `canvas_element.style` form reads the same style; saving the
focused form preserves it, and default styles gain no JSON fields. Colours and theme tokens follow
the UI canvas contract.

## The mapping

Each canvas component has its saved form and a binding that copies its fields both ways, generated
from the `@SceneBinding` on the saved form: `CanvasRectBinding` loads a `canvas_rect` as a `CanvasRect`,
`CanvasButtonBinding` a `canvas_button` as a `CanvasButtonWidget`, and so on. The bindings of
`canvas_layout`, `canvas_cluster` and `canvas_bind` use `@SceneBinding(whole = "...")` to save their
live value under one field. Only the older `canvas_element` reader is written by hand and marked
`@RegisteredBinding`. The module's generated
`CanvasSceneComponents` lists them all, in binding-name order, which is also the order a node lists its
canvas components when saved; `DefaultSceneComponentResolvers` registers it. `CanvasBindingGoldenTest`
holds what each binding saves, recorded before they were generated. `CanvasComponentExposureTest`
fails when a component option is neither a field of its saved form nor listed as set from code only.
`CanvasPointerState`, what the player is doing to an element, has no saved form.

A canvas entity holds each component, and a `CanvasElement` made of the same instances, which is what
the overlay draws. `World.syncCanvasElements()`, which `SceneCanvas` calls each frame, keeps the two
in step: a component added, replaced or removed on the entity reaches the element, and one changed
through the element reaches the entity. A node loaded in the current form becomes an element at the
next sync; `canvasElements`, `findCanvasElement` and part resolution sync first, and a world export
(`SceneLoader.fromWorld`) syncs before it saves. An entity given a `CanvasRect` becomes an element; one whose
`CanvasRect` is removed is one no more. `World.addCanvasElement` and `removeCanvasElement` do either at once.

### The older `canvas_element`

Scenes and prefabs saved before the split hold one `canvas_element` for each element. `CanvasElementBinding`
still reads it, as the component set `SceneCanvasElement.toCanvasComponents()` gives, each attached
through its own binding, so it loads and draws as before; `CanvasSavedFormBaselineTest` holds one
baseline for a HUD in both forms and as a world that loaded it saves it back. A world never saves a
`canvas_element`. `SceneDocument.withCanvasComponents()` and `ScenePrefab.withCanvasComponents()`
rewrite a file in the current form, and `SceneNode.canvasElementView()` reads a node in either form
as the single record a tool written for it expects. `CanvasElementExposureTest` still checks the older
mapping covers every option of a `CanvasElement`.

## Prefabs in code

`canvasPrefab(guid, name) { ... }` builds a `ScenePrefab` of canvas element nodes, with a typed setting
for each field and a function for each kind, saves each element as its canvas components, and
validates it, reporting a setting its kind does not use; `CanvasPrefabs.toJson` writes the file
an editor saves, which loads and saves back unchanged. `CanvasPrefabBuilderCoverageTest` fails when a
`canvas_element` field has no builder setting. An element's `elementId` is the stable id code finds it
by: `World.findCanvasElement(root, id)` under one placed copy, `ScenePrefab.findCanvasNode(id)` in the
prefab.

Code that both builds a prefab and binds it declares its elements once as typed handles instead:
a `CanvasWidgetParts` with a part per element (`CanvasImagePart`, `CanvasBarPart`, ...), groups and
`list`s of parts for repeated ones, and ids composed from the declaration (`bar.slot-3.icon`) or
pinned to one a prefab already saves. The builder's functions take the handles and write the same
string ids, so the saved prefab is unchanged; `resolve(world, root)` binds one placed copy in a single
pass over the world, or throws `CanvasPartResolutionException` naming each missing part, and each
handle then writes only its kind's fields on the cached element.

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
it calls equal keep the world's order. `world.canvasDrawOrder(siblingOrder)` lists the entities in
the order they draw, and `world.canvasElementAt(x, y, siblingOrder)` finds the one last drawn on top
at a point in the canvas's units, as an editor's click picks it.

## Many followers

`SceneCanvasResolver` reports a follower's projection through `CanvasProjector.projectFollow`, which
`SceneAppLifecycleRuntime`'s projector answers from the camera it draws with: the point through the
backend's clip space, the depth along the view, the distance, and for a point behind the camera the
side it lies on. `CanvasProjector.occluded(target, x, y, z)` asks the world's `CanvasOcclusion`
(`world.setCanvasOcclusion`) from the camera's eye, with the followed node, so its own body is left
out; without one nothing is occluded. A played project sets one that casts physics rays
(`PhysicsCanvasOcclusion` in `awake:project:runtime`), within the budget of the scene's
`canvas_follow_occlusion` (`SceneCanvasFollowOcclusion`, saved from `CanvasFollowOcclusion`). This
module depends on no physics module.
A scene's `canvas_follow_limit`s (`SceneCanvasFollowLimit`, saved from `CanvasFollowLimit`) reach
`SceneCanvas` through `world.canvasFollowLimits()`. `CanvasFollowerPool(world, template)` hands
follower entities out and takes them back, hidden, so churning followers makes no entities.

## Scale and safe area

A `canvas_scaler` on any node, the saved form of `awake:ui:canvas`'s `CanvasScaler`, works a scale
out from the screen's size. With `scope = Canvas`, the default, `SceneCanvas(world)` finds it with
`world.canvasScaler()` and draws the whole canvas at it in place of `scale`, and a scene without one
draws at `scale` as before. With `scope = Subtree`, the scaler applies only to the node it sits on
(whether a canvas element or a plain grouping node) and the nodes beneath it, worked out from the
canvas's box, while the rest of the canvas keeps its own scale; a nested `Subtree` scaler overrides an
outer one for its own branch. `CanvasScalerBinding` loads and saves it, with its `snap` (`None`,
`WholeStep`, `HalfStep`) and `scope` (`Canvas`, `Subtree`).
`SceneCanvas(world, safeArea = …, dpi = …)` keeps anchored elements clear of a notch, which an
element's `safeArea` field opts out of, and gives a `ConstantPhysicalSize` scaler the screen's real dots per inch. `SceneAppLifecycleRuntime` passes
what its platform reports with each frame, `AppFrame.safeArea` and `AppFrame.dpi`, unless a host
sets `canvasSafeArea` or `canvasDpi` itself.

## Text in the player's language

An element with a `textKey` shows that key's text from the `Strings` passed as
`SceneCanvas(world, strings = …)`, with its `textArgs` and its `bind`'s `textArgs`, which read
fields the game sets, in its placeholders. `SceneAppLifecycleRuntime` passes its `canvasStrings`,
which `runProject` sets to the project's `strings/` tables. `CanvasElementBinding` carries both
fields to and from the scene file; a scene saved before them loads as literal text.

A TextField's `placeholder` and any element's `tooltip` are keys into the same tables, with the same
arguments. `padding` (`horizontal` 12, `vertical` 0) is a TextField's inner space; the three load
with those defaults from a scene saved before them. Validation reports `placeholder` and `padding` on
another kind, a negative or non-finite padding, and a blank key.

## A 3D view and following a node

A `View` shows what the camera on the node its `camera` names sees; `SceneCanvas(world, views = …)`
takes the `CanvasViews` that renders each picture, and `SceneAppLifecycleRuntime` passes its own,
which renders every View drawn this frame into a target the size of its box before the UI samples
it, and `canvasViews(world)` hands it to a host. An element that follows a node is placed by a
`CanvasProjector`, which reports where the node lands in the frame's pixels; `SceneCanvasResolver`
is how `CanvasOverlay` reaches both without knowing what a `World`, a node or a camera is.

For a portrait over a translucent or gradient frame, set the focused `canvas_view` component's
`settings` to `CanvasView(transparent = true)`, or `view = CanvasView(...)` in the prefab
builder. The runtime clears that camera target to transparent and omits its sky; geometry,
lighting and fog remain, and the coverage survives a multisampled plan's resolve. A View draws its
camera's scene pass without the camera's post-processing stack. A `mask = CanvasMask.Circle` clips just the picture; the default
`Box` mask follows `style.cornerRadius`. Explicit ancestor clips are retained, and the target
still uses the element's scaled pixel size and safe-area placement. The default stays opaque.
These settings round-trip through the focused scene binding and normal world export; the
retired `canvas_element` snapshot helper represents only its older fields.

## Reacting to a button

```kotlin
if (world.get<CanvasElement>(jumpButton)?.consumePress() == true) jump()
```

See `awake:ui:canvas`'s README for `consumePress()`, `presses`, touch controls and nesting; they
are the same whether an element comes from a scene or from a host's own list.
