# Core runtime

The core runtime contains platform-neutral building blocks used by the rest of Awake. It is
designed to be usable without a renderer or a platform backend.

## Responsibilities

- 3D math, transforms, projections, and geometry.
- Input and frame-timing primitives.
- Image, bitmap, and color data types.
- Shared utility types used by higher-level modules.

Core does not create windows, select a graphics backend, or own a scene. Add the modules your
application needs rather than depending on the entire engine.

Start with [Install and first app](../getting-started.md), then use the [core tutorials and
samples](../core/tutorial.md) to choose a focused path through math and ECS.

## Related pages

- [Core math](../core/math.md)
- [Application bootstrap](bootstrap.md)
- [ECS runtime](ecs.md)
