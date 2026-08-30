/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.graphics.Painter
import io.github.awakelab.awake.compose.ui.layout.composeInto
import io.github.awakelab.awake.compose.ui.layout.layoutTree
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.compose.ui.unit.Constraints
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.graphics2d.DrawCommand
import io.github.awakelab.awake.core.graphics2d.UiDrawPrimitive
import io.github.awakelab.awake.ui.shadcn.components.ShadcnRangeSlider
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSlider
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The first recipe rendered through `:compose:foundation`, so this is as much a check that the path
 * works end to end as it is a check of the slider.
 */
class ShadcnSliderTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    private fun render(value: Float, enabled: Boolean = true): List<UiDrawPrimitive> {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            provideShadcnTheme(theme) {
                ShadcnSlider(
                    value = value,
                    modifier = Modifier.size(200.dp),
                    enabled = enabled,
                )
            }
        }
        root.layoutTree(Constraints.of(0, 400, 0, 400))
        return Painter().paint(root)
    }

    // `DrawCommand.RoundedQuad`, not `UiDrawPrimitive.RoundedQuad`: the variants are declared on
    // `DrawCommand` and `UiDrawPrimitive` is the name the rename plan retires. New code takes the
    // canonical one. The parameter type stays `UiDrawPrimitive` only because `Painter.paint` still
    // returns it.
    private fun rounded(primitives: List<UiDrawPrimitive>) =
        primitives.filterIsInstance<DrawCommand.RoundedQuad>()

    @Test
    fun aSliderDrawsATrackARangeAndARingedThumb() {
        val drawn = rounded(render(0.5f))

        // Four, not three: the thumb is `border-primary border bg-white`, so it is a
        // primary-filled circle with a background-filled one inside it. Drawn as a single white
        // circle it was invisible on a light background.
        assertEquals(4, drawn.size, "expected track, range, thumb ring and thumb fill")
    }

    @Test
    fun theRangeIsHalfTheTrackAtTheMidpoint() {
        val drawn = rounded(render(0.5f))
        val track = drawn[0]
        val range = drawn[1]

        assertEquals(track.w / 2f, range.w, 1f, "the filled range did not reach the value")
    }

    @Test
    fun theRangeIsEmptyAtTheMinimumAndFullAtTheMaximum() {
        assertEquals(0f, rounded(render(0f))[1].w, 0.5f, "a slider at its minimum drew a range")
        val full = rounded(render(1f))
        assertEquals(full[0].w, full[1].w, 0.5f, "a slider at its maximum did not fill its track")
    }

    @Test
    fun theThumbStaysInsideTheWidgetAtBothEnds() {
        // The ui-headless bug this port carries forward the fix for: a thumb centred on an uninset
        // track hangs half its width outside and any clipping parent cuts it.
        listOf(0f, 1f).forEach { value ->
            val thumb = rounded(render(value)).last()

            assertTrue(thumb.x >= -0.5f, "the thumb started at ${thumb.x}, outside the widget")
            assertTrue(
                thumb.x + thumb.w <= 200f + 0.5f,
                "the thumb ended at ${thumb.x + thumb.w}, past the widget's 200px",
            )
        }
    }

    @Test
    fun theTrackIsSixPixelsTallAndTheThumbSixteenAcross() {
        // shadcn's h-1.5 and size-4, at density 1.
        val drawn = rounded(render(0.5f))

        assertEquals(6f, drawn[0].h, 0.5f, "the track is not shadcn's h-1.5")
        // The thumb's outer edge is its ring, so `size-4` is the third rect. `last()` is the fill
        // inside it, which is 2px narrower -- one border on each side.
        assertEquals(16f, drawn[2].w, 0.5f, "the thumb is not shadcn's size-4")
        assertEquals(14f, drawn.last().w, 0.5f, "the thumb fill did not inset by border")
    }

    @Test
    fun aValueOutsideItsRangeIsClampedRatherThanDrawnOutside() {
        val over = rounded(render(5f))

        assertEquals(over[0].w, over[1].w, 0.5f, "an out-of-range value drew past the track")
    }

    @Test
    fun aDisabledSliderStillDrawsButDimmed() {
        // shadcn's root carries data-[disabled]:opacity-50, so the parts stay and fade.
        val enabled = rounded(render(0.5f))
        val disabled = rounded(render(0.5f, enabled = false))

        assertEquals(enabled.size, disabled.size, "disabling removed a part instead of dimming it")
        assertTrue(
            disabled[0].color.a < enabled[0].color.a,
            "a disabled slider was not dimmed",
        )
    }

    private fun renderRange(start: Float, end: Float): List<UiDrawPrimitive> {
        val root = LayoutNode(ColumnMeasurePolicy())
        composeInto(root) {
            provideShadcnTheme(theme) {
                ShadcnRangeSlider(start = start, end = end, modifier = Modifier.size(200.dp), min = 0f, max = 100f)
            }
        }
        root.layoutTree(Constraints.of(0, 400, 0, 400))
        return Painter().paint(root)
    }

    @Test
    fun aRangeSliderDrawsOneTrackOneSpanAndTwoThumbs() {
        val drawn = rounded(renderRange(25f, 75f))

        // track, span, then a ring and a fill for each of two thumbs -- six, not the eight two
        // independently-drawn ShadcnSliders would have produced (two tracks instead of one).
        assertEquals(6, drawn.size, "expected one track, one span and two ringed thumbs")
    }

    @Test
    fun theSpanRunsFromTheStartThumbToTheEndThumb() {
        val drawn = rounded(renderRange(25f, 75f))
        val track = drawn[0]
        val span = drawn[1]

        assertEquals(track.x + track.w * 0.25f, span.x, 1f, "the span did not start at the lower value")
        assertEquals(track.w * 0.5f, span.w, 1f, "the span did not run to the upper value")
    }

    @Test
    fun bothThumbsStayInsideTheWidgetAtTheExtremes() {
        val drawn = rounded(renderRange(0f, 100f))
        val startThumb = drawn[2]
        val endThumb = drawn[4]

        assertTrue(startThumb.x >= -0.5f, "the start thumb started at ${startThumb.x}, outside the widget")
        assertTrue(
            endThumb.x + endThumb.w <= 200f + 0.5f,
            "the end thumb ended at ${endThumb.x + endThumb.w}, past the widget's 200px",
        )
    }
}
