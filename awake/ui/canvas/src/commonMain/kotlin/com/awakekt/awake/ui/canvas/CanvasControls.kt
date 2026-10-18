/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.input.InputDevice
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * What every on-screen control on a canvas shares: when its touch controls show, how small a control
 * may be to a thumb, how they all look, and the player's own layout of them.
 *
 * @property touchVisibility When [CanvasElement.touchOnly] elements show: as the host says, by the
 * device last used, always, or never.
 * @property minTouchSize The smallest a stick, action button or look area is to a thumb, in dp: one
 * drawn smaller still takes a touch up to this size around its centre. 44 is the size platform
 * guidelines give a touch target; 0 takes touches only on what is drawn.
 * @property theme How every control looks, slot by slot, from a preset and tokens; null draws each
 * in its plain look from its own colours, unless it has its own [CanvasElement.skin]. Set a new one
 * to restyle every control at once.
 * @property playerLayout Where the player moved each control, how big and how opaque, saved as data;
 * set from a settings screen and read back to save.
 */
data class CanvasControls(
    val touchVisibility: CanvasTouchVisibility = CanvasTouchVisibility.Host,
    val minTouchSize: Float = DEFAULT_MIN_TOUCH_SIZE,
    var theme: CanvasControlTheme? = null,
    var playerLayout: CanvasPlayerLayout = CanvasPlayerLayout(),
) {
    /** What is wrong with these settings, as messages naming the field; empty when nothing is. */
    fun problems(): List<String> = buildList {
        if (minTouchSize < 0f || !minTouchSize.isFinite()) add("minTouchSize must not be negative")
        theme?.problems()?.forEach { add("theme.$it") }
        playerLayout.problems().forEach { add("playerLayout.$it") }
    }

    /** Defaults. */
    companion object {
        /** A canvas's controls when it names none. */
        val Default = CanvasControls()

        /** The touch target size platform guidelines give, in dp. */
        const val DEFAULT_MIN_TOUCH_SIZE = 44f
    }
}

/** When a canvas's touch-only elements show. */
@Serializable
enum class CanvasTouchVisibility {
    /** As the host says: shown on a touch screen, hidden elsewhere. */
    Host,

    /**
     * By the device the player last used: shown once a finger touches the screen, hidden once a
     * controller, a key or the mouse is used, switching live. Until any is used, as the host says.
     */
    Auto,

    /** Always shown, as while testing touch controls on a desktop. */
    Always,

    /** Never shown, as for a game that draws no touch controls. */
    Never,

    ;

    /** Whether touch-only elements show when the host says [host] and the player last used [last]. */
    fun shows(host: Boolean, last: InputDevice?): Boolean = when (this) {
        Host -> host
        Always -> true
        Never -> false
        Auto -> if (last == null) host else last == InputDevice.Touch
    }
}

/**
 * How many pixels past [element]'s box a touch still lands on it, at [density], so its smaller side
 * reaches [minTouchSize] dp: none for an element already that big.
 */
internal fun touchMarginPx(element: CanvasElement, minTouchSize: Float, density: Float): Int =
    ((minTouchSize - minOf(element.width, element.height)).coerceAtLeast(0f) / 2f * density).roundToInt()

/** How many pixels past [element]'s box a touch still lands on it at [density], by the tree's [CanvasControls.minTouchSize]. */
internal fun CanvasTree.touchMarginPx(element: CanvasElement, density: Float): Int = touchMarginPx(element, controls.minTouchSize, density)

/**
 * The state [this] control is drawn in: its [CanvasElement.previewState] when an editor sets one, or
 * the first that holds of disabled, cancelling, aiming, held, pressed, cooling down, ready and idle.
 */
internal fun CanvasElement.controlState(): CanvasControlState = previewState ?: when {
    kind == CanvasElementKind.Button && !enabled -> CanvasControlState.Disabled
    aimCancelling -> CanvasControlState.Cancelling
    aiming -> CanvasControlState.Aiming
    buttonPress.longPressed && isHeld -> CanvasControlState.Held
    isHeld || lookState.count > 0 -> CanvasControlState.Pressed
    coolingDown -> CanvasControlState.CoolingDown
    readyPulsing() -> CanvasControlState.Ready
    else -> CanvasControlState.Idle
}

private fun CanvasElement.readyPulsing(): Boolean =
    readySince?.let { it.elapsedNow().inWholeMilliseconds < READY_PULSE_MILLIS } ?: false

/** How long a button stays [CanvasControlState.Ready] after it becomes usable again. */
private const val READY_PULSE_MILLIS = 400L

/** This control's slots: its own skin over the tree's theme, or null to draw its plain look. */
internal fun CanvasTree.skinOf(element: CanvasElement): CanvasControlSkin? = element.resolvedSkin(controls.theme)
