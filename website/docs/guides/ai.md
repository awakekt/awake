# AI

<p class="awake-lede">Guards that patrol, hounds that chase and deer that flee, walking real routes over a navigation grid, plus behaviour trees and state machines for your own logic.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">component: <code>patrol</code></span>
<span class="awake-badge">component: <code>chase</code></span>
<span class="awake-badge">component: <code>flee</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

Two modules. `awake:ai:behavior` has the ready-made `patrol`, `chase` and `flee` behaviours, which
walk routes from [navigation](navigation.md). `awake:ai` has behaviour trees and finite-state
machines for logic you write yourself.

## Add patrol, chase and flee

A guard walking a beat, a hound chasing the player and a deer running from it. Both forms below build
the same components.

=== "Scene document"

    ```json title="guards.scene.json"
    --8<-- "website/docs/snippets/world/guards.scene.json"
    ```

    Register the bindings. Each behaviour arrives with the `PathRequest` it asks for routes through.
    The behaviours route over a grid, which the scene carries as a `navigation` component:

    ```json title="navigation component"
    { "component": "navigation", "cellSize": 1.0,
      "rows": [ "......", ".####.", "......" ] }
    ```

    `.` is a cell an agent can stand on and `#` one it cannot. Played through `loadPlayableProject`
    and `playProject`, a scene with a behaviour and a `navigation` component runs the behaviours and
    answers their routes, with no AI code in the host; a project with a behaviour and no grid is
    refused at load.

    ```kotlin title="Kotlin"
    --8<-- "awake/ai/behavior/src/desktopTest/kotlin/com/awakekt/awake/ai/behavior/AiDocsSampleTest.kt:load"
    ```

=== "Scene DSL"

    ```kotlin title="Kotlin"
    --8<-- "awake/ai/behavior/src/desktopTest/kotlin/com/awakekt/awake/ai/behavior/AiDocsSampleTest.kt:behaviours-dsl"
    ```

    There are no dedicated DSL functions for behaviours yet, so attach them with `with(...)`.

=== "Studio"

    AwakeKt Studio has no **Add component** entry for these behaviours and does not register their
    scene bindings, so author them in the scene document or in Kotlin. For a behaviour already on
    an entity, the **Inspector** shows a **Patrol**, **Chase** or **Flee** section with its
    tuning fields.

## Run the behaviours

Each behaviour has a system that decides where to go and writes a `PathRequest`;
`PathRequestSystem` answers it from a navigation grid. Run the behaviour systems, then the path
system:

```kotlin title="Kotlin"
--8<-- "awake/ai/behavior/src/desktopTest/kotlin/com/awakekt/awake/ai/behavior/AiDocsSampleTest.kt:systems"
```

## Properties

`patrol`:

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `stops` | list of vectors | empty | Positions to visit in order. |
| `style` | `loop` · `pingPong` · `once` | `loop` | Wrap to the first stop, walk back, or stop at the last one. |
| `dwellSeconds` | number | `1` | Seconds to wait at each stop. |
| `speed` | number | `1.8` | Metres per second. |
| `repathInterval` | number | `1` | Seconds between path queries. |
| `waypointRadius` | number | `0.3` | How close counts as reaching a waypoint. |

`chase`:

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `target` | node name or none | none | The node to pursue. None waits for a target set in code. |
| `speed` | number | `2.5` | Metres per second. |
| `repathInterval` | number | `0.5` | Seconds between path queries. |
| `waypointRadius` | number | `0.3` | How close counts as reaching a waypoint. |

`flee`:

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `threat` | node name or none | none | The node to run from. |
| `panicRadius` | number | `6` | Start fleeing when the threat is closer than this. |
| `safeRadius` | number | `12` | Stop once the threat is further than this. Must be more than `panicRadius`. |
| `fleeDistance` | number | `10` | How far ahead to aim when picking where to run. |
| `speed` | number | `3.5` | Metres per second. |
| `repathInterval` | number | `0.4` | Seconds between path queries. |
| `waypointRadius` | number | `0.3` | How close counts as reaching a waypoint. |

