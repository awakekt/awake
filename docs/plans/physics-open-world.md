# Physics Plan — Open World & Character

What the physics subsystem is missing before Awake can carry an open world with a
player character, and the order to close it in.

Scope: `:awake:physics:api`, `:awake:backend:jolt`, `:awake:scene:physics`, and the seams
where they meet world streaming and character movement. Rendering-side character work
(modular skeletal meshes, terrain LOD) is covered separately in
[RFC_OPEN_WORLD_ENGINE_SUBSYSTEMS.md](../RFC_OPEN_WORLD_ENGINE_SUBSYSTEMS.md); this plan
does not repeat it.

## Current state

`PhysicsWorld` exposes `createBody` / `destroyBody` / `step` / `syncTransforms` / `raycast`
([PhysicsWorld.kt](../../awake/physics/api/src/commonMain/kotlin/com/awakekt/awake/physics/PhysicsWorld.kt)).

- Shapes: `BoxShape`, `SphereShape`, `HeightFieldShape` (STATIC only).
- Two hardcoded object layers, one broadphase layer, `MAX_BODIES = 5_000`
  ([JoltPhysicsWorld.kt](../../awake/backend/jolt/src/desktopMain/kotlin/com/awakekt/awake/physics/jolt/JoltPhysicsWorld.kt)).
- Four duplicated backend implementations: desktop and Android on jolt-jni, iOS on JoltC
  cinterop, wasmJs on JoltPhysics.js.
- ECS bridge is
  [PhysicsSystem](../../awake/scene/physics/src/commonMain/kotlin/com/awakekt/awake/scene/physics/systems/PhysicsSystem.kt):
  lazy one-shot body creation, one `step`, one `syncTransforms` per `update`.

Already built, and deliberately **not** wired to physics yet:

- `WorldPartitionSystem`, `AsyncWorldCellStream`, `FloatingOriginSystem`, `WorldOrigin`
  (`:awake:scene:scene-core`)
- `NavGrid*` streaming (`:awake:scene:navigation`)
- `FixedTimestepLoop` (`:awake:core:host`)
- `MutableHeightmap` (`:awake:asset:terrain`)
- `DebugVisualizationSystem` (`:awake:scene:rendering`)

Every item below is a real absence confirmed against the tree, not a wishlist.

---

## Tier 0 — open world is incorrect without these

### 1. Floating origin does not move physics bodies — LANDED 2026-08-30

`FloatingOriginSystem` rebased `Transform`s while Jolt bodies kept their old coordinates, so
the next `syncTransforms()` snapped every entity back.

Fixed by `PhysicsWorld.shiftOrigin(offset: Vec3f)` plus `PhysicsOriginShiftListener` in
`:awake:scene:physics`, which keeps `:awake:scene:scene-core` from having to see the physics
module. See the D28 note for the verification detail worth remembering: bodies are moved *and
then stepped*, because the simulation gets the last word on a position.

`51d8f6158` shipped desktop, iOS and wasm but not `androidMain`, leaving that target unable to
compile — a missing override against an interface member that is not optional. Fixed
2026-08-31 by copying the desktop body verbatim, which is what that source set being a
duplicate means in practice, and exactly the tax Tier 3 describes: three of four backends is
not a partial landing, it is a red build nobody sees until someone builds Android.

Related, worth settling in the same pass: `WorldOrigin.toAbsolute` returns `Vec3f`
([WorldOrigin.kt](../../awake/scene/scene-core/src/commonMain/kotlin/com/awakekt/awake/scene/world/WorldOrigin.kt)).
The absolute coordinate is the one genuinely unbounded quantity in the engine — the thing
floating origin exists to keep out of float32 — so it is the exact case `Vec3d` was added for,
and `Vec3d` has no production user today besides `MeshSimplifier`. Not a bug at current world
sizes; see [D30](../decisions/D30-math-numeric-primitive-variants.md).

### 2. No physics streaming — LANDED 2026-08-31

`PhysicsCellStreamer` implements `AsyncWorldCellStreamListener`: a cell's shape is built off the
frame thread and its body created on it, mirroring `MeshCellStreamer` down to the cell-centre
convention so a streamed collider lands where the streamed mesh does.

It spawns an entity carrying `PhysicsBody` rather than creating a body directly, so `PhysicsSystem`
owns creation exactly as it does for authored bodies and the collider appears in
`physicsDebugLines` like any other. Unloading destroys the body **and then** the entity, because
nothing else will — `PhysicsSystem` never notices an entity disappearing, so removing one on its
own leaves its body simulating forever, invisible and unreachable.

**Jolt's own shape build is still on the frame thread**, inside `createBody`. Jolt can cook a
shape on any thread, but `PhysicsWorld` has no separate cook step, and adding one is a new method
on four backends — worth doing when a profile says the cook rather than the sample generation is
what costs.

### 3. Physics is not on a fixed timestep — mostly already built; this entry was wrong

The engine half of this is done and has been for a while. `FixedTimestepLoop` accumulates,
drains in fixed steps, clamps substeps against the spiral of death, and computes a render
`alpha`; `SceneSchedule.advance` already drives it, and any system registered through the
DSL's `fixedSystem(...)` receives exactly `fixedDelta`. Seven tests cover it, including
`everyFixedUpdateCallReceivesExactlyFixedDelta` and the dropped-remainder behaviour.

