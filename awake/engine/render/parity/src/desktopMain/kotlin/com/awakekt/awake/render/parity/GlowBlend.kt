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
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking

/** How [renderGlowScene] draws its red quad. */
enum class GlowBlend { None, Alpha, Additive }

/**
 * A red quad lying just above the lit ground, drawn transparent by [blend] ([GlowBlend.None] leaves
 * it out). Blended by alpha it covers the ground; added, the ground's green and blue show through.
 */
fun Renderer.renderGlowScene(blend: GlowBlend): ByteArray {
    val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
    val ground = createMesh(plane(GROUND_HALF, y = 0f))
    val glow = createMesh(texturedPlane(GLOW_HALF, y = GLOW_Y))
    val groundMaterial = createMaterial(LitShadowUniformLayout)
    val glowMaterial = texturedMaterial(SolidRed)
    return try {
        val draws = buildList {
            add(RenderDrawCommand(ground, groundMaterial))
            if (blend != GlowBlend.None) {
                add(
                    RenderDrawCommand(
                        glow,
                        glowMaterial,
                        extraUniformFloats = WHITE_FACTORS,
                        transparent = true,
                        additive = blend == GlowBlend.Additive,
                    ),
                )
            }
        }
        renderToTexture(target, compileShadowScene(draws))
        runBlocking { readPixels(target) }.data
    } finally {
        ground.destroy()
        glow.destroy()
        groundMaterial.destroy()
        glowMaterial.destroy()
        target.destroy()
    }
}

private const val GLOW_HALF = 2f
private const val GLOW_Y = 0.05f
private val SolidRed = TextureAsset(data = ByteArray(2 * 2 * 4) { if (it % 4 == 0 || it % 4 == 3) -1 else 0 }, width = 2, height = 2)
