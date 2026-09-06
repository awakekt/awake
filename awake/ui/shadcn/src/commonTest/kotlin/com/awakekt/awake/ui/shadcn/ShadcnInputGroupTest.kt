/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.text.rememberTextFieldState
import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.ui.shadcn.components.ShadcnInputGroup
import com.awakekt.awake.ui.shadcn.components.ShadcnText
import com.awakekt.awake.ui.shadcn.components.ShadcnTextVariant
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadcnInputGroupTest {
    @Test
    fun inputGroupRendersUnifiedContainerWithPrefixAndSuffix() {
        val frame = composeFrame(320, 50) {
            provideShadcnTheme(shadcnThemeValues(dark = true)) {
                val state = rememberTextFieldState("example.com")
                ShadcnInputGroup(
                    state = state,
                    prefix = { ShadcnText("https://", variant = ShadcnTextVariant.Small) },
                    suffix = { ShadcnText("/profile", variant = ShadcnTextVariant.Small) },
                )
            }
        }

        // Must render without crashing and include draw primitives
        assertTrue(frame.primitives.isNotEmpty())
    }
}
