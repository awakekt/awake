/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.scene.document.SceneValidationException
import com.awakekt.awake.ui.canvas.CanvasElementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A [CanvasParts] declaration composes each element's id in one place, and the prefab its handles
 * build is the prefab string ids built: the same nodes and the same file, so nothing saved changes.
 */
class CanvasPartsTest {
    @Test
    fun idsAreDerivedFromTheDeclaration() {
        val parts = ActionBarParts()

        assertEquals("bar", parts.frame.id, "a group's own element takes the group's id")
        assertEquals("bar.title", parts.title.id)
        assertEquals("bar.slot-0", parts.slots[0].button.id)
        assertEquals("bar.slot-2.icon", parts.slots[2].icon.id)
        assertEquals(listOf(0, 1, 2), parts.slots.map { it.index })
        assertEquals(-1, parts.index, "a widget is not a list item")
        assertEquals(CanvasElementKind.Image, parts.slots[1].icon.kind)
        assertEquals(CanvasElementKind.Sweep, parts.slots[1].cooldown.kind)
    }

    @Test
    fun handlesBuildTheSamePrefabAsHandWrittenIds() {
        val fromParts = actionBarFromParts()
        val fromStrings = actionBarFromStrings()

        assertEquals(fromStrings, fromParts)
        assertEquals(CanvasPrefabs.toJson(fromStrings), CanvasPrefabs.toJson(fromParts), "the saved file is unchanged")
        assertNotNull(fromParts.findCanvasNode("bar.slot-1.cooldown"), "the string lookup finds what a handle built")
    }

    @Test
    fun aPinnedIdKeepsTheIdAPrefabAlreadySaves() {
        class LegacySlot(scope: CanvasPartScope) : CanvasParts(scope) {
            val cooldown = sweep("cooldown", pinnedId = "slot-${scope.index + 1}-cooldown")
        }
        class LegacyBar : CanvasWidgetParts("bar") {
            val slots = list("slot", 2) { LegacySlot(it) }
            val energy = bar("energy", pinnedId = "energy")
        }
        val parts = LegacyBar()

        assertEquals(listOf("slot-1-cooldown", "slot-2-cooldown"), parts.slots.map { it.cooldown.id })
        assertEquals("energy", parts.energy.id)
    }

    @Test
    fun groupsNestTheirIds() {
        class Header(scope: CanvasPartScope) : CanvasParts(scope) {
            val label = text("label")
        }
        class Window : CanvasWidgetParts("window") {
            val header = group("header") { Header(it) }
        }

        assertEquals("window.header.label", Window().header.label.id)
        assertEquals("window.header", Window().header.id)
    }

    @Test
    fun aNameOrIdThatCannotBeSavedIsRefused() {
        class Dotted : CanvasWidgetParts("w") {
            val label = text("a.b")
        }
        class BadPin : CanvasWidgetParts("w") {
            val label = text("label", pinnedId = "-nope")
        }

        val dotted = assertFailsWith<IllegalArgumentException> { Dotted() }
        assertTrue("a.b" in dotted.message.orEmpty(), dotted.message)
        assertFailsWith<IllegalArgumentException> { BadPin() }
        assertFailsWith<IllegalArgumentException> { object : CanvasWidgetParts("") {} }
    }

    @Test
    fun aPartPlacedTwiceInOnePrefabIsReported() {
        val parts = ActionBarParts()

        assertFailsWith<SceneValidationException> {
            canvasPrefab(guid = BAR_GUID, name = "Twice", root = parts.frame) {
                text(parts.title)
                text(parts.title, name = "Again")
            }
        }
    }
}
