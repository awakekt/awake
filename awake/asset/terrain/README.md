# Awake Terrain Asset Pipeline (`awake:asset:terrain`)

The `awake:asset:terrain` module provides data models, GPU geometry generators, dynamic heightfield sculpting, and texture splatting definitions for rendering landscapes in the Awake Engine.

---

## Architectural Paradigms: Geometry Clipmaps vs Worldstream

In Awake, terrain rendering is split into two complementary "cousin" paradigms depending on map scale and streaming requirements:

```
                            Terrain Architecture
                                      │
           ┌──────────────────────────┴──────────────────────────┐
           ▼                                                     ▼
┌─────────────────────────────────────┐       ┌─────────────────────────────────────┐
│       Geometry Clipmaps             │       │        Cell Worldstream             │
│ (Awake Core: awake:asset:terrain)   │       │ (Awake Pro: :plugins:worldstream)   │
├─────────────────────────────────────┤       ├─────────────────────────────────────┤
│ • Concentric rings around camera    │       │ • Discrete (x, z) spatial cells     │
│ • Rings follow & snap to grid       │       │ • Asynchronous disk/network stream  │
│ • GPU vertex-displaced heightmap    │       │ • Individual cell meshes & colliders│
│ • O(1) constant VRAM footprint      │       │ • Dynamic memory budgeting / LOD    │
│ • Zero chunk pop-in / no seams      │       │ • Massive open-world MMO scale      │
└─────────────────────────────────────┘       └─────────────────────────────────────┘
```

### Architectural Comparison

| Decision Factor | Geometry Clipmaps (Awake Core) | Cell Worldstream (Awake Pro) |
| :--- | :--- | :--- |
| **Primary Class** | `TerrainClipmapGeometry`, `TerrainClipmapTracker` | `MeshCellStreamer`, `HeightFieldCellStreaming` |
| **Memory Model** | **Constant $O(1)$**: Fixed number of concentric rings | **Dynamic**: Budgeted cache of active cells around player |
| **Mesh Lifecycle** | Meshes created once on attach; transforms updated per frame | Meshes spawned and destroyed dynamically in coroutines |
| **GPU Displacement** | Vertex texture fetch via `AslTerrainShader` / `AslTerrainSplatShader` | Pre-computed mesh geometry or localized patch displacement |
| **Physics Collision** | Single continuous heightfield or centralized collider | Per-tile Jolt physics shapes (`heightFieldTile`) |
| **Ideal For** | Island maps, flight simulators, continuous single regions | Multi-zone open worlds, seamless multi-kilometer MMOs |

---

## Core Capabilities

### 1. Heightmap & Dynamic Sculpting
* **`Heightmap`**: Read-only elevation grid with bilinear interpolation, normal calculation, and elevation sampling.
* **`MutableHeightmap`**: Thread-safe dynamic sculpting with real-time brush stamps, Gaussian smoothing, and terraforming edits.
* **`HeightmapMeshBuilder`**: Generates discrete index-buffered `MeshGeometry` for single-tile or static terrain grids.

### 2. Geometry Clipmaps (`clipmap/`)
* **`TerrainClipmapConfig`**: Configures ring count (e.g., 5 rings), resolution per ring (e.g., $64 \times 64$), and base grid spacing.
* **`TerrainClipmapGeometry`**: Generates the nested concentric ring meshes (central full patch, outer hollow ring meshes with stitching seams).
* **`TerrainClipmapTracker`**: Camera tracker calculating snapped grid centers for each ring per frame without CPU mesh reallocation.

### 3. Multi-Texture Splatting (`splat/`)
* **`TerrainSplatWeightMap`**: Stores and validates 4-channel (`rgba`) weight data for multi-texture ground blending.
* **Shader Integration**: Seamlessly pairs with `PackShaderSets.TerrainSplat` (`AslTerrainSplatShader` in `awake:asset:shader-pack`) to blend a 4-layer `texture_2d_array` diffuse texture across ground surfaces.

---

## Usage Example: Geometry Clipmap Driver

```kotlin
// 1. Configure clipmap rings
val config = TerrainClipmapConfig(
    ringCount = 5,
    ringResolution = 64,
    baseSpacing = 2.0f,
)
val tracker = TerrainClipmapTracker(config)

// 2. Build ring meshes once
val geometries = TerrainClipmapGeometry.buildAllClipmapMeshes(config)
val ringMeshes = geometries.map { renderer.createMesh(it) }

// 3. Update ring entities per frame based on camera position
fun onFrame(cameraEye: Vec3f) {
    val ringStates = tracker.update(cameraEye)
    for (i in ringEntities.indices) {
        val entity = ringEntities[i]
        val state = ringStates[i]
        world.add(entity, Transform(position = state.snappedCenter))
    }
}
```
