/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.texture.RenderTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import com.awakekt.awake.webgpu.renderer.Renderer as WebGpuRenderer

/**
 * UI staged by `drawUi` is recorded at the next `draw`, so the material and render target it
 * samples can be destroyed in between: a scene switch frees the old scene's canvas Views before the
 * new scene's first frame. That frame must draw nothing of them, rather than throw on the
 * destroyed material or sample the destroyed texture. Staged UI is used up by that `draw`, so a
 * frame that stages none draws none.
 */
class WebGpuStagedUiTargetTest {
    @Test
    fun uiStagedBeforeItsMaterialAndTargetWereDestroyedDrawsNothingOfThem() = staged { material, target ->
        material.destroy()
        target.destroy()
    }

    @Test
    fun uiStagedBeforeItsTargetWasDestroyedDrawsNothingOfIt() = staged { _, target ->
        target.destroy()
    }

    @Test
    fun aFrameThatStagesNoUiDrawsNone() = presenting { renderer, frame ->
        renderer.drawUi(listOf(redQuad()))
        frame()
        assertEquals(1, renderer.statsCounter.latest?.drawCalls, "the staged UI is drawn")

        frame()

        assertEquals(0, renderer.statsCounter.latest?.drawCalls, "the last frame's UI was drawn again")
    }

    /**
     * Presents UI sampling a target once, then stages it again, runs [destroy] and presents: the
     * second frame must draw nothing.
     */
    private fun staged(destroy: (Material, RenderTarget) -> Unit) = presenting { renderer, frame ->
        val target = renderer.createRenderTarget(SIZE, SIZE)
        renderer.drawUiToTexture(target, listOf(redQuad()))
        val material = renderer.createMaterial(renderTarget = target)
        val ui = listOf(UiDrawPrimitive.Texture(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), material, premultiplied = true))
        frame() // so the next frame's count holds no draw into the target

        // Positive control: while both live, the staged UI is one draw of the target.
        renderer.drawUi(ui)
        frame()
        assertEquals(1, renderer.statsCounter.latest?.drawCalls, "the staged UI draws the target")

        renderer.drawUi(ui)
        destroy(material, target)
        frame()

        assertEquals(0, renderer.statsCounter.latest?.drawCalls, "a destroyed material or target must not be drawn")
    }

    /** Runs [block] with a renderer whose surface presents, and a frame step; no WebGPU error may occur. */
    private fun presenting(block: (WebGpuRenderer, frame: () -> Unit) -> Unit) = withPresentingUi(block)

    private fun redQuad() = UiDrawPrimitive.Quad(0f, 0f, SIZE.toFloat(), SIZE.toFloat(), Color(1f, 0f, 0f, 1f))

    private companion object {
        const val SIZE = 8
    }
}
