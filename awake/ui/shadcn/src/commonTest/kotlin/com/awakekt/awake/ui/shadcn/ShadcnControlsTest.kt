/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.testing.composeFrame
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.heroicons.icon.HeroIcons
import com.awakekt.awake.ui.shadcn.components.ShadcnButton
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonSizeVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnButtonVariant
import com.awakekt.awake.ui.shadcn.components.ShadcnCheckbox
import com.awakekt.awake.ui.shadcn.components.ShadcnSwitch
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadcnControlsTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    // -- button ---------------------------------------------------------------------------------

    private fun button(
        variant: ShadcnButtonVariant = ShadcnButtonVariant.Default,
        size: ShadcnButtonSizeVariant = ShadcnButtonSizeVariant.Default,
        enabled: Boolean = true,
    ) = composeFrame(300, 80) {
        provideShadcnTheme(theme) {
            ShadcnButton("Save", variant = variant, size = size, enabled = enabled)
        }
    }

    @Test
    fun everyButtonVariantDrawsItsLabel() {
        ShadcnButtonVariant.entries.forEach { variant ->
            assertTrue(
                button(variant).primitivesOf<DrawCommand.Glyph>().isNotEmpty(),
                "$variant drew no label",
            )
        }
    }

    @Test
    fun theDefaultButtonIsThirtySixTall() {
        // shadcn's `h-9`. Worth pinning: a Material-flavoured 40dp once propagated up from a
        // ui-headless fallback, and awake-ui-authoring still cites that as live -- it is not.
        val quad = button().primitivesOf<DrawCommand.RoundedQuad>().first()

        assertEquals(36f, quad.h, 0.5f, "the default button is not h-9")
    }

    @Test
    fun aLeadingIconUsesTheSourceGapAndCompactPadding() {
        val frame = composeFrame(300, 80) {
            provideShadcnTheme(theme) {
                ShadcnButton(
                    "Save scene",
                    variant = ShadcnButtonVariant.Outline,
                    leadingIcon = HeroIcons.Solid20Mini.arrowDownTray,
                )
            }
        }
        val quad = frame.primitivesOf<DrawCommand.RoundedQuad>().first()

        // The pinned source measures this exact fixture at 121px: a 16dp icon, gap-2, and
        // `has-[>svg]:px-3`, with the outline border inside the 36dp-high box.
        assertEquals(121f, quad.w, 0.5f, "the icon button did not use shadcn's compact inset")
    }

    @Test
    fun everySizeIsItsOwnHeightAndTheIconSizesAreSquare() {
        ShadcnButtonSizeVariant.entries.forEach { size ->
            val quad = button(size = size).primitivesOf<DrawCommand.RoundedQuad>().first()
            assertEquals(size.height.value, quad.h, 0.5f, "$size drew the wrong height")
            if (size.square) {
                assertEquals(quad.h, quad.w, 0.5f, "$size is not square")
            }
        }
    }

    @Test
    fun anIconSizedButtonCentresItsContent() {
        // `items-center justify-center`. Only the fixed-square sizes expose this: a text size hugs
        // its label, so a top-left label looks identical to a centred one.
        val frame = button(size = ShadcnButtonSizeVariant.Icon)
        val box = frame.primitivesOf<DrawCommand.RoundedQuad>().first()
        val glyphs = frame.primitivesOf<DrawCommand.Glyph>()
        val left = glyphs.minOf { it.x }
        val right = glyphs.maxOf { it.x + it.w }

        val leading = left - box.x
        val trailing = (box.x + box.w) - right
        // Compared on glyph *ink*, which is narrower than the advance the layout centres, so the
        // two sides differ by the trailing bearing. Uncentred would be leading == 0.
        assertTrue(leading > 1f, "the label starts flush at the box edge, so it is not centred")
        assertEquals(leading, trailing, 3f, "the label is not centred: $leading vs $trailing")
    }

    @Test
    fun destructiveTakesWhiteNotAThemeToken() {
        // The fourth place upstream hardcodes white, after the slider thumb and the badge.
        val label = button(ShadcnButtonVariant.Destructive)
            .primitivesOf<DrawCommand.Glyph>().first()

        assertEquals(Color.White, label.color, "destructive's label is not text-white")
    }

    @Test
    fun ghostAndLinkCarryNoFillAtRest() {
        listOf(ShadcnButtonVariant.Ghost, ShadcnButtonVariant.Link).forEach { variant ->
            val filled = button(variant).primitivesOf<DrawCommand.RoundedQuad>()
                .filter { it.color.a > 0f && it.color != theme.palette.background }

            assertTrue(filled.isEmpty(), "$variant painted a fill at rest")
        }
    }

    @Test
    fun aDisabledButtonIsDimmedNotHidden() {
        val on = button().primitivesOf<DrawCommand.Glyph>().first().color.a
        val off = button(enabled = false).primitivesOf<DrawCommand.Glyph>().first().color.a

        assertTrue(off < on, "a disabled button was not dimmed")
        assertTrue(off > 0f, "a disabled button vanished")
    }

    // -- switch ---------------------------------------------------------------------------------

    private fun switchFrame(checked: Boolean) = composeFrame(120, 60) {
        provideShadcnTheme(theme) { ShadcnSwitch(checked) }
    }

    @Test
    fun aSwitchTracksItsStateWithPrimaryAndInput() {
        // `data-[state=checked]:bg-primary` / `data-[state=unchecked]:bg-input` -- not bg-muted,
        // which would read as disabled rather than off.
        assertEquals(
            theme.palette.primary,
            switchFrame(true).primitivesOf<DrawCommand.RoundedQuad>().first().color,
        )
        assertEquals(
            theme.palette.input,
            switchFrame(false).primitivesOf<DrawCommand.RoundedQuad>().first().color,
        )
    }

    @Test
    fun theThumbSlidesRightWhenChecked() {
        fun thumbX(checked: Boolean) =
            switchFrame(checked).primitivesOf<DrawCommand.RoundedQuad>()[1].x

        assertTrue(thumbX(true) > thumbX(false), "the thumb did not travel")
    }

    @Test
    fun theThumbOverhangsTheTrack() {
        // `size-4` on a `h-[1.15rem]` track: 16 on 18.4, so it sits proud by 1.2 a side. That is the
        // design, and a thumb clamped to the track would look like a different control.
        val quads = switchFrame(true).primitivesOf<DrawCommand.RoundedQuad>()
        val track = quads[0]
        val thumb = quads[1]

        assertTrue(thumb.h < track.h, "the thumb is not inside the track's height")
        assertTrue(thumb.h > track.h - 4f, "the thumb shrank well below shadcn's size-4")
    }

    // -- checkbox -------------------------------------------------------------------------------

    private fun checkboxFrame(checked: Boolean) = composeFrame(80, 60) {
        provideShadcnTheme(theme) { ShadcnCheckbox(checked) }
    }

    @Test
    fun aCheckedBoxFillsWithPrimary() {
        assertEquals(
            theme.palette.primary,
            checkboxFrame(true).primitivesOf<DrawCommand.RoundedQuad>().first().color,
        )
    }

    @Test
    fun theCheckMarkComesFromTheIconRegistry() {
        // The recipe this replaces hand-built a check from transcribed coordinates while
        // The source-faithful Lucide check is a generated stroked vector, not hand-authored
        // coordinates. Its primitive proves the recipe retained the registry path and stroke.
        assertTrue(
            checkboxFrame(true).primitivesOf<DrawCommand.Mesh>().isNotEmpty(),
            "the check mark was not drawn as a vector",
        )
        assertTrue(
            checkboxFrame(false).primitivesOf<DrawCommand.Mesh>().isEmpty(),
            "an unchecked box drew a check",
        )
    }

    @Test
    fun anUncheckedBoxIsVisibleAgainstThePage() {
        // It first rendered as nothing: `bg-background` with no border, on a page of the same
        // colour. Upstream carries `border border-input` in both states.
        val quads = checkboxFrame(false).primitivesOf<DrawCommand.RoundedQuad>()

        assertTrue(
            quads.any { it.color == theme.palette.input },
            "an unchecked box drew no border-input edge, so it is invisible on the page",
        )
    }

    @Test
    fun theBoxRadiusIsUpstreamsLiteralFourNotTheThemeStep() {
        // `rounded-[4px]` is an arbitrary value: it does not move with the theme's base radius the
        // way `sm` would, so binding it to the token would drift on any non-default radius.
        val quad = checkboxFrame(true).primitivesOf<DrawCommand.RoundedQuad>().first()

        assertEquals(4f, quad.radius, 0.5f, "the checkbox is not rounded-[4px]")
    }
}
