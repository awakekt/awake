# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **Milestone 3 physics and character-controller coverage.** Moved the real Jolt controller suite
  to multiplatform `commonTest`, including heightfield terrain, ground probes, traversal, and
  moving-platform behavior.
- **Named skeletal sockets.** glTF bone names are preserved, skeletons support name lookup, and
  socket attachments now follow joint translation and rotation in world space.

### Fixed

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
