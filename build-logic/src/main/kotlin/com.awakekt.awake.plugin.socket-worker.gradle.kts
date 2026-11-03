/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
import com.awakekt.awake.build.tasks.ExtractSocketWorkerTask
import com.awakekt.awake.build.tasks.isNetClientBrowser
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

// Puts the browser socket's worker script (`awake-net/socket-worker.js`) beside a web app's page.
// Apply it to the Kotlin Multiplatform module that has the wasmJs target and the executable:
//     plugins { id("com.awakekt.awake.plugin.socket-worker") version "<the Core version you build against>" }
//
// If the module's wasmJs runtime classpath holds `net:client-browser`, its klib's `awake-net/` folder
// is added to wasmJsMain's resources, which the development server and the production distribution
// both serve beside index.html. A module that doesn't depend on the client gets nothing. The klib is
// found when the task runs, never while the build configures.
plugins.withId("org.jetbrains.kotlin.multiplatform") {
    val extract = tasks.register<ExtractSocketWorkerTask>("extractSocketWorker") {
        outputDirectory.set(layout.buildDirectory.dir("generated/awake/socketWorker/resources"))
    }
    configurations.matching { it.name == "wasmJsRuntimeClasspath" }.configureEach {
        // A lazy view: nothing resolves until the task's inputs are read.
        extract.configure {
            clientKlibs.from(incoming.artifactView { componentFilter(::isNetClientBrowser) }.files)
        }
    }
    extensions.configure<KotlinMultiplatformExtension> {
        sourceSets.matching { it.name == "wasmJsMain" }.configureEach {
            // The task, not its directory, so processResources waits for it.
            resources.srcDir(extract)
        }
    }
}
