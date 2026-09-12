# Implementation Plan: HAL vs Render Graph — Decoupling `render:contract`

## Background & Executive Summary

`render:contract` is Awake Engine's **Hardware Abstraction Layer (HAL)**. Over time, it accumulated
scene-level, content-level, and shader-specific vocabulary that has no place in a hardware
interface. Types like `SceneLight`, `DrawCall`, `Lens`, shadow cascade parameters, environment
uniforms, skybox fields, and scene-aware debug geometry were placed directly in `render:contract`.
Because both Vulkan (`awake:backend:vulkan`) and WebGPU (`awake:backend:webgpu`) backends depend on
`render:contract`, they inherited this high-level vocabulary, coupling the GPU drivers directly to
scene constructs.

This revised plan addresses the critical architectural, Gradle boundary, and backend decoupling blockers:

1. **Eliminates Gradle Circular Dependencies**: `awake:engine:render:passes` depends on
   `awake:engine:render:contract`. `render:contract` MUST NOT depend on `render:passes`. We define a
   pure, backend-agnostic HAL input descriptor (`GpuPassInput` / `GpuSubPass` / `GpuDrawCommand`) inside
   `render:contract`. High-level scene representations (`GpuSceneFrame`, `ScenePassGraph`) live in
   `render:passes` and compile down to `GpuPassInput` before entering the HAL.
2. **Guarantees 100% Backend Decoupling (Zero Game-Authored Vocabulary)**:
   - **Pre-Pass Generalization**: The backend's `DepthPrePassFeature` and `RendererCommandRecording` currently accept
     `ShadowCascadeUniforms` and compute `light.shadowCascades()`. In this plan, shadow cascade and scene depth passes
     are compiled by the render graph into generic `GpuSubPass` array-layer render commands. Backends execute these
     sub-passes generically without knowing what a "light" or "shadow" is.
   - **RenderFrameContext Purged**: `RenderFrameContext` in `render:passes` currently holds `val light: SceneLight` and
     `val environment: EnvironmentUniforms`. These are replaced by generic `passUniforms: FloatArray` or feature-specific
     uniform buffers, allowing `RendererFrameContext.kt` (Vulkan) and `WebGpuFrameContext.kt` (WebGPU) to drop all scene imports.
   - **Content-Free HAL**: Removes content-specific properties (`showSky: Boolean`, `shadowsEnabled`,
     `shadowCascades: ShadowCascadeUniforms?`, `fogDensity`, `fogColor`) from HAL inputs. Skybox drawing is scheduled as
     standard draw commands via a `RenderFeature`, and fog/lighting/shadow data is pre-packed into raw uniform
     buffers before entering the backend.
   - **Instancing, Skinning & Particle Handling**: Preserves hardware-level instancing (`instanceBuffer`,
     `instanceJointPalettes`, `instanceColors`, `instanceFrames`) inside `GpuDrawCommand` as raw GPU buffer handles and
     instance counts, avoiding content knowledge while preserving zero-allocation batching.
3. **Corrects Repository State & Ledger**: Correctly lists the exact 5 exempt backend files per
   backend tracked in `build-logic`, replaces non-existent file references, audits
   `GpuDevice.DEFAULT_UNIFORM_FLOAT_COUNT = 24` as Layer 2 debt, and numbers the decision **D31** in
   `docs/reference/decision-log.md`.
4. **Clarifies Skill Rules & Staging**: Updates `.agents/skills/awake-render-pipeline/SKILL.md` to
   distinguish between future prohibitions and tracked legacy debt.

---

## Full Audit: `render:contract` Vocabulary Breakdown

### Layer 1 — TRUE HAL Vocabulary (Keep in `render:contract`)

These types describe hardware capabilities, GPU resource management, and execution controls:

