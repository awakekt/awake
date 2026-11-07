/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.strings

/** A group of keys: [leaves] are keys at this level, [groups] are the nested levels, by identifier. */
class KeyTree private constructor(
    val leaves: Map<String, Entry>,
    val groups: Map<String, KeyTree>,
) {
    /** A key at one level of the tree, with its value for documentation. */
    class Entry(val path: KeyPath, val value: String)

    companion object {
        /**
         * Arranges [table] into a tree of identifiers. Fails, naming every case, when two keys land on
         * one identifier (`a-b` and `a_b` under escaping), when a key is both a leaf and a group
         * (`menu` and `menu.start`), or when a segment is in [reserved].
         */
        fun build(table: StringTable, reserved: Set<String> = emptySet()): KeyTree {
            val problems = mutableListOf<String>()
            val root = Node()
            for ((path, value) in table.entries) {
                path.segments.firstOrNull { it in reserved }?.let {
                    problems += "Key '${path.raw}' uses '$it', which the generated code reserves."
                }
                insert(root, path, value, problems)
            }
            require(problems.isEmpty()) {
                "The string keys do not map to identifiers:\n" + problems.joinToString("\n") { "  - $it" }
            }
            return root.freeze()
        }

        private fun insert(root: Node, path: KeyPath, value: String, problems: MutableList<String>) {
            var node = root
            for ((index, segment) in path.segments.withIndex()) {
                val raw = path.rawSegments[index]
                val first = node.spellings.getOrPut(segment) { raw }
                if (first != raw) {
                    problems += "Key '${path.raw}': '$raw' and '$first' both become '$segment'."
                    return
                }
                val isLast = index == path.segments.lastIndex
                val leaf = node.leaves[segment]
                when {
                    leaf != null && !isLast ->
                        return run { problems += "'${leaf.path.raw}' is a key and also a group (see '${path.raw}')." }
                    isLast && segment in node.groups ->
                        return run { problems += "'${path.raw}' is a key and also a group." }
                    isLast -> node.leaves[segment] = Entry(path, value)
                    else -> node = node.groups.getOrPut(segment) { Node() }
                }
            }
        }

        private class Node {
            val leaves = LinkedHashMap<String, Entry>()
            val groups = LinkedHashMap<String, Node>()
            val spellings = HashMap<String, String>()

            fun freeze(): KeyTree = KeyTree(leaves, groups.mapValues { it.value.freeze() })
        }
    }
}
