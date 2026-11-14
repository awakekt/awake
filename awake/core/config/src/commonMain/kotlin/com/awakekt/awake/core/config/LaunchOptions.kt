/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.config

/**
 * The options a game reads at launch, each declared once, as a plain Kotlin object:
 *
 * ```kotlin
 * object Launch : LaunchOptions() {
 *     val server = string("server", "ws://localhost:7000", "Game server address", aliases = listOf("host"))
 *     val shadowDistance = float("shadow-distance", 60f, "Shadow distance in metres", range = 0f..500f)
 *     val slot = int("slot", 0, "Development character slot", range = 0..7, development = true)
 *
 *     init { ignorePrefix("utm_") }
 * }
 *
 * val launch = Launch.parse(browserLaunchParameters().toMap())
 * launch.problems.forEach { log.warn(it.message) }
 * val server = launch[Launch.server]
 * ```
 *
 * The schema reads the parameters a host already parsed (`LaunchParameters.toMap()` in
 * `awake:engine:window`, on every platform), and needs nothing that draws, so it works before the engine
 * starts. [parse] never throws: an unknown name, a refused value or a development option in a release
 * build is a [LaunchProblem], and a refused option keeps its default. [describe] writes the help text.
 */
@Suppress("TooManyFunctions") // One declaration function per value type.
abstract class LaunchOptions {
    private val declared = ArrayList<LaunchOption<*>>()
    private val names = HashMap<String, LaunchOption<*>>()
    private val ignoredNames = HashSet<String>()
    private val ignoredPrefixes = ArrayList<String>()

    /** Every option, in the order it was declared: what a debug page or a generated help lists. */
    val options: List<LaunchOption<*>> get() = declared

    /** Declares an option of any [type]; [check] returns why a value is refused, and [bounds] shows the limits. */
    @Suppress("LongParameterList")
    protected fun <T : Any> option(
        name: String,
        type: ValueType<T>,
        default: T,
        description: String = "",
        aliases: List<String> = emptyList(),
        development: Boolean = false,
        secret: Boolean = false,
        bounds: String? = null,
        check: ((T) -> String?)? = null,
    ): LaunchOption<T> {
        val option = LaunchOption(
            name, type, default, description, aliases, development, secret, bounds, check, this, declared.size,
        )
        for (each in listOf(name) + aliases) {
            val key = normalizeName(each)
            require(key.isNotEmpty()) { "A launch option needs a name" }
            val taken = names.put(key, option)
            require(taken == null) { "Launch option '$each' is the same name as '${taken?.name}'" }
        }
        declared += option
        return option
    }

    /** A switch: a bare `--name` or `?name` is true. */
    protected fun boolean(
        name: String,
        default: Boolean = false,
        description: String = "",
        aliases: List<String> = emptyList(),
        development: Boolean = false,
    ): LaunchOption<Boolean> = option(name, ValueType.Boolean, default, description, aliases, development)

    /** A whole number, refused outside [range]. */
    @Suppress("LongParameterList")
    protected fun int(
        name: String,
        default: Int,
        description: String = "",
        range: IntRange? = null,
        aliases: List<String> = emptyList(),
        development: Boolean = false,
    ): LaunchOption<Int> = option(
        name,
        ValueType.Int,
        default,
        description,
        aliases,
        development,
        bounds = range?.toString(),
        check = range?.let { r -> { v: Int -> if (v in r) null else "is outside $r" } },
    )

    /** A number, refused outside [range]. */
    @Suppress("LongParameterList")
    protected fun float(
        name: String,
        default: Float,
        description: String = "",
        range: ClosedFloatingPointRange<Float>? = null,
        aliases: List<String> = emptyList(),
        development: Boolean = false,
    ): LaunchOption<Float> = option(
        name,
        ValueType.Float,
        default,
        description,
        aliases,
        development,
        bounds = range?.toString(),
        check = range?.let { r -> { v: Float -> if (v in r) null else "is outside $r" } },
    )