| Type / File                                                                                                                              | Function / Rationale                                                   |
|------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| `GpuDevice.kt`                                                                                                                           | Core hardware interface: pipelines, textures, buffers, device lifetime |
| `Renderer.kt` (`clearColor`, `wireframe`, `debugMode`, `sceneViewport`, `drawUi`, `drawDebugLines`, `readPixels`, `awaitFrameResources`) | GPU execution and presentation controls                                |
| `RenderViewport.kt`                                                                                                                      | Scissor rect and viewport dimensions                                   |
| `RenderTarget.kt`                                                                                                                        | Framebuffer attachments and render targets                             |
| `TextureAsset.kt`, `MipChain.kt`                                                                                                         | Raw texture GPU handles and descriptors                                |
| `Material.kt`                                                                                                                            | Descriptor set + uniform buffer handle                                 |
| `Mesh.kt`                                                                                                                                | Vertex / index buffer GPU handle                                       |
| `PipelineSpec.kt`, `PipelineTable.kt`, `PipelineRegistry.kt`, `PipelineVariant.kt`                                                       | Pipeline state objects and descriptors                                 |
| `ShaderSource.kt`, `BindingLayout.kt`, `GroupBindings.kt`                                                                                | Shader binding reflection and layout descriptors                       |
| `UiPipelineDescriptor.kt`, `UiTargetCompositeMode`                                                                                       | UI composite pass descriptors                                          |
| `CullMode.kt`                                                                                                                            | Rasterizer state enum                                                  |
| `FrameCapture.kt`, `PixelMap.kt`                                                                                                         | Raw GPU pixel readback structures                                      |
| `UniformLayout.kt`, `UniformWriter.kt`, `UniformField`, `UniformFields`                                                                  | GPU byte/float packing primitives                                      |
| `LineSegment.kt`                                                                                                                         | Primitive geometry for debug line drawing                              |

---

### Layer 2 — SCENE / CONTENT Vocabulary (Debt to move out of `render:contract`)

#### Group A — Scene Shading & Lighting Data (Move to `render:passes` or `render:passes/uniforms`)

| Misplaced Type / Constant                                                                             | Current Location  | Correct Target Location                                    | Problem / Rationale                                                    |
|-------------------------------------------------------------------------------------------------------|-------------------|------------------------------------------------------------|------------------------------------------------------------------------|
| `SceneLight.kt` (`SceneLight`, `PointLight`, `MAX_POINT_LIGHTS`)                                      | `render:contract` | `render:passes/uniforms`                                   | A light is a scene object, not a GPU primitive.                        |
| `EnvironmentUniforms.kt`                                                                              | `render:contract` | `render:passes/uniforms`                                   | Sky, fog, and shadow properties are scene choices.                     |
| `ScenePassDescriptor.kt`                                                                              | `render:contract` | `render:passes`                                            | Combines `SceneLight + EnvironmentUniforms + Lens + DrawCall`.         |
| `DrawCall.kt`                                                                                         | `render:contract` | `render:passes`                                            | High-level scene draw object (mesh + material + transform + skinning). |
| `Renderer.DEFAULT_SCENE_LIGHT`, `DEFAULT_HORIZON_COLOR`, `DEFAULT_ZENITH_COLOR`, `DEFAULT_FOG_COLOR`  | `render:contract` | `render:passes/uniforms/SceneDefaults.kt`                  | Scene default constants inside the HAL interface.                      |
| `GpuDevice.DEFAULT_UNIFORM_FLOAT_COUNT = 24`                                                          | `render:contract` | `render:passes/uniforms/`                                  | Hardcodes `MVP (16) + SceneLight (8)` assumptions on `GpuDevice`.      |
| `Renderer.draw(camera: Lens, drawCalls: List<DrawCall>, light: SceneLight)`                           | `render:contract` | Replaced by `draw(input: GpuPassInput)`                    | Primary abstract method takes scene objects.                           |
| `Renderer.shadowsEnabled`, `showEnvironment`, `horizonColor`, `zenithColor`, `fogColor`, `fogDensity` | `render:contract` | Delete (handled via `EnvironmentUniforms` in render graph) | Scene properties on `Renderer`.                                        |

#### Group B — Shadow & Depth Pass Data (Move to `render:passes` / `render:passes/uniforms`)

| Misplaced Type / Constant                                              | Current Location  | Correct Target Location  | Problem / Rationale                              |
|------------------------------------------------------------------------|-------------------|--------------------------|--------------------------------------------------|
| `ShadowCascades.kt` (`cascadeSplitDistances`, frustum box calculation) | `render:contract` | `render:passes`          | Shadow cascade math is a render-graph algorithm. |
| `DirectionalShadowBox.kt`                                              | `render:contract` | `render:passes`          | Frustum geometry calculation.                    |
| `ShadowCascadeUniforms.kt`                                             | `render:contract` | `render:passes/uniforms` | Shadow map uniform layout.                       |

#### Group C — Content-Specific Uniform Layouts (Move to `asset:shader-pack` or `render:passes`)

