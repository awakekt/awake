/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.sheet.SliceInsets
import com.awakekt.awake.compose.foundation.skin.LocalSkin
import com.awakekt.awake.compose.foundation.skin.SheetRect
import com.awakekt.awake.compose.foundation.skin.Skin
import com.awakekt.awake.compose.foundation.skin.skin
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.testing.ComposeComponentFrame
import com.awakekt.awake.compose.testing.ComposeTestSession
import com.awakekt.awake.compose.testing.composeTestSession
import com.awakekt.awake.compose.testing.rasterize
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnCard
import com.awakekt.awake.ui.shadcn.components.ShadcnProgressBar
import com.awakekt.awake.ui.shadcn.components.ShadcnSkinKeys
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The shadcn button, card and progress bar draw from `LocalSkin` and are unchanged without it. */
class ShadcnSkinTest {
    // Six 4 x 4 cells in a row, one solid colour each: the button's four states, the track, the fill.
    private val sheet = ImageBitmap(
        24,
        4,
        ByteArray(24 * 4 * 4) { i ->
            val colour = ART[(i / 4) % 24 / 4]
            if (i % 4 == 3) -1 else ((colour shr (16 - 8 * (i % 4))) and 0xFF).toByte()
        },
    )
    private val full: Skin = skin(sheet) {
        states(ShadcnSkinKeys.ButtonFace, SheetRect(0, 0, 16, 4))
        frame(ShadcnSkinKeys.ProgressTrack, SheetRect(16, 0, 4, 4), SliceInsets(1))
        bar(ShadcnSkinKeys.ProgressFill, SheetRect(20, 0, 4, 4), SliceInsets(1))
        frame(ShadcnSkinKeys.CardFrame, SheetRect(16, 0, 4, 4), SliceInsets(2))
    }
    private val onlyCard: Skin = skin(sheet) { frame(ShadcnSkinKeys.CardFrame, SheetRect(16, 0, 4, 4), SliceInsets(2)) }

    private val theme = ShadcnThemeValues(ShadcnTheme)

    context(_: Composer)
    private fun themed(skin: Skin?, content: context(Composer) () -> Unit) =
        provideShadcnTheme(theme) { CompositionLocalProvider(LocalSkin provides skin, content = content) }

    private fun pixels(frame: ComposeComponentFrame, size: Int): ByteArray = frame.primitives.rasterize(size, size)

    private fun rgb(px: ByteArray, size: Int, x: Int, y: Int): Int {
        val o = (y * size + x) * 4
        return ((px[o].toInt() and 0xFF) shl 16) or ((px[o + 1].toInt() and 0xFF) shl 8) or (px[o + 2].toInt() and 0xFF)
    }

    context(_: Composer)
    private fun button(enabled: Boolean) =
        ShadcnButton(Modifier.size(24.dp).testTag("b"), size = ShadcnButtonSizeVariant.Icon, enabled = enabled, content = {})

    private fun buttonStates(skin: Skin?, density: Float): List<ByteArray> {
        var enabled = true
        val session = composeTestSession(width = 24, height = 24, density = density) {
            themed(skin) { button(enabled) }
        }
        val mid = (12 * density).toInt()
        val press = FrameInput(24, 24, pointerX = mid, pointerY = mid, pointerDown = true)
        val normal = pixels(session.frame(), 24)
        val hovered = pixels(session.hover("b"), 24)
        val pressed = pixels(session.frame(press), 24)
        enabled = false
        return listOf(normal, hovered, pressed, pixels(session.frame(press), 24))
    }

    @Test
    fun aSkinnedButtonShowsItsFourStatesFromTheSkin() {
        for (density in listOf(1f, 1.5f)) {
            val mid = (12 * density).toInt()
            val states = buttonStates(full, density).map { rgb(it, 24, mid, mid) }
            assertEquals(ART.take(4), states, "button states at $density")
        }
    }

    @Test
    fun withNoSkinOrASkinMissingThePartTheButtonIsUnchanged() {
        for (density in listOf(1f, 1.5f)) {
            val none = buttonStates(null, density)
            val missing = buttonStates(onlyCard, density)
            none.indices.forEach { assertContentEquals(none[it], missing[it], "state $it at $density") }
            assertNotEquals(ART[0], rgb(none[0], 24, 12, 12))
        }
    }

