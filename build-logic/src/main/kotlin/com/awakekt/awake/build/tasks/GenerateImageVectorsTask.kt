/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import com.awakekt.awake.build.icons.generatePack
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Generates one Kotlin `ImageVector` file per icon pack under [sourceDirectory].
 *
 * A pack is a directory holding a `manifest.json` and the SVGs it names, so
 * `src/commonMain/svg/lucide/manifest.json` becomes one generated file. Only the icons vendored
 * there exist, which is what lets each module carry just the glyphs it draws.
 *
 * Writes into `build/generated`, not `src/`: registering the task with `kotlin.srcDir` lets Gradle
 * infer the compile dependency, and detekt and spotless never see generated code.
 */
@CacheableTask
abstract class GenerateImageVectorsTask : DefaultTask() {

    /** `src/<sourceSet>/svg`, one subdirectory per pack. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val output = outputDirectory.get().asFile
        // A removed pack must not leave its file behind to keep compiling.
        output.deleteRecursively()
        val manifests = sourceDirectory.get().asFile
            .listFiles()
            .orEmpty()
            .map { it.resolve("manifest.json") }
            .filter { it.isFile }
            .sorted()
        check(manifests.isNotEmpty()) { "no <pack>/manifest.json under ${sourceDirectory.get().asFile}" }
        for (manifest in manifests) {
            val pack = generatePack(manifest)
            val target = output.resolve(pack.packageName.replace('.', '/')).resolve("${pack.fileName}.kt")
            target.parentFile.mkdirs()
            target.writeText(pack.source)
            logger.info("Generated ${target.name} from ${manifest.parentFile.name}")
        }
    }
}
