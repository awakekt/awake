/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Quat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.worldtext.WorldText
import com.awakekt.awake.worldtext.WorldTextBatch
import com.awakekt.awake.worldtext.WorldTextStyle
import kotlinx.coroutines.runBlocking
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A sign placed in the scene, [text] in white, half a metre an em, seen through a 60 degree lens from
 * [distance] metres and turned [turnDegrees] about its vertical axis, as [size]-square RGBA. Its em is
 * about 110 pixels at 1 m on a 256 pixel frame. Empty [text] draws the ground alone.
 */
fun Renderer.renderWorldTextScene(distance: Float, turnDegrees: Float = 0f, size: Int, text: String = "Hg"): ByteArray {
    val batch = WorldTextBatch(this)
    val target = createRenderTarget(size, size)
    try {
        val radians = turnDegrees * PI.toFloat() / DEGREES_PER_HALF_TURN
        val placement = Mat4().setTrs(Vec3f(0f, 0f, 0f), Quat(0f, sin(radians / 2f), 0f, cos(radians / 2f)), Vec3f(1f, 1f, 1f))
        val lens = Lens(eye = Vec3f(0f, 0f, distance), center = Vec3f.ZERO, fovYRadians = (PI / 3).toFloat(), near = 0.05f, far = 200f)
        val draws = ArrayList<RenderDrawCommand>()
        if (text.isNotEmpty()) batch.collect(WorldText(text, WorldTextStyle(size = SIGN_EM)), placement, lens, draws)
        renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = draws,
                clipSpace = clipSpace,
                aspect = 1f,
                drawPreparer = (this as GpuDrawPreparationSource).gpuDrawPreparer,
            ),
        )
        return runBlocking { readPixels(target) }.data.copyOf(size * size * RGBA)
    } finally {
        target.destroy()
        batch.close()
    }
}

private const val SIGN_EM = 0.5f
private const val RGBA = 4
private const val DEGREES_PER_HALF_TURN = 180f
