# Blueprint runtime plan

Date: 2026-09-27
Status: **stub** — records the current state and the prerequisites. The design is settled in its
own discussion, which has not happened yet. Consumer 1 of the
[node-graph plan](2026-09-27-node-graph-plan.md).

## Goal

Event-driven game logic authored as node graphs. For example: player enters a trigger → open the
door → wait 2 s → close it.

- **Coders** write nodes in Kotlin.
- **Non-coders** wire them in Studio.
- **An LLM**, as a planned future extension, composes the same graphs from the same catalogue.

AI decision-making (state trees) is a separate runtime and a separate plan. It shares the graph
model, registry and canvas.

## Direction so far

These points came out of the node-graph discussion. Any of them can be reopened:

- **Graphs are data interpreted by a Core runtime, never generated Kotlin.**
  - The web and iOS builds cannot load code.
  - Studio can reload a graph into a running game.
  - LLM output stays inside what the validator accepts.
- **Explicit node registry, no reflection scanning.** It is the Studio palette, the coder API, and
  later the LLM schema. An unknown node type rejects the whole graph.
- **Runs in the fixed-step phase.** The logic is deterministic, and can run on a game server.
- **Where nodes live.** Engine nodes (flow, math, entity, physics, animation, audio, input) live
  in Core. Game nodes such as `DealDamage` stay in the game repository.
- **Boundary.** Putting the runtime in Core needs the exception record that
  [framework-game-boundary](../reference/framework-game-boundary.md) asks for. The consumers are
  Studio and the private consumer pack.

## Current state

### Seams that exist

- **Scheduling.**
  - `fixedSystem`/`frameSystem` register systems in order (`scene/authoring` `SceneAppDsl`).
  - `SceneSchedule` runs Fixed inside `FixedTimestepLoop`: a 1/60 s step, at most 5 steps per
    frame.
  - The system set is fixed at `initialize`.
- **Scene data.**
  - `SceneComponent` is an open polymorphic interface, not a sealed one.
  - `SceneComponentBinding` attaches and exports components.
  - `SceneComponentRegistry.registerGlobal` is the extension point for kit modules.
  - `SceneCustomComponent(type, payload: JsonElement)` is the fallback that needs no schema.
- **Entity references.** `SceneResolutionContext.deferNodeLink(name)` resolves a node name to an
  entity after load. `ChaseBinding` and `FleeBinding` use it. Node names are unique per document.
- **Names.** Every named node carries a `Name` component.
- **Physics triggers.**
  - Sensors, plus `PhysicsWorld.drainContacts`, which reports BEGAN/ENDED events per sub-shape
    pair.
  - `setContactReporting` opts a solid body in.
- **Animation.** `Animator.player.play(clipId)` and `crossFadeTo(clipId, seconds)`.
- **Audio.** `AudioPlayer.playSound` / `playSound3D`, taking a decoded clip.
- **Entities.**
  - `world.create()` and `world.destroy(entity)`.
  - `SceneLoader.instantiate(document, world, registry)` loads a sub-scene into a live world, and
    `Scene.destroy()` removes it.
- **Input.** Raw `InputSnapshot`, read through `GameplayInput` so UI ownership is respected.
  `KeybindingProfile<A>` maps typed actions to keys, but only tests use it.

### Prerequisites

Each item was checked in code on 2026-09-27. None is a defect today; each becomes necessary once
blueprints are a second consumer. Each lands as its own PR at the bottom of the blueprint stack.

1. **Contact fan-out and a public body-to-entity lookup.**
   - `drainContacts` hands events to one caller and forgets them. The showcase's goal-zone system
     is the only drainer, and its comments note that a second caller would see nothing.
   - `PhysicsSystem.handleToEntity` is private, so the sample finds an entity with a linear scan
     over every `PhysicsBody`.
2. **A component registry for `SceneManager`.**
   - `SceneManager.switchTo` instantiates with the default registry, which holds globally
     registered components only.
   - `registerAiBehaviors()` registers on a registry instance, so `patrol`/`chase`/`flee` scenes
     loaded through `SceneManager` need the game to register those bindings globally itself.
   - Blueprint components would hit the same gap.
3. **Entity lookup by name at runtime.** There is no name index. Lookup today is a
   `queryEach<Name>` scan or a walk over `Scene.roots`.

### Gaps to close only when a blueprint feature needs them

- An event or message channel between ECS systems. None exists; systems talk through shared
  components or direct references.
- Named, data-driven input actions that respect UI ownership. Also, the input snapshot is taken
  once per frame while Fixed runs 0–5 times per frame, so a "pressed" edge read in Fixed can be
  missed or seen twice.
- Playing a sound on demand by asset id. `AudioSource` holds a decoded clip and auto-plays once,
  and `AudioSystem` is not registered in any main code.
- Prefab expansion. `ScenePrefab`/`ScenePrefabLink` are data only, and `PrefabLinkBinding` does
  nothing.
- `scene.json` variants for physics bodies, audio and the animator.
- Adding or removing systems at runtime.
- Tolerance for unknown component discriminators. An unregistered `"component"` fails to decode;
  this is inferred, not tested.

### Verified non-issues

- **Destroying during `queryEach` is invalid by design,** as stated in `Family1Cache` and the ECS
  skill: collect first, then act. The runtime queues its own structural changes and applies them
  after its loop. Enforcing the rule inside the ECS would be a hot-path change, gated on the ECS
  benchmark rules.

## Suggested v1 slice

A sensor-triggered door. It needs:
- prerequisite 1 (the trigger);
- prerequisite 3 (finding the door by name);
- animation play, which exists;
- a delay node, which is part of the runtime.

Input, audio, prefabs and the event channel are not needed.

## Questions for the design discussion

1. **Execution model.** Execution wires plus data wires, or pure data flow with events? Data is
   pulled lazily or computed eagerly?
2. **Instance state.** Per-entity variables, and their layout. The tick allocates nothing, which
   is the bar the AI plan sets.
3. **Latent nodes** (`Delay`, `MoveTo`). How execution suspends and resumes across ticks, and
   whether this shares a mechanism with the AI plan's long-running tasks.
4. **Reload.** What happens to running instances when Studio replaces a graph: restart, or keep
   the variables that still match?
5. **Node registration API.** How a coder declares ports, config and docs in Kotlin.
6. **Debugging.** Live execution highlighting on the canvas, variable watch, breakpoints.
7. **Server and networking.** What runs where in a server-authoritative game.
