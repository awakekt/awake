/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build

// Reads a settings file's `repositories { … }` blocks as text, for the repository tests (#1592).
// Comment lines are dropped first, so a commented-out repository is not counted.

/** Each `repositories { … }` block's entries, in order, as the name each top-level entry calls. */
internal fun repositoryBlocks(source: String): List<List<String>> {
    val code = source.lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
    return Regex("""\brepositories\s*\{""").findAll(code).map { match ->
        var depth = 1
        var i = match.range.last + 1
        val entries = mutableListOf<String>()
        var atStatement = true
        while (i < code.length && depth > 0) {
            val c = code[i]
            when {
                c == '{' || c == '(' -> depth++
                c == '}' || c == ')' -> depth--
                c == '\n' -> if (depth == 1) atStatement = true
                depth == 1 && atStatement && c.isLetter() -> {
                    entries += Regex("""[A-Za-z]+""").find(code, i)!!.value
                    atStatement = false
                }
            }
            i++
        }
        entries
    }.toList()
}

/** Every `repositories` block's entries, in order, each as its name and its whole text. */
internal fun repositoryEntries(source: String): List<List<Pair<String, String>>> {
    val code = source.lineSequence().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
    return Regex("""\brepositories\s*\{""").findAll(code).map { open ->
        val entries = mutableListOf<Pair<String, String>>()
        var depth = 1
        var i = open.range.last + 1
        var start = -1
        while (i < code.length && depth > 0) {
            val c = code[i]
            if (depth == 1 && start < 0 && c.isLetter()) start = i
            when (c) {
                '{', '(' -> depth++
                '}', ')' -> depth--
                '\n' -> if (depth == 1 && start >= 0) {
                    val text = code.substring(start, i).trim()
                    entries += Regex("""[A-Za-z]+""").find(text)!!.value to text
                    start = -1
                }
            }
            i++
        }
        entries.toList()
    }.toList()
}
