# Render plans and shaders

<p class="awake-lede">A render plan names the pipelines an app draws with. Each pipeline gets a shader set: WGSL for both backends, taken from the shader pack or written in Kotlin with the shader DSL. A project can also ship a shader of its own as data.</p>

<div class="awake-badges" markdown>
<span class="awake-badge">component: <code>shader_effect</code></span>
<span class="awake-badge">module: <code>awake:asset:shaders</code></span>
<span class="awake-badge">module: <code>awake:asset:shader-pack</code></span>
<span class="awake-badge">module: <code>awake:asset:shader-dsl</code></span>
<span class="awake-badge">module: <code>awake:asset:shader-document</code></span>
<span class="awake-badge">module: <code>awake:scene:shader</code></span>
<span class="awake-badge awake-badge--ok">Vulkan</span>
<span class="awake-badge awake-badge--ok">WebGPU</span>
</div>

Render plans are Kotlin. A scene document and AwakeKt Studio pick components, and the app's plan
decides which pipelines exist. A shader can also be project data: a project ships a shader document, a
JSON file, and a `shader_effect` component draws it, with no Kotlin. See
[Ship a shader with a project](#ship-a-shader-with-a-project).

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
| `depthPrePassKeyedVariants` | `Map<DepthRenderKey, ShaderSet>` | empty | Shadow shaders by caster kind and alpha mode. A masked caster casts only through one, so map `DepthRenderKey(kind, AlphaMode.Masked)` to `PackShaderSets.MaskedTexturedShadowDepth` (`Ordinary`), `InstancedMaskedTexturedShadowDepth` (`Instanced`) or `SkinnedMaskedTexturedShadowDepth` (`Skinned`). |
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

## Ship a shader with a project

A project can bring its own shader with no Kotlin and no change to the app's plan. A shader document
is a JSON file in the project, such as `shaders/gradient-sky.shader.json`. A `shader_effect` component
names it by path, and the engine checks it, compiles it to WGSL for both backends and draws it. This
document draws a sky that mixes two colours by how far up each pixel looks:

```json title="gradient-sky.shader.json"
--8<-- "website/docs/snippets/rendering/gradient-sky.shader.json"
```

A node names the document and sets its parameters:

```json title="shader-effect.scene.json"
--8<-- "website/docs/snippets/rendering/shader-effect.scene.json"
```

`loadProject` reads every document a scene names, once each, and `runProject` draws them. In
your own app, call `loadShaderEffects` with the decoded scene document and the project's
`AssetSource`, and give the result to a `ShaderEffectSystem`. The system attaches each document's
pipeline after the engine starts, through `host`, which is `renderer as? ContentFeatureHost`. Both
backends implement it:

```kotlin title="Kotlin"
--8<-- "awake/scene/shader/src/desktopTest/kotlin/com/awakekt/awake/scene/shader/ShaderEffectDocsSampleTest.kt:attach"
```

Add the system to the scene's frame systems, and call `system.release()` when the scene closes.

### What a document says

- **A surface.** `background` fills the screen behind the scene, as a sky does. `overlay` fills the
  screen over it, alpha-blended. `plane` is a rectangle in the scene, placed by its node's transform
  and depth-tested against scene geometry. A plane can also have a vertex stage that moves each
  vertex along its normal, and a blend of `additive`.
- **Parameters.** A document declares each parameter's name, type (`float`, `vec2`, `vec3`, `vec4` or
  `color`) and default. A scene sets one by name, with as many numbers as its type holds. A parameter
  it leaves out takes the default.
- **Textures.** A document names up to four images. A scene gives each one the project path of an
  image, and the document reads it with `sample`.
- **Expressions and statements.** A document computes each pixel's colour from its parameters, its
  textures, inputs the engine supplies each frame (`uv`, `screenUv`, `worldPosition`, `normal`,
  `viewDirection`, `cameraPosition`, `sunDirection`, `time`, `deltaTime` and `resolution`) and
  expressions over them: arithmetic, comparisons, swizzles, vectors, and a fixed set of WGSL
  built-ins. Its statements are `let`, `var`, `set`, `if`, `for` with literal bounds, and `discard_if`.

