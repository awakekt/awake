/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.skin

import com.awakekt.awake.compose.foundation.interaction.Interaction
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.sheet.SheetState
import com.awakekt.awake.compose.foundation.sheet.SliceInsets
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val Panel = FrameKey("panel")
private val Button = StatesKey("button")
private val Gauge = BarKey("gauge")
private val Unused = FrameKey("unused")

class SkinTest {
    private val sheet = ImageBitmap(32, 16, ByteArray(32 * 16 * 4))

    private fun sampleSkin(image: () -> ImageBitmap? = { sheet }) = skin(image, pixelsPerDp = 2f) {
        frame(Panel, SheetRect(0, 0, 8, 8), SliceInsets(2), repeatEdges = true)
        states(Button, SheetRect(8, 0, 16, 4))
        bar(Gauge, SheetRect(0, 8, 8, 4), SliceInsets(left = 2, right = 2))
    }

    @Test
    fun builderCutsEachPartFromItsRectangle() {
        val skin = sampleSkin()
        val panel = skin.resolve(Panel)!!
        assertEquals(listOf(0, 0, 8, 8, 2), listOf(panel.srcX, panel.srcY, panel.srcWidth, panel.srcHeight, panel.sliceLeft))
        assertTrue(panel.repeatEdges && !panel.repeatCenter)
        assertEquals(2f, panel.pixelsPerDp)
        val pressed = skin.resolve(Button)!!.fill(SheetState.Pressed)
        assertEquals(listOf(16, 0, 4, 4), listOf(pressed.srcX, pressed.srcY, pressed.srcWidth, pressed.srcHeight))
        assertEquals(setOf<PartKey<*>>(Panel, Button, Gauge), skin.keys)
    }

    @Test
    fun aPartIsResolvedOnceAndAgainOnlyForANewImage() {
        var current = sheet
        val skin = sampleSkin { current }
        assertSame(skin.resolve(Panel), skin.resolve(Panel))
        val first = skin.resolve(Panel)
        current = ImageBitmap(32, 16, ByteArray(32 * 16 * 4))
        assertTrue(first !== skin.resolve(Panel))
    }

    @Test
    fun beforeTheSheetDecodesNothingResolves() {
        val skin = sampleSkin { null }
        assertNull(skin.resolve(Panel))
        assertNull(skin.resolve(Button))
    }

    @Test
    fun aMissingPartIsNamedByTheCheck() {
        val skin = sampleSkin()
        assertEquals(listOf<PartKey<*>>(Unused), skin.missing(listOf(Panel, Unused)))
        skin.requireParts(listOf(Panel, Button, Gauge))
        val failure = assertFailsWith<IllegalArgumentException> { skin.requireParts(listOf(Unused, Panel)) }
        assertTrue("unused" in failure.message.orEmpty(), failure.message)
        assertNull(skin.resolve(Unused))
    }

    @Test
    fun declaringAPartTwiceOrAnUnevenStripFails() {
        assertFailsWith<IllegalArgumentException> {
            skin(sheet) {
                frame(Panel, SheetRect(0, 0, 8, 8))
                frame(Panel, SheetRect(0, 0, 8, 8))
            }
        }
        assertFailsWith<IllegalArgumentException> { skin(sheet) { states(Button, SheetRect(0, 0, 15, 4)) } }
    }

    @Test
    fun stateSelectionFollowsTheInteraction() {
        val states = sampleSkin().resolve(Button)!!
        val source = InteractionSource()
        assertEquals(8, states.fill(source).srcX)
        source.tryEmit(Interaction.Hover.Enter)
        assertEquals(12, states.fill(source).srcX)
        source.tryEmit(Interaction.Press.Press)
        assertEquals(16, states.fill(source).srcX)
        assertEquals(20, states.fill(source, enabled = false).srcX)
    }
}
