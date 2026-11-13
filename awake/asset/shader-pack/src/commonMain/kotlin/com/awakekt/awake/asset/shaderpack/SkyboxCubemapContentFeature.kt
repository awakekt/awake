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
import com.awakekt.awake.core.math.inverse
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.passes.SharedSkyboxRenderFeature
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.texture.TextureAsset

/**
 * How a cubemap sky looks this frame, read each frame so a day cycle can change it without
 * reloading the panorama. The defaults draw the cubemap as baked.
 *
 * @property exposure Multiplies the sampled colour.
 * @property blend How far the first cubemap has blended into the second, 0 to 1.
 * @property tintR Red of the colour the sky is multiplied by.
 * @property tintG Green of the colour the sky is multiplied by.
 * @property tintB Blue of the colour the sky is multiplied by.
 */
class SkyboxCubemapLook(
    var exposure: Float = 1f,
    var blend: Float = 0f,
    var tintR: Float = 1f,
    var tintG: Float = 1f,
    var tintB: Float = 1f,
)

/**
 * A sky drawn from [cubemap], sampled along each pixel's view ray, behind everything else.
 *
 * [cubemap]'s six layers are the +X, -X, +Y, -Y, +Z and -Z faces in world axes, each laid out the
 * way Vulkan and WebGPU sample a cube face: see `createCubemapAsset`. It draws whenever the frame
 * shows a sky, over the gradient sky if the plan has one.
 *
 * @param cubemap The sky, as a cubemap texture.
 * @param exposure Multiplies the sampled colour; the starting exposure of [look].
 * @param shaders The shader set to draw it with.
 * @param blendTo A second cubemap, which [look]'s blend moves the sky toward. Defaults to [cubemap].
 * @param look The exposure, tint and blend the sky draws with, read every frame.
 */
fun skyboxCubemapContentFeature(
    cubemap: TextureAsset,
    exposure: Float = 1f,
    shaders: ShaderSet = PackShaderSets.SkyboxCubemap,
    blendTo: TextureAsset = cubemap,
    look: SkyboxCubemapLook = SkyboxCubemapLook(exposure),
): ContentFeatureSource {
    require(cubemap.isCubemap && blendTo.isCubemap) { "A cubemap sky needs cubemap textures; build one with createCubemapAsset." }
    return ContentFeatureSource { backend ->
        ContentFeature(
            name = "skybox_cubemap",
            spec = shaders.stagesFor(backend).spec(
                vertexFormat = VertexFormat.None,
                variant = PipelineVariant.Background,
                uniforms = SkyboxCubemapUniformLayout,
            ),
            textures = mapOf(CUBEMAP_BINDING to cubemap, BLEND_CUBEMAP_BINDING to blendTo),
        ) { pipeline, uniforms, _ -> SkyboxCubemapRenderFeature(pipeline, uniforms, look) }
    }
}

private class SkyboxCubemapRenderFeature(
    private val pipeline: PipelineHandle,
    private val uniforms: UniformBlock,
    private val look: SkyboxCubemapLook,
) : RenderFeature<RenderFrameContext> {
    override val pass = RenderPassSlot.Scene

    private val shared = SharedSkyboxRenderFeature()

    override fun recordCommands(context: RenderFrameContext): Unit = with(context) {
        if (!environment.showSky) return
        val inverse = viewProjection.inverse() ?: return
        uniforms.write(frameIndex) {
            put(SkyboxCubemapFields.InverseViewProjection, inverse)
            put(SkyboxCubemapFields.CameraEye, cameraEye)
            put(SkyboxCubemapFields.Exposure, look.exposure, look.blend, 0f, 0f)
            put(SkyboxCubemapFields.Tint, look.tintR, look.tintG, look.tintB, 0f)
        }
        shared.recordCommands(recorder, pipeline, uniforms.binding(frameIndex))
    }

    /** Nothing: the registry owns the pipeline, and the block lives on it. */
    override fun destroy() = Unit
}

/** Where [SkyboxCubemapShader] declares its cube texture. */
private const val CUBEMAP_BINDING = 1

/** Where [SkyboxCubemapShader] declares the cube texture the sky blends toward. */
private const val BLEND_CUBEMAP_BINDING = 3
