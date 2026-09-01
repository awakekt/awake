/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering.animation

import io.github.awakelab.awake.core.animation.Skin
import io.github.awakelab.awake.render.material.Material
import io.github.awakelab.awake.render.mesh.Mesh

/**
 * One submesh bound to a modular character's shared skeleton, keyed by a consumer-chosen
 * [slotName]. What slots a game has -- hair, armor, gloves -- is its own vocabulary (see D28);
 * this type only carries the binding.
 */
data class CharacterSlot(
    val slotName: String,
    val mesh: Mesh,
    val material: Material,
    var isVisible: Boolean = true,
)

/**
 * ECS component representing a multi-slot skinned modular character.
 *
 * All attached [slots] share the same underlying [skin] and skeletal hierarchy,
 * allowing modular parts to be attached and detached at runtime without requiring
 * software CPU texture atlasing or vertex buffer recombination.
 */
data class ModularCharacterComponent(
    val skin: Skin,
    val slots: MutableMap<String, CharacterSlot> = LinkedHashMap(),
    var isVisible: Boolean = true,
) {
    /** Attaches or replaces the submesh bound to [slot]'s own name. */
    fun setSlot(slot: CharacterSlot) {
        slots[slot.slotName] = slot
    }

    /** Detaches the submesh bound to [slotName]. */
    fun removeSlot(slotName: String): CharacterSlot? {
        return slots.remove(slotName)
    }

    /** Returns true if a submesh is currently bound to [slotName]. */
    fun hasSlot(slotName: String): Boolean = slots.containsKey(slotName)

    /** Returns the number of currently visible slot pieces. */
    val visibleSlotCount: Int
        get() = slots.values.count { it.isVisible }
}
