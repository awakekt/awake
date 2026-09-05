/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.testing.composeFrame
import io.github.awakelab.awake.core.graphics2d.DrawCommand
import io.github.awakelab.awake.ui.shadcn.components.ShadcnAlert
import io.github.awakelab.awake.ui.shadcn.components.ShadcnAlertVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnAvatar
import io.github.awakelab.awake.ui.shadcn.components.ShadcnAvatarSizeVariant
import io.github.awakelab.awake.ui.shadcn.components.ShadcnTooltip
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadcnSurfacesTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    // -- alert ----------------------------------------------------------------------------------

    private fun alert(variant: ShadcnAlertVariant) = composeFrame(300, 120) {
        provideShadcnTheme(theme) {
            ShadcnAlert("Heads up", description = "Something happened.", variant = variant)
        }
    }

    @Test
    fun aDestructiveAlertKeepsTheCardFill() {
        // `bg-card text-destructive` -- only the type turns red. A red box is the obvious wrong
        // guess and looks deliberate once shipped.
        val default =
            alert(ShadcnAlertVariant.Default).primitivesOf<DrawCommand.RoundedQuad>().first()
        val destructive = alert(ShadcnAlertVariant.Destructive)
            .primitivesOf<DrawCommand.RoundedQuad>().first()

        assertEquals(theme.palette.card, default.color)
        assertEquals(theme.palette.card, destructive.color, "a destructive alert changed its fill")
    }

    @Test
    fun aDestructiveAlertTurnsItsTitleRed() {
        val title = alert(ShadcnAlertVariant.Destructive).primitivesOf<DrawCommand.Glyph>().first()

        assertEquals(theme.palette.destructive.r, title.color.r, 0.001f)
        assertEquals(theme.palette.destructive.g, title.color.g, 0.001f)
    }

    @Test
    fun theDescriptionIsSofterThanTheTitle() {
        // `text-destructive/90` on the description, so the two read as a hierarchy rather than one
        // block of red.
        val glyphs = alert(ShadcnAlertVariant.Destructive).primitivesOf<DrawCommand.Glyph>()
        val titleAlpha = glyphs.first().color.a
        val descriptionAlpha = glyphs.last().color.a

        assertTrue(descriptionAlpha < titleAlpha, "the description is not softer than the title")
    }

    // -- avatar ---------------------------------------------------------------------------------

    @Test
    fun everyAvatarSizeIsItsShadcnSize() {
        // size-6 / size-8 / size-10. awake-ui-authoring still cites this component as carrying a
        // 40dp default that shadcn does not have; it does not, and has not for a while.
        ShadcnAvatarSizeVariant.entries.forEach { size ->
            val quad = composeFrame(120, 80) {
                provideShadcnTheme(theme) { ShadcnAvatar("AB", size = size) }
            }.primitivesOf<DrawCommand.RoundedQuad>().first()

            assertEquals(size.size.value, quad.w, 0.5f, "$size is the wrong width")
            assertEquals(quad.w, quad.h, 0.5f, "$size is not square")
        }
    }

    @Test
    fun anAvatarIsAFullCircle() {
        val quad = composeFrame(120, 80) {
            provideShadcnTheme(theme) { ShadcnAvatar("AB") }
        }.primitivesOf<DrawCommand.RoundedQuad>().first()

        assertTrue(quad.radius >= quad.h / 2f - 0.5f, "rounded-full did not reach a circle")
    }

    @Test
    fun theInitialsAreCentred() {
        val frame = composeFrame(120, 80) {
            provideShadcnTheme(theme) { ShadcnAvatar("AB") }
        }
        val circle = frame.primitivesOf<DrawCommand.RoundedQuad>().first()
        val glyphs = frame.primitivesOf<DrawCommand.Glyph>()

        assertTrue(
            glyphs.minOf { it.x } > circle.x,
            "the initials start flush at the circle's edge",
        )
        assertTrue(glyphs.maxOf { it.x + it.w } < circle.x + circle.w, "the initials overflow")
    }

    // -- tooltip --------------------------------------------------------------------------------

    @Test
    fun aTooltipIsInverted() {
        // `bg-foreground text-background`. Reaching for `popover` -- what "a small floating panel"
        // suggests -- gives a tooltip that looks like a menu.
        val frame = composeFrame(200, 60) {
            provideShadcnTheme(theme) { ShadcnTooltip("Copy to clipboard") }
        }

        assertEquals(
            theme.palette.foreground,
            frame.primitivesOf<DrawCommand.RoundedQuad>().first().color,
            "the tooltip is not filled with the foreground token",
        )
        assertEquals(
            theme.palette.background,
            frame.primitivesOf<DrawCommand.Glyph>().first().color,
            "the tooltip's type is not the background token",
        )
    }

    @Test
    fun aTooltipDoesNotReuseThePopoverFill() {
        // The specific wrong answer this guards: popover is the token a menu uses, and a tooltip
        // filled with it is indistinguishable from one until you see them side by side.
        val fill = composeFrame(200, 60) {
            provideShadcnTheme(theme) { ShadcnTooltip("Copy") }
        }.primitivesOf<DrawCommand.RoundedQuad>().first().color

        assertTrue(fill != theme.palette.popover, "the tooltip took the popover fill")
    }
}
