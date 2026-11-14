/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * #1545: a still UI that draws an offscreen layer every frame uploads nothing once each frame in
 * flight holds its meshes, and a destroyed layer's meshes are freed only after every frame that
 * could still read them has finished.
 */
class RendererUiLayerSlotsTest {
    private val layer = List(2) { i -> UiDrawPrimitive.RoundedQuad(i * 6f, 2f, 5f, 12f, Color(0.2f, 0.6f, 0.9f, 1f), radius = 2f) }
    private val overlay = List(2) { i -> UiDrawPrimitive.RoundedQuad(i * 6f, 20f, 5f, 12f, Color(0.9f, 0.6f, 0.2f, 1f), radius = 3f) }

    @Test
    fun aStillLayerAndOverlayUploadNothingOnceEveryFrameInFlightHoldsThem() = withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val bytes = List(FRAMES) {
            renderer.drawUiToTexture(target, layer.map { it.copy() })
            renderer.drawUi(overlay.map { it.copy() })
            renderer.presentWithoutScene()
            renderer.frameUiUploads?.bytes
        }
        println("PERF vulkan uiLayerSlots per frame=$bytes")
        assertEquals(List(FRAMES - FRAMES_IN_FLIGHT) { 0L }, bytes.drop(FRAMES_IN_FLIGHT), "still frames re-uploaded")
        target.destroy()
    }

    @Test
    fun aDestroyedLayersMeshesAreFreedOnlyAfterEveryFrameInFlightFinished() = withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
        val target = renderer.createRenderTarget(SIZE, SIZE)
        repeat(FRAMES_IN_FLIGHT) {
            renderer.drawUiToTexture(target, layer)
            renderer.drawUi(overlay)
            renderer.presentWithoutScene()
        }
        target.destroy()
        assertEquals(1, renderer.bufferPools.retiredUiSlotCount, "kept while a frame in flight may read them")
        // Between frames a retire cannot tell the next frame from the one recording, so it counts
        // the next one as a reader too: freed one frame later than strictly needed, never earlier.
        repeat(FRAMES_IN_FLIGHT + 1) {
            renderer.drawUi(overlay)
            renderer.presentWithoutScene()
        }
        assertEquals(0, renderer.bufferPools.retiredUiSlotCount, "freed once the frames that could read them finished")
    }

    /**
     * The canvas-view capture's order: a target drawn and destroyed while a frame records, then
     * more UI recorded in that frame, which waits on the frame slot's fence again before submitting.
     * That wait used to count as the frame finishing, so the target's meshes were destroyed one
     * frame later while the frame that drew them still executed (a lavapipe abort).
     */
    @Test
    fun aLayerDestroyedMidFrameOutlivesTheFrameThatDrewIt() = withPresentableUiRenderer(SIZE, FRAMES_IN_FLIGHT) { renderer ->
        val target = renderer.createRenderTarget(SIZE, SIZE)
        renderer.drawUiToTexture(target, layer)
        target.destroy()
        renderer.drawUi(overlay)
        renderer.presentWithoutScene()
        renderer.drawUi(overlay)
        renderer.presentWithoutScene()
        assertEquals(1, renderer.bufferPools.retiredUiSlotCount, "the frame that drew it may still execute")
        renderer.drawUi(overlay)
        renderer.presentWithoutScene()
        assertEquals(0, renderer.bufferPools.retiredUiSlotCount, "freed once that frame's fence was waited on")
    }

    private companion object {
        const val SIZE = 32
        const val FRAMES_IN_FLIGHT = 2
        const val FRAMES = 8
    }
}
