/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.BoxWithConstraints
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.onPlaced
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.StaleSkip
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.WeightedUiFont
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A text run's settle holds count frames, not measure or paint passes (#1764).
 *
 * After its em size changes, a run keeps continuous sizing and the distance field for
 * [TextMotionState.SETTLE_FRAMES] frames, then returns to exact-size pixel glyphs on whole pixels.
 * The hold counted down on every measure. `ComposeHost.verifySkippedCompositions` measures a skipped
 * frame twice, as skipped and again after composing, so on the frame the hold ran out between the two
 * the skipped side still drew distance-field glyphs at a fractional y and the composed side pixel
 * glyphs a fraction lower: a stale skip that was not there, reported by a private consumer pack as
 * y 176.97 against 177 after a theme switch. A production frame that lays out twice, because
 * placement wrote state composition read, settled the run twice as fast.
 */
class TextHoldOncePerFrameTest {
    private val large = mutableStateOf(false)
    private val placedFrame = mutableStateOf(0)
    private var frame = 0

    context(_: Composer)
    private fun Label(writeOnPlacement: Boolean) {
        Column(Modifier.width(286.dp)) {
            if (writeOnPlacement) {
                // Read in composition, written by placement: the host composes and lays out again.
                placedFrame.value
                Box(Modifier.size(1.dp).onPlaced { _, _, _, _ -> placedFrame.value = frame })
            }
            Text("Label", style = TextStyle(size = if (large.value) 14.sp else 13.sp))
        }
    }

    @Test
    fun aSizeChangeSettlesWithoutAStaleSkip() {
        for (measureSkip in listOf(false, true)) {
            for (retain in listOf(false, true)) {
                large.value = false
                val found = mutableListOf<StaleSkip>()
                val host = ComposeHost().apply {
                    skipUnchangedComposition = true
                    skipUnchangedMeasure = measureSkip
                    retainUnchangedDraw = retain
                    verifySkippedCompositions = true
                    onStaleSkip = { found += it }
                }
                repeat(FRAMES) {
                    frame = it + 1
                    if (it == GROW_AT) large.value = true
                    if (it == SHRINK_AT) large.value = false
                    host.frame(input) { Label(writeOnPlacement = false) }
                }
                assertEquals(emptyList(), found, "measure skip $measureSkip, retained draw $retain")
            }
        }
    }

    @Test
    fun aFrameThatLaysOutTwiceHoldsTheDistanceFieldForTheWholeSettle() {
        large.value = false
        val host = ComposeHost()
        var distanceFieldFrames = 0
        repeat(FRAMES) {
            frame = it + 1
            if (it == GROW_AT) large.value = true
            val glyph = host.frame(input) { Label(writeOnPlacement = true) }
                .primitives.filterIsInstance<UiDrawPrimitive.Glyph>().first()
            if (glyph.font is WeightedUiFont) distanceFieldFrames++
        }
        assertEquals(FRAMES, host.placementRecompositions, "every frame laid out twice")
        assertEquals(TextMotionState.SETTLE_FRAMES, distanceFieldFrames, "frames drawn from the distance field")
    }

    /**
     * The same hold reached through a resize: a label inside a subcomposition whose size follows its
     * constraints, as a private consumer pack's 1 px shift after the viewport grew (#1764, case B). On
     * `main` before the fix it reported a stale skip three frames after the resize.
     */
    @Test
    fun aSizeThatFollowsASubcompositionsWidthSettlesWithoutAStaleSkip() {
        for (measureSkip in listOf(false, true)) for (retain in listOf(false, true)) {
            val found = mutableListOf<StaleSkip>()
            val host = ComposeHost().apply {
                skipUnchangedComposition = true
                skipUnchangedMeasure = measureSkip
                retainUnchangedDraw = retain
                verifySkippedCompositions = true
                onStaleSkip = { found += it }
            }
            repeat(RESIZE_FRAMES) {
                host.frame(FrameInput(if (it < RESIZE_AT) NARROW else WIDE, 360, deltaSeconds = 0.016f)) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val wide = maxWidth > THRESHOLD.dp
                        Row { Box { Text("Label", style = TextStyle(size = if (wide) 12.sp else 10.sp)) } }
                    }
                }
            }
            assertEquals(emptyList(), found, "measure skip $measureSkip, retained draw $retain")
            host.dispose()
        }
    }

    private companion object {
        val input = FrameInput(400, 300, deltaSeconds = 0.016f)
        const val FRAMES = 40
        const val GROW_AT = 5
        const val SHRINK_AT = 25
        const val RESIZE_FRAMES = 16
        const val RESIZE_AT = 4
        const val NARROW = 640
        const val WIDE = 780
        const val THRESHOLD = 700
    }
}