    @Test
    fun aDisabledSkinnedButtonTakesNoClick() {
        var clicks = 0
        val session = composeTestSession(width = 24, height = 24) {
            themed(full) {
                ShadcnButton(Modifier.size(24.dp).testTag("b"), size = ShadcnButtonSizeVariant.Icon, enabled = false, onClick = { clicks++ }, content = {})
            }
        }
        session.frame()
        session.click("b")
        assertEquals(0, clicks)
    }

    private fun bar(skin: Skin?, value: Float, density: Float): ByteArray {
        val session = composeTestSession(width = 48, height = 24, density = density) {
            themed(skin) {
                Box(Modifier.size(20.dp, 4.dp)) { ShadcnProgressBar(value, thickness = 4.dp) }
            }
        }
        return pixels(session.frame(), 48)
    }

    @Test
    fun aSkinnedBarFillsFromTheSkinAtEveryValueAndDensity() {
        for (density in listOf(1f, 1.5f)) {
            val y = (2 * density).toInt()
            val q = (5 * density).toInt()
            val threeQ = (15 * density).toInt()
            fun probe(value: Float) = bar(full, value, density).let { listOf(rgb(it, 48, q, y), rgb(it, 48, threeQ, y)) }
            assertEquals(listOf(ART[4], ART[4]), probe(0f), "0 at $density")
            assertEquals(listOf(ART[5], ART[4]), probe(0.5f), "0.5 at $density")
            assertEquals(listOf(ART[5], ART[5]), probe(1f), "1 at $density")
        }
    }

    @Test
    fun aBarWithNoSkinOrASkinMissingItsPartsIsUnchanged() {
        for (value in listOf(0f, 0.5f, 1f)) {
            assertContentEquals(bar(null, value, 1f), bar(onlyCard, value, 1f), "value $value")
        }
    }

    @Test
    fun aCardUnderASkinInsetsItsContentByTheSliceInsets() {
        fun contentLeft(skin: Skin?): Int {
            val session = composeTestSession(width = 200, height = 100) {
                themed(skin) {
                    ShadcnCard(Modifier.size(120.dp, 80.dp), contentPadding = 0.dp) { content { Box(Modifier.size(4.dp).testTag("c")) } }
                }
            }
            return session.frame().onNodeWithTag("c").getBoundsInRoot().left
        }
        assertEquals(1, contentLeft(null), "the token border")
        assertEquals(2, contentLeft(onlyCard), "two source pixels of slice inset")
    }

    private inner class Rig(skip: Boolean, private val art: Skin) {
        val host = ComposeHost().also { it.skipUnchangedComposition = skip }
        val value = mutableStateOf(0f)

        fun frame(input: FrameInput) = host.frame(input) { ui() }

        context(_: Composer)
        private fun ui() = themed(art) {
            Box(Modifier.size(24.dp)) { ShadcnButton(Modifier.size(24.dp), size = ShadcnButtonSizeVariant.Icon, content = {}) }
            Box(Modifier.size(20.dp, 4.dp)) { ShadcnProgressBar(value.value, Modifier.size(20.dp, 4.dp), thickness = 4.dp) }
        }
    }

    @Test
    fun skippingUnchangedCompositionDrawsTheSameSkinnedButtonAndBar() {
        val always = Rig(skip = false, full)
        val skipping = Rig(skip = true, full)
        val steps: List<Pair<String, Rig.() -> FrameInput>> = listOf(
            "first" to { still() }, "still" to { still() }, "hover" to { at(12, 12) }, "hover stays" to { at(12, 12) },
            "press" to { at(12, 12, down = true) }, "release" to { at(12, 12) }, "leave" to { at(40, 40) },
            "value half" to { value.value = 0.5f; still() }, "value full" to { value.value = 1f; still() }, "value still" to { still() },
        )
        for ((name, step) in steps) {
            assertEquals(always.frame(always.step()).primitives, skipping.frame(skipping.step()).primitives, "differs at '$name'")
        }
        assertTrue(skipping.host.skippedCompositions > 0, "the skipping host never skipped a pass")
    }

    private fun still() = FrameInput(48, 48, deltaSeconds = 0f)

    private fun at(x: Int, y: Int, down: Boolean = false) = FrameInput(48, 48, pointerX = x, pointerY = y, pointerDown = down, deltaSeconds = 0f)

    private companion object {
        val ART = listOf(0xC03030, 0x30C030, 0x3030C0, 0x808080, 0x202020, 0xE0E040)
    }
}
