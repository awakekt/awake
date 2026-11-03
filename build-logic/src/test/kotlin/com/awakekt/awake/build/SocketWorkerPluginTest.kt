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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A tiny wasmJs project that depends, from a local Maven repository, on a stand-in for the published
 * `net:client-browser` module: Gradle module metadata that redirects to its wasm-js variant, and a klib
 * (a zip) that holds `awake-net/socket-worker.js`, as the real one does.
 */
class SocketWorkerPluginTest {
    private val script = File("../awake/net/client-browser/src/wasmJsMain/resources/awake-net/socket-worker.js")

    @Test
    fun theScriptLandsInTheWebResourcesWithTheConfigurationCache() {
        withProject(klib = klibWith(scriptBytes())) { dir ->
            val first = run(dir, "wasmJsProcessResources", "--configuration-cache")
            assertEquals(TaskOutcome.SUCCESS, first.task(":extractSocketWorker")?.outcome)
            val processed = File(dir, "build/processedResources/wasmJs/main/awake-net/socket-worker.js")
            assertContentEquals(scriptBytes(), processed.readBytes(), "the script is beside the page")

            val second = run(dir, "wasmJsProcessResources", "--configuration-cache")
            assertContains(second.output, "Reusing configuration cache")
            assertEquals(TaskOutcome.UP_TO_DATE, second.task(":extractSocketWorker")?.outcome)
        }
    }

    @Test
    fun theDevelopmentServerAndTheProductionDistributionWaitForIt() {
        withProject(klib = klibWith(scriptBytes())) { dir ->
            listOf("wasmJsBrowserDevelopmentRun", "wasmJsBrowserDistribution").forEach { task ->
                val plan = run(dir, task, "--dry-run").output
                assertContains(plan, ":extractSocketWorker SKIPPED", message = "$task runs it first: $plan")
            }
        }
    }

    @Test
    fun aProjectWithoutTheClientGetsNothing() {
        withProject(klib = null) { dir ->
            val result = run(dir, "wasmJsProcessResources", "--configuration-cache")
            assertEquals(TaskOutcome.NO_SOURCE, result.task(":extractSocketWorker")?.outcome)
            assertFalse(File(dir, "build/processedResources/wasmJs/main/awake-net").exists())
        }
    }

    @Test
    fun aKlibWithoutTheScriptFailsTheBuildNamingTheFix() {
        withProject(klib = klibWith(null)) { dir ->
            val result = GradleRunner.create().withProjectDir(dir).withPluginClasspath()
                .withArguments("wasmJsProcessResources", "--stacktrace", "--max-workers=2").buildAndFail()
            assertContains(result.output, "has no awake-net/socket-worker.js")
            assertContains(result.output, "unzip")
        }
    }

    private fun scriptBytes(): ByteArray =
        script.also { assertTrue(it.isFile, "the worker script is at $it") }.readBytes()

    private fun run(dir: File, vararg arguments: String): BuildResult =
        GradleRunner.create().withProjectDir(dir).withPluginClasspath()
            .withArguments(*arguments, "--stacktrace", "--max-workers=2").build()

    /** [script] null makes a klib without the worker script. */
    private fun klibWith(script: ByteArray?): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("default/manifest"))
            zip.write("unique_name=client-browser\n".toByteArray())
            zip.closeEntry()
            if (script != null) {
                zip.putNextEntry(ZipEntry("awake-net/socket-worker.js"))
                zip.write(script)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    /** [klib] null means the project doesn't depend on the client at all. */
    private fun withProject(klib: ByteArray?, block: (File) -> Unit) {
        val directory = createTempDirectory("awake-socket-worker-test").toFile()
        try {
            val repository = File(directory, "repo")
            if (klib != null) writeClientRepository(repository, klib)
            File(directory, "settings.gradle.kts").writeText("rootProject.name = \"web\"\n")
            File(directory, "gradle.properties").writeText(
                "org.gradle.jvmargs=-Xmx1g -XX:ActiveProcessorCount=2\n" +
                    // No stdlib to fetch: the only module the wasm classpath holds is the stand-in.
                    "kotlin.stdlib.default.dependency=false\n",
            )
            File(directory, "build.gradle.kts").writeText(
                """
                plugins {
                    id("org.jetbrains.kotlin.multiplatform")
                    id("com.awakekt.awake.plugin.socket-worker")
                }
                repositories { maven { url = uri("repo"); metadataSources { gradleMetadata() } } }
                kotlin {
                    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
                    wasmJs { browser(); binaries.executable() }
                    sourceSets {
                        ${if (klib != null) "wasmJsMain.dependencies { implementation(\"com.awakekt.awake.net:client-browser:1.0.0\") }" else ""}
                    }
                }
                """.trimIndent(),
            )
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun writeClientRepository(repository: File, klib: ByteArray) {
        val root = File(repository, "com/awakekt/awake/net/client-browser/1.0.0").apply { mkdirs() }
        val wasm = File(repository, "com/awakekt/awake/net/client-browser-wasm-js/1.0.0").apply { mkdirs() }
        val attributes = """
            "org.gradle.category": "library",
            "org.gradle.jvm.environment": "non-jvm",
            "org.gradle.usage": "kotlin-runtime",
            "org.jetbrains.kotlin.platform.type": "wasm",
            "org.jetbrains.kotlin.wasm.target": "js"
        """.trimIndent()
        val component = """"group": "com.awakekt.awake.net", "version": "1.0.0", "attributes": {"org.gradle.status": "release"}"""
        File(root, "client-browser-1.0.0.module").writeText(
            """
            {
              "formatVersion": "1.1",
              "component": {$component, "module": "client-browser"},
              "variants": [{
                "name": "wasmJsRuntimeElements-published",
                "attributes": {$attributes},
                "available-at": {"url": "../../client-browser-wasm-js/1.0.0/client-browser-wasm-js-1.0.0.module", "group": "com.awakekt.awake.net", "module": "client-browser-wasm-js", "version": "1.0.0"}
              }]
            }
            """.trimIndent(),
        )
        File(wasm, "client-browser-wasm-js-1.0.0.module").writeText(
            """
            {
              "formatVersion": "1.1",
              "component": {"url": "../../client-browser/1.0.0/client-browser-1.0.0.module", $component, "module": "client-browser"},
              "variants": [{
                "name": "wasmJsRuntimeElements-published",
                "attributes": {$attributes},
                "files": [{"name": "client-browser-wasm-js-1.0.0.klib", "url": "client-browser-wasm-js-1.0.0.klib"}]
              }]
            }
            """.trimIndent(),
        )
        File(wasm, "client-browser-wasm-js-1.0.0.klib").writeBytes(klib)
    }
}
