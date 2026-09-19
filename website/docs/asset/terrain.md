# Terrain

The terrain module provides heightmaps, terrain mesh generation, geometry clipmaps, and texture
splatting data for landscape rendering.

Install [`awake:asset:terrain`](../getting-started.md#terrain-assets) in the source set that builds
or consumes terrain data.

## Core types

- `Heightmap` samples elevation and interpolated normals.
- `MutableHeightmap` supports runtime sculpting and smoothing.
- `HeightmapMeshBuilder` creates indexed terrain geometry.
- `TerrainClipmapConfig`, `TerrainClipmapGeometry`, and `TerrainClipmapTracker` support nested
  camera-centered rings with a bounded geometry footprint.
- `TerrainSplatWeightMap` stores four-channel material weights.

Terrain data is independent of the graphics backend. Pair it with the shader and scene-rendering
modules when drawing the generated geometry.