So `PhysicsSystem.update` does not pass a variable delta to `step()` because of a missing
loop. It is simply that **nothing wires physics into the schedule at all** — no
`JoltPhysicsWorld` is constructed anywhere outside its own module, and `PhysicsSystem` has no
caller but its own tests. The defect is a wiring hazard, not a missing mechanism: register it
with `fixedSystem(...)` and it is correct; register it with `system(...)` and it is not.

What genuinely remains:

- **Wire physics into a real scene**, so the guarantee is exercised rather than assumed. This
  is also the prerequisite for the two items below meaning anything.
- **Render interpolation — LANDED 2026-09-01.** `SceneSchedule` was where `alpha` died: its
  `render` lambda took no parameter. `InterpolatedSystem` is the seam, `PhysicsSystem` implements
  it, and it keeps each body's last two poses to blend between. Rotation is blended as a quaternion
  and converted to Euler afterwards, never the reverse — halfway between 350 and 10 degrees is 180,
  so a body spinning past the wrap would snap right round once per revolution.
  `update` still writes the newest pose, so a caller that never interpolates is unchanged.
  Rendering is deliberately one step behind rather than extrapolated: extrapolation invents poses
  the simulation never had and has to visibly correct them the frame a body hits something.

### 4. `syncTransforms()` is O(all bodies) per frame and allocates — LANDED 2026-08-31

Replaced by `forEachBodyTransform`, which hands the caller scratch values instead of allocating a
list plus a vector and a quaternion per body. jolt-jni and JoltPhysics.js enumerate only their
awake bodies, so a settled scene now costs almost nothing to read back. JoltC has no
`GetActiveBodies`, so iOS still visits everything it tracks — correct, just more work, and the
third capability that binding lacks after heightfields and a hit collector.

`syncTransforms()` survives as an extension for tests and tools, built on the visitor and openly
allocating.

The consequence worth remembering: **a body may never be visited at all.** Static bodies are
never awake and settled ones stop being awake, so neither appears. Three existing tests broke on
exactly that and were rewritten to ask the simulation where a body is, or to keep the last pose
reported — which is what a game does anyway, since its `Transform` holds it. Nothing in the API
reads one specific body's pose; if that is ever needed, it is a new method rather than a return
to the list.

### 5. Euler angles in the physics API — LANDED 2026-08-31

`BodyTransform.rotation` is a `Vec3f` of Euler radians, so every frame does a
quaternion → Euler → quaternion roundtrip. Gimbal lock and drift on any tumbling body, and
wrong character facing at pitch extremes.

Fixed: `BodyTransform.rotation` and `createBody`'s `rotation` are now `core.math.Quat`, so all
four backends hand the solver's own quaternion straight through. `QuatEuler.kt` is deleted —
its `quatToEulerVec3` was proven bit-identical to `Quat.toEuler()` (delta exactly 0.0 over 500
random quaternions) and its `eulerVec3ToQuatWxyz` has no callers left.

`Transform` still stores Euler, so the conversion did not disappear — it moved from four
backends to one place, `PhysicsSystem`, where the engine's convention genuinely changes. That
conversion uses a new in-place `Quat.toEuler(target)`, because the existing `toEuler()` builds
a `Mat4` per call and this runs per body per frame. `QuatTest` pins the hand-expanded in-place
formula against the matrix-based one, which is the drift `toEuler()`'s own comment warns about.

**This does not fix gimbal lock**, and the entry above overstated what it would buy. The
degenerate representation lives in `Transform.rotation`, and it stays until `Transform` carries
a quaternion — a much wider change (`Mat4.setEulerTRS`, scene JSON, camera rigs, the editor).
What landed is the prerequisite and a real deletion, not the cure.

---

## Tier 1 — character is impossible without these

### 6. No character controller — LANDED 2026-08-31

`KinematicCharacterController` in `:awake:scene:physics`: collide-and-slide over `shapeCast`, with
ground probing, a slope limit, step up and down, moving-platform velocity, contact reporting for
pushing dynamic bodies, and crouch gated on a headroom sweep before standing. Pure Kotlin above
`PhysicsWorld`, as planned.

**It can now carry a body of its own** — `CharacterConfig(innerBody = true)`, LANDED 2026-08-31.
Without one a controller is invisible to physics: it moves by sweeping shapes, so Jolt has nothing
to report contacts about and no trigger volume can detect the player. The body is kinematic, follows
the character, and is excluded from the controller's own sweeps — which is what item 10's `ignore`
was needed for, since a body at the character's own position is nearer than anything else in every
sweep it makes. Removing that exclusion fails three tests.

**An inner body does not give pushing for free**, which is the obvious guess and is wrong: the
controller sweeps and refuses to move into a crate, so the kinematic body never drives through one
and there is no contact to solve. Shoving things stays the caller's job through `onContact`. A test
pins that, after asserting the opposite and being corrected by the simulation.

A teleport rebuilds the body rather than driving it: `moveKinematic` derives velocity from distance
over the step, so carrying one across a respawn would send it through the level shoving everything.

The original entry follows.

Player movement writes position directly
([MatrixRelativeMovementSystem](../../awake/scene/controls/src/commonMain/kotlin/com/awakekt/awake/scene/controls/systems/MatrixRelativeMovementSystem.kt)),
so the character walks through walls and terrain.

Required behaviours, whoever supplies them:

- ground state (on-ground / on-steep-slope / in-air) and a slope limit
- step-up and step-down offsets — without these the character catches on every 20cm ledge
- moving-platform support (velocity relative to the ground body)
- character pushes dynamic bodies; characters stand on each other
- crouch as a shape swap, gated by an overlap test before standing back up

