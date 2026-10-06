/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** A tag exists before Central necessarily serves its platform metadata and binaries. */
@DisableCachingByDefault(because = "Checks availability in an external repository")
abstract class WaitForVulkanCentralTask : DefaultTask() {
    @get:Input
    abstract val releaseVersion: Property<String>

    @get:Input
    abstract val timeoutSeconds: Property<Long>

    init {
        releaseVersion.convention(project.providers.gradleProperty("awake.waitForVulkanVersion"))
        timeoutSeconds.convention(3600L)
    }

    @TaskAction
    fun waitForRelease() {
        val version = releaseVersion.get()
        require(version.matches(Regex("[0-9][A-Za-z0-9.-]*")) && !version.endsWith("-SNAPSHOT")) {
            "Expected an immutable Vulkan release version, got $version"
        }
        val repository = URI("https://repo.maven.apache.org/maven2/")
        val modules = listOf(
            "com/awakekt/awake/backend/vulkan",
            "com/awakekt/awake/vulkan-kmp",
            "com/awakekt/awake/vulkan-kmp-android-native",
        ).map { module -> repository.resolve("$module/$version/${module.substringAfterLast('/')}-$version.module") }
        val gate = CentralAvailability(repository, modules)
        require(timeoutSeconds.get() > 0) { "Central availability timeout must be positive" }
        val deadline = System.nanoTime() + Duration.ofSeconds(timeoutSeconds.get()).toNanos()
        while (true) {
            val missing = gate.missingFiles()
            if (missing.isEmpty()) {
                logger.lifecycle("Vulkan $version metadata and binaries are available on Maven Central")
                return
            }
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) throw GradleException("Vulkan $version is not available on Maven Central: ${missing.joinToString()}")
            logger.lifecycle("Waiting for Vulkan $version on Maven Central (${missing.size} missing): ${missing.first()}")
            Thread.sleep(minOf(30_000L, maxOf(1L, Duration.ofNanos(remaining).toMillis())))
        }
    }
}

/** Walk Gradle's available-at links, checking every declared variant file, not just the root POM. */
internal class CentralAvailability(private val repository: URI, modules: List<URI>) {
    private val pending = linkedSetOf<URI>()
    private val verified = mutableSetOf<URI>()
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    init {
        modules.forEach(::addModule)
    }

    fun missingFiles(): List<URI> {
        val attempted = mutableSetOf<URI>()
        while (true) {
            val uri = pending.firstOrNull { it !in attempted } ?: break
            attempted.add(uri)
            val metadata = uri.path.endsWith(".module")
            val request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .method(if (metadata) "GET" else "HEAD", HttpRequest.BodyPublishers.noBody()).build()
            val response = try {
                client.send(request, HttpResponse.BodyHandlers.ofString())
            } catch (_: IOException) {
                continue
            }
            when (response.statusCode()) {
                200 -> {
                    if (metadata) discoverFiles(uri, response.body())
                    pending.remove(uri)
                    verified.add(uri)
                }
                404, 429, in 500..599 -> Unit
                else -> throw GradleException("Central availability check failed: HTTP ${response.statusCode()} for $uri")
            }
        }
        return pending.toList()
    }

    private fun discoverFiles(module: URI, body: String) {
        val variants = Json.parseToJsonElement(body).jsonObject.getValue("variants").jsonArray
        require(variants.isNotEmpty()) { "No variants in $module" }
        variants.forEach { entry ->
            val variant = entry.jsonObject
            variant["available-at"]?.jsonObject?.get("url")?.jsonPrimitive?.content?.let {
                addModule(module.resolve(it))
            }
            variant["files"]?.jsonArray?.forEach { file ->
                addFile(module.resolve(file.jsonObject.getValue("url").jsonPrimitive.content))
            }
        }
    }

    private fun addModule(module: URI) {
        require(module.path.endsWith(".module")) { "Expected Gradle module metadata: $module" }
        addFile(module)
        addFile(URI(module.toString().removeSuffix(".module") + ".pom"))
    }

    private fun addFile(uri: URI) {
        require(uri.normalize().toString().startsWith(repository.toString())) { "Artifact outside repository: $uri" }
        if (uri !in verified) pending.add(uri)
    }
}
