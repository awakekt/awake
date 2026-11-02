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
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipFile

/**
 * Takes the Basis Universal transcoder out of the `texture-ktx-loader` wasm-js klib on a web app's
 * runtime classpath and writes it to `basis/` under [outputDirectory], after checking each file against
 * the release Core pins ([BasisTranscoderRelease]).
 *
 * The Kotlin plugin doesn't copy a dependency klib's resources into a wasm distribution, so without this
 * a consumer's page has no `basis/basis_transcoder.js` and its textures can't be transcoded. A project
 * that doesn't depend on the loader has no klib here, so the task is skipped (NO-SOURCE) and writes
 * nothing.
 */
@CacheableTask
abstract class ExtractBasisTranscoderTask : DefaultTask() {
    /** The loader's klib: empty unless the project depends on `texture-ktx-loader`. */
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val loaderKlibs: ConfigurableFileCollection

    /** File name under `basis/` to its expected SHA-256. */
    @get:Input
    abstract val expectedDigests: MapProperty<String, String>

    /** The resources folder that receives `basis/`. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "awake web"
        description = "Extracts the pinned Basis Universal transcoder from the texture-ktx-loader klib for the web distribution."
        expectedDigests.convention(BasisTranscoderRelease.digests)
    }

    @TaskAction
    fun extract() {
        val target = outputDirectory.get().asFile
        target.deleteRecursively()
        val basis = File(target, "basis").apply { mkdirs() }
        val expected = expectedDigests.get()
        loaderKlibs.files.forEach { klib ->
            ZipFile(klib).use { zip ->
                expected.forEach { (name, digest) ->
                    val entry = zip.getEntry("basis/$name")
                    checkNotNull(entry) {
                        "${klib.name} has no basis/$name, so the web app can't ship the Basis Universal transcoder. " +
                            "Use a texture-ktx-loader release that carries it, or copy the files by hand: " +
                            "unzip ${klib.name} 'basis/basis_transcoder.js' 'basis/basis_transcoder.wasm' -d src/wasmJsMain/resources"
                    }
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    val actual = BasisTranscoderRelease.sha256(bytes)
                    check(actual == digest) {
                        "basis/$name in ${klib.name} is not Core's pinned Basis Universal ${BasisTranscoderRelease.TAG} file: " +
                            "its SHA-256 is $actual, not $digest. Apply the Awake Gradle plugin at the same version as the " +
                            "Core release the app builds against."
                    }
                    File(basis, name).writeBytes(bytes)
                }
            }
        }
    }
}

/** Selects the `texture-ktx-loader` component of a runtime classpath: the module as published, or Core's own project. */
internal fun isTextureKtxLoader(id: ComponentIdentifier): Boolean = when (id) {
    // Gradle module metadata redirects the root module to its wasm-js variant, which resolves as `-wasm-js`.
    is ModuleComponentIdentifier ->
        id.group == "com.awakekt.awake.asset" && (id.module == "texture-ktx-loader" || id.module == "texture-ktx-loader-wasm-js")
    is ProjectComponentIdentifier -> id.projectPath.endsWith(":texture-ktx-loader")
    else -> false
}
