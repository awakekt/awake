# Gizmo rotation: scoping audit gap #13

Scope for the scene-editor audit's P1 #13, *"Euler-radian rotation summed per component -- gimbal
lock, no arbitrary-axis rotate"*. Written before any change, because the fix has two credible shapes
with very different blast radii and the choice should be made on evidence.

## The defect is sharper than "gimbal lock"

`Transform.rotation` is a `Vec3f` of Euler radians, and `SceneGizmo.applyDrag` adds into one
component per axis handle:

```kotlin
transform.rotation.x += axis.direction.x * radians
```

`Quat.fromEuler` writes `Qx * Qy * Qz`
([Quat.kt:147](../../awake/core/math/src/commonMain/kotlin/io/github/awakelab/awake/core/math/Quat.kt)),
and `Quat`'s multiply composes in **apply-order** — `a * b` means apply `a`, then `b` — so the
matrix is **R = Rz(z) · Ry(y) · Rx(x)**, with `z` outermost. (This was measured, not read: the
first version of this document had X and Z the wrong way round because it assumed the other
convention.) Adding `d` to each component therefore means three different things:

| Handle | Algebra | What it actually rotates about |
|---|---|---|
| Z | `Rz(z+d)·Ry·Rx` = `Rz(d)·R` | **World Z. Correct.** |
| Y | `Rz·Ry(d)·Ry·Rx` | The axis `Rz` maps Y to — world Y only while `rotation.z == 0` |
| X | `Rz·Ry·Rx(x+d)` = `R·Rx(d)` | The object's **local** X, not world X |

So the three rings look identical, are labelled by world axis, and obey three different rules. Only
one of the three is the rotation the handle implies, and the other two go wrong on any object that
already has a rotation — not just near a pole. Gimbal lock proper is at `rotation.y == ±90°`: `y` is
the middle term of `Rz·Ry·Rx`, so that is where the outer and inner axes collapse onto one. It is
the extreme case of the same problem, not the whole of it.

This is why the gap survived: the obvious manual test is rotating one axis of an unrotated object,
which is exactly the case that works.

## What is already in place

- **`Quat` is complete and tested** — `fromEuler`, `toEuler`, `fromAxisAngle`, `toMat4`, `nlerp`,
  with `QuatOpsTest` covering them. `toEuler`'s KDoc already names `Transform.rotation`'s convention
  and composition order, so the conversion is not the risky part.
- **The engine's skeletal path is already quaternion-native.** `Bone.rotation` is a `Quat` and
  `AnimationPose` nlerps quaternions; glTF loads quaternions directly.
- **`Transform` is the last Euler holdout**, and it is a lossy boundary in both directions. Jolt
  produces a quaternion which `quatToEulerVec3` degrades to Euler every frame in
  `PhysicsSystem.syncTransforms`, purely because `Transform` cannot hold the original.

## Option A — fix the gizmo, leave the component alone

Compose the drag as a quaternion and write the result back through Euler:

```kotlin
val turned = Quat.fromEuler(transform.rotation) * Quat.fromAxisAngle(axis.direction, radians)
val euler = turned.toEuler()
transform.rotation.set(euler.x, euler.y, euler.z)
```

`current * delta`, not the other way round: the multiply composes in apply-order, so that is the
pre-multiplication a world-axis turn means.

- **Fixes:** all three handles rotate about the world axis they are drawn on; arbitrary-axis
  rotation becomes expressible, which is what local/parent space (#10) will need.
- **Does not fix:** storage is still Euler, so a rotation at the poles still has no unique
  representation and repeated drags through one will snap. Physics still round-trips per frame.
- **Blast radius:** `SceneGizmo` only. No component change, no serialization change, no undo change
  (`SceneVectorCommand` still snapshots the same `Vec3f`).
- **Cost:** roughly 30 lines and two tests.

## Option B — `Transform.rotation` becomes a `Quat`

- **Fixes:** everything in A, plus the poles, plus the per-frame physics degradation, and puts the
  transform on the same footing as the skeletal path.
- **Blast radius:** ~23 non-test call sites and 20 test files mention `.rotation`. Specifically:
  `computeLocalMatrix`/`setEulerTRS`, `PhysicsSystem` (improves), `SpinSystem`,
  `SceneEntityCommands`' snapshot, the inspector's `Vec3Field`, and `SceneWorldExport`.
- **Two decisions it forces:**
  - *The inspector still shows Euler*, because nobody authors a rotation as four numbers. That means
    displaying `toEuler()` and writing `fromEuler()`, and a field that cannot round-trip at the
    poles — the same compromise every editor makes, and the reason they keep a separate Euler hint
    alongside the quaternion.
  - *`SceneTransform.rotation` stays Euler* in the document, so authored scenes stay readable and
    `SCENE_SCHEMA_VERSION` does not move. The conversion happens at load and save.
- **Undo:** rotation drags currently record a `SceneVectorCommand` over a `Vec3f`. A quaternion needs
  its own snapshot type, or `SceneValueCommand<Quat>` with a copying write.

## Recommendation

**Do A first, as its own change. Decide B separately.**

A fixes the failure a user hits on every rotate drag of an already-rotated object, is contained to
one file, and is a prerequisite for #10's local/parent space regardless of which storage wins. B is a
data-model change that reaches physics, serialization and undo; it is probably right, but it should
not ride along inside a gizmo fix, and it wants its own decision on the inspector's Euler display.

Doing A does not make B harder: A's call site becomes the one place that already thinks in
quaternions when B lands.

## The test to write first

Whichever option, the assertion is the same and fails today:

> From an orientation with all three Euler components non-zero, dragging a ring must turn the
> object about the world axis that ring is drawn on — which means the probe's component *along that
> axis* cannot change.

Stated that way it needs no knowledge of how far the drag went, which matters because the angle
comes out of a pixel distance. A single-axis starting rotation will not do: with only `x` set, the Y
and Z handles both happen to be correct, which is exactly why the bug went unnoticed.

**Status: landed.** `SceneGizmoRotationTest` failed on the X ring (`0.457 → 0.032`) and the Y ring
(`0.587 → 0.276`) before the fix, with the Z ring passing as a control.
