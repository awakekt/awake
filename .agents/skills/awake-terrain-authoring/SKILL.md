---
name: awake-terrain-authoring
description: Author Awake heightmaps, dynamic terrain edits, grid meshes, and terrain collision composition. Use before changing `asset:terrain`, creating terrain patches, or consuming terrain from a game; excludes water, biomes, vegetation, and placement packs.
metadata:
  author: awake
  last-updated: '2026-08-24'
---

# Awake Terrain Authoring

Read [the terrain plan](../../docs/tasks/2026-08-24-terrain-rendering-plan.md) and
[the framework/game boundary](../../docs/reference/framework-game-boundary.md) before adding a
terrain capability.

## Ownership

`awake:asset:terrain` owns backend-neutral terrain source data and geometry only:

- `Heightmap`: immutable rectangular samples, row-major `z * width + x`, Y-up, corner origin.
- `MutableHeightmap`: explicit runtime edits, revisioned dirty regions, and immutable snapshots.
- Grid/patch mesh construction and local surface queries.

It must not own GPU handles, ECS entities, materials, Jolt bodies, water, biomes, vegetation, or
placement policy. Those consumers compose the terrain data independently.

## Dynamic edits

Use `MutableHeightmap.apply(...)` for a batch of edits. It validates the entire batch before
committing and returns `HeightmapChange(revision, dirtyRegion)` only when samples changed.

- Pass the change explicitly to the consumers your game owns; do not add a global listener
  registry to the terrain data object.
- A mesh, collision region, water mask, or placement cache decides for itself whether its affected
  region needs rebuilding.
- Use `snapshot()` when a consumer needs stable mesh or collision input. An edit must never mutate
  an already-issued snapshot.

## Geometry and coordinate rules

- Samples use local coordinates: `(x * scale.x, height * scale.y, z * scale.z)`.
- `Vec3f` is mutable: keep scale defensively owned and do not share it as a mutable constant.
- Mesh packing derives from `VertexFormat`; do not introduce hand-written stride, offset, or colour
  arrays.
- Terrain normals are shared CPU geometry logic. Do not duplicate heightmap-normal math in Vulkan
  and WebGPU.
- A future surface query accepts local X/Z only. Scene/game composition transforms the returned
  point; it uses inverse-transpose for a world normal under non-uniform scale.

## Rendering and collision composition

- Render terrain patches as ordinary `MeshRenderer` entities with `MeshBounds`; reuse
  `RenderSystem` culling and `LodGroup` mesh selection. Do not add a terrain-specific backend
  declaration, frame loop, or render feature unless existing generic paths are proven insufficient.
- `HeightFieldShape` is an explicit game/scene-composition conversion. It is square and static;
  reject incompatible source data rather than crop, centre, or resample it silently.
- `physics:api` never imports `asset:terrain`, and `asset:terrain` never imports physics.

## Routing

Water, rivers, erosion, biomes, vegetation, prop placement, procedural world generation, and game
assets belong in a consumer/private pack. Promote a reusable pack primitive into Awake only after
two independent consumers use the same public contract unchanged, or a consumer cannot proceed
without a documented minimal Awake API.

## Verification

Run `:awake:asset:terrain:desktopTest` and the relevant KMP target compilation. Add focused tests
for sample ownership, coordinate/indexing rules, dynamic edit atomicity, dirty regions, snapshots,
and patch-border continuity. Rendering or collision changes also require their domain skills.
