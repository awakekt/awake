/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.strings.StringKeysExtension
import com.awakekt.awake.build.tasks.GenerateStringKeysTask
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Typed string keys from a JSON string table, so code reads `Strings.menu.start`, not "menu.start":
//     plugins { id("com.awakekt.awake.plugin.string-keys") version "<the Core version you build against>" }
//     stringKeys { packageName.set("com.example.app") }
//
// The table is `src/commonMain/strings/en.json` unless `sourceFile` says otherwise. The generated file
// is added to commonMain, and has no dependency beyond the Kotlin standard library. Without the Kotlin
// Multiplatform plugin, register `tasks.named<GenerateStringKeysTask>("generateStringKeys")` with your
// source set's `srcDir` yourself.
val stringKeys = extensions.create<StringKeysExtension>("stringKeys")
stringKeys.sourceFile.convention(layout.projectDirectory.file("src/commonMain/strings/en.json"))
stringKeys.objectName.convention("Strings")
stringKeys.keyTypeName.convention("StringKey")
stringKeys.strictKeys.convention(false)

val generate = tasks.register<GenerateStringKeysTask>("generateStringKeys") {
    group = "awake codegen"
    description = "Generate the typed string keys object from the string table."
    sourceFile.set(stringKeys.sourceFile)
    packageName.set(stringKeys.packageName)
    objectName.set(stringKeys.objectName)
    keyTypeName.set(stringKeys.keyTypeName)
    strictKeys.set(stringKeys.strictKeys)
    outputDirectory.set(layout.buildDirectory.dir("generated/awake/stringKeys"))
}

plugins.withId("org.jetbrains.kotlin.multiplatform") {
    extensions.configure<KotlinMultiplatformExtension> {
        // The TASK, not the directory, so compiling runs the generator on a clean checkout.
        sourceSets.named("commonMain") { kotlin.srcDir(generate) }
    }
}
