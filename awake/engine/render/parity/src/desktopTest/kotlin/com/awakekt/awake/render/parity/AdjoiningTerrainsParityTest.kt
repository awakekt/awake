/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Three terrains of one shader set draw at once on both backends, each where it was placed and
 * with its own texture, and each casts into the shadow pass; detaching the middle one leaves the
 * other two drawn.
 *
 * Measured on lavapipe, both backends alike: each tile drew 800 pixels of its own tint and cast
 * 49 to 67. Negative controls, measured on Vulkan: with every feature drawing through the
 * pipeline's one group (one set of uniforms and textures) the three drew over each other where
 * the last one lies, and tile 0 drew 0 red pixels and cast 0; with the origin left out of the
 * uniforms all three drew over the centre, tile 0 again drew 0 red, and the middle column still
 * drew 800 pixels after its own terrain left. Before awakekt/awake#1461 the second attach threw.
 */
class AdjoiningTerrainsParityTest {

    @Test
    fun threeAdjoiningTerrainsDrawWithTheirOwnTintOnBothBackends() = BACKEND_ORDER.forEach { backend ->
        openHeadlessScene(backend).use { session ->
            val frame = session.renderer.renderAdjoiningTerrainsScene()

            Tint.entries.forEachIndexed { tile, tint ->
                val covered = frame.columnPixels(tile).count { frame.tintAt(it) == tint }
                assertTrue(covered > MIN_TILE_PIXELS, "$backend: tile $tile drew only $covered ${tint.name} pixels.")
            }
        }
    }

    @Test
    fun detachingTheMiddleTerrainLeavesItsNeighboursDrawn() = BACKEND_ORDER.forEach { backend ->
        openHeadlessScene(backend).use { session ->
            val frame = session.renderer.renderAdjoiningTerrainsScene(detachMiddle = true)

            assertEquals(0, frame.columnPixels(1).count { frame.tintAt(it) != null }, "$backend: the detached middle terrain still drew.")
            listOf(0 to Tint.Red, 2 to Tint.Blue).forEach { (tile, tint) ->
                val covered = frame.columnPixels(tile).count { frame.tintAt(it) == tint }
                assertTrue(covered > MIN_TILE_PIXELS, "$backend: tile $tile drew only $covered ${tint.name} pixels after the middle left.")
            }
        }
    }

    /** What turns dark only with the shadow pass on is what each terrain cast. */
    @Test
    fun everyTerrainCastsIntoTheShadowPass() = BACKEND_ORDER.forEach { backend ->
        openHeadlessScene(backend).use { session ->
            val shadowed = session.renderer.renderAdjoiningTerrainsScene()
            val unshadowed = session.renderer.renderAdjoiningTerrainsScene(cascades = false)

            val darker = shadowed.darkerThan(unshadowed)
            repeat(Tint.entries.size) { tile ->
                val cast = shadowed.columnPixels(tile).count { it in darker }
                assertTrue(cast > MIN_CAST_PIXELS, "$backend: tile $tile cast only $cast pixels of shadow.")
            }
        }
    }

    private enum class Tint { Red, Green, Blue }

    /** Pixel offsets of tile [tile]'s column, a margin inside its edges, across the whole frame height. */
    private fun ByteArray.columnPixels(tile: Int): List<Int> {
        val centre = ADJOINING_TILE_X[tile]
        val from = adjoiningColumn(centre - TILE_SPAN / 2f) + MARGIN
        val to = adjoiningColumn(centre + TILE_SPAN / 2f) - MARGIN
        return (0 until SCENE_SIZE).flatMap { y -> (from until to).map { x -> (y * SCENE_SIZE + x) * BYTES_PER_PIXEL } }
    }

    /** The one channel lit at [offset], or null for the clear colour or a mixed one. */
    private fun ByteArray.tintAt(offset: Int): Tint? {
        val channels = (0..2).map { this[offset + it].toInt() and 0xFF }
        val lit = channels.indices.filter { channels[it] > LIT_MIN }
        val others = channels.indices.filter { channels[it] > OTHER_MAX }
        return if (lit.size == 1 && others == lit) Tint.entries[lit.single()] else null
    }

    /** Offsets where this frame is clearly darker than [control], over pixels both drew. */
    private fun ByteArray.darkerThan(control: ByteArray): Set<Int> =
        (0 until SCENE_SIZE * SCENE_SIZE).map { it * BYTES_PER_PIXEL }.filter { offset ->
            val here = luminance(offset)
            val there = control.luminance(offset)
            there > LIT_MIN && here < there * SHADED_PERCENT / 100
        }.toSet()

    private fun ByteArray.luminance(offset: Int) = (0..2).maxOf { this[offset + it].toInt() and 0xFF }

    private companion object {
        /** Vulkan first, for the loader reason [UiBackendParityTest] documents. */
        val BACKEND_ORDER = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu)
        const val BYTES_PER_PIXEL = 4
        const val MARGIN = 3
        const val LIT_MIN = 24
        const val OTHER_MAX = 12
        const val SHADED_PERCENT = 85
        const val MIN_TILE_PIXELS = 400
        const val MIN_CAST_PIXELS = 20
    }
}
