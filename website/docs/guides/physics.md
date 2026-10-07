# Physics

<p class="awake-lede">Rigid bodies, colliders, triggers and queries: give an entity a body and the simulation moves it, stops it against the level, and tells you what it touched.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">component: <code>physics_body</code></span>
<span class="awake-badge awake-badge--ok">Jolt</span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

Two modules split the work. `awake:physics:api` is the backend-neutral `PhysicsWorld` contract, and
`awake:scene:physics` binds it to the ECS through the `physics_body` component and `PhysicsSystem`.
The simulation itself comes from a backend; today that is [Jolt](physics-jolt.md).

## Add a physics body

Give an entity a `physics_body` component. A static body is level geometry that never moves; a
dynamic body falls, collides and comes to rest. Both forms below build the same components.

=== "Scene document"

    ```json title="crate.scene.json"
    --8<-- "website/docs/snippets/world/crate.scene.json"
    ```

    `physics_body` is not one of the default components. Register it before you load the document:
    pass `SceneComponentRegistry().registerPhysics()` as the `componentRegistry`. The project runtime
    in `awake:project:runtime` registers it for you.

=== "Scene DSL"

    ```kotlin title="Kotlin"
    --8<-- "awake/scene/physics/src/desktopTest/kotlin/com/awakekt/awake/scene/physics/PhysicsDocsSampleTest.kt:crate-dsl"
    ```

    There is no dedicated DSL function for a body yet, so attach a `PhysicsBody` with `with(...)`.

=== "Studio"

    1. Select an entity in the **Hierarchy**.
    2. In the **Inspector**, open **Add component** and choose **Physics Body**. Studio adds a 1 m
       box that is `DYNAMIC`.
    3. Set **Motion** to `STATIC`, `KINEMATIC` or `DYNAMIC`.

    Studio cannot change the shape, layer or sensor flag yet. Edit those in the scene document.

## Step the simulation

`PhysicsSystem` creates each body the first time it sees it, steps the world once per update, and
writes the simulated pose back into the entity's `Transform`.

```kotlin title="Kotlin"
--8<-- "awake/scene/physics/src/desktopTest/kotlin/com/awakekt/awake/scene/physics/PhysicsDocsSampleTest.kt:step"
```

In an app, register it as a fixed-step system so it steps at the same rate on every machine:
`fixedSystem("physics") { PhysicsSystem(physicsWorld) }`.

