# Render extensibility convention

Awake is a library/framework: a consumer must be able to build their own rendering content
(skybox, debug overlays, post-processing, custom UI) without forking a backend module.

That is the goal, not yet the state -- both backends still declare content of their own, tracked
below under Known gaps.

## The hard rule

**A graphics backend knows hardware only.** Pipelines, buffers, textures, samplers, command
recording. It must never know what a skybox or a shadow *is*.

Said the other way: the moment `awake:backend:vulkan` declares `SkyboxRenderPipeline`, the driver
layer has become a game. A fourth content feature then cannot be added without editing a backend --
and it has to be edited twice, once per backend, which is exactly how the two drifted apart before.

Enforced by `verifyBackendLayering` (`awake.backend-layering-convention.gradle.kts`), which runs on
`check` and rejects content vocabulary in any backend declaration. It checks *declared names*, so a
doc comment or a shader path naming a skybox stays legal -- explaining the thing is fine, declaring
it here is not.

## The content-versus-capability test

Ask what the backend would have to know:

| | Capability | Content |
|---|---|---|
| Question it answers | "what can this hardware do" | "what is being drawn" |
| Example | `LineRenderPipeline`, the UI pass, `drawDebugLines(...)`, `DepthTarget`, `DepthOnlyPipeline` | skybox, shadow, fog, particles, water |
| Who supplies the thing drawn | the app, every time | it *is* the thing drawn |
| Lives in | a backend module | shared content layer, or `samples/<game>/` |

`LineRenderPipeline` passes: nobody is forced to call it, and the real gizmo/overlay content lives
in `samples:*`. `SkyboxRenderPipeline` fails: the pipeline *is* the sky.

A name can also fail the test while the code passes it, which is how most of the split was
actually resolved -- see the names table below. `ShadowMap` held nothing but a depth image and a
descriptor set; the type was already a capability and only the name answered "what is being
drawn". Check the members before assuming a rename is cosmetic, and check them before assuming
it is not enough.

Note this rule is strictly stronger than the one this doc carried before. "Authored content is a
nullable, opt-in constructor param" was the old bar -- but a nullable
`skyboxRenderPipeline: SkyboxRenderPipeline?` still means the backend knows what a skybox is. Opt-in
is not the same as absent.

## Where content lives instead

| Tier | Module |
|---|---|
| Hardware | `awake:backend:vulkan`, `awake:backend:webgpu` |
| Engine-provided optional content (skybox, shadow) | `awake:asset:shaders` for the declaration, `awake:engine:render:passes` for shared recording |
| Game-authored content | `samples/<game>/` -- see `skills/awake-framework-boundary/SKILL.md` |

No new module is needed; all three already exist with the right dependency edges.

## Where this applies

- `awake:backend:vulkan`, `awake:backend:webgpu` -- every declaration in the module, checked by
  `verifyBackendLayering`.
- `awake:compose:*` and `awake:ui:designsystem` -- the same principle, with Compose runtime and
  Foundation supplying neutral mechanics while the design system owns opinionated recipes. See
  [ui-ownership.md](ui-ownership.md) for the current placement rules.
- Any future subsystem exposing a pluggable point (ECS systems, scene loaders) should
  default to the same test: is this *content* (opt-in, nullable/injected) or a
  *capability* (always-available, content-neutral)?

## The names the split produced

What each backend type is called now, and the content its old name carried. Reach for these when
naming the next one: the test is whether the name survives someone using the type for a different
purpose.

| Name | What it is | Was | Why the old name failed |
|---|---|---|---|
| `DepthTarget` | square depth-only render target: image, view, sampler, render pass, framebuffer, its own descriptor set at `DEPTH_SET` | `ShadowMap` | an occlusion or SSAO pre-pass wants the identical object |
| `DepthOnlyPipeline` | colorless `RenderPipeline` -- no fragment output, no colour attachment, no blend state | `ShadowRenderPipeline` | takes the *caller's* shaders, vertex format and descriptor layout; it never knew what it drew |
| `DepthPrePassFeature` | re-renders the frame's draws, depth only, into a `DepthTarget` before the scene pass | `ShadowFeature` | never sees a light -- the viewpoint is a transform the caller's vertex shader reads from the per-draw uniform buffer |
| `depthPrePassShaderSet` | opts a backend into that pre-pass | `shadowShaderSet` | what the depth is *for* is the shader's business |
| `ContentFeature` | a pipeline spec plus a record lambda, the supported way to author content | `VulkanContentFeature` / `WebGpuContentFeature` | one per backend meant writing every feature twice |

`shadowsEnabled` deliberately stays on the `Renderer` interface. That is app-facing vocabulary,
and shadow mapping is genuinely what a caller toggles -- the backend only reads a flag it did not
name.

## Known gaps

**The declaration rule is met.** `contentExemptBackendFiles` is empty as of `21e820127`; no
backend file declares skybox, shadow, particle, fog, terrain, water, decal, billboard or
occlusion. It ran 14 -> 0 across
[2026-08-23-backend-content-split-plan.md](../tasks/2026-08-23-backend-content-split-plan.md).

**Do not read that as "the backends carry no content."** `verifyBackendLayering` matches the
vocabulary against *declared names* only -- `class`/`interface`/`object`/`fun` plus a name.
Content in a call, a local, a property, or a well-chosen function name is invisible to it.

`RendererDraw3D` was the live example, and it is worth keeping as the illustration even though
it is fixed: it declared `lightViewProjection`, which decided what volume a directional light
covers. Real content, in the driver layer, and no forbidden word in a declared name -- it never
appeared on any list. `0a94382e3` moved it out: `SceneLight` carries `viewProjection`,
`RenderSystem` fills it in, and a backend renders depth from whatever matrix it is handed.

That one is **closed too** (`b19fe3e63`): `prepareDrawCalls` used to write
`lit_shadow.wgsl`'s uniform block field by field, and now calls the shared `litShadowUniforms`
in `render:passes` beside `texturedUniforms`. The layout that describes it is one declaration,
re-exported to the shader pack under the consumer-facing name.

**What is left in a backend is references, not decisions.** `RendererDraw3D` still names
`lit_shadow.wgsl` in doc comments and calls `directionalShadowTexelDepthScale`, which lives in
`render:contract` beside the constants it reads. A backend asking a shared function for a number
is the shape this rule wants; the naming in prose is deliberately legal, because a capability is
unreadable without saying what a consumer uses it for.

Widening the check to match line-level vocabulary would flag exactly that prose. Do not.

The shared `Renderer` contract leaks too: `showEnvironment`, `horizonColor` and `zenithColor` are
sky vocabulary on the hardware interface. A horizon colour is not a hardware capability -- it is
uniform data belonging to the skybox feature. Same plan, phase 3.
