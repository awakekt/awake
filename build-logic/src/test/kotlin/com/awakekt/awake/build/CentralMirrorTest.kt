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
 * Every build lists Google's mirror of Maven Central right before Central, and keeps Awake's own
 * group off the mirror (#1592).
 *
 * Gradle moves on to the next repository only after a 404: a 429 or 5xx ends the lookup at the
 * repository that answers it. On 20 Nov Central answered these runners 429 for longer than every
 * retry, and wrongly 404 for files it serves. A mirror listed after Central can help with the 404s
 * only; listed first, it serves what Central would refuse, and what it lacks is a clean 404 that
 * moves on to Central. It carries no Awake release, so `com.awakekt` skips it. Repositories are not
 * part of a published POM or module file.
 */
class CentralMirrorTest {
    private val root = File("..").canonicalFile

    @Test
    fun everyBuildListsTheMirrorRightBeforeCentral() {
        val files = buildScripts() + TESTKIT_FIXTURES.map { File(root, it) }
        val missing = files.flatMap { file -> centralWithoutMirror(file.readText()).map { "${file.relativeTo(root)}: $it" } }
        assertEquals(emptyList(), missing, "a build lists mavenCentral() without the mirror, keeping com.awakekt off it, right before it")
        assertTrue(files.size >= MIN_SCRIPTS, "the check reads every build script: ${files.size}")
    }

    @Test
    fun theCheckSeesAMissingOrMisplacedMirror() {
        val source = """
            pluginManagement {
                repositories {
                    $MIRROR_ENTRY
                    mavenCentral { content { excludePortalOnly() } }
                }
            }
            dependencyResolutionManagement {
                repositories {
                    mavenCentral()
                    $MIRROR_ENTRY
                    google()
                }
            }
            repositories {
                maven("$MIRROR") { name = "MavenCentralMirror" }
                mavenCentral()
            }
            repositories { mavenCentral() }
        """.trimIndent()

        assertEquals(
            listOf(
                "one-line block: repositories { mavenCentral() }",
                "repositories block 2: mavenCentral()",
                "repositories block 3: mavenCentral()",
            ),
            centralWithoutMirror(source),
        )
    }

    @Test
    fun aPluginTheMirrorLacksResolvesFromCentralBehindIt() {
        ProbePluginRepository.failing(NOT_FOUND).use { mirror ->
            ProbePluginRepository.serving().use { central ->
                val result = probeBuild(listOf(mirror.declaration, central.declaration)) { it.build() }

                assertTrue("PROBE APPLIED" in result.output, result.output)
                assertTrue(mirror.requests.get() > 0, "the mirror was asked first")
            }
        }
    }

    @Test
    fun aThrottlingCentralBehindTheMirrorIsNeverAsked() {
        ProbePluginRepository.serving().use { mirror ->
            ProbePluginRepository.failing(TOO_MANY_REQUESTS).use { central ->
                val result = probeBuild(listOf(mirror.declaration, central.declaration)) { it.build() }

                assertTrue("PROBE APPLIED" in result.output, result.output)
                assertEquals(0, central.requests.get(), "Central was asked though the mirror served it")
            }
        }
    }

    @Test
    fun aThrottlingCentralFirstFailsTheBuildWithTheMirrorAfterIt() {
        ProbePluginRepository.failing(TOO_MANY_REQUESTS).use { central ->
            ProbePluginRepository.serving().use { mirror ->
                val result = probeBuild(listOf(central.declaration, mirror.declaration)) { it.buildAndFail() }

                // Gradle reports a plugin lookup that ended on an error as "not found", naming no status.
                assertTrue("was not found in any of the following sources" in result.output, result.output)
                assertTrue(central.requests.get() > 0, "Central was asked first")
                assertEquals(0, mirror.requests.get(), "a 429 moved on to the mirror")
            }
        }
    }

    @Test
    fun theCiInitScriptPutsTheMirrorBeforeCentralInABuildThatListsOnlyCentral() {
        ProbePluginRepository.failing(TOO_MANY_REQUESTS).use { central ->
            ProbePluginRepository.serving().use { mirror ->
                // Named as `mavenCentral()` is, which is how the script finds Central.
                val onlyCentral = central.declaration.replaceFirst("maven { ", "maven { name = \"MavenRepo\"; ")
                val result = probeBuild(listOf(onlyCentral)) {
                    it.withArguments("probe", "--init-script", File(root, INIT_SCRIPT).path, "-Pawake.centralMirror=${mirror.url}").build()
                }

                assertTrue("PROBE APPLIED" in result.output, result.output)
                assertEquals(0, central.requests.get(), "the throttling Central was asked before the mirror")
            }
        }
    }

    /** The build scripts of every build here: settings and build files, outside build output. */
    private fun buildScripts(): List<File> = root.walkTopDown()
        .onEnter { it == root || (it.name !in SKIPPED_DIRS && !it.name.startsWith(".")) }
        .filter { it.isFile && it.name.endsWith(".gradle.kts") }
        .toList()

    private companion object {
        const val MIRROR = "https://maven-central.storage-download.googleapis.com/maven2/"
        const val MIRROR_ENTRY = """maven("$MIRROR") { name = "MavenCentralMirror"; content { excludeGroupAndSubgroups("com.awakekt") } }"""
        const val NOT_FOUND = 404
        const val TOO_MANY_REQUESTS = 429

        /** What CI installs in the Plugins consumer job's Gradle User Home, for builds that don't list the mirror. */
        const val INIT_SCRIPT = "scripts/central-mirror-before.init.gradle.kts"

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

        /** Each `mavenCentral` entry of [source] not right after the mirror, with `com.awakekt` kept off it. */
        fun centralWithoutMirror(source: String): List<String> {
            val oneLine = ONE_LINE_BLOCK.findAll(source).map { it.value }.filterNot { it.mirrorBeforeCentral() }
            val multiLine = repositoryEntries(source).flatMapIndexed { block, entries ->
                entries.withIndex()
                    .filter { (index, entry) -> entry.first == "mavenCentral" && !entries.afterMirror(index) }
                    .map { (_, entry) -> "repositories block ${block + 1}: ${entry.second.lineSequence().first()}" }
            }
            return oneLine.map { "one-line block: $it" }.toList() + multiLine
        }

        private fun String.mirrorBeforeCentral(): Boolean {
            val mirror = indexOf("""maven("$MIRROR")""")
            return mirror >= 0 && mirror < indexOf("mavenCentral") && KEEPS_AWAKE_OFF in this
        }

        private fun List<Pair<String, String>>.afterMirror(index: Int): Boolean =
            getOrNull(index - 1)?.second?.let { it.startsWith("""maven("$MIRROR")""") && KEEPS_AWAKE_OFF in it } == true

        const val KEEPS_AWAKE_OFF = """excludeGroupAndSubgroups("com.awakekt")"""
    }
}
