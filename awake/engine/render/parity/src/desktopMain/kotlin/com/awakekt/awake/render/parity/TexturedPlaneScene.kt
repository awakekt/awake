/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.directionalShadowBox
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.PointLight
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking
import com.awakekt.awake.render.material.Material as RenderMaterial

/**
 * [renderTexturedPbrScene]'s plane and light with [material] on it, which the caller made and still
 * owns: a texture uploaded some other way, such as block-compressed, drawn the same as one from a
 * [TextureAsset]. [material] needs [MaterialUniformLayouts.PbrTextured]'s uniforms.
 */
fun Renderer.renderTexturedMaterialScene(material: RenderMaterial, size: Int = SCENE_SIZE): ByteArray = renderTexturedPlane(
    material,
    EnvironmentUniforms.Default.copy(shadowsEnabled = false),
    TextureAnimation.None,
    timeSeconds = 0f,
    sunDirection = Vec3f(0f, 1f, 0f),
    ambient = null,
    points = emptyList(),
    metallic = 0.1f,
    roughness = 0.45f,
    size = size,
)

@Suppress("LongParameterList") // renderTexturedPbrScene's parameters, passed through.
internal fun Renderer.renderTexturedPlane(
    material: RenderMaterial,
    environment: EnvironmentUniforms,
    textureAnimation: TextureAnimation,
    timeSeconds: Float,
    sunDirection: Vec3f,
    ambient: Float?,
    points: List<PointLight>,
    metallic: Float,
    roughness: Float,
    size: Int,
): ByteArray {
    val target = createRenderTarget(size, size)
    val mesh = createMesh(texturedPlane())
    return try {
        val lens = Lens(
            eye = Vec3f(0f, EYE_Y, EYE_Z),
            center = Vec3f(0f, 0f, 0f),
            fovYRadians = 1f,
            near = 0.1f,
            far = 50f,
        )
        val draw = RenderDrawCommand(
            mesh = mesh,
            material = material,
            extraUniformFloats = pbrMaterialFloats(
                metallic = metallic,
                roughness = roughness,
                baseColorFactor = Color.White,
                emissiveFactor = Color.Transparent,
                textureAnimation = textureAnimation,
            ),
            timeSeconds = timeSeconds,
        )
        renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = listOf(draw),
                light = SceneLight(direction = sunDirection, color = Vec3f(1f, 1f, 1f), points = points, ambient = ambient),
                environment = environment,
                clipSpace = clipSpace,
                aspect = 1f,
                drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
            ),
        )
        runBlocking { readPixels(target) }.data
    } finally {
        mesh.destroy()
        target.destroy()
    }
}
