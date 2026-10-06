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
| Play Animation | `animation.play` | Plays `clip` on the target's `Animator`, once unless `loop`; a missing animator or clip is skipped. Presentation |

A `target` input with nothing wired to it means the blueprint's own entity.

Nodes that reach the world take `BlueprintSystem.scene`, which gives `world`, `names` and a
body-first `destroy`. Game nodes use it the same way.

## Verification

`DoorSampleTest` is the headless gate. A box falls into a door's sensor on real Jolt, and the door
plays `open`, then `close` 2 s later. A copy of the graph without the wire after the wait fails the
same check.
