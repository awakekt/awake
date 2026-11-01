# `awake:scene:scene2d`

The 2D scene components: sprites, named sprite clips and finite tilemaps, with schema, binding,
validation and render extraction. It is the
scene-binding layer for 2D, the way [`awake:scene:particles`](../particles/README.md) is for particles.

```kotlin
implementation(project(":awake:scene:scene2d"))
```

## What is in it

- `SceneSprite` -- the document's `sprite` component: one cell of a frame sheet, its size, pivot, flips, tint
  and draw order and layer. Its field names are the file format.
- `SortingLayers` -- a project's named sorting layers, back to front, ranked around `Default`.
- `Sprite` and `SpriteBinding` -- the live component on an entity, and the binding that attaches the
  schema and exports it back as it is then. A game sets `sprite.frame`, `flipX` and the rest.
- `World.collectSpriteDraws()` -- maps `Transform` and `Sprite` into the portable atlas renderer in
  `render:passes`. The standard scene runtime includes these draws in its combined scene pass.
- `SceneSpriteClips`, `SpriteClips` and `SpriteClipSystem` -- bind named animation runs to `Sprite.frame`,
  using the reusable `FrameClipPlayer` in `core:animation`.
- `SceneTilemap`, `Tilemap` and `TilemapBinding` -- bind editable tile layers to the chunk grid
  and atlas geometry in `awake:tilemap`; the runtime owns their cached GPU batches.
- `SceneParticleEmitter2D`, `ParticleEmitter2DBinding`, `Transform2DPlacement` and
  `SceneParticleEmitter2D.toBurstTemplate()` -- bind the 2D particle emitter (`particle_emitter_2d`) to a
  node. The emitter itself (`ParticleEmitter2D`, `Particle2DSystem` and `BurstEmitter2DPool`) lives in
  [`awake:particles`](../../particles/README.md), beside the 3D one; `Transform2DPlacement` tells its
  system where a node is, and `collectSpriteDraws()` sorts its quads alongside sprites and tilemaps by
  `sortingLayer` and `sortOrder` (alpha-blended or additive).

## Drawing sprites

A sprite's `texture` is a name the scene's assets resolve when the sprite is first drawn. Register
decoded pixels under it:

```kotlin
assets {
    texture("hero") { decodedHeroSheet }
}
```

Or read every texture from files as it is first drawn, so a scene whose images are project files
needs no registration. `runProject` does this with the project's files:

```kotlin
val textures = AssetSourceTextureResolver(projectFiles) // close() it when the scene stops
assets {
    resolver(textures)
}
```

The name is then a path in those files. A registered texture is used first; otherwise the first
resolver whose `texture(name)` supplies it answers. Until a texture has loaded, what uses it is not
drawn, and the scene logs once that it is not drawn yet; it draws from the frame it arrives. A
texture that fails to load, or that nothing registers or resolves, draws nothing and is logged once as
an error. Neither throws. A resolver of your own overrides `SceneAssetResolver.texture`, answering
`SceneTextureLoad.Loading` until it has the pixels.

