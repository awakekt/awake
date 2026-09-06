# Awake Core Math 2D (`awake:core:math2d`)

Lightweight, high-performance 2D geometric and layout primitives for screen space, hit-testing, and UI layout in Awake Engine.

---

## Architecture & Design Principles

1. **Clean Separation of Extent vs. Placement**:
   - `Size2D` represents an unpositioned 2D extent (width & height).
   - `Rectangle` represents positioned bounds `(x, y, width, height)`.
   - Layout measure passes produce `Size2D`; layout placement passes produce `Rectangle`.
2. **Allocation & Value Semantics**:
   - `Dp` and `Sp` are `@JvmInline value class` wrappers over `Float`. They use `Float.NaN` (`Dp.Unspecified`) as a sentinel value to avoid JVM/JS heap boxing on optional layout parameters.
   - `Vec2` is designed for screen-space vector math, pointer tracking, and gizmo line segment hit-testing.
3. **No Upstream Compose Coupling**:
   - Primitives in this module have zero dependencies on Jetpack/Compose Multiplatform UI libraries, enabling lightweight rendering, headless UI tests, and embedded editor tooling.

---

## API Catalog

### 1. Vector Math (`Vec2`)
Location: `com.awakekt.awake.core.math2d.Vec2`

| Member / Function | Description |
| :--- | :--- |
| `Vec2(x, y)` | 2-component vector for screen or pixel coordinates. Supports `Int` and `Float` constructors. |
| `+`, `-`, `*` | Arithmetic operators allocating new `Vec2` instances. |
| `dot(other)` | Computes scalar dot product. |
| `length()` | Computes Euclidean magnitude. |
| `distanceTo(other)` | Euclidean distance between two screen coordinates. |
| `distanceToSegment(start, end)` | Clamped distance from a point to a finite line segment `[start, end]`. Ideal for hit-testing gizmo handles and lines. |

```kotlin
val mousePos = Vec2(120f, 250f)
val handleStart = Vec2(100f, 200f)
val handleEnd = Vec2(100f, 300f)

val distance = mousePos.distanceToSegment(handleStart, handleEnd)
if (distance <= 8f) {
    // Pointer is hovering over the handle
}
```

---

### 2. Geometry (`Rectangle`, `Size2D`)
Location: `com.awakekt.awake.core.math2d`

| Type / Function | Description |
| :--- | :--- |
| `Rectangle(x, y, width, height)` | Immutable measured bounds. Also aliased as `Bounds`. |
| `Rectangle.intersect(other)` | Computes overlap region (or zero-sized rect if non-overlapping). |
| `Rectangle.contains(other)` | Returns `true` if rectangle completely contains `other`. |
| `Rectangle.contains(px, py)` | Returns `true` if point `(px, py)` falls inside bounds. |
| `pixelPerfectPixel(value)` | Snaps coordinate to nearest integer float to prevent subpixel blur. |
| `Size2D(width, height)` | Unpositioned 2D extent. |
| `Size2D.isEmpty` | Returns `true` if `width <= 0f` or `height <= 0f`. |
| `Size2D.at(x, y)` | Places extent into a `Rectangle` positioned at `(x, y)`. |
| `Rectangle.size` | Extracts `Size2D(width, height)` from bounds. |

```kotlin
val measuredSize = Size2D(320f, 240f)
val placedRect = measuredSize.at(x = 16f, y = 16f)

if (placedRect.contains(mouseX, mouseY)) {
    // Mouse is inside component bounds
}
```

---

### 3. Density Units (`Dp`, `Sp`)
Location: `com.awakekt.awake.core.math2d`

| Type / Extension | Description |
| :--- | :--- |
| `Dp(value)` | Inline value class for density-independent pixels. |
| `Float.dp` / `Int.dp` | Convenient extension properties for authoring dimensions (`16.dp`, `2.5f.dp`). |
| `Dp.Unspecified` | Sentinel `Dp(Float.NaN)` for unconstrained layout limits without boxing. |
| `Dp.isSpecified` | `true` when value is not NaN. |
| `Sp(value)` | Inline value class for scale-independent typography sizing. |
| `Float.sp` / `Int.sp` | Convenient extension properties for font sizing (`14.sp`, `18f.sp`). |
| `Sp.Unspecified` | Sentinel `Sp(Float.NaN)` for unconstrained font sizes. |
