/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

/**
 * Finds source-level references that make a driver depend on render-runtime vocabulary.
 *
 * The check deliberately recognizes an import, a local alias and an exact qualified spelling.
 * Kotlin permits all three, and treating only imports as a boundary is how a backend can keep a
 * compile-time scene dependency while a layering task reports green. This remains a narrow source
 * check, not a Kotlin parser: Gradle dependency closure checks enforce module-level boundaries.
 */
internal fun findRenderRuntimeReferenceViolations(
    lines: List<String>,
    forbiddenSimpleNames: Collection<String>,
    forbiddenQualifiedNames: Collection<String>,
    forbiddenQualifiedPrefixes: Collection<String> = emptyList(),
): List<String> =
    buildList {
        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("//")) return@forEachIndexed
            val lineNumber = index + 1
            if (trimmed.startsWith("import ")) {
                val imported = trimmed.removePrefix("import ").substringBefore(" as ").trim()
                if (imported.substringAfterLast('.') in forbiddenSimpleNames) {
                    add("$lineNumber: imports ${imported.substringAfterLast('.')}")
                } else if (imported.endsWith(".*") && forbiddenQualifiedPrefixes.any { prefix ->
                        imported.removeSuffix("*").startsWith(prefix)
                    }) {
                    add("$lineNumber: imports forbidden render-runtime package")
                }
                return@forEachIndexed
            }

            val alias = TYPE_ALIAS.find(trimmed)?.groupValues?.get(1)
            when {
                alias != null && alias.substringAfterLast('.') in forbiddenSimpleNames ->
                    add("$lineNumber: aliases ${alias.substringAfterLast('.')}")
                forbiddenQualifiedNames.any { qualified -> trimmed.contains(qualified) } ||
                    forbiddenQualifiedPrefixes.any { prefix -> trimmed.contains(prefix) } ->
                    add("$lineNumber: references render-runtime vocabulary directly")
            }
        }
    }

private val TYPE_ALIAS = Regex("""\btypealias\s+[A-Za-z_][A-Za-z0-9_]*\s*=\s*([A-Za-z_][A-Za-z0-9_.]*)""")
