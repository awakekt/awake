/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import com.awakekt.awake.build.scene.SceneComponentCodegen
import com.awakekt.awake.build.scene.SceneComponentSpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Generates each scene component under [specDirectory]: its document class and its binding.
 *
 * `src/commonMain/scene-components/canvas_element.json` becomes `SceneCanvasElement.kt` and
 * `CanvasElementBinding.kt` in the spec's package. Writes into `build/generated`, so registering the
 * task with `kotlin.srcDir` infers the compile dependency and detekt and spotless never see it.
 */
@CacheableTask
abstract class GenerateSceneComponentsTask : DefaultTask() {
    /** `src/<sourceSet>/scene-components`, one `<serialName>.json` spec per component. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val specDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val output = outputDirectory.get().asFile
        // A removed spec must not leave its classes behind to keep compiling.
        output.deleteRecursively()
        val specs = sceneComponentSpecs(specDirectory.get().asFile)
        check(specs.isNotEmpty()) { "no <serialName>.json spec under ${specDirectory.get().asFile}" }
        for (spec in specs) {
            SceneComponentCodegen.generate(spec).forEach { it.writeTo(output) }
            logger.info("Generated ${spec.sceneClass} and ${spec.binding} from ${spec.sourcePath}")
        }
    }
}

/** Every spec in [directory], read in name order, each naming its file by its path from [directory]. */
internal fun sceneComponentSpecs(directory: File): List<SceneComponentSpec> = directory
    .listFiles { file -> file.isFile && file.extension == "json" }
    .orEmpty()
    .sortedBy { it.name }
    .map { SceneComponentSpec.parse(it.readText(), "${directory.name}/${it.name}") }
