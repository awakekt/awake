# Terrain surface layers plan

Date: 2026-09-27
Status: **proposed** — no implementation authorized until the open decisions at the end are
answered. Amends [D28](../architecture/decisions/D28-open-world-framework-boundary.md) (see
"Boundary").

## Goal

Render terrain that blends an unbounded palette of surface layers, with the per-pixel cost fixed
regardless of how many layers a world uses. The first consumer is a private open-world
content pack; the Studio terrain template is the second. The shading must be a pluggable module so
that a better surface model can replace it and so that the authoring tools can be sold.

## Current state

- `terrainContentFeature` draws the clipmap heightfield with `TerrainShader`: vertex-stage
  displacement and ring morphing, then one constant shade. The geometry carries a single upward
  normal, so every terrain pixel is the same grey.
- The feature reads its bindings from `TerrainShader` directly. A consumer cannot add a surface
  texture or a fragment stage without copying the whole feature and its vertex stage.
- `TerrainSplatShader` (4 layers, RGBA weightmap, `texture_2d_array`) is exactly that copy. It
  duplicates the clipmap vertex stage line for line, and nothing draws with it.
- `TerrainComponent.splatMap` and `TerrainMaterial` (colour map, diffuse layers) exist, and no
  render path reads either.
- `ContentFeature` already binds arrayed textures (`TextureAsset.layerCount > 1`, validated
  against the declared binding). That D28 primitive landed, so the missing piece is the terrain
  seam, not texture arrays.
- The `terrain` scene component binding (`SceneTerrainBinding`) lives in Studio
  (`awake-pro:awake/editor/scene`), not Core. A game built on Core alone cannot load a terrain
  scene that Studio authored.
- Studio's render plan (awake-pro `f670798`) registers only the skybox content feature. No
  terrain draw is wired there yet.

### Layer statistics from a real consumer

Measured from one 40-tile region of the private consumer pack's converted legacy terrain,
ignoring per-patch layer flags (so the overlap is a slight overstatement):

| Measure | Value |
|---|---|
| Distinct textures across the region | 59 |
| Layers per tile | average 8.2, maximum 27 |
| Texels with 1 layer | 83.0% |
| Texels with 3 layers or fewer | 97.5% |
| Texels with 4 layers or fewer | 98.5% |
| Texels with 5–7 layers | 1.5% |

A world uses many layers; a texel uses few. A 4-channel weightmap limits the world, and stacking
N/4 weightmaps makes every pixel pay for the whole world.

## Decision (proposed)

Separate what a texel uses from what the world offers:

1. **Layer palette.** An ordered list of layers per terrain: albedo (alpha holds height), an
   optional normal map, world-space tiling, blend sharpness and an opaque physical-material id.
   At load the palette packs into one texture array per map kind. An 8-bit index allows 256
   layers, which is also WebGPU's default `maxTextureArrayLayers`.
2. **Control map.** Two RGBA8 images at heightmap resolution: `indices` holds the 4 strongest
   palette indices for the texel, and `weights` holds their weights, normalised to sum to 255.
   The encoder keeps the top 4 and renormalises, which is exact for 98.5% of the measured texels.
3. **Fixed-cost shading.**
   - The fragment stage reads the 4 neighbouring control texels with `textureLoad`. Indices
     cannot be filtered.
   - It merges their indices into at most 4 layers, each weighted by its summed bilinear
     contribution, and drops the weakest when the union exceeds 4.
   - It samples each array at most 4 times.
   - It blends by height (albedo alpha plus weight, sharpened per layer) rather than linearly.
   - Adding a layer is a data change. The shader and its cost do not change.

Rejected:

