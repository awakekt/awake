# Blueprint runtime plan

Date: 2026-09-27
Status: **active** — phases 0 and 1 are done (#125, #126, #128, #129); phase 2 is
[#123](https://github.com/awakekt/awake/issues/123). Consumer 1 of the
[node-graph plan](2026-09-27-node-graph-plan.md).

## Goal

Event-driven game logic authored as node graphs, edited in Awake Studio and reloaded into the
running game. For example: player enters a trigger → open the door → wait 2 s → close it.

- **Coders** write node types in Kotlin.
- **Non-coders** wire those nodes in Studio, with no code.
- **An LLM**, as a planned future extension, composes the same graphs from the same catalogue.

Blueprints run inside ECS. One `BlueprintSystem` runs the graph of every entity that carries a
`BlueprintComponent`, and nodes act through the same components Kotlin systems use. Heavy
per-frame work over many entities stays in Kotlin systems; blueprints are for event-driven,
per-entity logic.

AI decision-making (state trees) is a separate runtime and a separate plan. It shares the graph
model, registry, canvas and the latent-action mechanism below.

## Decisions

1. **Execution model: execution wires plus data wires.**
   - Execution wires say what happens next.
   - Data wires carry values. A data input is read when the node that needs it runs. A pure node
     upstream is evaluated then, once per execution step. An event or action output upstream
     reports the value that node stored when it ran.
   - An input with no wire uses the value set on the node (`GraphNode.config`).
2. **Per-entity state: a compiled program plus one component.**
   - A graph compiles once per asset into a `BlueprintProgram`: nodes by index, their executors,
     resolved input sources and execution successors.
   - Each entity holds a `BlueprintComponent` with typed value slots (`FloatArray`, `IntArray`,
     `Array<Any?>`) for node outputs and variables, plus its pending latent actions.
   - Running a graph at rest allocates nothing.
3. **Long-running nodes: latent actions.**
   - A latent node (`Delay`, later `MoveTo`) suspends execution. The runtime records it in the
     instance's pending list and polls it every tick until it reports done, then continues from
     its output. Pending actions can be cancelled.
   - Latent state lives in the instance's slots, not in objects, so waiting allocates nothing.
   - No coroutines: they allocate, are hard to keep deterministic, and cannot be saved.
   - AI state trees use the same mechanism for their long-running tasks.
4. **Live reload.** When Studio replaces a graph, the program is recompiled. Running instances keep
   variables whose name and type still exist, cancel their pending latent actions, and do not fire
   `On Start` again.
5. **Node API: plain classes.** A node type is one Kotlin object with its `NodeSpec` beside its
   behaviour, so the palette entry and the code cannot drift apart.

   ```kotlin
   object PlaySound : ActionNode {
       override val spec = NodeSpec(
           type = "audio.play",
           displayName = "Play Sound",
           category = "Audio",
           inputs = listOf(PortSpec("exec", EXEC, multiple = true), PortSpec("clip", ASSET)),
           outputs = listOf(PortSpec("then", EXEC)),
       )
       override val effect = Effect.Presentation
       override fun run(ctx: BlueprintContext): Step {
           ctx.audio.play(ctx.asset("clip"))
           return Step.Continue("then")
       }
   }
   ```

   - The shapes are `EventNode` (starts a chain), `ActionNode` (runs, then continues), `PureNode`
     (computes outputs on demand) and `LatentNode` (an action that suspends).
   - Port types are a closed set: `exec`, `bool`, `int`, `float`, `string`, `vec3`, `entity`,
     `asset`.
   - A graph naming a port its node does not declare is refused at load by the `:awake:node-graph`
     validator.
   - A Kotlin DSL can come later as sugar over these interfaces, if writing nodes gets repetitive.
6. **Debugging.** A per-instance trace of executed node indices, off unless a debugger asks for it,
   maps back to node ids. That drives the canvas's highlight channel. Variables can be read from the
   instance. Breakpoints come later.
7. **Server and networking.** Every node type declares an `Effect`: `Logic` (changes game state) or
   `Presentation` (sound, effects, UI). The runtime can be told to skip presentation nodes, which
   then pass execution straight through. v1 is single-player; the marker exists so that a server
   later runs only logic, and no node has to be revisited.

## Design

### Modules

| Module | Holds | Depends on |
|---|---|---|
| `:awake:blueprint` | node interfaces, `BlueprintNodes` registry, compiler, interpreter, instance state, latent actions, reload, trace | `:awake:node-graph`, `:awake:ecs` |
| `:awake:scene:blueprint` | `BlueprintComponent` scene binding, `BlueprintSystem`, engine nodes (events, flow, entity, animation, variables, math) | `:awake:blueprint`, scene-core, scene binding, scene physics, scene3d |

- The runtime has its own API independent of the scene, so it is a top-level module. The ECS glue
  and the nodes that reach into scene components are the scene-binding layer, per
  `awake-framework-boundary`.
- Game-specific nodes live in the game. Nothing in Core depends back on a game.

### Graph kind

`awake.logic.event-graph`: no cycles in data wires; execution wires may loop back only through a
latent node. The kind's `canConnect` allows equal types only, with `exec` never connecting to data.

### Boundary exception record

Required by [framework-game-boundary](../reference/framework-game-boundary.md):

- **Consumers.** Studio templates, and the private consumer pack's world logic.
- **Missing API.** A shipped game cannot run logic that was authored as data. Today, logic Studio
  authors would need Studio code at runtime.
- **Contract.** The graph kind, the node interfaces, `BlueprintComponent` and the `blueprint`
  scene component.
- **Excluded policy.** Game vocabulary: combat, quests, inventory and economy nodes stay in games.
- **Dependency direction.** Game → `:awake:scene:blueprint` → `:awake:blueprint` →
  `:awake:node-graph`, `:awake:ecs`. Nothing points back.
- **Validation.** Runtime tests with test nodes, and the door sample running headless.

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

## Phases

### Phase 0 — prerequisites

Three small PRs, each useful on its own:

1. Contact fan-out and a public body-to-entity lookup.
2. A component registry for `SceneManager`.
3. Entity lookup by name.

**Gate:** each ships with tests on its own terms; see "Prerequisites" above.

### Phase 1 — `:awake:blueprint` runtime

Compiler, interpreter, instance state, latent actions (`Delay`), variables, reload, trace and the
effect skip, plus the scene-free core nodes: `On Start`, `Branch`, `Delay`, `Add`, `Greater` and
typed variable get/set. There is no sequence node, because wiring one execution output to several
nodes runs them in wire order. Exercised with test-only nodes, with no scene.

**Gate:**
- Execution follows execution wires; data is read at the node that needs it; a pure node runs
  once per step.
- `Delay` suspends and resumes on the right tick, and a cancelled one never resumes.
- Reload keeps matching variables, drops removed ones, cancels waits and does not refire
  `On Start`.
- With presentation skipped, presentation nodes pass execution through untouched.
- The trace lists executed nodes in order.
- An allocation probe shows a graph at rest allocates nothing.

### Phase 2 — `:awake:scene:blueprint`

`BlueprintComponent`, the `blueprint` scene component (graph path plus variable overrides),
`BlueprintSystem` in the fixed phase, and the v1 engine nodes:

- events: `On Sensor Enter`, `On Sensor Exit`;
- entity: `Self`, `Find By Name`, `Destroy`;
- animation: `Play Animation`.

Flow, variable and math nodes already came with phase 1.

**Gate:** the door sample. A headless test drives a body into a sensor, and the door's animator
plays `open`, then `close` 2 s later. A positive control with the wire removed fails.

### Phase 3 — Studio blueprint editor (awake-pro)

Its own plan: a workspace on the canvas, the palette from the registry's catalogue, live reload
into the preview, and the trace driving highlights.
