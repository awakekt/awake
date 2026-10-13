# `awake:scene:blueprint`

Runs [blueprints](../../blueprint/README.md) on scene entities: the `blueprint` scene component,
`BlueprintSystem`, and the engine nodes that act on the scene and its game UI.

## Setup

```kotlin
val physics = PhysicsSystem(physicsWorld)
val blueprints = BlueprintSystem(graphs = { path -> NodeGraphJson.decode(readText(path)) }, physics = physics)
blueprints.nodes.register(MyGameNode(blueprints.scene))   // game nodes, before the first update

fixedSystem("physics") { physics }
fixedSystem("blueprints") { blueprints }                  // after physics: it reads the step's contacts
```

- `graphs` is called once per path; the compiled program is shared by every entity using it.
- Without `physics`, sensor events never fire and `Destroy` removes entities only.
- `BlueprintSystem(runPresentation = false)` skips presentation nodes, for a server.
- `blueprints.reload(path, graph)` swaps an edited graph into running instances. Variables whose name
  and type still exist keep their values, waits are cancelled, and On Start does not fire again.

## Scene data

```json
{ "component": "blueprint", "graph": "door.graph.json", "variables": { "delay": 2.5 } }
```

Register it with `SceneComponentRegistry().registerBlueprints()`. The entity gets a
`BlueprintComponent`; `BlueprintSystem` starts it on its next step, setting `variables` before On Start
fires. Entity variables cannot be set from JSON.

## Nodes

| Node | Type | Does |
|---|---|---|
| On Sensor Enter / On Sensor Exit | `event.sensor.enter` / `event.sensor.exit` | Fires on a sensor's entity; `other` is the body's entity, or none for a body no entity owns |
| Self | `entity.self` | The blueprint's own entity |
| Find By Name | `entity.find-by-name` | The live entity with that `Name`, or none |
| Destroy | `entity.destroy` | Frees the target's physics body, then destroys it |
| Load Scene | `scene.load` | Asks the game to play the scene at `scene`, a path relative to the project root, in place of this one; see [Switching scenes](#switching-scenes) |
| Play Animation | `animation.play` | Plays `clip` on the target's `Animator`, once unless `loop`; a missing animator or clip is skipped. Presentation |
| On Button Pressed | `event.button.pressed` | Fires once for each press of the Button whose `Name` is `name`, or of the blueprint's own Button when `name` is empty; `element` is the pressed element's entity |
| Set Text | `ui.set-text` | Sets a canvas element's `text`: a Text's words, a Button's label, a TextField's content. Presentation |
| Set Visible | `ui.set-visible` | Shows or hides a canvas element and what is inside it. Presentation |

A `target` input with nothing wired to it means the blueprint's own entity. A wired `target` that
gives no entity, such as a Find By Name that found no one, makes the node do nothing: it never falls
back to the blueprint's own entity, and it warns once rather than every time it runs.

## Game UI

The canvas nodes act on the scene's `canvas_element`s ([`awake:scene:canvas`](../canvas/README.md)),
so a menu or a HUD works without code:

```text
On Button Pressed (name: Play) -> Set Text (name: Title, text: Loading) -> Set Visible (name: Window, visible: false)
```

- **Which element.** Set Text and Set Visible act on the `target` wired to them, else the element
  whose `Name` is `name`, else the blueprint's own entity's. A wired `target` that gives no entity,
  and a name that matches no element, are skipped. On Button Pressed listens to its `name` the same way, or to its own entity's Button.
- **Presses.** Each fixed step, after the sensor events, On Button Pressed fires once for each press
  since the last step, however many steps a frame runs. It counts `CanvasElement.presses` and takes
  nothing: a Button's `action`, or a game system calling `consumePress()`, sees the press as it would
  without the blueprint.

Nodes that reach the world take `BlueprintSystem.scene`, which gives `world`, `names` and a
body-first `destroy`. Game nodes use it the same way.

## Switching scenes

Load Scene goes from a menu to a level, or from one level to the next, without code:

```text
On Sensor Enter -> Load Scene (scene: scenes/level-2.scene.json)
```

It asks through `BlueprintScene.loadScene`, which the host that runs `BlueprintSystem` sets. In a
project played by `runProject`, `BlueprintCapability` sets it to the project's scene switch: the next
scene loads while this one plays on, then replaces it at the start of a later step, and nothing of
this scene carries across ([`awake:project:runtime`](../../project/runtime/README.md#switching-to-another-scene)).
A scene that can't be loaded is logged and this one goes on. Where nothing switches scenes, such as
an editor playing the one scene it edits, `loadScene` is null and Load Scene logs a warning, once, and
carries on to `then`, as it does for an empty `scene`. A host of its own sets it to whatever plays
another scene for it.

`LoadSceneNodeTest` checks the request and the warning. In `awake:project:blueprint`, `LoadSceneTest`
plays a project whose menu's blueprint loads the level, and the level's own blueprint runs.

## Verification

`DoorSampleTest` is the headless gate. A box falls into a door's sensor on real Jolt, and the door
plays `open`, then `close` 2 s later. A copy of the graph without the wire after the wait fails the
same check.

`CanvasNodesTest` taps Buttons through a real `SceneCanvas` frame: pressing Play sets a Text and hides
a Panel, and the Button keeps its press for its other readers. In `awake:project:blueprint`,
`ButtonActionTest` plays a project whose Button has an `action` and a blueprint listening to it, and
one press reaches both, whichever system runs first.
