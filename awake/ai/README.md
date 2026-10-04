# `awake:ai`

Behavior Tree and Finite State Machine primitives for Awake games and systems.

## What is here

- **Behavior Trees (`btree/`)**: `BehaviorNode`, `SequenceNode`, `SelectorNode`, `ParallelNode`, `InverterNode`, `RepeaterNode`, `CooldownNode`, `TimeoutNode`, `ActionNode`, `ConditionNode`, `Blackboard`, and `behaviorTree { }` DSL.
- **Finite State Machines (`fsm/`)**: `AiState`, `AiStateMachine`, `AiStateMachineComponent`, and `AiStateMachineSystem`.
- **`AiContext`**: execution context passed to action nodes and state machines (holds entity and world).

## Architecture

`awake:ai` contains core primitives only and depends solely on `awake:ecs`. It has no dependency on `scene-core`, `transform`, or `navigation`.

Starter navigation-backed behaviors (`ChaseBehavior`, `FleeBehavior`, `PatrolBehavior`) live in `awake:ai:behavior`, with the
`navigation` scene component (`SceneNavigation`) that carries the grid they route over as rows of cells, so a scene document
brings everything a played scene needs to run them.
