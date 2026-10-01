/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking

/**
 * A textured plane skinned to one joint, drawn with [palette] as that joint's matrix: the identity
 * shows the texture where the plane lies, a zero matrix collapses it to nothing.
 */
fun Renderer.renderTexturedSkinnedScene(palette: FloatArray, texture: TextureAsset = SolidOrange): ByteArray {
    val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
    val mesh = createMesh(skinnedTexturedPlane())
    val material = createMaterial(SkinnedUniformLayout, texture = texture)
    return try {
        val lens = Lens(eye = Vec3f(0f, EYE_Y, EYE_Z), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f)
        renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = listOf(RenderDrawCommand(mesh = mesh, material = material, extraUniformFloats = palette)),
                light = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f)),
                environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false),
                clipSpace = clipSpace,
                aspect = 1f,
                drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
            ),
        )
        runBlocking { readPixels(target) }.data
    } finally {
        mesh.destroy()
        material.destroy()
        target.destroy()
    }
}

/**
 * [renderShadowScene] with a textured skinned caster posed by [palette]: the identity casts the
 * same shadow as the static caster, a zero matrix collapses it and casts none.
 */
fun Renderer.renderSkinnedShadowScene(palette: FloatArray): ByteArray {
    val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
    val ground = createMesh(plane(GROUND_HALF, y = 0f))
    val caster = createMesh(skinnedTexturedPlane(CASTER_HALF, y = CASTER_Y))
    val groundMaterial = createMaterial(LitShadowUniformLayout)
    val casterMaterial = createMaterial(SkinnedUniformLayout, texture = SolidOrange)
    return try {
        renderToTexture(
            target,
            compileShadowScene(
                listOf(
                    RenderDrawCommand(ground, groundMaterial),
                    RenderDrawCommand(caster, casterMaterial, extraUniformFloats = palette),
                ),
            ),
        )
        runBlocking { readPixels(target) }.data
    } finally {
        ground.destroy()
        caster.destroy()
        groundMaterial.destroy()
        casterMaterial.destroy()
        target.destroy()
    }
}

/**
 * [texturedPlane] with every vertex bound wholly to joint 0. A joint index is read back as a raw
 * `uint32`, and joint 0's bit pattern is the float 0.
 */
internal fun skinnedTexturedPlane(half: Float = GROUND_HALF, y: Float = 0f) = MeshGeometry(
    floatArrayOf(
        -half, y, -half, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
        half, y, -half, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
        half, y, half, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
        -half, y, half, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
    ),
    intArrayOf(0, 1, 2, 2, 3, 0),
    VertexFormat.PositionNormalColorUvSkin,
)
