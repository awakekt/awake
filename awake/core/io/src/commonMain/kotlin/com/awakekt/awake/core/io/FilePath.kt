/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

/** A normalized path relative to a [FileSystem]'s root. An empty value represents the root. */
value class FilePath private constructor(val value: String) {
    companion object {
        val Root: FilePath = FilePath("")

        /** Creates a safe root-relative path, rejecting absolute paths and traversal. */
        fun of(raw: String): FilePath {
            require(raw.isNotBlank()) { "File path must not be blank; use FilePath.Root for the root." }
            return normalize(raw)
        }

        private fun normalize(raw: String): FilePath {
            val candidate = raw.replace('\\', '/')
            require(!candidate.startsWith('/')) { "Absolute paths are not allowed: $raw" }
            require(!candidate.matches(Regex("^[A-Za-z]:.*"))) { "Drive paths are not allowed: $raw" }

            val parts = candidate.split('/')
            val normalized = buildList {
                for (part in parts) {
                    when (part) {
                        "", "." -> Unit
                        ".." -> throw IllegalArgumentException("Path traversal is not allowed: $raw")
                        else -> add(part)
                    }
                }
            }.joinToString("/")
            require(normalized.isNotEmpty()) { "Path resolves to the root; use FilePath.Root." }
            return FilePath(normalized)
        }
    }

    override fun toString(): String = if (value.isEmpty()) "." else value
}

/** A logical asset key shared by bundled and project-backed asset sources. */
value class AssetPath(val value: String) {
    fun asFilePath(): FilePath = FilePath.of(value)

    override fun toString(): String = value
}

/** Normalizes a child asset reference relative to a containing asset path. */
fun AssetPath.resolve(child: String): AssetPath {
    if (child.startsWith("data:")) return AssetPath(child)
    val base = value.substringBeforeLast('/', missingDelimiterValue = "")
    val parts = listOf(base, child).filter { it.isNotEmpty() }
        .joinToString("/")
        .replace('\\', '/')
        .split('/')
    val resolved = ArrayDeque<String>()
    parts.forEach { part ->
        when (part) {
            "", "." -> Unit
            ".." -> if (resolved.isNotEmpty()) resolved.removeLast() else throw IllegalArgumentException("Asset path escaped its root: $child")
            else -> resolved.addLast(part)
        }
    }
    return AssetPath(FilePath.of(resolved.joinToString("/")).value)
}
