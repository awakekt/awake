# Render plans and shaders

<p class="awake-lede">A render plan names the pipelines an app draws with. Each pipeline gets a shader set: WGSL for both backends, taken from the shader pack or written in Kotlin with the shader DSL.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">module: <code>awake:asset:shaders</code></span>
<span class="awake-badge">module: <code>awake:asset:shader-pack</code></span>
<span class="awake-badge">module: <code>awake:asset:shader-dsl</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge awake-badge--ok">WebGPU</span>
</div>

Render plans and shaders are Kotlin only. A scene document and AwakeKt Studio pick components; which
pipelines exist is decided by the app's plan.

## Declare a render plan

Declare the plan once, in `commonMain`. The same plan goes to `runVulkanDesktopGame` and to
`WebGpuEngine`.

```kotlin title="Kotlin"
--8<-- "awake/asset/shader-pack/src/desktopTest/kotlin/com/awakekt/awake/asset/shaderpack/ShadersDocsSampleTest.kt:plan"
```

The backend builds one pipeline for each entry: here the primary pipeline, the particle pipeline,
and the sky. A mesh draws through the pipeline for its vertex format; a mesh whose format has no
pipeline is not drawn.

`RenderPlan`:

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `primary` | `ScenePipeline` | required | Draws every mesh that has no pipeline of its own. |
| `scenePipelines` | `List<ScenePipeline>` | empty | More pipelines, one per `PipelineKey`. |
| `contentFeatures` | `List<ContentFeatureSource>` | empty | Whole-frame passes such as the sky and depth fog. |
| `depthPrePassShaderSet` | `ShaderSet?` | none | Renders shadow maps. With none, nothing casts a shadow. |
| `sceneDepthShaderSet` | `ShaderSet?` | none | Renders camera depth for passes that read it, such as depth fog. |
| `depthPrePassVariants` | `Map<DepthCasterKind, ShaderSet>` | empty | Shadow shaders for instanced, skinned and particle casters. |
| `depthPrePassKeyedVariants` | `Map<DepthRenderKey, ShaderSet>` | empty | Shadow shaders by caster kind and alpha mode, such as masked textures. |
| `sceneDepthVariants` | `Map<DepthCasterKind, ShaderSet>` | empty | Camera-depth shaders by caster kind. |

`ScenePipeline`:

| Property | Type | Default | What it does |
| --- | --- | --- | --- |
| `key` | `PipelineKey` | required | Which draws use it. |
| `shaders` | `ShaderSet` | required | The vertex and fragment shaders for both backends. |
| `vertexFormat` | `VertexFormat` | required | The vertex layout its meshes use. |
| `variant` | `PipelineVariant` | `Opaque` | Blend and depth state, such as `Instanced` or `AlphaBlendedParticle`. |
| `bindingLayout` | `BindingLayout` | `Standard` | The resource groups the shader expects. |
| `materialBindings` | `GroupBindings?` | none | The material group's bindings, such as `GroupBindings.UniformOnlyMaterial`. |
| `usesMaterialGroup` | `Boolean` | `true` | Whether draws bind a material. |
| `buildWireframe`, `buildBackCulled`, `buildTransparent` | `Boolean` | `false` | Build these variants too. The primary pipeline always gets all three. |
| `depthShaders` | `ShaderSet?` | none | This pipeline's own shadow-depth shaders. |

`PipelineKey`:

| Key | Draws |
| --- | --- |
| `Primary` | Everything without another pipeline. Only for `RenderPlan.primary`. |
| `Format(vertexFormat)` | `MeshRenderer` meshes of that format, such as `PositionNormalColorUv` (textured) or `PositionNormalColorSkin` (skinned). |
| `Instanced` | `InstancedMeshRenderer`. |
| `InstancedFormat(vertexFormat)` | Instanced meshes of another format. |
| `SkinnedInstanced` | `InstancedSkinnedMeshRenderer`. |
| `Particle` | `ParticleEmitter`. |
| `Content(name)` | A content feature; made for you from `contentFeatures`. |

## Use the shader pack

`PackShaderSets` (`awake:asset:shader-pack`) holds the engine's ready-made shader sets:

| Shader set | Use it for |
| --- | --- |
| `LitShadow` | The primary pipeline: lit, shadowed, fogged `PositionNormalColor` meshes. |
| `Textured`, `InstancedTextured` | glTF-style PBR textured meshes (`PositionNormalColorUv`). |
| `Instanced` | `PipelineKey.Instanced`. |
| `Skinned`, `SkinnedTextured`, `SkinnedInstanced` | Skinned meshes. |
| `Particle` | `PipelineKey.Particle`. |
| `Skybox`, `SkyboxCubemap` | The gradient and cubemap skies. |
| `Terrain`, `InfiniteGrid` | Terrain and an editor-style ground grid. |
| `ShadowDepth` and its `…ShadowDepth` companions | `depthPrePassShaderSet` and `depthPrePassVariants`. |
| `SceneDepth` | `sceneDepthShaderSet`. |
| `Triangle` | A minimal unlit shader. |

