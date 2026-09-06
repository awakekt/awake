/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.foundation.animation.animateFloat
import com.awakekt.awake.compose.runtime.Composer

/**
 * How opaque an overlay should be, and therefore whether it is still on screen at all.
 *
 * An overlay that unmounts the instant its flag flips has nowhere to fade out *from* -- the caller's
 * `if (!visible) return` removes the subtree before anything can animate. This keeps the subtree
 * alive while the value falls, so the component can render itself at a decreasing alpha and stop
 * only once it has actually gone.
 *
 * Both directions, for an overlay that toggles. `animateFloat` returns its target on the *first*
 * pass only, so a component composed with `visible` already true is opaque immediately -- but one
 * that starts closed and opens has a 0 to animate from, which is every overlay driven by a flag.
 * That asymmetry is worth knowing when reading a capture: a fixture that renders an overlay open on
 * frame one sees no fade, and a fixture that opens it sees one.
 */
context(_: Composer)
internal fun rememberOverlayAlpha(visible: Boolean): Float =
    animateFloat(if (visible) 1f else 0f, OVERLAY_FADE_SECONDS)

/** Below this an overlay is invisible, so it stops being rendered and its layer goes away. */
internal fun isPresent(visible: Boolean, alpha: Float): Boolean = visible || alpha > OVERLAY_GONE_ALPHA

/** shadcn's `data-[state=closed]:duration-150` on its overlays. */
private const val OVERLAY_FADE_SECONDS = 0.15f

/** One step below what a byte of alpha can represent, so nothing lingers invisibly. */
private const val OVERLAY_GONE_ALPHA = 1f / 255f
