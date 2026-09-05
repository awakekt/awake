/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.compose.foundation.layout.size
import io.github.awakelab.awake.compose.testing.composeFrame
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.core.graphics2d.DrawCommand
import io.github.awakelab.awake.ui.shadcn.components.ShadcnKbd
import io.github.awakelab.awake.ui.shadcn.components.ShadcnLabel
import io.github.awakelab.awake.ui.shadcn.components.ShadcnProgress
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSkeleton
import io.github.awakelab.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShadcnStatusComponentsTest {

    private val theme = ShadcnThemeValues(ShadcnTheme)

    // -- skeleton -------------------------------------------------------------------------------

    @Test
    fun aSkeletonUsesAccentNotMuted() {
        // The drift the re-vendor exposed: the hand-copied reference said `bg-muted`, so every
        // skeleton parity number was measured against the wrong background. Upstream is `bg-accent`.
        val quad = composeFrame(120, 40) {
            provideShadcnTheme(theme) { ShadcnSkeleton(Modifier.size(100.dp, 20.dp)) }
        }.primitivesOf<DrawCommand.RoundedQuad>().first()

        assertEquals(theme.palette.accent.r, quad.color.r, 0.001f, "the skeleton is not bg-accent")
        assertEquals(theme.palette.accent.g, quad.color.g, 0.001f)
        assertEquals(theme.palette.accent.b, quad.color.b, 0.001f)
    }

    @Test
    fun aSkeletonPulsesBetweenFullAndHalfOpacity() {
        // Tailwind's `animate-pulse` bottoms out at 50%, not at zero -- a skeleton never disappears.
        val quad = composeFrame(120, 40) {
            provideShadcnTheme(theme) { ShadcnSkeleton(Modifier.size(100.dp, 20.dp)) }
        }.primitivesOf<DrawCommand.RoundedQuad>().first()

        assertTrue(quad.color.a in 0.5f..1.0f, "opacity ${quad.color.a} left the pulse range")
    }

    // -- progress -------------------------------------------------------------------------------

    @Test
    fun theProgressTrackIsPrimaryAtTwentyPercent() {
        // `bg-primary/20`, not bg-muted: the track tints with the theme rather than reading grey.
        val track = composeFrame(200, 40) {
            provideShadcnTheme(theme) { ShadcnProgress(0.5f) }
        }.primitivesOf<DrawCommand.RoundedQuad>().first()

        assertEquals(0.2f, track.color.a, 0.01f, "the track is not bg-primary/20")
    }

    @Test
    fun theIndicatorIsTheFractionOfTheTrack() {
        fun indicatorWidth(progress: Float): Float {
            val quads = composeFrame(200, 40) {
                provideShadcnTheme(theme) { ShadcnProgress(progress) }
            }.primitivesOf<DrawCommand.RoundedQuad>()
            return if (quads.size > 1) quads[1].w else 0f
        }

        assertEquals(0f, indicatorWidth(0f), 0.5f, "an empty bar drew an indicator")
        assertEquals(indicatorWidth(1f) / 2f, indicatorWidth(0.5f), 1f, "half did not draw half")
    }

    @Test
    fun progressClampsRatherThanOverdrawing() {
        val quads = composeFrame(200, 40) {
            provideShadcnTheme(theme) { ShadcnProgress(5f) }
        }.primitivesOf<DrawCommand.RoundedQuad>()

        assertEquals(quads[0].w, quads[1].w, 0.5f, "an out-of-range value drew past the track")
    }

    // -- kbd ------------------------------------------------------------------------------------

    @Test
    fun theKbdRadiusIsTheSmStepNotXs() {
        // The live parity failure this port fixes: kbdStatesStyleMatchesShadcn reported 4 against
        // upstream's 6, because the old recipe used the `xs` step where upstream says `rounded-sm`.
        val quad = composeFrame(120, 40) {
            provideShadcnTheme(theme) { ShadcnKbd("K") }
        }.primitivesOf<DrawCommand.RoundedQuad>().first()

        assertEquals(theme.radii.sm.value, quad.radius, 0.5f, "kbd is not rounded-sm")
        assertTrue(theme.radii.sm.value > theme.radii.xs.value, "the scale collapsed sm into xs")
    }

    @Test
    fun aSingleCharacterKeyStaysSquare() {
        // `h-5 min-w-5` -- without the min width a one-glyph key collapses to the glyph.
        val quad = composeFrame(120, 40) {
            provideShadcnTheme(theme) { ShadcnKbd("K") }
        }.primitivesOf<DrawCommand.RoundedQuad>().first()

        assertTrue(quad.w >= 20f - 0.5f, "the key collapsed to ${quad.w}, under its min-w-5")
    }

    // -- label ----------------------------------------------------------------------------------

    @Test
    fun aDisabledLabelIsDimmedNotHidden() {
        // `peer-disabled:opacity-50` -- the label reacts to its control's state, and stays readable.
        fun alpha(enabled: Boolean) = composeFrame(200, 40) {
            provideShadcnTheme(theme) { ShadcnLabel("Email", enabled = enabled) }
        }.primitivesOf<DrawCommand.Glyph>().first().color.a

        assertTrue(alpha(false) < alpha(true), "a disabled label was not dimmed")
        assertTrue(alpha(false) > 0f, "a disabled label vanished instead of dimming")
    }
}
