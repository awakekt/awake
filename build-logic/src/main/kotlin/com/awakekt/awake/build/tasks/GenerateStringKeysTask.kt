/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import com.awakekt.awake.build.strings.StringTable
import com.awakekt.awake.build.strings.generateStringKeys
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Generates a typed Kotlin `object` of string keys from a flat or nested JSON string table
 * ([sourceFile], usually `en.json`), so code writes `Strings.menu.start` instead of `"menu.start"`.
 *
 * Writes into [outputDirectory] under `build/`; register the task with `kotlin.srcDir` (the
 * `com.awakekt.awake.plugin.string-keys` plugin does) so compiling runs it.
 */
@CacheableTask
abstract class GenerateStringKeysTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFile: RegularFileProperty

    @get:Input
    abstract val packageName: Property<String>

    @get:Input
    abstract val objectName: Property<String>

    /** The name of the generated key type, beside [objectName] in the same file. */
    @get:Input
    abstract val keyTypeName: Property<String>

    /** Fail on a key that is not an identifier, instead of escaping it. */
    @get:Input
    abstract val strictKeys: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        objectName.convention("Strings")
        keyTypeName.convention("StringKey")
        strictKeys.convention(false)
    }

    @TaskAction
    fun generate() {
        val source = sourceFile.get().asFile
        val table: StringTable
        val text = try {
            table = StringTable.parse(source.readText(), strictKeys.get())
            generateStringKeys(table, packageName.get(), objectName.get(), keyTypeName.get())
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("${source.name}: ${e.message}", e)
        }
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        val target = output.resolve(packageName.get().replace('.', '/')).resolve("${objectName.get()}.kt")
        target.parentFile.mkdirs()
        target.writeText(text)
        logger.info("Generated ${target.name} with ${table.size} keys from ${source.name}")
    }
}
