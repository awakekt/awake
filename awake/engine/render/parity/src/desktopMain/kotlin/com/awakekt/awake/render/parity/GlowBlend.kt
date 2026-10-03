/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.LitShadowUniformLayout
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.texture.PbrTextureSet
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

/**
 * An effect sprite added over the lit ground the way a glow is usually authored: a black base
 * colour and [sprite] as the emissive texture. Null [sprite] leaves it out. The sun sits where the
 * sprite mirrors it into the camera, so a lit sprite would show its specular highlight; [fogDensity]
 * fogs the whole scene.
 */
fun Renderer.renderEffectSpriteScene(sprite: TextureAsset?, fogDensity: Float = 0f): ByteArray {
    val target = createRenderTarget(SCENE_SIZE, SCENE_SIZE)
    val ground = createMesh(plane(GROUND_HALF, y = 0f))
    val quad = createMesh(texturedPlane(GLOW_HALF, y = GLOW_Y))
    val groundMaterial = createMaterial(LitShadowUniformLayout)
    val spriteMaterial = sprite?.let {
        createMaterial(texture = it, uniformFloatCount = MaterialUniformLayouts.PbrTextured.total, pbrTextures = PbrTextureSet(emissive = it))
    }
    return try {
        val draws = buildList {
            add(RenderDrawCommand(ground, groundMaterial))
            if (spriteMaterial != null) {
                // PbrMaterial's defaults, with the effect's black base and white emissive.
                val factors = pbrMaterialFloats(0f, 0.5f, Color(r = 0f, g = 0f, b = 0f, a = 0.8f), Color.White)
                add(RenderDrawCommand(quad, spriteMaterial, extraUniformFloats = factors, transparent = true, additive = true))
            }
        }
        val lens = Lens(eye = Vec3f(0f, EYE_Y, EYE_Z), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f)
        renderToTexture(
            target,
            ScenePassCompiler.compile(
                lens = lens,
                drawCalls = draws,
                // The eye mirrored in the sprite's plane: the sun's reflection off the sprite's centre.
                light = SceneLight(direction = Vec3f(0f, EYE_Y, -EYE_Z), color = Vec3f(1f, 1f, 1f)),
                environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false, fogDensity = fogDensity),
                clipSpace = clipSpace,
                aspect = 1f,
                drawPreparer = (this as? GpuDrawPreparationSource)?.gpuDrawPreparer,
            ),
        )
        runBlocking { readPixels(target) }.data
    } finally {
        ground.destroy()
        quad.destroy()
        groundMaterial.destroy()
        spriteMaterial?.destroy()
        target.destroy()
    }
}

/** Black, fully opaque: the background of a glow sprite. */
internal val SolidBlack = TextureAsset(data = ByteArray(2 * 2 * 4) { if (it % 4 == 3) -1 else 0 }, width = 2, height = 2)

private const val GLOW_HALF = 2f
private const val GLOW_Y = 0.05f
private val SolidRed = TextureAsset(data = ByteArray(2 * 2 * 4) { if (it % 4 == 0 || it % 4 == 3) -1 else 0 }, width = 2, height = 2)
