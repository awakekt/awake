# Streamed terrain — one clipmap over resident pages

Implemented for [#409](https://github.com/awakekt/awake/issues/409), milestone `v0.4.0`.
The existing whole-heightmap terrain API remains available.

## Ownership and rendering

`asset:terrain` owns the page lattice and canonical height samples. `terrain` owns the index,
residency, bounded reads, edits and upload journals. Neither depends on a scene. `asset:shader-pack`
provides the paged clipmap and shared lit/cascade vertex stage. `kit:terrain-layers` adapts the
existing control/lightmap codecs and loads a palette once. `scene:worldstream` contains the scene
schema, binding and system applying residency to observers and colliders.

One merged clipmap draws the world. Height pages are endpoint-inclusive `(intervals + 1)` squares;
control/light images are cell-centred. Each array layer holds one complete cell. A small RGBA8
table maps index cells to `layer + 1`, encoded little endian in red/green. Zero reads the coarse
whole-world fallback. Palette arrays and tiling tables are shared across cells.

Each fine height knot belongs to the cell on its positive side, except the outer world boundary.
CPU queries and GPU lookups select the same owner. Four knots interpolate height; normals query
across cells. Missing owners use a nested coarse lattice, preserving continuous geometry at
residency boundaries. Four surface taps independently look up cells so control blends cross pages.
Lit and cascade depth call the same height lookup, morph and finer-ring discard.

## Asset contract

`TerrainPageIndexCodec` reads/writes a version-1 JSON index. Paths resolve relative to the index.
It includes minimum absolute cell coordinates, cell counts/size, fine intervals, vertical scale,
a fixed raw elevation range, a mandatory coarse height file/dimensions, and sparse cell entries.
Layered worlds also name a shared palette, coarse controls, optional coarse lighting, and uniform
per-cell control dimensions/slot count. Entries name raw heights, `.terrainctl` and optional
`.terrainlight` files. Existing control/lightmap formats are unchanged.

Raw heights are unsigned 16-bit little endian with exact byte counts. Coarse knots must be an
isotropic nested subset of fine knots spanning the same footprint. Publication rejects resident
shared-edge disagreement before changing the model. Layered reads validate palette indices,
255-sum weights, slots and image dimensions. Missing cells use fallback; errors are separately
observable through `failedCoords` and the failure callback.

## Budgets and frame lifetime

`capacity` bounds resident detail cells and array layers. `maxConcurrentReads` bounds live readers,
including cancelled readers still retiring, and the completion channel. Reads are admitted nearest-first; cancellation generations reject late
results after unload/re-entry. Failures require explicit retry. Unsaved cells remain pinned and
can prevent further admission.

`maxUploadBytes` bounds uploads per frame slot. A table entry appears only after all cell images
arrive in that submission. Pending cells use fallback. Layer reuse replaces its data and table
atomically for the writable slot. Eviction removes the table entry even while a replacement waits.
`isUploaded(coord, frameIndex)` distinguishes CPU residency from GPU publication.

Generic `TextureRegion` uploads run before shadow, camera-depth and scene passes. Mutable images
have one mip; palette images keep mip filtering through an independent sampler. WebGPU queue
writes precede submission and follow earlier submitted frames. Vulkan records staging copies and
vertex/fragment read barriers in the current buffer and retains staging through the slot's fence.
It keeps mutable texture copies per fenced presentation/offscreen slot. Ordinary updates introduce
no queue/device idle. Detach retains its existing idle wait.

64 layers of 129-square RGBA8 heights, eight-slot 128-square controls and 128-square lighting cost
4.063 + 16 + 4 = 24.063 MiB per GPU slot. Vulkan multiplies mutable data by fenced slot count,
including offscreen; WebGPU uses one set. Add table/coarse images, palette, geometry, CPU pages and
staging. `textureBytesPerSlot` reports the texture portion. Dimensions are capped at 8192 and must
also fit backend limits. Frame replication replaces the originally proposed copy-on-write retirement
scheme to make fence ownership explicit.

## Integration

Load assets off-thread, then attach through the normal content lifecycle:

```kotlin
val loaded = PagedTerrainLayers.load(assets, AssetPath("world/index.terrainpages.json"), capacity = 64)
val config = ScenePagedTerrain("world/index.terrainpages.json", collider = true)
val streamer = config.streamer(loaded.terrain, scope, loaded::read) { coord, error ->
    log.error(error) { "Terrain cell $coord failed to load" }
}
val system = config.system(streamer, observer = { camera.position }, physics = physicsSystem)
val attached = host.attachContentFeature(loaded.content)
// Install system before physicsSystem; close system and attachment at scene teardown.
```

Register `PagedTerrainBinding` with the project's scene registry. Asset resolution and provider
selection are explicit project lifecycle dependencies. Core clients can use `TerrainPageHeightReader`,
`TerrainPageStreamer` and `pagedTerrainContentFeature` without a scene. Props/meshes stream separately.

The scene adapter calculates absolute observers/origins in double precision and subtracts the current
origin when spawning centred colliders. Render snapping preserves the absolute grid phase. Nearby
collision entities use the same resident height pages; deformation replaces only affected entities.
The existing `PhysicsSystem` owns body creation/destruction and origin movement. `collisionReady`
is true only after the real body exists. Consumer movement policy gates on readiness; visual fallback
does not imply collision. Scope, reader, observer, render host and physics system are code-only options.

## Editing and saving

Height edits use global fine sample coordinates. All shared copies must be resident; validation
precedes mutation. Seam/corner edits update copies atomically and report affected cells. Nested coarse
knots update alongside detail. Non-knot detail does not alter decimated coarse heights.
`PagedTerrainLayers.editControl` updates a page and centre-decimated coarse controls owned by its cell.
Generic providers update coarse surface images through `editFallbackTextures`.

Take `saveSnapshot(coord)` on the owner thread, run `save(snapshot, writer)` off-thread, then acknowledge
the exact revision on the owner thread after success. An intervening edit stays dirty. Save coarse
heights/surfaces using their separate snapshots/writer methods and acknowledge likewise. The writer
defines atomic replacement/transaction policy. Author a lightmap path before saving baked-light edits.
Brushes, region UI and undo belong to consuming tools. Dirty pages stay retained on streamer close.

## Verification

Core tests cover borders, all-copy edits, save revisions, pinned eviction, byte budgets, frame-slot
publication/reuse, index validation and late reads. Layered tests verify wide controls, relative paths,
shared palette loading and cell/coarse saves. Scene tests cover post-shift spawn positions, readiness,
rebuild/unload and schema mapping. A real Jolt test checks 129-sample seams and unloaded-body removal.

Real desktop content tests measure height-page pixels: WebGPU changes 2,272 pixels and Vulkan 2,690
in their respective fixtures. Eviction is the recorded negative control: both match fallback with
zero differing pixels. Wide-control surface uploads change 3,660 pixels on both backends; origin
shifts and repainting each leave zero differing pixels against their expected surface. Palette repeats
are anchored to the fixed index footprint. Vulkan also exercises two presentation slots. These runs
cover desktop Vulkan and wgpu-native; browser/iOS execution remains separate.
