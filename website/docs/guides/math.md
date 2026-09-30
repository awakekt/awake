# Math

<p class="awake-lede">Vectors, rotations, matrices and camera lenses shared by scenes, physics and rendering, written so a per-frame system can do its math without allocating.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>com.awakekt.awake.core:math</code></span>
<span class="awake-badge">Desktop · Android · iOS · Web</span>
</div>

The math module has no dependencies beyond 2D vectors, and every other module speaks its types. The
coordinate system is right-handed and Y-up, and the camera looks along -Z.

## Normalize a direction

`Vec3f` has two forms of most operations. A bare verb such as `normalize()` changes the vector and
returns it; the `-ed` form such as `normalized()` returns a new vector and leaves the original alone.

Use the changing form in a frame loop, on a vector you own:

```kotlin title="Kotlin"
--8<-- "awake/core/math/src/commonTest/kotlin/com/awakekt/awake/core/math/Vec3MutabilityTest.kt:normalize-in-place"
```

Use the new-vector form when you still need the original:

```kotlin title="Kotlin"
--8<-- "awake/core/math/src/commonTest/kotlin/com/awakekt/awake/core/math/Vec3MutabilityTest.kt:normalized-copy"
```

| Changes the receiver, returns it | Returns a new value |
| --- | --- |
| `set`, `add`, `sub`, `scale`, `lerp`, `normalize`, `moveTowards` | `normalized()`, `+`, `-`, `* scalar`, `cross` |

`dot`, `length3`, `distanceTo` and the other queries change nothing.

## Named vectors

```kotlin title="Kotlin"
--8<-- "awake/core/math/src/desktopTest/kotlin/com/awakekt/awake/core/math/MathDocsSampleTest.kt:vector-defaults"
```

`Vec3f.ZERO`, `ONE`, `UP`, `DOWN`, `LEFT`, `RIGHT`, `FORWARD` and `BACK` each return a new instance,
so changing one cannot change it for every other caller.

## Rotate

A `Transform` stores rotation as three angles in radians, applied X, then Y, then Z. For a rotation
you compose or apply to a point, use a `Quat`:

```kotlin title="Kotlin"
--8<-- "awake/core/math/src/desktopTest/kotlin/com/awakekt/awake/core/math/MathDocsSampleTest.kt:rotate"
```

`90.angleRad` converts degrees to radians. `Quat.fromEuler(radians)` builds the same rotation a
`Transform` describes, `toEuler()` goes back, and `Quat.nlerp(a, b, t)` blends two rotations.

## Project with a lens

A `Lens` is a camera's eye, target, up, field of view and clip distances. The renderer supplies the
clip-space convention when the matrix is built, so pass the one your renderer uses:

```kotlin title="Kotlin"
--8<-- "awake/core/math/src/desktopTest/kotlin/com/awakekt/awake/core/math/MathDocsSampleTest.kt:lens"
```

`projectToViewport` returns the pixel a world point lands on, or `null` when it is behind the
camera; `rayThroughViewport` goes the other way, from a pixel to a world `Ray` for picking.

| `ClipSpace` | Y axis | Depth |
| --- | --- | --- |
| `Vulkan` | down | 0 to 1 |
| `WebGpu` | up | 0 to 1 |
| `OpenGl` | up | -1 to 1 |

## Choose a type

| Need | Start with |
| --- | --- |
| Positions, directions, scales | `Vec3f` (alias `Vec3`) |
| Rotation without Euler-angle drift | `Quat` |
| World, view or projection transforms | `Mat4` |
| Camera setup and projection | `Lens`, `CameraMathUtils` |
| Visibility and hit tests | `Ray`, `Plane`, `Aabb`, `Frustum` |
| Angles in degrees | `Angle`, `angleRad`, `angleDeg` |

## How it works

The changing forms exist so a `System.update` can reuse scratch vectors held in fields and allocate
nothing per frame; the new-value forms exist so ordinary code reads naturally. Picking the wrong one
fails silently: `v.normalized()` with the result dropped does nothing.

!!! warning "`Vec3f()` is not zero"
    The no-argument constructor gives `(1, 1, 1)`. Write `Vec3f.ZERO` or `Vec3f(0f, 0f, 0f)` for
    the origin.

!!! warning "Choosing the wrong clip space"
    A matrix built for the wrong `ClipSpace` renders upside down or clips away near geometry, and looks
    like a camera bug. Take it from the renderer (`renderer.clipSpace`) rather than choosing one.

## See also

- [Cameras and controls](cameras-and-controls.md) for lenses in a scene.
- [ECS](ecs.md) for writing allocation-free systems.