| Alternative | Why not |
|---|---|
| N/4 RGBA weightmaps | Every pixel samples every map; a 27-layer tile needs 7 maps. |
| Nearest-texel control lookup | Cheap, but leaves blocky layer boundaries at control-map resolution. |
| 16-sample bilinear (4 taps × 4 slots, no merge) | Exact, but 4× the array samples. Kept only as a reference image for the merge's error test. |
| Virtual texturing | Cost independent of layers and handles decals/roads, but it is a streaming system. It fits later as another surface provider over the same palette and control data, not as the base. |

## Boundary

D28 put "splat layer selection, tiling and terrain material authoring" in a consumer starter-kit
and kept draw emission from a `Heightmap` in Awake. This plan keeps that split and adds two Core
seams. Each seam meets the framework boundary's limitation test. There are two consumers (the
private consumer pack and the Studio template), and neither can do this through public APIs today.

| Owner | Scope |
|---|---|
| Core (`awaken`, Apache-2.0) | Terrain surface seam, heightmap normals, and the `terrain` scene binding with a surface-provider registry. No layer vocabulary. |
| Terrain layers runtime (see open decision 1) | Palette and control-map model, codecs, surface shader, surface provider. |
| Editor contract (`awake:editor:contract`, Apache-2.0) | `TerrainImporter`: a source format converted to the runtime's canonical files. |
| Studio Pro (`awake-pro`, commercial) | Palette editor, control-map painting, rule-based auto-material, import wizards. |
| Private consumer pack | Its legacy-format importer. D28 keeps proprietary format parsers there. |

### Core seams and their justifications

| Seam | Limitation it removes |
|---|---|
| `terrainContentFeature(surface: TerrainSurface?)` plus a shared ASL clipmap vertex stage | Bindings are read from `TerrainShader`, so a surface means forking the feature. `TerrainSplatShader` shows the fork already happened once. |
| Normals derived from the heightmap in the shared vertex stage | Every surface model needs a real normal, and the base shader needs one too. Deriving it belongs with displacement, not in each surface. |
| `terrain` scene binding in Core, with `surface: { provider, version, payload }` | A shipped game cannot load Studio-authored terrain without Studio code. |
| `TerrainSurfaceProvider` registry keyed by provider id | Lets a scene name a surface model without Core knowing it. An unknown provider falls back to base shading with a warning and keeps the payload, the same as `SceneExtension`. |

```kotlin
// Core: declares what a surface adds. The clipmap geometry, tracker and vertex stage stay Core's.
class TerrainSurface(
    val shader: AslShaderDefinition,           // built on the shared clipmap vertex stage
    val textures: Map<Int, TextureAsset>,      // bindings beyond the heightmap
)

interface TerrainSurfaceProvider {
    val id: String                             // e.g. "awake.terrain.layers"
    suspend fun resolve(payload: JsonElement, assets: AssetSource): TerrainSurface
}
```

Deleted by this plan: `TerrainSplatShader`, and `TerrainComponent.splatMap`/`material` in favour
of a surface reference. D28 already called for removing the unused splat shader.

## Terrain layers runtime

### Files

- `*.terrainpalette.json`: `formatVersion`, then ordered `layers[]`, each with `id`, `albedo`,
  `normal?`, `tiling`, `blendSharpness` and `physicalMaterial?`. Palette position is the control
  index. Removing a layer is a remap, which the editor owns.
- `*.control.indices.png` and `*.control.weights.png`: RGBA8, heightmap resolution. Unused slots
  have weight 0.
- `*.lightmap.png` (optional): an RGBA8 multiply map for baked lighting or a colour map. Legacy
  formats often bake lighting beside each layer's alpha; an importer emits it here.

### Load-time rules

- All layers of one map kind share a resolution. Mismatches are rejected, not resampled; the
  importer normalises sizes, and the runtime refuses to guess.
- Per-layer tiling and blend parameters live in an N×1 RGBA8 layer table texture, because a
  content feature owns one uniform block with a fixed layout. Validate the RGBA8 precision
  against the tiling range in phase 2.
