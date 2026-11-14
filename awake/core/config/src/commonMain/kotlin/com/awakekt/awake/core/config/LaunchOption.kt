/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.config

/**
 * One option a game reads at launch, declared once in a [LaunchOptions] schema: `?name=value` on the web,
 * `--name=value` on desktop, an extra on Android. Read its value with [LaunchValues.get].
 *
 * The name is matched ignoring case, `-` and `_`, so `shadow-distance`, `shadowDistance` and
 * `shadowdistance` are this option. [aliases] are other names, such as one an older build used.
 *
 * @param T the type of the option's value.
 * @property name the name the help text shows, usually kebab-case.
 * @property type how the value is read.
 * @property default the value when the option is not given, or is given and refused.
 * @property description what the option does, for the help text.
 * @property aliases other names for the option.
 * @property development true when only a development build reads it; a release build reports it.
 * @property secret true for a token or a sign-in: its value never appears in a problem, a help text or
 *   [LaunchValues.toString].
 * @property bounds the allowed range or choices, as the help text shows them, or null.
 * @param check why a value is refused, or null when it is allowed.
 * @property owner the schema that declared the option.
 * @property index the option's place in [owner], where its value is kept.
 */
class LaunchOption<T : Any> internal constructor(
    val name: String,
    val type: ValueType<T>,
    val default: T,
    val description: String,
    val aliases: List<String>,
    val development: Boolean,
    val secret: Boolean,
    val bounds: String?,
    private val check: ((T) -> String?)?,
    internal val owner: LaunchOptions,
    internal val index: Int,
) {
    /** Why [value] is refused, or null when it is allowed. */
    fun refusal(value: T): String? = check?.invoke(value)

    override fun toString(): String = "LaunchOption($name)"
}

/**
 * Something a launch parse did not use as given. A parse never throws: the app decides what to log.
 * A secret option's value never appears in a [message].
 */
sealed class LaunchProblem {
    /** One line saying what happened, for a log. */
    abstract val message: String

    override fun toString(): String = message

    /**
     * A name that no option of the schema has, and that the schema does not ignore.
     *
     * @property name the name as given.
     */
    data class Unknown(val name: String) : LaunchProblem() {
        override val message: String get() = "unknown launch option '$name'"
    }

    /**
     * A value of [option] that was refused for [reason]; the option keeps its default.
     *
     * @property option the option the value was for.
     * @property given the text as given; never in [message] for a [LaunchOption.secret] option.
     * @property reason why it was refused.
     */
    class Invalid(val option: LaunchOption<*>, val given: String, val reason: String) : LaunchProblem() {
        override val message: String
            get() = "launch option '${option.name}' " +
                (if (option.secret) "" else "'$given' ") + "$reason; using the default"
    }

    /**
     * A [LaunchOption.development] option given to a build that does not read them.
     *
     * @property option the development option.
     */
    class DevelopmentOnly(val option: LaunchOption<*>) : LaunchProblem() {
        override val message: String
            get() = "launch option '${option.name}' is for development builds only; ignored"
    }

    /**
     * A command-line [argument] that is not an option, such as the `a,b` of `--debug a,b`. [hint] says
     * what was probably meant.
     *
     * @property argument the argument, or `(secret)` when it followed a secret option.
     * @property hint what to write instead, or null.
     */
    data class Stray(val argument: String, val hint: String?) : LaunchProblem() {
        override val message: String
            get() = "stray argument '$argument'" + (hint?.let { ": $it" } ?: "")
    }
}
