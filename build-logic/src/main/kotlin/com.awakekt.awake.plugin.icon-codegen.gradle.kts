/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.tasks.GenerateImageVectorsTask
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Every Kotlin source set with an `svg/` directory gets its icon packs generated into it:
// `src/commonMain/svg/<pack>/manifest.json` feeds commonMain, `src/commonTest/svg/...` feeds tests.
plugins.withId("org.jetbrains.kotlin.multiplatform") {
    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.configureEach {
            val sourceSetName = name
            val svgRoot = layout.projectDirectory.dir("src/$sourceSetName/svg")
            if (!svgRoot.asFile.isDirectory) return@configureEach
            val generate = tasks.register<GenerateImageVectorsTask>(
                "generate${sourceSetName.replaceFirstChar(Char::uppercaseChar)}ImageVectors",
            ) {
                group = "awake codegen"
                description = "Generate ImageVector sources from src/$sourceSetName/svg."
                sourceDirectory.set(svgRoot)
                outputDirectory.set(layout.buildDirectory.dir("generated/imagevector/$sourceSetName"))
            }
            // The TASK, not the directory: a bare directory compiles fine and never runs the generator,
            // so a clean checkout would build the module with no icons at all.
            kotlin.srcDir(generate)
        }
    }
}