Jolt's `CharacterVirtual` implements all of it, but this plan builds the controller in pure
Kotlin above `PhysicsWorld` instead — see [Build vs. Jolt](#build-vs-jolt) for why, and
Phase 2 for the shape. Either way it depends on `CapsuleShape`, which does not exist yet, and
on the sweep query from item 10.

### 7. No velocity or force API — LANDED 2026-08-31

`setLinearVelocity`, `getLinearVelocity`, `addImpulse` and `moveKinematic` are on all four
backends. `addForce` was not added: nothing has asked for a continuous force, and an impulse covers
every consumer so far.

The original entry follows.

No `setLinearVelocity`, `addImpulse`, `getLinearVelocity`. No jump, knockback, projectile, or
kinematic platform is expressible.

Fix: `setLinearVelocity`, `getLinearVelocity`, `addImpulse`, `addForce`,
`setKinematicTarget`.

### 8. No collision events, no sensors — LANDED 2026-08-31, sensors only

`createBody(..., sensor = true)` makes a body detect what passes through it instead of blocking
it, and `drainContacts` hands over the `BEGAN`/`ENDED` pairs once per step. Pickups, zone entry
and damage volumes are expressible now.

The queue was the whole design problem, and it is the same on every backend for the same reason:
**Jolt calls its contact listener from worker threads, in the middle of `Update`.** Nothing acts
there — the callback only records, and the drain replays on the frame thread, where destroying
the body you just picked up is an ordinary call rather than re-entry into a running simulation.
The three runtimes only differ in what guards the buffer: `synchronized` on the JVM backends, a
pthread mutex on iOS (no Kotlin/Native equivalent exists, and coroutine primitives cannot be
entered from a thread Kotlin never started), and nothing at all on wasmJs, which has no threads.

Three deliberate limits:

- **Only sensors report.** Jolt reports every touching pair in the scene on every step;
  forwarding all of it would allocate an event per contact per frame for events nothing reads.
- **No contact point, normal or impulse.** `ENDED` arrives from a callback that carries none of
  them, so they would be present half the time. A hit reaction that needs an impact point adds
  them; a trigger does not.
- **One event per sub-shape pair, not per body pair.** Two convex shapes touch once, so a sensor
  and a crate produce one `BEGAN` — but a body with several parts in contact reports each.

`MeshShape` and `HeightFieldShape` are rejected as sensors: both are surfaces with no inside, so
"what is inside me" has no answer for either.

**Solid-body contacts LANDED 2026-09-01**, as the filter half only: `setContactReporting` opts a
solid body in, and it is silent until asked -- Jolt offers every touching pair in the scene, so
reporting them all would queue an event per pair per step for a settled pile of crates.
Still missing: the **manifold read**. All four bindings expose a contact normal and a penetration
depth; none exposes contact *points* the same way, so that is where the work is. Deferred rather
than guessed at, because the verbs that asked for this -- hit sounds, impact damage -- need to know
two bodies touched and can read impact speed from velocity.

**And one that surfaced only when the showcase tried to use it: a sensor cannot detect the
player.** `KinematicCharacterController` owns no body -- it moves by sweeping shapes -- so there is
nothing for Jolt to report contacts about. An inner kinematic body following the character is the
standard answer (Jolt's own `CharacterVirtual` has one), but it is blocked here: the controller
sweeps unfiltered, so it would immediately collide with its own inner body, and neither escape
exists yet. `onlyLayer` restricts a sweep to *one* layer while a character needs to hit both level
and props, and excluding a specific body needs the multi-hit queries of item 10 -- with a
closest-hit-only cast, a hit on the ignored body hides everything behind it. So the showcase's
trigger fires on the crates. **Multi-hit queries unblock the player trigger**, which is a better
reason to do them than the one already written down.

### 9. Layers are hardcoded to two — LANDED 2026-08-31, minus masks

`CollisionLayers` is now the world's own matrix: a layer count, which layers collide, and which
of them move. Every backend builds its Jolt tables from it instead of from a hardcoded pair, and
bodies carry a `CollisionLayer`. Broadphase is now two trees rather than one, static and moving,
which is what it should always have been.

Queries take `onlyLayer`, and that is the compromise worth knowing about. **It restricts a query
to one layer rather than to a set.** jolt-jni exposes only `SpecifiedObjectLayerFilter`, whose
JoltPhysics.js counterpart is also single-layer, and the mask-capable `DefaultObjectLayerFilter`
has a package-private constructor there. So "everything except my own layer" — what a character
sweeping around its own body wants — still cannot be said. iOS is the exception, since JoltC
takes an arbitrary predicate, but an API is only as portable as its worst backend.

Closing that gap means either a jolt-jni change upstream or a custom filter per backend; it is
not blocked on design here.

### 10. The query surface is a single raycast — LANDED 2026-08-31

`shapeCast` (sphere/box/capsule sweeps, with a layer filter) landed earlier; `overlapShape` and
`shapeCast`'s `ignore` land now. That is the whole list bar foot IK's ground probe, which is a
`shapeCast` a consumer can already write.

**`overlapShape` visits every body inside a convex volume**, which no cast can do — a sweep stops
at the first thing that would block it, so an explosion built out of casts finds one victim.
A visitor rather than a list, for the same no-garbage reason as `forEachBodyTransform`.

**`ignore` skips one body inside the query**, which is what a character with a body of its own
needs: its own body is nearer than anything else in every sweep it makes. Filtering the result
afterwards cannot substitute, because a closest-hit cast only ever reports the ignored body, and
everything behind it stays invisible.

The backends split on how, and the spike that settled it is worth keeping: **jolt-jni's
`BodyFilter` cannot be overridden.** It has a public `shouldCollide` and no callback subclass, and
an override is simply never called — measured, not assumed: a filter rejecting everything left the
ray hitting the body anyway. So the JVM backends collect every hit and take the nearest other one,
which allocates; iOS builds a real `JPC_BodyFilter` and wasmJs uses `IgnoreSingleBodyFilter`, both
of which skip inside the query. Same semantics, three routes.

**Casts do not report sensors**, and finding that out was worth the spike. A sweep at a sensor hit
it at fraction 0.17 — meaning the goal-zone trigger added an hour earlier was a solid obstacle the
character walked into, invisible and unexplained. A sensor is not solid, so it is not an answer to
"what would block me". `shapeCast` and `raycast` skip them; `overlapShape` is the query that sees
them. Casts ask what stops you, overlaps ask what you are inside.

On the JVM that costs a second query in the rare case: the closest-hit collector already told Jolt
to stop looking, so when the nearest hit turns out to be a sensor or the ignored body there is
nothing behind it to fall back to and the whole set has to be collected. iOS and wasmJs do it in
one pass, inside the body filter.

Not done, and deliberately: **no `ignore` on `raycast`**, because nothing casts a ray from inside
its own body — the character sweeps. And **no multi-hit cast returning every hit along a sweep**
(a piercing projectile), which has no consumer; `overlapShape` covers the cases that do.

### 11. No `MeshShape` or `ConvexHullShape` — LANDED 2026-08-31

`MeshShape` (static level geometry) and `ConvexHullShape` (dynamic props) are on all four
backends. A mesh takes the same vertex and index arrays the renderer's geometry carries, so a
collider is built from the mesh being drawn rather than from a second description of it.

Two things worth knowing. **Winding decides which side of a mesh triangle is solid** -- a floor
wound the wrong way lets bodies fall straight through while rays still hit it from both sides,
which reads as anything except a winding bug; it cost a debugging round here and is now in
`MeshShape`'s own docs. And a mesh is a *surface*: no inside, no mass, static only. Anything that
moves and is not a box wants a hull, which loses concavity in exchange for having a volume.

`ConvexHullShape` cannot be swept on wasmJs -- a hull does not flatten into the parameters that
backend's cast path carries, and nothing sweeps a hull today; characters sweep capsules.
`CompoundShape` and `RotatedTranslatedShape` are still absent.

### 12. No continuous collision detection flag — LANDED 2026-08-31

`PhysicsWorld.setContinuousCollision(handle, enabled)`, on all four backends. A setter rather than
a `createBody` argument, for two reasons: every binding exposes `SetMotionQuality` on the body
interface so runtime change costs nothing, and whether a body needs it depends on how fast it is
*now* — unlike `sensor`, which is what a body is. It would also have been `createBody`'s seventh
parameter.

Measured rather than asserted: a 0.1-radius sphere at 400 units/s against a 0.1-thick wall crosses
it in one step without this and is stopped by it with, and `JoltContinuousCollisionTest` asserts
**both** directions. The tunnelling half is the control — "it stopped" proves nothing if the bullet
was never fast enough to tunnel — and it behaves identically on desktop, iOS and wasmJs.

Two limits, both in the KDoc. **It protects the body it is set on, not the ones it hits**: whichever
thing moves fast is the one that needs it. And it is a shape cast per step per body, so it is for
projectiles and long falls, not for crates.

---

## Tier 2 — needed for it to feel like an open world

- **Terrain**: `HeightFieldShape` is STATIC-only and one body for the whole map.
  **Tile cutting — LANDED 2026-08-31.** `heightFieldTile` slices one tile out of a larger sample
  grid and `heightFieldTileCenter` says where its body goes, for both a centred authored heightmap
  and a corner-anchored streamed one. **Tiles share their edge samples** — tile `k` starts at
  `k * (samples - 1)`, not `k * samples` — and that off-by-one is the whole difficulty: cut them
  disjointly and every seam is a one-sample wall to walk into or a gap to fall through, which looks
  like a physics bug rather than a slicing one. Asserted against a real Jolt world on all four
  backends, and cutting disjointly fails three of the five tests.
  **Streaming and deform — LANDED 2026-08-31.** `heightFieldCellStreamer` builds a
  `PhysicsCellStreamer` whose `shapeFor` cuts a tile per cell, and **derives the cell size** rather
  than taking one: the streamer places a body at `coord * cellSize + cellSize / 2` and a tile is
  centred on its own `(tileSamples - 1) * scale` extent, so a hand-passed cell size that disagrees
  offsets every collider from its terrain uniformly — which reads as the whole world being subtly
  wrong, not as a configuration mistake.
  `PhysicsCellStreamer.reloadCells` rebuilds a deformed cell's collider, keeping the same
  off-thread/frame-thread split as `loadCell`, and only for cells that are actually resident.
  `tilesTouchedByEdit` maps an edit's sample bounds to the cells to rebuild — **a sample on a tile
  boundary invalidates both sides**, the deform-time form of the shared-edge rule, because
  rebuilding one leaves the seam standing as a wall exactly where the player dug.
  Both properties are mutation-checked: a wrong cell size and a one-sided invalidation each fail
  exactly the test that owns them.
  **Partial heightfield update — DECLINED 2026-09-01, on a fact rather than a preference.** Jolt can
  update a heightfield's samples in place, and both jolt-jni (`setHeights`) and JoltPhysics.js
  (`SetHeights`) expose it. **JoltC exposes no heightfield at all** — zero symbols — which is why
  iOS builds its heightfields as triangle meshes in the first place, and a triangle mesh has no
  samples to update. So this would be a capability three targets have and the fourth answers by
  rebuilding, which is what all four already do. The rebuild path is tested everywhere and the win
  applies only to *continuous* edits, which nothing here does. Revisit alongside a terrain-sculpting
  tool, if one is ever built.
  **iOS heightfields — CLOSED 2026-08-31, without forking JoltC.** JoltC exposes no
  `JPC_HeightFieldShapeSettings` and still does not; that target builds the field as a triangle
  mesh instead. A heightfield *is* a grid of triangles — the dedicated shape is a compression of
  one — so the binding was missing an optimisation, not a capability, and `HeightFieldShape` now
  works on all four backends. The cost is real and scales badly: one float per sample becomes
  three per vertex plus six indices per cell, irrelevant at 9x9 and half a million triangles at
  512x512, which is an argument for per-tile fields rather than against the approach.
  The three heightfield tests moved back to `commonTest` and a fourth was added — **a box resting
  on the field**, which is the only assertion that catches winding. Rays hit a backwards-wound
  surface from both sides; bodies fall through it.
- **Water and buoyancy** — buoyancy LANDED 2026-08-31; volume and swim state not.
  `PhysicsWorld.applyBuoyancy(handle, surfaceY, Buoyancy, deltaTime)` on all four backends, with a
  `Buoyancy` settings type whose `strength` is the parameter that decides float from sink (1 is
  neutral, which looks like a bug).
  **Called per step, per body in the fluid** — it is one step's worth of push, not a state. Physics
  has no idea where the water is; a sensor covering the volume does, which is what makes this
  compose with the contact events rather than needing a water-volume concept in the backend.
  Deliberately a horizontal surface at a height, not an arbitrary plane: every water body in a game
  is level.
  **Sleeping bodies are skipped**, found only by running the suite on an Android device: buoyancy
  adds velocity, and a sleeping body has no motion state to receive it. Android ships jolt-jni's
  *debug* artifact, so it asserts where desktop's release build runs on quietly — which makes that
  target a checked build in the same way the iOS simulator is, and worth remembering as the place
  misuse surfaces first.
  The first attempt at that guard asked `BodyInterface.isActive` while already holding a write lock
  on the same body, which is a recursive lock and crashed a *different* test. It asks the locked
  body instead. That is the second time nested body locks have bitten in this plan, after iOS's
  constraint creation.
  The binding split is worth knowing: wasmJs exposes this on `BodyInterface`, while jolt-jni and
  JoltC have it only on `Body`, so those two take a body lock — which doubles as the "is that handle
  still alive" check. **Dynamic bodies only, enforced**: Jolt's own call reaches for motion
  properties a static body does not have and *aborts the process* rather than returning. The KDoc
  claimed that guard before the code had it, and the static-body test found it as a SIGABRT.
  **The volume LANDED 2026-08-31**, in the showcase: a sensor box is the pool, contact events track
  who is inside it, and the fixed step applies buoyancy to each occupant. That composition is the
  point — physics has no idea where water is, and a backend owning a water volume would have to own
  its shape, its currents and its edges too.
  Sample-side rather than engine, per the framework boundary: one consumer, and nothing here that a
  game could not write. What the engine supplies is `applyBuoyancy` and sensors.
  Two things the exercise taught. **There can be only one drain** — `drainContacts` hands over the
  events since the last call and forgets them, so the goal zone and the pool share a single loop
  that routes by which sensor was touched; a second caller would see only what the first left, which
  is nothing. And **a pool whose surface sits at the height of its own bed demonstrates nothing**:
  floating and resting come to rest in the same place, so the feature and its absence look
  identical. A placement test now asserts the surface clears the terrain under it.
  **Swimming LANDED 2026-08-31**, and needed no controller change at all. Vertical velocity is
  already the caller's to integrate — that is the controller's own guarantee, that it only ever
  removes motion from what it was handed — so a swim state is the caller integrating differently
  while the pool reports the player inside it. `applyBuoyancy` could not have done this job: the
  character's body is kinematic, and buoyancy only moves dynamic ones.
  The three ways to get it wrong are each a test: no drift is a swimmer hanging motionless, full
  gravity is a swimmer falling, and a drift stronger than the swim speed is water that cannot be
  climbed out of.
- **Constraints and joints** — hinge and distance LANDED 2026-08-31; the other eight not.
  `HingeConstraint` is every door, lid, lever and wheel; `DistanceConstraint` is every rope, chain
  and grapple. Two rather than ten because two cover the verbs this entry named, and guessing at
  parameters nobody has used is how an API grows surface it cannot justify. Anchors are world-space
  and read once, so whatever arrangement the bodies are in at creation is the rest pose.
  **A constraint dies with either body it joins, and the backends enforce that.** Jolt does not
  detach one when a body is destroyed — it crashes on the next step — so `destroyBody` removes the
  constraints referencing it first, and `destroy()` clears them before freeing bodies.
  Three implementation traps, each found by a test rather than reasoned about:
  - **Two write locks at once is a Jolt lock-ordering assert.** iOS took nested locks to reach both
    bodies and died in `BodyLockInterfaceLocking::LockWrite`; it now takes one at a time, which is
    what the JVM backend already did. JoltC has no no-lock body interface, unlike wasmJs.
  - **A constraint is ref-counted, so freeing it by hand double-frees.** On wasmJs that corrupted
    the shared heap and surfaced as *unrelated* tests failing later, not as a fault at the call.
  - **Jolt wants a hinge's normal axis and does not derive one**, so it is computed perpendicular
    to the hinge axis rather than left at whatever the settings defaulted to.
  **Ball-and-socket** LANDED 2026-09-01, built on six-DOF with translations locked and rotations
  limited to a cone plus a twist range. Cone rather than Jolt's pyramid swing: JoltC's settings
  struct has no swing-type field at all, so cone is the one the four bindings agree on, and a cone
  is symmetric — an asymmetric joint wants a hinge.
  Still missing: point, slider, pulley, gear, rack-and-pinion, and fixed. Ladders
  are not a constraint at all — that is a movement mode on the character.
- **Ragdoll** — a physical one LANDED 2026-09-01; skeleton mapping not.
  `Ragdoll` builds limbs and joints above `PhysicsWorld`, and `humanoidRagdoll` describes an
  eleven-limb figure. Not a binding to Jolt's own `Ragdoll`: **JoltC exposes none at all** — zero
  ragdoll or skeleton symbols — so binding it would work on three targets and throw on the fourth.
  A ragdoll *is* bodies and constraints, and Jolt's class is convenience over that.
  **Ball joints were approximated as distance constraints first, and finding out why that fails cost
  a wrong assumption.** Jolt solves a distance constraint along the axis between its two points, and
  when they coincide that axis is undefined, so the solver never converges — eleven limbs still
  awake after fifteen seconds. Self-collision was the first suspect and was wrong: putting every
  limb on a layer that does not collide with itself changed nothing, which is what sent the search
  to the constraints. Superseded 2026-09-01 by a real `BallSocketConstraint`.
  **A joint limit is measured from the pose the ragdoll was built in**, which is the thing to know
  before choosing one. A figure built standing has to swing its thighs a right angle out of that
  pose merely to lie on the floor, so the first hip cone (72°) left it propped against the ground
  forever, all eleven limbs awake. 120° — real hip flexion — settles.
  **The three backends converge at visibly different rates** on a chain of limited six-DOF joints:
  asleep by 300 steps on desktop, 600 on wasm, 900 on the iOS simulator. A settling threshold picked
  from whichever backend is to hand is a test that fails on somebody else's machine.
  **Skeleton mapping** LANDED 2026-09-01. `RagdollSkeleton` walks each limb's world pose down the
  bone hierarchy into the parent-relative form `AnimationPose` stores, so a corpse wears its own
  mesh instead of being invisible capsules next to a character still playing its idle. Quaternions
  throughout, no matrix inverse: a rigid body and a bone driven by one are rotation plus translation
  with no scale. A bone no limb is bound to keeps what the animation left there.
  `Ragdoll.forEachLimb` retains its last known poses, because `forEachBodyTransform` reports only
  what a backend has awake — a caller reading straight from the world watches limbs vanish one by
  one as the figure settles. Fixed once here rather than in every consumer.
  Still missing: nothing named for ragdolls. What is left is applying one in the showcase, which is
  scene wiring rather than a physics capability.
- **Vehicles**: Jolt `VehicleConstraint`, wheeled and tracked. Large surface — build only on
  demand, ship last. Still nothing demands one, so it is still not built. Named here so that
  "everything else on this plan is done" does not read as "vehicles were forgotten".
- **Sleeping and activation** — control LANDED 2026-08-31; the policy deliberately not.
  `PhysicsWorld.setActive(handle, active)` and `isActive(handle)` on all four backends. Jolt already
  sleeps a settled body on its own, which is why this is control over *when* rather than a mechanism
  that did not exist — and why `forEachBodyTransform` stops reporting a body that has come to rest.
  **Deactivating is not disabling.** A sleeping body still collides and is woken by whatever hits
  it; it stops *integrating*, not existing. An open world that sleeps its distant scenery does not
  become scenery the player falls through. The test that asserts this was wrong first: it used a
  dynamic box in mid-air as the floor, so waking it just dropped it and the faller together.
  Two consequences follow from elsewhere in the contract and both bite: a sleeping body is not
  visited by `forEachBodyTransform`, and **a sensor only detects active bodies**, so deactivating
  something inside a trigger volume makes the trigger forget it.
  **The distance-based policy is not here, on purpose.** Nothing consumes it, and the framework
  boundary admits a capability on two consumers or on a limitation a consumer cannot work around —
  neither applies. A game deactivating what is far from its player needs no engine support beyond
  these two methods, and what "far" means is a game's decision.
  `setAllowSleeping` was skipped for the same reason: no consumer, and it is `Body`-only in every
  binding, so it would need a body lock on three backends to serve nothing.
- **Physics debug draw** — LANDED 2026-08-31. `physicsDebugLines(world)` builds world-space
  wireframes of every collider at the pose the simulation has it in, coloured by motion type, and
  the showcase has a "Colliders" toggle. Lines are returned rather than drawn, matching
  `navGridDebugLines`, because `drawDebugLines` replaces the frame buffer instead of appending.
  Heightfields draw as a footprint rather than a sample grid: the useful question about terrain
  collision is where its edges are.

---

## Tier 3 — engine hygiene that will bite

- **Fourfold backend duplication — the conformance suite LANDED 2026-08-31.** Every API in this
  plan is still written four times, but the suite that keeps them honest now exists: the module's
  tests live in `commonTest` and run on desktop, an Android device, the iOS simulator and a headless
  browser. It found four bugs on its first run and two more since — an iOS abort on its very first
  step, `raycast` silently ignoring its layer filter there, a wasm body filter comparing a pointer
  to an id, a mesh swept without complaint, and buoyancy crashing the process on Android.
  What stays per-backend is in `joltJniTest`, for capabilities the contract deliberately makes
  optional. The original text follows.
  Before adding ~20 methods, add a **capability conformance suite in `commonTest`** that all four
  backends must pass. The binding Phase 2 rests on, `CastShape`, has been spiked and is
  present on all four — wasm verified by execution, not by reading types
  ([D29](../decisions/D29-physics-own-engine-exit-criteria.md)). One asymmetry to carry into
  the suite: JoltC ships no prebuilt hit collector, so iOS builds one from a
  `staticCFunction` `AddHit` while jolt-jni and JoltPhysics.js both hand over a ready
  `ClosestHit` collector. Same contract, three different shapes underneath — exactly what the
  conformance suite is for.
- **Determinism — DECIDED and LANDED 2026-09-01: same-binary replay is a goal.**
  Contact events are normalised low-id-first and sorted before delivery, and every map whose
  iteration feeds teardown order is insertion-ordered. `JoltDeterminismTest` runs a scene twice and
  compares poses and contact events for **exact** equality, on all four backends.
  **The scene size decides whether that test means anything.** At six falling bodies, removing the
  sort still passed — the ordering happened to be stable. At forty, removing it fails every run.
  Jolt guarantees deterministic *simulation*, not the order in which it invokes a contact listener
  across worker threads, and it takes enough contacts in one step to see the difference.
  `ComponentStore.forEach` walks a dense array by index, so ECS iteration was already reproducible
  given the same sequence of adds and removes — which is what a replay is. No ECS change needed.
  **Cross-platform determinism is not available and is not being pursued.** The four backends are
  three different Jolt builds on three architectures, and Jolt does not guarantee bit-identical
  results across them. Networked physics here means an authoritative peer replicating state, not
  lockstep — which constrains `samples/net-demo` rather than this module.
- **Threading — MEASURED and DECLINED 2026-09-01.** `step()` runs on the frame thread, and async
  stepping (step N while rendering N−1) was the standard next move once fixed-step and interpolation
  landed. Both have landed, so the question was live; it is now answered with a number instead.
  **One fixed step of 144 dynamic bodies on a 65×65 heightfield costs 0.71 ms** — about four per
  cent of a 16.7 ms frame (`JoltStepBudgetTest`, desktop). Moving that off the frame thread buys
  almost nothing, and it would cost a hard platform split: Kotlin/Wasm has no worker threads here,
  and JoltPhysics.js's multithread flavour needs SharedArrayBuffer with COOP/COEP response headers
  the dev server does not set. So one of the four targets could never have it.
  Revisit if that number changes by an order of magnitude, which is what the test now guards.

---

## Build vs. Jolt

Recorded as [D29](../decisions/D29-physics-own-engine-exit-criteria.md), which also fixes the
response ladder for a Jolt capability gap and the one condition (cross-target determinism)
that would justify a hand-written engine.

Awake already owns a real math and geometry foundation — `Vec3f`, `Quat`, `Matrix`, `Aabb`,
`Plane`, `Ray`, `Frustum`, `Grid` in `:awake:core:math`, and mesh geometry in
`:awake:core:geometry`. Fair question: how much of this can be written here instead of bound
from Jolt? The answer splits cleanly, and the split decides Phase 2.

### Dynamic rigid bodies: keep Jolt

Math and geometry are roughly 5% of a rigid-body engine. The other 95% is not in the tree and
is not cheap:

- broadphase — an incrementally refit BVH, not a uniform grid
- narrowphase — GJK and EPA, contact manifold *generation*, and multi-frame manifold
  *persistence*; persistence is where stacked boxes stop jittering, and it is the part that
  is always underestimated
- the solver — sequential impulse or TGS, warm starting, friction cones, restitution
- islands, sleeping, deterministic ordering
- CCD, stacking stability, robustness at high mass ratios

None of this is hard to write badly; all of it is hard to write stably. Jolt is a decade-plus
of shipped tuning, Apache-2.0 (same licence as Awake), and it supplies most of this plan's
Tier 1–3 items for free. Writing a replacement means owning every stacking artifact with no
reference implementation to diff against. Not a good trade.

### Kinematic character: build it

Collide-and-slide is not a solver problem. It is: sweep the capsule, take the earliest hit,
project the remaining motion onto the contact plane, repeat a few times. Given the existing
math types, the whole controller — ground state, slope limit, step up/down, platform-relative
velocity, crouch gating — is a few hundred lines of ordinary Kotlin. The only thing it needs
from the backend is a shape sweep, which Jolt already has as `CastShape`.

Building it here buys three things:

- **One implementation instead of four.** A `CharacterVirtual` binding would be written once
  per backend (jolt-jni, JoltC, JoltPhysics.js) and would behave subtly differently in each.
- **wasm is de-risked.** JoltPhysics.js may not expose `CharacterVirtual` at all — an open
  question flagged in Tier 3. A pure-Kotlin controller removes that from the critical path
  entirely, since `CastShape` is far more likely to be exposed than the character classes.
- **It is testable without a native library.** A stub `PhysicsWorld` returning scripted sweep
  hits covers slope, step, and moving-platform behaviour in `commonTest`, on every target.

The cost is real and worth stating: character *feel* is tuning, and Jolt's controller has had
tuning this one will not have on day one. Expect to iterate on step height, slope threshold,
and de-penetration slop against the actual showcase.

### Where the line sits

`PhysicsWorld` stays a thin, backend-neutral contract over Jolt: bodies, shapes, stepping,
queries. Anything that is *policy over queries* rather than *constraint solving* belongs above
it in Kotlin — the character controller first, and later foot IK, camera collision, and spawn
validity. That keeps the four-backend duplication tax confined to the things that genuinely
need native code.

## Build order

**Phase 1 — make it stop lying** (items 1–5, plus debug draw)

Quaternions in the API → fixed step and interpolation → active-body readback →
floating-origin shift → physics cell streaming → collider debug draw. No new gameplay
capability; everything after depends on it. Debug draw belongs here, not in Tier 2 — it is
the cheapest item on the list and it pays for itself in every later phase.

**Phase 2 — character** (6, 7, capsule from 11, sweeps from 10)

The controller is **pure Kotlin in `:awake:scene:physics`, above `PhysicsWorld`** — not a
binding to `CharacterVirtual`. Rationale in [Build vs. Jolt](#build-vs-jolt); what it costs
the backends is one new query, `shapeCast`.

1. `CapsuleShape` in `:awake:physics:api`, and `shapeCast(shape, from, to, filter)` on
   `PhysicsWorld` — capsule and sphere sweeps, backed by Jolt's `CastShape`. This is the only
   new backend surface the phase needs, and it is confirmed available on all four backends
   ([D29](../decisions/D29-physics-own-engine-exit-criteria.md)). Budget extra for iOS: JoltC
   has no prebuilt collector, so it needs a `staticCFunction` `AddHit` writing into a context
   slot — the backend's first collector, though it already uses that pattern for layer filters.
2. Velocity API (item 7) — needed for jump, knockback, and for pushing dynamic bodies.
3. `KinematicCharacterController` in commonMain: collide-and-slide over the sweep query.
   Sweep → earliest hit → project the remaining motion onto the contact plane → repeat, 3–4
   iterations. Then step-up/step-down probes, slope classification against the slope limit,
   and a ground-body query for platform-relative velocity. Roughly the shape of Unity's
   Kinematic Character Controller and Godot's `move_and_slide`, on top of Awake's own
   `Vec3f`/`Plane`/`Aabb`.
4. A character driver consuming the existing `MovementControl`, registered on the **fixed**
   step. This entry used to say it *replaced* `MatrixRelativeMovementSystem`, which was wrong
   twice over: that system has no production caller to migrate, and the two are different
   tools rather than two versions of one. It writes `Transform.position` directly, which is
   correct for a spectator camera, an editor viewport, or any scene with no `PhysicsWorld` —
   and it stays. The character path is an addition beside it.

   It lives in the sample for now, not in `:awake:scene`. One consumer, and the framework
   boundary admits a capability on two consumers or on a limitation a consumer cannot work
   around. The camera-relative basis is duplicated from `MatrixRelativeMovementSystem` for the
   same reason; extract it when something else needs it.
5. Sphere-cast camera collision — same `shapeCast`, no extra backend work.

One implementation across desktop, Android, iOS, and wasm. Testable in `commonTest` with a
stub `PhysicsWorld` that returns scripted sweep hits, so slope, step, and platform behaviour
are covered without a native library or a running frame loop.

This is the phase that makes the showcase feel like a game.

**Phase 3 — gameplay verbs** (8, 9, rest of 10, 12) — COMPLETE 2026-08-31

Layers and collision matrix → contact events and sensors → overlap and multi-hit queries →
CCD. All four landed on all four backends.

What Phase 3 deliberately did not deliver, and what it would take:

- **Contact events for solid bodies** (hit sounds, impact damage). The same listener with a
  different filter and a manifold read; sensors were the blocker for gameplay verbs, and solid
  contacts are not.
- **A sensor that can detect the player.** Blocked on the character controller owning a body,
  which item 10's `ignore` now makes possible.
- **`addForce`, `CompoundShape`, `RotatedTranslatedShape`, multi-hit casts.** No consumer.

**Phase 4 — world** (Tier 2)

Mesh and convex shapes → streamed terrain tiles → water → constraints → ragdoll. Vehicles
only on request.

---

## Risk and leverage

Highest risk: items 1 (floating origin vs physics) and 5 (Euler in the API). Both are cheap
now and expensive once more code depends on them.

Highest leverage: `shapeCast`. One backend query unlocks the entire character controller, the
camera collision, spawn validity, and later foot IK — all of it written once in Kotlin rather
than four times against three different Jolt bindings.

Highest uncertainty *was* whether JoltPhysics.js exposes `CastShape`. Spiked and cleared on all
four backends ([D29](../decisions/D29-physics-own-engine-exit-criteria.md)), so Phase 2 has no
remaining unknown dependency.

What is still open, and is a decision rather than a discovery: **is cross-target determinism a
goal.** It constrains body creation order and system iteration order, it is expensive to
retrofit, and it is the one condition that would justify replacing Jolt outright. Settle it
before Phase 2.
