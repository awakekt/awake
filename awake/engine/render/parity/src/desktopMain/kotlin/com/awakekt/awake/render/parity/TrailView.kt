/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.trail.TrailBatch
import com.awakekt.awake.trail.TrailBlend
import com.awakekt.awake.trail.TrailRenderer
import kotlinx.coroutines.runBlocking
import kotlin.math.PI

/**
 * Trails drawn as a played scene draws them, frame after frame, over a black background: each
 * [frame] uploads only what changed since the last, through one [TrailBatch] kept for the view's
 * life, and returns the frame as [size]-square RGBA. The camera looks down -Z from 3 m through a
 * 60 degree lens, so x from -1 to 1 spans about the middle 58 % of the frame.
 */
class TrailView(private val renderer: Renderer, private val size: Int) : AutoCloseable {
    private val batch = TrailBatch(renderer)
    private val target = renderer.createRenderTarget(size, size)
    private val lens = Lens(eye = Vec3f(0f, 0f, EYE_Z), center = Vec3f.ZERO, fovYRadians = (PI / 3).toFloat(), near = 0.1f, far = 50f)
    private val placement = Mat4()
    private val draws = ArrayList<RenderDrawCommand>()

    /** Draws [trails], each at the origin, and reads the frame back. */
    fun frame(trails: List<TrailRenderer>): ByteArray {
        draws.clear()
        for (trail in trails) batch.collect(trail, placement, emptyList(), draws)
        batch.releaseUnseen()
        renderer.renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = draws,
                environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false, clearColor = Color.Black),
                clipSpace = renderer.clipSpace,
                aspect = 1f,
                drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
            ),
        )
        return runBlocking { renderer.readPixels(target) }.data.copyOf(size * size * RGBA)
    }

    override fun close() {
        batch.close()
        target.destroy()
    }

    private companion object {
        const val EYE_Z = 3f
        const val RGBA = 4
    }
}

/**
 * A horizontal ribbon of [pairs] pairs from x = -1 to 1, [height] tall around [y], every pair
 * [color] at full alpha, blended by [blend]. Nothing is visible until the caller says how much is.
 */
fun straightTrail(pairs: Int, color: Color, blend: TrailBlend = TrailBlend.Additive, y: Float = 0f, height: Float = 0.4f): TrailRenderer =
    TrailRenderer(pairs, blend).apply {
        val a = Vec3f(0f, 0f, 0f)
        val b = Vec3f(0f, 0f, 0f)
        for (pair in 0 until pairs) {
            val x = -1f + 2f * pair / (pairs - 1)
            a.set(x, y - height / 2f, 0f)
            b.set(x, y + height / 2f, 0f)
            set(pair, a, b, color, color)
        }
    }
