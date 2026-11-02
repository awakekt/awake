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
 * `texture-ktx-loader` module: Gradle module metadata that redirects to its wasm-js variant, and a klib
 * (a zip) that holds `basis/`, as the real one does.
 */
class BasisTranscoderPluginTest {
    private val vendored = File("../awake/asset/texture-ktx-loader/src/wasmJsMain/resources/basis")

    @Test
    fun theTranscoderLandsInTheWebResourcesWithTheConfigurationCache() {
        withProject(klib = klibWith(vendoredFiles())) { dir ->
            val first = run(dir, "wasmJsProcessResources", "--configuration-cache")
            assertEquals(TaskOutcome.SUCCESS, first.task(":extractBasisTranscoder")?.outcome)
            val processed = File(dir, "build/processedResources/wasmJs/main/basis")
            vendoredFiles().forEach { (name, bytes) ->
                assertContentEquals(bytes, File(processed, name).readBytes(), "$name is beside the page")
            }

            val second = run(dir, "wasmJsProcessResources", "--configuration-cache")
            assertContains(second.output, "Reusing configuration cache")
            assertEquals(TaskOutcome.UP_TO_DATE, second.task(":extractBasisTranscoder")?.outcome)
        }
    }

    @Test
    fun theDevelopmentServerAndTheProductionDistributionWaitForIt() {
        withProject(klib = klibWith(vendoredFiles())) { dir ->
            listOf("wasmJsBrowserDevelopmentRun", "wasmJsBrowserDistribution").forEach { task ->
                val plan = run(dir, task, "--dry-run").output
                assertContains(plan, ":extractBasisTranscoder SKIPPED", message = "$task runs it first: $plan")
            }
        }
    }

    @Test
    fun aCopyVendoredByHandIsReportedAsADuplicateNotSilentlyMixed() {
        withProject(klib = klibWith(vendoredFiles())) { dir ->
            vendoredFiles().forEach { (name, bytes) ->
                File(dir, "src/wasmJsMain/resources/basis/$name").apply { parentFile.mkdirs() }.writeBytes(bytes)
            }
            val result = GradleRunner.create().withProjectDir(dir).withPluginClasspath()
                .withArguments("wasmJsProcessResources", "--stacktrace", "--max-workers=2").buildAndFail()
            assertContains(result.output, "Entry basis/basis_transcoder.js is a duplicate")
        }
    }

    @Test
    fun aProjectWithoutTheLoaderGetsNothing() {
        withProject(klib = null) { dir ->
            val result = run(dir, "wasmJsProcessResources", "--configuration-cache")
            assertEquals(TaskOutcome.NO_SOURCE, result.task(":extractBasisTranscoder")?.outcome)
            assertFalse(File(dir, "build/processedResources/wasmJs/main/basis").exists())
        }
    }

    @Test
    fun aChangedFileFailsTheBuildNamingTheDigest() {
        val tampered = vendoredFiles().toMutableMap()
        tampered["basis_transcoder.wasm"] = tampered.getValue("basis_transcoder.wasm").copyOf().also { it[0] = (it[0] + 1).toByte() }
        withProject(klib = klibWith(tampered)) { dir ->
            val result = GradleRunner.create().withProjectDir(dir).withPluginClasspath()
                .withArguments("wasmJsProcessResources", "--stacktrace", "--max-workers=2").buildAndFail()
            assertContains(result.output, "basis/basis_transcoder.wasm in texture-ktx-loader-wasm-js-1.0.0.klib is not Core's pinned Basis Universal")
            assertContains(result.output, "a0f65d4a30ecb3269d01ead7d0a3477d2b0208146d083625a90623f473f6c139")
        }
    }

    @Test
    fun aKlibWithoutTheFilesFailsTheBuildNamingTheFix() {
        withProject(klib = klibWith(emptyMap())) { dir ->
            val result = GradleRunner.create().withProjectDir(dir).withPluginClasspath()
                .withArguments("wasmJsProcessResources", "--stacktrace", "--max-workers=2").buildAndFail()
            assertContains(result.output, "has no basis/basis_transcoder.js")
            assertContains(result.output, "unzip")
        }
    }

