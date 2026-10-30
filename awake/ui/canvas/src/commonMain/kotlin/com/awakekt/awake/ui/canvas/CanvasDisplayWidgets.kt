/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.animation.TrailingValue

/**
 * What a canvas element is beyond a box: a bar, a button, a field, a stick. Each widget component
 * owns only its own data and the settings value it is set up by; an element has at most one, and
 * one with none is a Text when it has a [CanvasText] and a Panel when it does not.
 *
 * The widgets that only show something are here; the controls a player works are in
 * CanvasControlWidgets.kt.
 */
sealed interface CanvasWidget {
    /** The element kind this widget makes its element. */
    val kind: CanvasElementKind
}

/**
 * A progress bar filled to [value]: across from the left, or as its [settings] say, up, down,
 * around as a ring, segmented, trailed or labelled.
 */
class CanvasBarWidget : CanvasWidget {
    override val kind: CanvasElementKind get() = CanvasElementKind.Bar

    /** The fill, from 0 to 1; a [CanvasBinding.value] field takes its place while it reads. */
    var value: Float = 1f

    /** How the bar draws: its direction, trail, segments, ticks and label; null fills across from the left. */
    var settings: CanvasBar? = null

    /** The bar's trailing fill, advanced each frame it is drawn. */
    internal val trail = TrailingValue(value)

    /** Whether [trail] has been put on the value it first drew at, so a bar does not trail in from full. */
    internal var trailStarted = false
}

/**
 * A radial sweep: a pie or ring filled to [value] of a full turn, as its [settings] say, for a
 * cooldown, a timer or a charge.
 */
class CanvasSweepWidget : CanvasWidget {
    override val kind: CanvasElementKind get() = CanvasElementKind.Sweep

    /** The share of a full turn filled, from 0 to 1; a [CanvasBinding.value] field takes its place while it reads. */
    var value: Float = 1f

    /** How the sweep turns: its start, direction, mask, ring and track; null is [CanvasSweep.Default]. */
    var settings: CanvasSweep? = null
}

/** What a camera sees: the scene from the camera on the node [camera] names, under the element's children. */
class CanvasViewWidget : CanvasWidget {
    override val kind: CanvasElementKind get() = CanvasElementKind.View

    /** The name of a node with a camera, whose picture the view shows; empty shows none. */
    var camera: String = ""

    /** How the picture clears and is clipped; null keeps an opaque, styled box. */
    var settings: CanvasView? = null
}

/** A picture: its element's [CanvasAppearance.style] image. A [CanvasInput.pressable] one takes taps. */
class CanvasImageWidget : CanvasWidget {
    override val kind: CanvasElementKind get() = CanvasElementKind.Image
}
