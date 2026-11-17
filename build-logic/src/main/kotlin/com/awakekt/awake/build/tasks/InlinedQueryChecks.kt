/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

/**
 * The 1-based lines of [source], a Kotlin file, that hand an ECS query a function object instead of a
 * lambda: `world.queryEach(Type::class, track)`, or `family<A>().forEach(track)`.
 *
 * Both are inline. A lambda written at the call is inlined into the query's loop, so the `Entity`
 * value class it receives stays a plain `Long`. A function object is called through its generic
 * `invoke`, which boxes every `Entity`, every frame (#1742). A desktop JVM's escape analysis removes
 * that box once the loop is warm, so no allocation test sees it; Android's runtime pays it. Comments
 * and string literals are skipped, and so are the functions' own declarations. An inline function
 * forwarding its own function parameter, as `World.queryEach` does to `Family.forEach`, is inlined in
 * turn, so that is not a site either.
 */
internal fun functionObjectQueryLines(source: String): List<Int> {
    val code = kotlinCodeOnly(source)
    return (QUERY_EACH.findAll(code) + FAMILY_FOR_EACH.findAll(code))
        .filterNot { declares(code, it.range.first) }
        .filter { passesFunctionObject(code, it) }
        .filterNot { forwardsInlineParameter(code, lastArgument(it.groupValues[1])) }
        .map { match -> code.substring(0, match.range.first).count { it == '\n' } + 1 }
        .sorted()
        .toList()
}

/** Whether the match at [start] is a declaration, `fun <A : Any> queryEach(`, not a call. */
private fun declares(code: String, start: Int): Boolean {
    val lineStart = code.lastIndexOf('\n', start - 1) + 1
    return DECLARATION.containsMatchIn(code.substring(lineStart, start))
}

/**
 * Whether the call [match] covers passes a function as its last argument: no trailing lambda after
 * its parentheses, and a last argument that is neither a class literal nor a lambda inside them.
 */
private fun passesFunctionObject(code: String, match: MatchResult): Boolean {
    val after = code.substring(match.range.last + 1).trimStart()
    if (after.startsWith("{")) return false
    // A lambda inside the parentheses holds commas of its own, so its presence settles it.
    if ('{' in match.groupValues[1]) return false
    val last = lastArgument(match.groupValues[1])
    return last.isNotEmpty() && !last.endsWith("::class")
}

/** The last of [arguments], a trailing comma allowed. */
private fun lastArgument(arguments: String): String = arguments.trim().removeSuffix(",").substringAfterLast(',').trim()

/** Whether [argument] names a function-typed parameter of an `inline fun` in [code], which is inlined too. */
private fun forwardsInlineParameter(code: String, argument: String): Boolean =
    IDENTIFIER.matches(argument) &&
        Regex("""\binline\s+fun\b[^{=]*\b${Regex.escape(argument)}\s*:\s*(?:suspend\s*)?\(""").containsMatchIn(code)

private val IDENTIFIER = Regex("""[A-Za-z_]\w*""")

// The argument list may span lines but holds no parentheses of its own: class literals and names.
private val QUERY_EACH = Regex("""\bqueryEach\s*(?:<[^<>()]*(?:<[^<>()]*>[^<>()]*)*>)?\s*\(([^()]*)\)""")
private val FAMILY_FOR_EACH = Regex("""\bfamily\s*(?:<[^<>()]*>)?\s*\([^()]*\)\s*\.forEach\s*\(([^()]*)\)""")
private val DECLARATION = Regex("""\bfun\b""")

/** What fails the check: each file of [sites], repository-relative path to its lines, and where. */
internal fun inlinedQueryFailures(sites: Map<String, List<Int>>): List<String> =
    sites.flatMap { (path, lines) ->
        lines.map { line ->
            "$path:$line hands an ECS query a function object; write the lambda at the call, so the " +
                "inline query inlines it and no Entity is boxed per entity per frame"
        }
    }
