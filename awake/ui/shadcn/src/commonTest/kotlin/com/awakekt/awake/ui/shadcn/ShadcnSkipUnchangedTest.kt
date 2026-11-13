/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.input.key.KeyEvent
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.ui.shadcn.components.ShadcnCalendar
import com.awakekt.awake.ui.shadcn.components.ShadcnToggle
import com.awakekt.awake.ui.shadcn.components.ShadcnTooltipped
import com.awakekt.awake.ui.shadcn.components.SidebarState
import com.awakekt.awake.ui.shadcn.components.rememberSidebarState
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * `ComposeHost.skipUnchangedComposition` must not change what shipped components draw (#1512).
 *
 * Each test runs the same frames on two hosts, one composing every frame and one skipping a pass in
 * which nothing it read changed, and compares draw primitives and the semantics tree after every
 * frame. The state each component keeps (a finger hold, the displayed month, a sidebar's open flag)
 * used to be a plain field a skipped pass could not see change.
 */
class ShadcnSkipUnchangedTest {
    private class TwinHosts(
        private val ui: context(Composer)
        () -> Unit,
    ) {
        val always = ComposeHost().also { it.skipUnchangedComposition = false }
        val skipping = ComposeHost().also { it.skipUnchangedComposition = true }
        private val drawn = mutableListOf<List<Any>>()

        fun frame(name: String, input: FrameInput): FrameOutput = frame(name) { input }

        /** [make] is called once per host: a key event is consumed by the host that handles it. */
        fun frame(name: String, make: () -> FrameInput): FrameOutput {
            val expected = always.frame(make(), ui)
            val actual = skipping.frame(make(), ui)
            assertEquals(expected.primitives.size, actual.primitives.size, "draw primitive counts differ at '$name'")
            assertEquals(expected.primitives, actual.primitives, "draw primitives differ at '$name'")
            // Compared by their text: a node holds its action lambdas, which differ between two hosts.
            assertEquals(expected.semantics.toString(), actual.semantics.toString(), "semantics differ at '$name'")
            drawn.add(expected.primitives.toList())
            return expected
        }

        fun assertChanged(from: Int, to: Int, what: String) =
            assertNotEquals(drawn[from], drawn[to], "$what changed nothing, so the script proves nothing")

        fun assertSkipped() = assertTrue(skipping.skippedCompositions > 0, "the skipping host never skipped a pass")
    }

    private fun quiet(w: Int = 400, h: Int = 400) = FrameInput(w, h, deltaSeconds = 0f)

    @Test
    fun aFingerHoldOpensAndClosesATooltipOnASkippingHost() {
        val rig = TwinHosts {
            provideShadcnTheme(shadcnThemeValues(dark = true)) {
                Column {
                    Spacer(Modifier.size(60.dp))
                    ShadcnTooltipped("Wireframe") { Box(Modifier.size(40.dp).clickable { }) {} }
                }
            }
        }

        fun finger(down: Boolean, seconds: Float = 0f) =
            FrameInput(300, 200, pointers = listOf(PointerFrame(1, 20, 80, down)), deltaSeconds = seconds)
        rig.frame("first", FrameInput(300, 200, deltaSeconds = 0f))
        rig.frame("down", finger(down = true))
        rig.frame("down 2", finger(down = true))
        rig.frame("held", finger(down = true, seconds = 0.6f))
        rig.frame("held 2", finger(down = true))
        rig.frame("lifted", finger(down = false))
        rig.frame("lifted 2", FrameInput(300, 200, deltaSeconds = 0f))
        rig.assertChanged(1, 4, "holding a finger")
        rig.assertChanged(4, 6, "lifting it")
        rig.assertSkipped()
    }

    @Test
    fun theCalendarShowsTheNextMonthAfterAKeyboardActivationOnASkippingHost() {
        val rig = TwinHosts {
            provideShadcnTheme(shadcnThemeValues(dark = true)) {
                ShadcnCalendar(selectedDate = null, onDateSelected = {})
            }
        }

        fun key(key: Key, type: KeyEventType): () -> FrameInput = { quiet().copy(keyEvents = listOf(KeyEvent(key = key, type = type))) }
        rig.frame("first", quiet())
        // Focus moves to the previous button, then to the next one. Activating it by key changes the
        // month with no hover, press or focus count changing in that frame.
        for (i in 1..2) {
            rig.frame("tab $i down", key(Key.Tab, KeyEventType.Down))
            rig.frame("tab $i up", key(Key.Tab, KeyEventType.Up))
        }
        rig.frame("still", quiet())
        rig.frame("enter down", key(Key.Enter, KeyEventType.Down))
        rig.frame("enter up", key(Key.Enter, KeyEventType.Up))
        rig.frame("after", quiet())
        rig.frame("after 2", quiet())
        rig.assertChanged(5, 8, "activating the next-month button")
        rig.assertSkipped()
    }

    @Test
    fun aToggleActivatedByKeyChangesOnASkippingHost() {
        val rig = TwinHosts {
            provideShadcnTheme(shadcnThemeValues(dark = true)) {
                val on = remember { mutableStateOf(false) }
                on.value = ShadcnToggle("Pin", on.value)
            }
        }

        fun key(key: Key, type: KeyEventType): () -> FrameInput = { quiet().copy(keyEvents = listOf(KeyEvent(key = key, type = type))) }
        rig.frame("first", quiet())
        rig.frame("tab down", key(Key.Tab, KeyEventType.Down))
        rig.frame("tab up", key(Key.Tab, KeyEventType.Up))
        rig.frame("still", quiet())
        rig.frame("space down", key(Key.Space, KeyEventType.Down))
        rig.frame("space up", key(Key.Space, KeyEventType.Up))
        rig.frame("after", quiet())
        rig.frame("after 2", quiet())
        rig.assertChanged(3, 7, "activating the toggle")
        rig.assertSkipped()
    }

    @Test
    fun aSidebarOpenedFromOutsideCompositionRedrawsOnASkippingHost() {
        val states = mutableListOf<SidebarState>()
        val rig = TwinHosts {
            val state = rememberSidebarState()
            if (states.none { it === state }) states.add(state)
            Text(if (state.isOpen) "open" else "closed")
        }
        rig.frame("first", quiet())
        rig.frame("still", quiet())
        // From outside composition, as a keyboard shortcut would: one state per host, toggled in step.
        states.forEach { it.toggle() }
        rig.frame("toggled", quiet())
        rig.frame("still 2", quiet())
        rig.assertChanged(1, 2, "toggling the sidebar")
        rig.assertSkipped()
    }
}
