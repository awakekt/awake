/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.render.renderer.UiUploadStats
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #1545: a still UI that draws an offscreen layer every frame uploads nothing after its first frame.
 *
 * A layer pass and the overlay each stage their runs into UI mesh slots. Shared slots made each
 * pass overwrite the other's meshes, so both re-uploaded every frame although nothing changed.
 */
class WebGpuUiLayerSlotsTest {
    private val layer = List(RUNS) { i -> UiDrawPrimitive.RoundedQuad(i * 6f, 2f, 5f, 12f, Color(0.2f, 0.6f, 0.9f, 1f), radius = 2f) }
    private val overlay = List(RUNS) { i -> UiDrawPrimitive.RoundedQuad(i * 6f, 20f, 5f, 12f, Color(0.9f, 0.6f, 0.2f, 1f), radius = 3f) }

    @Test
    fun aStillLayerAndOverlayUploadNothingAfterTheFirstFrame() = withPresentingUi { renderer, frame ->
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val uploads = List(FRAMES) {
            // Built fresh every frame, as composition builds them; a layer's runs never share an
            // array identity with the overlay's.
            renderer.drawUiToTexture(target, layer.map { it.copy() })
            renderer.drawUi(overlay.map { it.copy() })
            frame()
            renderer.frameUiUploads
        }
        println("PERF uiLayerSlots per frame=${uploads.map { it?.bytes }}")
        assertEquals(List(FRAMES - 1) { UiUploadStats(0L, 0, 0L) }, uploads.drop(1), "still frames re-uploaded")
        target.destroy()
    }

    private companion object {
        const val SIZE = 64
        const val RUNS = 2
        const val FRAMES = 6
    }
}
