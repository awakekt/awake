/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderdocument.ShaderDocumentException
import com.awakekt.awake.asset.shaderdocument.ShaderDocuments
import com.awakekt.awake.asset.shaders.AttachedContentFeature
import com.awakekt.awake.asset.shaders.ContentFeatureHost
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.Renderer
import kotlinx.coroutines.runBlocking

/**
 * Shader documents of the fixture project, the `shader-effects/` resources of this module, by name
 * without `.shader.json`. The shaders live only in those files, as a project's would: no Core or
 * backend source names them.
 */
fun shaderEffectDocument(name: String): String {
    val resource = checkNotNull(ShaderEffectView::class.java.getResource("/shader-effects/$name.shader.json")) {
        "No fixture shader document '$name'."
    }
    return resource.readText()
}

/** Where the camera stands for [renderShaderEffectScene]. */
enum class ShaderEffectView(internal val eye: Vec3f, internal val center: Vec3f) {
    /** Level with the horizon: the top half of the frame looks up, the bottom half down. */
    Horizon(Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, -1f)),

    /** Above and in front of the origin, looking down at a plane lying there. */
    Above(Vec3f(0f, 6f, 4f), Vec3f(0f, 0f, 0f)),
}

/**
 * Draws [documents], shader document texts, as a played scene draws its `shader_effect`s: each is
 * compiled, given inputs at [timeSeconds], and attached through the renderer's content-feature host.
 * A document the compiler rejects is left out, as the project loader leaves it out, so the frame is
 * what the scene draws without it.
 *
 * Needs a renderer that attaches content features; every feature is detached again before returning.
 */
fun Renderer.renderShaderEffectScene(
    documents: List<String>,
    view: ShaderEffectView = ShaderEffectView.Horizon,
    timeSeconds: Float = 0f,
    size: Int = SCENE_SIZE,
): ByteArray {
    val host = checkNotNull(this as? ContentFeatureHost) { "This renderer cannot attach content features." }
    val attached = documents.mapNotNull { text -> attachDocument(host, text, timeSeconds) }
    val target = createRenderTarget(size, size)
    return try {
        val lens = Lens(eye = view.eye, center = view.center, fovYRadians = 1f, near = 0.1f, far = 100f)
        renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = emptyList(),
                light = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f)),
                clipSpace = clipSpace,
                aspect = 1f,
                drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
            ),
        )
        runBlocking { readPixels(target) }.data
    } finally {
        target.destroy()
        attached.forEach(AttachedContentFeature::detach)
    }
}

private fun attachDocument(host: ContentFeatureHost, text: String, timeSeconds: Float): AttachedContentFeature? {
    val compiled = try {
        ShaderDocuments.compile(text)
    } catch (_: ShaderDocumentException) {
        return null
    }
    val inputs = compiled.newInputs().apply { this.timeSeconds = timeSeconds }
    return runBlocking { host.attachContentFeature(compiled.contentFeature(inputs, name = compiled.document.name)) }
}
