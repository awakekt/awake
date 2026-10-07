# Awake Backend Jolt

Jolt Physics backend for [Awake](../../../README.md) implementing `:awake:physics:api` through platform bindings. Provides rigid body dynamics, collision detection, and raycasting across Desktop (JVM), Android, iOS, and WasmJs. Bodies support unrestricted 3D motion or `DegreesOfFreedom.PLANE_2D` (X/Y translation and Z rotation).

## Installation

```kotlin
implementation(project(":awake:backend:jolt"))
```

## Key Primitives

- `JoltPhysicsWorld` — implements `PhysicsWorld` wrapping Jolt's native physics system and body interface.
- `createJoltPhysicsWorld` — portable suspend factory returning a `PhysicsWorld`.

## Architecture

- **Desktop (JVM)**: jolt-jni with a platform native library loaded at runtime.
- **Android**: jolt-jni device ABIs loaded through `System.loadLibrary`.
- **iOS**: JoltC built from pinned sources and linked through Kotlin/Native cinterop.
- **WasmJs**: JoltPhysics.js, a single-threaded WebAssembly module compiled via Emscripten.

Each binding sets Jolt's allowed degrees of freedom when creating a body, so the solver enforces
planar motion during contacts as well as ordinary movement. Collision shapes retain their thickness.
See [Physics in 2D](../../../website/docs/guides/physics.md#physics-in-2d) for scene and API examples.

## Related Modules

- [`:awake:physics:api`](../../physics/api/README.md) — pure Kotlin physics interface implemented by this backend.
- `:awake:scene:physics` — ECS physics synchronization systems (no README yet).