A texture cooked to KTX2 (#1211) loads from the cooked file when it ships beside the image: for
`art/hero.png`, `art/hero.astc.ktx2` or `art/hero.etc2.ktx2` on a device that samples that format,
else `art/hero.ktx2` transcoded to the device's block format, or decoded to RGBA8 where it takes none.
`runProject` does this; elsewhere pass a cooked loader, which is tried before the image is decoded:

```kotlin
var formats = emptySet<CompressedTextureFormat>() // renderer.compressedTextureFormats, once it's ready
val cooked = CookedTextureLoader(projectFiles) // awake:asset:texture-ktx-loader
val textures = AssetSourceTextureResolver(projectFiles, Dispatchers.Default) { name -> cooked.load(name, formats) }
```

A texture is kept once it has loaded. When its file changes, or one that failed has been fixed, forget
it on the frame thread, and the next frame reads it again and draws the new pixels:

```kotlin
val assets = runtime.requireAssetLibrary()
assets.forgetTexture("art/hero.png") // this file changed
assets.forgetTextures() // the resolvers now read another project's files
```

Forgetting destroys what the sprites and tilemaps uploaded from it, has each resolver drop what it kept
(`SceneAssetResolver.forgetTexture`), and logs a texture that is still missing again, once.

A file watcher that runs on its own thread asks instead, and the runtime forgets at the start of the
next frame, before its systems and draws run:

```kotlin
assets.requestForgetTexture("art/hero.png") // safe from any thread
assets.requestForgetTextures()
```

Add `spriteScenePipeline()` from the shader pack to the app's `RenderPlan.scenePipelines`. This
selects `PositionUv`, the material bindings, alpha blending and depth-test-only behavior. The runtime renders unlit, straight-alpha quads, using nearest
base-level texels. Sheets must divide into whole pixel cells; a sprite whose sheet does not, or a
tilemap whose atlas does not, is not drawn, and this is logged once for each image, however many
sprites or tilemap layers use it. A cell's dimensions divided by
`pixelsPerUnit` determine local size before the node's world transform is applied. Atlas rows run
from the top; texture pixels use Awake's bottom-up `createBitmap(...).toRgba8Bytes()` layout.
Flips stay inside the chosen cell. A sprite's `pivot`, a fraction of its cell from the bottom left and
its centre by default, is the point placed at the node's position; the node's rotation and scale turn
and scale the sprite about it, and a flipped sprite mirrors about it. A later `sortingLayer` draws over an
earlier one, in the order `SortingLayers` gives them (`SceneAppLifecycleRuntime.sortingLayers`, which
`runProject` sets from the manifest's `sortingLayers`). Within a layer, higher `sortOrder` draws last, with ties sorted
back to front by camera distance. Sprites do not write scene/shadow depth, so empty margins preserve
what is behind them. Opaque scene geometry still depth-tests against sprites.

The runtime reuses one quad and a material per named sheet until runtime disposal; uploaded textures
follow the renderer's existing texture lifetime. Custom hosts can use `SpriteRenderBatch` directly
without a scene and must destroy it before destroying their renderer.

## Named sprite animations

Add `sprite_clips` beside `sprite` on the same node. Dimensions come from the sprite; frame indices
count from the top left across rows.

```json
{
  "component": "sprite_clips",
  "clip": "idle",
  "clips": {
    "idle": { "firstFrame": 0, "frameCount": 4, "framesPerSecond": 4, "loop": true },
    "hit": { "firstFrame": 4, "frameCount": 2, "framesPerSecond": 8, "loop": false }
  }
}
```

The standard runtime advances clips before rendering while its realtime provider is active, and
`sceneSystemsFor` adds `SpriteClipSystem` for a scene with `sprite_clips`. Custom ECS hosts run `SpriteClipSystem` once
per simulation frame before extracting sprite draws. A game selects a run with
`world.get<SpriteClips>(entity)?.play("hit")`; asking for the active run preserves time unless
`restart = true`. Set `speed = 0f` to pause, and read `isFinished` for one-shot completion. An empty
clip library leaves manual `Sprite.frame` control intact. Scenes export the selected clip name;
elapsed time and speed are transient runtime state. Existing `texture_clips` continues to control
textured mesh materials, with the same underlying clock.

## Importing sheet metadata

Read a sprite-gen component-row manifest with `SpriteGenManifest` from `asset:sprite`. Resolve its
`image` reference with your asset loader and call `requireImageSize` after decoding the image.
Bake the sheet into the document before instantiation:

```kotlin
val sheet = SpriteGenManifest.decode(manifestText)
sheet.requireImageSize(bitmap.width, bitmap.height)
val baked = authored.withSpriteSheets(mapOf("hero" to sheet))
baked.instantiate(world = world)
```

The map key is the sprite's authored `texture` name. Nested matching nodes get the imported grid
and named clips. Local runs override same-named imported runs; the selected clip, sprite styling
and transforms are preserved. If selection is omitted, the first exported run plays. The baked
document saves as standard `sprite` and `sprite_clips` data, so playback does not need the manifest.
For a new node, `sheet.toSceneSprite("hero")` and `sheet.toSceneSpriteClips("idle")` map the metadata
directly. The caller owns texture registration and path resolution.

The importer supports contiguous, untrimmed grid rows with uniform timing. See the
[asset module](../../asset/sprite/README.md) for format validation and limitations.

## Tilemaps

Add `tilemap` to a node; its `texture` resolves as a sprite's does, registered or from files.
Include `spriteScenePipeline()` in the render plan. The standard runtime renders tile layers
alongside sprites, with the same `sortingLayer` and `sortOrder`, nearest filtering and alpha behavior.

```json
{
  "component": "tilemap",
  "texture": "terrain-tiles",
  "width": 3,
  "height": 2,
  "columns": 2,
  "rows": 2,
  "pixelsPerUnit": 16,
  "chunkSize": 16,
  "tiles": [0, 1, -1, 2, 3, 0]
}
```

Tile indices run from the atlas's top left across rows, and -1 is empty. Map rows run down from
the node's top left: +X right, -Y down. A tile's pixel size divided by `pixelsPerUnit` determines
its local size; parent and node transforms apply to the entire layer. A game edits a cell with
`world.get<Tilemap>(entity)?.setTile(x, y, frame)`. Export saves those edits and current tint/order.

Each visible non-empty chunk contributes one mesh draw. Unchanged chunks reuse requests and GPU
buffers; editing a cell rebuilds only its chunk. Frustum culling uses the resolved camera viewport,
including virtual viewport scaling. Removing the component or disposing the runtime releases
owned chunk meshes and material buffers. The grid and atlas geometry live in
[`awake:tilemap`](../../tilemap/README.md), outside the scene wrapper.

## 2D particles (`particle_emitter_2d`)

Add `particle_emitter_2d` to a 2D node to spawn quads through the unlit sprite pipeline (`spriteScenePipeline()`),
sorted alongside `sprite` and `tilemap` nodes by `sortingLayer` and `sortOrder`. Leaving `texture` empty (`""`)
draws solid-tinted quads; naming a project sheet resolves it through the scene's sprite batch with `columns × rows`
cells and `frameCount` animated frames.

```json
{
  "component": "particle_emitter_2d",
  "texture": "fx/ember_sheet.png",
  "columns": 4,
  "rows": 1,
  "frameCount": 4,
  "frameRate": 12,
  "maxParticles": 96,
  "spawnRate": 24,
  "lifetime": 0.8,
  "size": 0.25,
  "endSize": 0.6,
  "shape": { "kind": "Disc", "radius": 0.2 },
  "directionDegrees": 90,
  "spreadDegrees": 35,
  "speed": 1.5,
  "maxSpeed": 3.0,
  "gravity": { "x": 0, "y": -4.0 },
  "additive": true,
  "sortingLayer": "Foreground",
  "sortOrder": 5
}
```

For repeated one-shot bursts (hit sparks, pickups, footsteps) without per-burst entity churn or heap allocations,
prepare a `BurstEmitter2DPool` once, build a template once from authored settings with
`settings.toBurstTemplate()` (it validates them, so a bad setting throws at load time, not mid-game), and call
`pool.spawn(world, x, y, template)`. When all `maxSlots` resident slots are active, `spawn` skips and increments
`droppedBurstCount` rather than evicting an in-flight burst or allocating a new entity.

Stepping, the `onParticleDeath` and `dynamicSpawnRate` hooks, and `spawn` allocate nothing. Drawing reuses each
particle's model matrix and an unchanged tint, but still builds a `SpriteDrawInput` per particle each frame, as a
sprite does.

## What stays out of it

An algorithm or behaviour with an API of its own (overlap tests, batching, sampling) lives in a module
that depends on no `awake:scene` module, and this one binds it. `./gradlew verifyCapabilityLayering`
enforces that. 2D overlap already lives in `awake:core:math` (`Box2`, `Circle2`, `Overlap2`).
