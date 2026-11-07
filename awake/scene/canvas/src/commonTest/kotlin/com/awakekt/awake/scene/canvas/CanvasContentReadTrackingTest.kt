/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.ui.canvas.CanvasElement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [canvasContent] recomposes on the frame after tracked state its last pass read changed, and on no
 * other: a `mutableStateOf` it read, or the `StateFlow` it was made from.
 */
class CanvasContentReadTrackingTest {
    private val world = World()
    private val hud = world.create().also { world.add(it, Transform()) }
    private val system = CanvasContentSystem()

    private fun frame() = system.update(world, 0f)

    private fun text(id: String): String? =
        world.findCanvasElement(hud, id)?.let { world.get<CanvasElement>(it) }?.text

    @Test
    fun aWriteToStateTheBlockReadRecomposesItOnTheNextFrame() {
        val label = mutableStateOf("one")
        canvasContent(world, hud) { text(id = "label") { text = label.value } }

        label.value = "two"
        assertEquals("one", text("label"), "nothing runs before the frame")
        frame()

        assertEquals("two", text("label"))
    }

    @Test
    fun aWriteToStateTheBlockDidNotReadRunsNothing() {
        val read = mutableStateOf("one")
        val unread = mutableStateOf(0)
        var passes = 0
        canvasContent(world, hud) {
            passes++
            text(id = "label") { text = read.value }
        }

        unread.value = 1
        repeat(3) { frame() }

        assertEquals(1, passes)
    }

    @Test
    fun aStateBlockAlsoFollowsStateItReads() {
        val party = MutableStateFlow("ana")
        val title = mutableStateOf("Party")
        var passes = 0
        canvasContent(world, hud, party) { leader ->
            passes++
            text(id = "title") { text = "${title.value}: $leader" }
        }

        title.value = "Group"
        frame()
        assertEquals("Group: ana", text("title"))
        party.value = "bo"
        frame()
        frame()

        assertEquals("Group: bo", text("title"))
        assertEquals(3, passes)
    }

    @Test
    fun aDisposedBlockIgnoresWrites() {
        val label = mutableStateOf("one")
        var passes = 0
        val content = canvasContent(world, hud) {
            passes++
            text(id = "label") { text = label.value }
        }
        content.dispose()

        label.value = "two"
        frame()

        assertEquals(1, passes)
        assertEquals(null, text("label"))
    }
}
