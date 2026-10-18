/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

import kotlinx.serialization.Serializable

/**
 * A variable a game declares in its own data, for a plugin to add to the [FormulaVariables] its formulas
 * read. A game names its stats once, and a plugin declares each under the prefixes it evaluates them for,
 * with [FormulaVariables.Builder.declare]:
 *
 * ```json
 * "stats": [
 *   { "name": "str", "description": "Strength: adds to damage" },
 *   { "name": "dex", "description": "Dexterity: adds to accuracy" },
 *   { "name": "berserk", "type": "boolean" }
 * ]
 * ```
 *
 * A bad name fails the load with the reason [FormulaVariables.validateName] gives.
 *
 * @property name What a formula calls it, after any prefix the plugin adds: `str` becomes `attacker.str`.
 * @property type Whether it holds a number or true or false; a number when omitted.
 * @property description What it means, for an editor to show; empty when none is given.
 */
@Serializable
class FormulaVariableDeclaration(
    val name: String,
    val type: FormulaType = FormulaType.Number,
    val description: String = "",
) {
    init {
        val reason = FormulaVariables.validateName(name)
        require(reason == null) { reason.orEmpty() }
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is FormulaVariableDeclaration &&
            other.name == name &&
            other.type == type &&
            other.description == description

    override fun hashCode(): Int = (name.hashCode() * 31 + type.hashCode()) * 31 + description.hashCode()

    override fun toString(): String = "$name: $type"
}
