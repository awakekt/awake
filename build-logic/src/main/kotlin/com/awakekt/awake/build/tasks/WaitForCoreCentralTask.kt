/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Waits until Maven Central serves the Core modules the WebGPU release pin in Maven Local depends on.
 * A Core release's upload returns once Central is publishing it, before repo1 serves the files, and a
 * pin uploaded then names Core that no consumer can resolve yet.
 */
@DisableCachingByDefault(because = "Checks availability in an external repository")
abstract class WaitForCoreCentralTask : DefaultTask() {
    @get:Input
    abstract val releaseVersion: Property<String>

    @get:Input
    abstract val timeoutSeconds: Property<Long>

    init {
        releaseVersion.convention(project.providers.gradleProperty("awake.waitForCoreVersion"))
        timeoutSeconds.convention(3600L)
    }

    @TaskAction
    fun waitForRelease() {
        val version = releaseVersion.get()
        require(version.matches(Regex("[0-9][A-Za-z0-9.-]*")) && !version.endsWith("-SNAPSHOT")) {
            "Expected an immutable Core release version, got $version"
        }
        val pin = Path.of(System.getProperty("user.home"), ".m2", "repository", "com", "awakekt", "awake", "backend", "webgpu")
            .resolve("$version-SNAPSHOT").resolve("webgpu-$version-SNAPSHOT.module")
        if (!Files.isRegularFile(pin)) {
            throw GradleException("No WebGPU release pin for Core $version in Maven Local ($pin); publish it there first")
        }
        val repository = URI("https://repo.maven.apache.org/maven2/")
        val modules = pinnedCoreModules(pin, version).map { centralModule(repository, it, version) }
        CentralAvailability(repository, modules).await("Core $version", timeoutSeconds.get(), logger)
    }
}

/** The Gradle module file of [coordinate] (`group:name`) at [version] in the Maven [repository]. */
internal fun centralModule(repository: URI, coordinate: String, version: String): URI {
    val (group, name) = coordinate.split(':')
    return repository.resolve("${group.replace('.', '/')}/$name/$version/$name-$version.module")
}
