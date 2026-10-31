/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.webgpu.application.WebGpuEngine
import com.awakekt.awake.webgpu.device.createCanvasGpuContext
import web.dom.document
import web.html.HTMLCanvasElement

/** A booted WebGPU engine on a small canvas, for tests that only need its renderer. */
internal class ImageUploadEngine : WebGpuEngine(AppSpecBuilder().apply { window { size(SIZE, SIZE) } }.build().createLifecycle(), PLAN) {
    val errors = mutableListOf<String>()

    suspend fun boot(): Renderer {
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        create(createCanvasGpuContext(canvas, SIZE, SIZE) { errors += it.message })
        check(startupError == null) { "The engine did not start: $startupError" }
        return renderer as Renderer
    }

    fun destroy() = dispose()

    private companion object {
        const val SIZE = 16
        val PLAN = RenderPlan(
            primary = ScenePipeline(PipelineKey.Primary, PackShaderSets.LitShadow, VertexFormat.PositionNormalColor, materialBindings = GroupBindings.UniformOnlyMaterial),
            depthPrePassShaderSet = PackShaderSets.ShadowDepth,
            sceneDepthShaderSet = PackShaderSets.SceneDepth,
            scenePipelines = listOf(
                ScenePipeline(PipelineKey.Format(VertexFormat.PositionNormalColorUv), PackShaderSets.Textured, VertexFormat.PositionNormalColorUv),
            ),
        )
    }
}
