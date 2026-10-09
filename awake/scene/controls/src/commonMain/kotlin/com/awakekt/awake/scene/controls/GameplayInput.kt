/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.compose.ui.platform.blocksGameplayKeys
import com.awakekt.awake.core.input.ActionInputSource
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerButton
import com.awakekt.awake.core.input.ScrollSource

/**
 * This frame's input with whatever the UI claimed already taken out.
 *
 * One value rather than a raw snapshot beside an ownership record. Every system that took both did
 * the same subtraction itself, and the rule is not uniform -- which is the reason to write it once
 * rather than to make it uniform:
 *
 * - **Pointer** reads subtract [InputOwnership.isCaptured]. A drag the UI is holding is not a world
 *   drag.
 * - **Keys** subtract [blocksGameplayKeys]. Typing "wasd" into a focused field must not walk the
 *   player.
 * - **Scroll** subtracts [InputOwnership.isScrollConsumed], so a scrolled panel does not also zoom.
 *
 * A focused text field owns the keyboard and *not* the mouse, so dragging the world while a field
 * has focus stays legitimate. That asymmetry is deliberate -- `CameraSystem` carried the comment
 * saying so -- and it survives here rather than being tidied into one flag.
 *
 * It is also what a scene's input actions read keys and pointer buttons from, with the same rules.
 *
 * @param snapshot Raw input state snapshot captured this frame.
 * @param claimed UI input ownership state for the current frame.
 */
class GameplayInput(
    private val snapshot: InputSnapshot,
    private val claimed: InputOwnership,
) : ActionInputSource {
    /** False while the UI holds the pointer or a modal is open, so a click on a dialog or backdrop is not a world drag. */
    val pointerDown: Boolean get() = snapshot.pointerDown && !claimed.isCaptured && !claimed.isModalOpen

    /** Current horizontal pointer coordinate in window pixels. */
    val pointerX: Float get() = snapshot.pointerX

    /** Current vertical pointer coordinate in window pixels. */
    val pointerY: Float get() = snapshot.pointerY

    /** Zero while a scrollable under the pointer took the wheel or a modal layer is open. */
    val scrollDeltaY: Float get() = if (claimed.isScrollConsumed || claimed.isModalOpen) 0f else snapshot.scrollDeltaY

    /** Sideways scroll -- a trackpad's two-finger swipe, or a tilt wheel -- gated exactly like [scrollDeltaY]. */
    val scrollDeltaX: Float get() = if (claimed.isScrollConsumed || claimed.isModalOpen) 0f else snapshot.scrollDeltaX

    /** What sent this frame's scroll, when the platform can tell; see [ScrollSource]. */
    val scrollSource: ScrollSource get() = snapshot.scrollSource

    /**
     * Whether [button] is held for the world, gated by the same capture and modal rules as [pointerDown].
     *
     * A middle-drag that began over a panel belongs to the panel, exactly as a left-drag does.
     * Reading `snapshot.buttonsDown` directly would skip that and pan the camera from under a UI
     * the user was interacting with.
     */
    override fun isDown(button: PointerButton): Boolean = !claimed.isCaptured && !claimed.isModalOpen && snapshot.isDown(button)

    /** Whether [button] was pressed this frame for the world, gated like [isDown]. */
    override fun wasPressed(button: PointerButton): Boolean =
        !claimed.isCaptured && !claimed.isModalOpen && snapshot.wasPressed(button)

    /** False while the UI owns the keyboard, whatever the key. */
    override fun isDown(key: Key): Boolean = !claimed.blocksGameplayKeys && snapshot.keysDown.contains(key)

    /**
     * Returns whether [key] was newly pressed this frame, false while the UI owns the keyboard.
     *
     * @param key The key code to check.
     * @return `true` if the key transitioned from released to pressed this frame and UI does not own the keyboard.
     */
    override fun wasPressed(key: Key): Boolean = !claimed.blocksGameplayKeys && snapshot.wasPressed(key)

    /**
     * Whether the UI has the keyboard.
     *
     * Exposed because clearing a held movement intent is not the same as reading no keys: a system
     * that simply saw `isDown == false` would leave the player walking in the last direction.
     */
    val keysOwnedByUi: Boolean get() = claimed.blocksGameplayKeys

    /** True when a modal layer is open. */
    val isModalOpen: Boolean get() = claimed.isModalOpen
}
