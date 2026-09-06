/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.PipelineRequest

/** Which backend is resolving a [RenderPlan]. The only thing a backend adds to one. */
enum class RenderBackend {
    Vulkan,
    WebGpu,
}

/**
 * A content feature that has not picked a backend yet.
 *
 * The deferral [ScenePipeline] already uses, in the one place it was missing: `ScenePipeline`
 * stores a whole [ShaderSet] and lets `toRequests` pick the half, while a content feature used to
 * take the selector at construction. That single difference is what forced an app to declare its
 * feature list once per backend, since `ShaderSet::vulkan` cannot be written in `commonMain`
 * without also writing `ShaderSet::webGpu` somewhere else.
 */
fun interface ContentFeatureSource {
    /** @param backend supplied by the engine, which already knows which one it is. */
    fun resolve(backend: RenderBackend): ContentFeature
}

/** [this]'s half for [backend] -- the one line every resolver would otherwise write by hand. */
fun ShaderSet.stagesFor(backend: RenderBackend): ShaderStages = when (backend) {
    RenderBackend.Vulkan -> vulkan
    RenderBackend.WebGpu -> webGpu
}

/**
 * What an app renders with, independent of which backend renders it.
 *
 * Every field was already backend-neutral and already a parameter on both engines -- this only
 * stops the list being *declared* twice. `samples/studio` used to author it in
 * `StudioVulkanBootstrap.kt` (appMain) and `StudioWebGpuBootstrap.kt` (wasmJsMain), source sets
 * that cannot see each other, and they had already drifted: the WebGPU one silently omits the
 * skinned pipeline.
 *
 * A per-backend difference stays expressible, and has to be written down to exist: `copy()` at
 * one visible line with the reason beside it, rather than an absence in a second file.
 *
 * @property primary The pipeline every mesh draws through unless its vertex format says
 * otherwise. Separate from [scenePipelines] so "this app has a primary" is a compile error to
 * get wrong rather than a runtime one.
 * @property scenePipelines Everything past [primary], declared by whoever needs it.
 * @property contentFeatures Scene-pass features the app opts into, in paint order. Unresolved --
 * see [ContentFeatureSource].
 * @property depthPrePassShaderSet Opts into the shadow-map pre-pass -- depth rendered from the
 * light, which `lit_shadow` samples. Despite the general name this is the light's depth, not the
 * camera's; see [sceneDepthShaderSet] for that.
 * @property sceneDepthShaderSet Opts into a camera-space depth pre-pass, bound at
 * [com.awakekt.awake.render.pipeline.BindingSemantic.SceneDepth] for anything that
 * needs the depth already in front of it -- water, soft particles, depth fog.
 *
 * A full extra geometry pass per frame, which is why it is opt-in rather than always on. The
 * scene pass cannot supply this itself: content features draw inside it, and neither backend
 * lets a shader sample the depth attachment it is currently writing.
 */
data class RenderPlan(
    val primary: ScenePipeline,
    val scenePipelines: List<ScenePipeline> = emptyList(),
    val contentFeatures: List<ContentFeatureSource> = emptyList(),
    val depthPrePassShaderSet: ShaderSet? = null,
    val sceneDepthShaderSet: ShaderSet? = null,
) {
    /** [contentFeatures] resolved against [backend]. */
    fun contentFeaturesFor(backend: RenderBackend): List<ContentFeature> =
        contentFeatures.map { it.resolve(backend) }

    /**
     * Every pipeline [backend] has to compile for this plan: the primary, its companions, each
     * scene pipeline, and one per content feature.
     *
     * Both engines built this list themselves, in the same order, from the same fields, with the
     * same three companion flags and near-identical comments explaining them. Which pipelines
     * exist is a decision, not a driver detail -- and each backend deciding it separately is how
     * WebGPU shipped without an alpha-blended pipeline for as long as Vulkan had one.
     *
     * The companion flags are unconditional on purpose. Whether a wireframe pipeline *exists* is
     * not a per-app choice: `Renderer.wireframe` is the runtime toggle, and back-face culling and
     * transparency opt in per entity and per draw. All three were once constructor flags that
     * every call site set to true and none set to false.
     */
    fun toPipelineRequests(backend: RenderBackend): List<PipelineRequest> = buildList {
        add(
            PipelineRequest(
                key = PipelineKey.Primary,
                spec = primary.shaders.stagesFor(backend).spec(primary.vertexFormat),
                buildWireframe = true,
                buildBackCulled = true,
                buildTransparent = true,
            ),
        )
        addAll(scenePipelines.toRequests { it.stagesFor(backend) })
        addAll(
            contentFeaturesFor(backend).map {
                PipelineRequest(key = PipelineKey.Content(it.name), spec = it.spec)
            },
        )
    }
}

