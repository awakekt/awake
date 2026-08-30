# Terrain rendering plan

**Status:** in progress — milestone 0 is complete: `:awake:asset:terrain` provides an immutable
heightmap and ordinary whole-grid mesh builder. `MutableHeightmap` now supplies atomic edits,
revisioned dirty regions, and immutable snapshots; Studio proves one source can create both a mesh
and `HeightFieldShape`. The next approved slice is local surface queries, followed by tiled
patches **without LOD**. This plan deliberately does not turn `HeightFieldShape` into a rendering
API.

## Decision

Terrain is authored geometry content backed by a reusable terrain asset, with an optional adapter
to the already-existing physics collision shape. It is neither a Vulkan/WebGPU feature nor a
`PhysicsShape` extension. The existing `scene:rendering` `LodGroup` and `RenderSystem` already
provide per-entity mesh LOD selection and `MeshBounds` frustum culling; terrain must consume
those generic capabilities rather than introduce a terrain render feature.

The KMP module is:

```text
:awake:asset:terrain
```

It owns a backend-neutral, rectangular height-sample asset, validation, and initial ordinary mesh
generation using `core:geometry` and `core:color`. It does not depend on physics, scene, render
passes, shaders, or a native image codec.
The first two consumers are intentionally separate:

```text
TerrainHeightmap asset
  ├─ scene/game composition: patch entities with MeshRenderer/MeshBounds/LodGroup
  │    └─ scene:rendering RenderSystem: existing culling and generic mesh-LOD selection
  │         └─ backend:*: existing generic mesh/buffer/pipeline/command implementation
  └─ scene/game composition: explicit square-region → HeightFieldShape conversion
       └─ backend:jolt: collision body
```

The conversion is explicit because the two data contracts differ: rendering needs rectangular
regions, tiles, and independent visual LOD; Jolt's first collision shape is square, static-only,
and has no editable update contract. A terrain collider may use the same source samples, but it
is never the renderer's mesh source or synchronization mechanism.

### Sample: one source, visual mesh, and optional collision shape

```kotlin
val heightmap = Heightmap(
    samples = authoredSamples,
    width = 129,
    depth = 129,
    scale = Vec3f(1f, 0.25f, 1f),
)

val visualMesh = heightmap.toPositionNormalColorMesh { _, _, height -> terrainColorFor(height) }

// Game/scene composition only: Jolt currently requires a square static heightfield.
val collisionShape = HeightFieldShape(
    heights = heightmap.copySamples(),
    sampleCount = heightmap.width.also { require(it == heightmap.depth) },
    scale = heightmap.scale,
)
```

`visualMesh` works for rectangular heightmaps and does not require physics. The collision
conversion is deliberately explicit: a rectangular map fails rather than being silently cropped,
centred, or resampled.

## Scope and release slices

### Milestone 0 — shared source and single grid (complete)

- `Heightmap` is rectangular, immutable, row-major `z * width + x`, Y-up, corner-origin.
- It owns defensive copies of input samples and scale, and validates finite positive scale.
- `toPositionNormalColorMesh` derives packing from `VertexFormat`, generates scale-aware normals,
  and uses counter-clockwise Y-up triangles.
- Studio uses the same samples for the render mesh and an explicit Jolt `HeightFieldShape`.

### Milestone 0.1 — local surface queries (next)

Terrain must be useful to gameplay before it is a large-world renderer. Add one backend-neutral
query to `asset:terrain`, conceptually `sampleSurfaceAtLocal(x, z): TerrainSurfaceSample?`:

- Input X/Z is in the heightmap's **local** coordinates, not scene world coordinates.
- A successful result contains interpolated local height and an upward local normal. A request
  outside the sampled rectangle returns `null`; it never clamps silently.
- State the interpolation rule (expected: bilinear height interpolation with the matching local
  surface normal) and its border behavior. Grid-cell and exact-sample queries must agree.
- Scene/game composition applies its entity `Transform` when it needs a world-space point. A
  world normal uses the transform's inverse-transpose rule (not ordinary point multiplication),
  which keeps `asset:terrain` free of scene/ECS dependencies and makes rotated/non-uniformly
  scaled terrains unambiguous.
- The query has no physics dependency. It is for terrain-following gameplay, prop placement,
  camera grounding, decals, and editor tools whether or not a Jolt world is active.

**Gate:** common tests prove interior interpolation, exact samples, non-square horizontal scale,
boundary behavior, normal direction, and immutable input ownership. A composition test proves a
transformed local result becomes the expected world point/normal.

### Milestone 0.2 — dynamic height edits (complete)

- `MutableHeightmap.apply(edits)` validates an entire batch before changing any sample. A bad
  coordinate or non-finite height rejects the batch without a partial mutation.
- A committed batch returns `HeightmapChange(revision, dirtyRegion)`, where `dirtyRegion` is the
  smallest inclusive grid rectangle containing changed samples. No-op batches return `null`.
