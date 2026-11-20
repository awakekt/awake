/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build

import kotlin.test.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every build lists Google's mirror of Maven Central right after Central (#1592).
 *
 * On 20 Nov a Central edge answered 404, for 9 minutes, for files Central serves: a plugin marker
 * POM on every retry. Gradle moves on to the next repository after a module's 404, so with the
 * mirror next the lookup succeeds there. Central stays first and the source of truth: the mirror is
 * asked only for what Central answered 404, and repositories are not part of a published POM or
 * module file.
 *
 * It can't help a file that 404s behind a POM Central served (that day's first miss, a klib):
 * Gradle asks for a file only from the repository that served its metadata. A retry with
 * `--refresh-dependencies` asks Central for the POM again (`scripts/gradle-retry.sh`): served, the
 * file is asked of Central again; answered 404, the POM and then the file come from the mirror.
 */
class CentralMirrorTest {
    private val root = File("..").canonicalFile

    @Test
    fun everyBuildListsTheMirrorRightAfterCentral() {
        val files = buildScripts() + TESTKIT_FIXTURES.map { File(root, it) }
        val missing = files.flatMap { file -> centralWithoutMirror(file.readText()).map { "${file.relativeTo(root)}: $it" } }
        assertEquals(emptyList(), missing, "a build lists mavenCentral() without the mirror right after it")
        assertTrue(files.size >= MIN_SCRIPTS, "the check reads every build script: ${files.size}")
    }

    @Test
    fun theCheckSeesAMissingOrMisplacedMirror() {
        val source = """
            pluginManagement {
                repositories {
                    mavenCentral { content { excludePortalOnly() } }
                    $MIRROR_ENTRY
                }
            }
            dependencyResolutionManagement {
                repositories {
                    $MIRROR_ENTRY
                    mavenCentral()
                    google()
                }
            }
            repositories { mavenCentral() }
        """.trimIndent()

        assertEquals(
            listOf("one-line block: repositories { mavenCentral() }", "repositories block 2: mavenCentral()"),
            centralWithoutMirror(source),
        )
    }

    @Test
    fun aMarkerCentralAnswers404ForResolvesFromTheMirror() {
        ProbePluginRepository.failing(NOT_FOUND).use { central ->
            ProbePluginRepository.serving().use { mirror ->
                val result = probeBuild(listOf(central.declaration, mirror.declaration)) { it.build() }

                assertTrue("PROBE APPLIED" in result.output, result.output)
                assertTrue(central.requests.get() > 0, "Central was asked first")
            }
        }
    }

    @Test
    fun withoutTheMirrorTheSame404FailsTheBuild() {
        ProbePluginRepository.failing(NOT_FOUND).use { central ->
            val result = probeBuild(listOf(central.declaration)) { it.buildAndFail() }

            assertTrue("was not found in any of the following sources" in result.output, result.output)
        }
    }

    /** The build scripts of every build here: settings and build files, outside build output. */
    private fun buildScripts(): List<File> = root.walkTopDown()
        .onEnter { it == root || (it.name !in SKIPPED_DIRS && !it.name.startsWith(".")) }
        .filter { it.isFile && it.name.endsWith(".gradle.kts") }
        .toList()

    private companion object {
        const val MIRROR = "https://maven-central.storage-download.googleapis.com/maven2/"
        const val MIRROR_ENTRY = """maven("$MIRROR") { name = "MavenCentralMirror" }"""
        const val NOT_FOUND = 404

        /** Root, build-logic, three templates and three tools, at least. */
        const val MIN_SCRIPTS = 9

        val SKIPPED_DIRS = setOf("build", "node_modules", "kotlin-js-store")

        /** TestKit tests that write a build resolving from Central. */
        val TESTKIT_FIXTURES = listOf(
            "build-logic/src/test/kotlin/com/awakekt/awake/build/SceneComponentsPluginTest.kt",
            "awake/project/cli/src/test/kotlin/com/awakekt/awake/project/cli/ProjectBundleTest.kt",
        )

        /** A `repositories { … }` block on one line, whose entries [repositoryEntries] does not see. */
        val ONE_LINE_BLOCK = Regex("""\brepositories\s*\{[^\n]*\bmavenCentral\b[^\n]*}""")

        /** Each `mavenCentral` entry of [source] not followed directly by the mirror. */
        fun centralWithoutMirror(source: String): List<String> {
            val oneLine = ONE_LINE_BLOCK.findAll(source).map { it.value }.filterNot { "maven(\"$MIRROR\")" in it }
            val multiLine = repositoryEntries(source).flatMapIndexed { block, entries ->
                entries.withIndex()
                    .filter { (index, entry) -> entry.first == "mavenCentral" && !entries.followedByMirror(index) }
                    .map { (_, entry) -> "repositories block ${block + 1}: ${entry.second.lineSequence().first()}" }
            }
            return oneLine.map { "one-line block: $it" }.toList() + multiLine
        }

        private fun List<Pair<String, String>>.followedByMirror(index: Int): Boolean =
            getOrNull(index + 1)?.second?.startsWith("""maven("$MIRROR")""") == true
    }
}
