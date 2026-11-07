/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The string-keys and version-stamp plugins, run in a real build outside git and in a git checkout. */
class CodegenPluginsTest {
    @Test
    fun stringKeysGeneratesTheObjectAndReusesTheConfigurationCache() {
        project(
            """
            plugins { id("com.awakekt.awake.plugin.string-keys") }
            stringKeys { packageName.set("com.example") }
            """,
        ) { dir ->
            File(dir, "src/commonMain/strings").mkdirs()
            File(dir, "src/commonMain/strings/en.json").writeText("""{"menu": {"start": "Start"}}""")
            val first = run(dir, "generateStringKeys", "--configuration-cache")
            assertEquals(TaskOutcome.SUCCESS, first.task(":generateStringKeys")?.outcome)
            val generated = File(dir, "build/generated/awake/stringKeys/com/example/Strings.kt").readText()
            assertContains(generated, "StringKey(\"menu.start\")")

            val second = run(dir, "generateStringKeys", "--configuration-cache")
            assertContains(second.output, "Reusing configuration cache")
            assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateStringKeys")?.outcome)

            File(dir, "src/commonMain/strings/en.json").writeText("""{"menu": "x", "menu.start": "y"}""")
            val failed = GradleRunner.create().withProjectDir(dir).withPluginClasspath()
                .withArguments("generateStringKeys", "--max-workers=2").buildAndFail()
            assertContains(failed.output, "en.json")
            assertContains(failed.output, "key and also a group")
        }
    }

    @Test
    fun versionStampFallsBackToUnknownOutsideGit() {
        project(stampBuild()) { dir ->
            val first = run(dir, "generateVersionStamp", "--configuration-cache")
            assertEquals(TaskOutcome.SUCCESS, first.task(":generateVersionStamp")?.outcome)
            val generated = File(dir, "build/generated/awake/versionStamp/com/example/VersionStamp.kt").readText()
            assertContains(generated, "VERSION: String = \"1.0.0-rc.2\"")
            assertContains(generated, "COMMIT: String = \"unknown\"")
            assertContains(run(dir, "generateVersionStamp", "--configuration-cache").output, "Reusing configuration cache")
        }
    }

    @Test
    fun versionStampReadsTheCommitInAGitCheckout() {
        project(stampBuild()) { dir ->
            if (!git(dir, "init", "-q")) return@project
            git(dir, "-c", "user.name=t", "-c", "user.email=t@example.com", "commit", "-q", "--allow-empty", "-m", "c")
            run(dir, "generateVersionStamp", "--configuration-cache")
            val sha = ProcessBuilder("git", "rev-parse", "--short", "HEAD").directory(dir).start()
                .inputStream.bufferedReader().readText().trim()
            assertTrue(sha.isNotEmpty())
            assertContains(File(dir, "build/generated/awake/versionStamp/com/example/VersionStamp.kt").readText(), sha)
        }
    }

    private fun stampBuild() = """
        plugins { id("com.awakekt.awake.plugin.version-stamp") }
        version = "1.0.0-rc.2"
        versionStamp { packageName.set("com.example") }
    """

    private fun git(dir: File, vararg args: String): Boolean = runCatching {
        ProcessBuilder("git", *args).directory(dir).redirectErrorStream(true).start().also { it.inputStream.readBytes() }
            .waitFor() == 0
    }.getOrDefault(false)

    private fun project(build: String, body: (File) -> Unit) {
        val dir = createTempDirectory("codegen-plugin").toFile()
        try {
            File(dir, "settings.gradle.kts").writeText("rootProject.name = \"codegen-test\"\n")
            File(dir, "build.gradle.kts").writeText(build.trimIndent() + "\n")
            body(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun run(dir: File, vararg arguments: String): BuildResult =
        GradleRunner.create().withProjectDir(dir).withPluginClasspath()
            .withArguments(*arguments, "--stacktrace", "--max-workers=2").build()
}
