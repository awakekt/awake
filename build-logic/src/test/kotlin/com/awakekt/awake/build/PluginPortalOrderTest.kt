/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every build lists the Gradle Plugin Portal last (#1592).
 *
 * Gradle tries repositories in order and stops at one that fails to answer, so the Portal listed
 * first failed builds whose plugins Maven Central serves: `com.vanniktech.maven.publish` 0.37.0, in
 * CI runs whose Portal returned an error. Listed last it is asked only for what no other repository
 * holds.
 */
class PluginPortalOrderTest {
    private val root = File("..")

    @Test
    fun everyRepositoriesBlockListsThePortalLast() {
        val misordered = SETTINGS.flatMap { path ->
            repositoryBlocks(File(root, path).readText()).mapIndexedNotNull { index, entries ->
                val portal = entries.indexOfFirst { it.startsWith("gradlePluginPortal") }
                if (portal >= 0 && portal != entries.lastIndex) "$path, repositories block ${index + 1}: $entries" else null
            }
        }
        assertEquals(emptyList(), misordered, "the Plugin Portal is listed before another repository")
    }

    @Test
    fun theCheckSeesThePortalWhereItIsListed() {
        val blocks = repositoryBlocks(
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    maven("https://example.org") { content { includeGroup("x") } }
                    google()
                }
            }
            """.trimIndent(),
        )
        assertEquals(listOf(listOf("gradlePluginPortal", "maven", "google")), blocks)
        assertTrue(SETTINGS.all { File(root, it).isFile }, "every listed settings file exists")
    }

    /** Each `repositories { … }` block's entries, in order, as the name each top-level entry calls. */
    private fun repositoryBlocks(source: String): List<List<String>> {
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

    private companion object {
        val SETTINGS = listOf(
            "settings.gradle.kts",
            "build-logic/settings.gradle.kts",
            "templates/game/settings.gradle.kts",
            "templates/plugin/settings.gradle.kts",
            "templates/project/settings.gradle.kts",
            "tools/scene-components-consumer/settings.gradle.kts",
        )
    }
}
