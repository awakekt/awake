/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.compose.ui.platform.blocksGameplayKey
import com.awakekt.awake.compose.ui.platform.blocksGameplayKeys
import com.awakekt.awake.core.input.ActionInputSource
import com.awakekt.awake.core.input.GamepadButton
import com.awakekt.awake.core.input.HostInputState
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PinchGesture
import com.awakekt.awake.core.input.PointerButton
import com.awakekt.awake.core.input.ScrollSource
import com.awakekt.awake.core.input.TouchPoint

/**
 * This frame's input with whatever the UI claimed already taken out.
 *
 * One value rather than a raw snapshot beside an ownership record. Every system that took both did
 * the same subtraction itself, and the rule is not uniform -- which is the reason to write it once
 * rather than to make it uniform:
 *
 * - **Pointer** reads subtract [InputOwnership.isCaptured]. A drag the UI is holding is not a world
 *   drag.
 * - **Keys** subtract [blocksGameplayKey] per key. Typing "wasd" into a focused field must not walk
 *   the player, but a single-line field has no use for Up/Down and lets them reach a game system --
 *   a consumer pack's chat line stepping through its sent history.
 * - **Scroll** subtracts [InputOwnership.isScrollConsumed], so a scrolled panel does not also zoom.
 * - **Controller buttons** pass through whole: the UI reads no controller, so it claims none.
 *
 * A focused text field owns the keyboard and *not* the mouse, so dragging the world while a field
 * has focus stays legitimate. That asymmetry is deliberate -- `CameraSystem` carried the comment
 * saying so -- and it survives here rather than being tidied into one flag.
 *
 * It is also what a scene's input actions read keys, pointer and controller buttons from, with one difference:
 * while [keysOwnedByUi], actions read no key at all ([keysWithheldFromActions]). The keys a focused
 * field lets through are for a system that asks for them directly, not for movement, jump or run
 * bound to the same keys -- Up both recalling a chat line and walking the player forward.
 *
 * @param snapshot Raw input state snapshot captured this frame.
 * @param claimed UI input ownership state for the current frame.
 * @property hostState Live host preference/device information, supplied by the scene's standard
 *   input provider. Null for a manually constructed input whose host reports none.
 */
