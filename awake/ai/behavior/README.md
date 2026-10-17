# `awake:ai:behavior`

Navmesh-backed steering behaviors: `PatrolBehavior`, `ChaseBehavior`, `FleeBehavior`, the `RouteFollower` they share, and the systems that drive them. It has no scene dependency.

## Spatial behaviour-tree kinds

`registry.registerSpatialKinds(placement)` adds the kinds that move and place agents to an
`awake:ai` registry, so data-driven trees and machines can walk:

| Kind | Does |
| --- | --- |
| `ai.move-to` | Walks to a target; succeeds there, fails when it cannot be reached. |
| `ai.follow` | Keeps within a distance of a target; runs while the target has a place. |
| `ai.flee` | Runs from a threat until a safe distance, trying other directions when blocked. |
| `ai.wander` | Walks to a seeded random point near where it started, then succeeds. |
| `ai.face` | Turns toward a target through `AgentPlacement.face`. |
| `ai.distance` | Compares the ground distance to a target. |
| `ai.in-area` | Tests a position against a circle or a box. |

A target is a blackboard `key` holding an `Entity` or a `Vec3f`, such as perception's
`perception.target`, or a fixed `point`. The moving kinds walk the same way the behaviours do, through
`RouteFollower` and the agent's `PathRequest`, so a `PathRequestSystem` answers them; leaving one
withdraws its question. Their routes are part of the tree's snapshot. Arrival is never closer than
`waypointRadius`, where a route ends.

`NavFieldLineOfSight` gives perception line of sight over a navigation grid.

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
