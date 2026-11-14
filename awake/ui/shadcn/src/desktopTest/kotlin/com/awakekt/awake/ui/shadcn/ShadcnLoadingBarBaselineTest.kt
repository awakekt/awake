/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.layout.Column
import com.awakekt.awake.compose.foundation.layout.padding
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.testing.assertMatchesBaseline
import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.ui.shadcn.components.ShadcnLoadingBar
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test

/** Pins how [ShadcnLoadingBar] looks while loading, while waiting, and after a failure. */
class ShadcnLoadingBarBaselineTest {
    @Test
    fun aMeasuredLoad() = baseline("loading-bar") {
        ShadcnLoadingBar("Loading the harbor", fraction = 0.6f)
    }

    @Test
    fun aLoadWaitingForSignIn() = baseline("loading-bar-waiting") {
        ShadcnLoadingBar("Waiting for sign-in", fraction = null)
    }

    @Test
    fun aFailedLoadWithARetry() = baseline("loading-bar-failed") {
        ShadcnLoadingBar("Loading the harbor", fraction = 0.6f, error = "The connection was lost.", retryLabel = "Retry", onRetry = {})
    }

    private fun baseline(name: String, content: context(Composer) () -> Unit) {
        composeFrame(WIDTH, HEIGHT) {
            provideShadcnTheme(theme) { Column(Modifier.padding(all = 16.dp)) { content() } }
        }.assertMatchesBaseline(name, width = WIDTH, height = HEIGHT, background = theme.palette.background, font = UiFonts.default())
    }

    private val theme = ShadcnThemeValues(ShadcnTheme)

    private companion object {
        const val WIDTH = 320
        const val HEIGHT = 120
    }
}
