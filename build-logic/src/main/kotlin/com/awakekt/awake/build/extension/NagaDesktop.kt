/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.build.extension

import org.gradle.api.Task
import org.gradle.process.JavaForkOptions

/**
 * Gives [task]'s JVM the naga JNI library, and builds it first.
 *
 * Every desktop consumer that compiles a shader at runtime needs this: shipped shaders are WGSL
 * now, so `VulkanShaderResolver` calls `NagaShaderCompiler` on the way to every pipeline. Without
 * it a sample dies at `UnsatisfiedLinkError: no awake_naga in java.library.path` while building
 * its first pipeline -- which reads as a missing dependency rather than as an unbuilt one.
 *
 * By explicit property rather than by extending `java.library.path`, because that path is already
 * spoken for by the Vulkan bindings' own `.dylib` and a second entry there is one more thing to
 * keep in step. See `NagaShaderCompiler`'s desktop actual, which prefers the property.
 *
 * The `dependsOn` is the load-bearing half. Setting the property alone is silently a no-op on a
 * fresh clone or a new git worktree, where cargo has not run yet.
 */
fun <T> useNagaShaderCompiler(task: T) where T : Task, T : JavaForkOptions {
    val shaderCompilerProj = task.project.findProject(":awake:asset:shader-compiler")
    if (shaderCompilerProj != null) {
        val library = shaderCompilerProj.layout.projectDirectory
            .dir("rust-native/target/release")
            .asFile.resolve(HostOs.libraryFileName("awake_naga"))
        task.dependsOn("${shaderCompilerProj.path}:buildNagaDesktop")
        task.systemProperties["awake.naga.library"] = library.path
    }
}