/**
 * What a backend can actually run, declared by that backend.
 *
 * Here rather than as a `require` on the plan, because a plan an app authors once has to survive
 * meeting a backend that supports less of it. `WebGpuEngine` used to reject a non-null
 * [RenderPlan.depthPrePassShaderSet] outright, which made a whole-app plan unusable on web -- and
 * that is exactly why `samples/studio` narrowed its own plan by hand in a per-target file, the
 * duplication [RenderPlan] exists to remove.
 *
 * @property backend Which backend this describes. Named so a report says who dropped what.
 * @property depthPrePass Whether a depth-only pre-pass yields something a shader can sample.
 * @property supportsPipeline False for a pipeline this backend cannot run. A backend states its
 * own gap here; nothing above it has to know.
 */
class RenderCapabilities(
    val backend: RenderBackend,
    val depthPrePass: Boolean,
    val supportsPipeline: (ScenePipeline) -> Boolean = { true },
)

/**
 * [this] reduced to what [capabilities] can run, reporting every omission through [report].
 *
 * Dropping is deliberately loud. The alternative -- an app narrowing its own plan per target --
 * is what drifted: studio's WebGPU list had lost a pipeline, and the reason survived only as a
 * comment in a file nothing could compare against. A backend that quietly rendered less would be
 * worse still, so every omission names who dropped it and why.
 *
 * An unsupported [RenderPlan.primary] throws instead of dropping: every mesh without its own
 * pipeline falls back to it, so narrowing it away renders nothing at all. That is a
 * misconfiguration, not a capability gap.
 */
fun RenderPlan.narrowedTo(
    capabilities: RenderCapabilities,
    report: (String) -> Unit = ::println,
): RenderPlan {
    val name = capabilities.backend.name
    require(capabilities.supportsPipeline(primary)) {
        "$name cannot run this plan's primary pipeline (${primary.key}). Every mesh without its " +
            "own pipeline draws through the primary, so narrowing it away would render nothing."
    }
    scenePipelines.filterNot(capabilities.supportsPipeline).forEach {
        report("$name: dropped scene pipeline ${it.key} -- this backend cannot run it.")
    }
    val depthPrePass = depthPrePassShaderSet?.takeIf { capabilities.depthPrePass }
    if (depthPrePassShaderSet != null && depthPrePass == null) {
        report("$name: dropped the depth pre-pass -- no shader here can sample it.")
    }
    // Same capability: scene depth renders through the same DepthTarget/DepthOnlyPipeline the
    // shadow pre-pass does, only from the camera, so a backend supports both or neither.
    val sceneDepth = sceneDepthShaderSet?.takeIf { capabilities.depthPrePass }
    if (sceneDepthShaderSet != null && sceneDepth == null) {
        report("$name: dropped the scene-depth pass -- no shader here can sample it.")
    }
    return copy(
        scenePipelines = scenePipelines.filter(capabilities.supportsPipeline),
        depthPrePassShaderSet = depthPrePass,
        sceneDepthShaderSet = sceneDepth,
    )
}