A document cannot say functions, storage buffers, compute, raw WGSL, binding numbers, or a loop whose
bound is computed. The [shader document reference](../reference/shader-document.md) has a row for
every op, statement, input, function and limit.

### Limits

`ShaderDocuments.compile` checks a document against `ShaderDocumentLimits` before anything compiles.
The limits bound what a document can cost: its size, how deep its JSON and expressions nest, how many
expressions and statements it has, how much work each pixel does (a loop's body counts once for each
iteration), loop iterations and nesting, texture reads per pixel, and how many parameters, textures
and locals it declares. `loadShaderEffects` uses the defaults, which the
[reference](../reference/shader-document.md#limits) lists.

!!! warning "The limits are provisional"
    The defaults bound the cost of a document, but they have not been measured on a slow device.
    Expect them to change.

### When a document fails

A bad document never stops a scene from loading. `loadShaderEffects` does not throw for a project's
content. It logs the failure and the effect is not attached, and the scene plays on without it:

- A document that cannot be read, decoded or compiled is logged with every problem. Each names its
  path in the document and the parameter, texture or local involved.
- An effect that does not match its document is logged with its node, the document and the problem:
  a parameter the document does not declare, a parameter with the wrong count of numbers, a texture
  the effect gives no loaded image for, or a texture the document does not declare.
- An image that cannot be read or decoded is logged, and the effects that need it are not drawn.
- A backend that refuses to attach an effect is logged. An effect that was already drawing keeps
  drawing as it was.
- A renderer that is not a `ContentFeatureHost` gets one warning, and nothing is drawn.

An effect whose parameters later stop matching its document is hidden until they match again.

### Preview an edit

To show an edited document, load the project's documents again and give the running system the result:

```kotlin title="Kotlin"
--8<-- "awake/scene/shader/src/desktopTest/kotlin/com/awakekt/awake/scene/shader/ShaderEffectDocsSampleTest.kt:preview"
```

The system attaches again each effect whose document or images are new objects. The old effect keeps
drawing until the new one is ready, and its clock carries on. A document that no longer compiles is
logged and leaves the last good one on screen. Replacing an effect's component with one that has
another document or other textures attaches again too; other parameters, or `enabled`, change in
place.

Hot reload, replacing a running shader without attaching again, is a follow-up:
[#214](https://github.com/awakekt/awake/issues/214) for WebGPU and
[#217](https://github.com/awakekt/awake/issues/217) for UI pipelines. Preview does not wait for it.

### What a shader document does not do

This is Stage 1: a document draws as a pass of its own, a screen surface or a plane. It does not:

- **Take raw WGSL.** A document is data the engine checks and turns into WGSL itself.
- **Draw on an arbitrary mesh.** A draw names a mesh and a material, not a pipeline. A shader on any
  scene mesh is Stage 2, which needs both backends to route draws, and it is not built.
- **Read scene depth or colour.** A document cannot read what is already drawn. An overlay is a blend
  over the frame, not post-processing, and a pass that samples scene depth, such as depth fog, takes a
  plan entry written in Kotlin.
- **Cast or receive shadows.** A plane is not drawn into a shadow map, reads none, and takes no light
  but what its own code computes.
- **Sort against transparent surfaces.** A plane with an `alpha` or `additive` blend draws after the
  opaque geometry, depth-tested, but nothing orders it against other transparent surfaces.
- **Replace a texture, or choose a mip level.** A texture is uploaded once, when its effect attaches,
  and `sample` reads the image's base level (LOD 0).
- **Attach for free.** Each attach compiles a pipeline, which takes 1 to 50 ms.

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

- [Shader document reference](../reference/shader-document.md) for every op, statement, input, function and limit of a shader document.
- [`shader_effect`](../reference/scene-document-components.md#shader_effect) for the fields of the scene component that draws one.
- [Shader compilation](shader-compilation.md) for validating WGSL and compiling it at run time.
- [Vulkan backend](vulkan.md) and [WebGPU backend](webgpu.md) for how each backend runs a plan.
- [Meshes and materials](meshes-and-materials.md) for which components each pipeline draws.
