/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.ecs.World
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.consumeLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A Text element's `[[key|shown words]]` span draws in the link colour, and a click on its words
 * reports the key once through [CanvasElement.consumeLink]. Plain text with no span is unchanged.
 */
class CanvasLinkTest {

    private val world = World()
    private val host = ComposeHost()

    private fun textElement(text: String, width: Float = 300f, height: Float = 40f): CanvasElement {
        val element = CanvasElement().apply { kind = CanvasElementKind.Text; this.text = text; this.width = width; this.height = height }
        world.add(world.create(), element)
        return element
    }

    private fun frame(x: Int = -1, y: Int = -1, pressed: Boolean = false, down: Boolean = false, released: Boolean = false): FrameOutput =
        host.frame(FrameInput(WIDTH, HEIGHT, pointerX = x, pointerY = y, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
            SceneCanvas(world)
        }

    /** Taps the pixel ([x], [y]) the way a mouse does: the pointer arrives, goes down and comes up. */
    private fun tap(x: Int, y: Int) {
        frame(x, y)
        frame(x, y, pressed = true, down = true)
        frame(x, y, released = true)
    }

    @Test
    fun aClickOnTheShownWordsReportsTheKeyOnce() {
        val element = textElement("See [[quest|the quest board]] for work.")
        val box = assertNotNull(frame().semantics.find("canvas-link-quest"), "the link's words must be on screen")

        tap(box.x + box.width / 2, box.y + box.height / 2)

        assertEquals("quest", element.consumeLink())
        assertNull(element.consumeLink(), "consumeLink must answer the key only once")
    }

    @Test
    fun aClickElsewhereReportsNull() {
        val element = textElement("See [[quest|the quest board]] for work.")
        frame()

        tap(0, 0) // the plain "See " prefix, not the link

        assertNull(element.consumeLink())
    }

    @Test
    fun linksFalseDrawsTheSpanSyntaxAsTypedAndReportsNoKey() {
        val element = textElement("See [[quest|the quest board]] for work.").apply { links = false }
        val output = frame()

        assertNull(output.semantics.find("canvas-link-quest"), "links = false must gain no clickable span")
        tap(0, 0)

        assertNull(element.consumeLink())
    }

    @Test
    fun plainTextWithNoSpansDrawsNoLinkAndNeverReportsOne() {
        val element = textElement("Just words, no brackets.")
        val output = frame()

        assertNull(output.semantics.find("canvas-link-quest"), "plain text must not gain a clickable span")
        tap(10, 10)
        assertNull(element.consumeLink())
    }

    @Test
    fun wordsWithLinksWrapAtTheBoxsEdgeAndAtALineBreak() {
        textElement("[[a|one]] [[b|two]] [[c|three]]\n[[d|four]]", width = 60f, height = 200f)
        val semantics = frame().semantics
        val first = assertNotNull(semantics.find("canvas-link-a"))
        val wrapped = assertNotNull(semantics.find("canvas-link-c"))
        val broken = assertNotNull(semantics.find("canvas-link-d"))

        assertTrue(wrapped.y > first.y, "three words do not fit 60 wide, so the last wraps below the first")
        assertTrue(broken.y > wrapped.y, "the line break starts a line below")
    }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 100
    }
}

private fun List<SemanticsNode>.find(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.find(tag) }
