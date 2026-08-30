/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.foundation.text.rememberTextFieldState
import io.github.awakelab.awake.compose.testing.composeTestSession
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.platform.FrameInput
import io.github.awakelab.awake.compose.ui.semantics.testTag
import io.github.awakelab.awake.core.input.TextEditAction
import io.github.awakelab.awake.ui.shadcn.components.ShadcnInputOtp
import io.github.awakelab.awake.ui.shadcn.components.ShadcnInputOtpPatterns
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** Types into the OTP the way the frame loop does, one committed keystroke per frame. */
class ShadcnInputOtpTest {

    @Test
    fun typingDigitsFillsTheSlots() {
        lateinit var read: () -> String
        val session = composeTestSession(WIDTH, HEIGHT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                val state = rememberTextFieldState("")
                read = { state.text }
                ShadcnInputOtp(state = state, modifier = Modifier.testTag("otp"))
            }
        }
        val bounds = session.frame().onNodeWithTag("otp").getBoundsInRoot()
        println("PROBE otp bounds=$bounds")

        session.click("otp")
        "123456".forEach { session.frame(typing(it)) }
        session.frame()
        println("PROBE after typing text='${read()}'")
        assertEquals("123456", read(), "typing six digits should fill the six slots")
    }

    @Test
    fun typingPastTheEndIsRejected() {
        lateinit var read: () -> String
        val session = composeTestSession(WIDTH, HEIGHT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                val state = rememberTextFieldState("")
                read = { state.text }
                ShadcnInputOtp(state = state, modifier = Modifier.testTag("otp"))
            }
        }
        session.frame()
        session.click("otp")
        "12345678".forEach { session.frame(typing(it)) }
        session.frame()
        println("PROBE overflow text='${read()}'")
        assertEquals("123456", read(), "a seventh digit must not be kept, hidden or otherwise")
    }

    @Test
    fun aPatternRejectsWhatDoesNotMatchIt() {
        assertEquals("123", typeInto("1a2b3c", pattern = ShadcnInputOtpPatterns.Digits))
    }

    /**
     * Upstream's `pattern` has no default and `input-otp` accepts anything without one, because a
     * one-time code is not always numeric. Pinned so the permissiveness reads as a decision.
     */
    @Test
    fun withoutAPatternAnyCharacterIsAccepted() {
        assertEquals("1a2b3c", typeInto("1a2b3c", pattern = null))
    }

    /**
     * The reported symptom was that backspace did nothing, and the diagnosis was that it was
     * deleting characters the cap had let in but never drawn. That claim is only worth anything if
     * backspace is actually exercised.
     */
    @Test
    fun backspaceDeletesTheLastDigitImmediately() {
        lateinit var read: () -> String
        val session = otpSession { read = it }
        session.frame()
        session.click("otp")
        "123456".forEach { session.frame(typing(it)) }
        // Two more than fit. Before the cap these were kept, so the first two backspaces below
        // deleted invisible characters and the display did not move.
        "78".forEach { session.frame(typing(it)) }
        session.frame(editing(TextEditAction.Backspace))
        session.frame()
        println("PROBE after one backspace text='${read()}'")
        assertEquals("12345", read(), "one backspace should remove one visible digit")
    }

    @Test
    fun backspaceEmptiesTheFieldOneDigitAtATime() {
        lateinit var read: () -> String
        val session = otpSession { read = it }
        session.frame()
        session.click("otp")
        "1234".forEach { session.frame(typing(it)) }
        repeat(4) { session.frame(editing(TextEditAction.Backspace)) }
        session.frame()
        assertEquals("", read(), "four backspaces should clear four digits")
    }

    private fun otpSession(bind: (() -> String) -> Unit) = composeTestSession(WIDTH, HEIGHT) {
        provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
            val state = rememberTextFieldState("")
            bind { state.text }
            ShadcnInputOtp(state = state, modifier = Modifier.testTag("otp"))
        }
    }

    private fun editing(action: TextEditAction) =
        FrameInput(WIDTH, HEIGHT, editActions = listOf(action))

    /**
     * A control's size must not depend on whether it is editable.
     *
     * The focus overlay is only composed when enabled, so with `fillMaxSize` an enabled field
     * measured its parent's whole area and a disabled one measured its slots -- and the enabled
     * one's click target covered the entire parent along with it.
     */
    @Test
    fun theFieldMeasuresItsSlotsWhetherOrNotItIsEditable() {
        fun boundsWhen(enabled: Boolean) = composeTestSession(WIDTH, HEIGHT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                val state = rememberTextFieldState("123456")
                ShadcnInputOtp(state = state, modifier = Modifier.testTag("otp"), enabled = enabled)
            }
        }.frame().onNodeWithTag("otp").getBoundsInRoot()

        val editable = boundsWhen(enabled = true)
        val disabled = boundsWhen(enabled = false)
        println("PROBE editable=$editable disabled=$disabled")
        assertEquals(disabled.width, editable.width, "an editable field must not be wider")
        assertEquals(disabled.height, editable.height, "an editable field must not be taller")
        assertEquals(SIX_SLOTS_WIDE, editable.width, "six 36dp slots")
    }

    private fun typeInto(keys: String, pattern: Regex?): String {
        lateinit var read: () -> String
        val session = composeTestSession(WIDTH, HEIGHT) {
            provideShadcnTheme(ShadcnThemeValues(ShadcnTheme)) {
                val state = rememberTextFieldState("")
                read = { state.text }
                ShadcnInputOtp(state = state, modifier = Modifier.testTag("otp"), pattern = pattern)
            }
        }
        session.frame()
        session.click("otp")
        keys.forEach { session.frame(typing(it)) }
        session.frame()
        println("PROBE keys='$keys' pattern=$pattern text='${read()}'")
        return read()
    }

    private fun typing(c: Char) = FrameInput(WIDTH, HEIGHT, typedText = c.toString())

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 80
        const val SIX_SLOTS_WIDE = 216
    }
}
