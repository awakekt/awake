# `awake:scene:navigation`

The navigation scene components and their bindings:

- `navigation`: the walkable grid a scene document carries as rows of cells. Its binding bakes it
  into a `NavigationGrid`, holding a `NavGrid` and, for agents, its clearance `NavSurfaceTile`.
- `navigation_agent`: an entity that walks to a destination around what the grid blocks. Its binding
  maps it onto a `NavigationAgent` holding a `NavPathFollower`, and `NavigationAgentSystem` steers it
  through an `Agent` `MovementControl`, or by its `Transform` when it has none.

The grid, the walkable surface, pathfinding and path following are `awake:navigation`, which has no
scene dependency; this module only maps the scene schema onto them and runs them over a `World`.

```kotlin
implementation(project(":awake:scene:navigation"))
```

`registerAiBehaviors()` in `awake:scene:ai` registers these components with the behaviours that route
over them. A scene that only needs the grid registers `NavigationSceneComponents` itself.