    /** Text, such as an address; [check] returns why a value is refused, for an allow-list. */
    @Suppress("LongParameterList")
    protected fun string(
        name: String,
        default: String,
        description: String = "",
        aliases: List<String> = emptyList(),
        development: Boolean = false,
        secret: Boolean = false,
        check: ((String) -> String?)? = null,
    ): LaunchOption<String> = option(name, ValueType.String, default, description, aliases, development, secret, null, check)

    /** One of [entries]. */
    @Suppress("LongParameterList")
    protected fun <E : Enum<E>> choice(
        name: String,
        default: E,
        entries: List<E>,
        description: String = "",
        aliases: List<String> = emptyList(),
        development: Boolean = false,
    ): LaunchOption<E> {
        val type = ValueType.choice(entries)
        return option(name, type, default, description, aliases, development, bounds = type.name)
    }

    /** Comma-separated items; a repeated option adds to the list. */
    protected fun list(
        name: String,
        default: List<String> = emptyList(),
        description: String = "",
        aliases: List<String> = emptyList(),
        development: Boolean = false,
    ): LaunchOption<List<String>> = option(name, ValueType.List, default, description, aliases, development)

    /** Comma-separated ids, lower-cased; a repeated option adds to the set. */
    protected fun ids(
        name: String,
        default: Set<String> = emptySet(),
        description: String = "",
        aliases: List<String> = emptyList(),
        development: Boolean = false,
    ): LaunchOption<Set<String>> = option(name, ValueType.Ids, default, description, aliases, development)

    /** Names that are not the game's, such as another library's page parameter: never reported. */
    protected fun ignore(vararg names: String) {
        names.mapTo(ignoredNames, ::normalizeName)
    }

    /** Name prefixes that are not the game's, such as `utm_` tracking parameters: never reported. */
    protected fun ignorePrefix(vararg prefixes: String) {
        prefixes.mapTo(ignoredPrefixes) { it.lowercase() }
    }

    /**
     * The values in [parameters], each key with its values as `LaunchParameters.toMap()` gives them.
     *
     * On desktop pass the `main` [arguments] too, so that a value written after a space, as in `--debug a,b`,
     * is reported with what to write instead (`--debug=a,b`) rather than dropped. Leave them out when the game
     * takes positional arguments of its own. [development] says whether this build reads
     * [LaunchOption.development] options; a release build reports and ignores them.
     */
    fun parse(
        parameters: Map<String, List<String>>,
        arguments: List<String> = emptyList(),
        development: Boolean = false,
    ): LaunchValues = LaunchParse(this, declared, development).run(parameters, arguments)

    /** Every option at its default, for a host with no parameters and for tests. */
    fun defaults(): LaunchValues = parse(emptyMap())

    /** The help text, one entry per option, for `--help` on desktop or a debug page. Secret defaults are hidden. */
    fun describe(): String = buildString {
        for (option in declared) {
            if (isNotEmpty()) append('\n')
            append("--").append(option.name)
            append(if (option.type.needsValue) "=<${option.type.name}>" else "[=<${option.type.name}>]")
            option.bounds?.takeIf { it != option.type.name }?.let { append("  ").append(it) }
            if (option.development) append("  (development)")
            if (option.description.isNotEmpty()) append("\n    ").append(option.description)
            append("\n    default: ").append(if (option.secret) "(secret)" else option.formatDefault())
            if (option.aliases.isNotEmpty()) append("; also: ").append(option.aliases.joinToString())
        }
    }

    internal fun find(name: String): LaunchOption<*>? = names[normalizeName(name)]

    internal fun isIgnored(name: String): Boolean =
        normalizeName(name) in ignoredNames || ignoredPrefixes.any { name.lowercase().startsWith(it) }
}

private fun <T : Any> LaunchOption<T>.formatDefault(): String = type.format(default).ifEmpty { "(none)" }
