/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexSemantic
import com.awakekt.awake.core.geometry.generate.generate
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.shadowCascadeUniforms
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.RenderDebugView
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import kotlinx.coroutines.runBlocking

/** The camera [renderDebugViewScene] looks through: down at the cube and out past the shadow distance. */
fun debugViewLens(): Lens = Lens(
    eye = Vec3f(0f, DEBUG_EYE_Y, DEBUG_EYE_Z),
    center = Vec3f(0f, 0f, DEBUG_CENTER_Z),
    fovYRadians = 1f,
    near = 0.1f,
    far = DEBUG_FAR,
)

/**
 * A red cube on a white ground that runs past the shadow distance, under the cascaded fit, drawn
 * as [view].
 *
 * Framed so every debug view has something to show: the ground carries several cascades and,
 * above them, the band beyond the shadow distance where shadowing ends; the cube's shadow falls
 * on the ground in front of the camera; and the cube shows two faces with different normals.
 */
fun Renderer.renderDebugViewScene(view: RenderDebugView, shadowsEnabled: Boolean = true): ByteArray {
    val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
    val ground = createMesh(generate { plane(size = DEBUG_GROUND_SIZE, colored = false) })
    val cube = createMesh(redCube())
    val material = createMaterial(LitShadowUniformLayout)
    return try {
        val lens = debugViewLens()
        val base = SceneLight(direction = Vec3f(SUN_X, SUN_Y, SUN_Z), color = Vec3f(1f, 1f, 1f))
        renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = listOf(
                    RenderDrawCommand(ground, material),
                    RenderDrawCommand(cube, material, model = Mat4().apply { identity() }.translate(0f, DEBUG_CUBE_SIZE / 2f, DEBUG_CUBE_Z)),
                ),
                light = base.copy(cascades = shadowCascadeUniforms(base, lens, 1f, clipSpace)),
                environment = EnvironmentUniforms(debugView = view, shadowsEnabled = shadowsEnabled),
                clipSpace = clipSpace,
                aspect = 1f,
                drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
            ),
        )
        runBlocking { readPixels(target) }.data
    } finally {
        ground.destroy()
        cube.destroy()
        material.destroy()
        target.destroy()
    }
}

/** A white cube recoloured pure red, so albedo has a colour to find. */
private fun redCube(): MeshGeometry {
    val white = generate { cube(size = DEBUG_CUBE_SIZE, colored = false) }
    val color = white.format.floatOffsetOf(VertexSemantic.Color)
    val vertices = white.vertices.copyOf()
    for (start in vertices.indices step white.format.strideFloats) {
        vertices[start + color + 1] = 0f
        vertices[start + color + 2] = 0f
    }
    return white.copy(vertices = vertices)
}

private const val DEBUG_EYE_Y = 20f
private const val DEBUG_EYE_Z = 30f
private const val DEBUG_CENTER_Z = -20f

/** Past the 100-unit shadow distance, so the frame shows where shadowing stops. */
const val DEBUG_FAR: Float = 250f
private const val DEBUG_GROUND_SIZE = 1000f
private const val DEBUG_CUBE_SIZE = 4f
private const val DEBUG_CUBE_Z = 5f
