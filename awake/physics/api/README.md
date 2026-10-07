# Awake Physics API

Pure Kotlin multiplatform physics contract for [Awake](../../../README.md) — rigid body lifecycle, collision shapes, motion types, transforms, and raycasting. Zero native or backend dependencies, compiling for all targets.

## Installation

```kotlin
implementation(project(":awake:physics:api"))
```

## Key Primitives

- `PhysicsWorld` — contract for rigid body simulation, step updates, body creation, and raycasting.
- `BodyHandle` — type-safe lightweight handle identifying a simulated rigid body.
- `PhysicsShape` — sealed collision geometry (`Box`, `Sphere`, `Capsule`, `Mesh`).
- `MotionType` — rigid body mobility (`Static`, `Kinematic`, `Dynamic`).
- `DegreesOfFreedom` — unrestricted motion (`ALL`) or X/Y translation and Z rotation (`PLANE_2D`).
- `BodyTransform` — position (`Vec3`) and orientation (`Quat`) representation.
- `RaycastHit` — raycast intersection result (hit position, normal, distance, body handle).

## Usage Example

```kotlin
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.DegreesOfFreedom
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f

// Create a dynamic body in the XY plane; omit degreesOfFreedom for unrestricted 3D motion.
val body = physicsWorld.createBody(
    shape = BoxShape(halfExtents = Vec3f(0.5f, 0.5f, 0.25f)),
    motionType = MotionType.DYNAMIC,
    position = Vec3f(0f, 5f, 0f),
    rotation = Quat.IDENTITY,
    degreesOfFreedom = DegreesOfFreedom.PLANE_2D,
)

// Step simulation
physicsWorld.step(deltaTime = 1f / 60f)
```

The constraint is fixed at creation and enforced by the backend during simulation. Shapes keep
their thickness and bodies keep their initial pose; an origin shift translates the plane too.
Existing implementations can keep implementing the original `createBody` overload. The new
overload delegates `ALL` to it and throws `PhysicsCapabilityException` for unsupported restrictions.

## Related Modules

- [`:awake:backend:jolt`](../../backend/jolt/README.md) — Jolt Physics C++ native backend implementing this contract.
- `:awake:scene:physics` — ECS component and system bindings (`RigidBodyComponent`, `PhysicsSystem`); no README yet.
