/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.TextureAsset
import kotlinx.coroutines.runBlocking

/**
 * The shadow scene's caster textured with a card whose right half is clear, [masked] or opaque, and
 * [skinned] to one joint at the identity or a static mesh.
 *
 * Masked, the clear half neither draws nor casts: the ground shows through it and only the left
 * half shadows the ground. Opaque, the whole card draws, its clear half black, and casts.
 */
fun Renderer.renderHalfClearCasterScene(masked: Boolean, skinned: Boolean = false): ByteArray {
    val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
    val ground = createMesh(plane(GROUND_HALF, y = 0f))
    val caster = createMesh(if (skinned) skinnedTexturedPlane(CASTER_HALF, y = CASTER_Y) else texturedPlane(CASTER_HALF, y = CASTER_Y))
    val groundMaterial = createMaterial(LitShadowUniformLayout)
    val casterMaterial = if (skinned) createMaterial(SkinnedUniformLayout, texture = HalfClear) else texturedMaterial(HalfClear)
    return try {
        val card = RenderDrawCommand(
            caster,
            casterMaterial,
            extraUniformFloats = if (skinned) IDENTITY_JOINT else WHITE_FACTORS,
            alphaMode = if (masked) AlphaMode.Masked else AlphaMode.Opaque,
        )
        renderToTexture(target, compileShadowScene(listOf(RenderDrawCommand(ground, groundMaterial), card)))
        runBlocking { readPixels(target) }.data
    } finally {
        ground.destroy()
        caster.destroy()
        groundMaterial.destroy()
        casterMaterial.destroy()
        target.destroy()
    }
}

private const val HALF_CLEAR_WIDTH = 16
private val IDENTITY_JOINT = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
private val ORANGE = intArrayOf(220, 80, 40, 255)

/** Orange on the left half, clear black on the right; wide enough that filtering blurs only the seam. */
private val HalfClear = TextureAsset(
    data = ByteArray(HALF_CLEAR_WIDTH * 4) { index ->
        if (index / 4 < HALF_CLEAR_WIDTH / 2) ORANGE[index % 4].toByte() else 0
    },
    width = HALF_CLEAR_WIDTH,
    height = 1,
)
