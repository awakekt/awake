# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.6.0] - 2026-10-10

### Added

- **An additive surface can stay lit.** A textured `mesh_renderer` drawn `additive` adds only its base and emissive colour, unlit, which suits fire, sparks and glows. Its `pbr_material` can now set `"litWhenAdditive": true` (`PbrMaterial.litWhenAdditive`) to add the colour the scene's light gives it instead: the sun's direction, its shadows, the ambient and point lights shade it as they shade an opaque surface, and it is still added, never covering what is behind it. Fog fades it out either way rather than adding the fog colour over its quad. The default is unchanged. The shared packer turns the draw's blend and the material's switch into one `TexturedBlend` code, which the textured shader reads on Vulkan and WebGPU alike.
- **Entity tags.** A scene node's `tag` component (`"tags": ["enemy", "flying"]`) becomes its entity's `Tags` (`awake:ecs`), and gameplay finds entities by role with `world.withTag("enemy")` and `world.hasTag(entity, "enemy")`. An entity can have several tags; each is letters, digits, `_`, `.` and `-`. A project can list its tags in the manifest's optional `tags`, which editors offer as choices: `ProjectContentValidator.unlistedTagIssues` warns about a scene tag the list leaves out, and `loadProject` logs it. A prefab's tags ride on its root entity.
- **A scene binds its controls as input actions.** A new `input_actions` component names actions and what triggers each: keys, pointer buttons and on-screen controls, active while held, for the frame pressed, or switched by each press, and a toggle can start on. Systems ask for an action, never a key. A player's `movement_control` follows `move`, `jump` and `run`, which keep today's keys (W A S D or the arrows, Space, Shift held) unless the scene rebinds them, and a game reads actions of its own with `world.inputActions()`. A `canvas_element` naming an action adds to it, so a `run` Button runs and a button for a game's own action holds and presses it. A key bound to two actions is refused. The mapping itself is `InputActions` in `awake:core:input`. Running unless a key is pressed to walk is a `run` action with `"trigger": "Toggle"` and `"startsOn": true`.
- **An editor or tool opens a scene without the game's code.** Decoding with `registry.sceneJson(keepUnknownComponents = true)`, or `SceneSerializers.createJson(keepUnknownComponents = true)`, keeps a component nothing registered as a `SceneUnknownComponent` holding its JSON, instead of refusing the scene. The rest of the scene validates and instantiates; the unknown component runs nothing, is kept on its entity as `SceneUnknownComponents`, and is written back, key for key, when the scene or its world is saved through any scene `Json`. `SceneValidator.unknownComponentIssues` names each one. `loadProject` and the default `Json` still refuse such a scene, so a shipped game fails loudly on a component it doesn't link.
- **A free-fly camera's keys are input actions.** `CameraGesturePolicy.flyActions` binds the `fly` and `rise` axes and the `fast` button (`CameraFlyActions`), so a host rebinds free-fly without code in `CameraSystem`; the defaults are still W/S/A/D, E and Q, and Shift. `CameraInputProcessor` flies by `CameraControlConfig.flyActions`, by default the same with Space rising too, as before. `InputSnapshot` is now an `ActionInputSource`, so code without a scene reads a frame straight into `InputActions`.
- **An editor can play a project's own capabilities.** `SceneDocumentEditSession` takes a `componentRegistry`, which Play's snapshot is encoded, decoded and instantiated with. An editor that loads each project into a `SceneComponentRegistry.scoped` registry passes it, so a component only that project registers survives into Play; the default is still the global registry. `SceneManager.switchTo(document, componentRegistry)` instantiates the editing world with such a registry as it is. `loadCapabilityContent(scene, files, capabilities, loaded)` adds what a game's capabilities load to content the host already holds, running only their `load` and none of Core's, so an editor that keeps Core's content loaded while a scene is edited still starts Play at once. A manifest's `plugins` entry may leave `path` empty when it names a `capabilityClass`: a capability compiled into the game's own app project has no file in the project.
- **Vulkan releases reach Maven Central as one deployment and are served by the fallback repository.** A `vulkan-v*` release stages its three modules and uploads them once, as a Core release does, and attaches the bundle to its GitHub Release whatever Central says. The fallback at `https://awakekt.github.io/awake/` now serves the newest two Vulkan releases beside the newest two Core releases, so a desktop game can build on a Vulkan release Central refused.

### Changed

- **`backend:vulkan`, `backend:vulkan:bindings` and `backend:webgpu` document their whole public API.** Every public class, function and property has KDoc, and Detekt now enforces it for all three modules, as it already did for `backend:jolt`.
- **`awake:core:text` no longer ships the Roboto `.ttf` files.** They were only the font atlas generator's input, and nothing loaded them at run time; the packed atlases text is drawn with are unchanged. Each `core:text` artifact, and every game that uses it, is about 1.1 MB smaller (the desktop jar goes from 1.6 MB to 1.0 MB). The faces now live in `awake/ui/font-atlas-generator/fonts/`.

### Deprecated

- `MOVE_ACTION` and `JUMP_ACTION` in `awake:project:runtime`: use `MovementActions.MOVE` and `MovementActions.JUMP` from `awake:scene:controls`, the names of the input actions a player follows.
- `KeybindingProfile`, `KeyBinding`, `KeybindingProfileBuilder` and `keybindingProfile` in `awake:scene:controls`: use `InputActions` from `awake:core:input`, or a scene's `input_actions`. `CameraGesturePolicy.flyKeys`, `defaultFlyKeys()` and `CameraFlyAction`: use `CameraGesturePolicy.flyActions` and `CameraFlyActions`. A policy given `flyKeys` still flies by them.

### Fixed

- **Character sweeps and overlap queries make far less garbage on desktop and Android.** `JoltPhysicsWorld` keeps the Jolt shapes, collectors, settings and filters `shapeCast` and `overlapShape` used to build on every call, each registered with a `Cleaner`. A character controller's sweep now allocates about 500 B instead of 3.1 KB, and an overlap query about 100 B instead of 1.5 KB, so a scene's garbage no longer grows with how many characters it moves. `MeshColliderSystem` no longer builds a `KClass` per collider each tick. A world's queries share their scratch objects, so use a world from one thread at a time, as the loop that steps it does.
- **Snapshots keep arriving on a busy day.** A push to `main` no longer cancels the Publish run already in progress, which threw away the snapshot it was about to publish; on a day of steady merges none was published. A run in progress now finishes, at most one more waits, and each newer push replaces the waiting one, so a snapshot lags `main` by at most one run.
- **A game project with a wasmJs module configures.** `com.awakekt.awake.plugin.project-content` registered a bare `check` task in a project's root, so when Kotlin's wasm setup later applied Gradle's lifecycle plugin there, configuration failed with "Cannot add task 'check' as a task with that name already exists". The plugin now applies `base` and hangs the content checks on its `check`.
- **A physics world holds an open-world region.** `JoltPhysicsWorld` allowed 5,000 bodies on desktop, Android and iOS, and Jolt.js's default of 10,240 on wasm; one more threw "ran out of bodies", so a region with thousands of prop colliders and characters could not load. Every backend now allows 65,536, Jolt's own suggestion for a real project.
- **A turned skinned mesh is lit from where the light is.** A skinned mesh drawn on its own (`skinned_textured` and the non-instanced `skinned` shader) lit its pose-space normals against the world's light, so turning the entity turned its lit side with it while its shadow stayed right. Both shaders now turn the normal by the entity's `model`, which the skinned uniform block already carried for the shadow pass; instanced skinned meshes already did.
- **A walking character moves on every frame.** `CharacterControllerSystem` stepped characters at the fixed rate and showed only those poses, so with more frames than steps, as on a 120 Hz display, a character stood still on every other frame: a camera following it shook and its walk stuttered. It is now an `InterpolatedSystem`, as `PhysicsSystem` is. Between steps it shows each character between its last two stepped poses, the yaw turning the short way, and each step starts from the real pose. `CharacterController.teleport` shows the new place at once instead of blending to it. A host that never interpolates sees the newest step, as before.
- **A project played with `runProject` draws its `terrain`.** Only Studio installed `TerrainContentSystem`, so the project player, a game host and an app built on the template showed a scene's props over no ground. On a renderer, a played scene now draws each `terrain`, with the built-in terrain shading or the surface its `surface` names. A project's content holds the layered-terrain kit's provider (`awake.terrain.layers`), and a host that runs the scene's systems itself passes providers under `CoreSceneContent.TerrainSurfaces`. Closing the scene's systems detaches the terrains; a headless host draws none.
- **No hard edge across baked terrain at night.** A layered terrain with a lightmap was dark inside the shadow distance and lit like noon beyond it when the sun was below the horizon, split by a straight line at the shadow range's far end. The bake's sunlit share now fades out as the sun sets, so at night the bake sits at ambient everywhere, like the rest of the scene. Daytime lighting is unchanged.
- **A game's shrunk Android release no longer crashes at launch.** R8 renamed classes and fields that Core's native code finds by name, such as jolt-jni's contact listener and the Vulkan bindings' models and `Function4` callback, so a minified release aborted creating its physics world or Vulkan device. The Jolt backend (`:awake:backend:jolt`), the Vulkan bindings (`:awake:backend:vulkan:bindings`) and the shader compiler (`:awake:asset:shader-compiler`) now ship consumer R8 rules in their Android artifacts, so an app needs no keep rules of its own for them. A test fails when the Vulkan bindings' C++ names a class those rules don't keep, and the nightly device tests launch the engine showcase's shrunk release.
- **The Vulkan backend starts on a driver without `VK_EXT_debug_utils`.** `GraphicsDevice` created its debug messenger whatever the instance enabled, and the bindings called `vkCreateDebugUtilsMessengerEXT` through the null pointer such an instance returns, so an app on a driver with no validation layer, such as an emulator's software Vulkan, crashed creating its device. It now sets up the messenger only when the instance has the extension, and the bindings report a missing one instead of calling it.
- **A raycast makes far less garbage on desktop and Android.** `JoltPhysicsWorld.raycast` reuses its settings, collectors and filters as shape casts do, and normalizes its direction without a temporary vector: a ray now allocates about 280 B instead of 940 B.
- **Roboto's license notices name the right license.** The Roboto 3.015 font Awake uses comes from Google Fonts' `ofl/` directory and is licensed under the SIL Open Font License 1.1, as its own `name` table says, but every `Roboto-LICENSE.txt`, including the one `awake:core:text` ships beside its UI font atlases, carried the Apache License 2.0. They now carry the OFL with Roboto's copyright notice; `LICENSES/OFL-1.1.txt` and a Roboto entry in `NOTICE.md` are added, and the generated `Roboto*UiFontData` atlases, a modified version of the font, mark their data as `OFL-1.1`.
- **A scene with `texture_clips` exports as one that loads.** `texture_clips` shows its cell through a `TextureAnimation` it gives the entity, and exporting the world saved that as a `texture_animation` of its own beside the clips, a pair no scene may have. An editor that saved such a scene, or played a copy of it, got a document that failed validation. `TextureAnimationBinding` now leaves out the animation of an entity with `TextureClips`, which the clips export.

## [0.5.0] - 2026-10-09

### Added

