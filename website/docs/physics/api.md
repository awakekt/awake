# Physics API

The physics API is a pure Kotlin contract for rigid-body lifecycle, collision shapes, transforms,
and raycasting. It is independent of a native physics engine.

Install [`awake:physics:api`](../getting-started.md#physics) to compile against the backend-neutral
types. Add a backend such as Jolt separately when you need a simulation implementation.

## Main types

- `PhysicsWorld` creates bodies, advances simulation, and performs raycasts.
- `BodyHandle` identifies a simulated body.
- `PhysicsShape` describes box, sphere, capsule, and mesh geometry.
- `MotionType` distinguishes static, kinematic, and dynamic bodies.
- `BodyTransform` stores position and orientation.
- `RaycastHit` reports the hit position, normal, distance, and body.

Use [Jolt](jolt.md) when an application needs the native Jolt implementation. Keep the API in
shared code and add the backend only on [supported targets](../getting-started.md#physics).
