/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.animation.animateFloat
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.LocalViewportSize
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.input.ImeComposition
import com.awakekt.awake.core.input.TextEditAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `ComposeHost.skipUnchangedComposition` must not change what a frame draws (#1512, stage a).
 *
 * Two hosts run the same scripted frames, one composing every frame and one skipping a pass when
 * nothing it read changed. Their draw primitives, semantics and effects must be equal after every
 * frame. The script covers a state write, hover, a click, focus and typing, a resize and an
 * animation. The skipping host must also have skipped, or the test proves nothing.
 */
class SkipUnchangedCompositionTest {
    private class Rig(skip: Boolean, private val clockFree: Boolean = false) {
        val host = ComposeHost().also { it.skipUnchangedComposition = skip }
        val count = mutableStateOf(0)
        val target = mutableStateOf(0f)
        val field = TextFieldState("")

        fun frame(input: FrameInput) = host.frame(input) { ui() }

        context(_: Composer)
        private fun ui() {
            val hover = rememberInteractionSource()
            val viewport = LocalViewportSize.current
            // The text field and the animation read the clock, which makes every timed frame compose.
            val width = if (clockFree) 0f else animateFloat(target.value)
            Column(Modifier.width((viewport.width / 2).dp)) {
                Text("clicks ${count.value}", Modifier.height(20.dp))
                Box(Modifier.size(60.dp, 20.dp).clickable { count.value += 1 }.background(Color(0.2f, 0.4f, 0.8f)))
                Box(
                    Modifier.size(60.dp, 20.dp).hoverable(hover)
                        .background(if (hover.isHovered) Color(0.9f, 0.1f, 0.1f) else Color(0.1f, 0.1f, 0.1f)),
                )
                Box(Modifier.size((10f + width * 50f).dp, 6.dp).background(Color(0.1f, 0.8f, 0.1f)))
                if (!clockFree) BasicTextField(field, Modifier.size(100.dp, 20.dp))
            }
        }
    }

    private val script: List<Pair<String, Rig.() -> FrameInput>> = listOf(
        "first" to { still(400, 300) },
        "still" to { still(400, 300) },
        "still, no time" to { still(400, 300, delta = 0f) },
        "still, no time 2" to { still(400, 300, delta = 0f) },
        "state write" to { count.value = 5; still(400, 300) },
        "hover enters" to { pointer(400, 300, 10, 30 + 20) },
        "hover stays" to { pointer(400, 300, 10, 30 + 20) },
        "click" to { pointer(400, 300, 10, 30, pressed = true, released = true) },
        "hover leaves" to { pointer(400, 300, 300, 250) },
        "focus the field" to { pointer(400, 300, 10, 71, pressed = true, released = true) },
        "type" to { still(400, 300).copy(typedText = "hi") },
        "still after typing" to { still(400, 300) },
        "resize" to { still(500, 300) },
        "animate start" to { target.value = 1f; still(500, 300) },
        "animate tick 1" to { still(500, 300) },
        "animate tick 2" to { still(500, 300) },
        "animate settled" to { still(500, 300, delta = 1f) },
        "still again" to { still(500, 300) },
        "still, no time again" to { still(500, 300, delta = 0f) },
        "still, no time again 2" to { still(500, 300, delta = 0f) },
    )

    @Test
    fun drawOutputIsIdenticalWithSkippingOnAndOff() {
        val always = Rig(skip = false)
        val skipping = Rig(skip = true)
        for ((name, step) in script) {
            val expected = always.frame(always.step())
            val actual = skipping.frame(skipping.step())
            assertEquals(expected.primitives, actual.primitives, "draw primitives differ at '$name'")
            assertEquals(expected.semantics, actual.semantics, "semantics differ at '$name'")
            assertEquals(expected.effects, actual.effects, "effects differ at '$name'")
            assertEquals(expected.ownership, actual.ownership, "input ownership differs at '$name'")
        }
        assertTrue(skipping.host.skippedCompositions > 0, "the skipping host never skipped a pass")
        assertEquals(0, always.host.skippedCompositions)
    }

    @Test
    fun aFrameWithNothingChangedSkipsAndAChangeComposes() {
        val rig = Rig(skip = true)
        rig.frame(still(400, 300, delta = 0f))
        rig.frame(still(400, 300, delta = 0f))
        assertEquals(1, rig.host.skippedCompositions, "a still frame must skip")
        rig.count.value = 1
        rig.frame(still(400, 300, delta = 0f))
        assertEquals(1, rig.host.skippedCompositions, "a state write must compose")
        rig.frame(still(401, 300, delta = 0f))
        assertEquals(1, rig.host.skippedCompositions, "a resize must compose")
        rig.frame(still(401, 300, delta = 0f))
        assertEquals(2, rig.host.skippedCompositions)
    }

    @Test
    fun hoverAndClickReachASkippingHostWithNoClockRead() {
        val always = Rig(skip = false, clockFree = true)
        val skipping = Rig(skip = true, clockFree = true)
        val frames = listOf(
            still(400, 300, delta = 0f),
            pointer(400, 300, 10, 50),
            pointer(400, 300, 10, 50),
            pointer(400, 300, 10, 30, pressed = true, released = true),
            pointer(400, 300, 10, 30),
            pointer(400, 300, 300, 250),
            pointer(400, 300, 300, 250),
        ).map { it.copy(deltaSeconds = 0f) }
        for ((i, input) in frames.withIndex()) {
            assertEquals(always.frame(input).primitives, skipping.frame(input).primitives, "primitives differ at frame $i")
        }
        assertTrue(skipping.host.skippedCompositions > 0, "the skipping host never skipped a pass")
        assertEquals(1, skipping.count.value)
    }

    /**
     * A composition that reads the field's text, caret, selection and composition state (a status
     * line) must repaint when typing, an edit key, a selection or an IME composition changes them.
     */
    @Test
    fun readsOfATextFieldStayCurrentThroughTheRealInputPath() {
        val always = TextRig(skip = false)
        val skipping = TextRig(skip = true)
        val still = FrameInput(400, 300, deltaSeconds = 0f)
        val at = still.copy(pointerX = 10, pointerY = 30)
        val frames = listOf(
            "first" to still,
            "hover" to at,
            "press" to at.copy(pointerDown = true, pointerPressed = true),
            "release" to at.copy(pointerReleased = true),
            "type" to at.copy(typedText = "hello"),
            "backspace" to at.copy(editActions = listOf(TextEditAction.Backspace)),
            "arrow left" to at.copy(editActions = listOf(TextEditAction.ArrowLeft)),
            "select all" to at.copy(editActions = listOf(TextEditAction.SelectAll)),
            "ime composing" to at.copy(imeComposition = ImeComposition("ab")),
            "ime commit" to at.copy(imeCommit = "ab"),
            "still" to still,
            "still 2" to still,
        )
        val drawn = mutableListOf<Any>()
        for ((name, input) in frames) {
            val expected = always.host.frame(input) { always.ui() }
            val actual = skipping.host.frame(input) { skipping.ui() }
            assertEquals(expected.primitives, actual.primitives, "draw primitives differ at '$name'")
            assertEquals(expected.semantics, actual.semantics, "semantics differ at '$name'")
            drawn.add(expected.primitives.toList())
        }
        assertEquals("ab", skipping.field.text, "the IME commit replaces the selected text")
        for (i in 4..9) assertTrue(drawn[i] != drawn[i - 1], "'${frames[i].first}' changed nothing, the script proves nothing")
        assertTrue(skipping.host.skippedCompositions >= 2, "the skipping host skipped ${skipping.host.skippedCompositions} passes")
    }

    private class TextRig(skip: Boolean) {
        val host = ComposeHost().also { it.skipUnchangedComposition = skip }
        val field = TextFieldState("")

        context(_: Composer)
        fun ui() {
            Column(Modifier.width(300.dp)) {
                val selection = "${field.selectionStart}-${field.selectionEnd}"
                Text("${field.text}|${field.cursor}|$selection|${field.hasComposition}", Modifier.height(20.dp))
                BasicTextField(field, Modifier.size(100.dp, 20.dp))
            }
        }
    }

    private fun still(w: Int, h: Int, delta: Float = 1f / 60f) = FrameInput(w, h, deltaSeconds = delta)

    private fun pointer(w: Int, h: Int, x: Int, y: Int, pressed: Boolean = false, released: Boolean = false) =
        FrameInput(w, h, pointerX = x, pointerY = y, pointerPressed = pressed, pointerReleased = released)
}