- The mutable object owns no listener registry. The caller passes the explicit change to whichever
  systems it owns; each independently decides whether to rebuild a mesh patch, a collision region,
  a water mask, or procedural placements.
- `snapshot()` returns a stable immutable `Heightmap` for a mesh/collider consumer. An edit never
  mutates an already-issued snapshot.

**Gate:** common tests prove batch atomicity, revision behavior, smallest dirty region, no-op
handling, and snapshot isolation. Automatic mesh/collider/water/placement refresh remains out of
scope until each consumer has a real lifecycle.

### Milestone 1 — tiled patches, no LOD (after surface queries)

This is the first full rendering slice. It must render one terrain as multiple static patches,
with every patch at the same resolution. That proves boundaries, lifecycle, and ordinary draw
integration before LOD introduces crack stitching.

- `asset:terrain` adds a backend-neutral patch layout and mesh generation from a `Heightmap`.
  A patch owns its sample/cell range and conservative local bounds; it owns neither a GPU handle
  nor a material.
- Adjacent patches share coordinate and normal conventions exactly. The owner must state whether
  boundary samples are duplicated (expected for independent patch meshes) and prove that both
  copies produce the same position and normal.
- Game/scene composition creates ordinary patch entities with `MeshRenderer` and `MeshBounds`.
  Existing `RenderSystem` culling proves the first slice; no terrain component, render feature,
  contract port, or backend declaration is introduced.
- Studio renders a map large enough to cross at least four patch boundaries using the ordinary
  opaque path. Physics remains optional and is not attached to Studio's no-physics lifecycle.

**Gate:** a fixed camera renders every patch with no gap, overlap, normal discontinuity, or
duplicate upload on repeated frames. No LOD, culling, or texture-layer API is added in this
milestone.

### Milestone 2 — reuse existing visibility and generic LOD

- Each patch supplies its conservative local `MeshBounds`; existing `RenderSystem` handles
  frustum culling through its normal opaque draw path.
- Give a patch ordinary `LodGroup` levels only after its compatible mesh variants exist.
  `RenderSystem` already selects exactly one mesh/material by camera distance and preserves
  normal opaque pipeline batching.
- Verify that `LodGroup`'s documented far-distance fallback means a patch never vanishes merely
  because it passed its final LOD threshold. No terrain render feature or backend code is added.

**Gate:** a deterministic camera fixture proves existing `MeshBounds` culls expected patch
entities and existing `LodGroup` picks the expected mesh level without losing a far patch.

### Milestone 3 — patch-level geometric LOD

- Generate terrain mesh variants from regular heightmap sampling, not general-purpose
  `MeshSimplifier`: an independent edge collapse can change a shared patch border and create a
  crack. `MeshSimplifier` remains useful for ordinary imported meshes, not this terrain contract.
- Add a declared terrain LOD policy: error metric, distance thresholds, ordering, and transition
  rule. It feeds ordinary `LodGroup` levels; it does not replace generic renderer LOD.
- Adjacent patches at differing resolutions must use one agreed crack-prevention mechanism:
  stitched indices, skirts, or a shared-edge topology. Choose one after a measured prototype;
  never accept visible cracks as an interim contract.
- `LodGroup` has no neighbour-aware stitching or hysteresis, so add terrain-specific selection
  state only if the chosen seam strategy needs it. Keep that policy above `RenderSystem`, not in
  a backend or render pass.

**Gate:** an adjacent-LOD fixture proves every shared edge is watertight; a threshold fixture
proves selection stability; a golden frame includes both a steep and flat transition.

### Milestone 4 — production validation and optional collision composition

- Define the explicit square-region-to-`HeightFieldShape` helper at scene/game composition only.
  It rejects incompatible rectangular or dynamic requests rather than cropping or resampling.
- Test scene load/unload repeatedly for release of mesh resources and optional collision bodies.
- Record patch count, visible triangles, draw count, uploads, CPU selection time, allocation
  profile, and GPU frame time at a stated target map size.

**Gate:** collision and visual corner/interior samples agree, lifecycle is leak-free, and a
measured budget is recorded before streaming or editing is proposed.

## Explicitly not covered

Runtime sculpting, GPU tessellation, clipmaps, virtual texturing, splat maps, vegetation,
navigation, streaming, erosion, terrain editing, procedural generation, image decoding policy,
and a generic world-partition system are follow-up decisions. No native Jolt or Vulkan/WebGPU
change is authorized by this plan unless a concrete generic GPU/RHI primitive is proven missing.

### Implemented sample spike

`samples:studio` now contains one sample-owned, immutable 9×9 height array. Its
`TerrainExampleAsset` creates a shared `Heightmap`, asks `asset:terrain` for a standard
`VertexFormat.PositionNormalColor` mesh, and explicitly copies the same samples into
`HeightFieldShape`, with the same corner origin and scale. Studio renders it through its ordinary
`MeshRenderer` path; it intentionally does not create a physics body because Studio has no live
`PhysicsWorld` lifecycle. The accompanying test asserts that a visual vertex maps to its collider
sample exactly. The terrain module's common tests cover dimensions, defensive ownership,
row-major indexing, scale-aware normals, topology, and derived mesh stride. This is proof of
source-data alignment, not an implementation of the reusable terrain renderer described below.

