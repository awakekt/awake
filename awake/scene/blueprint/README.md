# `awake:scene:blueprint`

Runs [blueprints](../../blueprint/README.md) on scene entities: the `blueprint` scene component,
`BlueprintSystem`, and the engine nodes that act on the scene.

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

A `target` input with nothing wired to it means the blueprint's own entity.

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
