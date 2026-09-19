# ECS runtime

Awake’s ECS stores entities and components in a compact, queryable world. Systems operate on the
world during the application update and render phases.

Install [`awake:ecs`](../getting-started.md#core-math-and-ecs) in `commonMain`. This dependency is
independent of graphics, scenes, physics, and UI.

## Core concepts

- An entity is an opaque identity in a `World`.
- Components hold data associated with an entity.
- Systems update matching entities through queries and families.
- Structural changes are explicit, so a system can control when entities enter or leave a query.

The ECS is platform-neutral and does not depend on Vulkan, WebGPU, physics, or UI. Those systems
can be layered on top of the same world.

## Create a component and query entities

This usage is extracted from the compiled ECS tests. Components are ordinary Kotlin values; the
world stores them by entity and queries entities matching the requested component types.

```kotlin
--8<-- "awake/ecs/src/commonTest/kotlin/com/awakekt/awake/ecs/WorldTest.kt:ecs-component-query"
```

The [scene runtime](scene.md) builds transforms, cameras, and scene lifecycle on top of the ECS.
For exact APIs and target availability, consult the release-matched API reference.
