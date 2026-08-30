/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.foundation.layout.ColumnMeasurePolicy
import io.github.awakelab.awake.compose.runtime.current
import io.github.awakelab.awake.compose.ui.layout.composeInto
import io.github.awakelab.awake.compose.ui.node.LayoutNode
import io.github.awakelab.awake.ui.shadcn.theme.LocalShadcnTheme
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The compose-side door onto the same theme the `UiLocal` one serves, which is what lets a recipe
 * shed its `ui-core` import one file at a time.
 */
class ShadcnComposeThemeTest {

    private val values = ShadcnThemeValues(ShadcnTheme)

    @Test
    fun aRecipeInsideTheProviderReadsTheValuesItWasGiven() {
        val root = LayoutNode(ColumnMeasurePolicy())
        var seen: ShadcnThemeValues? = null

        composeInto(root) {
            provideShadcnTheme(values) { seen = shadcnTheme }
        }

        assertSame(values, seen, "the provided theme did not reach the content")
    }

    @Test
    fun outsideTheProviderTheLocalIsNullRatherThanADefaultTheme() {
        // A neutral fallback would render *something*, so a missing provider would show up as
        // wrong branding somewhere far from the mistake instead of at it.
        val root = LayoutNode(ColumnMeasurePolicy())
        var seen: ShadcnThemeValues? = values

        composeInto(root) { seen = LocalShadcnTheme.current }

        assertNull(seen, "a theme appeared with no provider in scope")
    }

    @Test
    fun readingTheThemeWithNoProviderNamesTheFix() {
        val root = LayoutNode(ColumnMeasurePolicy())

        val failure = assertFailsWith<IllegalArgumentException> {
            composeInto(root) { shadcnTheme }
        }

        assertTrue(
            failure.message?.contains("provideShadcnTheme { }") == true,
            "the failure did not say what to wrap the content in: ${failure.message}",
        )
    }

    @Test
    fun aNestedProviderWins() {
        val inner = ShadcnThemeValues(ShadcnTheme)
        val root = LayoutNode(ColumnMeasurePolicy())
        var seen: ShadcnThemeValues? = null

        composeInto(root) {
            provideShadcnTheme(values) {
                provideShadcnTheme(inner) { seen = shadcnTheme }
            }
        }

        assertSame(inner, seen, "the inner provider did not override the outer one")
    }

    @Test
    fun theOuterThemeIsRestoredAfterANestedProviderCloses() {
        val inner = ShadcnThemeValues(ShadcnTheme)
        val root = LayoutNode(ColumnMeasurePolicy())
        var after: ShadcnThemeValues? = null

        composeInto(root) {
            provideShadcnTheme(values) {
                provideShadcnTheme(inner) { shadcnTheme }
                after = shadcnTheme
            }
        }

        assertSame(values, after, "a nested provider leaked past its own content")
    }
}
