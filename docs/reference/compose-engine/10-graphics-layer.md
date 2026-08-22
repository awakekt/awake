# 10 — graphicsLayer

This page exists to stop the engine over-promising. "graphicsLayer is real now" will be true of the
first list below and false of the second.

## Today

Alpha is a context stack composed multiplicatively and applied **per primitive**
(`UiContext.emitInternal` → `scaledByAlpha`). Transform is a second stack whose own doc states that
**nested scale blocks do not compose multiplicatively**. That is a per-primitive approximation, not
a layer.

## Fixed by the tree, for free

- Transform and alpha become node properties composed down the tree during place and paint, so
  **nesting composes correctly**. The documented divergence goes away.
- No matched push/pop pairs to leak on a throw.
- `LocalAlpha` and `LocalTransform` stop being ambient values — see `03-composition-locals.md`.

## Not fixed, and not promised

True layer semantics need render-to-texture. There is **no offscreen render-target support anywhere
in `awake/render` today**. Without it:

- `alpha` on a subtree with *overlapping* children double-darkens the overlap; a real layer does not
- `clip = true` under a rotated, non-axis-aligned transform
- blend modes, `RenderEffect`/blur, `shadowElevation`
- arbitrary-content shimmer (see `05-animation.md`)

That is `awake-render-vulkan` / `awake-render-webgpu` work — a named dependency, not part of this
engine. `ShimmerPrimitives.kt` already documents the same gap from the other side.
