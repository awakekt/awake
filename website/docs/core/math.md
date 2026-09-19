# Core math

Awake’s platform-neutral math module supplies the vectors, matrices, rotations, camera lenses,
and geometry primitives used by scenes, physics, and rendering. This page focuses on the first
decision a game loop makes: turning a target direction into a unit movement or camera direction.

Add the [Core Math artifact](../getting-started.md#core-math-and-ecs) to the source set that uses it. The
release-matched API reference remains the complete reference for signatures and contracts.

## Normalize a direction

`Vec3f` has two deliberately different normalization forms. The imperative `normalize()` reuses
the receiver; `normalized()` returns a new vector and leaves the receiver unchanged.

Use the mutating form in a frame loop when you already own a scratch vector:

````markdown
```kotlin
--8<-- "awake/core/math/src/commonTest/kotlin/com/awakekt/awake/core/math/Vec3MutabilityTest.kt:normalize-in-place"
```
````

Use the allocating form when preserving the original vector is useful:

````markdown
```kotlin
--8<-- "awake/core/math/src/commonTest/kotlin/com/awakekt/awake/core/math/Vec3MutabilityTest.kt:normalized-copy"
```
````

These examples are extracted from the compiled math tests. They are intentionally small: the
tests verify the mutation contract, while this page explains when to choose each form.

## Choosing the next primitive

| Need | Start with |
|---|---|
| Positions, directions, or scales | `Vec3f` |
| Rotation without Euler-angle drift | `Quat` |
| World, view, or projection transforms | `Mat4` |
| Camera setup and viewport projection | `Lens` and `CameraMathUtils` |
| Visibility and hit tests | `Ray`, `Plane`, `Aabb`, or `Frustum` |

Continue with [scene authoring](../scene/authoring.md) to place these values in a scene, or
[scene rendering](../scene/rendering.md) to pass camera and transform data into a render plan.
The release-matched API reference remains the source for complete signatures and contracts.
