/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import com.awakekt.awake.build.scene.SceneComponentDocs
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Keeps each spec's field table on the reference page: [update] writes the tables in, and otherwise
 * the task fails when the page's table differs from its spec, naming the task that fixes it.
 */
@DisableCachingByDefault(because = "It reads, and when updating writes, a page outside the build")
abstract class SceneComponentDocsTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val specDirectory: DirectoryProperty

    /** The reference page; [update] writes it, so it is not a tracked input. */
    @get:Internal
    abstract val docsPage: RegularFileProperty

    /** Write the tables in, rather than check them. */
    @get:Input
    abstract val update: Property<Boolean>

    /** The task a failed check names, for the message. */
    @get:Input
    abstract val updateTaskPath: Property<String>

    @TaskAction
    fun run() {
        val page = docsPage.get().asFile
        val text = page.readText().replace("\r\n", "\n")
        val updated = sceneComponentSpecs(specDirectory.get().asFile)
            .fold(text) { current, spec -> SceneComponentDocs.replaceTable(current, spec) }
        when {
            updated == text -> logger.info("${page.name} matches its scene component specs")
            update.get() -> page.writeText(updated)
            else -> throw GradleException(
                "${page.name}'s field tables differ from the scene component specs under ${specDirectory.get().asFile}. " +
                    "Edit the spec, not the page, and run ${updateTaskPath.get()}.",
            )
        }
    }
}
