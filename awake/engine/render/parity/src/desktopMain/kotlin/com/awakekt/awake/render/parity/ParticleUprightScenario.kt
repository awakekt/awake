/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.particles.ParticleFacing
import com.awakekt.awake.particles.ParticleMotion
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.texture.TextureAsset

/**
 * A real `awake:particles` emitter with [ParticleMotion.orientSpawnRing] and [facing]
 * (by default [ParticleFacing.Upright]), its [RING_RADIUS] spawn ring in the entity's XZ plane, seen
 * head-on from +Z through an orthographic lens as [size]-square RGBA. Its sprites show
 * [TopRedBottomGreenSprite], red at the texture's top and green at its bottom.
 *
 * Unturned, the ring lies on the world's ground and is seen edge on, and upright sprites stand in the
 * world's XY plane facing the camera: a horizontal band of sprites across the middle, red above green.
 * [rolled] turns the entity a quarter about +Z, so its +X is the world's +Y and its +Y the world's -X:
 * the ring stands in the world's YZ plane, seen edge on as a vertical band, and the sprites still face
 * the camera, standing in the entity's XY plane, their tops (red) toward the world's -X.
 *
 * A [ParticleFacing.Flat] emitter on the unturned entity lays its sprites on the ground, edge on to the
 * camera, so it draws nothing: the negative control.
 */
fun Renderer.renderUprightSpawnRingScene(
    size: Int,
    rolled: Boolean = false,
    facing: ParticleFacing = ParticleFacing.Upright,
): ByteArray = renderSpawnRingScene(
    size,
    SpawnRing(
        turn = if (rolled) quarterAboutZ() else Mat4(),
        facing = facing,
        spriteScale = UPRIGHT_SPRITE_SCALE,
        texture = TopRedBottomGreenSprite,
    ),
)

/** The upright scene's sprite size in world units: 16 px at 128 px across. */
const val UPRIGHT_SPRITE_SCALE: Float = 0.5f

/** A 2x2 texture whose top row is red and bottom row green, so a frame shows which way a sprite's top points. */
internal val TopRedBottomGreenSprite = TextureAsset(
    data = byteArrayOf(
        -1, 0, 0, -1, -1, 0, 0, -1,
        0, -1, 0, -1, 0, -1, 0, -1,
    ),
    width = 2,
    height = 2,
)

/** A quarter turn about +Z: the entity's +X is the world's +Y, its +Y the world's -X. */
private fun quarterAboutZ() = Mat4().apply {
    m00 = 0f
    m10 = 1f
    m01 = -1f
    m11 = 0f
}