class GameplayInput(
    private val snapshot: InputSnapshot,
    private val claimed: InputOwnership,
    val hostState: HostInputState?,
) : ActionInputSource {
    /** Input from a manually managed host that exposes no host-state service. */
    constructor(snapshot: InputSnapshot, claimed: InputOwnership) : this(snapshot, claimed, null)

    /**
     * True while UI owns every pointer: a modal is open, or [InputOwnership.isCaptured] was set
     * manually without per-pointer ids.
     */
    private val blocksAllPointers: Boolean
        get() = claimed.isModalOpen || (claimed.isCaptured && claimed.capturedPointerIds.isEmpty())

    private val hasTouchContacts: Boolean
        get() = snapshot.touches.isNotEmpty()

    private fun isTouchCaptured(touch: TouchPoint): Boolean =
        (touch.id + TOUCH_POINTER_ID_OFFSET) in claimed.capturedPointerIds

    internal fun hasCapturedTouchContact(): Boolean =
        hasTouchContacts && snapshot.touches.any(::isTouchCaptured)

    internal fun firstWorldTouch(): TouchPoint? {
        for (i in snapshot.touches.indices) {
            val touch = snapshot.touches[i]
            if (touch.down && !isTouchCaptured(touch)) return touch
        }
        return null
    }

    internal fun secondWorldTouch(after: TouchPoint): TouchPoint? {
        var passed = false
        for (i in snapshot.touches.indices) {
            val touch = snapshot.touches[i]
            if (!passed) {
                if (touch === after) passed = true
            } else if (touch.down && !isTouchCaptured(touch)) {
                return touch
            }
        }
        return null
    }

    /** False while UI owns the world pointer (captured mouse, all touches captured, or modal open). */
    val pointerDown: Boolean
        get() = when {
            blocksAllPointers -> false
            hasTouchContacts -> firstWorldTouch() != null
            else -> snapshot.pointerDown && MOUSE_POINTER_ID !in claimed.capturedPointerIds
        }

    /** Current horizontal pointer coordinate in window pixels, following the first uncaptured touch when touching. */
    val pointerX: Float
        get() = if (!blocksAllPointers && hasTouchContacts) firstWorldTouch()?.x ?: snapshot.pointerX else snapshot.pointerX

    /** Current vertical pointer coordinate in window pixels, following the first uncaptured touch when touching. */
    val pointerY: Float
        get() = if (!blocksAllPointers && hasTouchContacts) firstWorldTouch()?.y ?: snapshot.pointerY else snapshot.pointerY

    /** Zero while a scrollable under the pointer took the wheel or a modal layer is open. */
    val scrollDeltaY: Float get() = if (claimed.isScrollConsumed || claimed.isModalOpen) 0f else snapshot.scrollDeltaY

    /** Sideways scroll -- a trackpad's two-finger swipe, or a tilt wheel -- gated exactly like [scrollDeltaY]. */
    val scrollDeltaX: Float get() = if (claimed.isScrollConsumed || claimed.isModalOpen) 0f else snapshot.scrollDeltaX

    /** What sent this frame's scroll, when the platform can tell; see [ScrollSource]. */
    val scrollSource: ScrollSource get() = snapshot.scrollSource

    /**
     * This frame's pinch, or `null` while the UI holds the world pointer, a captured touch would
     * mix with the platform's two-finger pinch, or a modal is open.
     */
    val pinch: PinchGesture?
        get() = when {
            blocksAllPointers -> null
            hasCapturedTouchContact() -> null
            !snapshot.pointerFromTouch && MOUSE_POINTER_ID in claimed.capturedPointerIds -> null
            else -> snapshot.pinch
        }

    /** Two or more uncaptured fingers are down on the world: a pinch. No camera drags then. */
    val isMultiTouch: Boolean
        get() = when {
            blocksAllPointers -> false
            hasTouchContacts -> {
                var down = 0
                for (i in snapshot.touches.indices) {
                    val touch = snapshot.touches[i]
                    if (touch.down && !isTouchCaptured(touch) && ++down == 2) return true
                }
                false
            }
            else -> snapshot.isMultiTouch
        }

    /**
     * Whether [button] is held for the world, gated per-pointer against UI capture and modals.
     */
    override fun isDown(button: PointerButton): Boolean = when {
        blocksAllPointers -> false
        hasTouchContacts && button == PointerButton.Primary -> firstWorldTouch() != null
        else -> MOUSE_POINTER_ID !in claimed.capturedPointerIds && snapshot.isDown(button)
    }

    /** Whether [button] was pressed this frame for the world, gated like [isDown]. */
    override fun wasPressed(button: PointerButton): Boolean = when {
        blocksAllPointers -> false
        hasTouchContacts && button == PointerButton.Primary -> {
            snapshot.touches.any { it.down && it.pressed && !isTouchCaptured(it) }
        }
        else -> MOUSE_POINTER_ID !in claimed.capturedPointerIds && snapshot.wasPressed(button)
    }

    /** Whether controller [button] is held; the UI never takes one. */
    override fun isDown(button: GamepadButton): Boolean = snapshot.isDown(button)

    /** Whether controller [button] was pressed this frame; the UI never takes one. */
    override fun wasPressed(button: GamepadButton): Boolean = snapshot.wasPressed(button)

    /** False while the UI takes [key] for itself -- a captured pointer or a modal take every key, a focused text field only the keys it uses. */
    override fun isDown(key: Key): Boolean = !claimed.blocksGameplayKey(key) && snapshot.keysDown.contains(key)

    /**
     * Returns whether [key] was newly pressed this frame, false while the UI takes [key] for itself.
     *
     * @param key The key code to check.
     * @return `true` if the key transitioned from released to pressed this frame and the UI does not take [key].
     */
    override fun wasPressed(key: Key): Boolean = !claimed.blocksGameplayKey(key) && snapshot.wasPressed(key)

    /**
     * Whether the UI has the keyboard.
     *
     * Exposed because clearing a held movement intent is not the same as reading no keys: a system
     * that simply saw `isDown == false` would leave the player walking in the last direction.
     */
    val keysOwnedByUi: Boolean get() = claimed.blocksGameplayKeys

    /** [keysOwnedByUi]: input actions read no key while the UI has the keyboard, even a key [isDown] lets through. */
    override val keysWithheldFromActions: Boolean get() = claimed.blocksGameplayKeys

    /** True when a modal layer is open. */
    val isModalOpen: Boolean get() = claimed.isModalOpen
}

private const val MOUSE_POINTER_ID = 0L
private const val TOUCH_POINTER_ID_OFFSET = 1L
