/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.ui

import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.sample.uishowcase.state.UiShowcaseRuntimeState
import com.awakekt.awake.ui.shadcn.shadcnThemeValues
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertTrue

class ShowcaseCatalogTest {

    @Test
    @Suppress("TooGenericExceptionCaught")
    fun everyShowcasePagePreviewComposesOnTheCommonUiTestHost() {
        val failures = buildList {
            ShowcasePages.forEach { page ->
                try {
                    val frame = composeFrame(page.previewWidth, page.previewHeight) {
                        provideShadcnTheme(shadcnThemeValues()) {
                            ShowcasePagePreview(page, UiShowcaseRuntimeState())
                        }
                    }
                    assertTrue(frame.primitives.isNotEmpty(), "${page.id} drew nothing")
                } catch (failure: Throwable) {
                    add("${page.id}: ${failure::class.simpleName}: ${failure.message}")
                }
            }
        }
        assertTrue(
            failures.isEmpty(),
            "Showcase page preview smoke test failed:\n${failures.joinToString("\n")}",
        )
    }
}
