# Studio Infinite Grid with Infinite Axis Lines

Formalize an unprojected, multi-scale **Infinite Grid** with **Infinite Axis Lines** (X red / Z blue / Y green) in Awake Engine's 3D viewport, replacing finite grid line helpers with a procedural shader-driven grid pass and wiring it to Studio's editor viewport controls and tool rail.

---

## User Review Required

> [!IMPORTANT]
> **Shader-Based Unprojected Grid vs. Finite Wireframe Buffer**
> Today's `Grid.lines(...)` builds a fixed `List<Pair<Vec3f, Vec3f>>` with a fixed spatial extent (e.g., 20x20 units) uploaded via `Renderer.drawDebugLines`. An **Infinite Grid** uses a procedural ASL shader (`AslInfiniteGridShader`) rendered over an unprojected full-screen triangle. It calculates world-space ground plane intersections (`Y=0`), anti-aliased primary/secondary grid subdivisions using screen derivatives (`fwidth`/`dpdx`/`dpdy`), infinite X (red) and Z (blue) axis lines, and exponential horizon distance fading.

> [!NOTE]
> **Render Pipeline Placement**
> The infinite grid pass will execute as an opaque/alpha-blended debug pass after opaque scene geometry and before gizmo/UI overlays. Depth testing ensures scene geometry on or above the ground plane occludes the grid, while depth writing writes the exact world-space intersection depth to the depth buffer.

---

## Open Questions

1. **Subdivision Auto-scaling (LOD):** Should the grid dynamically transition scale steps (e.g., 0.1m -> 1m -> 10m -> 100m) based on camera altitude, or maintain a fixed user-configured primary/secondary step size?
   - *Proposal:* Implement logarithmic altitude-based sub-grid auto-scaling with smooth alpha blending between levels (matching Blender/Godot behavior) while defaulting to 1.0m primary / 0.1m secondary grid lines.

2. **Toolbar & Viewport Control Binding:** Should pressing the inert "Grid" button on Studio's tool rail toggle the viewport grid visibility, or open a popover for grid settings (spacing, snapping, axis visibility)?
   - *Proposal:* Pressing the tool rail "Grid" button toggles `WorldDebugSettings.showGrid`. A quick-toggle pill in `SceneViewportControls` adds quick options for grid scale and snapping.

---

## Proposed Changes

### Core Math (`awake:core:math`)

#### [MODIFY] [Grid.kt](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/math/src/commonMain/kotlin/com/awakekt/awake/core/math/Grid.kt)
- Expand `Grid` object to include plane intersection math helpers (`intersectGroundPlane(rayOrigin, rayDir, y = 0f)`).
- Add typed data model `GridSpec(size = 1.0f, subdivisions = 10, fadeDistance = 100f, showAxisLines = true)` for infinite grid shader uniform packing.
- Retain `lines(...)` for backward compatibility with bounded wireframe diagnostic calls.

#### [NEW] [GridTest.kt](file:///Users/ronvaldoz/StudioProjects/awaken/awake/core/math/src/commonTest/kotlin/com/awakekt/awake/core/math/GridTest.kt)
- Add unit tests verifying `intersectGroundPlane` math and `snapToGrid` rounding across positive, negative, and edge-case ray directions.

---

### Shaders & Shader Pack (`awake:asset:shaders` & `awake:asset:shader-pack`)

#### [NEW] [AslInfiniteGridShader.kt](file:///Users/ronvaldoz/StudioProjects/awaken/awake/asset/shader-pack/src/commonMain/kotlin/com/awakekt/awake/asset/shaderpack/AslInfiniteGridShader.kt)
- Define `InfiniteGridShader: AslShaderDefinition` using ASL DSL:
  - **Vertex stage:** Emits a full-screen triangle (`fullScreenTriangleCorner()`) at $z = 1.0$ far clip depth. Passes unprojected NDC coordinates to fragment stage.
  - **Fragment stage:**
    1. Unprojects NDC ray through `inverseViewProjection` to compute world-space direction `rayDir`.
    2. Calculates ground plane intersection `P = eye + t * rayDir` where $t = -eye.y / rayDir.y$. Discards pixels when $t < 0$ or looking above horizon.
    3. Calculates primary grid (1m) and subgrid (0.1m) lines using anti-aliased `fwidth(P.xz)`.
    4. Calculates infinite X axis ($|P.z| < axisWidth$, colored Red) and infinite Z axis ($|P.x| < axisWidth$, colored Blue) stretching to infinity.
    5. Applies distance-based falloff `exp(-distance * falloff)` to smoothly fade into background before clip plane.
    6. Outputs blended color and computes fragment depth.