| Misplaced Type / Constant                                         | Current Location  | Correct Target Location               | Problem / Rationale                                               |
|-------------------------------------------------------------------|-------------------|---------------------------------------|-------------------------------------------------------------------|
| `SkyboxUniforms.kt`, `SkyboxFields.kt`                            | `render:contract` | `asset:shader-pack`                   | Skybox shader fields and defaults belong with the skybox feature. |
| `ParticleUniforms.kt`                                             | `render:contract` | `asset:shader-pack` / `render:passes` | Particle system uniform layouts.                                  |
| `DepthFogFields.kt`                                               | `render:contract` | `asset:shader-pack`                   | Fog rendering uniform fields.                                     |
| `InfiniteGridFields.kt`                                           | `render:contract` | `asset:shader-pack`                   | Editor grid uniform layout.                                       |
| `DebugGeometry.kt` (`frustumDebugLines(camera: Lens, ...)`, etc.) | `render:contract` | `render:passes`                       | Takes scene camera (`Lens`) to generate lines.                    |

---

## Current Repository State & Tracked Exemptions

The `verifyBackendLayering` Gradle task (
`build-logic/src/main/kotlin/com.awakekt.awake.plugin.backend-layering.gradle.kts`) tracks the exact
files still importing scene vocabulary.

### Tracked Exempt Files (Known Debt — Exactly 5 per backend):

**Vulkan Backend (`awake:backend:vulkan`)**:

1. `renderer/RendererCommandRecording.kt`
2. `renderer/RendererDraw3D.kt`
3. `renderer/RendererFrameContext.kt`
4. `renderer/RendererOffscreen.kt`
5. `renderer/Renderer.kt`

**WebGPU Backend (`awake:backend:webgpu`)**:

1. `renderer/RendererDraw3D.kt`
2. `renderer/RendererOffscreen.kt`
3. `renderer/RendererOpaqueDraws.kt`
4. `renderer/WebGpuFrameContext.kt`
5. `renderer/Renderer.kt`

> [!NOTE]
> All 10 files import scene types because `Renderer.draw(camera: Lens, drawCalls, light: SceneLight)`
> currently requires them in its abstract signature, and `RendererFrameContext` forces `SceneLight` and
> `EnvironmentUniforms` into the backend frame context. Once `Renderer.draw()` and `RenderFrameContext` are
> updated in Phase 2, these imports will be deleted and the exemption list will reach 0.

---

## Architectural Deep Dive: Complete Backend Cleansing

To ensure Vulkan and WebGPU have **zero visible game-authored vocabulary (lights, sky, fog, shadow cascades)**:

### 1. The Shadow Cascade & Depth Pre-Pass Leak
- **Problem**: Today, `DepthPrePassFeature.kt` and `RendererCommandRecording.kt` import `ShadowCascadeUniforms` and call `light.shadowCascades()`. This couples the backend directly to directional sun shadows and cascade math.
- **Solution**: Shadow cascades are not special GPU hardware states; they are simply render passes that draw geometry into a depth texture (or layer of an array texture) using an orthogonal view-projection matrix and depth bias.
  In this plan, `GpuPassInput` models pre-passes as a list of generic `GpuSubPass` instances. The render graph computes the cascade matrices, sets the target depth layer index, and passes a `GpuSubPass`. The backend only executes:
  ```kotlin
  for (subPass in input.prePasses) {
      recordSubPass(subPass.target, subPass.layer, subPass.viewProjection, subPass.draws)
  }
  ```
  The words `Shadow`, `Cascade`, and `Light` vanish from backend code.

### 2. The `RenderFrameContext` Vocabulary Leak
- **Problem**: `RenderFrameContext` currently exposes `val light: SceneLight`, `val environment: EnvironmentUniforms`, `val horizonColor: Color`, `val zenithColor: Color`. Sibling backend files (`RendererFrameContext.kt` and `WebGpuFrameContext.kt`) must implement these properties.
- **Solution**: Purge these properties from `RenderFrameContext`. Scene lighting and environment parameters are pre-packed into raw uniform blocks by `render:passes` before command recording. Custom features read pre-allocated uniform slots or `context.passUniforms`.

