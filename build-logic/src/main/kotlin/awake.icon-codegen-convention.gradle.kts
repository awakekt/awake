/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
plugins {
    base
}

val generatedRoot = layout.buildDirectory.dir("generated/imagevector")

val generateImageVectors = tasks.register<GenerateImageVectorsTask>("generateImageVectors") {
    group = "build setup"
    description = "Generate ImageVector sources from the vendored SVGs."
    sourceDirectory.set(layout.projectDirectory.dir("src/commonMain/svg/heroicons"))
    outputDirectory.set(generatedRoot)
    generatorScript.set(
        rootProject.layout.projectDirectory.file(
            ".agents/skills/awake-ui-icons/scripts/svg_to_ui_image_vector.py",
        ),
    )
    // Python is already a build requirement nowhere else, so name the escape hatches up front
    // rather than after the first CI host without `python3` on PATH.
    pythonExecutable.convention(
        providers.gradleProperty("awake.icons.python")
            .orElse(providers.environmentVariable("AWAKE_PYTHON"))
            .orElse("python3"),
    )
}

// The TASK, not the directory. Registering the directory alone compiles fine and silently never
// runs the generator -- Gradle has nothing linking that path to the task that fills it, so on a
// clean checkout the module compiles zero sources and every consumer fails to resolve `HeroIcons`.
// Passing the task provider carries its declared output, which is what infers the dependency.
extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension>("kotlin") {
    sourceSets.named("commonMain") { kotlin.srcDir(generateImageVectors) }
}

// Nothing excludes the generated tree from detekt or spotless here, because neither reaches it:
// both are already scoped to `src/**` (see their conventions) and this writes to `build/generated`.
// That is the second reason for writing there rather than into `src/`.