- Budget, since arrays are raw RGBA8 with a CPU mip chain: the measured 59-layer palette at 256² come to
  about 20 MB of albedo; at 1024² the same palette is about 330 MB. The importer caps layer
  size, and compressed formats are a Core follow-up.

## Importers

`TerrainImporter` (editor contract) turns a source into a heightmap, a palette, control maps and
an optional lightmap, and reports what it lost. It runs at authoring time, on desktop or as a
CLI. The web build and shipped games only read canonical files, so no importer code runs there.

The consumer pack's importer, for example:

- stitches a region's tiles into one heightmap;
- maps each tile's layer textures into the region palette;
- converts per-layer alpha to top-4 control texels;
- emits the lightmap;
- reports the dropped weight per tile.

## Phases

### Phase 0 — confirm prerequisites

- `MipChain` handles `layerCount > 1` on both backends.
- WebGPU limits hold for the region: array layers, texture size, sampled textures per stage.
- Studio needs a terrain content feature in its render plan. It has none today.

**Gate:** each item is answered from code or a headless run, not assumed.

### Phase 1 — Core seams

- Extract the clipmap vertex stage into a shared ASL building block. Rebuild `TerrainShader` on
  it and add heightmap normals.
- `terrainContentFeature` accepts an optional `TerrainSurface`.
- Move the `terrain` scene binding from Studio into Core and add the provider registry.
- Delete `TerrainSplatShader`, `TerrainComponent.splatMap` and `TerrainMaterial`.

**Gate:**
- Before re-recording anything, state which baseline pixels change: only shading changes, and
  coverage stays identical. Audit the diff against that statement.
- A provider fixture renders through the seam on Vulkan and WebGPU.
- An unknown provider falls back to base shading and keeps its payload through a save round-trip.

### Phase 2 — layers runtime

Data model, codecs, surface shader and provider.

**Gate** (headless pixel tests, with a positive control for each):
- A control map split between two layers renders each half in its layer's colour.
- Across a boundary between tiles with different layer sets, colour changes monotonically and
  with no step.
- A texel union of more than 4 layers keeps the strongest 4 and stays within a stated error of
  the 16-sample reference.
- A shader that ignores the control map fails all three.

### Phase 3 — Studio integration

- Studio's render plan adds the terrain content feature.
- The layers provider is compiled into the desktop and web builds. The web build cannot load
  plugin code, so compile-time inclusion is the only route there.
- The terrain inspector shows the palette.

**Gate:** Studio's Mountain template renders its layers on desktop and at studio.awakekt.com.

### Phase 4 — first consumer importer (private)

**Gate:** the consumer's measured region renders in Studio on desktop and on the web, and each tile's dropped-weight
report is recorded.

### Phase 5 — Studio Pro authoring

Palette editor, control-map brush (evicting the weakest slot when a 5th layer is painted),
rule-based auto-material, and the import wizard.

## Limits and follow-ups

- **One region per feature.** Content-feature textures are uploaded once, by design. Streaming a
  world needs either updatable content textures (a Core change, to be justified when a streamed
  consumer exists) or one feature per worldstream cell. Phase 4 loads one region.
- **Ring cracks.** Seams between clipmap rings beyond the morph remain the known clipmap
  follow-up.
- **Texture compression.** BC/ASTC arrays are needed before large palettes ship.

## Open decisions

1. **Runtime licence and home.** Recommended: the runtime (format, shader, provider) is
   Apache-2.0 in a public starter-kit, and the authoring tools are commercial in Studio Pro. An
   open file format lets any tool write it, and buyers pay for tools rather than for a format.
   The alternative is a commercial runtime with a redistribution licence, which raises the
   adoption barrier and fragments the format. The starter-kit repository does not exist yet;
   this runtime would be its first module.
2. **D28 amendment.** Approve moving the `terrain` scene binding into Core and adding the
   surface seam and provider registry.
3. **Control-map resolution.** Heightmap resolution (128 per tile in the measured source), or an
   independent, finer resolution for sharper layer edges at extra memory cost.