Content features from the same module:

| Function | What it adds |
| --- | --- |
| `skyboxContentFeature(PackShaderSets.Skybox)` | The gradient sky from the scene's `skybox` component. |
| `depthFogContentFeature(color, isVisible)` | Full-screen fog from scene depth. `color`'s alpha is the density. Needs `sceneDepthShaderSet`. |
| `skyboxCubemapContentFeature(cubemap, exposure)` | A cubemap sky. `SkyboxCubemapSystem` attaches it at run time; see [Sky and fog](sky-and-fog.md). |

The Engine Showcase's plan uses every one of these; it is in
[`EngineShowcaseRenderPlan.kt`](https://github.com/awakekt/awake/blob/main/samples/engine-showcase/src/commonMain/kotlin/com/awakekt/awake/showcase/app/EngineShowcaseRenderPlan.kt).

## Write a shader in Kotlin

The AwakeKt shader DSL (ASL, `awake:asset:shader-dsl`) describes a shader in Kotlin and emits WGSL.
`aslShaderSet` turns it into a shader set for both backends:

```kotlin title="Kotlin"
--8<-- "awake/asset/shader-pack/src/desktopTest/kotlin/com/awakekt/awake/asset/shaderpack/ShadersDocsSampleTest.kt:asl-imports"

--8<-- "awake/asset/shader-pack/src/desktopTest/kotlin/com/awakekt/awake/asset/shaderpack/ShadersDocsSampleTest.kt:asl"
```

Operators, swizzles such as `.xyz`, constructors such as `vec4` and `.lit` are extension functions
in `com.awakekt.awake.asset.shaderdsl`; import the ones a shader uses.

The main pieces of `shader(name) { }`:

| Call | What it declares |
| --- | --- |
| `uniformBlock(structName, group, binding)` and `.field(shape)` | A uniform buffer and its fields. |
| `varyings(structName)` and `.varying(shape, location)` | Values passed from the vertex to the fragment stage. `out.position` is the clip-space position. |
| `texture2d`, `textureCube`, `texture2dArray`, `sampler`, `samplerComparison` (and depth variants) | Textures and samplers, by `group` and `binding`. |
| `const(name, value)` | A WGSL constant. |
| `fn(name, returns) { }` | A helper function. |
| `vertex { }` | The vertex stage. `inputsFrom(VertexFormat)` reads a mesh's attributes; `input(shape, location)` reads one. |
| `fragment { }` | The fragment stage. `colorOutput(value)` writes the colour; `discard()` drops the pixel. |
| `let`, `variable`, `assign`, `iff`, `loopI32`, `loopU32` | Statements inside a stage or function. |

An invalid definition throws `AslDefinitionException` when `shader(...)` builds it.
`aslShaderSet(::myShader)` takes a function of `ClipSpace` instead, for shaders that differ between
Vulkan and WebGPU clip-space conventions.

To use WGSL files instead, `shaderSet(name, bindingsByGroup)` loads
`assets/shader/vulkan/<name>.wgsl` and `assets/shader/webgpu/<name>.wgsl`.

## How it works

The plan is backend-neutral. When the engine starts, it asks the plan for its pipeline requests and
builds each one. Vulkan compiles each shader's WGSL to SPIR-V at load, once per shader, with the
built-in naga compiler; see [Shader compilation](shader-compilation.md). WebGPU hands the WGSL to the
browser.

A backend states what it can run as `RenderCapabilities`, and `narrowedTo` removes the rest, reporting
each removal. An unsupported primary pipeline throws instead, because every mesh without its own
pipeline draws through it:

```kotlin title="Kotlin"
--8<-- "awake/asset/shader-pack/src/desktopTest/kotlin/com/awakekt/awake/asset/shaderpack/ShadersDocsSampleTest.kt:narrow"
```

The WebGPU backend narrows every plan this way when it starts; see [WebGPU backend](webgpu.md).

!!! warning "Entry points are `vertexMain` and `fragmentMain`"
    `aslShaderSet` addresses the emitted WGSL by those two names. Keep the defaults of
    `vertex { }` and `fragment { }`.

!!! warning "A pipeline shader must match its pipeline's bindings"
    A shader in a `ScenePipeline` is bound with that pipeline's `bindingLayout` and
    `materialBindings`. A shader whose groups and bindings differ from them fails when the pipeline
    is built.

!!! tip "Preview a shader in the terminal"
    `./gradlew :awake:asset:shader-dsl:previewShader` renders the sample checker shader's fragment
    stage as coloured text, with no GPU. Pass `-Pargs="<width> <height> --wgsl"` to size it and print
    its WGSL.

## See also

- [Shader compilation](shader-compilation.md) for validating WGSL and compiling it at run time.
- [Vulkan backend](vulkan.md) and [WebGPU backend](webgpu.md) for how each backend runs a plan.
- [Meshes and materials](meshes-and-materials.md) for which components each pipeline draws.
