/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn.components

import io.github.awakelab.awake.compose.foundation.Canvas
import io.github.awakelab.awake.compose.foundation.clickable
import io.github.awakelab.awake.compose.foundation.hoverable
import io.github.awakelab.awake.compose.foundation.interaction.InteractionSource
import io.github.awakelab.awake.compose.foundation.layout.height
import io.github.awakelab.awake.compose.foundation.layout.width
import io.github.awakelab.awake.compose.runtime.Composer
import io.github.awakelab.awake.compose.runtime.remember
import io.github.awakelab.awake.compose.ui.Modifier
import io.github.awakelab.awake.compose.ui.unit.Dp
import io.github.awakelab.awake.compose.ui.unit.dp
import io.github.awakelab.awake.ui.shadcn.theme.shadcnTheme

/**
 * shadcn's switch: a pill track with a circular thumb that slides.
 *
 * Upstream's default track is `h-[1.15rem] w-8` -- **18.4px by 32px**, an arbitrary-value class
 * rather than a scale step, so there is no `Tw` step to name here. The thumb is `size-4` (16px),
 * which is *larger than the track is tall*: it overhangs by 1.2px on each edge, and that overhang is
 * the design rather than a rounding error.
 *
 * Checked is `bg-primary`, unchecked `bg-input` -- not `bg-muted`, which would read as disabled.
 *
 */
context(_: Composer)
fun shadcnSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /**
     * Supplied when a caller needs to drive the visual state -- a state-matrix preview renders the
     * same control hovered, pressed and focused without a pointer ever being there.
     *
     * Compose's own seam: its components take an `interactionSource` for exactly this. Nullable
     * rather than a `remember {}` default because a default argument that allocates a slot would
     * shift the slot table depending on whether a caller passed one.
     */
    interactionSource: InteractionSource? = null,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    val theme = shadcnTheme
    val owned = remember { InteractionSource() }
    val interaction = interactionSource ?: owned
    val next = checked

    Canvas(
        modifier
            .width(TrackWidth)
            .height(TrackHeight)
            .hoverable(interaction, enabled = enabled)
            .clickable(interaction) { if (enabled) onCheckedChange(!checked) },
    ) {
        val alpha = if (enabled) 1f else DISABLED_ALPHA
        withAlpha(alpha) {
            val trackH = height.toFloat()
            drawRoundedRect(
                width = width.toFloat(),
                height = trackH,
                color = if (next) theme.palette.primary else theme.palette.input,
                radius = trackH / 2f,
            )
            // `translate-x-[calc(100%-2px)]` when checked: the thumb travels the track's width less
            // its own, less the 2px upstream holds back so it never sits flush with the edge.
            val thumb = ThumbSize.value * density
            val travel = width - thumb - ThumbInset.value * density
            val x = if (next) travel else 0f
            drawRoundedRect(
                x = x,
                y = (trackH - thumb) / 2f,
                width = thumb,
                height = thumb,
                color = theme.palette.background,
                radius = thumb / 2f,
            )
        }
    }
}

/** `h-[1.15rem]` -- an arbitrary value upstream, so no Tailwind step names it. */
private val TrackHeight: Dp = 18.4f.dp

/** `w-8`. */
private val TrackWidth: Dp = 32.dp

/** `size-4`, deliberately taller than the track. */
private val ThumbSize: Dp = 16.dp

/** The `-2px` in upstream's `calc(100%-2px)`. */
private val ThumbInset: Dp = 2.dp

/** `disabled:opacity-50`. */
private const val DISABLED_ALPHA = 0.5f
