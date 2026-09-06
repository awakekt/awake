/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.input

import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.math.Vec3f
import kotlin.math.sqrt

/**
 * Key binding pairing a primary and optional secondary/alternative hardware key.
 *
 * @property primary The main keyboard key for this action.
 * @property secondary An optional alternative key (e.g. ArrowUp in addition to W).
 */
data class KeyBinding(
    var primary: Key,
    var secondary: Key? = null,
) {
    /** Returns true if either [primary] or [secondary] is contained in the given [keys] set. */
    fun matches(keys: Set<Key>): Boolean =
        primary in keys || (secondary != null && secondary in keys)
}

/**
 * Configurable, type-safe keybinding profile mapping semantic action identifiers to hardware keys.
 *
 * Supports runtime rebinding, normalized 2D movement axis querying, and zero-allocation checks.
 *
 * @param A The action type (typically an enum representing game actions).
 * @property bindings Map of action identifiers to configured key bindings.
 */
class KeybindingProfile<A : Any>(
    private val bindings: MutableMap<A, KeyBinding> = mutableMapOf(),
) {
    /** Returns the active binding for [action], or null if unbound. */
    fun getBinding(action: A): KeyBinding? = bindings[action]

    /** Checks whether any key bound to [action] is currently held down. */
    fun isDown(action: A, input: InputSnapshot): Boolean =
        bindings[action]?.matches(input.keysDown) ?: false

    /** Checks whether any key bound to [action] was newly pressed this frame. */
    fun isPressed(action: A, input: InputSnapshot): Boolean =
        bindings[action]?.matches(input.keysPressed) ?: false

    /** Checks whether any key bound to [action] was released this frame. */
    fun isReleased(action: A, input: InputSnapshot): Boolean =
        bindings[action]?.matches(input.keysReleased) ?: false

    /**
     * Calculates the normalized 2D movement vector (x = strafe, z = forward/back)
     * based on active directional action states.
     *
     * Returns `Vec3f.ZERO` if no directional keys are active or if opposing directions cancel.
     */
    fun getAxis2D(
        forward: A,
        backward: A,
        left: A,
        right: A,
        input: InputSnapshot,
    ): Vec3f {
        var moveX = 0f
        var moveZ = 0f

        if (isDown(forward, input)) moveZ -= 1f
        if (isDown(backward, input)) moveZ += 1f
        if (isDown(left, input)) moveX -= 1f
        if (isDown(right, input)) moveX += 1f

        if (moveX == 0f && moveZ == 0f) return Vec3f.ZERO

        val len = sqrt(moveX * moveX + moveZ * moveZ)
        return Vec3f(moveX / len, 0f, moveZ / len)
    }

    /**
     * Rebinds [action] to [newKey].
     *
     * @param isSecondary If true, updates the secondary slot; otherwise updates primary.
     */
    fun rebind(action: A, newKey: Key, isSecondary: Boolean = false) {
        val existing = bindings.getOrPut(action) { KeyBinding(newKey) }
        if (isSecondary) {
            existing.secondary = newKey
        } else {
            existing.primary = newKey
        }
    }
}

/** Builder class for the fluent [keybindingProfile] DSL. */
class KeybindingProfileBuilder<A : Any> {
    private val map = mutableMapOf<A, KeyBinding>()

    /** Binds [action] with a primary [primary] and optional [secondary] key. */
    fun bind(action: A, primary: Key, secondary: Key? = null) {
        map[action] = KeyBinding(primary, secondary)
    }

    /** Constructs the final [KeybindingProfile]. */
    fun build(): KeybindingProfile<A> = KeybindingProfile(map)
}

/**
 * Fluent DSL builder to construct a typed [KeybindingProfile].
 */
inline fun <A : Any> keybindingProfile(
    builder: KeybindingProfileBuilder<A>.() -> Unit,
): KeybindingProfile<A> = KeybindingProfileBuilder<A>().apply(builder).build()
