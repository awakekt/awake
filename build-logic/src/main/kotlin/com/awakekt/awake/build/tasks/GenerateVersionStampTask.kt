/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import com.awakekt.awake.build.version.generateVersionStamp
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Generates a Kotlin `object` with `VERSION` and `COMMIT` constants, so a build can report what it
 * was made from. [version] is checked as a release version (`X.Y.Z`, `-beta.N`, `-rc.N`); [commit] is
 * a provider so the task never runs git itself and stays configuration-cache safe.
 */
@CacheableTask
abstract class GenerateVersionStampTask : DefaultTask() {

    @get:Input
    abstract val version: Property<String>

    /** The short git commit; "unknown" when the build is not in a git checkout. */
    @get:Input
    abstract val commit: Property<String>

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val objectName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        objectName.convention("VersionStamp")
    }

    @TaskAction
    fun generate() {
        val text = generateVersionStamp(version.get(), commit.get(), packageName.get(), objectName.get())
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        val target = output.resolve(packageName.get().replace('.', '/')).resolve("${objectName.get()}.kt")
        target.parentFile.mkdirs()
        target.writeText(text)
        logger.info("Generated ${target.name} for ${version.get()} (${commit.get()})")
    }
}
