/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

/** The type of a value in a formula. */
enum class FormulaType {
    /** A finite floating-point number. */
    Number,

    /** `true` or `false`. */
    Boolean,
    ;

    internal val label: String
        get() = when (this) {
            Number -> "a number"
            Boolean -> "true or false"
        }
}

/**
 * One named input a formula may read. It belongs to the [FormulaVariables] that declared it: get it with
 * [FormulaVariables.get] and set its value on [FormulaInputs].
 */
class FormulaVariable internal constructor(
    /** What a formula calls it, such as `atk` or `attacker.level`. */
    val name: String,
    /** Whether it holds a number or true or false. */
    val type: FormulaType,
    /** What it means, for an editor to show; empty when none was given. */
    val description: String,
    internal val index: Int,
) {
    override fun toString(): String = "$name: $type"
}

/**
 * The variables a formula may read, in declaration order. A plugin declares them once, for example the
 * attacker's and defender's stats a damage formula sees; every formula is compiled against them, and a
 * formula that names anything else is rejected when it is compiled.
 *
 * A name is letters, digits and `_`, not starting with a digit, with `.` between parts (`attacker.level`).
 * It must not be a keyword: `and`, `or`, `not`, `if`, `true`, `false`.
 *
 * Two declarations with the same names and types in the same order are equal, and a formula compiled
 * against one accepts [FormulaInputs] made from the other.
 */
class FormulaVariables private constructor(declarations: List<Declaration>) {
    /** Every variable, in declaration order. */
    val all: List<FormulaVariable> =
        declarations.mapIndexed { index, declaration ->
            FormulaVariable(declaration.name, declaration.type, declaration.description, index)
        }

    /** Every variable's name, in declaration order. */
    val names: List<String> = all.map { it.name }

    private val byName: Map<String, FormulaVariable> = all.associateBy { it.name }

    /** How many variables there are. */
    val size: Int get() = all.size

    /**
     * The variable called [name].
     *
     * @throws IllegalArgumentException when none is declared with that name.
     */
    operator fun get(name: String): FormulaVariable =
        byName[name] ?: throw IllegalArgumentException("No formula variable '$name'; declared: ${names.joinToString()}")

    /** The variable called [name], or null when none is declared with that name. */
    fun find(name: String): FormulaVariable? = byName[name]

    internal fun owns(variable: FormulaVariable): Boolean {
        val own = all.getOrNull(variable.index) ?: return false
        return own === variable || (own.name == variable.name && own.type == variable.type)
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is FormulaVariables &&
            other.all.size == all.size &&
            all.indices.all { all[it].name == other.all[it].name && all[it].type == other.all[it].type }

    override fun hashCode(): Int = all.fold(0) { hash, variable -> 31 * (31 * hash + variable.name.hashCode()) + variable.type.hashCode() }

    override fun toString(): String = all.joinToString(prefix = "FormulaVariables(", postfix = ")")

    /** Declares variables in order; see [formulaVariables]. */
    class Builder internal constructor() {
        internal val declarations = mutableListOf<Declaration>()

        /** Declares a number variable called [name], described by [description] for an editor. */
        fun number(name: String, description: String = "") {
            declarations += Declaration(name, FormulaType.Number, description)
        }

        /** Declares a true-or-false variable called [name], described by [description] for an editor. */
        fun boolean(name: String, description: String = "") {
            declarations += Declaration(name, FormulaType.Boolean, description)
        }
    }

    internal class Declaration(val name: String, val type: FormulaType, val description: String)

    /** Ready-made declarations. */
    companion object {
        /** No variables: a formula compiled against it may use only numbers and functions. */
        val Empty: FormulaVariables = FormulaVariables(emptyList())

        /**
         * Number variables called [names], in that order.
         *
         * @throws IllegalArgumentException when a name is not valid or is repeated.
         */
        fun numbers(vararg names: String): FormulaVariables =
            of(names.map { Declaration(it, FormulaType.Number, "") })

        internal fun of(declarations: List<Declaration>): FormulaVariables {
            val seen = HashSet<String>()
            for (declaration in declarations) {
                require(isValidName(declaration.name)) {
                    "'${declaration.name}' is not a valid formula variable name: use letters, digits and _, " +
                        "not starting with a digit, with . between parts"
                }
                require(declaration.name !in Keywords) { "'${declaration.name}' is a formula keyword" }
                require(seen.add(declaration.name)) { "Formula variable '${declaration.name}' is declared twice" }
            }
            return FormulaVariables(declarations)
        }

        private fun isValidName(name: String): Boolean =
            name.isNotEmpty() && name.split('.').all { part ->
                part.isNotEmpty() && isNameStart(part[0]) && part.all(::isNamePart)
            }
    }
}

/**
 * Declares formula variables, in order.
 *
 * For example:
 *
 * ```kotlin
 * val combat = formulaVariables {
 *     number("atk", "Attacker's attack power")
 *     number("def", "Defender's defence")
 *     boolean("critical")
 * }
 * ```
 *
 * @throws IllegalArgumentException when a name is not valid, is a keyword, or is repeated.
 */
fun formulaVariables(block: FormulaVariables.Builder.() -> Unit): FormulaVariables =
    FormulaVariables.of(FormulaVariables.Builder().apply(block).declarations)

internal val Keywords: Set<String> = setOf("and", "or", "not", "if", "true", "false")

internal fun isNameStart(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c == '_'

internal fun isNamePart(c: Char): Boolean = isNameStart(c) || c in '0'..'9'