## Ownership and naming rules

| Concern | Owner | Must not own |
|---|---|---|
| Height samples, dimensions, spacing, validation | `asset:terrain` | GPU handles, collision bodies, shader code, image loading policy |
| Dynamic sample edits and dirty regions | `asset:terrain` | listener-owned mesh/collider/water/placement refresh policies |
| Local height/normal sampling | `asset:terrain` | scene transforms, rigid-body queries, or Jolt imports |
| Terrain entity/component and authored material choice | `scene:rendering` / game composition | native backend types or Jolt handles |
| Generic visibility and ordinary mesh LOD | existing `scene:rendering` `RenderSystem`, `MeshBounds`, `LodGroup` | terrain-specific backend API or a second draw loop |
| Terrain patch layout and crack-safe level construction | `asset:terrain` / scene-game composition | Vulkan/WebGPU types, independent edge-collapse LOD |
| Terrain shaders and `PipelineSpec` declaration | `asset:shaders` | driver calls or backend-specific shader branches |
| Buffers, images, descriptor/bind-group allocation and command encoding | `backend:vulkan` / `backend:webgpu` | `Terrain*` declarations or terrain visibility/LOD decisions |
| Physics conversion | scene/game composition beside the terrain authoring | rendering mesh generation or mutable collider updates |

Terrain uses existing opaque rendering and generic mesh LOD. A backend sees only generic buffers,
textures, pipeline layouts, and commands. `verifyBackendLayering` must remain at zero exemptions.

## Settled contracts and decisions deferred to their milestone

| Topic | Decision |
|---|---|
| Coordinates | Row-major `z * width + x`, Y-up, corner origin; `Vec3f` allows independent X/Z spacing and height scale. |
| Ownership | `Heightmap` defensively copies its samples and scale. Consumers request an explicit copied sample array. |
| Dynamic edits | `MutableHeightmap` validates and commits batches atomically, returns a revisioned dirty region, and creates immutable snapshots; consumers are notified explicitly by their owner. |
| Normals | Shared CPU mesh generation uses central differences in the interior and one-sided differences at the border. |
| Surface query | `asset:terrain` queries local X/Z only, returns an explicit out-of-bounds result, and documents interpolation. Scene/game composition transforms points and uses inverse-transpose for world normals. |
| Bounds | Milestone 1 adds conservative per-patch height bounds; frustum selection begins only in milestone 2. |
| Material | Start with one existing ordinary opaque material/colour; no splat-map or texture-layer contract yet. |
| Collision | A later adapter rejects non-square/non-static requests and preserves corner origin; it never crops, centres, or resamples. |
| LOD seams | Decide between stitched indices, skirts, and shared-edge topology only in milestone 3, backed by a visual and deterministic seam fixture. |

## Verification matrix

| Concern | Proof |
|---|---|
| Asset contract | Existing common tests cover dimensions, defensive ownership, row-major indexing, scale-aware normals, derived stride, and whole-grid topology. |
| Dynamic edits | Common tests cover atomic batch rejection, revision/no-op behavior, smallest dirty region, and snapshot isolation. |
| Surface query | Common fixtures cover exact samples, bilinear interior samples, scaled axes, out-of-bounds behavior, and local normal direction; one composition fixture covers transform application. |
| Tiled topology | Per-patch tests cover exact cell coverage, index range, coordinate continuity, equal duplicated-border normals, and conservative bounds. |
| Visibility | Existing `RenderSystem` fixtures plus a terrain patch fixture cover inside, outside, and frustum-boundary `MeshBounds` cases. |
| Generic LOD | A `LodGroup` fixture proves the expected near/far mesh and its non-vanishing far fallback. |
| Terrain LOD | Adjacent differing-level fixtures prove watertight edges; threshold fixtures prove any terrain-specific hysteresis. |
| Backend boundary | `verifyBackendLayering` stays clean; shared algorithms are not duplicated and backend declarations contain no terrain vocabulary. |
| Visual output | Desktop golden frame from a known camera, including patch borders, a steep region, and a flat region. |
| Collision agreement | Raycast and visual marker agree on known corner and interior samples once the optional adapter exists. |
| Lifecycle | Repeated scene load/unload releases patch buffers, textures, and optional physics bodies. |
| Performance | Measured patch count, visible triangles, draw count, uploads, allocation profile, CPU selection time, and GPU frame time at the target map size. |

## Exit criteria

One opt-in terrain can render correctly on every backend that supports the required generic
pipeline capability; it uses no terrain declaration in a backend; its visual and optional
collision origins agree; adjacent visible LOD patches do not crack; and its measured budget is
recorded before broader terrain systems are proposed.
