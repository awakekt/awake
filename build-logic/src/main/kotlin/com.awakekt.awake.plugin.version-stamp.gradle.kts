/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.tasks.GenerateVersionStampTask
import com.awakekt.awake.build.version.GitShortShaValueSource
import com.awakekt.awake.build.version.VersionStampExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// A generated `object` with `VERSION` and `COMMIT`, so an app reports what it was built from:
//     plugins { id("com.awakekt.awake.plugin.version-stamp") version "<the Core version you build against>" }
//     versionStamp { packageName.set("com.example.app") }
//
// VERSION is the project's `version` (X.Y.Z, X.Y.Z-beta.N or X.Y.Z-rc.N) unless `version` says otherwise;
// anything else fails the build. COMMIT is `git rev-parse --short HEAD`, read as a configuration-cache
// safe value, and "unknown" outside a git checkout. The file is added to commonMain; without the Kotlin
// Multiplatform plugin, register the `generateVersionStamp` task with your source set's `srcDir`.
val versionStamp = extensions.create<VersionStampExtension>("versionStamp")
versionStamp.objectName.convention("VersionStamp")
versionStamp.version.convention(provider { project.version.toString() })
versionStamp.commit.convention(
    providers.of(GitShortShaValueSource::class) {
        parameters.directory.set(layout.projectDirectory)
    },
)

val generate = tasks.register<GenerateVersionStampTask>("generateVersionStamp") {
    group = "awake codegen"
    description = "Generate the object that holds the build's version and commit."
    version.set(versionStamp.version)
    commit.set(versionStamp.commit)
    packageName.set(versionStamp.packageName)
    objectName.set(versionStamp.objectName)
    outputDirectory.set(layout.buildDirectory.dir("generated/awake/versionStamp"))
}

plugins.withId("org.jetbrains.kotlin.multiplatform") {
    extensions.configure<KotlinMultiplatformExtension> {
        // The TASK, not the directory, so compiling runs the generator on a clean checkout.
        sourceSets.named("commonMain") { kotlin.srcDir(generate) }
    }
}
