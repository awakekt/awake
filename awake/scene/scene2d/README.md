# `awake:scene:scene2d`

The 2D scene components. Today that is `sprite`: its schema, binding, validation and render extraction. It is the
scene-binding layer for 2D, the way [`awake:scene:particles`](../particles/README.md) is for particles.

```kotlin
implementation(project(":awake:scene:scene2d"))
```

## What is in it

- `SceneSprite` -- the document's `sprite` component: one cell of a frame sheet, its size, flips, tint
  and draw order. Its field names are the file format.
- `Sprite` and `SpriteBinding` -- the live component on an entity, and the binding that attaches the
  schema and exports it back as it is then. A game sets `sprite.frame`, `flipX` and the rest.
- `World.collectSpriteDraws()` -- maps `Transform` and `Sprite` into the portable atlas renderer in
  `render:passes`. The standard scene runtime includes these draws in its combined scene pass.

## Drawing sprites

Register decoded pixels under the scene component's `texture` name:

```kotlin
assets {
    texture("hero") { decodedHeroSheet }
}
```

Add `spriteScenePipeline()` from the shader pack to the app's `RenderPlan.scenePipelines`. This
selects `PositionUv`, the material bindings, alpha blending and depth-test-only behavior. The runtime renders unlit, straight-alpha quads, using nearest
base-level texels. Sheets must divide into whole pixel cells. A cell's dimensions divided by
`pixelsPerUnit` determine local size before the node's world transform is applied. Atlas rows run
from the top; texture pixels use Awake's bottom-up `createBitmap(...).toRgba8Bytes()` layout.
Flips stay inside the chosen cell. Higher `sortOrder` draws last, with ties sorted
back to front by camera distance. Sprites do not write scene/shadow depth, so empty margins preserve
what is behind them. Opaque scene geometry still depth-tests against sprites.

The runtime reuses one quad and a material per named sheet until runtime disposal; uploaded textures
follow the renderer's existing texture lifetime. Custom hosts can use `SpriteRenderBatch` directly
without a scene and must destroy it before destroying their renderer.

## What is not in it yet

- Animation. A game steps `Sprite.frame` itself for now.
- Tilemaps. The `tilemap` component lands with its own chunked-batching capability, outside `scene/`.

## What stays out of it

An algorithm or behaviour with an API of its own (overlap tests, batching, sampling) lives in a module
that depends on no `awake:scene` module, and this one binds it. `./gradlew verifyCapabilityLayering`
enforces that. 2D overlap already lives in `awake:core:math` (`Box2`, `Circle2`, `Overlap2`).
