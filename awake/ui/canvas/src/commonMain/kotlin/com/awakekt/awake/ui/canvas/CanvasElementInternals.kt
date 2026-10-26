/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.foundation.animation.TrailingValue
import com.awakekt.awake.compose.foundation.interaction.InteractionSource
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.ui.focus.FocusRequester
import com.awakekt.awake.core.input.HeldStep
import com.awakekt.awake.core.math2d.Vec2
import kotlin.time.TimeSource

// The overlay's own reach into an element's components and live input, by the names it has always
// used: each is the field of the component that now holds it.

internal val CanvasElement.interactions: InteractionSource get() = pointer.interactions

internal var CanvasElement.pressStart: Vec2?
    get() = pointer.pressStart
    set(value) {
        pointer.pressStart = value
    }

internal var CanvasElement.dragPointCanvasX: Float
    get() = pointer.dragPointCanvasX
    set(value) {
        pointer.dragPointCanvasX = value
    }

internal var CanvasElement.dragPointCanvasY: Float
    get() = pointer.dragPointCanvasY
    set(value) {
        pointer.dragPointCanvasY = value
    }

internal var CanvasElement.droppedSource: CanvasElement?
    get() = pointer.droppedSource
    set(value) {
        pointer.droppedSource = value
    }

internal var CanvasElement.droppedOutsideAt: Vec2?
    get() = pointer.droppedOutsideAt
    set(value) {
        pointer.droppedOutsideAt = value
    }

internal var CanvasElement.linkKey: String?
    get() = pointer.linkKey
    set(value) {
        pointer.linkKey = value
    }

internal val CanvasElement.textFieldState: TextFieldState get() = pointer.textFieldState

internal val CanvasElement.focusRequester: FocusRequester get() = pointer.focusRequester

internal var CanvasElement.focusGainedThisFrame: Boolean
    get() = pointer.focusGainedThisFrame
    set(value) {
        pointer.focusGainedThisFrame = value
    }

/** What times the element's presses, holds and ready pulse; a test swaps it for a `TestTimeSource`. */
internal var CanvasElement.clock: TimeSource
    get() = pointer.clock
    set(value) {
        pointer.clock = value
        (widget as? CanvasButtonWidget)?.clock = value
    }

/** A Bar's trailing fill; only a Bar draws one. */
internal val CanvasElement.trail: TrailingValue get() = barWidget.trail

internal var CanvasElement.trailStarted: Boolean
    get() = barWidget.trailStarted
    set(value) {
        barWidget.trailStarted = value
    }

private val CanvasElement.barWidget: CanvasBarWidget
    get() = checkNotNull(widget as? CanvasBarWidget) { "only a Bar has a trail" }

internal val CanvasElement.stick: CanvasStick get() = pointer.stick

internal val CanvasElement.lookState: CanvasLookState get() = pointer.lookState

internal val CanvasElement.buttonPress: CanvasButtonPress get() = pointer.buttonPress

internal val CanvasElement.minusPress: InteractionSource get() = pointer.minusPress

internal val CanvasElement.plusPress: InteractionSource get() = pointer.plusPress

internal val CanvasElement.minusHold: HeldStep get() = pointer.minusHold

internal val CanvasElement.plusHold: HeldStep get() = pointer.plusHold

internal val CanvasElement.slotTransitions: HashMap<String, SlotTransition> get() = pointer.slotTransitions

/** This control's skin over [theme], worked out again only when either changes. */
internal fun CanvasElement.resolvedSkin(theme: CanvasControlTheme?): CanvasControlSkin? = pointer.resolvedSkin(theme, skin)

internal fun CanvasElement.press() = pointer.press()

/** A right-click on a Button or a pressable element, reported only while it is [CanvasElement.usable]. */
internal fun CanvasElement.secondaryPress() {
    if (usable) pointer.secondaryPress()
}

/** Adds to the wheel collected since the last [CanvasElement.consumeWheel]; [CanvasOverlay] calls this. */
internal fun CanvasElement.addWheel(delta: Float) = pointer.addWheel(delta)

/**
 * Lets go of every pointer this element's controls hold, as when it stops being drawn mid-press:
 * a stick lets go as a release would, and a button or look area forgets its press without
 * reporting it, so nothing stays held while it cannot be seen.
 */
internal fun CanvasElement.dropPointers() {
    if (stick.pointer != null) stick.release(joystick ?: CanvasJoystick.Default)
    buttonPress.cancel(this)
    lookState.clear()
}

/**
 * Takes a TextField's line submitted by Enter. A line typed the same frame as its Enter hasn't
 * reached the text yet, so it is synced here rather than at the next composition: a system clearing
 * the text would otherwise look like no change at all, and the next sync would put the sent line back.
 */
internal fun CanvasElement.submit() {
    pointer.submittedText = textFieldState.text
    text = textFieldState.text
    pointer.lastSyncedText = text
    focused = false
}

/**
 * Keeps [textFieldState] and the element's text pointed at the same string, in whichever direction
 * changed: typing updates [textFieldState] first, so that reaches the text; a system writing the
 * text directly, clearing it after a send, is not the last synced text, so that reaches
 * [textFieldState] instead, caret at the end.
 */
internal fun CanvasElement.syncTextField() {
    if (text != pointer.lastSyncedText) {
        textFieldState.setText(text)
    } else if (textFieldState.text != text) {
        text = textFieldState.text
    }
    pointer.lastSyncedText = text
}
