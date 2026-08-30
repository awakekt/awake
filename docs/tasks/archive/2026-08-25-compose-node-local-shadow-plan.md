# Compose node-local shadow plan

Status: implemented

## Trigger

`TabsTrigger` in the pinned shadcn reference applies `shadow-sm` while active. The renderer and
CPU rasterizer already accept `UiDrawPrimitive.ShadowQuad`, but retained Compose initially had no
node-local drawing helper for it. `DrawScope.emit` is intentionally tree-space only, so a recipe
inside `Modifier.drawBehind` could not determine the origin needed to emit a correctly placed
shadow.

This is not `graphicsLayer` or `shadowElevation`: it emits one ordinary shadow primitive around a
node, does not render a subtree to a texture, and does not add blur effects or layer compositing.

## Scope

The implementation has two layers:

- Awake-local `DrawScope.drawShadow(...)`, which maps node-local bounds and effects through the
  same origin, scale, and alpha rules as `drawRect` and `drawTexture`.
- Compose-shaped `Modifier.dropShadow(shape, shadow)`, which resolves a rectangular or uniformly
  rounded `Shape`, paints first, then invokes the following modifier chain/content.

```kotlin
fun DrawScope.drawShadow(
    color: Color,
    radius: Float,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    blurRadius: Float,
    spread: Float = 0f,
    x: Float = 0f,
    y: Float = 0f,
    width: Float = this.width.toFloat(),
    height: Float = this.height.toFloat(),
)
```

The final parameter order should follow existing `DrawScope` helpers and avoid a new allocation on
the normal draw path. It must construct `ShadowQuad` only after translating from node-local to
tree coordinates.

## Implementation steps

1. Add `drawShadow` to `DrawScope` and implement it in `PaintScope`; map position, dimensions,
   offset, blur, and spread consistently under `withScale` and `withAlpha`. **Done.**
2. Add `Modifier.dropShadow(shape, shadow)` plus `Shadow`, `DpOffset`, and the supported `Brush`
   subset. **Done.** Generic paths fail explicitly until shape-mask rendering lands; they never
   receive a visually incorrect bounding-box shadow.
3. Add focused `:awake:compose:ui` tests for a nested offset node, scale, alpha, modifier order,
   rounded shapes, generic-shape rejection, and four-corner gradients. **Done.**
4. Add a recipe-level `shadow-sm` helper in `ui-designsystem`, sourced from the pinned shadcn
   class. It owns token color, opacity, blur, and offset; Tabs only declares that its active trigger
   uses that recipe style. **Done.**
5. Render Tabs light and dark, inspect reference/Awake/diff images, and run the manifest geometry,
   style, and paint checks. The shadow must not alter the 36dp list or trigger bounds.

## Non-goals

- `Modifier.graphicsLayer`, render-to-texture, blur filters, or elevation semantics.
- Replacing CSS `box-shadow` globally; each recipe translates its pinned source independently.
- Altering text metrics to hide the current 3px Tabs intrinsic-width delta. That is a separately
  measured typography-quality concern and needs its own evidence.
- Arbitrary path shadows, radial/sweep/image/runtime brushes, inner shadows, or blend modes. They
  require a shape-mask or compositing layer rather than an extra field on `ShadowQuad`.

## Acceptance evidence

| Evidence | Required result |
|---|---|
| `DrawScope` unit test | A node-local shadow has correct tree-space bounds, scaled offsets and alpha. |
| Tabs semantic capture | Track stays 36dp; trigger bounds do not move when the shadow is added. |
| Tabs light/dark previews | Active trigger carries the pinned `shadow-sm` footprint. |
| `scripts/awake ui validate --component tabs --theme both` | Structural checks remain valid; residual paint is inspected and explained. |

## Delivered gradient subset

`Shadow` now accepts `brush = Brush.horizontal(...)`, `Brush.vertical(...)`, or
`Brush.linearGradient(...)`. These map to the existing four-corner vertex-color format and render
through the native rounded-shadow pipeline. The CPU preview rasterizer samples the same gradient,
so headless captures remain useful evidence. A brush spans the emitted shadow footprint; it is not
yet Compose's arbitrary coordinate-space brush contract.
