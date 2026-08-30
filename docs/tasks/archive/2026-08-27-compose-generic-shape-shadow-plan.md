# Compose generic-shape shadow plan

Status: planned

## Trigger

`ShapeOutline.Generic` already drives `background`, `border`, and `clip`, but both
`Modifier.dropShadow` and `Modifier.graphicsLayer(shadowElevation = …)` reject it. A circle or
independently-rounded shape must cast its own silhouette, never its rectangular bounds. The
existing `ShadowQuad` is an efficient analytic caster for rectangles and uniformly rounded
rectangles; it cannot express an arbitrary path without lying about the shape.

## Decision

Use the existing renderer-aware graphics-layer compositor to rasterize a generic outline into an
alpha mask target, blur that target, and composite it below the owner. This is a backend-neutral
Compose request: `:compose:ui` records a path mask and its placement; `:engine:compose` owns
render targets/materials; Vulkan and WebGPU only draw and sample ordinary UI primitives.

Do not add a path field to `ShadowQuad`, tessellate a bounding rectangle, or approximate a path
with several offset opaque paths. Those approaches either change the caster geometry or make blur
quality depend on path tessellation rather than the sampled mask.

## Shared representation

1. Add a renderer-neutral shadow-layer record alongside `GraphicsLayerFrame`. It contains a local
   `DrawPath`, mask colour/alpha, x/y/width/height, blur inset, offset, scale/rotation, and
   texture placement. Its public origin stays in Compose paint; it carries no material or backend
   type.
2. Extend `FrameOutput` to retain these records in paint order. A generic shadow emits a
   `GraphicsLayerPlaceholder` texture before its owner content, exactly as an ordinary graphics
   layer does; a rectangle or uniform rounded outline continues using `ShadowQuad`.
3. Keep the colour in the mask primitive and composite the mask texture with alpha `1f`. This
   applies owner alpha once, matching the current `ShadowQuad` path and preventing a blurred
   layer from being dimmed a second time.

## Compositor implementation

1. Generalize the retained layer slot to own an optional mask target/material in addition to its
   content and blur targets. The target size is the mask bounds plus blur padding; it is reused
   while dimensions match and destroyed on removal, resize, compositor disposal, and renderer
   teardown.
2. Render `FilledPath(path, colour)` into the mask target, translated by the target inset. Reuse
   the existing nine-tap blur pass, then resolve the placeholder to the blurred mask material.
3. Preserve paint order with nested layers: all masks render before the parent primitive stream is
   resolved, and a generic shadow's placeholder remains before the node/layer it shadows.
4. Apply the same texture transform (scale, translation, rotation) to the shadow and its owner.
   The offset is part of the shadow texture placement, not a separate unrotated `ShadowQuad`.

## API integration

1. Teach `PaintScope.drawLayer` to create a generic elevation mask record when its shape outline
   is `Generic`; leave `Rectangle` and uniform `Rounded` on `ShadowQuad`.
2. Add an internal `LayerDrawScope.drawPathShadow` bridge so `DropShadowNode` can emit the same
   record for a generic shape. It must retain `drawBehind` modifier ordering and not isolate the
   node's content.
3. Preserve the currently supported solid and four-corner linear-gradient shadow brushes. A mask
   colour is sufficient for a solid brush; gradient support must define its coordinates against
   the shadow target before enabling it. Do not silently collapse a gradient to one colour.

## Verification

| Evidence | Required result |
|---|---|
| Compose paint tests | `CircleShape` and independently-rounded `RoundedCornerShape` no longer throw; their shadow record is before the owner and carries local path geometry. |
| Compositor structural test | A generic shadow allocates/reuses only its mask and blur resources, resolves a mask placeholder, and disposes every retained target/material. |
| Vulkan headless pixel capture | A circle's corner outside the silhouette remains transparent while its edge has blurred shadow alpha. |
| Desktop WebGPU pixel capture | The same silhouette/corner condition holds through WebGPU. |
| Existing rectangle tests | Rectangle and uniform-rounded shadows retain the native `ShadowQuad` fast path and unchanged pixels. |

## Non-goals

- Inner shadows, which need an inverse mask and different clip/composite semantics.
- Radial, sweep, image, or runtime shader brushes.
- Generic destination blend modes beyond the already supported `Screen` and `Overlay`.
- CPU readback or a backend-specific Compose API.
