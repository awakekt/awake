/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai.perception

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.jvm.JvmInline

/**
 * A faction's id, such as `"town-watch"` or `"wolves"`. Which factions exist is the game's data.
 *
 * @property value The id. Not blank.
 */
@Serializable
@JvmInline
value class FactionId(val value: String) {
    init {
        require(value.isNotBlank()) { "A faction id is not blank." }
    }

    override fun toString(): String = value
}

/** How one faction regards another. Written in data as `hostile`, `neutral` and `friendly`. */
@Serializable
enum class Relation {
    /** An enemy. */
    @SerialName("hostile")
    Hostile,

    /** Neither. */
    @SerialName("neutral")
    Neutral,

    /** An ally. */
    @SerialName("friendly")
    Friendly,
}

/**
 * One row of a [FactionTable]: how [from] regards [to].
 *
 * @property from The faction that regards.
 * @property to The faction regarded.
 * @property relation How.
 * @property mutual Whether [to] regards [from] the same way.
 */
@Serializable
data class FactionRelation(
    val from: FactionId,
    val to: FactionId,
    val relation: Relation,
    val mutual: Boolean = true,
)

/**
 * Who regards whom how, as data. A pair with no row gets [otherwise]; a faction regards itself as
 * [sameFaction] unless a row says otherwise.
 *
 * @property relations The rows. Two rows may not give one pair different relations.
 * @property otherwise The relation between factions no row names.
 * @property sameFaction The relation of a faction to itself.
 */
@Serializable
data class FactionTable(
    val relations: List<FactionRelation> = emptyList(),
    val otherwise: Relation = Relation.Neutral,
    val sameFaction: Relation = Relation.Friendly,
) {
    @Transient
    private val lookup: Map<Pair<FactionId, FactionId>, Relation> = buildMap {
        for (row in relations) {
            val pairs = if (row.mutual) listOf(row.from to row.to, row.to to row.from) else listOf(row.from to row.to)
            for (pair in pairs) {
                val previous = put(pair, row.relation)
                require(previous == null || previous == row.relation) {
                    "The faction table says ${pair.first} is both $previous and ${row.relation} toward ${pair.second}."
                }
            }
        }
    }

    /** How [from] regards [to]. */
    fun relation(from: FactionId, to: FactionId): Relation =
        lookup[from to to] ?: if (from == to) sameFaction else otherwise
}
