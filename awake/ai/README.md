# `awake:ai`

Behaviour trees and finite state machines for Awake games and tools, built in Kotlin or loaded from
JSON that a game authors without code.

## What is here

- **Behaviour trees (`btree/`)**: `BehaviorNode` and the nodes `SequenceNode`, `SelectorNode`,
  `ParallelNode`, `InverterNode`, `RepeaterNode`, `CooldownNode`, `TimeoutNode`, `SucceederNode`,
  `WaitNode`, `StatusNode`, `ChanceNode`, `EmitEventNode`, the blackboard nodes
  (`BlackboardCompareNode`, `BlackboardHasNode`, `BlackboardSetNode`, `BlackboardCopyNode`,
  `BlackboardRemoveNode`, `BlackboardAddNode`), `ActionNode`, `ConditionNode`, the `Blackboard`, and
  the `behaviorTree { }` DSL. `BehaviorTree` is one running tree: its nodes by path, the status each
  returned on the latest tick, and snapshots.
- **State machines (`fsm/`)**: `AiState`, `AiStateMachine` (with snapshots), `AiStateMachineComponent`
  and `AiStateMachineSystem`.
- **Definitions (`definition/`)**: `BehaviorTreeDefinition` and `StateMachineDefinition`, the
  `AiRegistry` of node and state kinds, Core's kinds in `BuiltInNodeKinds`, `AiDefinitionJson`, and
  the `awake:node-graph` bridge an editor uses.
- **`AiContext`**: what a node or state is ticked with: the world, the entity, the delta, the
  blackboard, and the `AiEventSink` emitted events go to.

## Trees and machines as data

A definition is plain `@Serializable` data. Each node names a **kind** registered in an
`AiRegistry` and carries that kind's parameters:

```json
{
  "formatVersion": 1,
  "blackboard": { "watered": 0 },
  "root": {
    "kind": "ai.selector",
    "children": [
      { "kind": "ai.sequence", "children": [
        { "kind": "ai.compare", "params": { "key": "heat", "op": ">=", "value": 30.0 } },
        { "kind": "ai.emit", "params": { "event": "vent.open" } }
      ] },
      { "kind": "ai.wait", "params": { "seconds": 2 } }
    ]
  }
}
```

```kotlin
val registry = AiRegistry()                                   // Core's kinds are registered
    .action("garden.water", WaterParams.serializer()) { p, ctx -> water(p.plot, ctx.delta) }
val compiled = registry.compileTree(AiDefinitionJson.Default.decodeTree(text))
world.add(entity, compiled.newComponent(seed = entity.packed))  // one running tree per agent
```

- **Kinds are typed.** A kind is an id, a `@Serializable` parameter class and a factory. The parameter
  class rejects bad values in its `init`, so `{ "seconds": -1 }` is refused when the definition is
  compiled. `NodeDefinition.of(kind, params)` writes a definition in Kotlin with typed parameters. A
  game registers its own kinds with `register`, or with `action`, `condition` and `state`.
- **Compiling checks everything at once.** `compileTree` and `compileStateMachine` throw an
  `AiDefinitionException` listing every problem with where it is: `node 0.1 (ai.wiat): unknown node
  kind 'ai.wiat' (did you mean 'ai.wait'?)`, an unknown or missing parameter, the wrong number of
  children, an unknown state, a transition with no trigger. `validate` returns the same list.
- **Core's kinds**: `ai.sequence`, `ai.selector`, `ai.parallel`; the decorators `ai.inverter`,
  `ai.repeat`, `ai.cooldown`, `ai.timeout`, `ai.succeeder`; the conditions `ai.compare`, `ai.has`,
  `ai.chance`, `ai.status`; the actions `ai.wait`, `ai.set`, `ai.copy`, `ai.remove`, `ai.add`,
  `ai.emit`; and the state kind `ai.state`. They are mechanisms over the blackboard and time. What a
  key, an event or a state means (a status such as "dazed", a faction, a shop's stock) is the
  game's data.
- **Blackboard values are typed.** `BlackboardValue` is a boolean, an int, a float, text, an entity or
  a vector, written in JSON as `true`, `3`, `2.5`, `"open"`, `{"entity": 7}` and
  `{"x": 0, "y": 0, "z": 1}`. A number with a fraction is a float, so `2.0` and `2` differ.
- **State machines.** A `StateDefinition` is a kind plus nodes ticked once on enter and on exit and a
  tree ticked on each update. A `TransitionDefinition` fires after a time in the state, when the
  state's tree has finished (`onTree`), and when its condition node succeeds; leaving out `from`
  makes it leave every other state. Transitions are asked in order and the first that fires wins.
- **Versions.** Each document records its `formatVersion`. `AiDefinitionJson` takes
  `AiFormatMigration`s that rewrite an older document's JSON before it is read, and refuses a newer
  one.

## Determinism and snapshots

Ticking is deterministic: the same definition, seed and deltas give the same statuses, writes and
events. `ai.chance` draws from a generator seeded per tree (`newTree(seed)`) and per node, so agents
with different seeds differ and a restored tree draws the same numbers again.

`BehaviorTree.snapshot()`, `AiStateMachine.snapshot()` and `Blackboard.snapshot()` return data that
`restore` puts back into a tree or machine built the same way. A restored machine is in its state
without entering it again. A node or state keeps its state through `Snapshottable`, with a typed
state class and its serializer; a game's own stateful nodes implement it too. A blackboard snapshot
holds the `BlackboardValue` types only, and names the key of anything else it meets.

## Editors

- `AiRegistry.parameterSchema(kind)` describes a kind's parameters (names, types, defaults, ranges,
  hints) through `awake:core:schema`, for an inspector form.
- `AiRegistry.behaviorTreeNodeRegistry()` is the vocabulary as an `awake:node-graph` registry, and
  `NodeDefinition.toNodeGraph()` and `NodeGraph.toNodeDefinition()` convert a tree to and from a graph,
  so a node-graph canvas edits trees. `NodeDefinition.layout` and `id` are kept for the editor.
- `BehaviorTree.statusOf(path)` and `runningPaths()` say what each node returned on the latest tick,
  for live highlighting, and `AiStateMachine.currentTree()` gives the running state's tree.

## Architecture

`awake:ai` depends on `awake:ecs`, `awake:core:math` (positions on the blackboard),
`awake:core:schema` (parameter forms), `awake:node-graph` (the editor bridge) and
`kotlinx-serialization-json`. It has no dependency on `scene-core`, `transform`, `navigation` or any
backend.

Starter navigation-backed behaviors (`ChaseBehavior`, `FleeBehavior`, `PatrolBehavior`) live in `awake:ai:behavior`, with no
scene dependency. `awake:scene:ai` binds them into scene documents, and `awake:scene:navigation` has the `navigation` scene
component (`SceneNavigation`) that carries the grid they route over as rows of cells, so a scene document brings everything a
played scene needs to run them.

## Limits

- Timers count Float seconds from the deltas they are given.
- Node states in a snapshot are JSON, so a snapshot is written as JSON.
- Core has no movement or perception kinds yet; a game registers its own, such as the action that
  moves its agents.
- A state machine has no graph conversion yet; a visual editor edits its trees.