#### [MODIFY] [EngineShaderSets.kt](file:///Users/ronvaldoz/StudioProjects/awaken/awake/asset/shaders/src/commonMain/kotlin/com/awakekt/awake/asset/shaders/EngineShaderSets.kt)
- Register `InfiniteGrid = aslShaderSet(InfiniteGridShader)` for Vulkan and WebGPU backend compilation.

---

### Scene & Rendering (`awake:scene:rendering`)

#### [MODIFY] [WorldDebugSettings.kt](file:///Users/ronvaldoz/StudioProjects/awaken/awake/scene/rendering/src/commonMain/kotlin/com/awakekt/awake/scene/rendering/debug/WorldDebugSettings.kt)
- Add `showGrid: Boolean = true` and `showAxisLines: Boolean = true` to `WorldDebugSettings`.
- Add configurable grid properties: `gridScale: Float = 1.0f`, `gridFadeDistance: Float = 100.0f`.

#### [MODIFY] [DebugVisualizationSystem.kt](file:///Users/ronvaldoz/StudioProjects/awaken/awake/scene/rendering/src/commonMain/kotlin/com/awakekt/awake/scene/rendering/debug/DebugVisualizationSystem.kt)
- Integrate infinite grid and infinite axis line parameters into `debugVisualizationLines` and the renderer pipeline pass execution.
- Maintain axis line accents (Red X-axis line, Blue Z-axis line, Green Y-axis origin indicator) as infinite line features.

---

### Editor & Studio Shell (`awake:editor:scene` & `apps:studio`)

#### [MODIFY] [EditorToolbar.kt](file:///Users/ronvaldoz/StudioProjects/awaken/awake/editor/src/commonMain/kotlin/com/awakekt/awake/editor/toolbar/EditorToolbar.kt)
- Wire the inert "Grid" rail button to toggle `WorldDebugSettings.showGrid`.

#### [MODIFY] [SceneViewportPanel.kt](file:///Users/ronvaldoz/StudioProjects/awaken/awake/editor/scene/src/commonMain/kotlin/com/awakekt/awake/editor/scene/viewport/SceneViewportPanel.kt)
- Add grid toggle button to viewport header pill controls alongside Wireframe and Shadow toggles.
- Connect grid visibility and axis line toggles to live `WorldDebugSettings`.

#### [MODIFY] [StudioShell.kt](file:///Users/ronvaldoz/StudioProjects/awaken/apps/studio/src/commonMain/kotlin/com/awakekt/awake/studio/ui/StudioShell.kt)
- Ensure `WorldDebugSettings` initialized on studio startup has `showGrid = true` and `showAxisLines = true` by default.

---

## Verification Plan

### Automated Tests
- Run math tests for `Grid` plane intersection and snapping:
  `./gradlew :awake:core:math:desktopTest`
- Run shader compilation validation tests for `InfiniteGridShader`:
  `./gradlew :awake:asset:shader-pack:desktopTest`
- Run Studio module wiring tests:
  `./gradlew :apps:studio:desktopTest`

### Manual Verification
- Launch Studio Desktop application:
  `./gradlew :apps:studio:run`
- Verify infinite grid in 3D scene viewport:
  - Grid extends to horizon with anti-aliased lines and smooth distance fading.
  - Infinite X axis (Red) and Z axis (Blue) lines run through the origin across the entire viewport.
  - Toggling "Grid" button on tool rail or viewport controls toggles infinite grid visibility live.
