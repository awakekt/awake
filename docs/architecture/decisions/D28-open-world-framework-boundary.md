# D28: Open-World Subsystems — Awake vs Starter-Kit Boundary

## Decision

The open-world RFC's subsystems are split by the
[framework boundary](../reference/framework-game-boundary.md)'s API-limitation test, not by
"an open-world game needs it". Awake gains three binding/upload primitives plus terrain draw
emission. Water, atmosphere, biomes, vegetation and placement move to a consumer starter-kit.

## Context

Two copies of the RFC exist and have diverged: this repository's
[RFC_OPEN_WORLD_ENGINE_SUBSYSTEMS.md](../RFC_OPEN_WORLD_ENGINE_SUBSYSTEMS.md) lists five
subsystems; the private consumer pack's copy lists six, adding runtime WGSL/ASL shaders with
uniform reflection. Both propose that all of them live in Awake core.

The boundary rule admits a capability into Awake on two grounds: two credible independent
consumers, or a concrete framework-level limitation a consumer cannot solve through public
APIs. Exactly one consumer pack exists today, so the first ground is unavailable and the
second decides every row below.

The `awake-framework-boundary` skill's rule 6 is more specific than the RFC and takes
precedence over it: water, biomes, vegetation, prop placement and procedural generation stay
in a consumer pack; Awake supplies neutral terrain data and extension seams, never authored
world policy.

### State at decision time

The RFC's components and systems exist and have no production consumers — only their own
unit tests. `SceneAppLifecycleRuntime` installs `TransformSystem`, `RenderSystem3D` and
`DebugVisualizationSystem`, and nothing else.

- `TerrainClipmapSystem` computes snapped clipmap ring origins and emits no draw calls.
- `WaterRenderSystem` and `AtmosphereSystem` each build a `FloatArray` of uniforms and
  discard it, annotated `@Suppress("unused")`.
- `WorldPartitionSystem.update` is a no-op; cells advance only when a caller invokes
  `updateObserverPosition`, and `WorldCellStreamListener` has no implementation.

## What Awake gains, and the limitation that justifies each

| Capability | Limitation a consumer cannot work around |
|---|---|
| Consumer-declarable texture bindings | `BindingSemantic` is a closed enum — `Material`, `ShadowDepth`, `JointPalette`. A splat weightmap, a layer array and a scene-depth sampler cannot be declared at all. |
| `texture2DArray` upload and binding | `TextureAsset` is a single 2D image and `Texture` is backend-internal. |
| Current-frame scene depth as a sampled input | The `ShadowDepth` path proves depth can be sampled, but a pass cannot read the depth it is writing; this needs an engine-owned resolve. |
| Clipmap geometry upload and draw emission from a `Heightmap` | `Heightmap` is already neutral data in `:awake:asset:terrain`; drawing a heightfield is generic to any 3D engine. |

Deferred on the same test, in this order once the above lands: `AssetManager`/`AssetHandle`
with refcounting and VRAM eviction; async cell streaming and cell entity lifecycle; cascaded
shadow maps; a spatial index; floating origin; LOD hysteresis.

**Floating origin, physics leg landed (2026-08-30).** `PhysicsWorld.shiftOrigin` on all three
Jolt backends, joined to `FloatingOriginSystem` by a listener in `:awake:scene:physics` rather
than by scene-core reaching into physics. Verified against real Jolt, not a fake: bodies are
moved, then STEPPED, because the simulation gets the last word on a position and a shift that
does not survive a step has not happened -- which is precisely what shifting only the ECS
transform looked like.

**Shadow texel size, half fixed (2026-08-31).** Fitting cascades with the real viewport aspect
instead of `CONSERVATIVE_ASPECT` took the near cascade from 1.91cm to 1.34cm per texel. The other
half -- capping the shadow DISTANCE rather than fitting out to the camera's far plane, worth
another 2.4x -- is NOT in. It is standard practice and the arithmetic is right, but it made the
shadow disappear entirely in `RendererHeadlessCascadedShadowTest`'s top-down probe scene (a
camera 14m up, a 40m cap): every fragment still lands inside a cascade that contains it, and the
shadow is gone anyway. Something about the fit at short shadow distances is wrong, and shipping
the speedup while that is unexplained would trade a visible artefact for an invisible one.

**Cascaded shadows, landed (2026-08-30).** The last of D28's deferred rows. Depth targets carry
layers, the depth pass renders one per cascade through a pass-scoped matrix block, and
`lit_shadow` samples the array. Two things are worth carrying forward: the cascade a fragment
uses is decided by CONTAINMENT rather than by comparing a view distance against split planes,
because the boxes are fitted to spheres and a distance test agrees with that fit only
approximately -- where it disagrees, a band of shadow goes missing. And every layer is rendered
every frame even when fewer cascades are configured, because an unrendered layer is an image
subresource in an undefined layout and sampling it is a validation error rather than a dark
pixel.

