/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

/**
 * Finds `data class` declarations in [sources] whose primary constructor has at least one `var`
 * property. Mutating such a property changes `hashCode()` and `equals()` after insertion into a
 * hash-based `Map` or `Set`, stranding the entry and causing silent misses or duplicate leaks.
 */
internal fun mutableDataClassNames(sources: Collection<String>): Set<String> = buildSet {
    for (source in sources) {
        val code = kotlinCodeOnly(source)
        for (match in DATA_CLASS_HEADER.findAll(code)) {
            val name = match.groupValues[1]
            val params = primaryConstructorBody(code, match.range.last) ?: continue
            if (VAR_PARAM.containsMatchIn(params)) add(name)
        }
    }
}

/**
 * The 1-based line numbers and key type names in [source] where a `Map` or `Set` uses a key type
 * listed in [mutableDataClasses]. Comments and string literals are stripped before scanning.
 */
internal fun mutableMapKeySites(source: String, mutableDataClasses: Set<String>): List<Pair<Int, String>> {
    if (mutableDataClasses.isEmpty()) return emptyList()
    return kotlinCodeOnly(source).lineSequence()
        .withIndex()
        .flatMap { (index, line) ->
            HASH_COLLECTION_KEY.findAll(line)
                .map { it.groupValues[1] }
                .filter { it in mutableDataClasses }
                .map { key -> (index + 1) to key }
        }
        .toList()
}

/**
 * Parses `<repository-relative path> <count>` lines from [text], ignoring blank lines and `#`
 * comments. Each entry is the number of baselined mutable-key map/set sites still allowed in that
 * file while awaiting migration; the baseline can only shrink.
 */
internal fun parseMutableMapKeyBaseline(text: String): Map<String, Int> =
    text.lineSequence()
        .map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .associate { line ->
            val parts = line.split(Regex("\\s+"))
            val count = parts.getOrNull(1)?.toIntOrNull()
            require(parts.size == 2 && count != null && count > 0) {
                "Mutable map-key baseline line '$line' is not '<path> <count>' with a positive count."
            }
            parts[0] to count
        }

/**
 * Reports new mutable `data class` map/set key sites above [baseline], and stale [baseline] entries
 * that allow more sites than remain so the baseline only shrinks.
 */
internal fun mutableMapKeyFailures(
    sites: Map<String, List<Pair<Int, String>>>,
    baseline: Map<String, Int>,
): List<String> = buildList {
    (sites.keys + baseline.keys).toSortedSet().forEach { path ->
        val found = sites[path].orEmpty()
        val allowed = baseline[path] ?: 0
        when {
            found.size > allowed -> {
                val detail = found.joinToString(", ") { (line, key) -> "line $line ($key)" }
                add(
                    "$path: found ${found.size} map/set site(s) keyed by a mutable data class " +
                        "($detail) where the baseline allows $allowed. Key by Entity or an immutable ID " +
                        "instead: mutating a primary-constructor var shifts hashCode() after insertion.",
                )
            }
            found.size < allowed -> add(
                "$path: the baseline allows $allowed mutable map-key site(s) but ${found.size} remain. " +
                    "Lower its line in config/mutable-map-keys-baseline.txt, or delete it when none remain.",
            )
        }
    }
}

private fun primaryConstructorBody(code: String, openParenIndex: Int): String? {
    if (openParenIndex !in code.indices || code[openParenIndex] != '(') return null
    var depth = 0
    for (index in openParenIndex until code.length) {
        when (code[index]) {
            '(' -> depth++
            ')' -> {
                depth--
                if (depth == 0) return code.substring(openParenIndex + 1, index)
            }
        }
    }
    return null
}

private val DATA_CLASS_HEADER = Regex(
    """\bdata\s+class\s+([A-Z][A-Za-z0-9_]*)\s*(?:<[^>]*>)?\s*(?:(?:public|internal|private|protected)\s+constructor\s*)?\(""",
)
private val VAR_PARAM = Regex("""\bvar\s+[a-zA-Z_]\w*""")
private val HASH_COLLECTION_KEY = Regex(
    """\b(?:HashMap|LinkedHashMap|MutableMap|Map|mutableMapOf|linkedMapOf|hashMapOf|HashSet|LinkedHashSet|MutableSet|Set|mutableSetOf|linkedSetOf|hashSetOf)\s*<\s*([A-Z][A-Za-z0-9_]*)\b""",
)
