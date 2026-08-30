<!--
SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz

SPDX-License-Identifier: Apache-2.0
-->

# engine-showcase

A catalogue of focused engine demonstrations — one scene per capability, each proving a single
thing works end to end.

**This is not the editor sample.** `samples:studio` exercises the editor host against one authored
scene and deliberately has no catalogue; engine demonstrations were moved out of it into here. If
you are adding a demo, it belongs in this module. If you are testing editor behaviour — selection,
gizmos, play mode — that belongs in studio.

## Running

```bash
./gradlew :samples:engine-showcase:run
```

Starts on `point-lights`, the default. **To open a specific demo, pass its id** — without this you
get the default no matter which demo you are working on:

```bash
./gradlew :samples:engine-showcase:run -Pawake.showcase=nav-chase
```

Web:

```bash
./gradlew :samples:engine-showcase:wasmJsBrowserDevelopmentRun
```

## Showcases

| Id | Title | What it proves |
|---|---|---|
| `empty` | Empty | The scene document, camera and clear pass with nothing in them — the baseline a broken frame is compared against |
| `point-lights` | Point lights | Multiple coloured point lights over a ground plane and lit cubes |
| `heightfield-terrain` | Heightfield terrain | A `Heightmap` becomes drawable geometry through `toPositionNormalColorMesh` |
| `gltf-viewer` | glTF viewer | glTF mesh, material and texture import |
| `skinned-mesh` | Skinned mesh | Joint palette upload and GPU skinning |
| `instanced-cubes` | Instanced cubes | One draw call for a 10×10 grid via `InstancedMeshRenderer` |
| `instanced-skinned` | Instanced skinned | Instancing and skinning together, animated per frame |
| `nav-chase` | Navigation chase | A cube paths around a ridge it cannot climb. The ridge is *terrain*, not a collider — nothing tells the chaser a wall exists; `bakeNavGrid` reads the slope. Red crosses mark blocked samples, the yellow line is the live route |
| `particles` | Particles | CPU emitter driving a quad batch with per-frame advance |

## Adding a showcase

1. Author `src/commonMain/resources/assets/examples/<id>.scene.json`. Its `"name"` **must** equal
   the id — `EngineShowcaseTest` asserts it.
2. Register an `EngineShowcase(...)` entry in `EngineShowcase.kt`. Use `onActivated` for state a
   scene document cannot express, and `driver` for per-frame work.
3. Register any new mesh or material in `registerEngineShowcaseAssets()`.
4. Add a row above.

`EngineShowcaseTest` then checks the id is unique and that the scene loads and instantiates. A demo
whose content is hand-tuned — terrain heights, spawn positions — deserves its own test guarding
that content, the way `NavChaseExampleDriverTest` asserts the ridge really blocks and both cubes
start on walkable ground. A demo that runs, renders, and quietly shows nothing is the failure mode
worth testing for.
