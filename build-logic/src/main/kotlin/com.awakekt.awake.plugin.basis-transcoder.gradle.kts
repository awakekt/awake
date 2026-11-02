/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.tasks.ExtractBasisTranscoderTask
import com.awakekt.awake.build.tasks.isTextureKtxLoader
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Puts the Basis Universal transcoder (`basis/basis_transcoder.js` and `.wasm`) beside a web app's page.
// Apply it to the Kotlin Multiplatform module that has the wasmJs target and the executable:
//     plugins { id("com.awakekt.awake.plugin.basis-transcoder") version "<the Core version you build against>" }
//
// If the module's wasmJs runtime classpath holds `texture-ktx-loader`, its klib's `basis/` folder is
// checked against Core's pinned digests and added to wasmJsMain's resources, which the development
// server and the production distribution both serve beside index.html. A module that doesn't depend on
// the loader gets nothing. The klib is found when the task runs, never while the build configures.
plugins.withId("org.jetbrains.kotlin.multiplatform") {
    val extract = tasks.register<ExtractBasisTranscoderTask>("extractBasisTranscoder") {
        outputDirectory.set(layout.buildDirectory.dir("generated/awake/basisTranscoder/resources"))
    }
    configurations.matching { it.name == "wasmJsRuntimeClasspath" }.configureEach {
        // A lazy view: nothing resolves until the task's inputs are read.
        extract.configure {
            loaderKlibs.from(incoming.artifactView { componentFilter(::isTextureKtxLoader) }.files)
        }
    }
    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.matching { it.name == "wasmJsMain" }.configureEach {
            // The task, not its directory, so processResources waits for it.
            resources.srcDir(extract)
        }
    }
}
