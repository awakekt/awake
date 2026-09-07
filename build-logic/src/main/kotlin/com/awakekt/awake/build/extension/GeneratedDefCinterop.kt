/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.extension
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

/**
 * Cinterop over a native library whose linker flags are build-tree paths: appends a
 * `linkerOpts` line to a checked-in base `.def` and registers the interop on the target's
 * main compilation.
 *
 * The pattern exists because `binaries { linkerOpts(...) }` only affects binaries the
 * declaring module links itself, never a downstream consumer's final link — the flags must
 * travel inside the klib, which means inside the `.def`. Three modules hand-rolled this
 * (jolt's JoltC, the Vulkan bindings' MoltenVK, the shader compiler's naga) with the same
 * write-generated-def dance; this is that dance, once.
 *
 * Generated eagerly at configuration time on purpose: the flags are just directory paths,
 * known before the native libraries exist — the same reasoning each call site documented
 * individually.
 */
fun Project.registerGeneratedDefCinterop(
    target: KotlinNativeTarget,
    interopName: String,
    baseDefFile: java.io.File,
    headerDirs: List<java.io.File>,
    linkerOpts: List<String>,
) {
    val generatedDefFile = layout.buildDirectory
        .file("cinterop/$interopName-${target.name}.def").get().asFile
    generatedDefFile.parentFile.mkdirs()
    generatedDefFile.writeText(
        baseDefFile.readText() + "\nlinkerOpts = ${linkerOpts.joinToString(" ")}\n",
    )
    target.compilations.getByName("main") {
        cinterops {
            create(interopName) {
                defFile(generatedDefFile)
                headerDirs.forEach { includeDirs(it) }
            }
        }
    }
}
