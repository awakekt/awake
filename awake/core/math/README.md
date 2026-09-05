# Awake Core Math (`:awake:core:math`)

High-performance, platform-neutral 3D mathematics, transformations, camera projections, and geometric primitives for the Awake Multiplatform Game Engine.

Designed around strict **zero per-frame allocation** rules for ECS simulation loops and real-time graphics pipelines on Desktop (JVM), iOS (Kotlin/Native), and Web (WASM).

---

## 1. Vector Types

| Type | Components | Precision | Use Case |
|---|---|---|---|
| `Vec3f` | `x, y, z` | 32-bit Float | Standard 3D spatial points, directions, normals, scales |
| `Vec3d` | `x, y, z` | 64-bit Double | Large-world coordinates, astronomical scales, high-precision physics |
| `Vec3i` | `x, y, z` | 32-bit Int | Voxel coordinates, grid indices, discrete tile coordinates |

### Common Operations
- **Arithmetic:** `+`, `-`, `*` (scalar scale or component-wise), `/`
- **Vector Math:** `dot(other)`, `cross(other)`, `length()`, `lengthSquared()`, `normalized()`, `distanceTo(other)`
- **Interpolation:** `vecA.lerp(vecB, fraction)`
- **Directions:** `Vec3f.ZERO`, `Vec3f.ONE`, `Vec3f.FORWARD` (0, 0, -1), `Vec3f.BACK` (0, 0, 1), `Vec3f.UP` (0, 1, 0), `Vec3f.DOWN` (0, -1, 0), `Vec3f.RIGHT` (1, 0, 0), `Vec3f.LEFT` (-1, 0, 0)

---

## 2. Rotations & Quaternions

| Type | Description |
|---|---|
| `Quat` | Unit quaternion (`x, y, z, w`) representing 3D spatial rotation without gimbal lock. |

### Features
- **Construction:** `Quat.fromAxisAngle(axis, radians)`, `Quat.fromEuler(pitch, yaw, roll)`, `Quat.lookAt(direction, up)`
- **Transforms:** `quat.rotateVector(v)`
- **Interpolation:** `slerp(other, fraction)` (spherical linear interpolation)
- **Matrix Conversion:** `quat.toMatrix()` / `mat4.toRotationQuat()`

---

## 3. Matrix 4x4 Transformations

| Type | Description |
|---|---|
| `Mat4` | 4x4 row-major / column-major transformation matrix. |

### Features
- **Transform Composition:** Translation, Rotation, Scale (`TRS`)
- **Camera & Projection:** `Mat4.lookAt(eye, center, up)`, `Mat4.perspective(fovY, aspect, near, far)`, `Mat4.orthographic(left, right, bottom, top, near, far)`
- **Inversion & Determinant:** `mat.inverse()` (returns `null` if singular), `mat.transpose()`
- **Transform Vectors:** `mat.transformPoint(v)`, `mat.transformDirection(v)`

---

## 4. Geometric & Collision Primitives

| Type | Description | Key Methods |
|---|---|---|
| `Ray` | Infinite 3D ray defined by `origin` and normalized `direction`. | `pointAt(t)`, `intersectSphere(...)`, `intersectAabb(...)`, `intersectPlane(...)`, `intersectGroundPlane(...)` |
| `Plane` | Infinite 3D plane (`normal`, `d`). | `signedDistanceTo(point)`, `Plane.XZ` |
| `Aabb` | Axis-Aligned Bounding Box (`min`, `max`). | `contains(point)`, `intersects(other)`, `transformed(mat4)`, `center`, `extents` |
| `Frustum` | 6-plane viewing frustum extracted from view-projection matrix. | `intersects(aabb)`, `contains(point)`, `isSphereVisible(center, radius)` |

---

## 5. Camera Math & Viewport Projections

| Utility | Description |
|---|---|
| `Lens` | Camera optical model holding `eye`, `center`, `up`, `fovYRadians`, and `projection`. |
| `CameraMathUtils` | Viewport unprojection and screen mapping: `projectToViewport(...)`, `rayThroughViewport(...)`. |

---

## 6. Scalar Math Utilities

- `lerp(start: Float, stop: Float, fraction: Float): Float` — Standard linear interpolation.
- `lerp(start: Double, stop: Double, fraction: Double): Double` — Double-precision linear interpolation.
- `clamp(value, min, max)` — Range bounding.
- `Angle.toDegrees(radians)` / `Angle.toRadians(degrees)`.

---

## Invariants & Performance Rules

1. **Zero per-frame allocations in `System.update`:** Never create temporary `Vec3f` or `Mat4` instances in hot frame loops. Mutate existing target vectors or use primitive parameters.
2. **Normalized Ray Directions:** `Ray(origin, direction)` always normalizes its direction on construction so hit-test distances remain in uniform world units.
