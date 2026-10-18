/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.expression

/**
 * One place in a definition that holds a formula, such as `combat.chances.miss`: its source, the variables
 * it may read and whether it has a random source. [FormulaSet.Builder.formula] and
 * [FormulaSet.Builder.condition] make one, and [FormulaSet.get] gives what it compiled to.
 *
 * @param T [CompiledFormula] for a number, [CompiledCondition] for true or false.
 * @property path Where it is in the definition, such as `combat.chances.miss`; unique in its set.
 * @property source The formula's text.
 * @property type What it must give.
 * @property variables What it may read.
 * @property random Whether its caller supplies [FormulaInputs.random], so it may call `random()`.
 * @property options The limits and policy it is compiled with.
 */
class FormulaSlot<out T : CompiledExpression> internal constructor(
    val path: String,
    val source: String,
    val type: FormulaType,
    val variables: FormulaVariables,
    val random: Boolean,
    val options: FormulaOptions,
) {
    /** Where it is in its builder's slots; set once, when it is added. */
    internal var index: Int = -1

    /** `path: 'source'`. */
    override fun toString(): String = "$path: '${preview(source)}'"
}

/**
 * Something wrong with one formula of a [FormulaSet], and where: which slot, and where in its source, so an
 * editor can underline both the slot and the part of the formula.
 *
 * @property path The slot's [FormulaSlot.path].
 * @property source The slot's formula text.
 * @property problem What is wrong, and where in [source].
 */
class FormulaSetProblem(
    val path: String,
    val source: String,
    val problem: FormulaProblem,
) {
    /** What is wrong. */
    val kind: FormulaProblemKind get() = problem.kind

    /** A sentence saying what is wrong, for a person. */
    val message: String get() = problem.message

    /** The offset in [source] of the first character it covers. */
    val start: Int get() = problem.start

    /** The offset in [source] after the last character it covers. */
    val end: Int get() = problem.end

    /** The 1-based line in [source] that [start] is on. */
    val line: Int get() = problem.line

    /** The 1-based column of [start] on its line. */
    val column: Int get() = problem.column

    /** `path: 'source': line:column: message`. */
    override fun toString(): String = "$path: '${preview(source)}': $problem"
}

/**
 * Thrown when a [FormulaSet] does not compile. Its message lists every problem of every slot, one per line,
 * as `path: 'source': line:column: message`.
 *
 * @property problems Everything wrong, in slot order and then source order; at least one.
 */
class FormulaSetException(
    val problems: List<FormulaSetProblem>,
) : IllegalArgumentException(problems.joinToString(separator = "\n", prefix = "Invalid formulas:\n") { "- $it" })

/** What compiling a [FormulaSet] gave: every slot compiled, or every problem. */
sealed interface FormulaSetResult {
    /** Everything wrong, in slot order and then source order; empty when it compiled. */
    val problems: List<FormulaSetProblem>

    /**
     * The compiled set.
     *
     * @throws FormulaSetException listing every problem when it did not compile.
     */
    fun getOrThrow(): FormulaSet

    /** Every slot compiled. */
    class Compiled(
        /** The compiled set. */
        val set: FormulaSet,
    ) : FormulaSetResult {
        override val problems: List<FormulaSetProblem> get() = emptyList()

        override fun getOrThrow(): FormulaSet = set
    }

    /** At least one slot did not compile. */
    class Invalid(
        override val problems: List<FormulaSetProblem>,
    ) : FormulaSetResult {
        init {
            require(problems.isNotEmpty()) { "An invalid formula set has at least one problem" }
        }

        override fun getOrThrow(): FormulaSet = throw FormulaSetException(problems)
    }
}

/**
 * Every formula of one definition, compiled together. A plugin's definition holds several formulas, each
 * with its own variables; loading it should fail once, naming every bad formula and where it is, not at the
 * first. A [Builder] collects the slots and compiles them all:
 *
 * ```kotlin
 * val formulas = FormulaSet.Builder()
 * val miss = formulas.formula("chances.miss", data.miss, chanceVariables, random = true)
 * val damage = formulas.formula("damage", data.damage, damageVariables)
 * val set = formulas.build() // throws FormulaSetException listing every problem
 * set[damage].evaluate(inputs)
 * ```
 *
 * A slot without a random source rejects `random()` when it is compiled, rather than failing when it runs.
 */
