/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Arrangement
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.Text
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.compose.testing.composeTestSession
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The samples on the "shadcn components" guide and the Button reference, each run as described. */
class ShadcnDocsSampleTest {

    @Test
    fun aThemedButtonTakesAClick() {
        var saves = 0
        val session = composeTestSession(width = 320, height = 120) {
            // --8<-- [start:themed-button]
            provideShadcnTheme(shadcnThemeValues(dark = false)) {
                ShadcnButton("Save", modifier = Modifier.testTag("save"), onClick = { saves++ })
            }
            // --8<-- [end:themed-button]
        }
        session.frame()

        session.click("save")

        assertEquals(1, saves)
    }

    @Test
    fun aButtonWithNoThemeInScopeFailsAndNamesTheFix() {
        val failure = assertFailsWith<IllegalArgumentException> {
            composeFrame { ShadcnButton("Save") }
        }

        assertTrue(failure.message.orEmpty().contains("provideShadcnTheme"), "${failure.message}")
    }

    @Test
    fun aConfiguredThemeCarriesItsPresetAndAccent() {
        // --8<-- [start:custom-theme]
        val theme = shadcnThemeValues(
            preset = ShadcnStylePreset.Nova,
            baseColor = ShadcnBaseColor.Zinc,
            accent = ShadcnAccent.Blue,
            dark = true,
        )
        // --8<-- [end:custom-theme]

        assertEquals(ShadcnStylePreset.Nova, theme.config.preset)
        assertEquals(5f, theme.radii.lg.value, "Nova's base radius is 5 dp")
    }

    @Test
    fun aComponentReadsTokensFromTheThemeInScope() {
        val theme = shadcnThemeValues(dark = false)
        val frame = composeFrame(120, 60) { provideShadcnTheme(theme) { StatusDot() } }

        val painted = frame.meshColors() + frame.primitivesOf<UiDrawPrimitive.RoundedQuad>().map { it.color }
        assertTrue(theme.palette.primary in painted, "the dot did not paint the theme's primary colour: $painted")
    }

    @Test
    fun theButtonOverloadsAllCompose() {
        val frame = composeFrame(600, 80) { provideShadcnTheme(shadcnThemeValues(dark = false)) { ButtonOverloads() } }

        assertEquals(3, frame.flatSemantics().count { it.testTag?.startsWith("overload-") == true })
    }
}

// --8<-- [start:read-tokens]
context(_: Composer)
fun StatusDot() {
    val theme = shadcnTheme
    Box(Modifier.size(12.dp).background(theme.palette.primary, theme.radii.full))
}
// --8<-- [end:read-tokens]

context(_: Composer)
private fun ButtonOverloads() {
    Row(horizontalArrangement = Arrangement.spacedByHorizontal(8.dp)) {
        // --8<-- [start:button-overloads]
        ShadcnButton("Export", leadingIcon = HeroIcons.Solid20Mini.arrowDownTray, modifier = Modifier.testTag("overload-icon"))
        ShadcnButton(variant = ShadcnButtonVariant.Outline, size = ShadcnButtonSizeVariant.Sm, modifier = Modifier.testTag("overload-slot")) {
            Text("Cancel")
        }
        ShadcnButton("Delete", variant = ShadcnButtonVariant.Destructive, enabled = false, modifier = Modifier.testTag("overload-disabled"))
        // --8<-- [end:button-overloads]
    }
}
