# `awake:ai:behavior`

Navmesh-backed steering behaviors: `PatrolBehavior`, `ChaseBehavior`, `FleeBehavior`, the `RouteFollower` they share, and the systems that drive them. It has no scene dependency.

## Architecture & Dependencies

```
awake:ecs         awake:navigation
    │                    │
    ▼                    │
awake:ai                 │
    │                    │
    └──────► awake:ai:behavior ◄──── awake:scene:ai (scene components, TransformAgentPlacement)
```

- `awake:ai`: Behavior trees and finite state machine primitives.
- `awake:navigation`: NavMesh, PathRequest, and NavGrid spatial pathfinding.

## Where an agent is

The behaviours do not know what places an entity, so `ChaseAiSystem`, `FleeAiSystem` and `PatrolAiSystem` take an `AgentPlacement`: it reads an entity's position and moves it on the ground plane. The behaviours move an agent with `steer`, a velocity at the behaviour's speed, which by default moves it that far through `moveBy`; a placement whose entities something else moves, such as a character controller, passes the velocity on instead. An app supplies the one it has. `awake:scene:ai` supplies `TransformAgentPlacement` for the scene's `Transform`, and `MovementAgentPlacement` for agents a character controller moves, with the `patrol`, `chase` and `flee` scene components; `awake:scene:navigation` has the `navigation` component they route over.

Games can use `awake:ai` primitives or `awake:navigation` pathfinding independently without including these behaviors.
