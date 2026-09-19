# Scene AI

Awake AI provides behavior-tree and finite-state-machine primitives that can be driven by ECS
systems.

Install [`awake:ai`](../getting-started.md#scene-authoring) in the source set that uses the AI
systems.

## Behavior trees

The behavior-tree DSL includes sequence, selector, parallel, inverter, repeater, cooldown,
timeout, action, condition, and blackboard nodes.

## Finite-state machines

The FSM API provides `AiState`, `AiStateMachine`, `AiStateMachineComponent`, and
`AiStateMachineSystem`. `AiContext` supplies the entity and world needed by actions and state
transitions.
