/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.config

/**
 * What a [LaunchOptions] schema read: each option's value, resolved once by [LaunchOptions.parse], and the
 * [problems] the parse met. Reading a value allocates nothing, so a system may read one each frame, although
 * reading it once into its constructor is the usual shape.
 *
 * A host registers it as a service (`FeatureHost.provide(launch)`), and a feature reads it with
 * `require<LaunchValues>()`. [toString] names the options that were given, never their values.
 *
 * @property schema the schema these values are of.
 * @property problems what the parse did not use as given, in the order it met them. Log them; nothing throws.
 */
class LaunchValues internal constructor(
    val schema: LaunchOptions,
    private val values: Array<Any>,
    private val given: BooleanArray,
    val problems: List<LaunchProblem>,
) {
    /** The value of [option]: the one given, or its default when it was not given or was refused. */
    operator fun <T : Any> get(option: LaunchOption<T>): T {
        require(option.owner === schema) { "$option is not an option of this schema" }
        @Suppress("UNCHECKED_CAST")
        return values[option.index] as T
    }

    /** Whether [option] was given and accepted, rather than left at its default. */
    fun isGiven(option: LaunchOption<*>): Boolean {
        require(option.owner === schema) { "$option is not an option of this schema" }
        return given[option.index]
    }

    override fun toString(): String =
        "LaunchValues(given=${schema.options.filter { given[it.index] }.map { it.name }}, problems=${problems.size})"
}

/** One parse of a schema: the work behind [LaunchOptions.parse]. */
internal class LaunchParse(
    private val schema: LaunchOptions,
    private val options: List<LaunchOption<*>>,
    private val development: Boolean,
) {
    private val values = Array<Any>(options.size) { options[it].default }
    private val given = BooleanArray(options.size)
    private val problems = ArrayList<LaunchProblem>()

    fun run(parameters: Map<String, List<String>>, arguments: List<String>): LaunchValues {
        val spaced = strays(arguments)
        for ((name, raw) in parameters) {
            val option = schema.find(name)
            when {
                option == null -> if (!schema.isIgnored(name)) problems += LaunchProblem.Unknown(name)
                option.development && !development -> problems += LaunchProblem.DevelopmentOnly(option)
                else -> read(option, raw, spaced[option.index])
            }
        }
        return LaunchValues(schema, values, given, problems)
    }

    private fun <T : Any> read(option: LaunchOption<T>, raw: List<String>, spacedValue: String?) {
        val text = if (option.type === ValueType.List || option.type === ValueType.Ids) {
            raw.joinToString(",")
        } else {
            raw.lastOrNull().orEmpty()
        }
        val value = option.type.parse(text)
        val reason = when {
            text.isEmpty() && option.type.needsValue -> "needs a value: use " + example(option, spacedValue)
            value == null -> "is not a ${option.type.name}"
            else -> option.refusal(value)
        }
        if (reason != null || value == null) {
            problems += LaunchProblem.Invalid(option, text, reason ?: "is not a ${option.type.name}")
        } else {
            values[option.index] = value
            given[option.index] = true
        }
    }

    /**
     * Reports each positional argument, and returns, per option index, the value written after a space for
     * an option that needs one (`--debug a,b`), so its own problem can say what to write.
     */
    private fun strays(arguments: List<String>): Map<Int, String> {
        val spaced = HashMap<Int, String>()
        val options = arguments.takeWhile { it != "--" }
        options.forEachIndexed { i, argument ->
            if (argument.startsWith("--") || argument.isEmpty()) return@forEachIndexed
            val previous = options.getOrNull(i - 1)?.takeIf { it.startsWith("--") && '=' !in it }
            val owner = previous?.let { schema.find(it.removePrefix("--")) }?.takeIf { it.type.needsValue }
            if (owner == null) {
                problems += LaunchProblem.Stray(argument, null)
            } else {
                spaced[owner.index] = argument
                val shown = if (owner.secret) "(secret)" else argument
                problems += LaunchProblem.Stray(shown, "use " + example(owner, argument))
            }
        }
        return spaced
    }

    private fun example(option: LaunchOption<*>, value: String?): String =
        "--${option.name}=" + if (value == null || option.secret) "<value>" else value
}
