/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.spec
import com.awakekt.awake.asset.shaders.stagesFor
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.SkyboxRenderFeature
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.renderer.SkyboxUniformLayout

/**
 * The procedural sky, as a feature an app opts into.
 *
 * Here in the shader pack, beside `skybox.wgsl` itself, rather than in either backend. Both used
 * to carry a `SkyboxRenderPipeline` and a pass adapter -- the same feature written twice, and a
 * GPU backend knowing what a sky is (see `docs/reference/render-extensibility.md`). This declares
 * a pipeline instead of building one: `VertexFormat.None` for the generated full-screen triangle,
 * [SkyboxUniformLayout] for the block the pipeline owns, and [PipelineVariant.Background] for
 * depth test and write both off. The engine's registry compiles it, on either backend.
 *
 * Opt-in for a resource reason rather than a taste one: `skybox.wgsl` ships in this module's own
 * shader directory, so only a consumer that syncs it has the compiled shader on its resource
 * path. An app that omits this feature leaves `Renderer.showEnvironment` an inert flag.
 *
 * Returns a [ContentFeatureSource], not a [ContentFeature]: the backend half of [shaders] is
 * picked when an engine resolves the plan, not when an app declares it. Taking the selector here
 * is what used to force `contentFeatures = listOf(...)` to be written once per backend, since
 * `ShaderSet::vulkan` cannot appear in `commonMain`.
 *
 * @param shaders The sky's shader set.
 */
fun skyboxContentFeature(shaders: ShaderSet): ContentFeatureSource = ContentFeatureSource { backend ->
    val stages = shaders.stagesFor(backend)
    ContentFeature(
        name = "skybox",
        spec = stages.spec(
            vertexFormat = VertexFormat.None,
            variant = PipelineVariant.Background,
            uniforms = SkyboxUniformLayout,
        ),
    ) { pipeline, uniforms, _ -> SkyboxRenderFeature(pipeline, uniforms) }
}
