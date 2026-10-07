<!--
SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz

SPDX-License-Identifier: Apache-2.0
-->

# engine-showcase

A catalogue of focused engine demonstrations — one scene per capability, each proving a single
thing works end to end.

The sample uses Core runtime components. Editor behaviour — selection, gizmos, play mode — belongs
to Awake Studio. Anything an engine change can break, including frame rate, should be reproducible
here first, without Studio.

Navigation uses the shadcn sidebar with a pinned header/footer and a scrollable scene list.
Below 768dp, the hamburger opens it in a dismissible mobile sheet; choosing a scene closes the
sheet. On wider windows the sidebar can be hidden and restored from the toolbar.

The toolbar shows the active scene, FPS, and **Debug**. Debug opens a right sheet on wide windows
or a bottom sheet on compact windows. **Render** contains global toggles and the active scene's
controls, **Scene** contains fog settings, **Buffers** captures framebuffer attachments, and
**Stats** contains detailed performance measurements. The close button, backdrop, and Escape
dismiss the panel. Its content scrolls independently of its close button.

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

**Replacing the lit shader while it runs (desktop):** press L to toggle between the shipped
`lit_shadow` and a brighter-ambient variant built at runtime. Press K to try a variant that does not
compile; it is refused, a warning is printed, and the current shader keeps drawing. See
`ShowcaseShaderSwap` and the ASL README's dev-loop section.

## Showcases

| Id | Title | What it proves |
|---|---|---|
| `empty` | Empty | The scene document, camera and clear pass with nothing in them: the baseline a broken frame is compared against. |
| `point-lights` | Point lights | Several coloured point lights over a ground plane and lit cubes. |
| `heightfield-terrain` | Heightfield terrain | A Heightmap becomes drawable geometry and a Jolt heightfield that boxes and a character land on. |
| `cascaded-shadows` | Cascaded shadows | One shadow caster per cascade, so the near shadow is sharp and the far one still exists. |
| `gltf-viewer` | glTF viewer | glTF mesh, material and texture import. |
| `skinned-mesh` | Skinned mesh | Joint palette upload and GPU skinning. |
| `instanced-cubes` | Instanced cubes | One draw call for a 16x16x4 block of cubes through InstancedMeshRenderer. |
| `instanced-skinned` | Instanced skinned | Instancing and skinning together, animated per frame. |
| `nav-chase` | Navigation chase | A cube paths around a terrain ridge it cannot climb; bakeNavGrid reads the slope, no collider says a wall exists. |
| `particles` | Particles | A CPU emitter driving a quad batch, advanced every frame. |
| `sprites-2d` | 2D sprites | A transparent four-frame sprite atlas animated on orthographic 2D layers. |
| `spatial-audio` | Spatial audio | 3D positional audio emitters with distance attenuation and panning relative to the camera listener. |
| `ecs-stress` | ECS stress | Up to 100,000 moving entities, each with Transform and MeshRenderer components, drawn in a few instanced calls. |

`EngineShowcaseReadmeTest` fails when this table and `EngineShowcases` disagree.

The `sprites-2d` showcase uses an original lantern-firefly idle atlas generated with
[sprite-gen](https://github.com/aldegad/sprite-gen): four transparent 256×256 frames at 4 fps.
Run it with `./gradlew :samples:engine-showcase:run -Pawake.showcase=sprites-2d`.
The [atlas](src/commonMain/resources/assets/sprites/lantern-firefly/sprite-sheet-alpha.png),
[animation preview](src/commonMain/resources/assets/sprites/lantern-firefly/idle.gif), and generated
manifest and request are packaged together. The scene's `texture_animation` describes the manifest's
four regular frame rectangles. It draws through the existing textured mesh pipeline and transparent
pass, with a low alpha cutoff that discards empty texels. It can be migrated to the dedicated
`sprite` component when that component has a draw feature.

![Lantern firefly idle preview](src/commonMain/resources/assets/sprites/lantern-firefly/idle.gif)

## Measuring frame time

Click the toolbar's FPS readout, or open **Debug → Stats**, to read:

| Line | Meaning |
|---|---|
| FPS, Frame | Average over the last 30 frames |
| p99, Worst | The 99th-percentile and slowest frame over the last 240. A stall shows here, not in the average |
| Game | Fixed and frame systems: gameplay, physics, animation |
| Render | Transform propagation, scene extraction, command recording and present |
| UI, Wait | Building and staging the UI; time blocked on the GPU's previous frame |
| Visible | Renderables that survived culling, before instancing folds them into a few draws |
| Recorded | Every draw the backend issued, shadow cascades and UI included, and its triangles |
| GPU | GPU time of the frame, where the backend can time it |

Enable **Phase timings** in Stats, or press F2, to measure the Game/Render/UI/Wait split and GPU
time. The stress showcase and perf log turn them on themselves.

To reproduce a frame-rate drop without Studio, open the stress showcase with vsync off, so the frame
rate shows headroom past the display, and log a summary line every 240 frames:

```bash
./gradlew :samples:engine-showcase:run -Pawake.showcase=ecs-stress -Pawake.showcase.entities=50000 \
  -Pawake.showcase.vsync=false -Pawake.showcase.perfLog=true -Pawake.vulkan.validation=false
```

```text
PERF ecs-stress renderables=50001 fps=… avg=… p99=… max=… game=… render=… ui=… wait=… draws=… instances=… tris=… gpu=…
```

- Turn validation off (`-Pawake.vulkan.validation=false`): a `run` task enables it, and it costs
  milliseconds per frame.
- Keep the window focused and in front. An unfocused window is capped at 15 FPS.
- Compare two builds on the same machine back to back. Before blaming code, check `uptime` and
  `ps -Ao pcpu,command -r`: a busy machine drops frames on its own.

The entity count can also be changed from the debug card, and "Move entities" off measures the same
entities standing still. `EcsStressSceneFrameTest` runs the same showcase headless and prints its
frame times.

## Adding a showcase

1. Author `src/commonMain/resources/assets/examples/<id>.scene.json`. Its `"name"` **must** equal
   the id — `EngineShowcaseTest` asserts it.
2. Register an `EngineShowcase(...)` entry in `EngineShowcase.kt` with a one-sentence `summary`.
   Use `onActivated` for state a scene document cannot express, `driver` for per-frame work,
   `onDeactivated` to destroy anything it spawned, and `controls` for knobs only it has.
3. Register any new mesh or material in `registerEngineShowcaseAssets()`.
4. Add its row above, with the same summary.

`EngineShowcaseTest` then checks the id is unique and that the scene loads and instantiates. A demo
whose content is hand-tuned — terrain heights, spawn positions — deserves its own test guarding
that content, the way `NavChaseExampleDriverTest` asserts the ridge really blocks and both cubes
start on walkable ground. A demo that runs, renders, and quietly shows nothing is the failure mode
worth testing for. Frame tests boot the real render plan headless through `HeadlessPlanEngine`.
