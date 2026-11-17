/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.BoxWithConstraints
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.ui.layout.SubcomposeLayout
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.EdgeInsets
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.LocalWindowInsets
import com.awakekt.awake.compose.ui.platform.WindowInsets
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A subcomposition reads its parent's composition locals as they are now, not as they were when it
 * was created (#1774).
 *
 * `SubcomposeLayout`, and so `BoxWithConstraints`, gave each slot its parent's locals once, when the
 * slot was made, so a slot kept them for its whole life: the soft keyboard's inset stayed 0 inside a
 * `BoxWithConstraints` after the keyboard opened, as did any local that changes at run time.
 */
class SubcomposeLocalsTest {
    private val provided = mutableStateOf(1)
    private val seen = HashMap<String, Int>()
    private val ime = HashMap<String, Float>()

    context(_: Composer)
    private fun Read(where: String) {
        seen[where] = LocalNumber.current
        ime[where] = LocalWindowInsets.current.ime.bottom
    }

    context(_: Composer)
    private fun Content() {
        CompositionLocalProvider(LocalNumber, provided.value) {
            Read("outside")
            BoxWithConstraints { Read("BoxWithConstraints") }
            SubcomposeLayout { _ ->
                subcompose(Unit) { Read("SubcomposeLayout") }
                layout(0, 0) {}
            }
        }
    }

    private fun host(skip: Boolean) = ComposeHost().apply {
        skipUnchangedComposition = skip
        skipUnchangedMeasure = skip
        retainUnchangedDraw = skip
    }

    private fun ComposeHost.frame() = frame(FrameInput(200, 100)) { Content() }

    @Test
    fun aChangedLocalReachesEverySubcomposition() {
        for (skip in listOf(false, true)) {
            provided.value = 1
            val host = host(skip)
            repeat(2) { host.frame() }
            provided.value = 2
            host.frame()
            assertEquals(mapOf("outside" to 2, "BoxWithConstraints" to 2, "SubcomposeLayout" to 2), seen, "skipping $skip")
            host.frame()
            assertEquals(mapOf("outside" to 2, "BoxWithConstraints" to 2, "SubcomposeLayout" to 2), seen, "skipping $skip, a frame later")
            host.dispose()
        }
    }

    @Test
    fun theKeyboardInsetReachesEverySubcomposition() {
        for (skip in listOf(false, true)) {
            val host = host(skip)
            repeat(2) { host.frame() }
            host.windowInsets = WindowInsets(ime = EdgeInsets(bottom = KEYBOARD))
            host.frame()
            assertEquals(mapOf("outside" to KEYBOARD, "BoxWithConstraints" to KEYBOARD, "SubcomposeLayout" to KEYBOARD), ime, "keyboard open, skipping $skip")
            host.windowInsets = WindowInsets.None
            host.frame()
            assertEquals(mapOf("outside" to 0f, "BoxWithConstraints" to 0f, "SubcomposeLayout" to 0f), ime, "keyboard closed, skipping $skip")
            host.dispose()
        }
    }

    private companion object {
        val LocalNumber = compositionLocalOf { 0 }
        const val KEYBOARD = 300f
    }
}
