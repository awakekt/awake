/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.animation

import com.awakekt.awake.core.animation.Skin
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh

/**
 * One submesh bound to a modular character's shared skeleton, keyed by a consumer-chosen [slotName].
 *
 * @property slotName Unique slot identifier (e.g., "hair", "chest", "hands").
 * @property mesh The geometric mesh for this equipment piece.
 * @property material The material used to shade this slot piece.
 * @property isVisible Whether this specific slot piece should be drawn.
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
 *
 * @property skin The skeletal hierarchy and inverse bind matrices shared across all slot pieces.
 * @property slots Map of equipped character slots keyed by slot name.
 * @property isVisible Master visibility toggle for the entire modular character.
 */
data class ModularCharacterComponent(
    val skin: Skin,
    val slots: Map<String, CharacterSlot> = LinkedHashMap(),
    var isVisible: Boolean = true,
) {
    // Safe: default constructor assigns LinkedHashMap, and copy preserves it.
    @Suppress("UNCHECKED_CAST")
    private val mutableSlots: MutableMap<String, CharacterSlot>
        get() = (slots as? MutableMap<String, CharacterSlot>)
            ?: error("slots must be an instance of MutableMap")

    /** Attaches or replaces the submesh bound to [slot]'s own name. */
    fun setSlot(slot: CharacterSlot) {
        mutableSlots[slot.slotName] = slot
    }

    /**
     * Equips or replaces a slot with the provided [mesh] and [material].
     *
     * @param slotName Identifier of the slot.
     * @param mesh Slot geometry.
     * @param material Slot material.
     * @param isVisible Initial visibility state.
     * @return The newly equipped [CharacterSlot].
     */
    fun equip(slotName: String, mesh: Mesh, material: Material, isVisible: Boolean = true): CharacterSlot {
        val slot = CharacterSlot(slotName, mesh, material, isVisible)
        mutableSlots[slotName] = slot
        return slot
    }

    /**
     * Detaches and returns the submesh bound to [slotName].
     *
     * @param slotName Name of the slot to unequip.
     * @return The removed [CharacterSlot], or null if absent.
     */
    fun unequip(slotName: String): CharacterSlot? = mutableSlots.remove(slotName)

    /**
     * Detaches the submesh bound to [slotName].
     */
    fun removeSlot(slotName: String): CharacterSlot? = mutableSlots.remove(slotName)

    /**
     * Retrieves the slot bound to [slotName], or null if absent.
     */
    fun getSlot(slotName: String): CharacterSlot? = slots[slotName]

    /**
     * Returns true if a submesh is currently bound to [slotName].
     */
    fun hasSlot(slotName: String): Boolean = slots.containsKey(slotName)

    /**
     * Clears all equipped slots from this character.
     */
    fun clearSlots() {
        mutableSlots.clear()
    }

    /** Returns the number of currently visible slot pieces. */
    val visibleSlotCount: Int
        get() = slots.values.count { it.isVisible }
}
