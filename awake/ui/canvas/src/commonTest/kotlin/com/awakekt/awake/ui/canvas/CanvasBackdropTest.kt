/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.testing.rasterizeToPixelMap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.graphics2d.backdropFallbackColor
import com.awakekt.awake.render.capture.RgbaSample
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `CanvasStyle.backdrop`: the glass a canvas element draws, as the primitives it emits. */
class CanvasBackdropTest {
    private fun panel(style: CanvasStyle, x: Float = 10f) = CanvasElement().apply {
        kind = CanvasElementKind.Panel
        offsetX = x; offsetY = 20f; width = 100f; height = 60f
        background = "#20304080"
        this.style = style
    }

    private fun frame(vararg elements: CanvasElement, blurOn: Boolean = true): List<UiDrawPrimitive> {
        val items = elements.mapIndexed { index, element -> CanvasItem(index, element) }
        return ComposeHost().frame(FrameInput(WIDTH, HEIGHT)) {
            CompositionLocalProvider(LocalCanvasBackdropBlur provides blurOn) { CanvasOverlay(items) }
        }.primitives
    }

    private fun List<UiDrawPrimitive>.backdrops() = filterIsInstance<UiDrawPrimitive.BackdropBlur>()

    @Test
    fun aBackdropBlursTheElementsBoxInItsCornersUnderItsFill() {
        val glass = CanvasStyle(cornerRadius = 12f, backdrop = CanvasBackdrop(blur = 10f, tint = "#FFFFFF33", saturation = 1.4f))
        val primitives = frame(panel(glass))
        val blur = primitives.backdrops().single()

        assertEquals(listOf(10f, 20f, 100f, 60f), listOf(blur.x, blur.y, blur.w, blur.h))
        assertEquals(10f, blur.blurRadius)
        assertEquals(12f, blur.cornerRadius)
        assertEquals(1.4f, blur.saturation)
        assertEquals(Color.fromHex("#FFFFFF33"), blur.tint)
        val fill = primitives.indexOfFirst { it is UiDrawPrimitive.RoundedQuad || it is UiDrawPrimitive.Quad }
        assertTrue(primitives.indexOf(blur) < fill, "the glass is drawn before the element's own fill")
    }

    @Test
    fun noBackdropEmitsNoBlur() {
        assertEquals(0, frame(panel(CanvasStyle(cornerRadius = 12f))).backdrops().size)
    }

    @Test
    fun theHostTurningBlurOffDrawsTheFallbackWithNoBlur() {
        val tint = "#FFFFFF33"
        val blur = frame(panel(CanvasStyle(backdrop = CanvasBackdrop(tint = tint))), blurOn = false).backdrops().single()

        assertEquals(0f, blur.blurRadius, "a zero radius is a fill, which stages no blur pass")
        assertEquals(backdropFallbackColor(Color.fromHex(tint)), blur.fallback)

        val explicit = frame(panel(CanvasStyle(backdrop = CanvasBackdrop(fallback = "#102030"))), blurOn = false).backdrops().single()
        assertEquals(Color.fromHex("#102030"), explicit.fallback)
    }

    /** With no GPU, a screenshot test still sees the glass: the software rasterizer fills its fallback. */
    @Test
    fun theSoftwareRasterizerDrawsTheFallback() {
        val element = panel(CanvasStyle(backdrop = CanvasBackdrop(fallback = "#00FF00"))).apply { background = "" }
        val pixels = frame(element).rasterizeToPixelMap(WIDTH, HEIGHT, background = Color.Black)

        assertEquals(RgbaSample(0, 255, 0, 255), pixels.sample(60, 50), "inside the panel")
        assertEquals(RgbaSample(0, 0, 0, 255), pixels.sample(200, 150), "control, outside it")
    }

    @Test
    fun theElementsAlphaFadesItsGlass() {
        val blur = frame(panel(CanvasStyle(alpha = 0.5f, backdrop = CanvasBackdrop()))).backdrops().single()
        assertEquals(0.5f, blur.alpha)
        val fill = frame(panel(CanvasStyle(alpha = 0.5f, backdrop = CanvasBackdrop())))
            .filterIsInstance<UiDrawPrimitive.Quad>().single()
        assertEquals(0.5f * 0.5f, fill.color.a, 0.01f, "the fill fades once, not twice")
    }

    @Test
    fun aBackdropIsReadFromTheCanvasDocument() {
        val style = Json.decodeFromString<CanvasStyle>(
            """{"cornerRadius": 8, "backdrop": {"blur": 12, "tint": "#0A0A0A40", "saturation": 1.2, "fallback": "#1A1A1AE0"}}""",
        )
        assertEquals(CanvasBackdrop(blur = 12f, tint = "#0A0A0A40", saturation = 1.2f, fallback = "#1A1A1AE0"), style.backdrop)
        assertEquals(CanvasBackdrop.DEFAULT_BLUR, Json.decodeFromString<CanvasStyle>("""{"backdrop": {}}""").backdrop?.blur)
    }

    @Test
    fun problemsNameTheBadField() {
        val problems = CanvasStyle(backdrop = CanvasBackdrop(blur = -1f, tint = "white", saturation = -2f)).problems()
        assertEquals(
            listOf("backdrop.tint \"white\" must be #RRGGBB or #RRGGBBAA", "backdrop.blur must not be negative", "backdrop.saturation must not be negative"),
            problems,
        )
    }

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 200
    }
}