class FormulaSet private constructor(
    /** Every slot, in the order it was added. */
    val slots: List<FormulaSlot<*>>,
    private val compiled: List<CompiledExpression>,
) {
    /** Every slot's compiled formula or condition, by [FormulaSlot.path], in the order they were added. */
    val expressions: Map<String, CompiledExpression> = slots.indices.associate { slots[it].path to compiled[it] }

    /**
     * What [slot] compiled to.
     *
     * @throws IllegalArgumentException when [slot] was made by another builder.
     */
    operator fun <T : CompiledExpression> get(slot: FormulaSlot<T>): T {
        require(slots.getOrNull(slot.index) === slot) { "The slot $slot is not part of this formula set" }
        @Suppress("UNCHECKED_CAST")
        return compiled[slot.index] as T
    }

    /**
     * The formula at [path].
     *
     * @throws IllegalArgumentException when there is no slot at [path] or it holds a condition.
     */
    fun formula(path: String): CompiledFormula =
        expressions[path] as? CompiledFormula ?: throw IllegalArgumentException(missing(path, FormulaType.Number))

    /**
     * The condition at [path].
     *
     * @throws IllegalArgumentException when there is no slot at [path] or it holds a number formula.
     */
    fun condition(path: String): CompiledCondition =
        expressions[path] as? CompiledCondition ?: throw IllegalArgumentException(missing(path, FormulaType.Boolean))

    private fun missing(path: String, type: FormulaType): String {
        val slot = slots.firstOrNull { it.path == path }
        return if (slot == null) {
            "No formula slot '$path'; the slots are ${slots.joinToString { it.path }}"
        } else {
            "The formula slot '$path' gives ${slot.type.label}, not ${type.label}"
        }
    }

    override fun toString(): String = slots.joinToString(prefix = "FormulaSet(", postfix = ")")

    /**
     * Collects a definition's formula slots, then compiles them all with [compile] or [build].
     *
     * @property options The limits and policy a slot is compiled with unless it names its own.
     */
    class Builder(
        val options: FormulaOptions = FormulaOptions.Default,
    ) {
        private val slots = ArrayList<FormulaSlot<*>>()
        private val paths = HashSet<String>()

        /**
         * Adds a slot at [path] for [source], a formula giving a number that reads [variables]. With [random],
         * its caller supplies [FormulaInputs.random] and it may call `random()`; without, `random()` is a
         * problem.
         *
         * @throws IllegalArgumentException when [path] is blank or already used.
         */
        fun formula(
            path: String,
            source: String,
            variables: FormulaVariables = FormulaVariables.Empty,
            random: Boolean = false,
            options: FormulaOptions = this.options,
        ): FormulaSlot<CompiledFormula> = add(FormulaSlot(path, source, FormulaType.Number, variables, random, options))

        /**
         * Adds a slot at [path] for [source], a condition giving true or false that reads [variables]. [random]
         * is as for [formula].
         *
         * @throws IllegalArgumentException when [path] is blank or already used.
         */
        fun condition(
            path: String,
            source: String,
            variables: FormulaVariables = FormulaVariables.Empty,
            random: Boolean = false,
            options: FormulaOptions = this.options,
        ): FormulaSlot<CompiledCondition> = add(FormulaSlot(path, source, FormulaType.Boolean, variables, random, options))

        private fun <T : CompiledExpression> add(slot: FormulaSlot<T>): FormulaSlot<T> {
            require(slot.path.isNotBlank()) { "A formula slot's path cannot be blank" }
            require(paths.add(slot.path)) { "The formula slot '${slot.path}' is added twice" }
            slot.index = slots.size
            slots += slot
            return slot
        }

        /** Compiles every slot added so far: all of them, or every problem of every slot. */
        fun compile(): FormulaSetResult {
            val compiled = ArrayList<CompiledExpression>(slots.size)
            val problems = ArrayList<FormulaSetProblem>()
            for (slot in slots) {
                val compilation = Compiler(slot.source, slot.variables, slot.options, allowRandom = slot.random)
                    .compile(slot.type)
                val number = compilation.number
                val boolean = compilation.boolean
                when {
                    number != null ->
                        compiled += CompiledFormula(slot.source, slot.variables, slot.options, compilation, number)
                    boolean != null ->
                        compiled += CompiledCondition(slot.source, slot.variables, slot.options, compilation, boolean)
                    else -> compilation.problems.mapTo(problems) { FormulaSetProblem(slot.path, slot.source, it) }
                }
            }
            return if (problems.isEmpty()) {
                FormulaSetResult.Compiled(FormulaSet(slots.toList(), compiled))
            } else {
                FormulaSetResult.Invalid(problems)
            }
        }

        /**
         * Compiles every slot added so far.
         *
         * @throws FormulaSetException listing every problem of every slot when any does not compile.
         */
        fun build(): FormulaSet = compile().getOrThrow()
    }
}

/**
 * Compiles the slots [block] adds, all together. Use a [FormulaSet.Builder] directly to keep each slot's
 * [FormulaSlot] handle.
 *
 * @throws FormulaSetException listing every problem of every slot when any does not compile.
 */
fun formulaSet(
    options: FormulaOptions = FormulaOptions.Default,
    block: FormulaSet.Builder.() -> Unit,
): FormulaSet = FormulaSet.Builder(options).apply(block).build()
