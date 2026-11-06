/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

/**
 * The 1-based lines of [source], a Kotlin file, that construct a coroutine scope: a call to the
 * `CoroutineScope(...)` or `MainScope()` factory, plain or fully qualified.
 *
 * A scope built this way has a lifetime of its own, which nothing above it cancels and whose failures
 * reach no app error handler, so only an approved owner builds one; everything else runs as a child of
 * an owner. Comments and string literals are skipped, so a KDoc that names the factory is not a site.
 * A type (`scope: CoroutineScope`), a supertype (`: CoroutineScope`), an extension receiver
 * (`fun CoroutineScope.x()`) and a declaration of a function by that name are not calls either.
 */
internal fun scopeConstructionLines(source: String): List<Int> = matchingLines(source, SCOPE_CONSTRUCTION)

/**
 * The 1-based lines of [source] that name `GlobalScope`, an import included. Its coroutines outlive
 * every owner, so it has no place in Awake and no baseline: each use fails.
 */
internal fun globalScopeLines(source: String): List<Int> = matchingLines(source, GLOBAL_SCOPE)

private fun matchingLines(source: String, pattern: Regex): List<Int> =
    kotlinCodeOnly(source).lineSequence()
        .withIndex()
        .flatMap { (index, line) -> pattern.findAll(line).map { index + 1 } }
        .toList()

// Not after a `.` or a word character, so `other.CoroutineScope(` and `MyCoroutineScope(` are not it,
// and not after `fun `, so declaring a function of that name is not a call to it.
private val SCOPE_CONSTRUCTION = Regex("""(?<![\w.])(?<!fun\s)(?:kotlinx\.coroutines\.)?(?:CoroutineScope|MainScope)\s*\(""")
private val GLOBAL_SCOPE = Regex("""(?<![\w.])(?:kotlinx\.coroutines\.)?GlobalScope\b""")

/**
 * The baseline of [text]: each non-blank, non-`#` line is `<repository-relative path> <count>`, the
 * number of scope constructions that file is still allowed while it waits to be migrated.
 */
internal fun parseScopeBaseline(text: String): Map<String, Int> =
    text.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .associate { line ->
            val parts = line.split(Regex("\\s+"))
            val count = parts.getOrNull(1)?.toIntOrNull()
            require(parts.size == 2 && count != null && count > 0) {
                "Coroutine scope baseline line '$line' is not '<path> <count>' with a positive count."
            }
            parts[0] to count
        }

/**
 * What fails the coroutine scope check.
 *
 * - A file of [sites], repository-relative path to the lines that construct a scope outside an
 *   approved owner, with more sites than [baseline] allows it: a new unowned scope.
 * - A [baseline] entry that allows more than the file has left, its file gone included, so the
 *   baseline only ever shrinks: lower it or delete the line once a site is migrated.
 * - Any [globalScope] use, which has no baseline.
 * - An approved owner in [missingOwners] whose file no longer exists, so the list stays true.
 */
internal fun coroutineScopeFailures(
    sites: Map<String, List<Int>>,
    baseline: Map<String, Int>,
    globalScope: Map<String, List<Int>>,
    missingOwners: Collection<String> = emptyList(),
): List<String> = buildList {
    (sites.keys + baseline.keys).toSortedSet().forEach { path ->
        val lines = sites[path].orEmpty()
        val allowed = baseline[path] ?: 0
        when {
            lines.size > allowed -> add(
                "$path:${lines.joinToString(",")} constructs ${lines.size} coroutine scope(s) where the baseline " +
                    "allows $allowed. Only an approved owner (AppCoroutineScope, SceneSession, a core:state store, " +
                    "the net:server-ktor host) builds a scope: run the work in a child of one, such as " +
                    "SceneSession.coroutineScope or AppServiceLookup.appChildScope().",
            )
            lines.size < allowed -> add(
                "$path: the baseline allows $allowed coroutine scope(s) but ${lines.size} remain. Lower its line in " +
                    "the baseline, or delete it when none remain, so it cannot grow back.",
            )
        }
    }
    globalScope.toSortedMap().forEach { (path, lines) ->
        if (lines.isNotEmpty()) {
            add("$path:${lines.joinToString(",")} uses GlobalScope. Launch in a child of an approved owner instead.")
        }
    }
    missingOwners.sorted().forEach { add("$it is listed as an approved coroutine scope owner but does not exist.") }
}
