/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import kotlinx.coroutines.runBlocking

/**
 * [renderShadowScene]'s red caster over the ground, drawn at [fadeOpacity]: below 1 its dither fade
 * discards that share of its pixels, so the ground shows through them, while it still casts its
 * whole shadow. At 1 the scene is [renderShadowScene]'s, pixel for pixel.
 */
fun Renderer.renderFadedCasterScene(fadeOpacity: Float): ByteArray {
    val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
    val ground = createMesh(plane(GROUND_HALF, y = 0f))
    val caster = createMesh(plane(CASTER_HALF, y = CASTER_Y, r = 1f, g = 0f, b = 0f))
    val material = createMaterial(LitShadowUniformLayout)
    return try {
        val card = RenderDrawCommand(caster, material).apply { this.fadeOpacity = fadeOpacity }
        renderToTexture(target, compileShadowScene(listOf(RenderDrawCommand(ground, material), card)))
        runBlocking { readPixels(target) }.data
    } finally {
        ground.destroy()
        caster.destroy()
        material.destroy()
        target.destroy()
    }
}
