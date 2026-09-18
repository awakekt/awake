# Core math

Awake’s platform-neutral math module provides the value types used by scenes, cameras, physics,
and rendering.

## Common types

| Type | Purpose |
|---|---|
| `Vec3f`, `Vec3d`, `Vec3i` | Spatial points, directions, and grid coordinates |
| `Quat` | Rotation without gimbal lock |
| `Mat4` | Transform, view, and projection matrices |
| `Ray`, `Plane`, `Aabb`, `Frustum` | Intersection and visibility queries |
| `Lens`, `CameraMathUtils` | Camera projections and viewport mapping |

The math API is intended for frame-loop code, so avoid creating unnecessary temporary objects in
hot update paths. The KDoc in the corresponding release source is the complete function and
constructor reference.
