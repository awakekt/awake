/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipFile

/**
 * Takes the browser socket's worker script out of the `net:client-browser` wasm-js klib on a web
 * app's runtime classpath and writes it to [SOCKET_WORKER_ENTRY] under [outputDirectory].
 *
 * The Kotlin plugin doesn't copy a dependency klib's resources into a wasm distribution, so without
 * this a consumer's page has no worker script and `connectBrowserSocket` runs the socket on the page.
 * A project that doesn't depend on the client has no klib here, so the task is skipped (NO-SOURCE) and
 * writes nothing.
 */
@CacheableTask
abstract class ExtractSocketWorkerTask : DefaultTask() {
    /** The client's klib: empty unless the project depends on `net:client-browser`. */
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val clientKlibs: ConfigurableFileCollection

    /** The resources folder that receives `awake-net/`. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "awake web"
        description = "Extracts the browser socket's worker script from the net:client-browser klib for the web distribution."
    }

    @TaskAction
    fun extract() {
        val target = outputDirectory.get().asFile
        target.deleteRecursively()
        clientKlibs.files.forEach { klib ->
            ZipFile(klib).use { zip ->
                val entry = checkNotNull(zip.getEntry(SOCKET_WORKER_ENTRY)) {
                    "${klib.name} has no $SOCKET_WORKER_ENTRY, so the web app can't run its socket in a worker. " +
                        "Use a net:client-browser release that carries it, or copy the file by hand: " +
                        "unzip ${klib.name} '$SOCKET_WORKER_ENTRY' -d src/wasmJsMain/resources"
                }
                val script = File(target, SOCKET_WORKER_ENTRY).apply { parentFile.mkdirs() }
                zip.getInputStream(entry).use { input -> script.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    companion object {
        /** Where the script is, in the klib and beside the page; `connectBrowserSocket`'s default. */
        const val SOCKET_WORKER_ENTRY: String = "awake-net/socket-worker.js"
    }
}

/** Selects the `net:client-browser` component of a runtime classpath: the module as published, or Core's own project. */
internal fun isNetClientBrowser(id: ComponentIdentifier): Boolean = when (id) {
    // Gradle module metadata redirects the root module to its wasm-js variant, which resolves as `-wasm-js`.
    is ModuleComponentIdentifier ->
        id.group == "com.awakekt.awake.net" && (id.module == "client-browser" || id.module == "client-browser-wasm-js")
    is ProjectComponentIdentifier -> id.projectPath.endsWith(":net:client-browser")
    else -> false
}