In Kotlin, `PatrolBehavior`, `ChaseBehavior` and `FleeBehavior` take the same values, with `target`
and `threat` as entities rather than names.

`navigation`:

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `rows` | list of text | empty | One string per row of cells along z; each character is a cell along x, `.` walkable and `#` blocked. Every row is the same length. |
| `cellSize` | number | `1` | World size of one cell. |
| `originX` | number | `0` | World x of the centre of cell (0, 0). |
| `originZ` | number | `0` | World z of the centre of cell (0, 0). |

A grid baked from terrain with `Heightmap.bakeNavGrid` is the same data; a tool that writes one out
as a `navigation` component gives the runtime its single way in.

## Behaviour trees

Build a tree with `behaviorTree { }`, attach it with `BehaviorTreeComponent`, and tick it with
`BehaviorTreeSystem`. Each tick runs the tree once for every enabled component.

```kotlin title="Kotlin"
--8<-- "awake/ai/behavior/src/desktopTest/kotlin/com/awakekt/awake/ai/behavior/AiDocsSampleTest.kt:behavior-tree"
```

| Node | What it does |
| --- | --- |
| `sequence { }` | Runs children in order until one does not succeed. |
| `selector { }` | Tries children in order until one does not fail. |
| `parallel(policy) { }` | Ticks every child each tick. `REQUIRE_ALL` (default) needs all to succeed, `REQUIRE_ONE` needs one. |
| `inverter { }` | Swaps success and failure of its child. |
| `repeater(count) { }` | Runs its child until it has succeeded `count` times; `-1` means forever. |
| `cooldown(seconds) { }` | Fails for `seconds` after its child succeeds. |
| `timeout(seconds) { }` | Fails once its child has kept running for `seconds`. |
| `condition { ctx -> }` | Succeeds when the predicate is true. |
| `action { ctx -> }` | Runs your code and returns `SUCCESS`, `FAILURE` or `RUNNING`. |

`ctx` is an `AiContext` with the `world`, the `entity`, the frame `delta` and the entity's
`blackboard`, a key-value store for the tree's state.

!!! warning "A forever repeater needs a child that waits"
    `repeater()` re-runs a succeeding child within the same tick. With `count = -1`, a child that
    succeeds at once never lets the tick end. Give it a child that returns `RUNNING` while it
    works.

## State machines

For logic with a few clear modes, use an `AiStateMachine`: named `AiState`s with `onEnter`,
`onUpdate` and `onExit`, and `StateTransition`s checked every update.

```kotlin title="Kotlin"
--8<-- "awake/ai/behavior/src/desktopTest/kotlin/com/awakekt/awake/ai/behavior/AiDocsSampleTest.kt:state-machine"
```

## How it works

The three behaviours share one route follower. On an interval each asks for a route to its goal: the
next stop, the target's position, or a point away from the threat. When an answer arrives it steps
along the waypoints at its speed. An unreachable goal clears the route instead of walking into a
wall; a patrol skips an unreachable stop, and a fleeing entity tries directions further off the
straight line.

!!! warning "No PathRequest, no movement"
    Every behaviour reads a `PathRequest` on the same entity and does nothing without one. The scene
    bindings add it, so a behaviour that comes from a scene document has one. A behaviour you attach
    yourself, in the scene DSL or in Kotlin, needs its own `PathRequest`.

!!! warning "Behaviours move on X and Z only"
    Navigation waypoints carry no height, so the behaviours leave `Transform.position.y` alone. Put
    the entity back on the ground yourself, for example from `Heightmap.heightAtWorld`.

!!! tip "Targets are node names"
    `target` and `threat` name another node in the same scene document. Loading fails if no node
    has that name, and saving fails if the target entity has no `Name`.

## See also

- [Navigation](navigation.md) for the grids the behaviours walk on.
- [Character controller](character-controller.md) for a player driven by input.
- [Terrain](terrain.md) for the heightmap a navigation grid is baked from.