## Properties

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `shape` | `box` · `sphere` · `capsule` · `mesh` | `box` | The collider, centred on the entity. |
| `shape.halfExtents` | vector | `(0.5, 0.5, 0.5)` | Half the box size on each axis (`box`). |
| `shape.radius` | number | `0.5` | Sphere or capsule radius. |
| `shape.halfHeight` | number | `0.5` | Half the capsule's cylinder, so the capsule is `2 * (halfHeight + radius)` tall. |
| `shape.mesh` | string | required | Project path of the `.glb` or `.gltf` model whose triangles collide (`mesh`). |
| `shape.primitive` | integer or none | none | Which primitive of the model, counted in node order. None merges them all (`mesh`). |
| `motion` | `STATIC` · `KINEMATIC` · `DYNAMIC` | `STATIC` | Level geometry, driven by code, or simulated. |
| `layer` | integer or none | `0` for `STATIC`, `1` otherwise | Collision layer index. See [collision layers](#collision-layers). |
| `sensor` | boolean | `false` | Detects what passes through it instead of blocking it. |

In Kotlin, `PhysicsBody(shape, motionType, layer, sensor)` takes any [shape](#shapes), not only the
four a scene document can describe.

## Collide with a model

A `mesh` shape makes a static body of a model's own triangles, so a character walks over a bridge
and under an arch rather than into their bounding boxes:

```json
{ "component": "physics_body", "shape": { "type": "mesh", "mesh": "models/bridge.glb" } }
```

The body is `STATIC` and not a sensor; a scene that asks for anything else fails validation. The
node's scale is baked into the triangles, any per-axis or mirrored scale included, and the body is
placed by the node's position and rotation like every other body. Saving writes the model path back,
not the triangles.

The shape loads as a `MeshCollider` and `MeshColliderSystem` builds the body from it, so register
that system before `PhysicsSystem`. It reads the triangles from a `CollisionMeshSource`;
`loadCollisionMeshes(scene, files)` in `awake:project:runtime` is the glTF one, reading each model
once and failing with the model path and node when one cannot be read. `loadProject` does
all of this for you. A host that calls `sceneSystemsFor` itself passes
`collisionMeshes = loadCollisionMeshes(scene, files)` in its `SceneHostServices`.

For moving props or sensors, use a `convex_hull` shape instead:

```json
{ "component": "physics_body", "shape": { "type": "convex_hull", "mesh": "models/crate.glb" }, "motion": "DYNAMIC" }
```

A `convex_hull` shrink-wraps the model's vertices into a convex shape with scale baked in. Because it
encloses volume, it can be `DYNAMIC`, `KINEMATIC`, or `STATIC`, and can act as a trigger sensor. It loads
through the same `loadCollisionMeshes` cache and `MeshColliderSystem`.

## Shapes

| Shape | Motion | Use it for |
| --- | --- | --- |
| `BoxShape(halfExtents)` | any | Crates, walls, floors. |
| `SphereShape(radius)` | any | Balls, projectiles, pickups. |
| `CapsuleShape(halfHeight, radius)` | any | Characters: it slides over steps where a box catches. |
| `ConvexHullShape(points)` | any | A prop that moves and is not a box. Concave detail is lost. |
| `MeshShape(vertices, indices)` | `STATIC` only | Authored level geometry. |
| `HeightFieldShape(heights, sampleCount, scale)` | `STATIC` only | Terrain. The grid is square, at least 4 samples a side. |

A heightfield for terrain, and a convex hull for a prop that rests on it:

```kotlin title="Kotlin"
--8<-- "awake/backend/jolt/src/desktopTest/kotlin/com/awakekt/awake/physics/jolt/PhysicsApiDocsSampleTest.kt:heightfield"
```

```kotlin title="Kotlin"
--8<-- "awake/backend/jolt/src/desktopTest/kotlin/com/awakekt/awake/physics/jolt/PhysicsApiDocsSampleTest.kt:convex-hull"
```

!!! warning "Winding decides which side of a mesh is solid"
    Jolt collides with the face a triangle's winding points at. Wind `MeshShape` triangles
    counter-clockwise as seen from the solid side, or bodies fall straight through while rays
    still hit it.

## Query the world

Queries go to the `PhysicsWorld`. Casts ask what would stop you, so they never report sensors;
overlaps ask what you are inside, so they do.

**Raycast.** The first body along a ray, or `null`. `RaycastHit` carries `handle`, `point` and
`distance`; it has no surface normal.

```kotlin title="Kotlin"
--8<-- "awake/backend/jolt/src/desktopTest/kotlin/com/awakekt/awake/physics/jolt/PhysicsApiDocsSampleTest.kt:raycast"
```

**Shape cast.** Sweeps a convex shape and returns the first hit. `ShapeCastHit` carries `handle`,
`point`, `normal` and `fraction`. Pass `ignore` to skip one body, such as the caster's own.

```kotlin title="Kotlin"
--8<-- "awake/backend/jolt/src/desktopTest/kotlin/com/awakekt/awake/physics/jolt/PhysicsApiDocsSampleTest.kt:shape-cast"
```

**Overlap.** Every body a convex shape overlaps right now: spawn checks, explosions, melee arcs.

```kotlin title="Kotlin"
--8<-- "awake/backend/jolt/src/desktopTest/kotlin/com/awakekt/awake/physics/jolt/PhysicsApiDocsSampleTest.kt:overlap"
```

All three take an optional `onlyLayer` that limits them to one [collision layer](#collision-layers).
A `MeshShape` or `HeightFieldShape` cannot be swept or used for an overlap; passing one throws
`PhysicsCapabilityException`.

## Triggers and contacts

A sensor body is a trigger volume: a pickup, a checkpoint, a damage zone.

```kotlin title="Kotlin"
--8<-- "awake/scene/physics/src/desktopTest/kotlin/com/awakekt/awake/scene/physics/PhysicsDocsSampleTest.kt:sensor"
```

`PhysicsSystem.contacts` holds every contact from its last step, with the entities involved. Read it
from a system that runs after `PhysicsSystem`:

```kotlin title="Kotlin"
--8<-- "awake/scene/physics/src/desktopTest/kotlin/com/awakekt/awake/scene/physics/PhysicsDocsSampleTest.kt:contacts"
```

Without the ECS, drain contacts from the world yourself, once per step, after stepping:

```kotlin title="Kotlin"
--8<-- "awake/backend/jolt/src/desktopTest/kotlin/com/awakekt/awake/physics/jolt/PhysicsApiDocsSampleTest.kt:sensor"
```

Sensors report contacts on their own. A solid body reports only after
`setContactReporting(handle, true)`, for hit sounds or impact damage.

!!! warning "What a sensor cannot see"
    A sensor only detects active bodies, so a body that fell asleep before entering it is never
    reported. A static sensor does not detect static bodies; make a trigger that must notice level
    geometry `KINEMATIC`. A `MeshShape` or `HeightFieldShape` cannot be a sensor.

!!! tip "One event per touching part"
    Contacts are reported per touching sub-shape pair, not per body pair. Two convex shapes touch
    once; a mesh or heightfield entering a sensor can report many. Deduplicate by pair if that
    matters.

## Collision layers

Every body belongs to one collision layer. The default table has two: `CollisionLayers.World`
(index 0) for static level geometry and `CollisionLayers.Moving` (index 1) for everything else, where
moving bodies hit the world and each other. A body without a `layer` lands in the one that matches its
motion type.

Queries take `onlyLayer` to see a single layer, for example a camera ray that stops at walls but not
at crates:

```kotlin title="Kotlin"
--8<-- "awake/backend/jolt/src/desktopTest/kotlin/com/awakekt/awake/physics/jolt/PhysicsApiDocsSampleTest.kt:only-layer"
```

For more layers, build a `CollisionLayers` table and pass it when you create the world. It says how
many layers there are, which of them move, and which pairs collide:

```kotlin title="Kotlin"
--8<-- "awake/backend/jolt/src/desktopTest/kotlin/com/awakekt/awake/physics/jolt/PhysicsApiDocsSampleTest.kt:layers"
```

```kotlin title="Kotlin"
--8<-- "awake/backend/jolt/src/desktopTest/kotlin/com/awakekt/awake/physics/jolt/PhysicsApiDocsSampleTest.kt:layer-body"
```

The table is fixed for the life of the world, must be symmetric, and holds at most 32 layers. In a
scene document, `layer` is the index.

!!! warning "Test a custom table before you rely on it"
    On desktop, a body in a third layer set to collide with `World` fell through a static `World`
    floor in our tests, while the same body in `Moving` landed on it. Check that your layers
    collide the way the table says before building on them.

## Constraints

A constraint ties two bodies together. Anchors are world-space and read once, at creation.

```kotlin title="Kotlin"
--8<-- "awake/backend/jolt/src/desktopTest/kotlin/com/awakekt/awake/physics/jolt/PhysicsApiDocsSampleTest.kt:constraint"
```

| Constraint | Holds |
| --- | --- |
| `DistanceConstraint(bodyA, bodyB, pointA, pointB, minDistance, maxDistance)` | A rope or rod between two points. |
| `HingeConstraint(bodyA, bodyB, point, axis, limits)` | A door or wheel turning about one axis. |
| `BallSocketConstraint(bodyA, bodyB, point, twistAxis, swingLimit, twistLimit)` | A shoulder or hip: swing in a cone, twist about an axis. |

`destroyBody` removes the constraints that reference that body, so a constraint handle is dead once
either of its bodies is.

## Move bodies from code

| Call | What it does |
| --- | --- |
| `setLinearVelocity(handle, velocity)` / `getLinearVelocity(handle)` | Replace or read the velocity. |
| `setAngularVelocity(handle, velocity)` | Replace the spin, in radians per second. |
| `addImpulse(handle, impulse)` | Push with mass taken into account. |
| `moveKinematic(handle, position, rotation, deltaTime)` | Drive a `KINEMATIC` body to a pose, pushing what it meets. |
| `setActive(handle, active)` / `isActive(handle)` | Put a body to sleep or wake it. A sleeping body still collides. |
| `setContinuousCollision(handle, true)` | Stop a fast body tunnelling through thin geometry. |
| `applyBuoyancy(handle, surfaceY, buoyancy, deltaTime)` | One step of push from water whose surface is at `surfaceY`. Call it every step. |
| `shiftOrigin(offset)` | Move every body; see [Large worlds](large-worlds.md). |

`PhysicsBody.handle` is the body's handle once `PhysicsSystem` has created it.

## How it works

`PhysicsSystem` is an ECS system over entities with both `Transform` and `PhysicsBody`. On its first
update it creates each body from the entity's transform. From then on the simulation owns the pose:
each update steps the world once and writes positions and rotations back into the transforms, with
interpolation between fixed steps. Changing `PhysicsBody.motionType` rebuilds the body from rest.

!!! warning "Destroy the body before the entity"
    Nothing destroys a body for you. Call `PhysicsSystem.destroyBody(world, entity)` before
    `World.destroy(entity)`; otherwise the body stays in the simulation, solid and unreachable.

## Debugging

`physicsDebugLines(world)` returns world-space wireframes of every body's collider at its simulated
pose, coloured by motion type. Draw them with your other debug lines to see where a collider and its
mesh disagree.

## See also

- [Jolt backend](physics-jolt.md) for creating a `PhysicsWorld` and platform notes.
- [Character controller](character-controller.md) for a player that walks on physics.
- [Terrain](terrain.md) for the heightmaps a heightfield is built from.
- [Large worlds](large-worlds.md) for streaming colliders and shifting the origin.
