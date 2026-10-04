/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.render.passes.uniforms.setParticleInstance
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.ParticleUniformLayout
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking

/** Where [renderSpriteScene] looks from, at the origin. */
enum class SpriteView(val eye: Vec3f, val up: Vec3f) {
    /** Straight down onto the ground plane. */
    Above(Vec3f(0f, SPRITE_EYE_DISTANCE, 0f), Vec3f(0f, 0f, -1f)),

    /** Level with the ground plane, so a quad lying in it has no area on screen. */
    EdgeOn(Vec3f(0f, 0f, SPRITE_EYE_DISTANCE), Vec3f(0f, 1f, 0f)),
}

/**
 * Two red particle sprites from one material in one frame, seen from [view]: a flat one lying in
 * the ground plane at x = [FLAT_SPRITE_X] (the left half of the frame from either view), and a
 * camera-facing one at x = [FACING_SPRITE_X] (the right half). Each draw carries its own quad axes,
 * as the scene particle compiler writes them: the ground's +X and -Z, or the camera's right and up.
 */
fun Renderer.renderSpriteScene(view: SpriteView): ByteArray {
    val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
    val quad = createMesh(SpriteQuad)
    val material = createMaterial(ParticleUniformLayout, texture = RedSprite)
    return try {
        val lens = Lens(eye = view.eye, center = Vec3f(0f, 0f, 0f), up = view.up, fovYRadians = 1f, near = 0.1f, far = 50f)
        val forward = (lens.center - lens.eye).normalized()
        val right = forward.cross(lens.up).normalized()
        val cameraUp = right.cross(forward)
        fun sprite(x: Float, quadRight: Vec3f, quadUp: Vec3f) = RenderDrawCommand(
            mesh = quad,
            material = material,
            instanceModels = listOf(Mat4().setParticleInstance(x, 0f, 0f, SPRITE_SIZE)),
            instanceColors = listOf(Vec4(1f, 1f, 1f, 1f)),
            instanceFrames = listOf(0f),
            // cameraRight, cameraUp, frameInfo: ParticleExtraUniformLayout.
            extraUniformFloats = floatArrayOf(
                quadRight.x, quadRight.y, quadRight.z, 0f,
                quadUp.x, quadUp.y, quadUp.z, 0f,
                1f, 0f, 0f, 0f,
            ),
        )
        renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = listOf(
                    sprite(FLAT_SPRITE_X, Vec3f(1f, 0f, 0f), Vec3f(0f, 0f, -1f)),
                    sprite(FACING_SPRITE_X, right, cameraUp),
                ),
                light = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f)),
                environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false),
                clipSpace = clipSpace,
                aspect = 1f,
                drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
            ),
        )
        runBlocking { readPixels(target) }.data
    } finally {
        quad.destroy()
        material.destroy()
        target.destroy()
    }
}

/**
 * One camera-facing particle sprite at the origin, seen from the side ([SpriteView.EdgeOn]), so world
 * up lies on screen. Its texture is red on the left half and blue on the right, so the picture shows
 * both the quad's proportions and which way round it is.
 */
fun Renderer.renderFacingSpriteFromTheSide(size: Int = SCENE_SIZE): ByteArray {
    val target = createRenderTarget(size, size)
    val quad = createMesh(SpriteQuad)
    val material = createMaterial(ParticleUniformLayout, texture = RedLeftBlueRight)
    return try {
        val view = SpriteView.EdgeOn
        val lens = Lens(eye = view.eye, center = Vec3f(0f, 0f, 0f), up = view.up, fovYRadians = 1f, near = 0.1f, far = 50f)
        val forward = (lens.center - lens.eye).normalized()
        val right = forward.cross(lens.up).normalized()
        val up = right.cross(forward)
        val sprite = RenderDrawCommand(
            mesh = quad,
            material = material,
            instanceModels = listOf(Mat4().setParticleInstance(0f, 0f, 0f, SPRITE_SIZE)),
            instanceColors = listOf(Vec4(1f, 1f, 1f, 1f)),
            instanceFrames = listOf(0f),
            extraUniformFloats = floatArrayOf(right.x, right.y, right.z, 0f, up.x, up.y, up.z, 0f, 1f, 0f, 0f, 0f),
        )
        renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = listOf(sprite),
                light = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f)),
                environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false),
                clipSpace = clipSpace,
                aspect = 1f,
                drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
            ),
        )
        runBlocking { readPixels(target) }.data
    } finally {
        quad.destroy()
        material.destroy()
        target.destroy()
    }
}

/** Two texels: red on the left, blue on the right. */
private val RedLeftBlueRight = TextureAsset(data = byteArrayOf(-1, 0, 0, -1, 0, 0, -1, -1), width = 2, height = 1)

private const val FLAT_SPRITE_X = -1f
private const val FACING_SPRITE_X = 1f
private const val SPRITE_SIZE = 1f
private const val SPRITE_EYE_DISTANCE = 4f

private val RedSprite = TextureAsset(data = ByteArray(2 * 2 * 4) { if (it % 4 == 0 || it % 4 == 3) -1 else 0 }, width = 2, height = 2)

/** The unit quad the scene's particle content draws: position and UV in the XY plane. */
private val SpriteQuad = MeshGeometry(
    floatArrayOf(
        -0.5f, -0.5f, 0f, 0f, 1f,
        0.5f, -0.5f, 0f, 1f, 1f,
        0.5f, 0.5f, 0f, 1f, 0f,
        -0.5f, 0.5f, 0f, 0f, 0f,
    ),
    intArrayOf(0, 1, 2, 2, 3, 0),
    format = VertexFormat.PositionUv,
)
