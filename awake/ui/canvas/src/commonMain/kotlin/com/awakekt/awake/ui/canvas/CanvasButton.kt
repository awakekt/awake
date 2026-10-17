/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.schema.PropertyRange
import kotlinx.serialization.Serializable

/**
 * How a Button acts as an on-screen action button: when it fires, what a long press and a release
 * do, and whether a cooldown holds it back. A Button with none is a plain button, tapped on release.
 *
 * The button's cooldown and charges are live values on the element, [CanvasElement.cooldown] and
 * [CanvasElement.charges], set from code or read from game state through [CanvasBinding.cooldown]
 * and [CanvasBinding.charges], so the same button serves a skill, a reload or a dash.
 *
 * @property trigger When the button's [CanvasElement.action] fires: on a [CanvasButtonTrigger.Tap],
 * released over the button, or the moment it goes [CanvasButtonTrigger.Down].
 * @property longPressSeconds How long a press is held, in seconds, before it is a long press.
 * @property longPressAction The input action a long press presses. While it is set, a press held
 * that long is not also a tap.
 * @property releaseAction The input action pressed when the button is let go, wherever the thumb is.
 * @property blockWhileCooling Whether the button takes no press while it is cooling down: its
 * [CanvasElement.cooldown] above 0 with no [CanvasElement.charges] left to spend.
 * @property cooldownSeconds A cooldown the button runs itself: each time it fires, its
 * [CanvasElement.cooldown] starts at 1 and runs down to 0 over this many seconds. 0 leaves the
 * cooldown to the game, set from code or [CanvasBinding.cooldown]; keep one of the two.
 * @property aim Drag to aim: while held, a drag away from the button aims, and letting go fires
 * [CanvasElement.action] with that aim, wherever the thumb is, unless it is let go in the aim's
 * cancel zone. An aimed button fires on release whatever its [trigger]. Null does not aim.
 */
@Serializable
data class CanvasButton(
    val trigger: CanvasButtonTrigger = CanvasButtonTrigger.Tap,
    @PropertyRange(min = 0.0, exclusiveMin = true) val longPressSeconds: Float = DEFAULT_LONG_PRESS_SECONDS,
    val longPressAction: String = "",
    val releaseAction: String = "",
    val blockWhileCooling: Boolean = true,
    @PropertyRange(min = 0.0) val cooldownSeconds: Float = 0f,
    val aim: CanvasAim? = null,
) {
    /** What is wrong with these settings, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        if (longPressSeconds <= 0f) add("longPressSeconds must be above 0")
        if (cooldownSeconds < 0f) add("cooldownSeconds must not be negative")
        aim?.problems()?.forEach { add("aim.$it") }
    }

    /** Defaults. */
    companion object {
        /** A Button's settings when it has none. */
        val Default = CanvasButton()

        /** How long a long press takes by default, in seconds: the common platform default. */
        const val DEFAULT_LONG_PRESS_SECONDS = 0.5f
    }
}

/** When an action button's [CanvasElement.action] fires. */
@Serializable
enum class CanvasButtonTrigger {
    /** When it is released over the button, as a plain Button is tapped: a thumb slid off cancels. */
    Tap,

    /** The moment the button goes down, for a jump or a shot that cannot wait for the release. */
    Down,
}