    private fun vendoredFiles(): Map<String, ByteArray> =
        listOf("basis_transcoder.js", "basis_transcoder.wasm").associateWith {
            File(vendored, it).also { file -> assertTrue(file.isFile, "the vendored $it is at $file") }.readBytes()
        }

    private fun run(dir: File, vararg arguments: String): BuildResult =
        GradleRunner.create().withProjectDir(dir).withPluginClasspath()
            .withArguments(*arguments, "--stacktrace", "--max-workers=2").build()

    private fun klibWith(basis: Map<String, ByteArray>): ByteArray {
        val bytes = java.io.ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("default/manifest"))
            zip.write("unique_name=texture-ktx-loader\n".toByteArray())
            zip.closeEntry()
            basis.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry("basis/$name"))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    /** [klib] null means the project doesn't depend on the loader at all. */
    private fun withProject(klib: ByteArray?, block: (File) -> Unit) {
        val directory = createTempDirectory("awake-basis-transcoder-test").toFile()
        try {
            val repository = File(directory, "repo")
            if (klib != null) writeLoaderRepository(repository, klib)
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
                    id("com.awakekt.awake.plugin.basis-transcoder")
                }
                repositories { maven { url = uri("repo"); metadataSources { gradleMetadata() } } }
                kotlin {
                    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
                    wasmJs { browser(); binaries.executable() }
                    sourceSets {
                        ${if (klib != null) "wasmJsMain.dependencies { implementation(\"com.awakekt.awake.asset:texture-ktx-loader:1.0.0\") }" else ""}
                    }
                }
                """.trimIndent(),
            )
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun writeLoaderRepository(repository: File, klib: ByteArray) {
        val root = File(repository, "com/awakekt/awake/asset/texture-ktx-loader/1.0.0").apply { mkdirs() }
        val wasm = File(repository, "com/awakekt/awake/asset/texture-ktx-loader-wasm-js/1.0.0").apply { mkdirs() }
        val attributes = """
            "org.gradle.category": "library",
            "org.gradle.jvm.environment": "non-jvm",
            "org.gradle.usage": "kotlin-runtime",
            "org.jetbrains.kotlin.platform.type": "wasm",
            "org.jetbrains.kotlin.wasm.target": "js"
        """.trimIndent()
        val component = """"group": "com.awakekt.awake.asset", "version": "1.0.0", "attributes": {"org.gradle.status": "release"}"""
        File(root, "texture-ktx-loader-1.0.0.module").writeText(
            """
            {
              "formatVersion": "1.1",
              "component": {$component, "module": "texture-ktx-loader"},
              "variants": [{
                "name": "wasmJsRuntimeElements-published",
                "attributes": {$attributes},
                "available-at": {"url": "../../texture-ktx-loader-wasm-js/1.0.0/texture-ktx-loader-wasm-js-1.0.0.module", "group": "com.awakekt.awake.asset", "module": "texture-ktx-loader-wasm-js", "version": "1.0.0"}
              }]
            }
            """.trimIndent(),
        )
        File(wasm, "texture-ktx-loader-wasm-js-1.0.0.module").writeText(
            """
            {
              "formatVersion": "1.1",
              "component": {"url": "../../texture-ktx-loader/1.0.0/texture-ktx-loader-1.0.0.module", $component, "module": "texture-ktx-loader"},
              "variants": [{
                "name": "wasmJsRuntimeElements-published",
                "attributes": {$attributes},
                "files": [{"name": "texture-ktx-loader-wasm-js-1.0.0.klib", "url": "texture-ktx-loader-wasm-js-1.0.0.klib"}]
              }]
            }
            """.trimIndent(),
        )
        File(wasm, "texture-ktx-loader-wasm-js-1.0.0.klib").writeBytes(klib)
    }
}