### 3. CPU Uniform Packing in Backends
- **Problem**: `RendererDraw3D.kt` (Vulkan and WebGPU) currently calls `sceneLightUniforms(light, cameraPosition, ...)` and `litShadowUniforms(...)` inside its per-draw loop.
- **Solution**: All lighting and fog uniform packing is moved to the render graph preparation phase (`render:passes`). The HAL only binds already-packed UBOs and descriptor sets.

### 4. Instancing, Skinning, and Billboard Particles
- **Problem**: High-level `DrawCall` contains `instanceJointPalettes`, `instanceColors`, and `instanceFrames`. If `GpuDrawCommand` only took `Mat4`, animated meshes and particles would be dropped.
- **Solution**: `GpuDrawCommand` retains the low-level hardware buffer handles and instance counts without knowing game semantics:
  - `instances: Int = 1`
  - `instanceVertexBuffer: BufferHandle? = null` (instance model matrices or particle positions)
  - `jointPaletteBinding: MaterialBinding? = null` (skinned joint matrices)
  - `instanceColorBuffer: BufferHandle? = null` (per-instance vertex attributes)
  - `instanceFrameBuffer: BufferHandle? = null` (per-instance sprite frame)

---

## Phase 1 Execution Plan: Documentation, Decision Log & Skills (Zero Code Changes)

Phase 1 establishes the architectural rules and documentation baseline without modifying production code.

### 1. Decision Log

