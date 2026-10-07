# `awake:scene:navigation`

The `navigation` scene component: the walkable grid a scene document carries as rows of cells, and
its binding, which bakes it into a `NavigationGrid` holding a `NavGrid`. The grid and pathfinding
are `awake:navigation`, which has no scene dependency.

```kotlin
implementation(project(":awake:scene:navigation"))
```

`registerAiBehaviors()` in `awake:scene:ai` registers this component with the behaviours that route
over it. A scene that only needs the grid registers `NavigationBinding` itself.
