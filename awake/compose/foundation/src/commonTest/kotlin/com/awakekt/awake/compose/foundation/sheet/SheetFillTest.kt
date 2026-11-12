/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.sheet

import com.awakekt.awake.asset.sprite.SpriteSheet
import com.awakekt.awake.compose.foundation.interaction.Interaction
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.core.animation.FrameClip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SheetFillTest {
    private val image = ImageBitmap(16, 8, ByteArray(16 * 8 * 4))
    private val sheet = SpriteSheet("s.png", 16, 8, 4, 4, mapOf("open" to FrameClip(5, 2, 0f, false)))

    @Test
    fun gridCellFillCutsTheCellAndSlices() {
        val fill = gridCellFill(image, 4, 4, column = 2, row = 1, slice = SliceInsets(1, 2, 1, 2), tiled = true)
        assertEquals(listOf(8, 4, 4, 4), listOf(fill.srcX, fill.srcY, fill.srcWidth, fill.srcHeight))
        assertEquals(listOf(1, 2, 1, 2), listOf(fill.sliceLeft, fill.sliceTop, fill.sliceRight, fill.sliceBottom))
        assertEquals(true, fill.repeatEdges && fill.repeatCenter)
    }

    @Test
    fun cellFillCountsInReadingOrder() {
        val fill = sheet.cellFill(image, 6)
        assertEquals(8 to 4, fill.srcX to fill.srcY)
        assertFailsWith<IllegalArgumentException> { sheet.cellFill(image, 8) }
        assertFailsWith<IllegalArgumentException> { sheet.cellFill(ImageBitmap(8, 8, ByteArray(256)), 0) }
    }

    @Test
    fun clipFillStartsAtTheClipsFirstCell() {
        assertEquals(4 to 4, sheet.clipFill(image, "open").let { it.srcX to it.srcY })
        assertEquals(8 to 4, sheet.clipFill(image, "open", offset = 1).let { it.srcX to it.srcY })
        assertFailsWith<IllegalArgumentException> { sheet.clipFill(image, "open", offset = 2) }
        assertFailsWith<IllegalArgumentException> { sheet.clipFill(image, "shut") }
    }

    @Test
    fun stateSheetLaysStatesOutAlongTheAxis() {
        val states = listOf(SheetState.Normal, SheetState.Hovered, SheetState.Disabled)
        val row = StateSheet.fromGrid(image, 4, 4, states)
        assertEquals(8, row.fill(SheetState.Disabled).srcX)
        val tall = ImageBitmap(4, 12, ByteArray(4 * 12 * 4))
        val column = StateSheet.fromGrid(tall, 4, 4, states, SheetAxis.Column)
        assertEquals(4 to 0, column.fill(SheetState.Hovered).let { it.srcY to it.srcX })
    }

    @Test
    fun missingFramesFallBack() {
        val sheet = StateSheet.fromGrid(image, 4, 4, listOf(SheetState.Normal, SheetState.Hovered))
        assertSame(sheet.fill(SheetState.Hovered), sheet.fill(SheetState.Pressed))
        assertSame(sheet.fill(SheetState.Normal), sheet.fill(SheetState.Selected))
        assertFailsWith<IllegalArgumentException> { StateSheet(emptyMap()) }
        assertFailsWith<IllegalArgumentException> { StateSheet.fromGrid(image, 4, 4, listOf(SheetState.Normal, SheetState.Normal)) }
    }

    @Test
    fun buttonStateFollowsTheInteractionSource() {
        val source = InteractionSource()
        assertEquals(SheetState.Normal, buttonState(source))
        source.tryEmit(Interaction.Hover.Enter)
        assertEquals(SheetState.Hovered, buttonState(source))
        assertEquals(SheetState.Selected, buttonState(source, selected = true))
        source.tryEmit(Interaction.Press.Press)
        assertEquals(SheetState.Pressed, buttonState(source, selected = true))
        assertEquals(SheetState.Disabled, buttonState(source, enabled = false))
        source.reset()
        source.tryEmit(Interaction.Focus.Focus)
        assertEquals(SheetState.Focused, buttonState(source))
    }
}