Update [docs/reference/decision-log.md](file:///Users/ronvaldoz/StudioProjects/awaken/docs/reference/decision-log.md)
with **Decision D31**:

- **D31**: `GpuDevice`/`Renderer` is a pure hardware interface; scene vocabulary does not cross the HAL.
- Documents root cause, vocabulary groups A–D, current backend debt, and the two-phase resolution roadmap.
- Details the complete elimination of `SceneLight`, `EnvironmentUniforms`, and `ShadowCascadeUniforms` from backends.

### 2. Reference Documentation

- **[docs/reference/render-hardware-interface.md](file:///Users/ronvaldoz/StudioProjects/awaken/docs/reference/render-hardware-interface.md)**:
  Add section **"HAL vs Render Graph — The Complete Vocabulary Boundary"**, incorporating the layer diagram,
  vocabulary classification tables, and the `GpuSubPass` / `GpuPassInput` abstraction.
- **[docs/reference/render-extensibility.md](file:///Users/ronvaldoz/StudioProjects/awaken/docs/reference/render-extensibility.md)**:
  Clarify the distinction between capability (hardware passes, attachments, buffers) and content (lighting, sky, fog, shadows).

### 3. Agent Skills & Agent Personas

- **[.agents/skills/awake-render-pipeline/SKILL.md](file:///Users/ronvaldoz/StudioProjects/awaken/.agents/skills/awake-render-pipeline/SKILL.md)**:
  - Add **§0.5 Two-Layer Rendering Model**.
  - Clarify the §1 "speculative generality" guard: explicitly note that it applies only to `RenderFeature` registration and pass dispatch order, and must not be used to justify adding scene fields to `Renderer`.
  - Add explicit staging guidance: distinguish future prohibitions (prohibiting *new* scene imports/fields in `render:contract`) from tracked legacy debt (existing signatures on `Renderer.kt` slated for Phase 2).
- **[.agents/skills/awake-render-vulkan/SKILL.md](file:///Users/ronvaldoz/StudioProjects/awaken/.agents/skills/awake-render-vulkan/SKILL.md)** & **[awake-render-webgpu/SKILL.md](file:///Users/ronvaldoz/StudioProjects/awaken/.agents/skills/awake-render-webgpu/SKILL.md)**:
  - Add the exact 5 exempt files per backend as tracked legacy debt.
  - Document that Phase 2 will achieve 0 exempt files and complete removal of all lighting/env/shadow imports.
- **[.agents/skills/awake/agents/awake-render-backend-engineer.md](file:///Users/ronvaldoz/StudioProjects/awaken/.agents/skills/awake/agents/awake-render-backend-engineer.md)**:
  - Update agent prompt and working rules: replace references to deprecated mutable properties (`shadowsEnabled`, `fogColor`, `skybox`) with pure HAL primitives (`GpuPassInput`, `GpuSubPass`, `GpuDrawCommand`).
  - Add explicit rule: a backend renderer receives pre-packed GPU passes only and must have zero knowledge of scene lights, camera, environment, or shadows.

### 4. Project Entry Points & Documentation

- Update [README.md](file:///Users/ronvaldoz/StudioProjects/awaken/README.md), [AGENTS.md](file:///Users/ronvaldoz/StudioProjects/awaken/AGENTS.md), and [GEMINI.md](file:///Users/ronvaldoz/StudioProjects/awaken/GEMINI.md) to highlight the HAL vs Render Graph boundary rule and reference `awake-render-pipeline` §0.5.
- Update [docs/reference/module-architecture.md](file:///Users/ronvaldoz/StudioProjects/awaken/docs/reference/module-architecture.md) and [docs/reference/glossary/3d.md](file:///Users/ronvaldoz/StudioProjects/awaken/docs/reference/glossary/3d.md) to define `GpuPassInput`, `GpuSubPass`, and the complete HAL isolation boundary.

---

## Phase 2 Execution Plan: Target Shapes & Code Migration

> [!IMPORTANT]
> Phase 2 contains code modifications and will be executed under a separate task approval following Phase 1 completion.

### Gradle Architecture & Module Dependencies

```
┌────────────────────────────────────────────────────────┐
│  awake:scene:rendering (RenderSystem, ECS integration)  │
└───────────────────────────┬────────────────────────────┘
                            │ api(project(":awake:engine:render:passes"))
┌───────────────────────────▼────────────────────────────┐
│  awake:engine:render:passes                            │
│  Owns: GpuSceneFrame, DrawCall, SceneLight, Lens,      │
│        EnvironmentUniforms, ShadowCascade calculation, │
│        RenderFeature dispatch, uniform packing         │
└───────────────────────────┬────────────────────────────┘
                            │ api(project(":awake:engine:render:contract"))
┌───────────────────────────▼────────────────────────────┐
│  awake:engine:render:contract                          │
│  Owns: Renderer, GpuDevice, GpuPassInput, GpuSubPass,  │
│        GpuDrawCommand, Mesh, Material, Mat4, UBOs      │
└────────────────────────────────────────────────────────┘
```

1. `awake:engine:render:passes` depends on `awake:engine:render:contract`.
2. `awake:scene:rendering` depends on `awake:engine:render:passes` and `awake:engine:render:contract`.
3. `awake:engine:render:contract` depends on NO higher-level engine modules.

---

### Target Shapes (HAL Interface vs Render Graph)

#### 1. HAL Input Primitives (`awake:engine:render:contract`)

```kotlin
// awake:engine:render:contract — GpuDrawCommand.kt
/**
 * A single draw command resolved to hardware primitives.
 */
data class GpuDrawCommand(
    val mesh: Mesh,
    val material: Material,
    val transform: Mat4,
    val instances: Int = 1,
    val instanceVertexBuffer: BufferHandle? = null,
    val jointPaletteBinding: MaterialBinding? = null,
    val instanceColorBuffer: BufferHandle? = null,
    val instanceFrameBuffer: BufferHandle? = null,
)

// awake:engine:render:contract — GpuSubPass.kt
/**
 * A generic hardware render pass execution request.
 * Used for pre-passes (depth pre-pass, shadow map cascades) or offscreen passes.
 */
data class GpuSubPass(
    val target: RenderTarget?,
    val targetLayer: Int = 0,
    val viewProjection: Mat4,
    val viewport: RenderViewport? = null,
    val draws: List<GpuDrawCommand> = emptyList(),
    val passUniforms: FloatArray = FloatArray(0),
    val depthBiasConstant: Float = 0f,
    val depthBiasSlope: Float = 0f,
)

// awake:engine:render:contract — GpuPassInput.kt
/**
 * Fully pre-packed, hardware-ready pass input for the HAL.
 *
 * References ONLY HAL primitives (Mat4, Vec3f, GpuSubPass, GpuDrawCommand, FloatArray).
 * Contains NO SceneLight, Lens, EnvironmentUniforms, or content flags.
 */
data class GpuPassInput(
    /** Any passes executing before the main scene (e.g. depth / shadow cascade layers). */
    val prePasses: List<GpuSubPass> = emptyList(),
    /** Combined View-Projection matrix for the primary scene pass. */
    val viewProjection: Mat4,
    /** Camera world position for distance-based calculations / sorting. */
    val cameraEye: Vec3f,
    /** Opaque draws pre-sorted and batched by pipeline/material. */
    val opaqueDraws: List<GpuDrawCommand>,
    /** Transparent draws pre-sorted back-to-front. */
    val transparentDraws: List<GpuDrawCommand>,
    /** Pre-packed uniform bytes (lighting, fog, shadow matrices) as raw floats. */
    val passUniforms: FloatArray,
) {
    companion object {
        val EMPTY = GpuPassInput(
            viewProjection = Mat4.IDENTITY,
            cameraEye = Vec3f.ZERO,
            opaqueDraws = emptyList(),
            transparentDraws = emptyList(),
            passUniforms = FloatArray(0),
        )
    }
}
```

#### 2. Target `Renderer` Interface (`awake:engine:render:contract`)

```kotlin
// awake:engine:render:contract — Renderer.kt
interface Renderer : GpuDevice {

    // ── Hardware controls ──────────────────────────────────────────────────
    var clearColor: Color
    var wireframe: Boolean
    var debugMode: Boolean
    var sceneViewport: RenderViewport?

    // ── Core draw entry points (Pure HAL) ─────────────────────────────────

    /**
     * Executes rendering from pre-packed GPU pass input.
     *
     * The HAL receives raw matrices, generic sub-passes, pre-sorted draw commands,
     * and uniform floats. It never imports or inspects SceneLight, DrawCall,
     * Lens, EnvironmentUniforms, or ShadowCascadeUniforms.
     */
    fun draw(input: GpuPassInput)

    /**
     * Renders [input] into offscreen [target] instead of swapchain.
     */
    fun renderToTexture(target: RenderTarget, input: GpuPassInput)

    // ── Pixel readback & UI overlay ───────────────────────────────────────
    suspend fun readPixels(target: RenderTarget): TextureAsset
    fun drawUi(primitives: List<UiDrawPrimitive>, font: UiFont? = null)
    fun drawUiToTexture(
        target: RenderTarget,
        primitives: List<UiDrawPrimitive>,
        font: UiFont? = null
    )
    fun compositeUiTargets(
        destination: RenderTarget,
        source: RenderTarget,
        output: RenderTarget,
        mode: UiTargetCompositeMode
    )

    // ── Debug lines ────────────────────────────────────────────────────────
    fun drawDebugLines(lines: List<LineSegment>)

    fun awaitFrameResources() = Unit
    fun presentWithoutScene() = draw(GpuPassInput.EMPTY)
}
```

#### 3. Render Graph Compiled Frame (`awake:engine:render:passes`)

`GpuSceneFrame` lives in `render:passes` and holds high-level scene data before compiling down into `GpuPassInput`:

```kotlin
// awake:engine:render:passes — GpuSceneFrame.kt
data class GpuSceneFrame(
    val lens: Lens,
    val drawCalls: List<DrawCall>,
    val light: SceneLight?,
    val environment: EnvironmentUniforms,
    val depthTarget: DepthTarget? = null,
) {
    fun toPassInput(clipSpace: ClipSpace, aspect: Float): GpuPassInput {
        val vp = lens.viewProjectionMatrix(aspect, clipSpace)
        val packedUniforms = UniformWriter.pack {
            // pack light, fog, and shadow matrices into raw FloatArray
        }
        
        // Compile shadow cascades into generic GpuSubPass instances
        val prePasses = mutableListOf<GpuSubPass>()
        if (environment.shadowsEnabled && light != null && depthTarget != null) {
            val cascades = light.shadowCascades(lens, aspect, clipSpace)
            cascades.forEachIndexed { layer, cascadeVp ->
                prePasses.add(
                    GpuSubPass(
                        target = depthTarget,
                        targetLayer = layer,
                        viewProjection = cascadeVp,
                        draws = drawCalls.toShadowDepthCommands(cascadeVp),
                        depthBiasConstant = SHADOW_DEPTH_BIAS_CONSTANT,
                        depthBiasSlope = SHADOW_DEPTH_BIAS_SLOPE,
                    )
                )
            }
        }

        val (opaque, transparent) = drawCalls.toGpuCommands(vp)
        return GpuPassInput(
            prePasses = prePasses,
            viewProjection = vp,
            cameraEye = lens.eye,
            opaqueDraws = opaque,
            transparentDraws = transparent,
            passUniforms = packedUniforms,
        )
    }
}
```

#### 4. Clean Backend Implementation (`awake:backend:vulkan` & `awake:backend:webgpu`)

After Phase 2, how the backends will look:

##### A. `Renderer.kt` (Thin & Hardware-Only)
`Renderer.kt` becomes an ultra-thin GPU driver coordinator (~100–120 lines total).
- **All scene properties gone**: No `shadowsEnabled`, `showEnvironment`, `horizonColor`, `zenithColor`, `fogColor`, `fogDensity`.
- **All scene overloads gone**: No `draw(camera, drawCalls, light)`.
- **Only 2 draw entry points**:
  ```kotlin
  override fun draw(input: GpuPassInput) = performDraw(input)
  override fun renderToTexture(target: RenderTarget, input: GpuPassInput) = performOffscreen(target, input)
  ```
- Retains only device resources: pipeline table, swapchain, render targets, and command pools.

##### B. `RendererDraw3D.kt` (No Scene Logic, No CPU Uniform Packing)
`RendererDraw3D.kt` shrinks dramatically from ~450 lines to a straightforward hardware command recording loop:
```kotlin
// Vulkan & WebGPU RendererDraw3D.kt
internal fun Renderer.performDraw(input: GpuPassInput) {
    val currentFrame = swapchainManager.currentFrame
    val imageIndex = acquireSwapchainImage(currentFrame) ?: return

    Vulkan.vkResetCommandBuffer(commandBuffers[currentFrame], 0)
    recordCommandBuffer(
        commandBuffer = commandBuffers[currentFrame],
        frameIndex = currentFrame,
        imageIndex = imageIndex,
        input = input,
    )
    submitAndPresent(currentFrame, imageIndex)
}

internal fun Renderer.recordCommandBuffer(
    commandBuffer: Long,
    frameIndex: Int,
    acquiredImageIndex: Int,
    input: GpuPassInput,
) {
    // 1. Generic pre-passes (depth pre-pass, shadow cascade layers, etc.)
    for (prePass in input.prePasses) {
        recordSubPass(commandBuffer, prePass)
    }

    // 2. Main swapchain render pass
    beginMainPass(commandBuffer, acquiredImageIndex)
    
    // 3. Upload pre-packed pass uniform bytes directly to UBO
    if (input.passUniforms.isNotEmpty()) {
        uploadPassUniforms(commandBuffer, input.passUniforms)
    }

    // 4. Record pre-sorted opaque & transparent draw commands
    for (cmd in input.opaqueDraws) {
        recordDrawCommand(commandBuffer, cmd)
    }
    for (cmd in input.transparentDraws) {
        recordDrawCommand(commandBuffer, cmd)
    }

    endMainPass(commandBuffer)
}
```

##### C. Total Decoupling Outcome:
- **Zero Game-Authored Knowledge**: The backend does not know whether a draw is a shadow caster, a skybox, a character mesh, or a particle. It only sees `Mesh`, `Material`, transforms, and buffer bindings.
- **Trivial Authoring for New Games & Features**: To add a new pass (e.g. SSAO, water reflection, custom depth, volumetric fog), a game developer authors a `RenderFeature` or compiles a `GpuSubPass` in `render:passes` or `scene:rendering`. **Zero edits to Vulkan or WebGPU backend code are ever required again.**

---

## Step-by-Step Code Migration Sequence (Phase 2 Progress Tracker)

- [x] **Step 1 — Introduce HAL Primitives**: Create `GpuDrawCommand.kt`, `GpuSubPass.kt`, and `GpuPassInput.kt` in
   `awake:engine:render:contract`. Introduce `GpuMesh` and `GpuMaterial` interfaces with backwards-compatible typealiases.
- [x] **Step 2 — Default Constant Extraction**: Move `DEFAULT_SCENE_LIGHT`, `DEFAULT_HORIZON_COLOR`, `DEFAULT_ZENITH_COLOR`,
   `DEFAULT_FOG_COLOR`, and `DEFAULT_UNIFORM_FLOAT_COUNT = 24` from `Renderer.kt` and `GpuDevice.kt` to `render:passes/uniforms/SceneDefaults.kt`.
- [x] **Step 3 — Render Graph Compilation Primitive**: Implement `GpuSceneFrame` in `render:passes` with `toPassInput(clipSpace, aspect)`
   to compile high-level scene data down to `GpuPassInput`.
- [x] **Step 4 — Add HAL Signatures to `Renderer`**: Add `fun draw(input: GpuPassInput)` and `fun renderToTexture(target, input: GpuPassInput)`
   to `render:contract/renderer/Renderer.kt`, implementing them on Vulkan `Renderer`, WebGPU `Renderer`, and `NoopRenderer`.
- [x] **Step 5 — Wire Scene Callers to `draw(GpuPassInput)`**:
  - `awake:scene:rendering` depends on `awake:engine:render:passes`.
  - `RenderSystem.kt` builds `GpuSceneFrame` and invokes `renderer.draw(passInput)`.
- [x] **Step 6 — Wire Swapchain & Offscreen `GpuPassInput`**: Both Vulkan and WebGPU `performDraw` and `performRenderToTexture` now execute `GpuPassInput`.
- [x] **Step 7 — Purge `RenderFrameContext`**: Removed `light` and `environment` from `RenderFrameContext` and backend context adapters (ledger dropped from 10 to 8 files).
- [x] **Step 8 — Cleanse `RendererCommandRecording.kt`**: Cleaned `SceneLight` and `EnvironmentUniforms` from Vulkan `RendererCommandRecording.kt` (ledger dropped from 8 to 7 files).
- [x] **Step 9 — Cleanse `RendererOffscreen.kt`**: Delegated legacy scene offscreen paths to `GpuSceneFrame.toPassInput` across both backends.
- [x] **Step 10 — Final Backend Driver Extraction & Zero-Exemption Ledger**:
  - Extract backend-internal `PreparedDrawCall` from `DrawCall` dependency so `RendererDraw3D.kt` and `RendererOpaqueDraws.kt` become pure hardware command loops.
  - Set `exemptBackendFiles = emptyList()` in `backend-layering.gradle.kts` and verify `verifyBackendLayering` passes with 0 exemptions.

---

## Expected Results & Acceptance Criteria

Upon completion of both Phase 1 and Phase 2, the system will satisfy the following concrete invariants:

### 1. Zero Backend Exemptions (`verifyBackendLayering` = 0)
- The exempt backend files list in `build-logic/src/main/kotlin/com.awakekt.awake.plugin.backend-layering.gradle.kts` will be reduced from 5 per backend to **0**.
- `forbiddenBackendImports` checks in both `awake:backend:vulkan` and `awake:backend:webgpu` pass with no exceptions.

### 2. Complete Elimination of Game-Authored Vocabulary in GPU Backends
- Neither `awake:backend:vulkan` nor `awake:backend:webgpu` imports or references any of:
  - `SceneLight`, `PointLight`
  - `EnvironmentUniforms`
  - `ShadowCascadeUniforms`, `shadowCascades`, `directionalShadowBox`
  - `DrawCall`
  - `Lens`
- All mutable scene properties on `Renderer` (`shadowsEnabled`, `showEnvironment`, `horizonColor`, `zenithColor`, `fogColor`, `fogDensity`) are completely deleted.

### 3. Ultra-Thin Hardware Driver Layer
- `Renderer.kt` in both backends shrinks to ~100–120 lines, containing only device resources and two hardware draw entry points (`draw(GpuPassInput)` and `renderToTexture(RenderTarget, GpuPassInput)`).
- `RendererDraw3D.kt` in both backends shrinks from ~450 lines to ~80–120 lines, containing zero CPU lighting calculations and zero uniform packing loops.

### 4. Acyclic, Extensible Render Pipeline Architecture
- `render:contract` depends on no higher-level engine modules.
- New game render passes (e.g. SSAO, water reflection, custom depth, volumetric fog) can be authored entirely in `render:passes` or `scene:rendering` as `GpuSubPass` / `RenderFeature` instances without modifying a single line of backend driver code.

### 5. Passing Build, Tests, and Spotless Gate
- All desktop tests across `:awake:engine:render:contract`, `:awake:engine:render:passes`, `:awake:backend:vulkan`, `:awake:backend:webgpu`, and `:awake:scene:rendering` pass green.
- `spotlessCheck` and `apiDump` pass cleanly.

---

## Verification Plan

### Phase 1 Verification

- Review updated skill files and documentation.
- Verify doc link resolution across markdown files.

### Phase 2 Verification (Build & Test Gate)

Run the complete suite of verification checks:

```bash
./gradlew :awake:engine:render:contract:desktopTest \
          :awake:engine:render:passes:desktopTest \
          :awake:backend:vulkan:desktopTest \
          :awake:backend:webgpu:desktopTest \
          :awake:scene:rendering:desktopTest \
          apiDump spotlessCheck verifyBackendLayering
```