**Spatial index, landed as an opt-in (2026-08-30).** `SpatialGrid` plus `SpatialIndexSystem`,
with `RenderSystem3D` culling through the index when a scene installs one. The benchmark that came
with it is the part worth keeping: the query is five times faster than scanning every entity, and
maintaining the index costs twice what the scan does, because this ECS reports neither movement
nor destruction and the only way to know an entity moved is to look at it. So the index is not a
free upgrade for culling; it pays when culling, AI range queries and picking share one pass.
Deferring it "until a real query load exists" was the right call -- one query is not enough.

**LOD hysteresis, landed (2026-08-30).** `LodGroup` selection carries a band and the level it
last drew, so an entity sitting on a threshold stops alternating between two meshes. Terrain's
clipmap needed nothing: its rings are concentric and always present, and the shader morphs
between them -- there is no discrete switch there to debounce.

**Floating origin, landed for transforms (2026-08-30).** `FloatingOriginSystem` rebases every
root `Transform` in whole steps once the `StreamObserver` passes a threshold, and `WorldOrigin`
records where the local frame sits so absolute coordinates survive the shift.
`WorldPartitionSystem` reads it and streams in absolute space, so which cells are loaded does not
depend on where the origin happens to be.

Physics is the stated gap. `PhysicsSystem` reads Jolt bodies back into transforms every frame, so
a body overwrites its shifted position on the next step, and `PhysicsWorld` has no reposition or
shift-origin call to fix that with. A scene mixing physics bodies with a shifting origin is
unsupported until it does. `OriginShiftListener` is the seam anything else holding world
coordinates -- a nav grid, a spatial index, a cached path -- follows the shift through.

**Streaming, partially landed (2026-08-29).** `WorldPartitionSystem.update` was an empty body, so
the grid and its hysteresis band only ran when something called `updateObserverPosition` by hand
and nothing did. It now drives itself from a `StreamObserver`-tagged entity's `Transform` -- a tag
rather than a camera lookup, because `:awake:scene:scene-core` cannot see `Camera` and because
streaming should follow the player rather than a camera that pans or cuts away.

What remains is the substantive half: **loading is still synchronous on the frame thread.**
`WorldCellStreamListener.onCellLoad` is called inline, so a consumer doing real IO there stalls
the frame. Async scheduling, cancelling an in-flight load when its cell leaves the radius, and
applying results back on the frame thread are one design, not three patches, and want their own
pass. `AssetManager` above is the natural companion -- there is nothing to schedule until
something owns loaded resources.

### Not a limitation

Registering a custom render pass from consumer code already works. D27 shipped
`ContentFeature` and `RenderPlan.contentFeatures`, and both samples use it. A starter-kit can
declare a `PipelineSpec` and have the engine build it today; what it cannot declare is that
pipeline's texture bindings.

## What moves out of Awake

To the starter-kit:

- `WaterSurfaceComponent`, `AtmosphereComponent`, `CloudLayerComponent`
- `WaterRenderSystem`, `AtmosphereSystem`
- `WaterShaderSource`, `AtmosphereShaderSource`
- Splat layer selection, tiling and terrain material authoring
- Biomes, vegetation scatter, prop placement, procedural world generation
- Character slot vocabulary and atlas-packing policy
- Camera feel and controller tuning

This is a net deletion from Awake: the two systems being moved are the dead ones, and the
components they read have no other caller.

Two components stay, trimmed:

- `TerrainComponent` keeps heightmap, weightmap and clipmap config; `layerTextureNames` is
  content and goes with the splat authoring above.
- `ModularCharacterComponent` already keys slots by a consumer-supplied string, so no slot
  vocabulary had to move; only its `equip`/`unequip` method naming failed the neutrality test
  and was renamed to `setSlot`/`removeSlot`.

To the private consumer pack, not the public starter-kit: proprietary map/model/terrain format
parsers, their walk/fly/move attribute encodings, map registries, and per-game texture atlas
builders. Licensed formats and assets — see `awake-copyright-provenance`.

## Relation to D27

D27 drew the line one level down: a graphics backend knows hardware, content is split out to
`awake:asset:shaders` or the app. D28 draws the next line up: of the content D27 placed
outside a backend, which belongs to Awake and which to a consumer. The two are consistent, and
D27's `ContentFeature` is the seam D28's starter-kit content registers through.

## Consequences

Sequence is fixed by dependency, not priority: bindings, then texture arrays and depth
resolve, then clipmap draw. Streaming and the asset manager stay deferred until one terrain
cell renders — an asset manager with nothing to stream cannot be validated.

The RFC's own P0 ordering does not survive this decision. Its "World Partitioning" and
"Modular Character" P0 rows are gated behind terrain rendering, and its water and atmosphere
rows are no longer Awake work at all.

Both RFC copies are now stale. Reconciling them is part of the first implementation task, not
a separate doc pass.
