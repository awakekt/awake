/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.controls

import io.github.awakelab.awake.compose.ui.platform.InputOwnership
import io.github.awakelab.awake.compose.ui.platform.blocksGameplayKeys
import io.github.awakelab.awake.core.input.InputSnapshot
import io.github.awakelab.awake.core.input.Key
import io.github.awakelab.awake.core.input.PointerButton

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
 */
class GameplayInput(
    private val snapshot: InputSnapshot,
    private val claimed: InputOwnership,
) {
    /** False while the UI holds the pointer or a modal is open, so a click on a dialog or backdrop is not a world drag. */
    val pointerDown: Boolean get() = snapshot.pointerDown && !claimed.isCaptured && !claimed.isModalOpen

    val pointerX: Float get() = snapshot.pointerX

    val pointerY: Float get() = snapshot.pointerY

    /** Zero while a scrollable under the pointer took the wheel or a modal layer is open. */
    val scrollDeltaY: Float get() = if (claimed.isScrollConsumed || claimed.isModalOpen) 0f else snapshot.scrollDeltaY

    /**
     * Whether [button] is held for the world, gated by the same capture and modal rules as [pointerDown].
     *
     * A middle-drag that began over a panel belongs to the panel, exactly as a left-drag does.
     * Reading `snapshot.buttonsDown` directly would skip that and pan the camera from under a UI
     * the user was interacting with.
     */
    fun isDown(button: PointerButton): Boolean = !claimed.isCaptured && !claimed.isModalOpen && snapshot.isDown(button)

    /** False while the UI owns the keyboard, whatever the key. */
    fun isDown(key: Key): Boolean = !claimed.blocksGameplayKeys && snapshot.keysDown.contains(key)

    fun wasPressed(key: Key): Boolean = !claimed.blocksGameplayKeys && snapshot.wasPressed(key)

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
