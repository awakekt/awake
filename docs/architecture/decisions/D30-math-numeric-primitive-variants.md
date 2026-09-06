# D30: Math — Which Numeric Primitive Variants Earn a Type

## Decision

`:awake:core:math` does **not** carry a variant of every vector/quaternion/matrix type for
every numeric primitive. A variant is added only when it answers a concrete **precision** or
**semantic** need. Symmetry is not a reason.

The admitted set:

| Type | Float | Double | Int | Other |
|---|---|---|---|---|
| Vec2 | yes — UI, UV, screen | no | yes — pixels, grid | no |
| Vec3 | yes — default | yes — absolute world coords, QEM quadrics | yes — grid, cell, voxel | no |
| Quat | yes | **no** | no | no |
| Matrix | yes | **no** | no | no |

Everything else — `Short`, `Byte`, `Long`, `UInt` — is out. Compression is a *packing
function* at a buffer boundary, not an arithmetic type.

## Context

Generalizing math types across primitives is a standing temptation, and in Kotlin it is a
trap: there are no numeric generics that stay unboxed. `Vec3<T : Number>` boxes, and per-frame
boxing is precisely what the [awake-core-math](../../.agents/skills/awake-core-math/SKILL.md)
skill forbids. So every variant is hand-written, permanent public API, carries its own
operator set, tests, and binary-compatibility surface. The cost is real and paid forever;
each type must earn it.

`Vec3f`, `Vec3d`, `Vec3i` and `Vec2` exist today. This decision fixes the rule for what comes
next rather than adding anything.

## The rule

Before adding a variant, name the concrete scenario, then check that neither of these already
solves it:

- **packing** — the value only needs to be small in a GPU buffer, not arithmetic
- **rebasing** — the value only needs precision because it is measured from a distant origin

Almost every proposed variant dies to one of the two.

## Per-type rationale

**Float — the default.** GPUs consume float32. Anything render-facing has no alternative.

**Double — one real scenario: unbounded absolute world coordinates.** float32 carries about 7
decimal digits; at 100km from the origin that is roughly 1cm of quantization, and it visibly
jitters. This is exactly why `FloatingOriginSystem` and `WorldOrigin` exist. A second, narrower
case is already in the tree: `MeshSimplifier`'s QEM quadric accumulation, where error metrics
sum over many triangles and float32 loses the small terms.

**Int — grid and pixel coordinates.** Legitimate, but the value is *semantic*, not "the int
version of Vec3". Prefer a named type (`WorldCellCoord`, which already exists) over a generic
integer vector wherever the domain has a name.

**Short / Byte / Long / UInt — no.** Packed normals and quantized positions are handled by
[VectorPacking.kt](../../awake/core/math/src/commonMain/kotlin/com/awakekt/awake/core/math/VectorPacking.kt),
which converts at the buffer boundary. You never want arithmetic in a quantized type — the
error compounds and the code reads as if it were exact.

**No `Quatd`.** Rotations are unit-length and bounded. Quaternion precision does not degrade
with distance from the origin, which is the only reason `Vec3d` earns its place. No scenario
produces the need.

**No `Mat4d`.** The large-world rendering problem looks like it wants a double matrix and does
not. The standard fix is camera-relative rendering: subtract the camera position *in double*,
downcast the result to float, build the matrix in float. That needs `Vec3d` — which exists —
and never a double-precision matrix. The GPU could not consume one anyway.

## Consequence found while deciding

`WorldOrigin.toAbsolute` returns `Vec3f`
([WorldOrigin.kt](../../awake/scene/scene-core/src/commonMain/kotlin/com/awakekt/awake/scene/world/WorldOrigin.kt)).
The absolute world coordinate is the one quantity in the engine that is genuinely unbounded —
the quantity floating origin exists to keep out of float32 — and it is currently returned at
float32 precision. `Vec3d` is already in the tree and has no other production user besides
`MeshSimplifier`.

Not urgent while worlds stay small, and not a bug today. It is the precise scenario `Vec3d`
was added for, and it should be revisited when world size grows or when physics origin
shifting lands ([physics-open-world.md](../plans/physics-open-world.md), Tier 0 item 1).

## Status

**DECIDED (2026-08-30).** No code change. Follow-up noted above on `WorldOrigin.toAbsolute`.