- **Read a published project over the network, checked file by file.** `IndexedAssetSource(index, fetch, cache)` in `awake:project` reads each file a `ProjectIndex` lists through the host's `fetch`. Every file is checked against its size and SHA-256 before a parser sees it, and a `ContentCache` keyed by SHA-256 keeps it, so a file unchanged between releases downloads once. `prefetch` reads a list ahead of play and reports progress. A mismatch fails the read and names the file, and a corrupt cached copy is fetched again.
- **A project runs with no renderer, as a game server runs it.** `SceneHostServices.headless(input, physics, content)` makes a host that doesn't draw, and `LoadedProject.sceneSystems(input, renderer = null)` builds a loaded project's systems with its own physics world, content and capabilities. On a headless host the scene simulates as it does in a drawn game, and the systems that make GPU content (particle sprites, shader effects) are left out. `SceneSystemPlan.hasRenderer` tells a capability which kind of host it's planning for.
- **The project runtime is checked to need no display.** `verifyHeadlessRuntime`, part of `awakeVerify`, fails a change that puts a GPU backend or the window module on the path of `awake:project` or `awake:project:runtime`, naming the chain, so a game server keeps playing projects with no renderer.
- **A manifest names where a plugin's runtime code is published.** `AwakeProjectPluginReference` gains `artifact` (`group`, `name`, `version`) and `capabilityClass`, the `SceneCapability` object's fully qualified name, so an export or an editor can resolve what a project's plugins need. The validator checks both, and a required plugin with no linked capability is still refused, now naming its capability class and artifact too.
- **One process can load projects whose components share a name.** `SceneComponentRegistry.scoped()` holds only what is registered on it, keeps its serializers out of the global set and decodes with its own `sceneJson()`. `loadProject(files, registry, capabilities)` loads a project into such a registry, decodes its scene and prefabs with it and plays it with it, and `registerProjectComponents` prepares one for a host that decodes scenes itself. Global registration is unchanged for a game that loads one project.
- **A project scene plays streamed terrain with no Kotlin.** Core's streamed-terrain capability registers `paged_terrain`; `loadProject` reads its page index and shared images, and the scene streams cells around the primary camera, draws them as one clipmap (with terrain layers when the index names a palette), and keeps collision cells near the camera through the scene's physics step when the component is a `collider`.
- **Characters the player doesn't drive.** `movement_control` takes `driver`: `Player` (the default) follows the keys and touch controls relative to the camera, while `Agent` leaves the intent to code, such as AI, a network or a script, as a world-space direction. `PlayerInputSystem` and the touch controls now drive only player characters, and `CharacterControllerSystem` and `MatrixRelativeMovementSystem` move an agent in world space, so a monster or a remote player walks with the same walls, steps and slopes as the player.
- **AI agents walk through the character controller.** A `patrol`, `chase` or `flee` agent with a `movement_control` whose `driver` is `Agent` is steered through it at the behaviour's speed (`MovementAgentPlacement`, `MovementControl.moveSpeed`), so with a `character_controller` walls stop it and it climbs slopes and steps instead of walking through them. Each frame clears those intents first (`AgentIntentResetSystem`), so an agent that arrived or lost its target stops. `AgentPlacement.steer` passes the behaviour's velocity to a placement. A behaviour agent with a `character_controller` and no `Agent` control is refused at load, where the controller would have held it in place.
- **A host can hand Core's capabilities the content it already holds.** `CoreSceneContent` names the keys Core's capabilities read from `SceneHostServices.content`: `CollisionMeshes`, `ParticleSprites` and `ShaderEffects`. An editor that loads that content while a scene is edited builds it with `SceneContent.build` and runs `sceneSystemsFor`, instead of keeping its own list of Core's systems. `loadSceneContent` and `runProject` fill the same keys and behave as before. A paged terrain's content still comes only from `loadSceneContent` (#553).
- **Spawn into a running scene.** `SceneAppLifecycleRuntime.spawn(node)` puts a scene node and its children into the running world, as loading the scene does: components attached, and models given renderers through the scene's asset library. `SpawnedNode.despawn()` takes it out and releases what its renderers held. In a played project, `spawn(project, node)` also starts its skinned glTF models animating. Use it for a player joining over the network, a monster a server spawns, a drop or a projectile.
- **Teleport a character.** `CharacterController.teleport(transform, position)` puts a character at a position at once, without sweeping through what lies between, and stops its fall. Use it for a respawn or a portal; movement still goes through `movement_control`.
- **A capability's systems can spawn.** `SceneHostServices.spawn(node)` puts a node into the running scene from inside a capability's system, for whatever joins the scene while it plays (a player over the network, a monster a server spawns). `runProject` gives its systems a spawner that also starts skinned models. A host that builds its own services adds one with `withSpawner`, and `canSpawn` says whether it did.
- **A fallback Maven repository for releases Maven Central refuses.** Each Core release attaches its signed files to its GitHub Release, and the newest two are served from `https://awakekt.github.io/awake/` under the same coordinates, so a build adds one repository line after `mavenCentral()` to resolve a release Central refused under its publishing limits.

### Changed

- **`compose:ui` and `compose:foundation` document their whole public API.** Every public class, function and property has KDoc, and Detekt now enforces it for both modules, as it already did for `compose:runtime`.
- **Maven Central releases are smaller.** Each published module now ships an empty placeholder javadoc jar instead of the Dokka HTML jar (about 1.1 MB per target, uploaded once per KMP target), and the checksum files of `.asc` signatures and the SHA-256 and SHA-512 checksums, which Central does not require, are no longer uploaded (publish plugin 0.37.0). IDEs still read KDoc from the sources jar, and `./gradlew developerDocs` still builds the Dokka API reference.
- **A Core release reaches Maven Central as one deployment.** The modules and the Gradle plugins are staged together and uploaded once, so a release publishes whole or not at all, where two uploads could leave modules on Central without the plugins that build against them.

### Removed

- **Paged terrain uploads moved to the shader pack.** `PagedTerrain` no longer uploads to the GPU: its upload journal and binding numbers (`uploadJournal()`, `initialTextures()`, `isUploaded()`, `updates()`, `bindings`, and `TERRAIN_HEIGHT_PAGES_BINDING` / `TERRAIN_PAGE_TABLE_BINDING` in `com.awakekt.awake.terrain`) are removed, and it is no longer a `ContentTextureUpdates`. Use `PagedTerrainUploads(terrain)` and the same-named constants in `awake:asset:shader-pack`, which `pagedTerrainContentFeature` already uses; `PagedTerrainUploads` also refuses a surface on a reserved binding. `PagedTerrain` now exposes `residentPages`, `resident`, `residencyRevision`, `fallbackRevision`, `surfaceFallbackRevision` and `surfaceFallbacks` for uploaders, and its residency, edit and save API is otherwise unchanged.

### Fixed

- **A game server plays a scene with streamed terrain.** On a headless host, `paged_terrain` streams its cells and keeps their collision without asking for a renderer, which a headless host refuses.

## [0.4.0] - 2026-10-09

### Added

- Add an integrated 2D runtime showcase with editable chunked tiles, animated sprites, orthographic viewport scaling and planar Jolt physics, verified through the sample app on Vulkan and WebGPU.
- Orthographic cameras support virtual viewport scaling (`fit`, `extend`, `fill`, `stretch`, `screen`). Shared allocation-free viewport math resolves world extents and pixel bounds, scene rendering and picking use the same view, and captures accept their own pixel dimensions. Camera export preserves authored dimensions across resizing; the 2D sprite showcase cycles strategies with V.
- Added streamed terrain with one clipmap over bounded height, control and lighting pages, a shared palette and coarse fallback, frame-safe Vulkan/WebGPU region uploads, per-cell scene collision, and revision-aware page editing and saving.
- Add finite orthogonal tilemap layers with atlas mapping, editable cells, cached chunk meshes, camera-frustum culling, and scene document binding on Vulkan and WebGPU.
- WebGPU shader hot reload support via `ShaderReplacement` capability, allowing runtime WGSL pipeline swapping and dependent bind group cache invalidation.
- Runtime shader hot reload for UI pipelines (`UiQuad`, `UiGlyph`, `UiTexture`, `UiRoundedQuad`, `UiTargetComposite`) via `ShaderReplacement` across Vulkan and WebGPU backends.
- Support per-instance RGBA tinting of instanced skinned draws on Vulkan and WebGPU, defaulting omitted tints to white while preserving the joint-palette layout used by depth and shadow passes.
- Report GPU frame time on WebGPU adapters supporting timestamp queries and include offscreen scene and UI rendering in frame timing on Vulkan and WebGPU without blocking for readback.
- Headless WebGPU test harness `HeadlessWebGpuEngine` and `webGpuHeadlessPlan` in `:awake:backend:webgpu` to boot a production `RenderPlan` dynamically over wgpu-native GLFW surfaces, verified with a lit shadowed cube frame test against committed pixel baselines and negative control.
- Sprite manifest clips can wrap across atlas rows in reading order, starting partway along their declared first row. Long animations no longer require a single wide texture; grid alignment, uniform cell size, bounds, contiguous playback order and fixed timing remain validated.
- Verify the showcase's production WebGPU render plan with an offscreen pixel comparison covering lit geometry, scene depth, and depth fog in CI.

### Fixed

- Preserve virtual viewport scaling in scene framebuffer captures by passing the capture's pixel dimensions to the scene planner.
- Validate WebGPU shader reloads through the GPU driver on browsers and desktop, preserve running pipelines after rejected replacements, and release abandoned prepared candidates.
- Preserve running shader identity across consecutive UI reloads on Vulkan and WebGPU and validate WebGPU UI pipeline candidates before swapping them in.
- Release Vulkan camera-depth resources and placeholder descriptor layouts during engine teardown.
- Make the template consumer check work on runners without ripgrep and preserve an existing snapshot repository declaration.

## [0.3.0] - 2026-10-07

### Added

- Added AndroidAudioPlayer backed by AudioTrack for low-latency PCM audio, volume mixing, and stereo panning on Android (#155).
- Add backend-neutral `DegreesOfFreedom.PLANE_2D` body creation and scene `physics_body.degreesOfFreedom`, enforced by Jolt on Desktop, Android, iOS and Web, with X/Y translation and Z rotation for 2D simulations.
- Add scene-authored `sprite_clips` with named runs, simulation-time playback, switching, pause and one-shot completion; share the extracted `core:animation` frame clock with existing textured-mesh clips and animate the firefly showcase through scene data.
- Render scene sprites as unlit transparent atlas quads on Vulkan and WebGPU, with nearest filtering, cell sizing, flips, tint and paint order; migrate the generated sprite showcase to the sprite component.
- Import sprite-gen regular-grid manifests as named sprite clips, bake metadata into scene documents, and drive the 2D showcase idle animation from its real manifest.
- **Copy, cut, paste and select-all in iOS text fields.** The iOS view answers UIKit's standard edit actions, so the edit menu and a hardware keyboard's Cmd+C/X/V/A work in a focused Awake text field: copy and cut put the field's answer on `UIPasteboard`, paste types the pasteboard's text, and a password field still gives nothing (#252).
- **Export a standard JSON Schema (Draft 2020-12) for scene documents and validate example scenes against it.** Generates a deterministic JSON Schema from `SceneComponentCatalog` with sorted keys and definitions, canonical snake_case component discriminators, and validation for data types, numeric ranges, and open `custom` extensions (#445).
- Annotate controls, character, and AI behavior components (`camera_rig`, `movement_control`, `character_controller`, `navigation`, `patrol`, `chase`, `flee`) with `@PropertyRange` and record numeric property decisions (#446).
- Scene document `day_cycle` now declares property ranges for `dayLengthSeconds`, `time` and `noonElevationDegrees`, and the remaining environment and lighting components (`ambient_light`, `fog`, `skybox`, `light`) record which numeric fields are deliberately unconstrained (#446).
- Scene document components `canvas_element` (`width`, `height`, `fontSize`, `value`), `pbr_material` (`metallic`, `roughness`, `alphaCutoff`), and `physics_body` (`layer`) declare property ranges matching their validation rules, and unconstrained coordinates and factors in `camera`, `canvas_element`, and `pbr_material` are recorded in the numeric decisions ledger (#446).
- Added `@PropertyRange` bounds and decisions for Batch 5 scene components (`locomotion_animation`, `keyframe_animation`, `texture_animation`, `texture_clips`, and `terrain`).
- Added property range annotations for particle emitter scene component, completing property range coverage across all registered scene components (#446).
- **Verification of scene components reference documentation.** `SceneComponentReferenceDocsTest` now validates field types, defaults, and numeric range constraints against `SceneComponentCatalog.schemas()`, ensuring reference documentation does not drift from component schemas (#447).
- Map `PropertySchema` onto `InspectorFieldScope` via `SchemaInspectorRenderer` and extend `InspectorFieldScope` with integer, color, readOnly, section, nullable, and list field kinds (#448).
- **Shaders as project data: `awake:asset:shader-document`.** A shader document is a JSON file a published project ships: a restricted subset of ASL with a surface (`background`, `overlay` or a displaceable `plane`), named parameters and textures, expressions over engine inputs (`time`, `uv`, `viewDirection` and others), and `let`/`var`/`if`/`for`/`discard_if` statements. `ShaderDocuments.compile` checks it against `ShaderDocumentLimits` before anything compiles (size, nesting, cost, loop bounds, texture samples), types it by WGSL's rules, folds every constant so none can fail in the shader compiler, and replaces every name with a generated one; each issue names its path and what is wrong. A `CompiledShaderDocument` emits WGSL for both backends and builds a `ContentFeatureSource` fed by `ShaderEffectInputs` (clock, model matrix, parameters). Every document that passes is valid WGSL: naga validates hand-written and 1,200 seeded random documents in both clip spaces. Scene binding follows in `awake:scene:shader` (#476, part of #456).
- **A project's shader documents draw in its scenes: the `shader_effect` component.** A node names a `*.shader.json` document the project ships, with parameter values and an image for each texture, and the generic player draws it as a sky, an overlay or a plane placed by the node, on Vulkan and WebGPU alike, with no Kotlin in the project. The new `awake:scene:shader` module holds the component, `loadShaderEffects`, which compiles each document once and never throws, and `ShaderEffectSystem`, which advances each effect's clock, follows its node, updates parameters in place and re-attaches when a document changes. `loadProject` loads them, `sceneSystemsFor` runs them, and `SceneHostServices` takes the loaded documents as `shaderEffects`. A document or effect that does not check out is logged with its node and parameter, and only that effect is not drawn (#477).
- **Scene capabilities: a game's own components and systems in a played project.** A `SceneCapability` names the scene components it adds, reads what its systems need from the project's files into `SceneContent`, and adds the systems a scene that uses it runs. Pass capabilities to `loadProject`, `loadSceneContent` and `sceneSystemsFor`; they run after Core's, which use the same contract. A plugin the manifest marks `required` must have a capability with its id, and a scene component no capability registers refuses the load, naming the component (#508).
- Add an animated transparent sprite atlas, generated with sprite-gen, to the orthographic 2D engine showcase.
- Add an original RPG ranger and thorn beast sprite sample with transparent twelve-frame breathing idle loops, manifest import, and a dedicated engine showcase entry.
- **A `sprite` scene component, in a new module `awake:scene:scene2d`.** The first 2D scene component: one cell of a frame sheet (`texture`, `columns`, `rows`, `frame`), sized by `pixelsPerUnit`, with `flipX`, `flipY`, a `tint` and a `sortOrder`. Its schema validates the sheet and the frame, `SpriteBinding` attaches a live `Sprite` a game can change and exports it back, and `DefaultSceneComponentResolvers` registers it. Nothing draws it yet: the sprite draw feature, unlit and nearest-pixel rendering follow in later PRs (#158).

### Changed

- **The browser canvas host moved to `awake:engine:window`.** Its wasmJs target now owns the canvas frame loop (`runBrowserCanvas`), canvas sizing, pointer, touch, wheel and keyboard input, and the hidden-`<input>` text bridge (`DomTextInputBridge`). `awake:backend:webgpu` keeps only the WebGPU device and surface, and `launchWebGpuGame` keeps its signature. `bindWindowPointerInput`, `bindWindowKeyboardInput`, `bindWindowTextInput` and `DefaultDomGameplayKeys` moved from `com.awakekt.awake.webgpu.application` to `com.awakekt.awake.engine.window`; update the imports. There are no deprecated aliases (#320).
- `SceneHostServices` carries what a scene's capabilities loaded as one `content` map, read with `loadSceneContent`, instead of the `particleSprites`, `collisionMeshes` and `shaderEffects` fields. `loadProject` takes `capabilities` before `physicsWorld`, so a trailing lambda still names the physics world. `ParticleContentSystem` and `ShaderEffectSystem` are `AutoCloseable`, and their `release()` is now `close()` (#508).
- Make engine-showcase navigation responsive with a scrollable sidebar and mobile hamburger sheet, and move debug controls and detailed stats into a dismissible panel with touch-accessible phase timings.
- **`awake:ai:behavior` no longer depends on the scene layer; its scene components moved to `awake:scene:ai` and `awake:scene:navigation`.** The `patrol`, `chase` and `flee` schemas and bindings (`ScenePatrol`, `SceneChase`, `SceneFlee`, `PatrolBinding`, `ChaseBinding`, `FleeBinding`), `AiBehaviorBindings` and `registerAiBehaviors()` are now in `awake:scene:ai`, package `com.awakekt.awake.scene.ai` (was `com.awakekt.awake.ai.behavior`); `SceneNavigation`, `NavigationBinding` and `NavigationGrid` are in `awake:scene:navigation`, package `com.awakekt.awake.scene.navigation`. `PatrolAiSystem`, `ChaseAiSystem` and `FleeAiSystem` now take an `AgentPlacement`, which reads an entity's position and moves it: in a scene pass `TransformAgentPlacement`, from `awake:scene:ai`. Scene documents do not change. A project that registers or runs these behaviours in a scene adds `awake:scene:ai`. There are no deprecated aliases (#386).
- **The terrain surface types moved to a new scene-free module, `awake:terrain`.** `TerrainSurfaceReference`, `TerrainSurfaceProvider` and `TerrainSurface` were in `awake:scene:scene3d`, so a surface provider had to depend on the scene layer. Their package is now `com.awakekt.awake.terrain` (was `com.awakekt.awake.scene.rendering.terrain`); update the imports. `scene:scene3d` depends on `awake:terrain` with `api`, so code that already depends on it needs no new dependency. `awake:kit:terrain-layers` now depends on `awake:terrain` and `asset:shader-pack` instead of `scene:scene3d`; code that used `scene:scene3d` through it must declare that dependency itself. There are no deprecated aliases (#386).
- **The world-cell types moved to a new scene-free module, `awake:world`.** `WorldCellCoord`, `WorldPartitionConfig`, `AsyncWorldCellStreamListener`, `CellContent` and `CompositeCellStreamListener` were in `awake:scene:world`, so `awake:navigation` depended on the scene layer to stream its grid by cell. Their package is now `com.awakekt.awake.world` (was `com.awakekt.awake.scene.world`); update the imports. `scene:world` depends on `awake:world` with `api`, so code that already depends on it needs no new dependency. `awake:navigation` now depends on `awake:world` and `awake:ecs` instead of `scene:scene-core` and `scene:world`; code that used those through it must declare them itself. There are no deprecated aliases (#386).
- `awake:project:runtime` uses the engine's own vocabulary instead of "play" names: `PlayableProject` is `LoadedProject`, `loadPlayableProject` is `loadProject`, `playProject` is `runProject`, `PlaySystems` is `SceneSystemSet`, `playSystemsFor` is `sceneSystemsFor` and `PlayServices` is `SceneHostServices`. There are no deprecated aliases; callers rename.

### Removed

- **The no-argument `ParticleSystem()` constructor is removed.** It was deprecated in 0.2.0 because it silently turned off entity following and rotation orientation. Pass a placement: `TransformPlacement` in a scene, or `EmitterPlacement.None` when an emitter should not follow or turn with its entity on purpose. The once-per-system warning that only this constructor could trigger is gone with it, and `awake:particles` no longer depends on `awake:core:logging` (#423).

### Fixed

- Preserve landscape swapchain dimensions during Android quarter-turn orientation changes instead of inverting already-rotated surface extents, preventing horizontally stretched rendering.
- Shader DSL (ASL) WGSL output keeps the parentheses an expression needs: a negated sum or a double negation, `&&` mixed with `||`, a comparison of comparisons, and an integer quotient on the right of `*` no longer emit WGSL that computes something else or that naga or Tint refuses.
- Shader DSL (ASL) WGSL output keeps the grouping of a right-nested operand: `viewProjection * (model * position)` is two matrix-vector products instead of a matrix-matrix product first, which the skinned, instanced, shadow and terrain vertex transforms now save per vertex, and `a + (b + c)` no longer rounds as `a + b + c`.
- A played project no longer leaks its physics world: `LoadedProject` is `AutoCloseable` and `close()` destroys the world `loadProject` made, which now reads every file before making it, so a cancelled load leaves none behind. `SceneSystemSet.interpolate(world, alpha)` lets a host that plays a scene in its own world smooth physics bodies between fixed steps, as `runProject` does.
- **Property schemas now agree with the decoder about nullable and omitted properties.** `propertySchemaOf` reads a nullable property with no default as not required when the `Json` has `explicitNulls = false`, because that decoder reads a missing one as null (`SchemaOptions` gains `nullablesAreOptional`, which `propertySchemaOf` sets from the `Json`). `deriveDefaults` decodes with the `Json` as given and reports a default the encoder never writes (`@EncodeDefault(NEVER)`) as unknown, not as `null` (#465).
- Support finger drags in Compose vertical and horizontal scroll containers; swiping a child button cancels its click and pressed state while small finger movement still permits taps.
- Play spatial audio in the web engine showcase using Web Audio, with user-gesture unlocking, live volume/mute and stereo panning, looping, and source cleanup.

## [0.2.0] - 2026-10-06

### Added

- **Convex hull colliders in scene documents.** Scene documents support a `convex_hull` collision shape on dynamic, kinematic or static `physics_body` components (`SceneConvexHullShape`), shrink-wrapping model vertices loaded through `loadCollisionMeshes` and `MeshColliderSystem`. Round-trip export preserves the model and primitive reference, and sensors are supported. (#428)
- Added `sprites-2d` showcase demonstrating orthographic camera projection, layer depth sorting, and animated sprite quads.
- Added `spatial-audio` showcase demonstrating 3D positional audio emitters with distance attenuation, stereo panning, and volume controls.
- Added interactive prop spawner (boxes, spheres, wedges) and orbit/free-fly camera mode controls to heightfield terrain showcase.
- **The first components describe their limits to editors, and every numeric property needs a decision.** `spin_control`, `camera`, `light` and `tone_mapping` now carry `@PropertyRange` on each limit their `validate()` already enforces (`camera.near` above 0, `camera.fovYDegrees` between 0 and 180, `light.shadowDistance` above 0, `light.ambient` above 0 and at most 1, `tone_mapping.exposure` above 0, and so on), and `spin_control.radians` is hidden from editors. A test now finds every numeric property of every registered component and fails for one with no `@PropertyRange`, no `free` line with a reason, and no `undecided` line; the `undecided` ledger only shrinks as each module is done. A second test loads a value just outside every annotated range and requires `validate()` to report it, so an annotation cannot claim more than the component enforces. No document that loaded before is rejected now (#446).
- **A property schema read from serialization descriptors.** New module `awake:core:schema`: `propertySchemaOf` turns a `@Serializable` type into a `PropertySchema` with each property's kind, nullability, required flag, default, numeric constraint and editor hints, and `deriveDefaults` learns the defaults a descriptor does not carry. Eight `@SerialInfo` annotations (`@PropertyRange`, `@PropertySlider`, `@PropertyStep`, `@PropertyUnit`, `@PropertyHint`, `@PropertyHidden`, `@PropertyReadOnly`, `@AssetReference`) say what an editor shows and what a validator enforces. It depends on no scene module, so inspectors, reference docs and a JSON Schema exporter can read any component the same way. No component is annotated yet (#443).
- **Day and night from scene data.** `day_cycle` (`SceneDayCycle`, `DayCycleSystem`) on the directional light's node moves the sun along a tilted path over `dayLengthSeconds`, rising at `sunriseAzimuthDegrees` and peaking at `noonElevationDegrees`, and blends the sky's horizon and zenith colours, the light's colour, intensity and ambient share, and the fog colour between authored stops, wrapping across midnight. Below the horizon the light keeps pointing at the sun, so the sky's moon stays opposite it and night comes from the stops. The shared play systems run it, saving writes the authored start time back, and `Light.intensity` and `Light.ambient` are now mutable. (#410)
- **A catalog of the scene components the engine can load.** `SceneComponentCatalog` lists every registered component id with its descriptor and property schema (defaults learned, required properties flagged, vectors and colours recognised), and describes a `SceneNode`, including its transform, which no component owns. An editor, a reference page or a schema exporter reads it instead of hard-coding components. `SceneSerializers.registeredSerializers()` exposes the registry as a snapshot (#444).
- **Static mesh colliders in scene documents.** A static `physics_body` can take a `mesh` shape (`SceneMeshShape`) naming a `.glb` or `.gltf` model, so props collide with their real triangles. `MeshColliderSystem` builds the body with the node's scale baked in, `loadCollisionMeshes` reads each model once and names the model and node when one is missing, `loadPlayableProject` and `playSystemsFor` wire both, and saving writes the model path back. (#422)
- Add falling wedge convex hull prop (`falling-wedge`) to heightfield terrain showcase with dynamic convex hull physics body.
- Document all public declarations and enforce strict Detekt KDoc rules for `:awake:backend:jolt`.
- Document all public declarations and enforce strict Detekt KDoc rules for `:awake:engine:window`.
- Document all public declarations and enforce strict Detekt KDoc rules for `:awake:kit:terrain-layers`.
- Document all public declarations and enforce strict Detekt KDoc rules for `:awake:ui:node-graph-canvas`.
- Document all public declarations and enforce strict Detekt KDoc rules for `:awake:engine:render:parity` and `:awake:engine:render:testing`.
- Document all public declarations and enforce strict Detekt KDoc rules for `:awake:engine:render:passes`.

### Deprecated

- **Deprecated no-argument `ParticleSystem()`.** `ParticleSystem()` silently disabled entity following and rotation orientation by defaulting to an unplaced configuration. Pass a concrete placement (such as `TransformPlacement` in a scene) or explicitly pass `EmitterPlacement.None` when disabling following and rotation on purpose (#423).

### Fixed

- Reuse typed ECS family views within each world generation so warmed `queryEach` and `firstOrNull` calls allocate no family wrappers or lookup keys.
- **Scene validation rejects nodes with both `texture_clips` and `texture_animation`.** Nodes declaring both components are now flagged with an explicit validation error naming the node, preventing silent conflicting animation states.
- **Remediated Kotlin 2.5 copy visibility, compiler warnings, and test deprecations.** Added `@ConsistentCopyVisibility` to `BindingLayout` to prevent KT-11914 error in future Kotlin compilers, removed redundant non-null assertions on `u.cameraPosition` in `AslShadowShaders`, cleaned up channel draining in `TerrainContentSystem`, and migrated test suites away from deprecated authoring DSL methods. (#427)
- Replace deprecated `content { ... }` and `appDefinition` bootstrap DSL calls with `ui { ... }` and `app { ... }` in `samples/engine-showcase`. (#450)
- **Terrain no longer disappears when the camera is off the map.** Clipmap rings centred on the camera with no bound, so zooming far out or moving past the map's edge left the far side undrawn while props stayed in the air. `TerrainClipmapTracker` now takes the heightmap it covers and clamps its centre into the map's rectangle; `terrainContentFeature` and `TerrainClipmapSystem` pass theirs. (#404)
- **The bounds debug overlay no longer crashes a big scene.** It outlined every mesh, instance and occluder box, so about 5,500 boxes or more passed the renderer's 65,536-line ceiling and threw. `debugVisualizationLines` now stays under `DEBUG_VISUALIZATION_MAX_LINES`, leaving room for an editor's own lines, and fills the budget with the boxes nearest the camera after the frustum, light, grid and axis overlays. (#418)
- **A `ParticleSystem` built with no placement says so.** `ParticleSystem()` places nothing, so an emitter that follows an entity or inherits its orientation does neither, which used to be silent. It now logs one warning per system when such an emitter appears, naming what to pass (`TransformPlacement` in a scene, or `EmitterPlacement.None` to say it is intended, which stays quiet). `awake:particles` takes a small `core:logging` dependency for it. Behaviour is otherwise unchanged; the no-argument form is deprecated in a later minor (#423).
- **Terrain no longer shadows its own cliffs in patches.** Terrain cast its shadows from a stand-in heightmap mesh, which at a sharp height change stood above the clipmap rings actually drawn and shaded the lit rim and face in polygon patches. The rings now cast themselves: a content feature can declare a `depth` pipeline built over its own bind group, and the terrain's depth shader places and discards its rings exactly as the drawn surface does, on Vulkan and WebGPU. (#402)
- Stabilize node-graph allocation checks with a fixed warm-up and multiple measurement windows, and expose the measured values and ceilings in CI logs and test reports.
- Prevent excluded release families from registering empty Maven Central bundles during snapshot publishing, and wait for tagged Vulkan artifacts before building consumers against Central.
- Wait for submitted Vulkan work before destroying mesh buffers, material uniform buffers and descriptor pools, and renderer-owned resources during scene replacement and shutdown.
- Run WebGPU and Vulkan/WebGPU pixel parity suites in CI, retain their diagnostics, bound WebGPU buffer uploads to the source length, release cached neutral textures once to prevent native heap corruption, and support the Vulkan SDK in desktop accessor builds.

## [0.1.0] - 2026-10-05

### Added

- **Every particle option a scene file can write is in `particle_emitter`.** It gains `acceleration`, `inheritOrientation`, `alphaCurve`, `burstCycle`, `turbulence`, `turbulenceFrequency`, `convergeToOrigin`, `stretchWithVelocity`, `stretchFactor`, `burstCount` and `ground` (`groundY`, `restitution`, `friction` and box `colliders`), each with the name and default of the runtime option it sets, so sparks that arc, bounce, streak and pulse no longer need Kotlin. Only what is code stays code: `onParticleDeath`, `groundHeightProvider`, `dynamicSpawnRate` and sub-emitters. A `burstCount` emitter removes its node once every particle has died, so a one-shot emitter belongs on a node of its own. `ParticleExposureTest` fails when a runtime particle option is neither mapped from a scene field nor listed as code only, so the two cannot drift apart unnoticed again.
- **Particles can spin.** `ParticleVisual.spin` takes a `ParticleSpin(minDegreesPerSecond, maxDegreesPerSecond, randomStartAngle)`: each particle draws its own turn rate between the two when it spawns and turns its quad in its own plane at it, for camera-facing and flat emitters alike, and a particle that has landed stops turning. `particle_emitter` gains the matching `spin` object. A stretched particle points along its motion, so spin does not apply to it. `setParticleInstance` takes a `rotation`, carried in column 2 of the instance matrix, which the particle shader and the particle shadow caster read: no new vertex attribute, so no backend code changed. Seen on WebGPU in `WebGpuParticleSpinTest`; the same scene is a render parity test and a render-evidence picture for Vulkan.
- **A frame sheet can start at any cell.** `TextureAnimation` and `texture_animation` gain `firstFrame`, so one sheet can hold several runs, an idle and a walk, and each entity plays its own; `frameCount` counts from it, and `0` plays to the sheet's last cell. With `framesPerSecond` 0 an entity shows that one cell. The cell travels in `textureScroll.w`, which nothing used, so the uniform block keeps its size, and a sheet that does not set it plays exactly as before.
- **Sprite sheets with named clips.** A new `texture_clips` component cuts a frame sheet into named clips (`firstFrame`, `frameCount`, `framesPerSecond`, `loop`) and plays one on the entity. `TextureClipSystem` steps it on the scene's clock and shows the cell through the entity's `TextureAnimation`, so a game switches clip with `TextureClips.play("walk")`, reads `isFinished` for a one-shot clip, and a paused game holds still. Asking for the clip that is already playing does nothing, so it can run every frame. `playProject` and `playSystemsFor` add the system for scenes that have the component. It builds on the new `firstFrame` of `texture_animation`; sprites, tilemaps and a 2D scene module are still to come under #158.
- 100% KDoc coverage and Detekt enforcement for `:awake:asset:gltf`.
- Document all public declarations and enforce strict Detekt KDoc rules for `:awake:asset:shader-dsl`.
- Document all public declarations and enforce strict Detekt KDoc rules for `:awake:asset:shader-pack`.
- Document all public declarations and enforce strict Detekt KDoc rules for `:awake:asset:shaders`.
- Document all public declarations and enforce strict Detekt KDoc rules for `:awake:asset:terrain`.
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for core foundations modules (`:awake:core:geometry`, `:awake:core:io`, `:awake:core:text`).
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for 2D graphics primitive and vector tessellation engine (`:awake:core:graphics2d`).
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for core memory pooling and mathematics modules (`:awake:core:pool`, `:awake:core:math`).
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for core ECS architecture library (`:awake:ecs`).
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for editor and tooling contracts (`:awake:editor:contract`, `:awake:node-graph`, `:awake:blueprint`).
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for the navigation subsystem (`:awake:navigation`).
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for network and platform cluster (`:awake:net:api`, `:awake:engine:platform`, `:awake:project:runtime`).
- Document all public declarations and enforce strict Detekt KDoc rules for `:awake:engine:render:contract`.
- Document all public declarations and enforce strict Detekt KDoc rules for `:awake:engine:render:passes2d`.
- 100% KDoc coverage and Detekt enforcement for `:awake:scene:scene3d`.
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for scene authoring DSL (`:awake:scene:authoring`).
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for scene document library (`:awake:scene:document`).
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for glTF scene bridge library (`:awake:scene:gltf`).
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for the scene management cluster (`:awake:scene:controls`, `:awake:scene:world`, `:awake:scene:worldstream`).
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for scene runtime (`:awake:scene:runtime`).

### Changed

- **`ParticleEmitterSource.authored` is now `settings`.** It names what the property holds, as `KeyframeAnimation.tracks` and `LocomotionAnimation.clips` do, not where it came from. Source-breaking for code that read it. `loadParticleSprites` and `ParticleContentSystem` moved to their own files, so a Java caller sees `loadParticleSprites` on `ParticleSpritesKt` rather than `SceneParticleEmitterKt`; Kotlin callers are unaffected.
- **Particles are their own module, `awake:particles`, with no scene dependency.** The emitters, the simulation (`ParticleSystem`), the burst pool and the draw builder (`ParticleDrawBuilder`) moved out of `awake:scene:scene3d`, so an app with its own world and camera can use them. `awake:scene:particles` is the scene wrapper: the `particle_emitter` component, its binding, `loadParticleSprites`, `ParticleContentSystem` and the new `TransformPlacement`. `ParticleSystem` no longer reads `Transform`; it asks an `EmitterPlacement` where an entity is, so a scene passes `ParticleSystem(TransformPlacement)` and an app with no scene passes its own or nothing. `ParticleEmitter` gains `spawn`, `liveParticleCount` and `forEachLiveParticle`. Source-breaking: the packages are now `com.awakekt.awake.particles` (library) and `com.awakekt.awake.scene.particles` (scene wrapper), where they were `com.awakekt.awake.scene.rendering.particles`, and a project that used particles through `scene:scene3d` alone must now add `awake:scene:particles` (`scene:runtime` already includes it). Scene files are unchanged.

## [0.1.0-rc.14] - 2026-10-05

### Added

- **A frame-scoped `ScratchPool`, and the ECS and bounds APIs that let a hot loop avoid allocating.** `ScratchPool<T>` in the new `awake:core:pool` module hands out reusable instances in order and rewinds them in one `reset()`. `World.componentStore(typeId)` hoists a store lookup out of a per-entity loop, and `World.generation` changes on every `clear()` so anything that caches type ids, families or stores can tell they are stale. `MeshBounds.writeWorldBounds` writes world-space bounds into a caller-provided `Aabb`. (#318)
- **Layered terrain blends up to eight layers where more than four meet.** `TerrainControlMap.reduce` keeps eight layers per texel when a texel, or the 2 x 2 texels one pixel blends, holds more than four, and the surface then uses an eight-slot shader. Where five or more ground textures met, the weakest used to vanish at a pixel boundary and leave a hard seam. Terrains with four or fewer keep the four-slot shader and its cost. `.terrainctl` files are now version 2 with a slots byte; version 1 files still load. (#273)

### Changed

- **Scene extraction stops allocating per visible mesh.** For entities with a `MeshRenderer` (with or without `MeshBounds`, billboarded, with a `PbrMaterial` and a `TextureAnimation`), `LodGroup` entities, modular characters and skinned entities (with or without a material), `SceneDrawCollector` allocates no draw command, bounding box, matrix or vector in steady state; a probe over 1,000 entities of each kind measures zero bytes per frame. Draw requests are now pooled and rewritten by the next collection, so a `GpuDrawRequest` (and its `model` and `worldBounds`) is valid only until then. `GpuDrawRequest`'s properties become mutable. Modular characters walk their slots by index, and `UniformLayout.offsetOf` no longer builds a list per call, which a tinted skinned draw paid for every frame. An `InstancedSkinnedMeshRenderer` splits its transforms and joint palettes out once at construction, so mutating its `instances` list in place is no longer picked up. (#318)

## [0.1.0-rc.13] - 2026-10-04

### Added

- **A pass can render its own draws alone, on a backdrop of its choosing.** `EnvironmentUniforms` (and `GpuEnvironmentState`) gain `contentFeatures`, which, when false, keeps the plan's content features (sky, terrain, fog and the like) out of the pass, and `clearColor`, the colour it clears to instead of the renderer's. An asset preview rendered with the running scene's renderer no longer shows that scene's terrain behind the asset.
- **Particle emitters take acceleration, an alpha curve, burst cycles and the node's orientation.** `ParticleMotion.acceleration` adds a constant world-space acceleration (gravity) to every live particle, `ParticleMotion.inheritOrientation` turns the spawn ring and launch velocity by the emitter entity's rotation, `ParticleVisual.alphaCurve` fades a particle in, holds, then fades it out instead of the plain linear fade, and `ParticleLifecycle.burstCycle` spawns bursts during part of a looping cycle and pauses for the rest. All default to the previous behaviour. Per-particle rotation is not included yet.
- **An editor can play a scene with the same systems as `playProject`.** `playSystemsFor(scene, PlayServices(input, renderer, physics, particleSprites))` returns the systems a `SceneDocument`'s components call for (movement, physics and characters, the camera rig, spin, locomotion, keyframes, particles, canvas actions and skinned animation) as plain `System`s: `fixed` ones for each fixed step, then `frame` ones for each rendered frame, in the order `playProject` runs them, with `close()` to release what they created. `playProject` is built on the same decision, so a host that plays a scene in a world of its own, with no app builder, no longer keeps a list that drifts when Core adds a component that needs a system.
- **Patrol, chase and flee run in a played project.** A scene with one of those behaviours and a new `navigation` component (`SceneNavigation`: `rows` of `.` walkable and `#` blocked cells, plus `cellSize`, `originX` and `originZ`) plays with the behaviours and the system that answers their routes already running, so `playProject` and `playSystemsFor` need no AI code in the host. The behaviours arrive with the `PathRequest` they ask through, which a host used to add to every agent by hand, and `loadPlayableProject` refuses a scene with a behaviour and no `navigation`. The grid is authored data, so a baker in Studio or a Core tool can write the same component later; `awake:project:runtime` now depends on `awake:ai:behavior`.
- **A scene camera can be orthographic, and 2D shapes can be tested for overlap.** `camera` gains `projection` (`perspective`, the default, or `orthographic`) and `orthoHalfHeight`, the half of the vertical world extent an orthographic view covers; an orthographic camera ignores `fovYDegrees` and keeps things the same size at any distance, which a 2D game draws through. `Box2`, `Circle2` and `Overlap2` in `awake:core:math` answer `overlaps` for any pair and `penetration`, the smallest move that separates two overlapping shapes, as a unit normal and a depth. The shapes are mutable and the result is written into a reusable `Overlap2`, so a frame of checks allocates nothing. Gravity and ground-snapping stay with the game.
- **One helper for the input gameplay may act on.** `SceneAppLifecycleRuntime.gameplayInput()` (in `awake:scene:authoring`) returns the running input minus whatever the UI has claimed. The camera, camera-input and player-input systems, `playProject` and the showcase now ask for it instead of each spelling out the same expression.

### Changed

- **The loading screen and site headers show the AwakeKt glyph, light or dark.** `awake-loader.js` drops the app-icon tile for the field-free glyph and follows the system light/dark setting, background and text included. The docs, awakekt.com, the samples page and the brand page inline the glyph so its gold follows their own theme toggle. Favicons, launchers and name lockups keep the icon on its dark field.
- **A WGSL shader text compiles once.** `CachingShaderCompiler` (in `awake:asset:shader-compiler`) hands back the SPIR-V of a text it has already compiled, keyed by the text itself and bounded to the 128 most recently used, so the vertex and fragment stages of one inline program, and a hot-reload preview of an unchanged file, skip naga after the first compile. `VulkanShaderResolver` uses it for `InlineText` sources and now takes the compiler as an optional constructor argument. A text naga rejects is never cached.
- **A replacement shader compiles off the render thread.** `ShaderReplacement` splits `replace` in two: `prepare(new)` compiles on a background dispatcher, one program at a time, and suspends the caller until it is done, touching no pipeline; `swapIn(old, prepared)` is the only step that must run on the render thread, between frames, and it does not compile. A live preview prepares in the background while frames keep drawing and swaps in at the start of the next one, so a large shader no longer drops a frame. `replace` keeps working and still compiles on the caller's thread. The Vulkan backend compiles replacements through a resolver of its own, so the compiled-shader cache of #216 is never shared between threads, and the showcase's shader swap now prepares in the background. New `BackgroundShaderCompile` in `awake:asset:shaders`.

### Fixed

- **The font atlas generator gives the same metrics on every platform.** `generateFontAtlas` took its ascent, descent and cell height from `java.awt.Font.getLineMetrics`, which reads `hhea` on Linux but the `OS/2` win metrics on Windows, so the same font file gave a different line height (0.950 em against 0.928 em for Roboto), cell size and glyph offsets on each. It now reads them from the font's own `hhea` table, so Windows now produces the committed metrics, and on Linux its output is byte-identical to before. Atlas pixels still depend on the `msdfgen` build; the generator's new README says which one reproduces the committed atlas.
- **One prefab can be placed more than once.** Node names had to be unique across the whole expanded document, so a second `prefab_link` to the same prefab failed to load with `duplicate node name`. Each prefab instance is now its own name scope: its nodes need unique names only within it, and a component linking a node by name, such as `camera_rig`'s target, resolves to its own instance's node first, then to the scopes around it.
- **A glTF skinned part keeps the factors its material was authored with.** A part of a skinned model drew with default factors, so its base colour, emissive and alpha mode were dropped, textured or not, while static models already kept theirs. `GltfMaterialSlot.parameters` now carries each part's factors, and `SceneAssetResolver.materialDefaults(mesh, material)` (new, defaulting to the material-only form) lets a resolver answer by mesh, so the untextured parts that all draw with `skinned-material` each get their own. Names in saved scene documents do not change. A skinned draw uses base colour and emissive; its shader does not read metallic or roughness yet, so those are kept on the part's default material for when it does. Tinting a skinned draw per instance is still open in #314.
- **The bundled UI font draws the middle dot, ellipsis, en and em dashes, multiplication sign and minus sign.** They had no glyph and drew `?`; they are now packed after the bullet in every weight, so ` · `, `…`, `–`, `—`, `×` and `−` render as written.

## [0.1.0-rc.12] - 2026-10-04

### Added

- **Multi-touch on the web and iOS, and tooltips a finger can open.** The web canvas and the iOS view report every finger, as Android already did, and the browser no longer turns a tap into a second, emulated mouse click or a pinch into page zoom. A finger no longer hovers, so a tooltip doesn't stick after a tap; holding a finger on a trigger shows its tooltip until it lifts, and that lift is not a click.
- **Editor plugins can add viewport tools, edit-time scene systems and component inspectors.** `:awake:editor:contract` adds `ViewportToolProvider`, `SceneSystemsProvider` and `ComponentInspectorProvider`, plus the host-implemented `EditHistory`, `EditCommand`, `SceneSelection` and `InspectorFieldScope`, so these hooks no longer need a host's own editor library. Decision D36.

### Changed

- **Skinned meshes, instanced meshes without shadows, and terrain now go through the same exposure and tone curve as lit meshes.** The `skinned`, `skinned_textured`, `instanced`, `terrain` and `terrain_layers` shaders used to write their colour straight out, so a `tone_mapping` exposure left them untouched and their highlights and darks didn't match the lit meshes around them. Their colour is display-referred, so it is decoded first: at the default exposure, colours below the curve's shoulder come back nearly as authored, highlights roll off the same way, and the darkest tones deepen as they do on lit meshes. The skinned, instanced and terrain uniform blocks gain an `exposure` field. `SceneDisplayTransform.displayReferred` is the helper a custom surface uses.

### Removed

- **`packedImageVector` is gone.** Icon codegen emits builder calls, so nothing produced its packed-string input, and only its own parity test called it. The unused LWJGL entries in the version catalog are gone too.

### Fixed

- **Metallic and roughness factors apply without a metallic-roughness map, and imported models draw with their own factors.** The 1x1 stand-in map a material binds when it has none held metallic at 0 and roughness at a half, so a `PbrMaterial`'s metallic did nothing and its roughness was halved. It is now neutral (one shared set in the render contract, used by both backends). Meanwhile imported glTF materials' own metallic, roughness, colours and alpha mode were never applied: `SceneAssetResolver.materialDefaults` now reports them and an entity without a `PbrMaterial` draws them (`MeshRenderer.defaultMaterial`, not saved). A textured draw with no factors at all keeps its old look (metallic 0, roughness 0.5). Models and materials that set roughness now look as rough as they say.
- **Camera-facing particles keep their shape from every angle.** Each particle's size sat where the particle shader reads its velocity stretch, so whenever world up was on screen a plain particle drew twice as tall as wide and mirrored left to right. Instance matrices are now written by one helper, `setParticleInstance`, which leaves the stretch zero unless the emitter stretches with velocity.
- **A newer push to `main` cancels the snapshot publish it supersedes**, so release tags no longer queue for hours behind snapshot runs.
- **Android no longer raises the soft keyboard with nothing to type into.** `AwakeSurfaceView` told Android it was always a text editor, so the system could open the keyboard whenever the window gained focus, such as at launch, on resume or after rotation. It now says so only while a text field holds focus.
- **Particles cast shadows on WebGPU where a render plan opts them in.** A particle draw reported no uniform buffer, so the WebGPU shadow pass skipped it; it now reports its per-draw slot, as plain instanced draws do. The headless scene renderers on both backends now build the particle shadow-depth pipeline, so the parity suite covers it.

## [0.1.0-rc.11] - 2026-10-04

### Fixed

- **A Core release no longer fails its consumer check when Vulkan has unreleased changes.** The template now builds against the last Vulkan release, which is what a consumer gets, instead of a Vulkan snapshot the Core-only publish never built.
- **An unfocused single-line text field shows the start of its value.** It kept scrolling to its caret, which sits at the end, so a field narrower than its value cut the first characters off (an inspector showed `12.75` as `2.75`). It follows the caret again once focused.

## [0.1.0-rc.10] - 2026-10-03

### Added

- **Multi-touch on Android.** Every finger reaches `Input.touches` and the UI as its own pointer, so two-finger gestures such as `Modifier.transformable`'s pinch and pan work on a phone. The first finger still drives the primary pointer for code that reads only that. Holding a finger opens a context menu, the way right-click does with a mouse, and a press that moved past touch slop is a drag, not a long press.

### Changed

- **`awake:engine:window` hosts the Android view.** The `SurfaceView`, its render thread, and its touch, key and IME input moved out of `awake:engine:platform` into the window module. Breaking: `com.awakekt.awake.engine.platform.VulkanView` is now `com.awakekt.awake.engine.window.AwakeSurfaceView`, with the same constructor; it never used Vulkan, so the name now says what it is.

### Fixed

- **Additive textured quads add nothing where their texture is black.** A transparent, additive textured mesh was lit like a surface, so a glow sprite with a black base colour still added the sun's specular reflection, and in fog the fog colour, over its whole square. An additive textured draw now shades unlit: it adds its base and emissive colour only, and fades out in fog instead of turning toward the fog colour.
- **Desktop `FileSystem.watch` honours `recursive` and reports the right kind of change.** It watched only the top directory and reported every event as a modified file. A recursive watch now covers every subdirectory, including ones created later, and creates, edits and deletes arrive as `Created`, `Modified` and `Deleted`, with directories marked as directories.
- **Alpha-masked skinned meshes cast their cut-out shadow and draw cut out.** A skinned mesh with a `Masked` material, such as hair cards, cast no shadow at all and drew its whole card, because the skinned material block never carried the alpha cutoff and no skinned depth shader could discard. The block now carries the draw's cutoff, the textured skinned shader discards below it, and `PackShaderSets.SkinnedMaskedTexturedShadowDepth` casts the cut-out shape; map `DepthRenderKey(DepthCasterKind.Skinned, AlphaMode.Masked)` to it in a `RenderPlan`.
- **Particles near the side edges of a wide view stay on screen.** Particle culling used a fixed 16:9 frustum, so in a wider viewport particles near the left and right edges vanished while still visible, and a narrow or portrait view drew particles it could not show. Particles are now culled with the aspect of the viewport or render target they are drawn into, the one the camera projection uses.
- **A scene readback shows what the window shows.** `SceneAppLifecycleRuntime.readback` and `readbackAttachment` drew a simplified copy of the scene. It had no particles, no `PbrMaterial` colours, alpha mode or texture animation, and no poses for skinned meshes. Transparent, additive and camera-facing meshes drew opaque and unturned, LOD groups and modular characters were missing, hidden meshes were drawn, and the scene's light, shadows and environment were replaced by the defaults. Captures are now planned by the scene's own `RenderSystem3D`, the same extraction the frame uses, through the new `RenderSystem3D.planCapture`. `SceneAppLifecycleRuntime.collectDrawCalls()`, which built the simplified list, is removed.
- **Curved strokes, icons and holes are drawn at their true weight and shape.** Four defects in the anti-aliased outline compounded. Shrinking a corner was capped at half its adjacent edge, which on a curve's sub-pixel segments put the fringe almost entirely outside the shape. A stroke outline closes on a point a float epsilon from its first, and that sliver edge skewed the fringe along the first edge it touched. A hole's fringe was built facing the wrong way, so every hole drew hard and too small. And the inside of every stroke turn folded back into a small loop, which hid the inner edge's fringe. Strokes from `tessellateStrokeAa`, which includes `Modifier.border`, now centre their fringe on the outline as icons already did: identical pixels on a pixel-aligned edge, but a 1 px rounded border no longer draws 35% heavier at its corners, and the anti-aliased rim reaches half the fringe past the node rather than all of it. Against Chromium rendering the same SVGs, Lucide icons went from 7-24% too much ink at 16 px and overlap (IoU) as low as 0.35 to 96-101% ink and an overlap of at least 0.97. The spinner's arc now carries exactly its geometric ink, where it drew 31% heavy. `tools/icons/compare_icon_sheets.py` measures an icon sheet against the Chromium capture.

## [0.1.0-rc.9] - 2026-10-03

### Added

- **Editor plugins can add toolbar controls, workspaces, floating cards, keybindings and entity templates through the Apache contract.** `:awake:editor:contract` gains `ToolbarProvider`, `WorkspaceProvider`, `FloatingCardProvider` (with the host's `FloatingCardDeck`), `KeybindingProvider` (`Keybinding`, `KeyChord`, `ActionId`) and `EntityTemplateProvider` (`EntityTemplate`, which configures a new entity's scene components). Each fixes its own kind, and simple providers need no codec: `NoProviderConfiguration` is the default, `PanelProvider` included. The contract now also depends on `:awake:compose:ui` and `:awake:ecs`.

## [0.1.0-rc.8] - 2026-10-03

### Added

- **Editor plugins can draw their own panels.** `PanelProvider` in `:awake:editor:contract` adds one `context(_: Composer) fun content()` to a provider whose kind is `BottomPanel`, `Sidebar` or `InspectorPanel`, so a plugin ships UI against the Apache contract alone instead of linking a particular editor's UI library. The host labels the tab with the provider's display name; `ProviderRegistry` rejects a panel provider in any other slot.
- **Particles can lie flat.** `particle_emitter` takes `facing: "Flat"` (and `ParticleVisual` takes `facing = ParticleFacing.Flat`) to lay each particle in the plane perpendicular to the emitter node's up axis instead of turning it to the camera, for ground glows, ripples and magic circles that spread on the floor. Particles keep their position, size, colour and fade, the node's rotation tilts the plane, and `Camera`, the default, leaves existing scenes unchanged. On WebGPU, particle draws that share a sprite no longer share one uniform block, so each keeps its own frame count and facing.
- **Publish a checkout to the local Maven repository under a version no release uses.** `./gradlew publishToMavenLocal -Pawake.version=0.1.0-local` (and `-p build-logic` for the Gradle plugins) gives every module and the Vulkan family that one version, so a consumer such as Awake Studio can build against unreleased Core from `mavenLocal()` without including Core's source build. `awake.version` cannot be combined with `awake.publishFamily`.
- **Prefab links load.** A node with `prefab_link { path }` places a prefab file there: `SceneDocument.withPrefabs` puts the prefab's root under the node, reading each file once however many nodes link it, and prefabs may link others. `loadPlayableProject` runs it. The live `PrefabLink` component exports back as the link, so a saved world does not copy the prefab in. `ScenePrefab` files now decode their components. The unused `prefabGuid`, `isRoot`, `SceneNode.prefabGuid` and `SceneNode.overrides` are removed.
- **A material tints skinned meshes.** A skinned entity's `PbrMaterial` base colour and emissive factors now reach its draw, so a greyscale part such as hair or dyed armour takes a per-character colour instead of drawing its raw texture. The skinned shaders multiply the texture and vertex colour by the base colour (its alpha included) and add the emissive, as the static textured shader does; a skinned draw with no material stays untinted. `skinnedMaterialFloats` packs a palette with factors for callers that build draws themselves. Instanced skinned draws are not tinted yet.

### Changed

- **`awake:engine:window` hosts the iOS view.** The UIKit view, its display link, and its touch and text input moved out of the Vulkan family into Core, so an iOS input change releases with Core. `makeVulkanGameViewController` keeps its signature. Breaking: `VulkanMetalView` is now `com.awakekt.awake.engine.window.AwakeMetalView`, and the `syncAwake*` UIKit bridges moved to the same package.

### Fixed

- **`awake:engine:window` publishes for every target.** It had desktop sources only, so its iOS targets compiled no library and publishing the release failed. The module now has common code as well.
- **`publishToMavenLocal -Pawake.version=<v>` publishes every target.** The Android, desktop and iOS publications ran only with `-PisMainHost=true`, so a local publish carried wasm alone and a desktop consumer could not resolve it. A local version now implies it.

## [0.1.0-rc.7] - 2026-10-03

### Added

- The engine showcase has an `ecs-stress` demonstration: up to 100,000 moving entities, each an ordinary ECS entity with Transform and MeshRenderer components, drawn in a few instanced calls. Its stats card shows p99 and worst frame time, game/render/UI split, recorded draws and triangles, and `-Pawake.showcase.perfLog=true` prints a `PERF` summary line, so a frame-rate drop can be reproduced without Studio.
- **`awake:engine:window` opens the desktop window.** The GLFW window, its keyboard, pointer, scroll, text input, clipboard and cursor moved out of the Vulkan family into this Core module, with its own native library (`libawake-window`) and a backend-neutral loop, `runDesktopWindow`. An input or text-field change now releases with Core instead of forcing a Vulkan release. `runVulkanDesktopGame` keeps its signatures. Breaking: `VulkanWindow` keeps only surface creation, the instance-extension list and the size queries, and `destroySurfaceWindow` is gone (the window host destroys its window); the `Glfw*` input bridges now live in `com.awakekt.awake.engine.window`.
- Renderers report what each frame recorded through `Renderer.frameStats` (draw calls, instances, triangles, and a slot for GPU time), and scene frame stats add the p99 and worst frame time plus a game/render split of the frame.
- Vulkan renderers report GPU frame time in `Renderer.frameStats.gpuTimeMs`, measured with timestamp queries around each frame's command buffer. The engine showcase stats card and `PERF` log show it. Devices whose graphics queue writes no timestamps, and WebGPU, keep reporting none.
- **Any module can generate its own icons from SVG.** The `com.awakekt.awake.plugin.icon-codegen` Gradle plugin is now published. Put a `manifest.json` and the SVGs it names under `src/<sourceSet>/svg/<pack>/` and the build generates one `ImageVector` object for that source set, with each icon a lazy `val` built on first use. The generator is Kotlin in build-logic, so Python is no longer a build requirement. Its output is byte-identical to the old script's.

### Changed

- **Lit surfaces keep their colour instead of washing out to grey.** Every lit shader (`lit_shadow`, `textured` and their instanced and skinned variants, on Vulkan and WebGPU) shows its colour through one shared transform: an exposure, Khronos PBR Neutral, then the gamma encode. This replaces per-channel Reinhard, which compressed bright channels more than dim ones and turned an amber cube khaki. `lit_shadow` now treats light colour as reflectance, as `textured` already did, so a light of intensity 1 lights a white face to near white instead of mid-grey. Scenes that raised intensities to make up for this need about a third of the old value. The engine showcase's point lights go from 6 to 2 and its ECS stress sun from 3 to 1. A new `tone_mapping` scene component sets `exposure` (default 1), and the curve deepens surfaces only the ambient reaches.
- Draws on Vulkan cost less CPU and fewer driver objects: the lit-shadow uniform block packs the frame's lights, cascades, camera and fog once per frame and copies them into each draw instead of re-packing them per draw, and a material's per-draw uniform buffers come out of 32-slot memory and descriptor-pool blocks instead of one allocation each, which kept scenes of a few thousand distinct draws under the device allocation limit.
- Copies of a back-face-culled mesh (`MeshRenderer.cullMode = Back`) are auto-instanced like unculled ones: every plain instanced scene pipeline now builds its back-culled twin, and batching folds back-culled copies wherever the backend has one. Before, each back-culled prop was its own draw.
- Scenes with many moving entities cost about 40% less CPU per frame and collect garbage about 94% less often (50,000 moving entities, measured with `ecs-stress`): world bounds are computed without allocating a corner list, culling a moving entity builds no box, `World.queryEach` no longer boxes each `Entity`, extraction reuses its lists, `TransformSystem` skips reference stores that change nothing, and instance batching groups consecutive copies without a key per draw.
- **Texture animation is its own scene component, `texture_animation`, instead of a field of `pbr_material`.** A PBR material now holds only glTF's metallic-roughness values; the frame sheet and UV scroll sit beside it as `SceneTextureAnimation`, live as the entity's `TextureAnimation`, and play with glTF's default factors on an entity that has no material. A scene that set `pbr_material.textureAnimation` loads with a still texture: move the object to a `texture_animation` component on the same node. `PbrMaterial.textureAnimation` is gone; `PbrMaterial.packedFloats` takes the animation.

### Removed

- **Core names no product and ships no biome presets.** `PluginManifest.isPro` is gone: a plugin's entitlement is `requiredLicense`, which each editor host maps to its own tiers, and manifests that still carry `"isPro"` parse unchanged. `ProceduralTerrainMaterialConfig.MountainAlpine`, `RollingHills` and `DesertCanyon` are removed; a project authors its own `ProceduralTerrainRule` set as data. `ChaseBehavior`, `FleeBehavior` and `PatrolBehavior` are now supported API rather than copy-and-adapt starters.
- **The `heroicons` artifact is gone.** Copy the SVGs you draw from the Heroicons `v2.2.0` release into your own module and apply `com.awakekt.awake.plugin.icon-codegen`; `HeroIcons.Solid20Mini.x` and the other tiers keep their names if your manifest declares the same tier objects. `com.awakekt.awake.ui:shadcn` now carries its eight Lucide glyphs internally, so read them through `ShadcnIcons`. `tools/icons/svg_to_ui_image_vector.py` is removed along with its `--packed`, `--batch`, `--svgo` and `--expand-strokes` modes.

### Fixed

- `./gradlew :samples:engine-showcase:run -Pawake.showcase=<id>` opens the requested demonstration again; the run task had stopped forwarding the property. The engine showcase web dev server no longer shares port 8088 with the net demo.
- Game and render phase times read during a frame, by a frame-time logger or any system, report the last completed frame instead of the current one half-counted; the engine showcase `PERF` line printed `game=0.0 render=0.0` because of it.
- An `InstancedMeshRenderer` (or any authored instance list) with more copies than one draw holds is split into several draws instead of throwing; skinned instance lists split at the palette buffer size. The frame stats no longer report every auto-instanced entity as an unresolved draw. Vulkan draw preparation waits for the frame slot before rewriting its uniforms, which an app without UI never did.

## [0.1.0-rc.6] - 2026-10-03

### Added

- Renderers report what each frame recorded through `Renderer.frameStats` (draw calls, instances, triangles, and a slot for GPU time), and scene frame stats add the p99 and worst frame time plus a game/render split of the frame.

### Fixed

- **A Core release no longer re-uploads the Vulkan family.** `-Pawake.publishFamily=core` was registered on the root project's publish tasks only, so it filtered nothing, and a Core release uploaded the Vulkan modules too. That went unnoticed while the Vulkan version was a snapshot. `v0.1.0-rc.4` was the first Core release after a Vulkan tag with no Vulkan changes since, so it re-uploaded `vulkan` 0.1.11, which Central already had, and Central rejected the whole release. The filter now applies to every project's publish tasks.

## [0.1.0-rc.5] - 2026-10-02

### Added

- `assertMatchesBaseline` also takes a list of draw primitives, so a screen captured by driving a whole app through a capturing renderer can be compared against a committed baseline.

## [0.1.0-rc.4] - 2026-10-02

### Added

- **Re-read a changed glTF model.** `GltfAssetResolver.forget(path)` drops the geometry, skinned scene, materials and part textures parsed from one model file, so the next `preload` reads it again. An editor reloading a scene from disk calls it for each model that changed; meshes and materials already created stay until released.

## [0.1.0-rc.3] - 2026-10-02

### Added

- **Keyframe animation from scene documents.** `keyframe_animation` (`SceneKeyframeAnimation`, `KeyframeAnimationSystem`) loops a node's local position, rotation and scale and its material's base colour alpha through linearly interpolated keys, with no skeleton. Pulsing glows, rising sparks and fading effect parts can be authored as data. `playProject` runs it when a scene has one.
- **Password fields.** `BasicTextField` and `ShadcnInput` take `mask: Char?`. A masked field draws one mask per character of its text, IME pre-edit included, and the caret, selection and click positions line up with the masked glyphs. `TextFieldState` keeps the real text. `revealLastTyped = true` shows each typed character in clear for 1.5 seconds, as Android and iOS do on a touch keyboard; a paste or any later edit hides it at once. A masked field carries the new `SemanticsProperties.Password`. While it has focus the frame reports `PlatformEffects.passwordKeyboard`, which reaches `Input.textInputPassword`: Android then declares the editor `TYPE_TEXT_VARIATION_PASSWORD` with no suggestions and iOS answers `isSecureTextEntry`, so the keyboard neither learns nor suggests the password. New `ComposeHost.copyFocusedSelection()`/`cutFocusedSelection()`, the path any clipboard adapter must take, return nothing from a masked field. Pass `PasswordMask` (`'•'`, U+2022) so every field masks alike; the bundled Roboto atlas now packs the bullet beside printable ASCII, in every weight.
- **Bullet glyph in the bundled UI font.** All seven bundled Roboto faces now pack U+2022 `•` beside printable ASCII, in what was the atlas's one free cell, so the atlas size and every existing glyph are unchanged. `font-atlas-generator` packs it from `EXTRA_GLYPHS`, which appends after ASCII so existing cells never move.
- **Copy, cut, paste and select-all in text fields.** Ctrl+C/X/V/A (Cmd on macOS) work on desktop through GLFW's clipboard, on the web, and on Android from a hardware keyboard and from the keyboard's own Select all/Copy/Cut/Paste buttons. Platforms push `ClipboardCommand`s and `TextEditAction.SelectAll` into `Input`; the focused field answers in `PlatformEffects.clipboardText`, which the host writes to the system clipboard, and paste arrives as typed text. Password fields still give nothing. Clipboard text keeps characters outside the BMP intact on desktop, where JNI's own string calls would have garbled them. iOS has no clipboard path yet.
- **A hidden input element for web text fields.** While an Awake text field has focus, the WebGPU host focuses a hidden `<input>` (`DomTextInputBridge`), so mobile browsers raise their keyboard, IME composition arrives as pre-edit and commit, dead keys and autocorrect type what the user meant, and a masked field becomes `type=password`, so keyboards and password managers treat it as one.
- **Particle emitters from scene documents.** `particle_emitter` (`SceneParticleEmitter`) places a `ParticleEmitter` at its node and follows it: a project image as the sprite, rate, lifetime, fade, size over life, velocity, cone, spawn ring, outward speed, tint, sprite strip and additive glow. `loadParticleSprites` reads the sprites and `ParticleContentSystem` creates the emitters, sharing one quad and one material per sprite. `playProject` does both when the scene has an emitter.
- **Additive, growing and outward-blown particles.** `ParticleVisual.additive` draws an emitter's particles added to what is behind them, through the particle pipeline's additive twin (`PipelineVariant.AdditiveBlendedParticle`, built with `buildAdditive = true`); `ParticleVisual.endScale` grows or shrinks each particle linearly over its life; `ParticleMotion.radialSpeed` blows particles horizontally outward from the spawn ring. `ParticleDynamics.followEntity` now follows a child node's world position rather than its local one.
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for AI core and behavior modules (`:awake:ai`, `:awake:ai:behavior`).

### Fixed

- **Ctrl and Cmd shortcuts no longer type their letter on desktop.** The GLFW text bridge ignored modifiers, so Ctrl+S or Ctrl+C in a focused text field also inserted "s" or "c". A letter pressed with Ctrl or Cmd held is now a shortcut only.

## [0.1.0-rc.2] - 2026-10-02

### Added

- **Billboard meshes.** `mesh_renderer` takes `billboard: true` (`MeshRenderer.billboard`): the mesh keeps its entity's position and scale but turns to face the camera every frame, local +Z toward the eye. Glows, flares and other effects authored as flat quads stay facing the viewer. A billboard is not culled, because its bounds turn with the camera.
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for animation and audio core modules (`:awake:core:animation`, `:awake:core:audio`).
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for asset tooling and project modules (`:awake:asset:mesh-optimizer`, `:awake:asset:shader-compiler`, `:awake:project`).
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for physics subsystem modules (`:awake:physics:api`, `:awake:scene:physics`, `:awake:physics:ragdoll`).

### Fixed

- **Skinned meshes cast shadows.** A textured skinned mesh was never classified as skinned, so no shadow pipeline matched it and the shadow pass skipped it. Untextured ones cast only where the plan declared a skinned depth variant, and that shader projected the camera's matrix into the shadow map. A skinned scene pipeline now names its caster with `depthShaders` (`PackShaderSets.SkinnedTexturedShadowDepth` or `SkinnedShadowDepth`). Any format with joints casts as skinned, and the skinned uniform block carries the model matrix the shadow shader places the posed mesh with. `VertexFormat.isSkinned` reports whether a format has joints.

## [0.1.0-rc.1] - 2026-10-01

### Added

- **Asset converters can be unregistered.** `AssetConverterRegistry.unregister(converter)` removes a converter from every extension it handled, and a converter it had replaced converts that extension again, so an editor can unload a plugin's converters without losing the ones it overrode.
- **Run an app without Vulkan validation.** `-Pawake.vulkan.validation=false` keeps the Khronos validation layer off for a Gradle `run` task, which otherwise turns it on. The layer checks every Vulkan call, which costs a large scene about a tenth of its frame, so measure frame time with it off. Tests still validate.
- **Static props cost almost nothing per frame.** A `static_transform` component (`StaticTransform`) marks a node that never moves. `TransformSystem` builds its world matrix once and then skips it without reading its values. On a region of about 8,000 placed props, comparing every prop each frame took about 16% of a Play frame. An editor that moves static nodes builds `TransformSystem(skipsStatic = false)`.
- **KDoc public API completeness audit and Detekt enforcement.** Performed a comprehensive public API KDoc audit across all 77 repository modules, identifying documentation coverage tiers and gaps. Enabled Detekt's public documentation rules (`UndocumentedPublicClass`, `UndocumentedPublicFunction`, `UndocumentedPublicProperty`) scoped to verified modules, and completed 100% public KDocs for `:awake:core:color`, `:awake:core:image`, and `:awake:engine:bootstrap`.
- **Complete KDoc documentation and Detekt enforcement for core foundation and utilities.** Documented 100% of public declarations across `:awake:core:logging`, `:awake:core:host`, `:awake:core:input`, and `:awake:core:math2d`. Expanded Detekt's scoped public KDoc rules (`UndocumentedPublicClass`, `UndocumentedPublicFunction`, `UndocumentedPublicProperty`) to enforce documentation completeness on all four modules.
- **Complete KDoc documentation and Detekt enforcement for core foundation modules.** Documented 100% of public declarations across `:awake:core:state`, `:awake:scene:binding`, `:awake:core:config`, `:awake:core:di`, `:awake:scene:character`, and `:awake:scene:scene-core`. Expanded Detekt's scoped public KDoc rules (`UndocumentedPublicClass`, `UndocumentedPublicFunction`, `UndocumentedPublicProperty`) to strictly enforce completeness on these modules.
- Completed 100% public declaration KDoc coverage and enabled strict Detekt enforcement for scene extensions and Compose integration modules (`:awake:compose:di`, `:awake:compose:state`, `:awake:scene:audio`, `:awake:scene:canvas`, `:awake:scene:blueprint`, `:awake:ui:material3`, `:awake:engine:compose`).

### Changed

- **A material is packed for the GPU once, not every frame.** `PbrMaterial.packedFloats()` keeps the uniform floats until a field changes, and the draw collector uses it. A region of about 8,000 props packed and allocated each masked prop's material every frame, about a tenth of a Play frame.
- **Cheaper UI frames on Vulkan.** An unchanged UI run is not uploaded again: the retained staging cache hands back the same arrays, and Vulkan now skips writing them, as WebGPU already did. The shared 2D loop also stops rebinding a pipeline or setting a scissor rect that the previous run left in place. Binding a UI mesh no longer allocates.

### Fixed

- **A scene that unloads frees the meshes and materials it drew.** `SceneAssetLibrary` cached every asset by name and nothing released it, so after a scene switch the next scene got the previous one's mesh whenever a name repeated, for example two projects shipping different files at the same model path. `SceneManager` now runs an unload hook before destroying a scene's entities, and the session releases each renderer that `resolve` built through the new `SceneAssetLibrary.releaseRenderable`. A mesh or material that fails to build no longer keeps a hold.

## [0.1.0-beta.1] - 2026-10-01

### Changed

- **The 0.1.0 public API is frozen.** From this beta until `0.1.0`, a change to a published API deprecates the old form before removing it. Android multi-touch, display density and audio (#155, #156) and AI in the project player (#213) move to 0.2.0, since each changes a public API. Everything else in this milestone shipped in `0.1.0-alpha.18`.
- **Static scenes cost less per frame.** `TransformSystem` rebuilds a world matrix only when the entity's position, rotation, scale, parent or parent's matrix changed, instead of every entity every frame; a region of thousands of static props now rebuilds none. `Transform.worldVersion` counts the rebuilds, and `MeshBounds.worldBounds(transform)` caches world bounds on it, so culling a prop that has not moved is one integer comparison. The draw collector resolves its component types once per frame.

## [0.1.0-alpha.18] - 2026-09-30

### Added

- **Jumps play in phases.** `locomotion_animation` gains `takeOff`, played once as the character leaves the ground, `fall`, looped while it comes down (`jump` now covers the rise), and `land`, played once as it touches down unless it is already walking away. All three are optional; a scene naming only `jump` plays it for the whole jump as before.

### Fixed

- **Android Vulkan apps follow a rotation.** The swapchain was built once for the starting orientation, so turning the device left the picture turned or stretched. It now asks the surface for an unrotated image (identity pre-transform, extent swapped on a quarter turn) and is rebuilt when the host reports a resize or the surface changes size or orientation. A suboptimal acquire no longer throws, and a suboptimal present rebuilds only when the surface actually changed. `GraphicsEngine.resizeBackend()` lets a backend react to a resize.
- Generate shadcn Lucide icons from pinned SVGs, correct round stroke inner joins, and center vector stroke antialiasing to keep icon edges close to the source at small and large sizes.
- **Textured skinned parts sample their texture the right way up.** `PackShaderSets.SkinnedTextured` passed UVs straight through, while every other textured shader undoes `createBitmap`'s bottom-up decode by flipping V, so a character's textures landed upside down on its parts.

## [0.1.0-alpha.17] - 2026-09-30

### Added

- **The scene DSL sets a sun's shadows.** `directionalLight(...)` and both `sun(...)` helpers take `shadowsEnabled` and `shadowDistance`, with the `Light` component's defaults, so a Kotlin scene can say everything a scene document can. The `direction` parameter is documented as the direction the light comes from, as `Light` has always used; it said the opposite before.
- **Additive blending for glowing surfaces.** A transparent draw with `additive` (`mesh_renderer.additive` in a scene, `MeshRenderer.additive`, `RenderDrawCommand.additive`) adds its colour to what is behind it (`SRC_ALPHA`/`ONE`) instead of covering it, the blend fire, light shafts and magic effects are authored for. `PipelineVariant.AdditiveBlended` is the new companion; `RenderPlan`'s primary pipeline builds it, and `ScenePipeline.buildAdditive` opts other formats in. Without one, an additive draw falls back to alpha blending.
- **A host can draw the scene canvas itself.** `SceneAppLifecycleRuntime.drawsSceneCanvas` (on by default) lets an editor or any host that shows the game in part of its window turn off the runtime's full-window scene canvas and draw `SceneCanvas` in its own game view.
- **Terrain is solid ground.** A `terrain` scene component with `collider: true` (`TerrainComponent.collider`) gets a static heightfield body built from its own heightmap and laid where its mesh lies, so characters and bodies stand on the ground they see. `TerrainColliderSystem` in `awake:project:runtime` builds it before `PhysicsSystem`; `loadPlayableProject` asks for a physics world when a scene has one, and `playProject` runs it. A collider needs a square terrain, which scene validation checks.
- **A skinned glTF draws every part, each with its own texture.** In a skinned `.gltf`, `gltf-primitive:<path>#<i>` is its i-th node with both a mesh and a skin, and `gltf-material:<path>#<i>` that part's base colour texture; `GltfAssetResolver.materialSlots` lists them. A textured part draws through a `PositionNormalColorUvSkin` pipeline (`PackShaderSets.SkinnedTextured`), which the host's render plan declares. `playProject` gives a model's parts one shared animator on their parent node, so they move as one character. Skinned formats no longer get a depth pipeline built from the plan's unskinned depth shader, which cast a wrong shadow; they cast none until they have their own. WebGPU binds a textured material against the pipeline shader's own group 0 when the plan declares no material bindings.
- **Characters run, turn and play clips for how they move.** `movement_control` gains `runSpeed`, used while Shift is held (`PlayerInputSystem` sets `MovementControl.run`), and `turnSpeed`, the radians per second a character turns to face where it moves; both `MatrixRelativeMovementSystem` and `CharacterControllerSystem` honour them. New `locomotion_animation` (`SceneLocomotionAnimation`, loaded by default) names a skinned model's idle, walk, run and jump clips and the speeds between them; `LocomotionAnimationSystem` measures how the entity moves and cross-fades to the matching clip, however it is moved. A `character_controller` keeps a `GroundContact` (new, in scene-core), and jump plays exactly while it is off the ground; other movers play jump from take-off until they land. A mover stepped at a fixed rate keeps its clip between steps. `playProject` runs it when a scene uses it.

### Fixed

- **Alpha-masked textures cut out, in the scene and in shadows.** A masked material's clear texels drew as their stored colour, usually black: the textured shader never discarded below the cutoff, so a tree's leaf cards showed as dark rectangles. The shadow pass drew masked casters as whole cards too, and its masked caster shader placed them with the camera matrix. Now the textured shader discards below a masked material's cutoff (opaque materials write a cutoff of 0, so their texture alpha is ignored), masked casters draw through `MaskedTexturedShadowDepth` or the new `InstancedMaskedTexturedShadowDepth` placed by the cascade, and a masked draw with no masked caster casts nothing rather than its card. Declare the masked casters in `RenderPlan.depthPrePassKeyedVariants` to get cut-out shadows.

## [0.1.0-alpha.16] - 2026-09-30

### Added

- **Scenes carry the player marker.** `movement_control` is now a Core scene component (`SceneMovementControl`), loaded after `registerControls()`, so games load scenes that Awake Studio authored with a controllable character. Its `speed` becomes `MovementControl.speed`, which `MatrixRelativeMovementSystem` uses in place of its own speed, and `PlayerInputSystem` sets `MovementControl.jump` from Space for a `character_controller` to act on.
- **Game UI in scenes.** New `awake:scene:canvas`: `CanvasElement` (saved as `canvas_element`) puts text, panels, bars and buttons on screen, pinned to one of nine anchors with an inward offset, and `SceneCanvas(world)` draws them. `SceneAppLifecycleRuntime` draws a scene's canvas over the game, and under the app's own `ui { }`, even when the app declares no UI. A button's `consumePress()` reports taps. It works like Unity's in-scene Canvas or Godot's `Control` nodes.
- **Brand guidelines and name lockups.** awakekt.com/brand shows the Ember Signal mark, the colours, the usage rules and six downloadable name lockups (`AwakeKt`, `AwakeKt Engine`, `AwakeKt Studio`, each for dark and light surfaces). The mark keeps its dark field on both; only the name's colour changes. `tools/fonts-tooling/brand_lockups.py` regenerates them with the text outlined in Roboto, and `docs/reference/brand-system.md` records them as the approved wordmark assets. The engine showcase's Android icon now sits inside the adaptive-icon safe zone instead of losing the A's corners on round launchers.
- **Images in Compose UI.** `Image(bitmap, contentDescription, contentScale = Fit | Crop | FillBounds | Inside)` draws decoded pixels: `decodeImageBitmap(bytes)` decodes PNG or JPEG with each platform's decoder into an `ImageBitmap`, and `DrawScope.drawImage` draws one into any rectangle. The engine's Compose host uploads each bitmap to the GPU the first frame it appears and keeps it for the host's lifetime, since a texture uploaded through `createMaterial` is freed only at renderer teardown; reuse a decoded bitmap rather than decoding the same file again. The software rasterizer used by `composeFrame` tests draws images too. WebGPU's UI pass can now sample a material built from an uploaded texture; it only accepted render-target materials before.
- **Camera rigs are saved in scenes.** `camera_rig` (`SceneCameraRig`) stores how a camera follows or orbits: mode, the node it follows, distance and its limits, pitch, yaw, aim offset and fly speed, so a template or game authors its camera feel as data. `CameraMode` is serializable. It loads after `registerControls()`, and the first `CameraSystem` update keeps the authored angles rather than resetting them for the mode.
- **Touch controls are scene UI.** `canvas_element` gains a `Joystick` kind, whose knob drag sets `stickX` and `stickY`, an `action` name and a `touchOnly` flag, and Buttons report `isHeld`. `SceneCanvas(world, showTouchControls)` and `SceneAppLifecycleRuntime.showTouchControls` show touch-only elements on touch screens. In `awake:project:runtime`, `CanvasActionSystem` steers the player with a `move` Joystick and jumps while a `jump` Button is held, and `playProject(project, touchControls = true)` turns them on; the scene decides where they sit and how big they are.
- **Repeated props draw as instances.** `ScenePassCompiler` folds opaque, unculled draws that differ only in their model matrix into one instanced draw whenever the backend has an instanced pipeline for the mesh's format (`GpuDrawPreparer.canInstance`), so a scene of repeated props costs one uniform upload, bind and draw call per mesh and material instead of one per copy. Textured meshes can now be instanced: `PackShaderSets.InstancedTextured` and `InstancedTexturedShadowDepth`, declared through the new `PipelineKey.InstancedFormat` and `ScenePipeline.depthShaders`. Instance buffers grow to what a draw holds instead of reserving 4096 matrices each, are allocated per instanced draw rather than per draw position, and WebGPU gives each instanced draw its own uniforms instead of one buffer every instanced draw overwrote. A draw with a single instance now casts its shadow from its instance matrix.
- **Colliders and rigid bodies are saved in scenes.** `physics_body` (`ScenePhysicsBody`) stores a body's shape (`box`, `sphere` or `capsule`), motion type, collision layer and whether it is a sensor, so floors, walls and props are authored as data and `PhysicsSystem` builds them from a loaded scene. It loads after `SceneComponentRegistry.registerPhysics()`. A body whose shape a scene can't describe, such as a terrain heightfield, is left out when saving.
- **Play an Awake project without the editor.** New `awake:project:runtime`: `loadPlayableProject(files, physicsWorld)` reads and checks `awake.project.json`, reads the entry scene, loads its glTF models and asks the host for a physics world only when the scene has bodies or characters; `SceneAppDsl.playProject(project)` then runs only what the scene's components call for (movement, physics and characters, the camera rig, spinning, skinned animation) with no tuning of its own. `builtInSceneAssets()` provides the neutral `cube`, `sphere`, `ground` and `plane` meshes and the `lit-shadow` material. `SceneAssetLibrary.meshName(mesh)` returns the name a live mesh was built under.
- **Characters that walk and jump through physics, saved in scenes.** New `awake:scene:character`: `character_controller` (`SceneCharacterController`) sets a character's capsule, step height, slope limit, jump speed and gravity, and `CharacterControllerSystem` moves it by its `movement_control` intent relative to the active camera through `KinematicCharacterController`, so walls stop it and it lands on `physics_body` floors. It loads after `registerCharacter()`. `CameraRelativeBasis` in `awake:scene:controls` turns movement intent into world motion for both this system and `MatrixRelativeMovementSystem`.
- **glTF models in scenes.** New `awake:scene:gltf`: `GltfAssetResolver` resolves `.gltf` and `.glb` paths in scenes to meshes and materials, including external `.bin` and image files, multi-material models and skinned models; register it with `assets { resolver(...) }`. A model whose textures fail to load logs why and draws untextured. It moves from Awake Studio, so a game loads the same models the author placed.
- **`Renderer.submitToTexture` renders offscreen without waiting for the GPU.** Vulkan's `renderToTexture` submits and then blocks until the GPU finishes, and since the queue runs in order, that includes all of the frame's earlier work: a small viewport widget rendered this way after the scene made the CPU sit idle through the whole scene's GPU time every frame. `submitToTexture` submits and returns, for a texture drawn every frame whose resources the caller keeps alive; later work, such as the UI that composites it, still runs after it. `renderToTexture` keeps waiting, for readbacks. WebGPU already rendered this way.
- **A loading screen for WebGPU pages.** `awake-loader.js`, shipped in the WebGPU backend's web resources, shows the AwakeKt mark and a progress bar while an app starts: the `.wasm` download by bytes, then WebGPU and shader startup, dismissed by the first frame drawn to the canvas. A missing download, a browser without WebGPU or an engine startup error shows a message instead of a black canvas. Load it before the app bundle with `<script src="awake-loader.js" data-product="…">`; the engine and UI showcases do. `prepare-samples-site.sh` now checks every script for a `.wasm` reference, so a second script beside the bundle no longer gets the bundle's wasm deleted.

### Changed

- **Changelog entries are fragments.** A PR adds `changelog/unreleased/<section>/<branch>.md` instead of editing `CHANGELOG.md`, and `releaseCut` files every fragment under the new version, in Keep a Changelog order, then deletes them. Every PR used to insert its bullet at the same line under `## [Unreleased]`, so open PRs conflicted there, and one merged after a cut landed its entry under a release that did not ship it. CI now asks `feat:`/`fix:` PRs for a fragment and rejects direct `CHANGELOG.md` edits outside `release-cut/*` branches.

### Removed

- **`awake:ui:builder` is removed.** The visual UI layout builder (layout documents, drag-and-drop reflow, Kotlin code generation) had one consumer, Awake Studio, which retired it. Game UI is heading toward anchored scene entities instead.

### Fixed

- **Jolt no longer crashes when two processes load it at once.** The desktop backend extracted `libjoltjni` into the shared temp directory and rewrote it on every load, so two test processes (or games) starting together could crash with SIGBUS when one rewrote the library the other had mapped. Each process now extracts into a folder of its own.
- **A kinematic character's jump is no longer cancelled on take-off.** For the first steps of a jump `KinematicCharacterController`'s ground probe still reached the floor, so it reported the character grounded and the next move snapped it back down. A move that rises faster than the ground under it now leaves the ground; a character riding a rising platform stays grounded.
- **Shadow passes draw only the casters they can see.** Every shadow cascade and point-light face used to draw every visible opaque draw, so a wide view of a large scene recorded its draw list five times over. Each pass now skips a draw whose world bounds lie beside or beyond its depth map. `GpuDrawRequest.worldBounds` carries those bounds; the scene fills it from `MeshBounds`, and a draw without bounds still casts into every pass.
- **Textured meshes cast their shadows where they stand.** The shadow pass draws every mesh with one depth shader, which reads the draw's model matrix at the lit block's offset. The textured PBR block kept its model elsewhere, so the pass read a cascade matrix instead, and a textured mesh's shadow landed out of view, moving with the camera. `MaterialUniformLayouts.PbrTextured` now starts with the same shadow-depth prefix as `LitShadow` (192 floats, up from 188). A textured caster's shadow matches an untextured one on both backends.

## [0.1.0-alpha.15] - 2026-09-29

### Added

- **Apps can tell a trackpad from a mouse wheel on macOS.** `InputSnapshot.scrollSource` (and `GameplayInput.scrollSource`) says whether the frame's scroll came from a `Trackpad` or a `Wheel`, so a scene view can pan with two fingers and zoom with a wheel instead of guessing from the deltas. GLFW does not report it; a native AppKit event monitor reads `NSEvent.hasPreciseScrollingDeltas` beside GLFW's scroll callback. Other platforms report `Unknown`. `InputSnapshot` gains a trailing field, so code built against its constructor or `copy` must be recompiled. Ships with the next Vulkan release.

### Fixed

- **Sideways scroll reaches the app on desktop.** GLFW's scroll callback kept only `yoffset`, so a trackpad's two-finger swipe left or right never arrived and `Input.scrollDeltaX` stayed 0 outside the browser. The callback now accumulates both axes and `pollGlfwInput` fills `scrollDeltaX`; `GameplayInput.scrollDeltaX` exposes it, gated like `scrollDeltaY`. The scroll functions move to `@JniNative` implementations in `VulkanWindow_native.cpp` (D11). Ships with the next Vulkan release.
- **Scrolling goes the right way in the browser.** The WebGPU canvas host passed DOM wheel deltas through with the wrong sign: the DOM counts how far the page scrolls (positive down), GLFW how far the wheel turned (positive away from the user), and every scroll container reads GLFW's convention. A wheel turned toward the user scrolled lists up in the browser and down on desktop, and a scene camera zoomed the wrong way. Both axes are now negated to match desktop.

## [0.1.0-alpha.14] - 2026-09-29

### Fixed

- **Android UI is sized for the screen.** The engine reported a density of 1 on Android, because a `Surface` carries no display metrics, so dp-sized UI drew at desktop pixel size: about 2.6× too small on a typical phone. `VulkanView` now passes `displayMetrics.density` through the new `WindowLifecycle.setDensity()`, and reads it again on every surface change, so folding or moving to DeX updates it.
- **A camera whose near plane passes the shadow distance no longer crashes.** Far enough out, a camera's near plane sat beyond the sun's `shadowDistance`, and fitting cascades to that empty range threw `Cascades need a positive near..far range`, which took the app down. Nothing that camera sees is within shadow reach, so the frame now draws without a shadow pass.
- **Shadows stay put while the camera moves on Vulkan.** The shadow pass read each cascade's matrix from one buffer shared by every frame in flight, so while the GPU still owed a frame its shadow maps, the CPU recording the next frame overwrote them. A moving camera then had its shadow maps drawn with the next frame's cascades and sampled with its own, and flat ground and water shadowed themselves in dark patches that vanished when the camera stopped. Each frame in flight, and the offscreen frame, now has its own cascade slots. The shadow map's render pass also now waits for the previous frame's sampling before overwriting it. WebGPU orders its buffer writes on the queue and was not affected. (Ships with the next Vulkan release: it came after the 0.1.0-alpha.13 tag.)

## [0.1.0-alpha.13] - 2026-09-29

### Added

- **Free-fly moves, and middle-drag pans.** A `CameraMode.FreeFly` camera now flies with W/S/A/D along the view, Q/E down and up, and Shift four times faster, at `CameraRig.flySpeed` units a second (default 10). `CameraGesturePolicy.flyKeys` rebinds them, and `canFly` gates them: always for games, only while Right is held in `CameraGesturePolicy.Editor`, where W is a tool shortcut. `isPanDragging` was declared but never read; a pan drag now moves the orbit point (`CameraRig.offsetPosition`), or a free-fly eye, across the view by `panSensitivity` times the distance per pixel. The editor pans with Middle; the default policy no longer claims Shift + Left, so a game's follow camera stays on its target.
- **Editor zoom keeps pace with distance.** `CameraGesturePolicy.zoomProportion` adds that share of the current distance to each scroll notch; the editor uses 0.1, so a far view no longer crawls in half a unit at a time.

### Changed

- **Switching camera modes keeps the view.** Free-fly starts from the eye, yaw and pitch it replaces instead of snapping level to -Z, and top-down keeps the rig's distance instead of jumping to 15.

### Fixed

- **Android apps survive going to the background.** `VulkanView` disposed the whole engine when its surface was destroyed and built a new device when it came back, while the app kept resources made on the old device, so the first draw after returning failed. The surface is now released and replaced on the same device and renderer: `WindowLifecycle` gains `releaseSurface()`/`restoreSurface(surface)`, the app is paused in between, and the engine is disposed when the view detaches.
- **A second Vulkan debug messenger no longer aborts the process.** The messenger's JNI accessor was copied on every use, and the copy freed the global references the singleton still held; recreating the device (an Android app returning from the background) then freed them again and ART aborted with a stale global reference. The accessor is now returned by reference and can't be copied.

## [0.1.0-alpha.12] - 2026-09-29

### Added

- **Terrain casts shadows.** Clipmap terrain is displaced on the GPU, so no shadow pass drew it and hills and cliffs cast nothing. `RenderSystem3D` now submits each visible terrain's heightmap as a mesh drawn into the shadow maps only -- up to 513 samples a side, every n-th sample past that -- rebuilt when the heightmap or its revision changes. `RenderDrawCommand.shadowsOnly` marks such a draw for any other stand-in caster: the pass compiler hands it to every shadow pass and keeps it out of the scene.
- **Scenes set the ambient light.** `SceneLight.ambient` (`Light.ambient` at runtime) on the directional light is how much of a surface's colour shows with no direct light, above 0 and up to 1. `lit_shadow`, the textured shader and terrain (base and layered) use it in place of their own 0.08 or 0.35; unset, each keeps its own, so existing scenes look the same. It travels in the scene light payload's `lightColor.w`, where 0 means unset.

### Fixed

- **The Android Vulkan library is 16 KB page aligned.** `libawake-vulkan.so` (arm64-v8a and x86_64) linked with 4 KB LOAD segments, which Google Play rejects for apps targeting Android 15+. It now links with `-z max-page-size=16384`.
- **Textured meshes keep their shade out of the sun.** The textured PBR shader lit a glTF base-colour texture's sRGB bytes as if they were linear and wrote the result unencoded, while targets take sRGB-encoded colour, as `lit_shadow` writes it. Lit faces came out about right, but a face lit by ambient alone lost almost all of it: mid-grey 128 came out as 10, near black, on Vulkan and in the browser. Base colour and emissive textures are now decoded to linear, and the output is encoded, as `lit_shadow` does; that face now comes out as 41.

### Fixed

## [0.1.0-alpha.11] - 2026-09-29

### Added

- **Cubemap skies draw.** `Skybox.Mode.Cubemap` (`SceneSkybox(type = "Cubemap", cubemapPath = ...)` in a scene file) rendered nothing. `SkyboxCubemapSystem(host, scope, assets)` now reads the path as a cubemap strip -- six square faces side by side, +X, -X, +Y, -Y, +Z, -Z, each laid out as the GPU samples a cube face -- and draws it behind the scene through `skyboxCubemapContentFeature(cubemap)`; another path swaps it and leaving cubemap mode removes it. An app registers the system like `TerrainContentSystem`. `decodeCubemapStrip` decodes a strip, and a content feature can now bind a cube texture.

## [0.1.0-alpha.10] - 2026-09-28

### Added

- **Ragdolls, world-stream content and glTF simplification are free runtime.** They lived in Studio's commercial modules, but a shipped game needs them at runtime, which is never commercial. New `com.awakekt.awake.physics:ragdoll` (`humanoidRagdoll`, `RagdollRig`, `Ragdoll`, `RagdollSkeleton`, on `physics:api` only) and `com.awakekt.awake.scene:worldstream` (`MeshCellStreamer`, `PhysicsCellStreamer`, `heightFieldCellStreamer`, `VirtualTerrainCellStreamListener`, `WorldstreamTerrainDriver`); `ProceduralTerrainSplatGenerator` joins `asset:terrain`'s splat package and `GltfMesh.simplified` joins `asset:gltf`.
- **Replace a running shader without a restart (Vulkan).** `renderer.capability(ShaderReplacement)` returns a `ShaderReplacement` whose `replace(old, new)` rebuilds every pipeline running the `old` shader program with `new`, in place, so the renderer, the pipeline table and content features all draw with it from the next frame. `ShaderStages.program()` turns an ASL set into a `ShaderProgram`. A replacement that does not compile, or binds differently from the pipeline, is refused with `ShaderReplacementException` and changes nothing; replacements chain. Call it on the render thread between frames. WebGPU returns null for now, and depth-only and debug-line pipelines are not replaced.
- **Lit shader variants, and a hot reload sample.** `litShadowShader(clipSpace, ambientStrength)` builds a variant of the shipped `lit_shadow`; the default emits the shipped shader byte for byte. The engine showcase uses it on desktop: L swaps the lit shader for a brighter-ambient variant while the showcase runs, and K tries a broken one, which is refused and printed as a warning. The desktop showcase now prints warnings and errors.
- **Scenes set how far directional shadows reach.** `SceneLight.shadowDistance` (and `Light.shadowDistance`) is how far from the camera a directional light's shadow cascades extend, in world units; the camera's far plane still caps it. It defaults to the previous fixed 100, so existing scenes are unchanged, and a large outdoor scene can push it out instead of losing shadows past 100 m. A distance that is not finite and positive fails scene validation.
- **Animated textures.** A textured PBR material can play its base texture as a frame sheet and scroll it: `ScenePbrMaterial.textureAnimation = SceneTextureAnimation(columns, rows, frameCount, framesPerSecond, scrollU, scrollV)` in a scene file, or `PbrMaterial.textureAnimation` at runtime. Frames play in reading order from the image's top-left and loop; the draw's `timeSeconds` drives both. The textured uniform block gains `textureFrames` and `textureScroll` (104 floats, from 96); a 12-float factor payload still packs as before. ASL gains `textureSampleGrad`.
- **Textured meshes receive the sun's shadow.** The textured PBR shader samples the cascaded shadow map like `lit_shadow`, so glTF props and buildings darken under what casts onto them, and the shadow debug views cover them. The textured uniform block gains the cascades (188 floats; the masked depth shader's offsets are unchanged), a UV mesh now picks the textured plan by its exact block size, and `PackShaderSets.Textured` is built per clip space: `TexturedShader` becomes `texturedShader(clipSpace)`. A custom pipeline for the textured shader must lay out the shadow map's set, as `lit_shadow`'s does.

### Fixed

- **Vulkan validation is opt-in.** The Vulkan backend enabled the Khronos validation layer whenever one was installed, so a packaged app failed to start with `VK_ERROR_LAYER_NOT_PRESENT` on a machine with the Vulkan SDK or Homebrew validation layers. It now enables the layer only with `-Dawake.vulkan.validation=true` or `AWAKE_VULKAN_VALIDATION=1`, which the Gradle run and test tasks set, and starts without it if a requested layer fails to load.
- **Projects keep opening once the engine reaches alpha.10.** `AwakeProjectValidator.isCompatible` compared pre-release tags as text, so `0.1.0-alpha.10-SNAPSHOT` sorted before `0.1.0-alpha.4` and an engine at alpha.10 refused every project requiring alpha.4 or later. Numeric parts now compare as numbers, and a `-SNAPSHOT` sorts just below the version it leads to.
- **Clipmap terrain no longer cracks between rings.** Levels had an odd number of cells, holes a fixed size and centres snapped each on its own grid, so a ring's border never met the level inside it: from above, 1937 of 25600 pixels in the seam test were background. `TerrainClipmapConfig.ringResolution` now counts cells (vertices are one more), each level's corner snaps to the next level's grid so the two share their border vertices, the edge morph runs in world space onto those vertices, and a coarse level discards what the finer one covers. A custom terrain fragment stage must call `terrainClipmapDiscardUnderFinerRing(terrain)`; ASL rejects one that does not.
- **Alpha-masked meshes cast shadows.** The masked shadow-depth shader discarded every fragment: `discard()` called from inside an `iff` lands in the enclosing block, ahead of an empty `if`. It now uses the new `AslFragmentBuilder.discardIf(condition)`, and `discard()` from inside a nested block throws instead of compiling.
- **`SceneTransform.rotation` is documented as radians.** Its KDoc said degrees, but the runtime passes it unconverted to `Mat4.setEulerTRS`, which takes radians applied X, then Y, then Z; Studio edits and saves it as radians too. A scene written in degrees rendered at the wrong angle.

## [0.1.0-alpha.9] - 2026-09-28

### Added

- **Scene files can author blended meshes.** `SceneMeshRenderer.transparent` (default `false`) serializes and reaches `MeshRenderer.transparent`, so a scene can draw water or glass in the transparent pass, blended by its material's alpha.
- **Render debug views.** Set `WorldDebugSettings.renderDebugView` (or `EnvironmentUniforms.debugView`) to make every scene shader draw a diagnostic instead of its lit colour: `WorldNormals`, `LinearDepth` (view distance over the far plane), `ShadowVisibility` (what the shadow lookup returns, tinted by the cascade that answered and grey past the shadow distance), `Albedo`, and for layered terrain `LayerWeights`, `DominantLayer` and `Lightmap`. `shadowMapViewContentFeature()` adds `ShadowMap`, which shows one layer of the real shadow depth array (`renderDebugLayer`; negative shows an empty map). `SceneViewLighting` compiles a world's lighting and cascades for a camera other than the primary one, so an editor preview lights and shadows like the main view. One shared `debugViewColor` in the shader pack decides what each view shows; backends only forward the pass's `GpuDebugView`, and the lit image is pixel-identical when the view is `Off`. **Breaking:** the lit, textured and terrain uniform blocks end with a new `debugView` field (`LitShadow` is 176 floats, `PbrTextured` 96), `TexturedUniformLayout` now re-exports `MaterialUniformLayouts.PbrTextured`, `EnvironmentUniforms.toGpuState` takes the camera's far distance, and `terrainShadowSampling` returns `CascadeShadowSampling`.
- **Physics contacts reach every system that wants them.** `PhysicsSystem` now drains the world's contacts after each step and republishes them as `contacts`, a list of `PhysicsContact` pairs with the entities their bodies belong to, readable by any number of systems registered after it; `entityFor(handle)` is public. Draining every step also stops the contact buffer growing in a game that never reads it. **Behaviour change:** code that called `PhysicsWorld.drainContacts` itself while a `PhysicsSystem` runs now receives nothing and should read `PhysicsSystem.contacts`. The engine showcase's goal zone moved over, and its linear body-to-entity scan is gone.
- **Terrain receives cascaded shadows.** The base and layered terrain surfaces sample the engine's shadow map, lit by the scene's own sun instead of a fixed default; the layered surface dims its bake toward the ambient floor in shadow. Behind it, content features can read the frame's shadows: `GpuPassInput` carries `cameraForward` and `shadowCascadeData`, `RenderFrameContext` exposes `passInput`, `shadowCascades` and `engineBinding(pipeline, semantic)`, a runtime-attached pipeline gets each engine-owned group its shader declares, and an engine with no shadow pass binds a placeholder shadow map. **Breaking:** `TerrainShader` and `TerrainLayersShader` are now `terrainShader(clipSpace)` and `terrainLayersShader(clipSpace)`, since shadow sampling depends on the backend's clip space.
- **`SceneManager` loads scenes with a caller's components.** `SceneManager(world, componentRegistry)` adds the registry's bindings to the globally registered ones on every load, so a kit's or a game's scene components (for example `registerAiBehaviors()`) load through `SceneManager` without being registered globally first. The one-argument constructor is unchanged.
- **Node graph canvas.** New module `com.awakekt.awake.ui:node-graph-canvas`: `NodeGraphCanvas`, a controlled editing surface for `:awake:node-graph` documents. Nodes render as a header, one row per port pair and a caller-owned body slot; wires are curves coloured by port type. Dragging between ports connects or reconnects, dragging nodes moves the selection, Shift/Ctrl-drag box-selects, the wheel zooms around the pointer, and every edit is reported as a `NodeGraphIntent` for the caller to apply. Zoom scales node content through density, so text stays sharp and controls are hit where they are drawn; labels hide below a legible zoom, and idle wires redraw retained meshes.
- **Find entities by name.** `EntityNames(world).find(name)` returns the live entity carrying that `Name`. Hits are checked on use and misses rebuild the index once, so an existing name costs a map lookup, renames are followed and destroyed entities are never returned.
- **Blueprint runtime.** New module `com.awakekt.awake:blueprint` runs event-driven game logic stored as node-graph documents. Node types are Kotlin objects carrying their `NodeSpec` (`EventNode`, `ActionNode`, `PureNode`, `LatentNode`), each marked `Effect.Logic` or `Effect.Presentation`. `BlueprintCompiler` turns a validated graph into a `BlueprintProgram`, rejecting data cycles and execution loops with no wait. `BlueprintInterpreter` fires events, polls waits each tick, and reloads an edited graph keeping matching variables. Runs allocate nothing. `CoreNodes` provides On Start, Branch, Delay, Add, Greater and typed variables.
- **Blueprints run on scene entities.** New module `com.awakekt.awake.scene:blueprint`. A `blueprint` scene component (graph path plus variable overrides) becomes a `BlueprintComponent`, and `BlueprintSystem`, registered in the fixed phase after `PhysicsSystem`, starts each blueprint, polls its waits and fires sensor events from the step's contacts. New nodes: On Sensor Enter, On Sensor Exit, Self, Find By Name, Destroy and Play Animation. `BlueprintSystem.reload(path, graph)` swaps an edited graph into running instances, and a step with nothing to do allocates nothing. Alongside: `BlueprintInstance.setVariable(name, value)`; `PhysicsSystem.destroyBody(world, entity)`, which frees a body before its entity is destroyed; and a `Delay` triggered again while it waits now restarts, instead of counting down twice as fast and continuing twice.

### Fixed

- **`releaseCut` never re-cuts or silently restarts a version.** Its tag lookup swallowed git failures, so a git that failed read as "no tags on this channel" and the cut restarted at `.1`: a dry run proposed the already-published `v0.1.0-alpha.1` instead of `v0.1.0-alpha.9`. The cause was a Gradle daemon first started by a git hook, which keeps the hook's `GIT_DIR` and hands it to every git it spawns. The task now clears git's repository-locating variables, fails with git's own message when a lookup fails, and refuses to cut a tag that already exists.
- **Oversized corner radii shrink together, as CSS border-radius does.** `RoundedCornerShape` clamped each corner to half the short side, which kept `rounded-full` pills symmetric but cut a one-ended full radius such as `rounded-t-full` to half height. All radii now scale by the one factor that fits the tightest side, which keeps pills and circles exact and lets a one-ended radius reach the whole short side. This fixes `PaintTest.roundedCornerShapeClampsAdjacentCornersLikeCompose`, which had been failing on `main`.
- **Unchanged text is no longer re-shaped every frame.** Each `Text` node keeps its last measurement and glyph run on its retained paint node and reuses them while the text, style, font and density are unchanged, and `TextStyle.then` returns an existing instance when a merge changes nothing. `TextFrameProbe` (60 labels) went from 293 KB to 126 KB allocated per frame (layout 110 KB to 5 KB) and from 0.29 ms to 0.16 ms, with rendered output unchanged; the node-graph canvas editor view fell from 451 KB to about 260 KB.
- **WebGPU consumers resolve the tested wgpu4k builds.** The WebGPU backend's published metadata named the floating io.ygdrasil SNAPSHOTs, so a consumer picked up webgpu-ktypes build 4, which does not match wgpu4k, and its web app stalled before the first frame with no error. The backend now publishes the pinned builds as dependency constraints.
- **WebGPU startup failures reach the browser console.** `GraphicsEngine` reports a failed start through `Log`, which has no sink on the web by default, so a failure left a black canvas and an empty console. `launchWebGpuGame` now installs a `PrintLogSink` at warning level when the app has not installed one.
- **The hosted engine showcase renders again.** The samples site shipped only each showcase's `index.html` from its processed resources, so models, scenes and textures were missing and the engine showcase stayed black. The site now ships every processed resource, caches only content-hashed `.wasm` as immutable, and has a redesigned index page matching awakekt.com.

## [0.1.0-alpha.8] - 2026-09-27

### Added

- **Terrain content can draw only surfaced terrains.** `TerrainContentSystem(surfacedOnly = true)` skips terrains without a `TerrainSurfaceReference`, so an editor can keep its own mutable preview for those and still draw layered terrain through the content system.
- **Node graph documents and registry.** New module `com.awakekt.awake:node-graph`: the shared model behind blueprint, state-tree and shader graph editors. `NodeGraph` documents with ordered edges, `GraphKind` rules for port compatibility and cycles, an explicit `NodeRegistry` of documented `NodeSpec`s with a JSON catalogue export, validation that reports every `GraphIssue` by node id, and a strict `NodeGraphJson` codec whose `load` refuses any graph that does not validate.
- **glTF images embedded in GLB buffers.** `GltfImage.bufferView` is parsed and material textures resolve their encoded bytes from the GLB binary chunk, with range checks, in addition to data URIs and pre-fetched external URIs.

### Fixed

- **Release tooling finds every release tag.** `releaseCut` now reads all `v*` tags instead of those reachable from `HEAD`, so a squash-merged release commit no longer makes it re-cut an existing version; the snapshot job skips publishing when a just-pushed tag makes the commit resolve to a release version.
- **WebGPU desktop renders again.** A `webgpu-ktypes` snapshot published on 2026-09-26 broke binary compatibility with the only `wgpu4k` snapshot there is, so every WebGPU desktop run failed with `NoSuchMethodError` once Gradle refreshed its snapshot cache. Every io.ygdrasil SNAPSHOT, including transitive requests, is now pinned to the builds published together on 2026-07-16.
- **Headless presentable Vulkan rendering is valid.** `VulkanEngine` boots a `HeadlessSurface` into stand-in images that end each frame in `PRESENT_SRC_KHR`, but headless devices never enabled `VK_KHR_swapchain`, so every render pass using that layout was invalid (VUID-VkAttachmentDescription-finalLayout-parameter). Headless devices now enable it when offered, and presentable mode refuses a device without it. Teardown no longer calls `vkDestroySwapchainKHR` with no swapchain, which crashed the JVM on such a device, and `readPresentedPixels` reads the image `draw` wrote instead of inferring it from the frame slot, which was wrong whenever frames in flight differed from the image count.

## [0.1.0-alpha.7] - 2026-09-27

### Added

- **Layered terrain kit.** New optional module `com.awakekt.awake.kit:terrain-layers`: an unbounded layer palette (`*.terrainpalette.json`), a top-4 index/weight control map per texel (`TerrainControlMap.reduce`, binary `*.terrainctl`), and `TerrainLayersShader`, which merges the four control texels around each pixel into four slots and height-blends only those, so per-pixel cost does not grow with the palette. `terrainLayersSurface()` returns the `TerrainSurface` for `terrainContentFeature`, and `TerrainLayersSurfaceProvider` loads one from project files when a scene terrain's `surface` names `awake.terrain.layers`. An optional `TerrainLightmap` (`*.terrainlight`) carries baked lighting: RGB multiplies the surface with 128 as x1, and alpha hands lighting over from the scene's sun to the bake.
- **Terrain in scene documents.** `"component": "terrain"` is a Core binding (`TerrainBinding`, installed by `DefaultSceneComponentResolvers`): embedded heights plus an optional `surface: { provider, version, payload }` that Core preserves unread. `TerrainContentSystem` draws every `TerrainComponent` entity through `ContentFeatureHost`, resolving its surface with the matching `TerrainSurfaceProvider` or falling back to base shading, and detaches it when the entity or component goes away. **Breaking for hosts that registered their own `terrain` scene class:** two classes with one serial name make every scene encode and decode throw.
- **Runtime content features.** Cast the app's renderer to `ContentFeatureHost` and `attachContentFeature(source)` to draw a content feature that arrives with a scene; `detach()` frees its pipeline, textures and geometry after the GPU is idle. Both engines build plan and attached features through one shared path, which now uploads arrayed and cubemap content textures with their real layer count. Features that sample scene depth, or share a registered pipeline spec, are rejected at attach.
- **Pluggable terrain surface shading.** `terrainClipmapVertexStage()` is the shared clipmap vertex stage; a surface shader adds only its fragment stage and bindings from `TERRAIN_SURFACE_FIRST_BINDING`. `terrainContentFeature` now reads bindings from the `ShaderSet` it is given and accepts `surfaceTextures`. `TerrainShader` renders pixel-identically. **Breaking:** removed the unused `TerrainSplatShader`, `PackShaderSets.TerrainSplat`, `TerrainMaterial`, and `TerrainComponent.material`.
- **Manual Release workflow.** Actions → Release opens a `chore(release): cut <tag>` PR via `releaseCut`; merging it tags the merge commit and triggers Publish. Workflow and job names are shortened (CI, Verify, Publish, Docs, Deploy Samples…).
- **Configurable Spotless SPDX license header.** `com.awakekt.awake.plugin.spotless` reads `awake.spotless.spdxLicense` (default `Apache-2.0`) so consumers under another license, such as Awake Studio's `AGPL-3.0-only OR LicenseRef-AwakeKt-Commercial`, keep their own file headers.
- **Cross-platform Awake application icon.** Applied the approved Ember Signal mark to the Engine Showcase Android adaptive launcher icon, iOS AppIcon catalog, Desktop taskbar/Dock identity, and Wasm favicon; the SVG master and 1024px PNG now ship with the sample assets.
- **Project content validation and browser index tooling.** Added the generic `ProjectContent*` contract, metadata-only `ProjectIndex`, streaming-safe path and digest checks, and Gradle tasks for project validation, asset-lock verification, lock generation, and index generation.
- **Stable project-content diagnostics.** Added machine-readable `ProjectIssueCode` values and path-aware `ProjectContentIssue` details while preserving the existing string validation APIs.
- **Published Awake Gradle plugins.** Publish only the `application`, `library`, `project-content`, `shader-pipeline`, `dokka`, `detekt`, and `spotless` plugin IDs to Maven Central, using the engine's tag-derived version and keeping internal convention markers private.
- **Dependency-aware Vulkan release family.** Version the Vulkan renderer, raw bindings, and Android JNI bridge together, independently from the shared Core release train, with exact stable Core dependency pins.
- **Stable shadow cascade transitions and camera-centered debug grid.** Blend shadow sampling across cascade edges, keep third-person camera poses synchronized with their targets, and center the infinite grid overlay on the active camera.
- **Public editor plugin contract and project plugin SHA-256 pin.** Added public `:awake:editor:contract` module with `EditorPlugin`, `EditorProvider`, `PluginManifest`, and `AssetConverterPlugin` contracts for open-source and commercial plugin authoring. Added optional `sha256` content integrity pin and default version semantics to `AwakeProjectPluginReference` in `:awake:project`.
- **Three-layer architecture governance and Awake Core Editor skill.** Formally codified the 3-layer architecture (Awake Core, Awake Core Editor, Awake Studio Pro) across agent guides and skills, adding the new `awake-core-editor` skill.

### Fixed

- **Terrain ends at its heightmap.** Clipmap rings reach past the heightmap and its sampler repeats, so terrain tiled across the view; vertices beyond the footprint now collapse onto its edge. Heightmap UVs also put the first and last samples on texel centres, where they had been half a texel off with edge heights averaged against the opposite edge.
- **Vulkan's first frame with shadows off is valid.** The scene pass binds the shadow map whether or not the depth pass runs, so a renderer whose first frame had shadows disabled sampled layers still in `UNDEFINED` (VUID-vkCmdDraw-None-09600). Every layer is now initialised once before that first bind.
- **Vulkan shadows no longer drop out on macOS.** MoltenVK up to 1.4.1 ignores render-pass subpass dependencies, so the scene pass could sample a shadow-map layer the depth pass was still writing and render a frame with no shadows. Each depth pass now records an explicit memory barrier, which also makes `RendererHeadlessCascadedShadowTest` deterministic (about 1 in 5 processes failed before).
- **Core releases no longer blocked by WebGPU.** Maven Central rejected the release because the WebGPU backend depends on a wgpu4k SNAPSHOT. `:awake:backend:webgpu` is now held out of Central releases while wgpu4k is a SNAPSHOT; it still publishes as a snapshot.
- **Texture arrays get mip chains.** `TextureAsset.mipChain()` downsamples each array layer on its own, and both backends upload every level of an array texture instead of level 0 only, so tiled terrain layers sampled with implicit LOD no longer shimmer at a distance. Cubemaps stay single-level.
- **Vulkan backend no longer ships LeakCanary.** `vulkan-android` declared `leakcanary-android` as a runtime dependency, so every consumer's signed release crashed on launch with "LeakCanary in non-debuggable build". Apps that want it add it as `debugImplementation`; the engine showcase does.
- **Android renders again.** The published `shader-compiler` Android AAR never contained `libawake_naga.so`, so every WGSL shader failed and Android apps showed a black screen; Publish now builds it with cargo-ndk and `verifyPublishedArtifacts` rejects an AAR without both ABIs. The Vulkan debug-messenger callback also looked up the `JNIEnv*` of the thread that created the instance, which aborted the render thread the first time a validation message fired off the main thread; it now uses the calling thread's env.
- **Snapshot publication after verification.** Ensure successful `main` verification cannot silently skip publishing the Core snapshot consumed by downstream projects.

- **Project-content Gradle task validation.** Declared explicit cache policies for project
  validation, asset-lock, and project-index tasks so Gradle plugin validation and Maven
  publication remain reliable on the supported Gradle version.
- **Vulkan publication scope and native verification.** Publish only the three Vulkan family
  modules, and verify the final desktop JAR contains both supported native library paths before
  Maven publication.
- **KMP desktop run-task runtime classpath resolution.** Resolve desktop runtime dependencies from
  the KMP main compilation's named runtime configuration and report missing target configuration
  clearly instead of failing with an absent Gradle provider.
- **Third-person camera orbit smoothing.** Restored delta-time-based eye easing without changing other camera modes.
- **Retained unchanged UI draw runs across frames.** Include active clip context in cached geometry keys, skip exact clipping for meshes wholly inside a clip's safe interior, and let vector painters and cached borders reuse staged geometry by stable identity rather than hashing every vertex each frame.
- **Camera-motion shadow shimmer and ultrawide cascade under-coverage.** Select and blend cascades by camera depth over a fitted overlap, use the live viewport aspect for cascade fitting, and make the shadow-frustum overlay match the active camera and viewport. Restrict the extra PCF sampling to split transition bands instead of broad light-map edges.

### Changed

- Consolidated maintained repository verification and release tooling into Gradle, retaining Python only for visual and vendored tooling.
- **Target-aware CI and Naga caching.** Classify Core, iOS, Vulkan, Web/Wasm, and docs-only changes before platform jobs, skip unrelated native matrices, and cache desktop and iOS Naga builds with Rust and source-aware keys.
- **Publication inventory and release dependency validation.** Inventory every published coordinate and direct API/runtime edge, and reject snapshot dependencies instead of rewriting them to unverified stable versions.
- **Separated agent-skill ownership from Awake product tooling.** Public `awake-*` guidance now ships from the Apache-2.0 `awake-agent-skills` bundle, Studio scoring and creative roles ship from a private `studio-*` overlay, vendor KMP content is pinned upstream, and UI/icon tooling now lives in stable `tools/` paths independent of agent installation.
- **Canonicalized `:awake:project` and retired `V1` suffixes.** Replaced legacy `AwakeProjectManifestV1`, `AwakeProjectPluginReferenceV1`, `AwakeAssetsLockV1`, `AwakeAssetLockEntryV1`, and `AwakeProjectV1Validator` with canonical `AwakeProjectManifest`, `AwakeProjectPluginReference`, `AwakeAssetsLock`, `AwakeAssetLockEntry`, and `AwakeProjectValidator`. Fully retired deprecated legacy `AwakeProject.kt` manifest format.
- **Retired deprecated editor shims.** Removed deprecated `EditorProviderKind.Dock` from `:awake:editor:contract`.
- **Stripped redundant `Editor` prefix from `:awake:editor:contract` plugin/provider types.** Canonical names are now `PluginId`, `PluginMetadata`, `PluginRegistry`, `PluginLifecycle`, `ProviderId`, `ProviderMetadata`, `ProviderConfiguration`, `ProviderCodec`, `ProviderRegistry`, `ComponentProvider`, `AssetProvider`, `EnvironmentProvider`, `AnimationProvider`, `BuildProvider`. Old `Editor*` names remain as deprecated typealiases for zero-churn migration.
- **Removed `contributesDockTab` and `dockTabTitle` from `PluginManifest`.** These stale fields from the retired `EditorDock` era have been fully removed. Bottom panel contributions are now declared by plugin `createProviders()` returning `ProviderKind.BottomPanel` providers.
- **Cleaned the public MkDocs site.** Replaced internal development-document passthroughs with
  curated public pages, removed speculative editor/roadmap pages, aligned examples with
  `0.1.0-alpha.4`, added a compiler-verified Button example with a reviewed render capture,
  documented the public example workflow, added release-coupled versioned docs with a Material
  version selector, and made the Cloudflare deployment fail on strict-build or deploy errors.

## [0.1.0-alpha.4] - 2026-09-19

### Added

- **Named scene entity creation helper.** Added `SceneAppLifecycleRuntime.findOrCreateEntity(name)` to retrieve or instantiate and label missing scene entities during runtime authoring and testing.
- **Autonomous 3D scene dirtiness tracking and frame loop pacing override.** Added `isRealtimeProvider` and `isDirtyProvider` parameters to `RenderSystem3D` and `defaultInfrastructureSystems` with allocation-free primitive camera and viewport change tracking, enabling the engine to bypass full scene re-planning and draw call compilation on static viewports. Added `frameRateOverride` to `DesktopFrameLoop` to allow host applications to dynamically rebind target frame rates at runtime.
- **Window focus throttling and background frame rate pacing.** Added `throttleCpuWhenNotForeground` and configurable `backgroundFrameRate` to `WindowConfig` and `AppWindowConfigBuilder`, bound GLFW window focus querying (`glfwGetWindowAttrib(window, GLFW_FOCUSED)`), and wired focus-adaptive frame pacing into `DesktopFrameLoop` and `VulkanDesktopHost` to eliminate unnecessary CPU/GPU battery drain when unfocused.
- **MutableState and mutableStateOf restoration.** Restored `State<T>`, `MutableState<T>`, and `mutableStateOf(initial)` with Kotlin property delegation (`getValue`/`setValue`) and destructuring operators in `:awake:compose:runtime`, providing standard, idiomatic local state containers for `remember` across all Compose UI targets.
- **Sealed skybox mode hierarchy, cubemap orientation, and clip-space math helpers.** Refactored `Skybox.mode` into a sealed interface (`Procedural`, `Cubemap`, `SolidColor`) with backward-compatible property delegates, introduced `CubemapFaces` orientation definitions and 90° FOV lens projection, added `unprojectFarRay` and `unprojectClipToWorld` in `shader-dsl`, and scoped the clip space decision matrix to engine skills.
- **Cubemap texture and runtime shader pipeline.** Added 6-face cubemap metadata validation and packing in `TextureAsset`, cubemap binding contracts in `ResourceBinding`, ASL `textureCube` resource bindings and `textureSampleCube`/`textureSampleLevelCube` builtins in `shader-dsl`, hardware cubemap image view creation for Vulkan (`VK_IMAGE_VIEW_TYPE_CUBE`) and WebGPU (`GPUTextureViewDimension.Cube`), and `SkyboxCubemapShader` with uniform layout packing in `shader-pack`.
- **Pull request governance and milestone policy.** Added CI verification and agent workflow invariants requiring every pull request to be linked to an active milestone and to update `CHANGELOG.md`.
- **Version-one project contract.** Added the shared `awake.project.json` and `assets.lock.json` models and structural validators for Core and Studio consumers, including project-relative path, plugin, version, and SHA-256 pin checks.
- **Portable SHA-256 content digest.** Added a multiplatform Core IO digest utility for reusable content identity and integrity verification.
- **Composable project storage and streaming integrity.** Added a writable Core overlay filesystem,
  chunked SHA-256 sessions, and a versioned IndexedDB Wasm filesystem that keeps browser metadata
  separate from lazily loaded file content.
- **Bounded browser storage migration.** Existing v1 Base64/localStorage files are migrated once
  into the v2 IndexedDB metadata/content stores with file-count and byte-size limits.
- **Derived project compatibility metadata.** Canonical project manifests can declare an explicit `minEngineVersion`, while the published project module exposes the Git-derived engine version without hardcoded defaults; Central releases now publish automatically after validation.

### Fixed

- **Rotation-invariant shadow cascade stabilization and allocation-free chart rendering.** Derived analytical bounding spheres for frustum cascades in `ShadowCascadesMath` to eliminate projection scaling wobble on camera rotation/orbit, corrected cascade containment and border PCF clamping in `AslShadowShaders`, and added `showDots = false` option with direct path evaluation in `ShadcnLineChart` to eliminate per-frame allocations and canvas draw call spikes.
- **Selective shadow depth pre-pass recording.** Restrict Vulkan and WebGPU depth pre-pass loops to only active directional cascades and authored subpasses instead of clearing and submitting passes across all 28 shadow array layers each frame.

## [0.1.0-alpha.3] - 2026-09-18

### Added

- **Slider drag completion callback.** Core sliders now expose a single release/keyboard completion
  seam so editors can preview values continuously and commit one undoable change.
- **Core capability extraction foundation.** Added rooted asynchronous file I/O, atomic write and
  transaction contracts, asset sources, a bounds-checked binary reader, pure scene byte codecs,
  and the portable project manifest module for Core and Studio consumers.
- **Core platform storage adapters.** Added rooted Android and iOS file storage with atomic
  publication and watches, plus durable browser virtual storage for Wasm; Studio now uses the Core
  Wasm adapter instead of an in-memory-only runtime.
- **Milestone 3 physics and character-controller coverage.** Moved the real Jolt controller suite
  to multiplatform `commonTest`, including heightfield terrain, ground probes, traversal, and
  moving-platform behavior.
- **Named skeletal sockets.** glTF bone names are preserved, skeletons support name lookup, and
  socket attachments now follow joint translation and rotation in world space.
- **Pure terrain asset codecs (`RawHeightmapCodec`, `SplatWeightMapCodec`).** Added pure byte codecs
  and `TerrainMaterial` contracts in `:awake:asset:terrain` for synchronous, headless terrain decoding
  and splat painting.
- **Audio playback lifecycle and dynamic spatial positioning.** Extended `:awake:core:audio` with
  playback state transitions, looping, volume and pitch modulation, and dynamic 3D listener-relative
  spatial updates.
- **shadcn Tabs Ghost variant.** Added containerless `Ghost` variant for toolbar and editor tab bars
  without heavy background containers.

### Fixed

- **Context menu secondary press dismissal.** Added auto-dismissal on secondary click outside menu
  bounds to match native desktop interaction paradigms.
- **Shadow map texel snapping.** Stabilized directional light cascaded shadow map texel snapping to
  eliminate sub-pixel swimming artifacts during camera movement.
- **Desktop Vulkan environment rendering.** Map the generic environment packet into Vulkan's frame
  context so the shared skybox feature no longer inherits the default hidden-environment flag.
- **Button-group divider visibility.** Use a readable interaction divider tint instead of the 10%-alpha
  page border token, which disappeared in dark Studio overlays.
- **Render environment boundary.** Make the lowered `GpuEnvironmentState` the only environment
  value exposed to render features and remove the duplicate compatibility projections.
- **Desktop Vulkan scene picking.** Align CPU viewport projection and picking rays with Vulkan's
  positive-height Y convention so model and gizmo hover/click coordinates match rendered pixels.
- **shadcn spinner motion.** Cached the tessellated arc and rotate the stable mesh per frame, removing
  per-frame stroke tessellation that caused uneven animation under load.
- **shadcn spinner placement.** Route the rotated cached mesh through the Canvas node's placement
  path so padded and nested spinners remain visible instead of rendering at the root origin.
- **UI showcase input forwarding.** Scene runtime frames now preserve secondary pointer presses,
  pointer modifiers, and key events so context menus and keyboard interactions work in the running
  showcase, not only in isolated component tests.
- **shadcn surface parity.** Matched dark field tinting to `dark:bg-input/30` and changed toast
  surfaces and text to the official Sonner `popover` tokens.
- **shadcn modal geometry.** Centered Dialog and Alert Dialog layers in the window, constrained
  their surfaces with the official responsive gutter/max-width rules, and kept Sheets and Drawers
  pinned to the window when declared inside nested showcase content.
- **UI showcase samples.** Removed React, Next.js, Radix, Stitches, and other web-framework copy
  from Awake UI examples, keeping the showcase self-contained and Awake-specific.

## [0.1.0-alpha.2] - 2026-09-12

### Added

- **Renderer onscreen pixel readout (`Renderer.readPresentedPixels`).** Promoted onscreen pixel
  readout into the backend-neutral `Renderer` contract (`awake:engine:render:contract`), implemented
  across both Vulkan and WebGPU backends. Decoupled sample test suites from platform backend casts.
- **Post-processing pass orchestration (`GpuPassInput.postPasses`).** Extended generic `GpuPassInput`
  with `postPasses: List<GpuSubPass>`, wiring Vulkan and WebGPU pass executors to run post-processing
  draws (bloom, tone mapping, color grading) after main forward lighting passes.
- **Hardware capability query tier (`GpuDevice.capability`).** Introduced typed optional extension
  querying via `GpuCapability` and `GpuCapabilityKind<T>` on `GpuDevice`, allowing Vulkan extensions
  (e.g., timeline semaphores, bindless descriptors) to be queried safely without breaking WebGPU
  browser compatibility.
- **WebGPU point-light omnidirectional shadow contact parity (`WebGpuPointLightShadowTest`).**
  Configured `HeadlessSceneRenderer` with full `MAX_SHADOW_TARGET_LAYERS` (9 layers: 3 directional
  cascades + 6 point-light cube faces) in parity with `WebGpuEngine`, and added hardware contact
  probe asserting attached point-light shadows without peter-panning.
- **Milestone 2 web hosting and showcase preview deployment.** Automated continuous Cloudflare Pages
  deployments for `demo.awakekt.com` (WasmJs WebGPU 3D Engine showcase & Compose UI gallery) and
  `docs.awakekt.com` (MkDocs documentation site).

## [0.1.0-alpha.1] - 2026-09-12

### Added

- **Frame rate pacing & FPS control (`FrameRateMode`).** Introduced `FrameRateMode` (`Unlimited`,
  `DisplaySync`, `TargetFps(fps)`) in `:awake:engine:platform` allowing explicit frame rate capping,
  display VSync locking, or unlimited rendering across all target platforms.
- **Interactive FramebufferDebugger & render diagnostics.** Added visual render target diagnostics,
  depth/color buffer inspection, and diagnostics tooling to sample showcases.
- **ECS family caching and component storage optimization.** Optimized query family matching,
  sparse set lookups, and system iteration performance.

### Changed

- Deprecated the legacy scene-shaped renderer and offscreen overloads. They remain migration
  bridges during the render architecture cutover and are scheduled for removal at A7; new code
  should submit resolved `GpuPassInput` packets.
- Routed `RenderSystem` scene frames through `GpuSceneFrame.toPassInput`, carrying directional and
  point-light payloads plus planned shadow subpasses through the generic render contract.
- Vulkan's generic packet executor now forwards planned shadow matrices into its existing depth
  pre-pass recorder for onscreen and offscreen submissions.
- Generic Vulkan and WebGPU offscreen packet preparation now consumes the authored lighting payload
  instead of silently falling back to default directional light values.
- Vulkan resolved packets now preserve camera-depth and planned shadow-depth passes by using the
  retained transitional source draws only for depth recording; the resolved packet remains the
  color-path source.

### Fixed

- **Frame loop timing and delta measurement.** Corrected `DesktopFrameLoop`, `AndroidFrameLoop`, and
  `IOSFrameLoop` to measure delta time from frame start to frame start (`currentFrameTime - previousFrameTime`)
  rather than excluding frame execution overhead, preventing runaway / infinite FPS calculations.

## [0.1.0-dev.12] - 2026-09-07

### Changed

- **Platform backend selection simplification (`AppWindowBackend.DEFAULT`).** Enhanced desktop Vulkan host
  (`VulkanDesktopHost`) to accept `AppWindowBackend.DEFAULT` alongside `AppWindowBackend.VULKAN`. Removed
  redundant expect/actual `PlatformBackend` boilerplate across `samples:engine-showcase`, `samples:ui-showcase`,
  and `samples:compose-showcase`, allowing all multiplatform sample entrypoints to rely on framework defaults.

### Removed

- **Commercial Awake Pro duplicate samples in `samples:engine-showcase`.** Removed proprietary showcase drivers,
  scenes, and tests (`ragdoll`, `skinned-ragdoll`, and `streamed-nav`) from the open-source repository. These
  commercial features and workflows now reside in the standalone `awake-pro` repository under
  `com.awakekt.awake.pro.*`.

## [0.1.0-dev.11] - 2026-09-07

### Added

- **Engine Showcase Android runner (`samples:engine-showcase:androidApp`).** Added a native Android application
  runner module (`com.android.application`) hosting a hardware-accelerated `VulkanView(this, application)`
  lifecycle surface. Added `com.android.kotlin.multiplatform.library` target to `samples:engine-showcase` and created
  an `Engine Showcase [Android]` IDE run configuration.

- **Automated framework embedding & IDE run configurations.** Modernized `iosApp.xcodeproj` build phases to invoke
  `./gradlew :samples:engine-showcase:embedAndSignAppleFrameworkForXcode`, bundle common assets, and enforce
  active-architecture compilation. Added standardized IDE run configurations for `Engine Showcase [iOS]`,
  `Engine Showcase [Desktop]`, and `Engine Showcase [Wasm]`.

### Changed

- **Collocated `iosApp` under sample directory.** Relocated `iosApp/` to `samples/engine-showcase/iosApp/` matching
  modern KMP Wizard layout conventions and collocating sample-specific platform hosts with their sample sources.

### Fixed

- **iOS `CAMetalLayer` upside-down presentation & retina resolution.** Fixed inverted render orientation in
  `VulkanMetalView` by applying a vertical scaling transform (`CATransform3DMakeScale(1.0, -1.0, 1.0)`) matching
  UIKit view coordinate space, and configured `metalLayer.contentsScale` to render at native retina resolution.

- **iOS Simulator Vulkan image view allocation in MoltenVK.** Attached `VkImageViewUsageCreateInfo` with
  `VK_IMAGE_USAGE_SAMPLED_BIT` to arrayed image views in `Vulkan.kt`, resolving MoltenVK texture array view creation
  failures on iOS Simulator Apple 2 GPU targets.

## [0.1.0-dev.10] - 2026-09-06

### Changed

- **Migrated coordinates, packages, and namespaces to `com.awakekt`.** Updated Maven group to
  `com.awakekt`, moved package root to `com.awakekt.awake`, and transferred repository to
  `awakekt/awake`.

### Added

- **GitHub issue templates.** Added structured YAML forms for bug reports, feature requests, and
  engine tasks under `.github/ISSUE_TEMPLATE/`.

### Fixed

- **Console log column overlap & text bleeding.** Resolved text collisions in `EditorConsolePanel`
  where long subsystem tags (such as `studio.plugins` and `studio.repository`) overflowed the
  hardcoded 72.dp column and painted over log messages. Increased default tag column width to
  120.dp, added bounds clipping (`clipToBounds()`), and added a vertical column divider.

- **Marketplace dialog extension misclassification and post-install UX.** Fixed an issue where newly
  installed extensions were misclassified as built-in engine subsystems (`RealInstalledPluginCard`)
  without controls. The Installed tab now clearly partitions user-installed extensions from engine
  core subsystems, surfacing full management controls (`Enable`/`Disable`, `Details`, `Uninstall`)
  for extensions, correctly counting installed extensions in the tab badge, and resetting import
  form states cleanly.

### Added

- **Resizable console tag column.** Added interactive horizontal column resizing in
  `EditorConsolePanel`. Users can drag the column divider handle in the header or table rows with
  `PointerCursor.ResizeHorizontal` to smoothly widen or narrow the tag column between 50.dp and
  320.dp.

- **Studio execution & transaction logging.** Connected all Studio state intents, plugin lifecycle
  events, repository mutations, and file operations to Awake's `LogRingBuffer`, surfacing a
  complete, frame-indexed audit trail in the bottom Console dock tab.

- **Floating toast notifications (`ShadcnToaster`).** Wired `ShadcnToastState` and `ShadcnToaster`
  into Studio root, displaying non-modal, transient bottom-right toasts on scene save, project
  folder open, asset import, and extension lifecycle changes.

- **Dynamic project directory scanning (`StudioFileOps`).** Added multiplatform `StudioFileOps`
  contract and desktop file walker to scan opened project directories, populating project assets
  dynamically into the Studio Files dock tab.

- **Real scene serialization on save.** Connected "Save Scene" and "Save Scene As..." in the Studio
  top bar to live `SceneLoader.fromWorld(world)` and `SceneLoader.encode(document)`, serializing and
  writing the scene document directly to disk.

- **Extension manifest validation.** Added schema validation for imported plugin manifests (`id`
  format, `version` semver, non-blank `name`), logging validation issues and displaying inline
  errors.

- **Directory selection and file save dialog support (`EditorFileChooser`).** Added `openDirectory`
  and `saveFile` APIs to multiplatform `EditorFileChooser`, backed on desktop by out-of-process
  native platform dialogs (`osascript` on macOS, `zenity` on Linux, and PowerShell/.NET
  `FolderBrowserDialog`/`SaveFileDialog` on Windows) with automatic fallback to AWT `FileDialog`.
  Wired into Studio toolbar as "Open Project Folder..." and "Save Scene As...".

- **Studio extension plugin enable/disable lifecycle.** Added `isEnabled` flag,
  `PluginLifecycleListener`, and `setEnabled(id, enabled)` to `StudioPluginRepository` and
  `DefaultStudioPluginRepository`. Studio now persists disabled plugin state across sessions and
  dynamically registers or tears down dock tabs and engine systems without requiring full
  uninstallation. Added Enable/Disable toggles to `StudioMarketplaceDialog`.

- **Dependency injection migration for Studio dialogs.** Migrated `StudioSettingsDialog` and
  `StudioLicenseDialog` to ambient DI (`rememberResolveOrNull<StudioThemeState>()` and
  `rememberResolveOrNull<AwakeLicenseRegistry>()`), registering singletons in `StudioDi.kt` and
  refactoring `StudioLicenseDialog` to an immutable `rememberReducerStore`.

### Changed

- **`StudioStore` refactored to `ReducerStore`.** Migrated `StudioStore` to delegate to Awake's
  canonical `reducerStore`, adding pure intents `StartPlay`, `StopPlay`, and `ReloadFixture` while
  preserving synchronous effect draining for frame-loop fidelity.

## [0.1.0-dev.9] - 2026-09-05

### Added

- **Native file chooser abstraction (`EditorFileChooser`).** Added multiplatform file chooser
  contracts to `:awake:editor:core` with an AWT-based native desktop implementation and platform
  fallbacks, wired into the Studio top bar (`File -> Open Scene...`, `File -> Import Asset...`)
  and the Marketplace import dialog.

- **Marketplace import redesign and JSON viewer.** Overhauled the Marketplace dialog with dedicated
  *Package* and *Bundle* tabs, native filesystem browsing, and read-only manifest JSON inspection.

- **Two-way `SceneComponentBinding` decouples scene export from hardcoded domain types.**
  Previously, `SceneLoader.fromWorld` hardcoded manual checks for each known component type,
  forcing all domain components to be compiled directly into the scene runtime.
  `SceneComponentBinding<C, S>`
  now defines a pluggable export and resolution contract on `SceneComponentRegistry`. Features and
  plugins
  can register custom bindings that export live ECS components into persistent scene records and
  reattach
  them during instantiation.

- **`snake_case` serialized component names.** All `@SerialName` discriminators (`mesh_renderer`,
  `spin_control`, `pbr_material`, `prefab_link`) and property names (`cull_mode`, `prefab_guid`,
  `is_root`) now use idiomatic `snake_case`. A lightweight pre-decode pass in `SceneLoader` and
  `@JsonNames` properties preserve 100% backward compatibility for existing `.scene.json` files.

- **Independent `awake:navigation`, `awake:ai`, and `awake:ai:behavior` modules.** Pathfinding
  (`awake:navigation`), behavior primitives (`awake:ai`), and starter NPC behaviors
  (`awake:ai:behavior`) are promoted from `awake:scene:ai` into standalone first-class modules,
  allowing games to use navigation and decision trees independently without pulling in scene
  rendering or UI runtimes.

### Changed

- **Modernized Studio app bootstrap & theme.** Migrated `StudioApp` to the canonical `app` and `ui`
  DSLs,
  set the default theme base to Zinc, and replaced deprecated `shadcnSurface` containers across the
  editor dock, toolbar, and viewport chrome with scoped `ShadcnCard` and foundation primitives.

- **Scene inspector card container layout.** Enclosed inspector component properties in card
  containers
  with consistent padding and hierarchical layout.

### Fixed

- **`BasicTextField` text bleed in single-line inputs.** Fixed overlapping text rendering in
  single-line
  dialog inputs by enforcing line-height bounds and layout clipping.

- **Every showcase camera glided down through its own scene on activation.** `EngineShowcaseLoader`
  derives a rig's yaw and pitch from the authored eye, and `CameraSystem` places the eye *behind*
  the pivot — so the vector from centre to eye is the negation of the forward vector, and both
  angles pick up a sign from that. The hand-written derivation had both the wrong way round, aiming
  each camera at the mirror image of its shot: for the heightfield showcase, `(5, 10, 10)` became
  `(-5, -9, 10)`, below the terrain. It did not look like a mirrored view because the orbit mode
  eases the eye toward where the rig says it belongs, so what a viewer saw was the camera sliding
  smoothly downward over about a second. The inverse now lives beside the forward as
  `CameraRig.aimAt`, round-tripped against `CameraSystem` itself rather than against the algebra.

- **A terrain mesh and its heightfield collider were cut on opposite diagonals.** Each grid quad is
  drawn as two triangles, and which diagonal splits it decides the surface *inside* that quad.
  `gridTriangleIndices` cut on the anti-diagonal while Jolt's heightfield takes its diagonal through
  `(x, z)`–`(x+1, z+1)`, so the ground being drawn and the ground things landed on were two
  different surfaces — up to 0.16 apart on the showcase terrain, which is a box resting visibly
  above or below the slope it stands on. Matching Jolt brings it to 0.003, and both consumers of the
  helper are terrain, so the matching diagonal is the right default rather than a special case.

## [0.1.0-dev.8] - 2026-09-01

### Added

- **The editor has pixel baselines.** It had no visual coverage at all, which the icon-role change
  made concrete: moving the hierarchy's header actions from filled 20px glyphs to 24px outlines
  changed what is on screen and the whole suite stayed green. The console (at every severity) and
  the tool palette (the four transform glyphs) are pinned through the same `assertMatchesBaseline`
  the shadcn corpus uses -- same zero-tolerance compare, same recording workflow, no second tool.
  Repointing one icon role moves 98 pixels while the icon invariant tests stay green, which is the
  class of defect only the pixels can see.

- **The editor's glyphs are named by role and drawn from one tier.** The scene hierarchy drew its
  header actions as filled 20px mini glyphs directly above rows drawn as 24px outlines -- two
  weights adjacent in one panel, which nothing had decided: each icon was picked at its call site
  from whichever tier carried the glyph, and five roles used mini only because their outline
  variants had never been vendored. `EditorIcons` names 30 roles on `Outline24`, so switching the
  editor between outline and filled is editing one file. That is the only variant switching offered
  -- Heroicons' tiers do not carry the same glyphs (19 of 57 exist in both mini and outline), so a
  general `icon(name, variant)` lookup would miss for most of the set at runtime; a role wanting a
  tier the glyph lacks is a compile error instead.

- **Solid bodies can report their contacts.** Only sensors did, and a sensor is the thing you pass
  *through* -- so hit sounds and impact damage had nothing to hang on. `setContactReporting` opts a
  body in, per body: Jolt offers every touching pair in the scene on every step, so reporting them
  all would queue an event per pair per frame for a settled pile of crates that nothing reads.

- **Heroicons are generated from vendored SVGs at build time.** `HeroIcons.kt` was 3,082 lines
  pasted in by hand, with provenance as an assertion rather than a fact: each glyph named an
  upstream source nothing checked, no Heroicons version was recorded anywhere, and the file had been
  hand-edited despite saying not to. The 78 SVGs are now vendored at a pinned **v2.2.0** and the
  build produces the Kotlin. All 78 reproduce byte-identically from that release -- both the proof
  that the pipeline reproduces what shipped, and the answer to the version question that was the
  only unrecoverable part of this and got worse with time. Adding a glyph is copying its SVG in.
  Heroicons' MIT license is retained beside the SVGs.

- **`packedImageVector`, so an icon can be one string instead of forty method calls.** A generated
  icon set is mostly `lineTo`/`cubicTo` statements: 78 Heroicons are 3,082 lines of Kotlin. The same
  geometry as text is 47,331 characters against 111,937 of builder calls. It is deliberately not an
  SVG parser and cannot become one -- no XML, no shape elements, no transforms, no arcs, no relative
  commands -- because codegen already resolved those into cubics and hole nesting, which is what
  keeps decoding cheap enough to do at class init and keeps the icon reference a compile-time
  symbol. Every committed Heroicon is proven to survive the round trip. The generated `HeroIcons.kt`
  still emits builder calls: it lands in `build/`, where its size stops mattering and reproducing
  what shipped byte-for-byte is worth more. `--packed` is one flag away if that changes.

- **A skinned character goes limp: the "Skinned ragdoll" showcase.** CesiumMan collapses wearing his
  own mesh, then is dropped again once he settles. The bodies are shaped from his own bind pose by
  `ragdollFromSkeleton` over twelve of his nineteen joints, and `RagdollSkeleton` writes them back
  into the pose the joint palette is built from. Hands, feet and the lower neck are left undriven
  and follow their parents.

- **A ragdoll can be built from a character's own skeleton.** `ragdollFromSkeleton` shapes one
  capsule per named bone, spanning from that bone to the bone below it, with joints following the
  same hierarchy -- and records the fixed transform between each capsule and its bone.
  That last part is what makes a skinned ragdoll possible at all: a capsule lies *along* a limb and
  a rig's bones point down whatever local axis their exporter chose, so binding a generic ragdoll to
  somebody else's rig shears the mesh. `RagdollSkeleton` applies the offset on the way back out, and
  an unmoved ragdoll now reproduces its skeleton's bind pose exactly.

- **A ragdoll showcase, and the showcase's physics is interpolated.** The engine showcase gained a
  "Ragdoll" entry: an eleven-limb figure collapsing under gravity and dropped again once it settles,
  which is where the joint limits are visible -- a shoulder that folds flat against a back and a
  knee that bends the wrong way are what a ragdoll without them looks like. Separately,
  `ShowcasePhysics.system()` wraps `PhysicsSystem` in a lazy delegate, and `SceneSchedule` decides
  who to interpolate by testing the *registered* system: the wrapper declared plain `System`, so
  the interpolation added alongside it never ran anywhere. `Mat4.setTrs` came with the showcase --
  the in-place form, so a set of instanced transforms rebuilt each frame stops allocating a matrix
  per instance per frame.

- **Physics is drawn between fixed steps instead of snapping between them.** The simulation runs at
  a fixed 60 Hz and a display refreshes whenever it likes, so most frames redrew a pose that had not
  changed and every so often one jumped -- stutter from a perfectly smooth simulation. `alpha` had
  been computed since `FixedTimestepLoop` was written and thrown away by `SceneSchedule`, which took
  no parameter for it. `InterpolatedSystem` is the seam; `PhysicsSystem` implements it and blends
  each body's last two poses. Rotation is blended as a quaternion and only then converted to the
  Euler angles `Transform` stores -- doing it the other way round makes a body spinning past the
  wrap point snap a full turn backwards, once per revolution.

- **A ragdoll can drive a skinned character's bones.** Until now a ragdoll was eleven invisible
  capsules falling next to a character still playing its idle animation -- the simulation was right
  and nothing wore it. `RagdollSkeleton` maps limbs onto bones and converts each limb's world pose
  into the parent-relative form `AnimationPose` stores, so a corpse keeps its own mesh; bones no
  limb is bound to (fingers, face) keep whatever the animation left there. `AnimationPose` gained
  the read/write surface that needs, which is also the seam an IK solver or a procedural head-turn
  would use. `Ragdoll.forEachLimb` now reports every limb rather than only the awake ones.

- **Ball-and-socket joints with real angular limits, so a ragdoll folds like a body.** A ball joint
  used to be a distance constraint pinned nearly to zero length, which held limbs together and
  limited nothing -- a shoulder could fold flat against the back. `BallSocketConstraint` is the real
  thing on all four Jolt bindings: a swing cone and a twist range about an axis, built on six-DOF
  with translations locked, because Jolt's point, cone and swing-twist constraints are all absent
  from JoltC and six-DOF is the only one every binding has. `humanoidRagdoll` now carries anatomical
  limits per joint, and `PhysicsWorld.setAngularVelocity` came with it -- the rotational twin of
  `setLinearVelocity`, and the only way to induce twist and so to test a twist limit at all.

- **The inspector can add a component.** Until now it could show and edit what an entity already
  carried and nothing else, so building an entity meant editing the scene file.
  `SceneComponentFactory`
  is the seam -- the third over the same types, beside the inspector's form and the snapshotter's
  copy, and for the same reason: the editor cannot name `PhysicsBody` or a game's own component, so
  whoever owns the type supplies the constructor call. `Transform` and `SpinControl` come from the
  scene, `PhysicsBody` from its plugin, and the menu offers only what the entity does not already
  have -- `World.add` replaces, so offering a present type is offering to discard what the user
  configured on it. Undoable, like every other edit.

- **`BoxScope.align`, so a Box can anchor each child where it belongs.** A Box could only stack
  every child at one alignment, which is why the scene viewport spent a layout row on its display
  toggles -- and a row across the top of a 3D view is a band of the view that is gone, at the height
  where a horizon and an object's head sit. `BoxWithConstraintsScope` is a `BoxScope` now too, as
  upstream's is, so a responsive layout can anchor per child without nesting another Box.

- **The backend parity harness renders a lit, shadowed scene, not just UI.** `vulkanHeadlessScene`
  and `webGpuHeadlessScene` stand up `lit_shadow` with a real cascaded depth pre-pass, and the new
  scenario draws a ground plane with a quad hovering over it and compares where the shadow lands on
  each backend. This is the case the UI scenarios could not reach: WebGPU sampled the shadow map
  along the wrong axis for as long as the shader existed, Vulkan's rendering of this same scene has
  had coverage the whole time, and nothing compared them. Reintroducing that bug now fails here.

- **One headless harness both backends answer to.** `:awake:engine:render:parity` opens Vulkan and
  WebGPU in a single run, draws the same scenarios through each, writes every capture to
  `build/reports/render-parity/`, and compares the two. Each backend exposes its windowless fixture
  as ordinary API (`vulkanHeadlessUi`, `webGpuHeadlessUi`, both returning `HeadlessRenderSession`)
  instead of hiding it in its own test source set, which is what made "do these draw the same
  thing?" a question with nowhere to live. It found a real divergence on its first run -- see the
  two glyph fixes below. Vulkan is opened first, deliberately: `libwgpu_native` brings its own
  Vulkan loader into the process and an instance created after it loads fails at `vkCreateInstance`.
  Every scenario is enforced. Coverage is compared everywhere and colour only where alpha is full:
  the two targets store colour differently -- Vulkan's offscreen target is UNORM and premultiplied,
  WebGPU's takes its format from the surface, which is sRGB on macOS -- so partial-coverage colour
  is not comparable across them, while the shape is.

- **A file panel with a pluggable viewer, and Studio's Files tab on top of it.**
  `EditorFilePanel` lists the files a host gives it and previews the selected one with whichever
  registered `EditorFileViewer` claims the extension. The editor never reads a file -- the host
  passes bytes, because `readResourceBytes` suspends and on the web it is a `fetch`, so a panel that
  owned loading could not run in a browser. The built-in `PlainTextFileViewer` deliberately does not
  claim `.md`: a built-in that took every text-shaped file would take the extension an add-on exists
  to handle, and the add-on would only win if the host happened to install it first. Studio's Files
  tab ships a Markdown document nothing can draw, so the seam is visible in the running app --
  install a viewer for `md` and the same document renders.

- **Ragdolls.** `Ragdoll` builds jointed limbs above `PhysicsWorld`, and `humanoidRagdoll` gives an
  eleven-limb figure that collapses under gravity and stays in one piece. Not a binding to Jolt's
  own `Ragdoll` — **JoltC exposes none**, so that would work on three targets and throw on the
  fourth; a ragdoll is bodies and constraints, and Jolt's class is convenience over that.
  **A zero-length distance constraint is not a ball joint**, which is what this cost to learn: Jolt
  solves one along the axis between its two points, and when they coincide that axis is undefined,
  so it never converges — eleven limbs still twitching after fifteen seconds. Two centimetres of
  slack makes it solvable and it settles in two. Self-collision was the first suspect and was
  innocent: a non-self-colliding layer changed nothing.
  Ball joints still have no angular limits, so a shoulder folds in ways a body cannot. That wants a
  six-DOF-backed joint, the only type all four bindings share.
- **Physics replays exactly: the same scene, stepped the same way, produces the same numbers.**
  Contact events are normalised low-id-first and sorted before delivery, and every map whose
  iteration feeds teardown order is insertion-ordered — teardown decides which body ids Jolt hands
  out next, which feeds its island ordering and so the simulation itself.
  This is same-build, same-machine reproducibility. Cross-platform is not available: the four
  backends are three different Jolt builds on three architectures.
  What it buys during development: physics tests that assert exact values instead of tolerances,
  bug reports that replay instead of "sometimes", and contact-ordering flakes that cannot happen.
  **The test only has teeth at scale** — with six falling bodies, removing the sort still passed;
  with forty it fails every run. Jolt guarantees deterministic simulation, not the order it invokes
  a contact listener across worker threads.
- **Fixed: buoyancy crashed the process on Android.** Applying it to a body that had settled and
  fallen asleep aborted, because buoyancy adds velocity and a sleeping body has no motion state to
  receive it. Only Android found it: it ships jolt-jni's *debug* artifact, so it asserts where
  desktop's release build runs on quietly — the same way the iOS simulator does, and worth
  remembering as where misuse surfaces first.
  The first fix asked `BodyInterface.isActive` while already holding a write lock on that body,
  which is a recursive lock and crashed a different test instead. It asks the locked body now. That
  is the second time nested body locks have bitten, after iOS's constraint creation.
- **The player can swim.** Walking into the showcase's pool now dampens gravity, slows horizontal
  movement, and turns Space and Ctrl into rise and dive. It needed **no character-controller
  change**: vertical velocity is already the caller's to integrate — the controller's guarantee is
  that it only ever removes motion from what it was handed — so swimming is the caller integrating
  differently while the pool reports the player inside it. `applyBuoyancy` could not do this job,
  because the character's body is kinematic and buoyancy only moves dynamic ones.
  Three ways to get it wrong, each a test: no drift is a swimmer hanging motionless, full gravity is
  a swimmer falling, and a drift stronger than the swim speed is water that cannot be climbed out
  of.
- **Water you can drop something into.** The terrain showcase has a pool: a sensor volume is the
  water, contact events track what is inside it, and the fixed step applies buoyancy to each
  occupant so a box dropped in floats instead of reaching the bottom. That composition is the point
  — physics has no idea where water is, so the sensor answers "who is in it" and the loop answers
  "how often".
  Two things worth knowing. **There can be only one `drainContacts` caller**: it hands over the
  events since the last call and forgets them, so the goal zone and the pool share one loop that
  routes by which sensor was touched. And **a pool whose surface is level with its own bed proves
  nothing** — floating and resting end at the same height — so a placement test asserts the surface
  clears the ground beneath it.
- **A trigger can finally detect the player.** `CharacterConfig(innerBody = true)` gives the
  character controller a kinematic body of its own. Without one it is invisible to physics — it
  moves by sweeping shapes, so Jolt has nothing to report contacts about, and every sensor built for
  pickups and checkpoints was deaf to the one thing it exists for. The showcase's goal zone now
  reacts to the player, not only to crates pushed into it.
  The body is excluded from the controller's own sweeps, which is what `shapeCast`'s `ignore` was
  needed for: a body at the character's own position is nearer than anything else in every sweep it
  makes, so without the exclusion the character stops dead on its first frame. Removing it fails
  three tests.
  **It does not give pushing for free** — the obvious guess, and wrong. The controller refuses to
  move into a crate, so the kinematic body never drives through one and there is no contact to
  solve; shoving stays the caller's job. A test asserts that after claiming the opposite and being
  corrected by the simulation.
- **Control over which bodies are actually being simulated.** `setActive` and `isActive` on all four
  backends. Jolt already sleeps a settled body, so this is control over *when* — the open-world case
  where a world holding thousands of crates should not be simulating thousands of crates.
  **Deactivating is not disabling:** a sleeping body still collides and is woken by what hits it, so
  an open world that sleeps distant scenery does not become scenery the player falls through. Two
  consequences bite from elsewhere in the contract — a sleeping body is not visited by
  `forEachBodyTransform`, and a sensor only detects *active* bodies, so deactivating something
  inside a trigger makes the trigger forget it.
  The distance-based *policy* is deliberately not here: nothing consumes it, and what counts as
  "far" is a game's decision, not the engine's.
- **Hinge and distance constraints, so a world can have doors and ropes.** `HingeConstraint` is
  every door, lid, lever and wheel; `DistanceConstraint` is every rope, chain and grapple. Two of
  Jolt's ten, because two cover the verbs that asked for them.
  **A constraint dies with either body it joins.** Jolt does not detach one when a body is
  destroyed — it crashes on the next step — so `destroyBody` removes them first and `destroy()`
  clears them before freeing bodies.
  Three traps, each caught by a test rather than reasoned about: **two write locks at once trips
  Jolt's lock-ordering assert** (iOS died in `BodyLockInterfaceLocking::LockWrite`; it now takes one
  at a time); **a constraint is ref-counted, so freeing it by hand double-frees** (on wasmJs that
  corrupted the shared heap and showed up as unrelated tests failing later); and **Jolt wants a
  hinge's normal axis and will not derive one**.
- **Buoyancy, so things float.** `PhysicsWorld.applyBuoyancy(handle, surfaceY, Buoyancy, deltaTime)`
  on all four backends. Called every step for every body in the fluid — it is one step's worth of
  push, not a state a body is put into — because physics has no idea where the water is and a
  sensor covering the volume does. That is the composition the contact-event work was for.
  A horizontal surface at a height rather than an arbitrary plane: every water body in a game is
  level. **Dynamic bodies only, and enforced**, because Jolt's own call reaches for motion
  properties a static body lacks and aborts the process rather than returning — the KDoc claimed
  that guard before the code had it, and the test that passes a static body found it as a SIGABRT.
- **Streamed terrain colliders, and rebuilding them when the ground is deformed.**
  `heightFieldCellStreamer` gives every streamed cell a tile of a larger heightfield, and **derives
  the cell size** instead of taking one — the streamer's cell centres and a tile's own extent agree
  only at one value, and a hand-passed mismatch offsets every collider from its terrain uniformly,
  which reads as the whole world being subtly wrong rather than as a mistake.
  `PhysicsCellStreamer.reloadCells` rebuilds a deformed cell, off the frame thread like a load and
  only for cells actually streamed in. `tilesTouchedByEdit` says which cells an edit invalidated —
  **an edit on a tile boundary invalidates both sides**, since neighbours share that sample, and
  rebuilding one leaves a wall exactly where the player just dug.
- **Terrain can be collided with as a grid of tiles, not one body for the whole map.**
  `heightFieldTile` cuts one tile out of a larger sample grid; `heightFieldTileCenter` places its
  body, for a centred authored heightmap or a corner-anchored streamed one.
  **Neighbouring tiles share their edge samples** — tile `k` starts at `k * (samples - 1)` — and
  that off-by-one is the entire difficulty: cut disjointly, the two sides of a seam end at
  different heights, so a character catches on a one-sample wall or drops through a gap, and
  nothing about it looks like a slicing bug. Verified against a real Jolt world on all four
  backends; cutting disjointly fails three of the five tests.
- **iOS can collide with terrain, and no vendored library was forked to do it.** JoltC wraps no
  heightfield at all, so that backend threw for one — the last per-target capability gap, and a
  blocker for anything terrain-shaped. It now builds the field as a triangle mesh: a heightfield
  *is* a grid of triangles, so the binding was missing an optimisation rather than a capability.
  The cost is one float per sample becoming three per vertex plus six indices per cell, which is
  nothing at 9x9 and half a million triangles at 512x512.
- **Vertex buffers are built through `VertexFormat` now, not by counting floats.**
  `InterleavedVertices` writes attributes by name — `put(vertex, VertexSemantic.Normal, x, y, z)` —
  and takes the offset from the format. The format had always known every attribute's offset and
  the stride; nothing used it to *write*, so every producer hand-packed `vertices[cursor++] = ...`
  and had to remember its write order matched the format it declared. When those disagree the mesh
  still loads and still draws, rendering UVs as colours, with nothing pointing at the packing loop.
  `GltfMesh`'s five near-identical interleave functions collapse to two shared helpers — 165 lines
  gone — and the heightmap mesh builder uses it too. **Four of those five had no test at all** while
  the showcase's skinned and glTF-viewer drivers depended on them, so they were pinned by
  characterisation tests first; those caught two things I had wrong, including that joint indices
  are stored as `Float.fromBits` patterns because the GPU reads that slot as `uint4`.
  The clipmap builders follow: their two byte-identical vertex loops become one, and their
  hand-maintained `COLOR_OFFSET = 6` and `STRIDE = 11` are now asked of the format through the new
  `VertexFormat.floatOffsetOf`. Pinned first by a test asserting a clipmap vertex slot by slot,
  which nothing did — the existing ones count vertices and read the colour channel, so a packing
  change kept every count intact and rendered UVs as colours.
- **One definition of grid winding, replacing four copies.** `gridTriangleIndices` in
  `:awake:core:geometry`, used by the heightmap mesh builder, both clipmap ring builders and the
  new physics conversion. Winding is the part worth centralising: wound backwards a surface still
  draws, and rays still hit it from both sides, and only a body falling through it shows the
  difference — so the shared version has the test that asserts the face normal, which none of the
  four copies had.
- **Continuous collision detection, so a bullet cannot cross a wall.** A step moves a body by
  velocity times delta and then looks for contacts where it landed, so at 400 units/s a projectile
  is six metres past a wall before anything is asked about it.
  `PhysicsWorld.setContinuousCollision(handle, enabled)` makes Jolt sweep the body's shape along
  that displacement instead. A setter rather than a `createBody` argument: whether a body needs it
  depends on how fast it is *now*, unlike `sensor`, which is what a body is.
  Off by default — it is a shape cast per step per body. **It protects the body it is set on, not
  the ones it hits:** whichever thing moves fast is the one that needs it.
  The test asserts both directions, because "it stopped" proves nothing if the bullet was never
  fast enough to tunnel; the no-CCD run must tunnel, and does, identically on all four backends.
- **Jolt's asserts are on for the iOS simulator build, off for the device.** With them off, Jolt
  does not check its callers at all — a wrong body type or an unlocked body read is silent
  corruption rather than a stop — and the simulator is where the tests that would catch that run.
  The device build stays off: an assert there stops a player's game over something the simulator
  should have caught.
  Worth knowing before relying on it: **this does not get you the message.** Jolt's default
  `AssertFailed` breakpoints and prints nothing, and its default `Trace` is a stub, so a violation
  is `signal 5: Trace/BPT trap` plus a stack trace rather than text. The frame under
  `JPH::DummyTrace` in the crash report is the site — and `DummyTrace` appearing at all is the tell
  that Jolt formatted a diagnostic and had nowhere to put it. Installing a real handler means
  setting C++ globals that cinterop cannot reach and JoltC does not expose.
- **Android instrumented tests, so all four physics backends are now actually run.** Android was
  the last blind spot and could not be covered by host tests at all: jolt-jni's Android artifact
  ships device ABIs and nothing for a JVM host, so `System.loadLibrary("joltjni")` has nothing to
  load. `connectedAndroidDeviceTest` runs the same `commonTest` sources on a device — 44 tests,
  including the heightfield ones, which had never executed on that binding. `testAndroidHostTest`
  is disabled in this module for the reason above; it would fail every test with
  `UnsatisfiedLinkError`. The four jolt-jni capability tests moved to a `joltJniTest` directory
  shared by desktop and Android, since those two are the same binding.
- **The Jolt backend's tests moved to `commonTest`, and found three more bugs on the way.** 40 of
  them now run on desktop, the iOS simulator and a headless browser instead of desktop alone. What
  the first run turned up, all of it invisible to a compiler:
    - **iOS `raycast` accepted `onlyLayer` and ignored it**, so a camera meant to see only the level
      was stopped by every crate — and it reported sensors as solid.
    - **iOS swept meshes silently**, returning a hit instead of the `PhysicsCapabilityException`
      every other backend throws for a surface with no inside.
    - **wasmJs's body filter compared a pointer to an id.** JoltPhysics.js hands its filter
      callbacks
      Emscripten *pointers*, not values, so `ignore` matched nothing and silently did nothing.
      Four tests stayed in `desktopTest` because they assert jolt-jni *capabilities* the contract
      makes
      optional — heightfields (unimplemented in JoltC) and active-body readback (
      `forEachBodyTransform`
      explicitly permits visiting everything).
- **Fixed: the iOS physics backend aborted on its very first step, and always had.**
  `TempAllocatorImpl` is a linear allocator over a fixed block — overrunning it calls `abort()`
  rather than falling back to malloc — and Jolt sizes its per-step arrays from the *configured*
  maxima, not from the bodies present. So 10 MB was under what a world with one box in it asks for,
  and every `step` killed the process. Raised to 32 MiB.
  It survived four features because nothing ever *ran* that backend: it compiled, and compiling
  proves nothing about a temp allocator. Found by moving one test into `commonTest`, where it runs
  on desktop, iOS and wasmJs instead of desktop alone. `JoltBackendSmokeTest` now guards all three.
- **Casts no longer report sensors, so a trigger volume is not an invisible wall.** Found by
  spiking the assumption rather than trusting it: a sweep at a sensor hit it at fraction 0.17, which
  meant the goal-zone trigger added a commit earlier was a solid obstacle the character bumped into.
  A sensor is not solid, so it is not an answer to "what would block me" -- `shapeCast` and
  `raycast` now skip them, and `overlapShape` is the query that does see them. That is the division:
  casts ask what stops you, overlaps ask what you are inside.
- **Overlap queries, and a sweep that can ignore one body.** `PhysicsWorld.overlapShape` visits
  every body inside a convex volume -- spawn validity, an explosion's victims, a melee arc's
  targets -- which no cast can answer, because a sweep stops at the first thing that would block
  it. A visitor rather than a list, so an explosion does not allocate at the rate the game is
  played. `shapeCast` gains `ignore`, which skips one body *inside* the query: filtering the result
  afterwards cannot substitute, since a closest-hit cast only ever reports the ignored body and
  everything behind it stays invisible.
  The backends split on how, and the reason is worth knowing: **jolt-jni's `BodyFilter` cannot be
  overridden** -- it has a public `shouldCollide` and no callback subclass, and an override is never
  called. Measured, not assumed: a filter rejecting everything left the ray hitting the body anyway.
  So the JVM backends collect every hit and take the nearest other one; iOS builds a real
  `JPC_BodyFilter` and wasmJs uses `IgnoreSingleBodyFilter`.
- **A pickup you can push a box into, in the terrain showcase.** A `goal-zone` post marks a trigger
  volume; boxes shoved into it are collected and removed, body first. The first thing in the
  showcase that reacts to physics having *happened* rather than to physics having moved something --
  nothing here measures a distance to the zone, it waits for Jolt to say a box entered.
  `PhysicsBody`
  gained a `sensor` flag so a scene can author a trigger at all.
  **The player is not what trips it, and cannot be.** `KinematicCharacterController` owns no body --
  it sweeps shapes -- so Jolt has nothing to report contacts for, and giving it an inner body is
  blocked until a sweep can exclude one: `onlyLayer` restricts a query to a single layer and a
  character needs two, and skipping a specific body needs multi-hit queries. Until then a trigger
  fires on the crates, not on the character.
- **Sensors and contact events, so physics can be a gameplay verb.**
  `createBody(..., sensor = true)` builds a body that detects what passes through it instead of
  blocking it, and `PhysicsWorld.drainContacts` hands over the `BEGAN`/`ENDED` pairs once per step.
  Pickups, checkpoints and damage volumes are expressible now; before this, physics could push
  things around but could not tell anyone that something had happened.
  The queue exists because **Jolt calls its contact listener from worker threads, in the middle of
  a step** — acting there would mean mutating a running simulation from a thread that does not own
  it, so the callback only records and the drain replays on the frame thread. Guarded by
  `synchronized` on the JVM backends, a pthread mutex on iOS, and nothing on wasmJs, which has no
  threads. Only sensors report, because Jolt reports every touching pair in the scene on every step
  and forwarding all of it would allocate an event per contact per frame for events nothing reads.
  No contact point, normal or impulse: `ENDED` arrives from a callback that carries none of them.
  `MeshShape` and `HeightFieldShape` are rejected as sensors — both are surfaces with no inside.
- **Snap and transform-space toggles in the scene editor's viewport.** `GizmoSnap` and `GizmoSpace`
  were complete and tested but nothing read them: the editor's only `GizmoFrame` left both at their
  defaults, so it snapped never and dragged in world space always. `EditorState` now carries
  `snapEnabled` and `transformSpace`, and `EditorGizmoOptions` toggles them beside the tool palette.
  Snapping is one flag rather than a step per tool, at one world unit, fifteen degrees and tenths
  of scale.
- **`EditorPlugin` has its first implementation, so the plugin registry is live.** A feature that
  simulates, draws and is editable has to reach `SceneSystemsDsl`, the shader pack's
  `ContentFeatureSource` and `EditorProviders` separately, and a host that forgets one gets a
  feature that runs but cannot be edited. `EditorPluginRegistry.install(plugin)` bundles the editor
  half and rejects a duplicate id or an incompatible API version on the way in. Plugins are linked
  at build time, not loaded at runtime -- Kotlin/Native and wasmJs cannot load code, so a
  marketplace would be a desktop host above this contract. Systems are not on the plugin:
  `SceneSystemsDsl` sits in a module `awake:editor:scene` depends on, so a plugin cannot reach the
  DSL without inverting that edge, and a host installs both for now.
- **`awake:editor:ai` and `awake:editor:render`.** The AI behaviour and rendering inspector fields
  were `when` branches inside `awake:editor:scene` while physics editing was already a plugin. Each
  feature's editor now ships with the feature, and what stays built into the scene editor is
  `Transform` and `SpinControl` -- what every scene has. A new engine feature adds a plugin rather
  than a branch. Both dropped Compose in the move: `SceneFieldScope` describes a form, so an
  inspector no longer knows how a field is drawn.
- **`awake:editor:physics`**, joining `awake:editor:scene` and `awake:scene:physics`. Neither may
  depend on the other -- the editor must not pull in the physics backend, and physics must not
  depend on an editor -- so the plugin that makes `PhysicsBody` inspectable needs a module of its
  own rather than a home inside whichever sample happened to see both.
- **Inspecting something no longer requires this ECS.** `EditorFieldScope` and
  `EditorInspector<T>` moved to `awake:editor`, which does not depend on `awake:ecs`; the ECS
  binding stays in `awake:editor:scene` as `SceneInspectorTarget(World, Entity)` and a
  `SceneComponentInspector` over it. The field vocabulary never named an entity, world or
  component -- it described a form -- but it lived beside the ECS-typed inspector, so an engine
  with a different object model could not reach it. `SceneFieldScope` is now a typealias, so
  existing inspectors compile untouched.
- **`PhysicsBody` is inspectable.** `PhysicsScenePlugin` contributes a motion-type field through
  the inspector seam, which until now had no user outside its own test -- `awake:editor:scene`
  cannot name the type without depending on the physics backend, which is what the seam exists to
  avoid. `shape` stays read-only: editing one means choosing a sealed variant and then its
  parameters, and there is no field for that.
- **Physics runs in a scene for the first time.** `createJoltPhysicsWorld` gives common code a way
  to obtain a world at all -- `JoltPhysicsWorld` is a separate class per target rather than one
  `expect` class, so until now nothing outside the backend could construct one. The
  heightfield-terrain showcase drops four boxes onto the same samples the terrain mesh is built
  from, stepped on a fixed timestep.
- **A capsule, and a shape cast to move it with.** `CapsuleShape` and
  `PhysicsWorld.shapeCast(shape, from, to)` on all four backends. `ShapeCastHit` carries a
  contact normal, which is what collide-and-slide projects along; without it a hit cannot be slid
  against. No rotation parameter, deliberately.
- **A character that walks on the world instead of through it.**
  `KinematicCharacterController` sweeps, slides along whatever stops it, steps up kerbs and
  follows the ground down the far side, and reports what it is standing on. It is Awake's own
  rather than a binding to Jolt's `CharacterVirtual`: collide-and-slide needs exactly one thing
  from a backend, so written once here it behaves identically on all four. Gravity stays the
  caller's -- the controller integrates nothing, which is what makes it predictable. The engine
  showcase drives one with WASD, Space to jump, a camera that follows it and stops at walls, and
  crates it can shove around.
- **Velocity and impulse: `setLinearVelocity`, `getLinearVelocity`, `addImpulse`,
  `moveKinematic`.** Four methods rather than one because each does what the others get wrong --
  an impulse is mass-aware where a velocity write is not, and a kinematic body moved toward a pose
  genuinely has a velocity rather than teleporting, so what stands on a platform rides it.
- **Level geometry can collide.** `MeshShape` gives static geometry a triangle-mesh collider built
  from the same vertex and index arrays the renderer draws, and `ConvexHullShape` gives a moving
  prop a shape that is not a box. Both on all four backends. A mesh is a surface -- no inside, no
  mass -- so it is static only, and its **winding decides which side is solid**: wound the wrong
  way, bodies fall through it while rays still hit it from both sides.
- **Crouching, and refusing to stand where there is no room.** `CharacterConfig.crouchHalfHeight`
  gives a character a shorter capsule; `crouch()` swaps to it with the feet planted, and
  `standUp()` sweeps upward first and returns whether it managed. A caller that ignores the answer
  puts the capsule inside the ceiling, which is the one state a swept character cannot get out of.
  Held on Ctrl in the engine showcase, tried every frame, so the character stands the moment it
  walks back into the open.
- **Moving platforms carry what stands on them.** `KinematicCharacterController` reports
  `groundVelocity` -- how fast the ground underfoot is moving -- which a caller adds to the
  displacement it asks for. Reported rather than applied, for the same reason gravity is: a move
  only ever does what it was asked, so the caller decides whether walking against a lift should
  win. The terrain showcase has a lift beside the mound, driven with `moveKinematic` so Jolt
  derives the velocity to arrive rather than teleporting it.
- **Colliders stream with their cells.** `PhysicsCellStreamer` gives every streamed world cell a
  collider and takes it back when the cell leaves, so an open world's body count follows the view
  distance rather than how far the player has walked. The shape is built off the frame thread and
  the body created on it, mirroring `MeshCellStreamer` -- including its cell-centre convention, so
  a streamed collider lands where the streamed mesh does. `PhysicsBody` gained a `layer`.
- **Colliders can be seen.** `physicsDebugLines(world)` builds world-space wireframes of every
  collider at the pose the simulation has it in, coloured by motion type, and the engine showcase
  has a "Colliders" toggle for them. The collider and the mesh drawn for it are two different
  things, and the bugs that matter live in the gap -- a capsule sunk into terrain, a heightfield
  offset by half a tile, a body whose collider never moved with its transform. All of them read as
  rendering faults until this is on. Heightfields draw as a footprint rather than a sample grid.
- **Collision layers.** `CollisionLayers` gives a world its own matrix -- how many layers, which
  collide, which move -- instead of the two every backend hardcoded, and bodies carry a
  `CollisionLayer`. Queries can be restricted to one layer, which is what stops the showcase
  camera being shoved about by the crates around the player's feet. Broadphase is two trees now,
  static and moving, rather than one. The limit worth knowing: a query takes ONE layer, not a
  mask, because that is all jolt-jni and JoltPhysics.js expose in public API.

### Fixed

- **Bone hierarchies were composed with quaternions multiplied the wrong way round.** `Quat.times`
  is the Hamilton product with its operands swapped, so composing a child under its parent is
  `local * parent` -- not the `parent * local` that reads correctly and that the matrix APIs are
  spelled as. Six places in the ragdoll code had it backwards, which was invisible because their
  tests recomposed with the same swap; it only surfaced against a walker mirroring real playback.
  `QuatCompositionTest` now pins the order against the matrix path a joint palette actually uses.
- **A bone authored as a baked matrix was read as though it had none.** `RagdollSkeleton` took an
  undriven bone's transform from the pose, which holds nothing for such a bone, and
  `ragdollFromSkeleton` took the matrix's rotation but not its translation. A glTF Z-up root is
  exactly this kind of bone, so every bone under one was posed a quarter-turn out.

- **Physics readback values were kept by reference where they had to be copied.**
  `PhysicsWorld.forEachBodyTransform` hands out a position and a rotation that the next body
  overwrites. Positions were copied everywhere; rotations were not, in three places -- so every body
  ended up sharing whichever rotation happened to be visited last. The pose interpolation added
  alongside it was affected: rolling previous from current made both halves the same object, leaving
  nothing to interpolate between. The shared test fake now hands out scratch the way the real
  backends do, which is what would have caught it.

### Changed

- **A stroked path is tessellated once, not once per frame.** Stroking is the most expensive
  tessellation the 2D coalescer runs -- flatten every curve, offset both sides of every contour with
  per-join arc trig, scanline-fill the ring -- and a UI redraws the same icons every frame in the
  same place. The frame ratchet's own scene went 41,016 to 56,184 vertices when the icon set became
  real outlines, and every one of those was being recomputed. `StrokedPathMeshCache` keys on the
  placed path, so a stroked path that moves still misses; normalising to the origin costs a full
  walk of the commands and is not worth it until something that moves strokes every frame.

- **The scene hierarchy's header actions are one button group.** Duplicate, delete and add all
  perform an action, which is shadcn's own rule for choosing `ButtonGroup` over `ToggleGroup`, and
  the reference cites it as well. Joined and at `size-6` they fit a 160dp sidebar, which three
  separate `size-9` buttons did not -- the Row overlaps rather than shrinks, so the title and the
  entity count were being painted over. The count is gone: the tree below shows what matched.

- **The hierarchy's duplicate and delete actions are always in the header, disabled with nothing
  selected.** They were composed only while a selection existed, which reflowed the header under the
  pointer as the selection changed -- and, worse, made a subtree come and go in an engine whose
  `remember` is positional, the exact shape that surfaced once as a `ClassCastException` in an
  unrelated panel. The React reference shows both permanently for the same reason.

- **The scene viewport's controls float over the scene, and move when it is too narrow to hold
  them.** Tools in one corner, display and debug toggles centred on the top edge, gizmo in the
  other -- the anchors the React reference (`tools/shadcn/reference-app`, case `studio-shell`) uses,
  and the reason it gives: a single strip across the top costs the scene its most useful band. Below
  760dp the three top anchors collide, so the toggles stack into the top corner and the tools move
  to the bottom one. Both were measured from the rendered shell rather than chosen.

- **The viewport's display and debug toggles read as two groups, and the inspector's empty state
  fits its column.** The two toggle groups sat eight pixels apart in identical styling, so ten
  unlabelled glyphs read as one strip a user navigates by counting positions; each group now sits on
  its own surface. The inspector showed `shadcnEmpty` -- shadcn's page-scale empty state, `p-12` and
  a `text-lg` title -- centred in a 300dp column, which is two lines of near-heading text floating
  in the middle of the panel; it is one muted line at the top now. The shared component is
  unchanged:
  the parity baselines hold it to the web's dimensions, and the call site is what was wrong.

- **A shader's clip space is handed to it, not remembered by whoever wrote it.**
  `aslShaderSet { clipSpace -> ... }` builds a definition once per backend with that backend's
  `ClipSpace` in scope, and `ndcToUv` in the shader DSL is the one place an NDC coordinate becomes a
  texture coordinate. `ClipSpace` used to govern only matrix construction, so the inverse -- reading
  an image back by screen or light-space position -- was arithmetic each shader author typed from
  memory. Two got it wrong: `depth_fog` was patched with a hand-passed `flipDepthV` boolean, and
  `lit_shadow` then sampled every shadow map mirrored on WebGPU. `litShadowShader` and
  `depthFogShader` both take a `ClipSpace` now and neither does the arithmetic itself.

- **Studio's bottom dock has Console, Assets and Timeline tabs**, composed through the stateless
  `EditorDock` with the selection in Studio's own store. `AwakePluggableWorkbench` is deleted: it
  derived its tabs from data presence, so an empty asset list meant no Assets tab and a user who
  deleted their last asset watched one disappear. The capability panels it wrapped are unchanged
  and now reachable directly. Assets lists the fixture's registered meshes; selecting one does
  nothing until drag-to-viewport exists, and the timeline is empty because the fixture has no
  clips -- both shown rather than hidden, which is the point.
- **Studio moved from `samples/studio` to `apps/studio`.** Its Gradle path is now `:app:studio`,
  so the quickstart is `./gradlew :app:studio:run`. It was never a sample: it is the product the
  README points a new user at, and the only host every editor seam is proven through. `samples/`
  now holds only illustrative code. No Kotlin changed -- the package was already
  `com.awakekt.awake.studio`.
- **Body poses are read back by visitor, and only while they are awake.**
  `PhysicsWorld.syncTransforms()` became `forEachBodyTransform`, which hands out scratch values
  instead of allocating a list plus a vector and a quaternion per body per frame, and on jolt-jni
  and JoltPhysics.js enumerates only the bodies Jolt considers awake -- so a settled scene costs
  almost nothing to read. `syncTransforms()` remains as an extension for tests and tools.

  This changes what a caller may assume: **a body may never be visited at all.** Static bodies are
  never awake and settled ones stop being awake, so neither is reported. Hold the last pose you
  were given -- which a scene's `Transform` already does -- or ask the world where a body is.
- **A body's rotation is a quaternion.** `BodyTransform.rotation` and `createBody` take
  `core.math.Quat` rather than Euler angles, so the solver's own rotation passes straight through
  instead of being converted in four backends. `Transform` still stores Euler, so the conversion
  moved to one place at the ECS boundary rather than disappearing -- this does not fix gimbal
  lock, which lives in `Transform`.
- **`PhysicsBody.motionType` is settable, and changing it rebuilds the body.** It was a `val`, and
  `PhysicsSystem` created a body once and never rebuilt one, so a hypothetical edit would have left
  the component saying `STATIC` while the simulation kept a dynamic body falling. The system now
  tracks what each live body was built with and destroys the old one on a mismatch. A rebuild
  rather than a mutation, because the backend port has no `setMotionType`; the body's velocity does
  not survive it.

### Fixed

- **Shadows compare in hardware, and the last of the acne is gone.** `lit_shadow` now samples its
  cascades through a linear-filtering comparison sampler (`textureSampleCompareLevel`, new to the
  shader DSL alongside `sampler_comparison`; `compareEnable`/`compareOp` new to the Vulkan sampler
  binding), so each PCF tap blends four comparison *results* instead of flipping on one near-tie
  depth -- the shadow edge is a smooth penumbra instead of a three-texel dither. The residual
  waffle on tilted faces turned out to be the map polygon's own slope, which no receiver-side
  `nDotL` estimate can see, so the cascade depth pass now applies the rasterizer's per-polygon
  depth bias at the source. Together they take the studio cube's worst yaw from 24 (Vulkan) and
  32 (WebGPU) self-shadowed pixels to 1 and 0, while the receiver bias constants *shrink*
  (2.5/3 -> 1.5/2 texels, normal offset 1.5 -> 1), so resting casters hold their shadows tighter,
  not looser. What the frame looks like is now pinned by `SceneShadowBaselineTest`: per-backend
  pixel goldens over one symmetry period of yaws, zero tolerance, on both backends.

- **Heroicons' outline tier is stroked path data again.** All 32 glyphs were generated before the
  script emitted `path(stroke = ...)`, so each was a *filled stroke centreline* -- the
  silent-garbage
  case the generator's own comment names. Regenerated from the official SVGs. Two of them,
  `userCircle` and `lightBulb`, still rendered as solid shapes afterwards -- a stroke-tessellation
  bug that predated the data, fixed below. `OutlineIconStrokeTest` measures the hole rather than
  leaving it to be noticed on screen.

- **A stroke outline is no longer mistaken for a convex shape.** `isConvex` only compared turn
  signs, and a stroked arc's ring turns one way the whole way round while wrapping about twice: out
  along one side, round the cap, back along the other, cap again. It read as convex,
  `tessellateFill`
  centroid-fanned it, and the fan covered the hole the outline was supposed to leave -- `userCircle`
  and `lightBulb` rendered as solid discs. It now also requires the total turning to be one
  revolution, summed over edge directions with zero-length edges dropped: pairing raw vertex triples
  across such an edge discards the turn at that vertex, enough to push a genuinely convex rounded
  rectangle out of tolerance. Those icons now go through the scanline triangulator, which is what an
  outline costs -- the frame ratchet moves from 41,016 to 56,184 vertices, and the cheaper number
  was
  measuring blobs.

- **A stroked contour that ends where it began is stroked as a loop.** The open-contour path walks
  the outer and inner boundaries the same way round, so under `NonZero` the windings add and the
  hole fills. Found while chasing the icons above; not what was wrong with those two, but wrong.

- **The icon generator emits types that exist.** It still wrote `UiImageVector`/`uiImageVector` and
  `UiStroke`, which went with `ui-core`, so its output no longer compiled against the file it feeds
  -- adding an icon meant hand-editing generated code, which is the one thing the icon rule forbids.

- **The hierarchy's header actions are all one Heroicons tier.** Duplicate came from `Solid24` while
  delete and add came from `Solid20Mini`; the two tiers are drawn for different pixel sizes, so one
  button read heavier than its neighbours. `square2Stack` is generated at 20 now.

- **The console marks warnings and errors with a glyph.** A letter column was standing in for the
  reference's `exclamation-triangle`/`x-circle`, which the icon set did not have. It does now. The
  column is reserved even on lines with no glyph, so a warning appearing above does not shift every
  message's left edge.

- **An inspector section's state belongs to the section, not to its position.** `remember` stores
  its slots on the nearest enclosing node and indexes them by call order, so every component
  section's expanded flag lived in the panel's own slot list -- adding or removing a component
  shifted them all by one, silently swapping which section was open. Each section is now a keyed
  node, which is what makes its state its own. The add-component menu made the same latent bug
  fatal rather than silent: its remembered state landed on a section and was read back as one.
- **A lit face no longer speckles itself as it turns.** The shadow slope bias approximated
  `tan(acos(n))` as `(1 - n)/n`, described as the same shape without a square root. The two differ
  by `sqrt((1 + n)/(1 - n))` -- 2.4x at `n = 0.7`, 4.4x at 0.9 -- and agree only at grazing angles,
  so the cheap form was weakest exactly where a surface faces the light. A rotating cube sweeps its
  lit faces through that band, which is why the studio's cube stippled at some angles and not
  others, and why it looked like a WebGPU fault when both backends measure it identically. The two
  texel counts had been fitted against the wrong slope and were re-swept with it. The parity harness
  gains the scene that can show this at all: its existing one uses a single-sided plane under one
  fixed box, which cannot self-shadow however wrong the bias is.

- **A horizontal `ShadcnButtonGroup` with a separator no longer takes its parent's whole height.**
  The separator is a hairline that fills the cross axis, and the group's `Row` set no height, so it
  read whatever the parent offered: dropped into the scene hierarchy's header it became as tall as
  the panel and pushed every entity row off the bottom of the screen. The vertical orientation
  already guarded this with `IntrinsicSize.Min`; the horizontal one does now too. A
  height-constrained bar -- the only place a separated group was used before -- hides it completely.

- **WebGPU sampled the shadow map upside down, so shadows landed away from what cast them.**
  `lit_shadow` turned a fragment's light-space NDC into a shadow-map UV with one formula for both
  backends. That formula is only right for a Y-down NDC: Vulkan's projection flips Y
  (`ClipSpace.Vulkan.flipY`), WebGPU's does not, so on the web the lookup was mirrored about the
  map's centre. The shader takes the axis as a parameter now -- the same shape `depthFogShader`
  already used for the identical divergence -- and `PackShaderSets.LitShadow` emits one source per
  backend. A test holds the two sources to differing on that line and no other.

- **WebGPU's shadow binding test reads the shader it is meant to check.** It loaded
  `assets/shader/webgpu/lit_shadow.wgsl` from the classpath, and that file stopped existing when the
  packed shaders moved their WGSL inline -- so the test failed on its first line and the binding it
  exists to verify has been unchecked since. It resolves both shaders from `PackShaderSets` now,
  and passes.

- **WebGPU's glyph shader was told what kind of atlas it had, so web text is sharp.** The font-info
  uniform was written as a partial `writeBuffer` at its own offset -- which reached the buffer and
  never the shader, leaving `fontInfo` at zero. The glyph shader read that as "coverage-alpha
  atlas" and sampled an MTSDF atlas's alpha channel as if it were ink, turning every edge into a
  wide linear ramp: the same letter came out about two pixels wider on every side than Vulkan's,
  with no solid interior at all. The pipeline now holds both halves of the uniform block and writes
  it whole from offset zero, the way Vulkan always has.

- **WebGPU scaled UI colour by coverage, so web text stopped rendering as blobs.** The UI blend
  state hardcoded `srcFactor = One` for every pipeline kind, but only the texture pipelines emit
  premultiplied colour -- quads, rounded quads and glyphs emit straight alpha, so a half-covered
  pixel painted at full strength and every antialiased glyph edge came out at full weight. Vulkan
  has always chosen the factor per kind from the same `isPremultiplied` flag this backend already
  carried and never read. Found by the new cross-backend parity harness, not by looking at Studio.

- **The WebGPU backend reports its display scale, so the web UI is no longer half size.**
  `BackendResources.density` was left at its `1f` default, while the canvas is sized in physical
  pixels -- so on any 2x display the whole UI laid out as though one dp were one physical pixel and
  came out at half the size it does on desktop, with distance-field text sampled far below the size
  it was tuned for. Vulkan has always derived the ratio from its window's logical extent; WebGPU now
  asks the platform, which on wasmJs is `devicePixelRatio`. Native desktop WebGPU still reports 1x:
  it never sees the GLFW window the ratio would come from.

- **Delete, undo and duplicate no longer drop components in silence.** `SceneEntitySnapshot` copied
  seven named component types; everything else an entity carried was not captured, so it did not
  come back and nothing reported the loss -- delete an NPC and undo, and it returned without its
  behaviour. `SceneComponentSnapshotter` is the seam a module registers a copy through, capture is
  driven by `World.componentTypes`, and a type with no snapshotter is warned about rather than
  skipped quietly. `PhysicsBody` and the AI behaviours are covered; `PhysicsBody.handle` is
  deliberately not, so a restored entity gets a fresh body instead of a pointer to a destroyed one.
- **The slider's bar tracked its value again**, along with the progress bar's. Both drew through
  `drawWithCache`, which rebuilds on size, density and layout direction only, so a drag changed the
  value while the paint kept the fraction from the frame that built the cache.
- **Terrain collision works on wasmJs.** It was refused outright, so the terrain showcase would
  have thrown in a browser. JoltPhysics.js ships heightfields in full and the case was simply
  unimplemented. iOS genuinely has none -- JoltC exposes no heightfield settings -- and still
  refuses.
- **Android and iOS compile again.** Both were left unbuildable by the floating-origin work:
  Android never got `shiftOrigin` at all, and iOS called a JoltC function it had not imported and
  passed pointers where that binding takes values.
- **The heightfield showcase renders again.** Its follow camera flew out of the scene, taking the
  view with it. `EngineShowcaseLoader` built every rig with `offsetPosition = camera.lens.center`
  -- the same `Vec3f` instance, not a copy -- and `CameraSystem` writes the pivot it computes back
  into `lens.center`. With no target the pivot is the offset and nothing changes, which is why it
  sat there harmlessly; with a target the pivot is `target.position + offsetPosition`, so the
  character's position was added again every frame. Nothing threw and nothing logged, which is
  what made it look like a rendering fault.
- **The third-person camera keeps its distance.** Its collision sweep starts on the character, and
  a sweep that starts in contact reports a fraction of zero -- so anything touching the player
  collapsed the eye onto them and the near plane clipped the world away. There is now a floor on
  how close it may be pulled, and it sweeps static geometry only, so crates rolling past no longer
  shove the view.
- **The character no longer hovers, or stalls against a step it can climb.** Ground was reported
  the moment the probe saw a surface, and since being grounded is what stops a caller accumulating
  gravity, nothing pulled the character the rest of the way down; it stood in the air. And the
  step probe advanced by whatever motion was left, which at walking pace leaves the capsule on its
  own rounded shoulder at the edge, where the sweep reports the edge rather than the top face and
  the step is refused.

- **A lit surface no longer shadows itself where the light grazes it.** A face nearly parallel to
  the light has almost no area in the shadow map, so its texels hold whatever stands above it and
  the face reads as shadowed by itself; no bias fixes that, because the stored depth is a
  different surface. The lookup now steps `texel / nDotL` along the normal, and -- the part that
  actually mattered -- the cascade is selected from the fragment's own position rather than from
  the offset one, so the offset can no longer change which cascade a fragment reads.
- **Turning shadows off no longer darkens the scene.** A primary draw with no cascades was handed
  `mvp + light.directional` -- a block sized for the UNSHADOWED lit shader -- while the pipeline
  was still the shadowed one, so material, camera position and fog read whatever the buffer held.
  A renderer that owns a depth target now always writes the full block, with a cascade set that
  shadows nothing. Measured: the frame's peak brightness went from 104 with shadows off to 144,
  matching shadows on.
- **A headless frame can go through the on-screen path.** `HeadlessSurface` now allocates
  stand-in swapchain images, so `Renderer.draw` -- acquire, record, submit -- runs without a
  display, and `readPresentedPixels` copies the frame it produced back. `renderToTexture` is a
  different recording, and the two have diverged before; this is how they get compared.
- **An app's render plan can be rendered without a window.** `HeadlessSurface` makes
  `VulkanEngine` create its device and swapchain headlessly, so a test can boot an app's OWN plan
  -- its content features, depth passes and pipelines -- instead of a hand-built fixture. Every
  shadow probe until now built its own single-pipeline renderer, which cannot reproduce a scene
  that looks wrong on screen while the probes pass.
- **Cascades can be turned off, not just outlined.** The showcase's "Shadow cascades" toggle drew
  cascade wireframes; there was no way to see the scene WITHOUT cascades, which is the only way to
  see what they buy. `WorldDebugSettings.cascadedShadows` switches the fit back to the single
  fixed box, and the showcase exposes it as its own checkbox.
- **An authored sun points where the scene says it does.** `SceneLight` had no `direction` field,
  and `SceneJson` ignores unknown keys, so every scene that authored one -- the cascaded-shadows
  showcase among them -- was lit from the engine's default angle instead. Nothing failed: the
  light existed and the shadows fell, just not where the file said. Found by making a test load
  the real scene rather than a copy of its numbers.
- **Shadows are fitted to a shadow DISTANCE, not to the camera's far plane.** A 1000m view gave a
  5m-wide scene cascades 329m, 676m and 2338m across -- texels of 16cm to 114cm, on which a 2m box
  is six texels wide and no amount of bias helps. `DEFAULT_SHADOW_DISTANCE` is 100m; a camera that
  sees less than that is unaffected.
- **A cascade slice keeps its camera's projection.** The per-slice `Lens` copied eye, centre and
  field of view but not `projection`/`orthoHalfHeight`, so every slice of an orthographic camera
  was fitted as a perspective cone -- the other half of the frustum fix below.
- **An orthographic camera is culled and shadowed as the box it is.** `Frustum.corners` derived a
  cone from the field of view whatever the lens said, so culling, the spatial index's frustum
  query, the debug overlay and the shadow cascade fit were all wrong together for such a camera:
  a 5m-wide top-down view was fitted with a 480m cascade.
- **A single-box light had no shadow bias at all.** Bias and normal offset are now sized in
  cascade texels, and a cascade set built from matrices alone -- which is what a non-cascaded
  light produces -- reported a texel size of zero, so both came out zero and every lit surface
  self-shadowed. The scale is now read off the matrix instead of defaulted.
- **Shadow bias is measured in texels, not metres.** A texel is the unit the error is actually in:
  the map stores one depth per texel, so a surface is misrepresented by its own slope across it.
  A fixed distance is too much in a near cascade and too little in a far one.
- **Shadows no longer scale over their own lit surfaces.** A metres-valued bias is the right unit
  but the wrong tool for a far cascade, whose texel covers tens of centimetres of slope -- more
  error than any bias small enough to keep the shadow attached. The lookup now steps along the
  surface normal by two texels of whichever cascade it lands in, so the fragment reads its
  neighbour's depth instead of its own. Acne and peter-panning are the two ends of one knob, and
  both are now asserted on the same scene.
- **A heightmap is centred on its own origin.** `generate { cube() }` and `plane()` already were;
  a heightmap put its corner at the origin, so a tile's `Transform.position` sat most of a tile
  from anything drawn -- which LOD distance, culling, origin rebasing and collider placement all
  read. `heightAtWorld`, the mesh builder, the nav bake, Jolt's heightfield offset and
  `MeshCellStreamer`'s cell placement all move together.
- **Shadow bias is a distance in metres, not a number in NDC.** The bias was a constant in
  shadow-map depth, and each cascade maps a different world depth range into 0..1 -- so the same
  constant was a centimetre in the near cascade and half a metre in the far one, which is what
  peter-panned the far one. Cascades now upload their NDC-depth-per-world-unit and the shader
  scales a metres-valued bias by it.

---

## Older Releases

For releases prior to `0.1.0-dev.8` (`v0.1.0-dev.1` through `v0.1.0-dev.7`),
see [CHANGELOG Archive](docs/archive/CHANGELOG-v0.1-archive.md).

[unreleased]: https://github.com/awakekt/awake/compare/v0.1.0-dev.9...HEAD

[0.1.0-dev.9]: https://github.com/awakekt/awake/compare/v0.1.0-dev.8...v0.1.0-dev.9

[0.1.0-dev.8]: https://github.com/awakekt/awake/compare/v0.1.0-dev.7...v0.1.0-dev.8
