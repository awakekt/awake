/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

/**
 * A project's named sorting layers, back to front: every sprite and tilemap on a later layer draws
 * over every one on an earlier layer, and their `sortOrder` orders them within a layer.
 *
 * [DEFAULT] is the layer a sprite is on unless it names another, and the one everything else
 * transparent, such as a 3D mesh, is drawn on. The list places it among the others by naming it;
 * a list that leaves it out has it first, behind them all. A name the list does not hold draws on
 * [DEFAULT], so a scene whose project lists no layers draws as it did before layers existed.
 *
 * @param names Layer names, back to front. Each is named once.
 */
class SortingLayers(names: List<String>) {
    /** The layers, back to front, [DEFAULT] among them. */
    val names: List<String> = if (DEFAULT in names) names.toList() else listOf(DEFAULT) + names

    private val ranks: Map<String, Int> = this.names.indexOf(DEFAULT).let { default ->
        this.names.withIndex().associate { (index, name) -> name to index - default }
    }

    init {
        require(names.none { it.isBlank() }) { "A sorting layer needs a name." }
        require(names.toSet().size == names.size) { "Each sorting layer is named once: $names" }
    }

    /**
     * Where the layer [name] draws relative to [DEFAULT], which is 0: below 0 behind it, above 0 over
     * it. A name not listed is 0.
     */
    fun rank(name: String): Int = ranks[name] ?: 0

    /** Whether [name] is one of the [names]. */
    operator fun contains(name: String): Boolean = name in ranks

    override fun equals(other: Any?): Boolean = other is SortingLayers && other.names == names

    override fun hashCode(): Int = names.hashCode()

    override fun toString(): String = "SortingLayers($names)"

    /** The default layer and the empty list. */
    companion object {
        /** The layer a sprite or tilemap is on unless it names another. */
        const val DEFAULT: String = "Default"

        /** No layers but [DEFAULT]: everything draws by its sort order alone. */
        val None: SortingLayers = SortingLayers(emptyList())
    }
}
