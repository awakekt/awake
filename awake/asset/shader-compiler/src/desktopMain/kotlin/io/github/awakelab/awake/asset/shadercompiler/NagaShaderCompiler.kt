/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shadercompiler

import java.io.File

/** Desktop JNI actual. The dylib/so is built on demand by `buildNagaDesktop` (cargo) into
 * `build/desktop-native-libs`; loaded from `awake.naga.library` (set by this module's test
 * task) or `java.library.path` -- same wiring as `awake:backend:vulkan:bindings`. */
actual object NagaShaderCompiler : RuntimeShaderCompiler {
    init {
        val explicit = System.getProperty("awake.naga.library")
        if (explicit != null) {
            System.load(File(explicit).absolutePath)
        } else {
            System.loadLibrary("awake_naga")
        }
    }

    actual override fun wgslToSpirv(wgsl: String): ByteArray = NagaJni.wgslToSpirv(wgsl)

    actual override fun validate(wgsl: String): String? = NagaJni.validate(wgsl)
}
