# `awake:asset:mesh-lod`

Mesh level of detail at cook time: an attribute-aware mesh simplifier, and a cooker that writes the
levels it makes into a glTF asset as [`MSFT_lod`](https://github.com/KhronosGroup/glTF/tree/main/extensions/2.0/Vendor/MSFT_lod).
Pure Kotlin, on every target Awake ships, so an editor or a build tool can cook on any of them.

```kotlin
implementation(project(":awake:asset:mesh-lod"))
```

## Simplify a mesh

`AttributeMeshSimplifier` collapses edges onto one of their endpoints, cheapest first, by quadric
error plus the weighted change in the moved vertex's attributes (the approach of meshoptimizer's
`meshopt_simplifyWithAttributes`):

```kotlin
val result = AttributeMeshSimplifier.simplify(
    positions, indices,
    SimplifyOptions(targetRatio = 0.25f),
    attributes = listOf(MeshAttribute(normals, 3, weight = 0.5f), MeshAttribute(uvs, 2)),
    skin = SkinInfluences(joints, weights),
)
result.indices   // a triangle list over the ORIGINAL vertices
result.error     // the largest collapse error, relative to the mesh's largest extent
```

- **Vertices are never moved or invented.** The result indexes the input's own vertex array, so every
  kept vertex keeps its UVs, normals, tangents, colours, joints and weights and morph targets exactly,
  and the levels of one mesh can share its vertex buffer.
- **Seams don't tear.** Vertices at one position with different attributes (a UV seam, a hard edge)
  collapse together and only along the seam, so no triangle ends up spanning both sides of it.
- **Borders hold.** An open-border vertex slides only along its border, or stays put with
  `lockBorders`, which keeps separately modelled parts and per-material primitives from opening gaps.
- **Skinning bends stay.** A collapse between vertices weighted to different joints costs the
  difference in their per-joint weights, so edges across a joint go last.
- **Important regions last.** `importance` (per vertex, `0` ordinary) makes collapsing a vertex
  `1 + importance` times as costly, so a face or hands keep their detail while the rest reduces. The
  target triangle count is still met.
- A collapse that would flip a triangle or change the surface's topology is refused, and
  `maxError` stops early.

`awake:core:geometry`'s position-only `MeshSimplifier` stays as it is for callers that want new,
optimally placed vertices and no attributes.

## Cook levels into a glTF asset

```kotlin
val cooked = GltfLodCooker.cook(glbBytes, settings.forAsset("props/rock.glb"))
cooked.outcome   // Generated, OptedOut, AuthoredLods or NothingToSimplify
cooked.meshes    // triangles per level for each mesh
cooked.bytes     // the asset, in the container it came in
```

Each node with a mesh gets one node per level, listed in `extensions.MSFT_lod.ids`, and its
`extras.MSFT_screencoverage` holds the smallest screen coverage each level is drawn at, the full mesh
first. Screen coverage is the model's projected bounding-sphere diameter over the viewport height;
below the last level's value the model is culled, so the default `0` keeps it drawn. A level is a mesh
whose primitives are the full mesh's with a new `indices` accessor, so only index data is added. A
LOD node carries just its mesh and skin and is drawn at its base node's transform; it is not in the
scene, so a loader that ignores `MSFT_lod` draws the full mesh as before.

An asset that already carries levels, as `MSFT_lod` or as nodes named `Name_LOD0`, `Name_LOD1`, ...,
is left as authored. `GltfLods.read` returns its chains either way, for a runtime to build LOD groups
from. Points and lines, and a level that wouldn't cut at least 15% of the previous level's
triangles, are skipped.

## Project settings

`ProjectLodSettings` is the project's `awake.lod.json`: defaults for every model, and per-asset
overrides keyed by project-relative path. Within an asset, `meshes` overrides settings per glTF mesh
name, so a game decides which parts keep their detail:

- `enabled: false` opts an asset, or one mesh, out of generated levels.
- `levels` per asset or per mesh: a head mesh can keep `0.7` / `0.5` / `0.3` while the body uses the
  defaults.
- `jointImportance`, keyed by joint node name, for skinned meshes: vertices weighted to those joints
  cost `1 + importance` times as much to collapse (weighted by their skin weights), so the silhouette
  and face survive simplification.

```json
{
  "formatVersion": 1,
  "defaults": {
    "baseScreenCoverage": 0.25,
    "levels": [
      { "ratio": 0.5, "screenCoverage": 0.12 },
      { "ratio": 0.25, "screenCoverage": 0.05 },
      { "ratio": 0.1, "screenCoverage": 0 }
    ],
    "lockBorders": true
  },
  "assets": {
    "props/hero-statue.glb": { "enabled": false },
    "props/fence.glb": { "levels": [{ "ratio": 0.3, "screenCoverage": 0 }] },
    "actors/character.glb": {
      "jointImportance": { "head": 4, "neck": 2, "hand_l": 2, "hand_r": 2 },
      "meshes": {
        "head": { "levels": [
          { "ratio": 0.7, "screenCoverage": 0.12 },
          { "ratio": 0.5, "screenCoverage": 0.05 },
          { "ratio": 0.3, "screenCoverage": 0 }
        ] },
        "eyelashes": { "enabled": false }
      }
    }
  }
}
```

Every field is optional; the defaults are the values above, plus attribute weights (`normalWeight`
0.5, `uvWeight` 1, `colorWeight` 0.5, `skinWeight` 1). To cook a project's models from the command
line, see `cookLods` in [`awake:asset:mesh-optimizer`](../mesh-optimizer/README.md).

## Provenance

A clean-room Kotlin implementation of published techniques: Garland and Heckbert, *Surface
Simplification Using Quadric Error Metrics* (SIGGRAPH 1997), with attribute and seam handling as
meshoptimizer documents it. No meshoptimizer (MIT) or other third-party source is copied, and the
module has no native or third-party dependency beyond kotlinx.serialization.
