# `awake:ai:behavior`

Navmesh-backed starter behaviors: `PatrolBehavior`, `ChaseBehavior`, `FleeBehavior`, the `RouteFollower` they share, and the systems that drive them.

## Architecture & Dependencies

```
awake:ecs    awake:scene:scene-core    awake:navigation
    │                  │                      │
    ▼                  ▼                      ▼
awake:ai       ────────┴──────────────────────┘
    │                         │
    └──────────► awake:ai:behavior
```

- `awake:ai`: Behavior trees and finite state machine primitives.
- `awake:navigation`: NavMesh, PathRequest, and NavGrid spatial pathfinding.
- `awake:scene:scene-core`: Transform component for entity movement.

Games can use `awake:ai` primitives or `awake:navigation` pathfinding independently without including high-level starter behaviors.
