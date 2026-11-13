/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.animation.animateFloat
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.height
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.foundation.sheet.SliceInsets
import com.awakekt.awake.compose.foundation.skin.LocalSkin
import com.awakekt.awake.compose.foundation.skin.SheetRect
import com.awakekt.awake.compose.foundation.skin.SkinnedBar
import com.awakekt.awake.compose.foundation.skin.SkinnedButton
import com.awakekt.awake.compose.foundation.skin.SkinnedKeys
import com.awakekt.awake.compose.foundation.skin.skin
import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.LocalViewportSize
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Four state frames, a track and a fill, one solid colour per 4 x 4 cell. Shared, so both hosts draw one image.
private val sheet = ImageBitmap(
    24,
    4,
    ByteArray(24 * 4 * 4) { i -> if (i % 4 == 3) -1 else (40 * ((i / 4) % 24 / 4 + 1) + 16 * (i % 4)).toByte() },
)
private val art = skin(sheet) {
    states(SkinnedKeys.ButtonFace, SheetRect(0, 0, 16, 4))
    frame(SkinnedKeys.BarTrack, SheetRect(16, 0, 4, 4), SliceInsets(1))
    bar(SkinnedKeys.BarFill, SheetRect(20, 0, 4, 4), SliceInsets(1))
}

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
        val gauge = mutableStateOf(0f)
        val clicked = mutableStateOf(0)

        fun frame(input: FrameInput) = host.frame(input) { ui() }

        context(_: Composer)
        private fun ui() {
            val hover = rememberInteractionSource()
            val viewport = LocalViewportSize.current
            // The text field and the animation read the clock, which makes every timed frame compose.
            val width = if (clockFree) 0f else animateFloat(target.value)
            Row {
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
                CompositionLocalProvider(LocalSkin provides art) {
                    Column(Modifier.width(60.dp)) {
                        SkinnedButton({ clicked.value += 1 }, Modifier.size(60.dp, 20.dp))
                        SkinnedBar({ gauge.value }, Modifier.size(60.dp, 8.dp))
                    }
                }
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
        "skinned button hover" to { pointer(400, 300, 210, 10) },
        "skinned button press" to { still(400, 300).copy(pointerX = 210, pointerY = 10, pointerDown = true) },
        "skinned button release" to { pointer(400, 300, 210, 10) },
        "skinned button leave" to { pointer(400, 300, 300, 250) },
        "gauge half" to { gauge.value = 0.5f; still(400, 300) },
        "gauge full" to { gauge.value = 1f; still(400, 300) },
        "gauge still" to { still(400, 300) },
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
            assertEquals(describe(expected.semantics), describe(actual.semantics), "semantics differ at '$name'")
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

    // A node's config has no equals, so two hosts' trees compare by role, label, state and bounds.
    private fun describe(nodes: List<SemanticsNode>): List<String> = nodes.flatMap { node ->
        val c = node.config
        listOf("$node ${c[SemanticsProperties.Label]} ${c[SemanticsProperties.Disabled]}") + describe(node.children)
    }

    private fun still(w: Int, h: Int, delta: Float = 1f / 60f) = FrameInput(w, h, deltaSeconds = delta)

    private fun pointer(w: Int, h: Int, x: Int, y: Int, pressed: Boolean = false, released: Boolean = false) =
        FrameInput(w, h, pointerX = x, pointerY = y, pointerPressed = pressed, pointerReleased = released)
}
