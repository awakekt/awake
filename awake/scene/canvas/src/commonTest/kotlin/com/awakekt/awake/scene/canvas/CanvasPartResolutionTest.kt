/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.document.ScenePrefab
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [resolve] binds a declaration to one placed copy of its prefab, once, and fails naming each part
 * it cannot find; the handles then write the fields of their own kind on that copy's elements.
 */
class CanvasPartResolutionTest {
    @Test
    fun eachPlacedCopyBindsItsOwnDeclaration() {
        val world = World()
        val left = world.placeCanvas(actionBarFromParts().root)
        val right = world.placeCanvas(actionBarFromParts().root)

        val leftBar = ActionBarParts().resolve(world, left)
        val rightBar = ActionBarParts().resolve(world, right)

        assertEquals(left, leftBar.frame.entity, "the root is a part too")
        assertNotEquals(leftBar.slots[1].icon.entity, rightBar.slots[1].icon.entity)
        assertEquals(leftBar.slots[1].icon.entity, world.findCanvasElement(left, "bar.slot-1.icon"), "what the string lookup finds")
        assertSame(world.get<CanvasElement>(leftBar.energy.entity), leftBar.energy.element)
    }

    @Test
    fun aListResolvesOnItsOwn() {
        val world = World()
        val root = world.placeCanvas(actionBarFromParts().root)
        val parts = ActionBarParts()

        val slots = parts.slots.resolve(world, root)

        assertTrue(slots.all { it.icon.isResolved && it.button.isResolved })
        assertFalse(parts.title.isResolved, "only the list's parts are bound")
    }

    @Test
    fun aMissingPartFailsNamingItsId() {
        val world = World()
        val root = world.placeCanvas(actionBarFromStrings().root)
        // The prefab has three slots; a declaration of four asks for one it does not have.
        val parts = ActionBarParts(slotCount = 4)

        val failure = assertFailsWith<CanvasPartResolutionException> { parts.resolve(world, root) }

        assertEquals(listOf("bar.slot-3", "bar.slot-3.icon", "bar.slot-3.cooldown", "bar.slot-3.count"), failure.ids)
        assertTrue("\"bar.slot-3.icon\": no element under the root has it" in failure.message.orEmpty(), failure.message)
        assertFalse(parts.slots[0].icon.isResolved, "nothing is bound when anything is missing")
    }

    @Test
    fun aPartRenamedInThePrefabFails() {
        val world = World()
        val root = world.placeCanvas(actionBarFromParts().root)
        world.get<CanvasElement>(world.findCanvasElement(root, "bar.energy")!!)!!.elementId = "bar.stamina"

        val failure = assertFailsWith<CanvasPartResolutionException> { ActionBarParts().resolve(world, root) }

        assertEquals(listOf("bar.energy"), failure.ids)
    }

    @Test
    fun aPartOfAnotherKindFails() {
        val world = World()
        val root = world.placeCanvas(actionBarFromParts().root)
        world.get<CanvasElement>(world.findCanvasElement(root, "bar.slot-0.count")!!)!!.kind = CanvasElementKind.Panel

        val failure = assertFailsWith<CanvasPartResolutionException> { ActionBarParts().resolve(world, root) }

        assertEquals(listOf("bar.slot-0.count"), failure.ids)
        assertTrue("it is a Panel, not a Text" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun anElementOfAnotherCopyIsNotFound() {
        val world = World()
        world.placeCanvas(actionBarFromParts().root)
        val other = world.placeCanvas(canvasPrefab(guid = "g", name = "Other", id = "other") {}.root)

        val failure = assertFailsWith<CanvasPartResolutionException> { ActionBarParts().resolve(world, other) }

        assertTrue("bar.slot-0.icon" in failure.ids, "a part is looked for under the root only")
    }

    @Test
    fun anUnresolvedHandleSaysSo() {
        val failure = assertFailsWith<IllegalStateException> { ActionBarParts().slots[0].icon.image = null }

        assertTrue("bar.slot-0.icon" in failure.message.orEmpty(), failure.message)
    }

    @Test
    fun typedHandlesWriteTheirKindsFields() {
        val world = World()
        val root = world.placeCanvas(actionBarFromParts().root)
        fun element(id: String) = world.get<CanvasElement>(world.findCanvasElement(root, id)!!)!!
        val icon = CanvasImage("ui/icons/slot-1.png")

        // --8<-- [start:canvas-parts-bind]
        val bar = ActionBarParts().resolve(world, root) // once, when the prefab is placed
        // Then, each frame: typed fields on cached elements, no lookups.
        bar.title.text = "Ready"
        bar.title.textArgs = mapOf("count" to "3")
        bar.energy.value = 0.25f
        bar.slots[1].icon.image = icon
        bar.slots[1].cooldown.value = 0.4f
        bar.slots[1].count.text = "12"
        bar.slots[1].button.charges = 2
        bar.slots[2].button.enabled = false
        bar.slots[2].icon.visible = false
        // --8<-- [end:canvas-parts-bind]

        assertEquals("Ready", element("bar.title").text)
        assertEquals(mapOf("count" to "3"), element("bar.title").textArgs)
        assertEquals(0.25f, element("bar.energy").value)
        assertEquals(icon, element("bar.slot-1.icon").style.image)
        assertEquals(0.4f, element("bar.slot-1.cooldown").value)
        assertEquals("12", element("bar.slot-1.count").text)
        assertEquals(2, element("bar.slot-1").charges)
        assertFalse(element("bar.slot-2").enabled)
        assertFalse(element("bar.slot-2.icon").visible)
        assertEquals("", element("bar.slot-0.count").text, "another slot is untouched")
    }

    @Test
    fun writingTheSamePictureKeepsTheStyle() {
        val world = World()
        val bar = ActionBarParts().resolve(world, world.placeCanvas(actionBarFromParts().root))
        val icon = bar.slots[0].icon
        icon.image = CanvasImage("ui/icons/slot-0.png")
        val style = icon.element.style

        icon.image = CanvasImage("ui/icons/slot-0.png")

        assertSame(style, icon.element.style, "an equal picture makes no new style")
        icon.image = CanvasImage("ui/icons/other.png")
        assertNotSame(style, icon.element.style)
    }

    @Test
    fun resolvingAgainBindsTheCopyAReloadPlaced() {
        val world = World()
        val prefab: ScenePrefab = actionBarFromParts()
        val first = world.placeCanvas(prefab.root)
        val bar = ActionBarParts().resolve(world, first)
        world.destroy(first)
        val second = world.placeCanvas(prefab.root)

        bar.resolve(world, second)

        assertEquals(second, bar.frame.entity)
        assertFailsWith<IllegalArgumentException>("a dead root") { bar.resolve(world, first) }
    }
}
