/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.util.Properties
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertTrue

class SceneComponentsPluginTest {
    @Test
    fun externalConsumerUsesPluginReleaseInsteadOfProjectVersion() {
        val version = javaClass.getResourceAsStream("/com/awakekt/awake/build/scene-components.properties")!!.use {
            Properties().apply { load(it) }.getProperty("version")
        }
        checkDependency("", "", "com.awakekt.awake.scene.binding:processor:$version")
    }

    @Test
    fun externalConsumerCanOverrideProcessorVersion() {
        checkDependency("", "awakeSceneComponents.processorVersion.set(\"0.0.0-local\")", "com.awakekt.awake.scene.binding:processor:0.0.0-local")
    }

    @Test
    fun coreContinuesToUseTheProcessorProject() {
        checkDependency("include(\":awake:scene:binding:processor\")", "", ":awake:scene:binding:processor")
    }

    private fun checkDependency(settings: String, configure: String, expected: String) {
        val directory = createTempDirectory("awake-scene-components-test").toFile()
        try {
            if (settings.isNotEmpty()) File(directory, "awake/scene/binding/processor").mkdirs()
            File(directory, "settings.gradle.kts").writeText("rootProject.name = \"consumer\"\n$settings\n")
            File(directory, "gradle.properties").writeText("org.gradle.jvmargs=-Xmx1g -XX:ActiveProcessorCount=2\n")
            File(directory, "build.gradle.kts").writeText(
                """
                plugins {
                    id("org.jetbrains.kotlin.multiplatform")
                    id("com.awakekt.awake.plugin.scene-components")
                }
                version = "7.8.9-consumer"
                repositories { mavenCentral() }
                kotlin {
                    jvm()
                    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
                    wasmJs { nodejs() }
                }
                awakeSceneComponents.registry.set("example.BeaconComponents")
                $configure
                tasks.register("checkSelection") {
                    doLast {
                        val dependency = configurations.getByName("kspCommonMainMetadata").dependencies.single()
                        val selected = if (dependency is ProjectDependency) dependency.path else
                            "${'$'}{dependency.group}:${'$'}{dependency.name}:${'$'}{dependency.version}"
                        check(selected == "$expected") { "Unexpected processor: ${'$'}selected" }
                        check(tasks.getByName("compileKotlinJvm").taskDependencies
                            .getDependencies(tasks.getByName("compileKotlinJvm"))
                            .any { it.name == "kspCommonMainKotlinMetadata" })
                        check(kotlin.sourceSets.getByName("commonMain").kotlin.srcDirs
                            .any { it.path.replace('\\', '/').endsWith("generated/ksp/metadata/commonMain/kotlin") })
                        println("SELECTION_OK")
                    }
                }
                """.trimIndent(),
            )
            val result = GradleRunner.create().withProjectDir(directory).withPluginClasspath()
                .withArguments("checkSelection", "--stacktrace", "--max-workers=2", "--no-configuration-cache").build()
            assertTrue("SELECTION_OK" in result.output)
        } finally {
            directory.deleteRecursively()
        }
    }
}
