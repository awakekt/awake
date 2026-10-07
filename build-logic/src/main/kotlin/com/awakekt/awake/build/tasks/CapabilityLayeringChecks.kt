/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

/** The path prefix of the scene modules, `:awake:scene:<name>`, that a capability must not reach. */
internal const val SCENE_MODULE_PREFIX = ":awake:scene:"

/**
 * Whether a dependency configuration named [name] declares what **main** code compiles or runs with.
 *
 * Awake is a library first and `awake:scene:*` is the wrapper that binds capabilities into the ECS
 * scene graph and the scene document, so a capability's main code must not depend on it. Tests may: an
 * integration test that loads a scene document is not a dependency of the library.
 *
 * The declaring configurations are the ones a build file writes to, under any source set:
 * `api`, `implementation`, `compileOnly`, `runtimeOnly`, `commonMainApi`, `desktopMainImplementation`.
 * Test ones (`testImplementation`, `commonTestImplementation`, `androidHostTestImplementation`) are not,
 * and neither are the resolvable and consumable ones Gradle derives from them (`desktopCompileClasspath`,
 * `desktopApiElements`, `commonMainResolvableDependenciesMetadata`), which declare nothing of their own.
 */
internal fun isMainDependencyConfiguration(name: String): Boolean {
    if (name.startsWith("test") || name.contains("Test")) return false
    return MAIN_CONFIGURATIONS.any { name == it || name.endsWith(it.replaceFirstChar(Char::uppercaseChar)) }
}

private val MAIN_CONFIGURATIONS = listOf("api", "implementation", "compileOnly", "runtimeOnly")

/** The project paths a module's [configurations], name to the project paths declared in it, give its main code. */
internal fun mainProjectDependencies(configurations: Map<String, List<String>>): Set<String> =
    configurations.filterKeys(::isMainDependencyConfiguration).values.flatten().toSortedSet()

/**
 * The scene modules [module] reaches through the main dependencies in [graph], module path to the module
 * paths its main code depends on, each with the shortest chain that reaches it, [module] first.
 *
 * A dependency is followed whatever it is, so a capability that depends on another module that depends
 * on a scene module is found too: it cannot be used without the scene layer either. The walk stops at a
 * scene module, so a chain names the first scene module on its way and nothing behind it.
 */
internal fun sceneModulesReached(graph: Map<String, Set<String>>, module: String): Map<String, List<String>> {
    val reached = LinkedHashMap<String, List<String>>()
    val visited = hashSetOf(module)
    val pending = ArrayDeque<List<String>>().apply { addLast(listOf(module)) }
    while (pending.isNotEmpty()) {
        val chain = pending.removeFirst()
        for (next in graph[chain.last()].orEmpty().sorted()) {
            if (!visited.add(next)) continue
            val extended = chain + next
            if (next.startsWith(SCENE_MODULE_PREFIX)) reached[next] = extended else pending.addLast(extended)
        }
    }
    return reached
}

/**
 * The 1-based lines of [source], a Kotlin file, that name something in an `awake.scene` package: an
 * import or a fully qualified reference.
 *
 * This catches what a project dependency would not: a scene type that reaches a capability's main code
 * through a published `com.awakekt.awake.scene:*` coordinate, or through anything else on its classpath.
 * Comments and string literals are skipped, so a KDoc link to a scene type is not a dependency.
 */
internal fun sceneReferenceLines(source: String): List<Int> =
    kotlinCodeOnly(source).lineSequence()
        .withIndex()
        .filter { (_, line) -> SCENE_REFERENCE.containsMatchIn(line) }
        .map { it.index + 1 }
        .toList()

private val SCENE_REFERENCE = Regex("""\bcom\.awakekt\.awake\.scene\.""")

/**
 * [source] with its comments, string literals and character literals blanked out. Line breaks are kept,
 * so a line number in the result is a line number in [source].
 */
@Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
internal fun kotlinCodeOnly(source: String): String {
    val out = StringBuilder(source.length)
    var i = 0
    fun skip(until: Int) {
        for (at in i until minOf(until, source.length)) if (source[at] == '\n') out.append('\n')
        i = minOf(until, source.length)
    }
    while (i < source.length) {
        when {
            source.startsWith("/*", i) -> skip(blockCommentEnd(source, i))
            source.startsWith("//", i) -> skip(source.indexOf('\n', i).let { if (it < 0) source.length else it })
            source.startsWith("\"\"\"", i) -> skip(rawStringEnd(source, i))
            source[i] == '"' || source[i] == '\'' -> skip(quotedEnd(source, i, source[i]))
            else -> out.append(source[i++])
        }
    }
    return out.toString()
}

/** Where the block comment opening at [start] ends. Kotlin block comments nest. */
private fun blockCommentEnd(source: String, start: Int): Int {
    var depth = 0
    var i = start
    while (i < source.length) {
        when {
            source.startsWith("/*", i) -> {
                depth++
                i += 2
            }
            source.startsWith("*/", i) -> {
                depth--
                i += 2
                if (depth == 0) return i
            }
            else -> i++
        }
    }
    return source.length
}

/** Where the raw string opening at [start] ends, including any quotes it ends with (`""""`). */
private fun rawStringEnd(source: String, start: Int): Int {
    val close = source.indexOf("\"\"\"", start + 3)
    if (close < 0) return source.length
    var end = close + 3
    while (end < source.length && source[end] == '"') end++
    return end
}

/** Where the string or character literal opening at [start] with [quote] ends: its closing quote, or its line's end. */
private fun quotedEnd(source: String, start: Int, quote: Char): Int {
    var i = start + 1
    while (i < source.length && source[i] != '\n') {
        when (source[i]) {
            '\\' -> i += 2
            quote -> return i + 1
            else -> i++
        }
    }
    return i
}

/**
 * What is wrong with how [sceneCouplings], module path to the reasons it depends on the scene layer,
 * sits against the recorded [debt].
 *
 * A module with a reason that is not in [debt] is new debt and fails. A module in [debt] with no reason
 * left fails too, so the ledger only shrinks: paying a debt means deleting its line.
 */
internal fun capabilityLayeringFailures(
    sceneCouplings: Map<String, List<String>>,
    debt: Set<String>,
): List<String> = buildList {
    sceneCouplings.toSortedMap().forEach { (module, reasons) ->
        if (reasons.isNotEmpty() && module !in debt) {
            add(
                "$module depends on the scene layer in main code: ${summarize(reasons)}. A capability must not " +
                    "depend on a scene module: put the capability in a module of its own and bind it from " +
                    "awake:scene:<name> (see awake/scene/README.md). If this is debt that already existed, it " +
                    "belongs in capabilityLayeringDebt with the reason.",
            )
        }
    }
    debt.sorted().forEach { module ->
        if (sceneCouplings[module].isNullOrEmpty()) {
            add(
                "$module is listed in capabilityLayeringDebt but its main code no longer depends on the scene " +
                    "layer. Remove it from the list.",
            )
        }
    }
}

/** [reasons] in one line, the first few in full and the rest counted, so a large module stays readable. */
private fun summarize(reasons: List<String>): String {
    val shown = reasons.take(REASONS_SHOWN).joinToString("; ")
    val rest = reasons.size - REASONS_SHOWN
    return if (rest > 0) "$shown; and $rest more" else shown
}

private const val REASONS_SHOWN = 3
