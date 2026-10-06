/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.extension

import com.sun.net.httpserver.HttpServer
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PublicationFamilyTest {
    @Test
    fun snapshotUploadsWithoutRegisteringAnExcludedStableBundle() = fixture { runner, files ->
        val result = runner.withArguments("publishFixture", "-PmavenCentralUsername=test", "-PmavenCentralPassword=test", "--stacktrace").build()
        assertEquals(TaskOutcome.SKIPPED, result.task(":vulkan:prepareMavenCentralPublishing")?.outcome)
        assertEquals(TaskOutcome.SKIPPED, result.task(":vulkan:enableAutomaticMavenCentralPublishing")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result.task(":core:publishMavenPublicationToMavenCentralRepository")?.outcome)
        assertTrue(files.keys.any { it.contains("/core/1.0.1-SNAPSHOT/") && it.endsWith(".jar") })
        assertTrue(files.keys.none { it.contains("/vulkan/") })
    }

    @Test
    fun noSelectedPublicationsDoesNotRegisterAReleaseBundle() = fixture { runner, files ->
        val result = runner.withArguments("publishFixture", "-PexcludeCore=true", "-PmavenCentralUsername=test", "-PmavenCentralPassword=test").build()
        assertEquals(TaskOutcome.SKIPPED, result.task(":core:prepareMavenCentralPublishing")?.outcome)
        assertTrue(files.isEmpty())
    }

    @Test
    fun selectedSnapshotUploadFailureStillFailsTheBuild() = fixture(rejectUploads = true) { runner, _ ->
        val result = runner.withArguments("publishFixture", "-PmavenCentralUsername=test", "-PmavenCentralPassword=test").buildAndFail()
        assertEquals(TaskOutcome.FAILED, result.task(":core:publishMavenPublicationToMavenCentralRepository")?.outcome)
        assertTrue(result.output.contains("401"), result.output)
    }

    @Test
    fun unfilteredStablePreparationTriggersTheRegressionTripwire() = fixture { runner, _ ->
        val result = runner.withArguments("publishFixture", "-Punfiltered=true", "-PmavenCentralUsername=test", "-PmavenCentralPassword=test").buildAndFail()
        assertEquals(TaskOutcome.FAILED, result.task(":vulkan:prepareMavenCentralPublishing")?.outcome)
        assertTrue(result.output.contains("Excluded stable bundle was registered"), result.output)
    }

    private fun fixture(
        rejectUploads: Boolean = false,
        test: (GradleRunner, Map<String, ByteArray>) -> Unit,
    ) {
        val directory = createTempDirectory("publication-family").toFile()
        val files = ConcurrentHashMap<String, ByteArray>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val content = files[path]
            when {
                rejectUploads -> exchange.sendResponseHeaders(401, -1)
                exchange.requestMethod == "PUT" -> {
                    files[path] = exchange.requestBody.readAllBytes()
                    exchange.sendResponseHeaders(201, -1)
                }
                content != null -> {
                    exchange.sendResponseHeaders(200, content.size.toLong())
                    exchange.responseBody.write(content)
                }
                else -> exchange.sendResponseHeaders(404, -1)
            }
            exchange.close()
        }
        server.start()
        try {
            directory.resolve("core").mkdirs()
            directory.resolve("vulkan").mkdirs()
            directory.resolve("settings.gradle").writeText("rootProject.name = 'fixture'\ninclude 'core', 'vulkan'\n")
            directory.resolve("build.gradle").writeText(
                """
                import com.awakekt.awake.build.extension.PublicationFamilyKt
                plugins { id 'com.vanniktech.maven.publish.base' apply false }
                subprojects {
                    apply plugin: 'java-library'
                    apply plugin: 'com.vanniktech.maven.publish.base'
                    group = 'test.awake'
                    version = name == 'core' ? '1.0.1-SNAPSHOT' : '1.0.0'
                    mavenPublishing.publishToMavenCentral(true)
                    publishing.publications.create('maven', MavenPublication) { from components.java }
                    afterEvaluate {
                        publishing.repositories.named('mavenCentral') {
                            url = uri('http://127.0.0.1:${server.address.port}/')
                            allowInsecureProtocol = true
                            credentials { username = 'test'; password = 'test' }
                        }
                    }
                    tasks.withType(PublishToMavenRepository).configureEach {
                        doFirst {
                            assert repository.url.host == '127.0.0.1': 'Tests must publish only to the local server'
                        }
                    }
                    if (!rootProject.hasProperty('unfiltered')) {
                        PublicationFamilyKt.configurePublicationFamily(project, name == 'core' && !rootProject.hasProperty('excludeCore'))
                    }
                    // Prevent a regressed filter from ever attempting a real Central release upload.
                    if (name == 'vulkan') tasks.named('prepareMavenCentralPublishing') {
                        doLast { throw new GradleException('Excluded stable bundle was registered') }
                    }
                }
                tasks.register('publishFixture') {
                    dependsOn ':core:publishAllPublicationsToMavenCentralRepository', ':vulkan:publishAllPublicationsToMavenCentralRepository'
                }
                """.trimIndent(),
            )
            test(GradleRunner.create().withProjectDir(directory).withPluginClasspath(), files)
        } finally {
            server.stop(0)
            directory.deleteRecursively()
        }
    }
}
