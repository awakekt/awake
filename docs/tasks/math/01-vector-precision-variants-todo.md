# `Vec3f` / `Vec3d` / `Vec3i` — precision variants

Drafted 2026-08-22. Status: todo. Gated on a real consumer per variant; see Triggers.

## Shape

Three **independent flat types**, no shared supertype, explicit conversions between them —
`toVec3d()`, `toVec3i()`, the way `Int.toLong()` works.

Not `Vec3<T : Number>`: Kotlin has no numeric type parameters, so `T` boxes. Three stack floats
become three heap objects per vector per frame, inside `System.update`, which is exactly what
`skills/awake-core-math` forbids.

Not `sealed interface Vec3` either, and this is the less obvious one:

| Problem | Detail |
|---|---|
| Virtual dispatch | `dot` is three multiplies and inlinable today. Behind an interface it is a call site the JIT can only devirtualise while monomorphic; a scene mixing `Vec3f` and `Vec3d` makes it bimorphic and inlining stops |
| The operations do not unify | What is `Vec3f + Vec3d`? Forbid it and the supertype buys nothing, since no code can be written against `Vec3` generically. Widen to double and float math silently allocates doubles. `Int`/`Long` share no numeric supertype for this reason |
| Forecloses `value class` | A `@JvmInline value class` boxes the moment it is used as its sealed interface type, giving up the one optimisation most worth keeping |

libGDX shipped the interface version — `Vector<T : Vector<T>>` — and its own docs say the members
are "not type safe", because `addVectors` accepts differently-typed vectors and compiles. three.js
and Compose both went the other way: `Vector2`/`Vector3` share nothing, `Offset`/`IntOffset` are
unrelated types.

## Triggers — build each when this is true, not before

| Variant | Trigger | Status |
|---|---|---|
| `Vec3d` | A second consumer needs double precision, or a world exceeds ~10 km from origin | **one hidden consumer today** |
| `Vec3i` | A chunk, voxel or tilemap grid exists | none |
| `Vec3f` rename | `Vec3d` or `Vec3i` has landed | not yet |

**`Vec3d`'s hidden consumer is `MeshSimplifier`**: 19 `DoubleArray` uses, because Garland-Heckbert
accumulates plane outer products and float loses accuracy fast. It works today on raw arrays with
`positions[i * POSITION_COMPONENTS + 1]` indexing — a `Vec3d` would replace that with names. That
is one consumer, which is why this is a trigger and not a task.

**The large-world case is the one that will actually force it.** Float carries ~7 significant
digits, so past roughly 10 km from origin a position loses sub-centimetre precision and geometry
visibly jitters. If the MMORPG world is larger than that, world-space coordinates need `Vec3d` or
a fixed-point chunk origin. Deciding that is a world-design question, not a math-library one, and
it belongs with the MMO repo's plan.

## Cost, measured

| | Uses | Files |
|---|---|---|
| `Vec3` | 1327 | 116 |
| `Vec4` | 191 | 34 |
| `Vec2` | 56 | 15 |

`Vec3` carries **24 members** and a naming contract a sibling has to mirror exactly: bare verbs
(`add`, `scale`, `normalize`) mutate and return `this`; `-ed` forms and operators allocate;
products and queries are pure. A `Vec3d` that gets this wrong is worse than none — `v.normalized()`
whose result is dropped silently does nothing, and that is a bug in one direction only.

The `Vec3` → `Vec3f` rename is 1327 sites. Mechanical and compiler-verified, but it must run
longest-name-first: `Vec3` is a prefix of nothing here, but `Vec3` appears inside `Vec3d`/`Vec3i`
once those exist, so the rename has to happen **before** the siblings or with word boundaries that
account for them.

## Order

1. **`Vec3d`, when its second consumer appears.** Mirror `Vec3`'s member set and naming contract.
   Add `Vec3.toVec3d()` / `Vec3d.toVec3()`; no implicit widening.
2. **Migrate `MeshSimplifier`** to it in the same pass — it is the proof the API is usable, and its
   existing tests are the check.
3. **`Vec3f` rename** once a sibling exists, so `Vec3` stops meaning "the float one by default".
4. **`Vec3i` when a grid exists.** Integer vectors have no `normalize`/`length` worth having;
   the member set is genuinely smaller, not a copy with a different type.

## Non-goals

- **No `Vec2d`/`Vec2i`/`Vec4d` on spec.** `Vec2` has 56 uses total and the audit found only two
  genuine consumers outside samples. Add a variant when its own trigger fires.
- **No `Mat4d`.** Double-precision matrices are a large-world concern, and the answer there is
  usually a doubled *translation* with a float rotation, not a doubled matrix.
- **No GPU-side doubles, ever.** Uniform and vertex buffers are `FloatArray` because that is what
  `vec4f` and `VK_FORMAT_R32G32B32A32_SFLOAT` consume. Desktop GPUs run fp64 at 1/32 to 1/64 the
  fp32 rate; mobile and WebGPU mostly do not expose it. A `Vec3d` reaching a buffer converts to
  float on upload — the precision is a CPU-side property and stops at the boundary.
- **No shared supertype**, per the reasoning above. If code genuinely needs to be generic over
  precision, it takes the components as parameters.
