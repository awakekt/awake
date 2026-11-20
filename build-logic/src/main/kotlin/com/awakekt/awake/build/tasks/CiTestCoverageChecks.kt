/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

/**
 * What CI runs of a module's tests, read from the workflows' text: whether some command runs its
 * [task] whole, and which test classes any command names with `--tests` (#1592).
 *
 * Gradle applies each `--tests` to the task written just before it, so `:m:desktopTest --tests X`
 * runs X alone and `:m:desktopTest` with no `--tests` after it runs the whole suite. A class named
 * anywhere counts as run: the check is about tests that run nowhere, not about which step runs them.
 */
internal class CiTestRuns(workflows: List<String>, task: String) {
    /** Whether some command runs [task] with no `--tests` filter. */
    val wholeSuite: Boolean

    /** Every class name some `--tests` filter names, its package dropped. */
    val namedClasses: Set<String>

    init {
        var whole = false
        val named = HashSet<String>()
        for (text in workflows) {
            // A comment that mentions a task runs nothing.
            val commands = text.lineSequence().filterNot { it.trimStart().startsWith("#") }.joinToString("\n")
            val tokens = commands.split(WHITESPACE).filter { it.isNotEmpty() }
            for (i in tokens.indices) {
                if (tokens[i] == task && tokens.getOrNull(i + 1) != TESTS_FLAG) whole = true
                if (tokens[i] == TESTS_FLAG) tokens.getOrNull(i + 1)?.let { named += simpleName(it) }
            }
        }
        wholeSuite = whole
        namedClasses = named
    }

    private fun simpleName(filter: String): String = filter.trim('\'', '"').substringAfterLast('.').substringBefore('*')

    private companion object {
        const val TESTS_FLAG = "--tests"
        val WHITESPACE = Regex("\\s+")
    }
}

/**
 * The test classes of a gated module that no CI step runs: none when some step runs its suite whole,
 * else each of [testClasses] that no `--tests` filter names. [testClasses] are simple class names.
 */
internal fun unrunTestClasses(runs: CiTestRuns, testClasses: Collection<String>): List<String> =
    if (runs.wholeSuite) emptyList() else testClasses.filter { it !in runs.namedClasses }.sorted()

/** The simple name of the test class [source] declares, when it has a `@Test`; null otherwise. */
internal fun testClassName(fileName: String, source: String): String? =
    if (TEST_ANNOTATION.containsMatchIn(source)) fileName.removeSuffix(".kt") else null

private val TEST_ANNOTATION = Regex("""^\s*@Test\b""", RegexOption.MULTILINE)
