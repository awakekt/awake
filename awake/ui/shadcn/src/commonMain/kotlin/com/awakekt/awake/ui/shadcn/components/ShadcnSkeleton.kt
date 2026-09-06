/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.animation.rememberLoopingPhase
import com.awakekt.awake.compose.foundation.background
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.ui.shadcn.theme.shadcnTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * A loading placeholder: `animate-pulse rounded-md bg-accent`.
 *
 * **`bg-accent`, not `bg-muted`** -- and that difference is a parity failure this port fixes rather
 * than inherits. The vendored reference app had a hand-copied `skeleton.tsx` that said `bg-muted`,
 * so every skeleton parity number was measured against the wrong background until the components
 * were re-vendored from the pin. Upstream settles it.
 *
 * `animate-pulse` is Tailwind's own keyframe: opacity 1 -> .5 -> 1 over 2s on an ease-in-out curve.
 * A sine gives that shape exactly, with no keyframe machinery, and `rememberLoopingPhase` is the
 * frame-clock subscription the engine already has.
 */
context(_: Composer)
fun ShadcnSkeleton(modifier: Modifier = Modifier) {
    val theme = shadcnTheme
    val phase = rememberLoopingPhase(PULSE_SECONDS)
    // sin over a full turn is +/-1; mapped to 1.0 .. 0.5 it is Tailwind's own opacity curve.
    val opacity = PULSE_MAX_ALPHA - (PULSE_MAX_ALPHA - PULSE_MIN_ALPHA) *
        ((1f - sin(phase * 2f * PI.toFloat() + PI.toFloat() / 2f)) / 2f)
    Spacer(modifier.background(theme.palette.accent.withAlpha(opacity), theme.radii.md))
}

/** Tailwind's `animate-pulse` is a 2s cycle. */
private const val PULSE_SECONDS = 2f

private const val PULSE_MAX_ALPHA = 1f

/** Tailwind's pulse bottoms out at 50%, not at zero. */
private const val PULSE_MIN_ALPHA = 0.5f
